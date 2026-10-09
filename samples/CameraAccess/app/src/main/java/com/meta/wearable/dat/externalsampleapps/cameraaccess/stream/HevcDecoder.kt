/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// HevcDecoder - On-device HEVC decode for the live preview
//
// Decodes the compressed HEVC frames delivered by the DAT SDK and renders them directly
// to a Surface via MediaCodec (the GPU handles YUV->RGB). Replicates the SDK's
// VideoDecoder NAL-parsing / enqueue logic: parse NAL units, cache config, activate on
// the first keyframe, then feed each NAL separately.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

class HevcDecoder {

  companion object {
    private const val TAG = "HevcDecoder"
    private const val DATA_QUEUE_CAPACITY = 100
    // Hardware HEVC decoders that corrupt this stream into fragments/trash; the SDK's own decoder
    // skips them too. Prefer a software decoder instead (see createHevcDecoder).
    private val BLOCKED_DECODERS = setOf("OMX.Exynos.hevc.dec", "c2.mtk.hevc.decoder")
  }

  private class DecoderFrame(
      val data: ByteBuffer,
      val offset: Int = 0,
      val size: Int = data.remaining(),
      val presentationTimeUs: Long = 0L,
      val isKeyFrame: Boolean = false,
      val isConfigFrame: Boolean = false,
  ) {
    val flags: Int
      get() {
        var bitmask = 0
        if (isKeyFrame) bitmask = bitmask or MediaCodec.BUFFER_FLAG_KEY_FRAME
        if (isConfigFrame) bitmask = bitmask or MediaCodec.BUFFER_FLAG_CODEC_CONFIG
        return bitmask
      }
  }

  // These references are torn down from stop() on the caller thread while MediaCodec callbacks may
  // still fire on the decoder thread. @Volatile is sufficient: each is a single reference assigned
  // on setup and nulled on teardown — no compound state, only publication visibility.
  @Volatile private var decoder: MediaCodec? = null
  @Volatile private var decoderThread: HandlerThread? = null
  private val incomingDataQueue = LinkedBlockingQueue<DecoderFrame>(DATA_QUEUE_CAPACITY)

  // Input buffers the codec has offered that no frame has filled yet. Locked because activation
  // and stop() clear it from outside the decoder thread: a codec reset invalidates every index.
  private val availableInputBuffers = ArrayDeque<Int>()
  @Volatile private var decoderHandler: Handler? = null
  private val feedRunnable = Runnable { feedInputBuffers() }

  @Volatile private var mediaFormat: MediaFormat? = null
  @Volatile private var cachedVideoCodec: ByteBuffer? = null
  @Volatile private var active = false
  @Volatile private var firstInputFrame = true
  @Volatile private var receivedKeyframe = false
  @Volatile private var outputSurface: Surface? = null

