/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.audio

import com.meta.pixelandtexel.birdspotter.domain.CaptureSampleRate

/**
 * The journal's audio segments as files: 16-bit PCM mono WAV at [CaptureSampleRate], written and
 * read by hand.
 *
 * Hand-written for the reason the FFT is — a 44-byte RIFF header is a paragraph of code that reads
 * the same in both languages, where the platform encoders (`MediaMuxer` here) have no shared
 * sentence. WAV rather than raw PCM so the file explains itself: a segment pulled off a device
 * opens in any editor, which is worth more to a demo than the 44 bytes.
 *
 * 16-bit rather than the float the pipeline runs in, because these are files: half the bytes, and
 * nothing downstream keeps more than 8 bits per pixel anyway. The round trip costs one quantisation
 * step, inaudible at the levels a bird is recorded at.
 *
 * [decode] reads only what [encode] writes — this is not a general WAV reader. Anything but PCM
 * 16-bit mono at the capture rate is refused, loudly, because the only way such a file gets into
 * the media store is a bug somewhere upstream. The one exception is a segment saved before the
 * capture rate rose to 16 kHz, which comes back upsampled — see [LegacySampleRate].
 */
object WavCodec {

  private const val HeaderBytes = 44
  private const val BytesPerSample = 2

  /**
   * The rate every segment was written at before the capture rate rose to [CaptureSampleRate].
   *
   * **Read, and brought up to the current rate, rather than refused.** Those files are outings
   * somebody kept, and the journal draws and plays everything at one rate — so a segment at the old
   * one is doubled by linear interpolation on the way in. It gains no bandwidth it never recorded;
   * it simply lines up with the columns and the playhead again.
   */
  private const val LegacySampleRate = 8000

  /** Samples in −1..1 to a complete WAV file. */
  fun encode(samples: FloatArray, sampleRate: Int = CaptureSampleRate): ByteArray {
    val dataBytes = samples.size * BytesPerSample
    val bytes = ByteArray(HeaderBytes + dataBytes)

    bytes.putAscii(0, "RIFF")
    bytes.putIntLe(4, 36 + dataBytes)
    bytes.putAscii(8, "WAVE")
    bytes.putAscii(12, "fmt ")
    bytes.putIntLe(16, 16) // fmt chunk size
    bytes.putShortLe(20, 1) // PCM
    bytes.putShortLe(22, 1) // mono
    bytes.putIntLe(24, sampleRate)
    bytes.putIntLe(28, sampleRate * BytesPerSample)
    bytes.putShortLe(32, BytesPerSample) // block align
    bytes.putShortLe(34, 16) // bits per sample
    bytes.putAscii(36, "data")
    bytes.putIntLe(40, dataBytes)

    for (i in samples.indices) {
      // Scaled by 32767 both ways, so a full-scale sample comes back full-scale; the
      // asymmetric −32768 is left unused rather than special-cased.
      val quantised = (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
      bytes.putShortLe(HeaderBytes + i * BytesPerSample, quantised)
    }
    return bytes
  }

  /**
   * A WAV file back to samples in −1..1.
   *
   * Walks the chunk list rather than assuming `data` sits at byte 44 — that much generality is free
   * — but throws [IllegalArgumentException] on any format this app does not write.
   */
  fun decode(bytes: ByteArray): FloatArray {
    require(bytes.size >= HeaderBytes) { "Not a WAV file: ${bytes.size} bytes" }
    require(bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WAVE") {
      "Not a WAV file"
    }

    var formatSeen = false
    var sampleRate = CaptureSampleRate
    var dataAt = -1
    var dataBytes = 0

    var at = 12
    while (at + 8 <= bytes.size) {
      val id = bytes.ascii(at, 4)
      val size = bytes.intLe(at + 4)
      val body = at + 8
      when (id) {
        "fmt " -> {
          require(size >= 16 && body + 16 <= bytes.size) { "Truncated fmt chunk" }
          require(bytes.shortLe(body) == 1) { "Not PCM" }
          require(bytes.shortLe(body + 2) == 1) { "Not mono" }
          sampleRate = bytes.intLe(body + 4)
          require(sampleRate == CaptureSampleRate || sampleRate == LegacySampleRate) {
            "Not ${CaptureSampleRate} Hz"
          }
          require(bytes.shortLe(body + 14) == 16) { "Not 16-bit" }
          formatSeen = true
        }

        "data" -> {
          dataAt = body
          dataBytes = size
        }
      }
      // Chunks are word-aligned; an odd size carries one pad byte.
      at = body + size + (size and 1)
    }

    require(formatSeen && dataAt >= 0) { "Missing fmt or data chunk" }
    require(dataAt + dataBytes <= bytes.size) { "Truncated data chunk" }

    val samples = FloatArray(dataBytes / BytesPerSample)
    for (i in samples.indices) {
      samples[i] = bytes.shortLe(dataAt + i * BytesPerSample) / Short.MAX_VALUE.toFloat()
    }
    return if (sampleRate == LegacySampleRate) doubled(samples) else samples
  }

  /**
   * Twice as many samples, each new one halfway between its neighbours — see [LegacySampleRate].
   * The last sample has no neighbour after it, so it is held.
   */
  private fun doubled(samples: FloatArray): FloatArray =
      FloatArray(samples.size * 2) { i ->
        val before = samples[i / 2]
        if (i % 2 == 0) before else (before + samples[minOf(i / 2 + 1, samples.lastIndex)]) / 2f
      }

  // ── Little-endian plumbing ──────────────────────────────────────────────

  private fun ByteArray.putAscii(at: Int, text: String) {
    for (i in text.indices) this[at + i] = text[i].code.toByte()
  }

  private fun ByteArray.ascii(at: Int, count: Int): String =
      String(this, at, count, Charsets.US_ASCII)

  private fun ByteArray.putIntLe(at: Int, value: Int) {
    this[at] = (value and 0xFF).toByte()
    this[at + 1] = (value shr 8 and 0xFF).toByte()
    this[at + 2] = (value shr 16 and 0xFF).toByte()
    this[at + 3] = (value shr 24 and 0xFF).toByte()
  }

  private fun ByteArray.intLe(at: Int): Int =
      (this[at].toInt() and 0xFF) or
          (this[at + 1].toInt() and 0xFF shl 8) or
          (this[at + 2].toInt() and 0xFF shl 16) or
          (this[at + 3].toInt() and 0xFF shl 24)

  private fun ByteArray.putShortLe(at: Int, value: Int) {
    this[at] = (value and 0xFF).toByte()
    this[at + 1] = (value shr 8 and 0xFF).toByte()
  }

  private fun ByteArray.shortLe(at: Int): Int =
      ((this[at].toInt() and 0xFF) or (this[at + 1].toInt() shl 8)).toShort().toInt()
}
