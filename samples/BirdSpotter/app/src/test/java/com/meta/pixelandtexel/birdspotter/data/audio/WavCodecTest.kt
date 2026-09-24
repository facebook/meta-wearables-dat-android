/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.audio

import com.meta.pixelandtexel.birdspotter.domain.CaptureSampleRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The journal's audio files, written and read by hand: what goes out must come back, and anything
 * this app never writes must be refused rather than guessed at.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class WavCodecTest {

  @Test
  fun roundTrip_returnsTheSamplesItWasGiven() {
    val samples = FloatArray(1000) { i -> kotlin.math.sin(i / 20.0).toFloat() * 0.8f }

    val decoded = WavCodec.decode(WavCodec.encode(samples))

    assertEquals(samples.size, decoded.size)
    for (i in samples.indices) {
      // One quantisation step is the honest cost of 16-bit; anything more is a bug.
      assertEquals(samples[i], decoded[i], 1f / Short.MAX_VALUE)
    }
  }

  @Test
  fun encode_writesTheHeaderASpecReaderExpects() {
    val bytes = WavCodec.encode(FloatArray(8))

    assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
    assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
    assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
    assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
    // 8 samples of 16-bit mono: 16 payload bytes on a 44-byte header.
    assertEquals(44 + 16, bytes.size)
    // The sample rate, little-endian at offset 24.
    val rate =
        (bytes[24].toInt() and 0xFF) or
            (bytes[25].toInt() and 0xFF shl 8) or
            (bytes[26].toInt() and 0xFF shl 16) or
            (bytes[27].toInt() and 0xFF shl 24)
    assertEquals(CaptureSampleRate, rate)
  }

  @Test
  fun decode_clampsWhatOverdrives() {
    val decoded = WavCodec.decode(WavCodec.encode(floatArrayOf(1.5f, -1.5f)))

    assertEquals(1f, decoded[0], 0.001f)
    assertEquals(-1f, decoded[1], 0.001f)
  }

  @Test
  fun decode_refusesWhatTheAppNeverWrites() {
    // Not a WAV at all.
    assertThrows(IllegalArgumentException::class.java) {
      WavCodec.decode(ByteArray(100))
    }

    // A real header at the wrong rate: byte 24 carries the sample rate.
    val wrongRate = WavCodec.encode(FloatArray(8))
    wrongRate[24] = 0x44.toByte()
    wrongRate[25] = 0xAC.toByte()
    assertThrows(IllegalArgumentException::class.java) {
      WavCodec.decode(wrongRate)
    }
  }

  @Test
  fun decode_upsamplesASegmentSavedAtTheOldRate() {
    // Byte 24 carries the sample rate: 8000 is 0x1F40, the rate segments were saved at before
    // the capture rate doubled.
    val legacy = WavCodec.encode(floatArrayOf(0.2f, 0.4f, 0.6f))
    legacy[24] = 0x40.toByte()
    legacy[25] = 0x1F.toByte()

    val decoded = WavCodec.decode(legacy)

    // Doubled, each new sample halfway between its neighbours and the last one held.
    val expected = floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.6f)
    assertEquals(expected.size, decoded.size)
    for (i in expected.indices) {
      assertEquals(expected[i], decoded[i], 0.001f)
    }
  }

  @Test
  fun decode_walksPastAForeignChunk() {
    // A `LIST` chunk between `fmt ` and `data`, the way editors leave metadata behind.
    val plain = WavCodec.encode(floatArrayOf(0.5f, -0.5f))
    val listBody = ByteArray(6)
    val withList = ByteArray(plain.size + 8 + listBody.size)

    plain.copyInto(withList, destinationOffset = 0, startIndex = 0, endIndex = 36)
    "LIST".forEachIndexed { i, c -> withList[36 + i] = c.code.toByte() }
    withList[40] = listBody.size.toByte()
    plain.copyInto(withList, destinationOffset = 36 + 8 + listBody.size, startIndex = 36)

    val decoded = WavCodec.decode(withList)

    assertEquals(2, decoded.size)
    assertEquals(0.5f, decoded[0], 0.001f)
  }
}