  fun start(width: Int, height: Int, surface: Surface) {
    outputSurface = surface
    mediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height).also { format
          ->
          format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
          format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
          format.setInteger(MediaFormat.KEY_BIT_RATE, 750000)
        }
    try {
      ensureCodecsCreated()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to create HEVC decoder: ${e.message}", e)
    }
  }

  fun decodeFrame(data: ByteArray, presentationTimeUs: Long) {
    if (data.isEmpty()) return

    // Replicate SDK VideoDecoder.enqueue(buffer, presentationTimeUs) exactly:
    // parse NAL units, cache config, activate on keyframe, feed each NAL separately.
    val buffer = ByteBuffer.wrap(data)
    val writableByteArray = data.copyOf()
    var index = 0
    val prefixFlags = BooleanArray(3)

    index = findNalUnit(writableByteArray, index, data.size, prefixFlags)
    while (index < data.size) {
      val unitType = getH265NalUnitType(writableByteArray, index)
      val isKeyFrame = isIrapNalType(unitType)
      val isConfigFrame = unitType == 32 || unitType == 33 || unitType == 34

      if (isConfigFrame) {
        cachedVideoCodec = cloneByteBuffer(buffer)
      } else if (isKeyFrame) {
        if (!active) {
          active = true
          cachedVideoCodec?.let { cachedConfig -> enqueuePublic(cachedConfig) }
        }
        if (!receivedKeyframe) {
          receivedKeyframe = true
        }
      }

      enqueuePrivate(
          DecoderFrame(
              data = cloneByteBuffer(buffer),
              offset = index,
              presentationTimeUs = presentationTimeUs,
              isKeyFrame = isKeyFrame,
              isConfigFrame = isConfigFrame,
          ),
      )

      index = findNalUnit(writableByteArray, index + 1, data.size, prefixFlags)
    }
  }

  fun stop() {
    active = false
    decoderHandler = null
    incomingDataQueue.clear()
    synchronized(availableInputBuffers) { availableInputBuffers.clear() }
    try {
      decoder?.stop()
      decoder?.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping decoder: ${e.message}", e)
    }
    decoder = null
    decoderThread?.quit()
    decoderThread = null
    firstInputFrame = true
    receivedKeyframe = false
    cachedVideoCodec = null
    outputSurface = null
  }

  // Mirrors SDK VideoDecoder's public enqueue(ByteBuffer) — recursive entry for cached config
  private fun enqueuePublic(buffer: ByteBuffer) {
    val writableByteArray =
        ByteBuffer.allocate(buffer.capacity())
            .apply {
              buffer.rewind()
              put(buffer)
              flip()
              buffer.rewind()
            }
            .array()
    var index = 0
    val prefixFlags = BooleanArray(3)
    index = findNalUnit(writableByteArray, index, buffer.limit(), prefixFlags)
    while (index < buffer.limit()) {
      val unitType = getH265NalUnitType(writableByteArray, index)
      val isKeyFrame = isIrapNalType(unitType)
      val isConfigFrame = unitType == 32 || unitType == 33 || unitType == 34
      if (isConfigFrame) {
        cachedVideoCodec = cloneByteBuffer(buffer)
      } else if (isKeyFrame) {
        if (!receivedKeyframe) {
          receivedKeyframe = true
        }
      }
      enqueuePrivate(
          DecoderFrame(
              data = cloneByteBuffer(buffer),
              offset = index,
              presentationTimeUs = 0,
              isKeyFrame = isKeyFrame,
              isConfigFrame = isConfigFrame,
          ),
      )
      index = findNalUnit(writableByteArray, index + 1, buffer.limit(), prefixFlags)
    }
  }

  // Mirrors SDK VideoDecoder's private enqueue(VideoFrame)
  private fun enqueuePrivate(frame: DecoderFrame) {
    if (!active) return
    if (!frame.isConfigFrame && !receivedKeyframe) return
    if (firstInputFrame) {
      firstInputFrame = false
      activateDecoder()
    }
    if (incomingDataQueue.remainingCapacity() == 0) {
      Log.w(TAG, "Decoder queue full")
      active = false
      return
    }
    if (incomingDataQueue.offer(frame)) {
      // An input buffer may already be waiting for this frame.
      decoderHandler?.post(feedRunnable)
    }
  }

  private fun ensureCodecsCreated() {
    if (decoder == null) {
      decoder = createHevcDecoder()
    }
  }

  // Prefer a software HEVC decoder
  private fun createHevcDecoder(): MediaCodec {
    val mime = MediaFormat.MIMETYPE_VIDEO_HEVC
    val softwareName =
        MediaCodecList(MediaCodecList.ALL_CODECS)
            .codecInfos
            .firstOrNull { info ->
              !info.isEncoder &&
                  info.isSoftwareOnly &&
                  info.name !in BLOCKED_DECODERS &&
                  info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
            ?.name
    return if (softwareName != null) {
      Log.d(TAG, "Using software HEVC decoder: $softwareName")
      MediaCodec.createByCodecName(softwareName)
    } else {
      Log.w(TAG, "No software HEVC decoder found; using platform default")
      MediaCodec.createDecoderByType(mime)
    }
  }

  private fun activateDecoder() {
    try {
      ensureCodecsCreated()
      decoder?.let { codec ->
        decoderThread?.quit()
        val thread = HandlerThread("HevcDecoderThread", Process.THREAD_PRIORITY_VIDEO)
        thread.start()
        decoderThread = thread
        synchronized(availableInputBuffers) { availableInputBuffers.clear() }
        val handler = Handler(thread.looper)
        decoderHandler = handler

        codec.reset()
        codec.configure(mediaFormat, outputSurface, null, 0)
        codec.setCallback(
            object : MediaCodec.Callback() {
              override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                synchronized(availableInputBuffers) { availableInputBuffers.addLast(index) }
                feedInputBuffers()
              }

              override fun onOutputBufferAvailable(
                  codec: MediaCodec,
                  index: Int,
                  info: MediaCodec.BufferInfo,
              ) {
                onOutputBuffer(codec, index, info)
              }

              override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(TAG, "Codec error: ${e.message}")
              }

              override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
            },
            // Deliver MediaCodec callbacks on our own decoder thread.
            handler,
        )
        codec.start()
      }
    } catch (e: MediaCodec.CodecException) {
      Log.e(TAG, "Decoder activation codec exception: ${e.message}", e)
    } catch (e: Throwable) {
      Log.e(TAG, "Decoder activation exception: ${e.message}", e)
    }
  }

  /**
   * Pairs queued frames with free input buffers until either runs out, without ever waiting for
   * one. The codec delivers decoded output on this same thread, so blocking here would hold every
   * decoded frame back until the next frame arrived. Mirrors SDK AsyncFrameHandler.
   */
  private fun feedInputBuffers() {
    val codec = decoder ?: return
    while (true) {
      // stop() clears this before releasing the codec, which it does from another thread.
      if (decoderHandler == null) return
      val (index, frame) =
          synchronized(availableInputBuffers) {
            if (availableInputBuffers.isEmpty()) {
              null
            } else {
              incomingDataQueue.poll()?.let { availableInputBuffers.removeFirst() to it }
            }
          } ?: return
      submit(codec, index, frame)
    }
  }

  // Mirrors VideoDecoderBufferHandler.onInputBuffer — feeds ENTIRE buffer with offset
  private fun submit(codec: MediaCodec, index: Int, frame: DecoderFrame) {
    var bufferQueued = false
    try {
      val inputBuffer = codec.getInputBuffer(index)

      if (inputBuffer == null || !active) {
        codec.queueInputBuffer(index, 0, 0, 0, 0)
        bufferQueued = true
        return
      }

      frame.data.rewind()
      inputBuffer.clear()
      inputBuffer.put(frame.data)
      inputBuffer.flip()
      val clampedSize = minOf(frame.size, inputBuffer.limit() - frame.offset)
      codec.queueInputBuffer(
          index,
          frame.offset,
          clampedSize,
          frame.presentationTimeUs,
          frame.flags,
      )
      bufferQueued = true
    } catch (e: Throwable) {
      Log.e(TAG, "Input buffer error: ${e.message}", e)
      if (active) active = false
    } finally {
      if (!bufferQueued) {
        try {
          codec.queueInputBuffer(index, 0, 0, 0, 0)
        } catch (_: Throwable) {}
      }
    }
  }

  private fun onOutputBuffer(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
    try {
      if (!active || info.size == 0) {
        codec.releaseOutputBuffer(index, false)
        return
      }
      // Render directly to the Surface — the GPU handles YUV→RGB conversion.
      codec.releaseOutputBuffer(index, true)
    } catch (e: Throwable) {
      Log.e(TAG, "Output buffer error: ${e.message}", e)
      try {
        codec.releaseOutputBuffer(index, false)
      } catch (_: Throwable) {}
    }
  }

  // --- NalUnitUtil (copied from SDK) ---

  private fun findNalUnit(
      data: ByteArray,
      startOffset: Int,
      endOffset: Int,
      prefixFlags: BooleanArray,
  ): Int {
    val length = endOffset - startOffset
    if (length == 0) return endOffset

    when {
      prefixFlags[0] -> {
        clearPrefixFlags(prefixFlags)
        return startOffset - 3
      }
      length > 1 && prefixFlags[1] && data[startOffset].toInt() == 1 -> {
        clearPrefixFlags(prefixFlags)
        return startOffset - 2
      }
      length > 2 &&
          prefixFlags[2] &&
          data[startOffset].toInt() == 0 &&
          data[startOffset + 1].toInt() == 1 -> {
        clearPrefixFlags(prefixFlags)
        return startOffset - 1
      }
    }

    val limit = endOffset - 1
    var i = startOffset + 2
    while (i < limit) {
      if ((data[i].toInt() and 0xFE) != 0) {
        // no NAL prefix here or next two positions
      } else if (data[i - 2].toInt() == 0 && data[i - 1].toInt() == 0 && data[i].toInt() == 1) {
        clearPrefixFlags(prefixFlags)
        return i - 2
      } else {
        i -= 2
      }
      i += 3
    }

    prefixFlags[0] =
        if (length > 2)
            (data[endOffset - 3].toInt() == 0 &&
                data[endOffset - 2].toInt() == 0 &&
                data[endOffset - 1].toInt() == 1)
        else
            (if (length == 2)
                (prefixFlags[2] &&
                    data[endOffset - 2].toInt() == 0 &&
                    data[endOffset - 1].toInt() == 1)
            else (prefixFlags[1] && data[endOffset - 1].toInt() == 1))
    prefixFlags[1] =
        if (length > 1) (data[endOffset - 2].toInt() == 0 && data[endOffset - 1].toInt() == 0)
        else (prefixFlags[2] && data[endOffset - 1].toInt() == 0)
    prefixFlags[2] = data[endOffset - 1].toInt() == 0

    return endOffset
  }

  private fun clearPrefixFlags(prefixFlags: BooleanArray) {
    prefixFlags[0] = false
    prefixFlags[1] = false
    prefixFlags[2] = false
  }

  private fun getH265NalUnitType(data: ByteArray, offset: Int): Int {
    if (offset + 3 >= data.size) return -1
    return (data[offset + 3].toInt() and 0x7E) shr 1
  }

  // Any IRAP picture (BLA 16-18, IDR 19-20, CRA 21) is a decode-refresh point the decoder can
  // (re)activate on. Detecting only IDR misses CRA keyframes some encoders emit, which leaves the
  // preview black after the decoder deactivates on a full queue. Must match the recorder's keyframe
  // definition so preview and recording stay consistent.
  private fun isIrapNalType(unitType: Int): Boolean = unitType in 16..21

  private fun cloneByteBuffer(original: ByteBuffer): ByteBuffer {
    val clone: ByteBuffer =
        if (original.isDirect) ByteBuffer.allocateDirect(original.capacity())
        else ByteBuffer.allocate(original.capacity())
    original.rewind()
    clone.put(original)
    original.rewind()
    clone.flip()
    return clone
  }
}
