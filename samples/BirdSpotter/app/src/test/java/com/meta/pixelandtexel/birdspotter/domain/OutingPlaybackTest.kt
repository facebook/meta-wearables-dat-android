/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The edit decision list a saved outing plays back through: segment samples where a segment covers
 * the playhead, zeros where none does, and an end that means it.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class OutingPlaybackTest {

  @Test
  fun read_insideASegmentIsItsSamples() {
    val playback = OutingPlayback(
        segments = listOf(PlaybackSegment(offsetMs = 0, samples = ramp(1600))),
        totalMs = 100,
    )
    val buffer = FloatArray(100)

    val valid = playback.read(fromFrame = 100, into = buffer)

    assertEquals(100, valid)
    assertEquals(100f, buffer[0], 0.0001f)
    assertEquals(199f, buffer[99], 0.0001f)
  }

  @Test
  fun read_aGapIsZeros() {
    // 100 ms of sound, 100 ms of nothing, 100 ms of sound.
    val playback = OutingPlayback(
        segments =
            listOf(
                PlaybackSegment(offsetMs = 0, samples = ramp(1600)),
                PlaybackSegment(offsetMs = 200, samples = ramp(1600)),
            ),
        totalMs = 300,
    )
    val buffer = FloatArray(1600)

    // Read the gap exactly: frames 1600..3200.
    val valid = playback.read(fromFrame = 1600, into = buffer)

    assertEquals(1600, valid)
    assertEquals(0f, buffer[0], 0.0001f)
    assertEquals(0f, buffer[1599], 0.0001f)
  }

  @Test
  fun read_acrossASeamHearsBothSegments() {
    // Adjacent segments — a handover at one sample boundary. Full-scale-ish values, so
    // the makeup gain stays at 1 and the seam is the only thing under test.
    val playback = OutingPlayback(
        segments =
            listOf(
                PlaybackSegment(offsetMs = 0, samples = FloatArray(1600) { 0.9f }),
                PlaybackSegment(offsetMs = 100, samples = FloatArray(1600) { 0.45f }),
            ),
        totalMs = 200,
    )
    val buffer = FloatArray(200)

    playback.read(fromFrame = 1500, into = buffer)

    assertEquals(0.9f, buffer[99], 0.0001f)
    assertEquals(0.45f, buffer[100], 0.0001f)
  }

  @Test
  fun gain_liftsAQuietRecordingToAudible() {
    // An unprocessed room capture: peak two orders under full scale. The lift is real
    // but capped — quiet becomes listenable, silence never becomes a wall of hiss.
    val playback = OutingPlayback(
        segments = listOf(PlaybackSegment(offsetMs = 0, samples = FloatArray(1600) { 0.01f })),
        totalMs = 100,
    )
    val buffer = FloatArray(100)

    playback.read(fromFrame = 0, into = buffer)

    assertEquals(PlaybackMaxGain, playback.gain, 0.0001f)
    assertEquals(0.32f, buffer[0], 0.0001f)
  }

  @Test
  fun gain_isNotPinnedByALoneTransient() {
    // A quiet walk with one knock of the phone against a table. The percentile reads
    // the walk, not the knock — and the knock, lifted past full scale, clips to it.
    val samples = FloatArray(16000) { 0.01f }
    samples[100] = 0.9f
    val playback = OutingPlayback(
        segments = listOf(PlaybackSegment(offsetMs = 0, samples = samples)),
        totalMs = 1000,
    )
    val buffer = FloatArray(200)

    playback.read(fromFrame = 0, into = buffer)

    assertEquals(PlaybackMaxGain, playback.gain, 0.0001f)
    assertEquals(0.32f, buffer[0], 0.0001f)
    assertEquals(1f, buffer[100], 0.0001f)
  }

  @Test
  fun gain_neverTurnsARecordingDown() {
    // A hot capture plays as captured — the distortion is the recording's fact.
    val playback = OutingPlayback(
        segments = listOf(PlaybackSegment(offsetMs = 0, samples = FloatArray(1600) { 1f })),
        totalMs = 100,
    )
    val buffer = FloatArray(100)

    playback.read(fromFrame = 0, into = buffer)

    assertEquals(1f, playback.gain, 0.0001f)
    assertEquals(1f, buffer[0], 0.0001f)
  }

  @Test
  fun read_shortensAtTheEndAndThenAnswersZero() {
    val playback = OutingPlayback(
        segments = listOf(PlaybackSegment(offsetMs = 0, samples = ramp(1600))),
        totalMs = 100,
    )
    val buffer = FloatArray(1200)

    // 1600 frames of outing, read from 1200: only 400 remain.
    assertEquals(400, playback.read(fromFrame = 1200, into = buffer))
    // At the end there is nothing left — which is how a player knows it played out.
    assertEquals(0, playback.read(fromFrame = 1600, into = buffer))
  }

  @Test
  fun totalFrames_isTheLongerOfClockAndAudio() {
    // The outing's clock ran to 300 ms; the audio stops at 100 ms. The silence is real.
    val clockLonger = OutingPlayback(
        segments = listOf(PlaybackSegment(offsetMs = 0, samples = ramp(1600))),
        totalMs = 300,
    )
    assertEquals(4800, clockLonger.totalFrames)

    // A duration recorded shy of the audio must not cut the audio off.
    val audioLonger = OutingPlayback(
        segments = listOf(PlaybackSegment(offsetMs = 0, samples = ramp(1600))),
        totalMs = 50,
    )
    assertEquals(1600, audioLonger.totalFrames)
  }

  @Test
  fun read_beyondTheLastSegmentIsSilenceUntilTheClockRunsOut() {
    val playback = OutingPlayback(
        segments = listOf(PlaybackSegment(offsetMs = 0, samples = FloatArray(160) { 1f })),
        totalMs = 100,
    )
    val buffer = FloatArray(1600)

    val valid = playback.read(fromFrame = 0, into = buffer)

    assertEquals(1600, valid)
    assertEquals(1f, buffer[159], 0.0001f)
    assertEquals(0f, buffer[160], 0.0001f)
    assertEquals(0f, buffer[1599], 0.0001f)
  }

  /** Samples whose value is their absolute frame index — seams and offsets show themselves. */
  private fun ramp(count: Int): FloatArray = FloatArray(count) { it.toFloat() }
}
