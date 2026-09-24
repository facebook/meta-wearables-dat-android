/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a session keeps for Save: one segment per unbroken stretch, split where the microphone
 * changed or genuinely stopped — and never for ordinary buffer jitter, because the recorder and the
 * strip share [placement]'s idea of a gap.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class SessionRecorderTest {

  @Test
  fun record_oneMicrophoneIsOneSegment() {
    val recorder = SessionRecorder()

    // Three chunks of 2048 samples, arriving on an honest clock: 128 ms apart.
    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 1.0)
    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 1.128)
    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 1.256)

    val segments = recorder.segments()
    assertEquals(1, segments.size)
    assertEquals(CaptureSourceKind.PHONE, segments[0].source)
    assertEquals(1_000L, segments[0].offsetMs)
    assertEquals(3 * 2048, segments[0].samples.size)
    // Measured from the samples, not read off the clock a second time.
    assertEquals(3 * 2048 * 1000L / CaptureSampleRate, segments[0].durationMs)
  }

  @Test
  fun record_aSourceChangeSplitsTheTake() {
    val recorder = SessionRecorder()

    recorder.record(chunk(2048, CaptureSourceKind.GLASSES), atSeconds = 0.5)
    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 0.628)

    val segments = recorder.segments()
    assertEquals(2, segments.size)
    assertEquals(CaptureSourceKind.GLASSES, segments[0].source)
    assertEquals(CaptureSourceKind.PHONE, segments[1].source)
    // The incoming microphone starts on its own clock reading.
    assertEquals(628L, segments[1].offsetMs)
  }

  @Test
  fun record_aRealGapSplitsTheTake() {
    val recorder = SessionRecorder()

    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 0.0)
    // The microphone stopped for four seconds — far past the jitter allowance.
    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 4.128)

    val segments = recorder.segments()
    assertEquals(2, segments.size)
    assertEquals(0L, segments[0].offsetMs)
    assertEquals(4_128L, segments[1].offsetMs)
    // The gap is the distance between the rows — no zeros were written into either.
    assertEquals(2048, segments[0].samples.size)
    assertEquals(2048, segments[1].samples.size)
  }

  @Test
  fun record_bufferJitterDoesNotSplit() {
    val recorder = SessionRecorder()

    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 0.0)
    // 60 ms late — a microphone handing over buffers when it feels like it.
    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 0.188)

    assertEquals(1, recorder.segments().size)
  }

  @Test
  fun segments_answersTheSameTwice() {
    val recorder = SessionRecorder()
    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 0.0)

    // Save can fail and be retried; reading the segments must consume nothing.
    val first = recorder.segments()
    val second = recorder.segments()

    assertEquals(1, first.size)
    assertEquals(1, second.size)
    assertEquals(first[0].samples.size, second[0].samples.size)
    assertEquals(first[0].offsetMs, second[0].offsetMs)
  }

  @Test
  fun reset_forgetsTheSession() {
    val recorder = SessionRecorder()
    recorder.record(chunk(2048, CaptureSourceKind.PHONE), atSeconds = 0.0)

    recorder.reset()

    assertTrue(recorder.segments().isEmpty())
  }

  private fun chunk(samples: Int, source: CaptureSourceKind) = AudioChunk(
      samples = FloatArray(samples) { 0.25f },
      source = source,
  )
}
