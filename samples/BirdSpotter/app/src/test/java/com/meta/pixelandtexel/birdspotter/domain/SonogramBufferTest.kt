/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ring the session's strip is drawn from: what it remembers, what it has forgotten, and what a
 * gap in the audio does to it.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class SonogramBufferTest {

  // ── Writing and reading ────────────────────────────────────────────────

  @Test
  fun append_countsColumnsForever() {
    val buffer = SonogramBuffer(capacity = 4)

    repeat(6) { buffer.append(column(1f)) }

    // Absolute indices: column 0 stays column 0 even once it has been overwritten.
    assertEquals(6, buffer.count)
    assertEquals(2, buffer.oldest)
  }

  @Test
  fun magnitude_answersSilenceOutsideTheSession() {
    val buffer = SonogramBuffer(capacity = 4)
    buffer.append(column(1f))

    // The strip scrolls past both ends of a session and drawing needs an answer, not a trap.
    assertEquals(0f, buffer.magnitude(-1, 0), 0.0001f)
    assertEquals(0f, buffer.magnitude(5, 0), 0.0001f)
    assertEquals(1f, buffer.magnitude(0, 0), 0.0001f)
  }

  @Test
  fun level_answersSilenceOutsideTheSession() {
    val buffer = SonogramBuffer(capacity = 4)
    buffer.append(column(1f, level = 0.5f))

    // The waveform scrolls past both ends of the session exactly as the sonogram does.
    assertEquals(0f, buffer.level(-1), 0.0001f)
    assertEquals(0f, buffer.level(5), 0.0001f)
    assertEquals(0.5f, buffer.level(0), 0.005f)
  }

  // ── What the live trace reads ──────────────────────────────────────────

  @Test
  fun recent_averagesTheNewestColumns() {
    val buffer = SonogramBuffer(capacity = 100)
    buffer.append(column(0f, level = 0f))
    buffer.append(column(1f, level = 1f))
    buffer.append(column(0.5f, level = 0.5f))

    // The two newest, meaned — the trace's whole smoothing, and it is a fact about the data
    // rather than an animation with a clock in it.
    assertEquals(0.75f, buffer.recentBins(2)[0], 0.005f)
    assertEquals(0.75f, buffer.recentLevel(2), 0.005f)
  }

  @Test
  fun recent_takesWhatThereIsWhenTheSessionIsYounger() {
    val buffer = SonogramBuffer(capacity = 100)
    buffer.append(column(1f, level = 1f))

    // Asked for four columns into a session one column old, the mean is over the one that
    // happened — not over three imaginary silences that would drag it to a quarter.
    assertEquals(1f, buffer.recentBins(4)[0], 0.005f)
    assertEquals(1f, buffer.recentLevel(4), 0.005f)
  }

  @Test
  fun recent_answersSilenceForASessionWithNothingInIt() {
    val buffer = SonogramBuffer(capacity = 100)

    // A flat trace, which is the honest picture of a microphone that has not opened yet.
    assertEquals(0f, buffer.recentBins(4)[0], 0.0001f)
    assertEquals(0f, buffer.recentLevel(4), 0.0001f)
  }

  // ── Gaps ───────────────────────────────────────────────────────────────

  @Test
  fun advance_leavesSilenceWhereTheAudioStopped() {
    val buffer = SonogramBuffer(capacity = 100)
    buffer.append(column(1f))

    buffer.advance(5)
    buffer.append(column(1f))

    // The columns either side of a dropout must not end up adjacent — the session did not get
    // shorter because the microphone stopped.
    assertEquals(6, buffer.count)
    assertEquals(1f, buffer.magnitude(0, 0), 0.0001f)
    for (silent in 1 until 5) {
      assertEquals(0f, buffer.magnitude(silent, 0), 0.0001f)
    }
    assertEquals(1f, buffer.magnitude(5, 0), 0.0001f)
  }

  @Test
  fun advance_clearsWhatTheRingWasStillHolding() {
    val buffer = SonogramBuffer(capacity = 4)
    repeat(4) { buffer.append(column(1f)) }

    // Column 5 lands in the slot column 1 used, so a skip that only moved the head would draw
    // a buffer-old song inside the silence.
    buffer.advance(6)

    assertEquals(0f, buffer.magnitude(5, 0), 0.0001f)
    assertEquals(6, buffer.count)
  }

  @Test
  fun advance_neverMovesBackwards() {
    val buffer = SonogramBuffer(capacity = 100)
    repeat(10) { buffer.append(column(1f)) }

    buffer.advance(3)

    // A written column is a column that happened.
    assertEquals(10, buffer.count)
    assertEquals(1f, buffer.magnitude(9, 0), 0.0001f)
  }

  @Test
  fun reset_forgetsTheSession() {
    val buffer = SonogramBuffer(capacity = 4)
    buffer.append(column(1f))
    buffer.advance(3)

    buffer.reset()

    assertEquals(0, buffer.count)
    assertEquals(0f, buffer.magnitude(0, 0), 0.0001f)
  }

  // ── Keeping it ─────────────────────────────────────────────────────────

  @Test
  fun encoded_survivesTheRoundTrip() {
    val buffer = SonogramBuffer(capacity = 8)
    buffer.append(column(1f, level = 0.5f))
    buffer.append(column(0.25f, level = 1f))
    // A gap, which has to come back just as dark as it went in.
    buffer.advance(4)
    buffer.append(column(0.75f, level = 0.25f))

    val restored = SonogramBuffer.decoded(buffer.encoded())!!

    assertEquals(buffer.count, restored.count)
    assertEquals(buffer.oldest, restored.oldest)
    for (column in 0 until buffer.count) {
      assertEquals(buffer.magnitude(column, 0), restored.magnitude(column, 0), 0.0001f)
      assertEquals(buffer.level(column), restored.level(column), 0.0001f)
    }
  }

  @Test
  fun encoded_keepsWhatTheRingStillHolds() {
    val buffer = SonogramBuffer(capacity = 4)
    for (i in 0 until 6) buffer.append(column((i + 1) / 6f))

    val restored = SonogramBuffer.decoded(buffer.encoded())!!

    // The two overwritten columns are gone from the file too, and column 2 is still column 2 —
    // which is what lets an event's timestamp keep landing where it did during the session.
    assertEquals(2, restored.oldest)
    assertEquals(6, restored.count)
    for (column in 2 until 6) {
      assertEquals(buffer.magnitude(column, 0), restored.magnitude(column, 0), 0.0001f)
    }
    assertEquals(0f, restored.magnitude(1, 0), 0.0001f)
  }

  @Test
  fun decoded_refusesBytesThatAreNotAStrip() {
    assertNull(SonogramBuffer.decoded("not a sonogram at all".toByteArray()))
  }

  @Test
  fun decoded_refusesAVersionItDoesNotKnow() {
    val bytes = SonogramBuffer(capacity = 4).encoded()
    bytes[4] = 99.toByte()

    // A strip from a build that laid the bytes out differently is recomputed, not drawn wrong.
    assertNull(SonogramBuffer.decoded(bytes))
  }

  @Test
  fun decoded_refusesAFileThatWasCutShort() {
    val buffer = SonogramBuffer(capacity = 4)
    repeat(4) { buffer.append(column(1f)) }
    val bytes = buffer.encoded()

    // What a crash between `write` and the file being flushed leaves behind.
    assertNull(SonogramBuffer.decoded(bytes.copyOf(bytes.size - 10)))
  }

  private fun column(magnitude: Float, level: Float = 0f) =
      SonogramColumn(FloatArray(SonogramBins) { magnitude }, level)
}
