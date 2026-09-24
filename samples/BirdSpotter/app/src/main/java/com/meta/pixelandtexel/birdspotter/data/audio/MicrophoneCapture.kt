/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureError
import com.meta.pixelandtexel.birdspotter.domain.AudioChunk
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.CaptureSampleRate
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * The phone's live microphone stream.
 *
 * **The phone's input and nothing else.** The glasses' microphone does not come through the
 * platform's audio stack at all — it arrives on the DAT camera stream (see
 * [GlassesMicrophoneSource]) — so this class never selects a communication device, never touches
 * the audio mode, and never has to refuse a route. What it records from is whatever the phone would
 * use on its own, which is the built-in microphone.
 *
 * **Asked for 16 kHz directly, not recorded wide and downsampled.** `AudioRecord` will happily
 * resample in the platform's own mixer, which is both better than anything written here and the
 * reason not to record at 44.1 kHz and throw most of it away. The point is to match what the
 * glasses' stream carries so the strip looks the same either way — see [CaptureSampleRate].
 *
 * `ENCODING_PCM_FLOAT` rather than 16-bit PCM: the analyzer wants floats, and letting the platform
 * hand them over is one conversion nobody has to write or test.
 *
 * A plain `flow` rather than a `callbackFlow`, because `AudioRecord` has no callback — it has a
 * blocking `read`, which belongs on [Dispatchers.IO] with the loop that drains it. Cancellation
 * lands within one buffer (128 ms): `read` returns, `emit` throws, and the `finally` stops and
 * releases the recorder. That is the whole cold-stream contract, with no second thread to
 * co-ordinate.
 */
class MicrophoneCapture(context: Context) {

  private val context = context.applicationContext

  fun audioStream(): Flow<AudioChunk> = flow {
    // The Identify gate should have collected this already, so reaching here denied means the
    // permission was revoked from Settings while the session was open.
    if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
    ) {
      throw AudioCaptureError.AccessDenied
    }

    val recorder =
        openRecorder()
            ?: run {
              BirdLog.warning(LogCategory.AUDIO) { "the phone's recorder would not open" }
              throw AudioCaptureError.Unavailable
            }

    try {
      recorder.startRecording()
      val buffer = FloatArray(ReadSamples)
      while (currentCoroutineContext().isActive) {
        val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
        when {
          read < 0 -> throw AudioCaptureError.Interrupted
          // `copyOf` because the buffer is reused on the next pass and an AudioChunk
          // outlives this loop iteration — handing out the live array would let the
          // analyzer read samples that had already been overwritten.
          read > 0 ->
              emit(
                  AudioChunk(
                      buffer.copyOf(read).also { samples ->
                        if (Gain != 1f) {
                          for (i in samples.indices) samples[i] *= Gain
                        }
                      },
                      source = CaptureSourceKind.PHONE,
                  ),
              )
        }
      }
    } finally {
      // `stop` throws if the recorder never started; releasing regardless is the point.
      runCatching { recorder.stop() }
      recorder.release()
    }
  }
      .flowOn(Dispatchers.IO)

  /**
   * `null` when the platform will not give us this format, or the mic at all.
   *
   * The grant is the caller's to hold — [audioStream] checks it and refuses before anything here
   * runs, which is the only place it can be checked *and* answered for: a revoked microphone is
   * [AudioCaptureError.AccessDenied] on the stream, and this function has no stream to fail.
   * Declared so that stays true of every future caller rather than by accident of there being one.
   */
  @RequiresPermission(Manifest.permission.RECORD_AUDIO)
  private fun openRecorder(): AudioRecord? {
    val minimumBytes =
        AudioRecord.getMinBufferSize(
            CaptureSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
    if (minimumBytes <= 0) return null

    val recorder =
        try {
          AudioRecord.Builder()
              // `MIC` rather than `UNPROCESSED`: unprocessed is the better signal for a
              // classifier and is not guaranteed present on every device, and a session that
              // cannot open the microphone at all is a worse demo than one with AGC in it.
              .setAudioSource(MediaRecorder.AudioSource.MIC)
              .setAudioFormat(
                  AudioFormat.Builder()
                      .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                      .setSampleRate(CaptureSampleRate)
                      .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                      .build(),
              )
              // Room for several reads, so a scheduling hiccup drops nothing.
              .setBufferSizeInBytes(maxOf(minimumBytes, ReadSamples * BytesPerFloat * 4))
              .build()
        } catch (_: Exception) {
          return null
        }

    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
      recorder.release()
      return null
    }
    return recorder
  }
}

/**
 * How much the phone's samples are lifted before anything downstream sees them.
 *
 * **One here, and that is the honest answer rather than an unfinished one.** [MicrophoneCapture]
 * asks for `AudioSource.MIC`, which arrives with the platform's own automatic gain already applied.
 * There is no level to buy back on this platform, and multiplying an already-ridden signal would
 * only push the noise floor up with it.
 *
 * **The number is a property of the microphone, not of the app.** A capture path that turns its
 * processing chain off — so a bird call is not reshaped on its way to the spectrogram — pays for
 * that in level and needs the gain back; this one does not. The seam is named so that stays one
 * decision in one place rather than a magic multiply in a read loop.
 */
private const val Gain = 1f

/**
 * Samples per read — 128 ms at 16 kHz, which is eight chunks a second and eight columns' worth of
 * work per chunk. Small enough that closing the session feels immediate, large enough that the loop
 * is not spinning.
 */
private const val ReadSamples = 2048

private const val BytesPerFloat = 4
