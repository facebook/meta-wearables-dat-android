/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.splash

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The splash's motion spec, pinned value by value.
 *
 * These look like change-detector tests because they are: the launch moment is one choreography,
 * and it stays one only while the numbers agree. A deliberate retune edits `SplashMotion` and this
 * file together; a one-sided edit fails the suite, which is the point.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class SplashMotionTest {

  @Test
  fun spin_landsOnAWholeNumberOfTurns() {
    // The rings must come to rest in the badge's drawn orientation — hand-drawn blobs
    // ending mid-turn sit visibly askew of the mark the app icon shows.
    assertEquals(0, SplashMotion.spinDegrees % 360)
  }

  @Test
  fun timeline_matchesTheMirroredSpec() {
    assertEquals(800, SplashMotion.spinMillis)
    assertEquals(360, SplashMotion.spinDegrees)
    assertEquals(60, SplashMotion.holdMillis)
    assertEquals(160, SplashMotion.crossfadeMillis)
  }
}
