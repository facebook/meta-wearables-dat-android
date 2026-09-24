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
 * The sonogram ramp: that it is magma, that it reaches both ends, and that it answers for every
 * byte a magnitude can be.
 *
 * The point of these is **parity with the seed pipeline**. If a stop is ever nudged, the live strip
 * stops matching the catalog's rendered PNGs — which is the whole reason the palette exists — and
 * the endpoints below are what would catch it.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class SonogramPaletteTest {

  @Test
  fun silenceIsBlack() {
    assertEquals(SonogramColour(0, 0, 0), SonogramPalette.colour(0.0))
  }

  @Test
  fun theLoudestIsMagmasPaleYellow() {
    assertEquals(SonogramColour(253, 253, 243), SonogramPalette.colour(1.0))
  }

  @Test
  fun aStopIsReturnedExactly() {
    // The fourth stop, which is where magma turns from purple into red.
    assertEquals(SonogramColour(155, 46, 101), SonogramPalette.colour(0.3522))
  }

  @Test
  fun betweenTwoStops_isMixedFromBoth() {
    // Exactly halfway from the black ground to the first stop, which lands blue on 52.5 —
    // deliberately a tie, so this also pins the two platforms to the same rounding.
    assertEquals(SonogramColour(5, 7, 53), SonogramPalette.colour(0.0503))
  }

  @Test
  fun outsideTheRange_clampsToAnEnd() {
    // A magnitude is normalised long before it reaches here; a strip needs an answer anyway.
    assertEquals(SonogramPalette.colour(0.0), SonogramPalette.colour(-1.0))
    assertEquals(SonogramPalette.colour(1.0), SonogramPalette.colour(4.0))
  }

  @Test
  fun theRampCoversEveryByte() {
    val ramp = SonogramPalette.ramp()

    assertEquals(256, ramp.size)
    assertEquals(SonogramPalette.colour(0.0), ramp.first())
    assertEquals(SonogramPalette.colour(1.0), ramp.last())
  }

  @Test
  fun theRampOnlyGetsBrighter() {
    // Magma is perceptually monotone, and a spectrogram whose ramp doubled back would read a
    // loud column as quieter than the one beside it.
    val brightness = SonogramPalette.ramp().map { it.red + it.green + it.blue }

    assertTrue(brightness.zipWithNext().all { (a, b) -> a <= b })
  }

  @Test
  fun theStopsAreOrderedAndSpanTheWholeRange() {
    val levels = SonogramPalette.stops.map { it.first }

    assertEquals(0.0, levels.first(), 0.0)
    assertEquals(1.0, levels.last(), 0.0)
    assertTrue(levels.zipWithNext().all { (a, b) -> a < b })
  }
}
