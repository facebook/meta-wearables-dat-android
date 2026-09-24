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
 * The travelling waves the waveform reading is drawn from: how many there are, what makes them
 * move, what makes them tall, and what they draw when there is nothing to draw.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class LiveWaveformTest {

  @Test
  fun curves_areOnePerBandAtFullResolution() {
    val curves = LiveWaveform.curves(bins = flat(0.5f), level = 1f, phase = 0.0)

    assertEquals(LiveWaveform.CurveCount, curves.size)
    curves.forEach { assertEquals(LiveWaveform.Resolution, it.size) }
  }

  @Test
  fun curves_carryTheWavelengthsAcrossTheWidth() {
    val curve = LiveWaveform.curves(bins = flat(0.5f), level = 1f, phase = 0.5)[0]

    // Four waves cross zero twice each. One of those eight can land exactly on the last sample,
    // where the envelope has already taken the value to zero — so seven is the honest floor.
    val drawn = curve.filter { it != 0f }
    val crossings = drawn.zipWithNext().count { (a, b) -> (a > 0f) != (b > 0f) }
    assertTrue("crossings were $crossings", crossings >= 2 * LiveWaveform.Wavelengths - 1)
    assertTrue("crossings were $crossings", crossings <= 2 * LiveWaveform.Wavelengths)
  }

  @Test
  fun curves_dieAtBothEnds() {
    val curves = LiveWaveform.curves(bins = flat(1f), level = 1f, phase = 1.7)

    // The envelope, not the sine: whatever the phase, the waves reach the strip's edges at
    // nothing, so the shape ends rather than being cut off.
    curves.forEach { curve ->
      assertEquals(0f, curve.first(), 0.0001f)
      assertEquals(0f, curve.last(), 0.0001f)
    }
  }

  @Test
  fun curves_travelWithThePhase() {
    val now = LiveWaveform.curves(bins = flat(0.5f), level = 1f, phase = 0.0)
    val later = LiveWaveform.curves(bins = flat(0.5f), level = 1f, phase = 1.0)

    // The clock is the only thing that moves them, and every curve has to move — three that
    // travelled together would read as one striped object.
    now.indices.forEach { curve ->
      val moved =
          now[curve].indices.any { i ->
            kotlin.math.abs(now[curve][i] - later[curve][i]) > 0.05f
          }
      assertTrue("curve $curve did not move", moved)
    }
  }

  @Test
  fun curves_doNotAllTravelTheSameWay() {
    // Three curves travelling the same way read as one thing scrolling, and a scroll is a claim
    // about time that this reading does not have — the sonogram beside it is the one with a time
    // axis. Mixed signs are what keep the picture churning in place instead of running backwards.
    val directions = (0 until LiveWaveform.CurveCount).map { directionOfCurve(it) }

    assertTrue("a curve stood still: $directions", directions.none { it == 0 })
    assertTrue("every curve travelled the same way: $directions", directions.toSet().size > 1)
  }

  /**
   * Which way curve [index] travels, as the sign of the offset that best re-aligns it with itself
   * half a second on. Measured across the middle half of the strip, where the envelope is flattest
   * and the travelling sine is all that is moving.
   */
  private fun directionOfCurve(index: Int): Int {
    val now = LiveWaveform.curves(bins = flat(0.5f), level = 1f, phase = 0.0)[index]
    val later = LiveWaveform.curves(bins = flat(0.5f), level = 1f, phase = 0.5)[index]
    val middle = (LiveWaveform.Resolution / 4) until (LiveWaveform.Resolution * 3 / 4)

    fun misfit(offset: Int) = middle.sumOf { i ->
      val difference = later[i] - now[i + offset]
      (difference * difference).toDouble()
    }

    // ±8 samples covers a quarter of a wave at this resolution, which is more travel than half a
    // second buys any of the three — so the best alignment is a real match, not a range edge.
    return (-8..8).minByOrNull { misfit(it) }!!.let { Integer.signum(it) }
  }

  @Test
  fun curves_scaleWithTheLevel() {
    val loud = LiveWaveform.curves(bins = flat(0.5f), level = 1f, phase = 0.4)
    val quiet = LiveWaveform.curves(bins = flat(0.5f), level = 0.25f, phase = 0.4)

    // The whole picture swells and falls with the room, and nothing about its shape changes as
    // it does — the phase is the same, so this is the same drawing at a quarter of the height.
    loud.indices.forEach { curve ->
      loud[curve].indices.forEach { i ->
        assertEquals(loud[curve][i] * 0.25f, quiet[curve][i], 0.0001f)
      }
    }
  }

  @Test
  fun curves_followTheirOwnBandOfTheSpectrum() {
    // Everything in the top third — a bird, not a voice.
    val bins = FloatArray(SonogramBins) { bin -> if (bin > SonogramBins * 2 / 3) 1f else 0.05f }

    val curves = LiveWaveform.curves(bins = bins, level = 1f, phase = 0.0)
    val reach = curves.map { curve -> curve.maxOf { kotlin.math.abs(it) } }

    // The third curve is the one that stands up. Three curves that only differed in phase would
    // be decoration; these carry the three numbers a listener would describe the sound with.
    assertTrue("reach was $reach", reach[2] > reach[0])
    assertTrue("reach was $reach", reach[2] > reach[1])
  }

  @Test
  fun curves_areFlatWithNothingToDraw() {
    // Three ways to have nothing: no column at all, a silent one, and one whose bins are all
    // zero. All three are a microphone that is open and hearing nothing.
    assertTrue(LiveWaveform.curves(FloatArray(0), 1f, 0.0).all { c -> c.all { it == 0f } })
    assertTrue(LiveWaveform.curves(flat(0.5f), 0f, 0.0).all { c -> c.all { it == 0f } })
    assertTrue(
        LiveWaveform.curves(FloatArray(SonogramBins), 1f, 0.0).all { c -> c.all { it == 0f } },
    )
  }

  @Test
  fun bandLevels_splitTheSpectrumInThirds() {
    val bins =
        FloatArray(SonogramBins) { bin ->
          when {
            bin < SonogramBins / 3 -> 0.9f
            bin < SonogramBins * 2 / 3 -> 0.6f
            else -> 0.3f
          }
        }

    val bands = LiveWaveform.bandLevels(bins)

    assertEquals(LiveWaveform.CurveCount, bands.size)
    assertEquals(0.9f, bands[0], 0.02f)
    assertEquals(0.6f, bands[1], 0.02f)
    assertEquals(0.3f, bands[2], 0.02f)
  }

  /** A spectrum with the same thing in every bin. */
  private fun flat(magnitude: Float) = FloatArray(SonogramBins) { magnitude }
}
