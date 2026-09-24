/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.location

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The elevation of the camera's aim, out of a rotation matrix, for the poses a phone is actually
 * held in.
 *
 * **This one has no mirror, and is meant not to** — the same carve-out [SystemHeadingProviderTest]
 * takes. The elevation is read out of a rotation matrix here, which is one line of sensor plumbing
 * rather than a piece of the design — see the platform-plumbing carve-out.
 *
 * No Robolectric here, unlike the bearing's test: `elevationFrom` touches no framework static, only
 * the one matrix cell and `asin`.
 *
 * The matrix is built the way `getRotationMatrixFromVector` builds one — device coordinates to
 * world coordinates, world being **X east, Y north, Z up** — so its columns are the device's own
 * axes written down as compass directions. Device X is the right edge, Y the top edge, Z out
 * through the screen; the camera looks the other way, out of the back.
 */
class SystemGazeProviderTest {

  /**
   * A matrix from the three device axes, each given as (east, north, up).
   *
   * The array `getRotationMatrixFromVector` fills is row-major, so the axes go in as columns.
   */
  private fun matrix(
      right: Triple<Float, Float, Float>,
      top: Triple<Float, Float, Float>,
      out: Triple<Float, Float, Float>,
  ) = floatArrayOf(
      right.first,
      top.first,
      out.first,
      right.second,
      top.second,
      out.second,
      right.third,
      top.third,
      out.third,
  )

  private val east = Triple(1f, 0f, 0f)
  private val north = Triple(0f, 1f, 0f)
  private val south = Triple(0f, -1f, 0f)
  private val up = Triple(0f, 0f, 1f)
  private val down = Triple(0f, 0f, -1f)

  @Test
  fun flatOnItsBack_isAimedAtTheGround() {
    // Screen at the sky, so the camera is looking at the watcher's shoes.
    assertEquals(-90.0, elevationFrom(matrix(right = east, top = north, out = up)), Tolerance)
  }

  @Test
  fun heldUpright_isAimedAtTheHorizon() {
    assertEquals(0.0, elevationFrom(matrix(right = east, top = up, out = south)), Tolerance)
  }

  @Test
  fun faceDown_isAimedAtTheSky() {
    assertEquals(90.0, elevationFrom(matrix(right = east, top = south, out = down)), Tolerance)
  }

  /** Tipping the phone back from upright walks the aim up degree for degree. */
  @Test
  fun tippingBack_raisesTheAim() {
    for (pitch in 0..80 step 5) {
      val radians = Math.toRadians(pitch.toDouble())
      val cos = kotlin.math.cos(radians).toFloat()
      val sin = kotlin.math.sin(radians).toFloat()
      // Pitched about the device's own X axis from upright, the back of the phone swinging up
      // from the southern horizon towards overhead.
      val rotation = matrix(
          right = east,
          top = Triple(0f, -sin, cos),
          out = Triple(0f, -cos, -sin),
      )
      assertEquals("at $pitch°", pitch.toDouble(), elevationFrom(rotation), Tolerance)
    }
  }

  /**
   * A fused rotation vector can land a hair past a unit axis, and `asin` of that is `NaN` — which
   * would blank the chip on one bad sample rather than on a phone that cannot answer.
   */
  @Test
  fun aReadingPastVertical_isClampedRatherThanNaN() {
    val overshoot = matrix(right = east, top = north, out = Triple(0f, 0f, 1.0001f))
    assertEquals(-90.0, elevationFrom(overshoot), Tolerance)
  }

  /** Half a degree. The chip reads one of five bands; this is well inside any of them. */
  private companion object {
    const val Tolerance = 0.5
  }
}
