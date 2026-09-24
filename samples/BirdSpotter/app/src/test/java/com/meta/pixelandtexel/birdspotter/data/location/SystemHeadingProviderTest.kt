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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The bearing, out of a rotation matrix, for the poses a phone is actually held in.
 *
 * **This one has no mirror, and is meant not to.** `SensorManager` answers with an orientation for
 * a device lying flat, so this side has to work out which axis the question was actually about.
 * That work belongs to the sensor, not to the design, so it is tested where it lives — see the
 * platform-plumbing carve-out.
 *
 * Robolectric, because `SensorManager.getOrientation` and `remapCoordinateSystem` are framework
 * statics — pure arithmetic, but arithmetic that lives in `android.jar`.
 *
 * Each matrix below is built the way `getRotationMatrixFromVector` builds one: it takes a vector in
 * device coordinates to world coordinates, world being **X east, Y north, Z up**, so its columns
 * are the device's own axes written down as compass directions. Device X is the right edge, Y the
 * top edge, Z out through the screen.
 */
@RunWith(RobolectricTestRunner::class)
class SystemHeadingProviderTest {

  private val remapped = FloatArray(9)
  private val orientation = FloatArray(3)

  private fun bearing(rotation: FloatArray): Double = bearingFrom(rotation, remapped, orientation)

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
  private val west = Triple(-1f, 0f, 0f)
  private val north = Triple(0f, 1f, 0f)
  private val south = Triple(0f, -1f, 0f)
  private val up = Triple(0f, 0f, 1f)

  @Test
  fun flatOnItsBack_readsTheTopEdge() {
    assertEquals(0.0, bearing(matrix(right = east, top = north, out = up)), Tolerance)
    assertEquals(90.0, bearing(matrix(right = south, top = east, out = up)), Tolerance)
    assertEquals(180.0, bearing(matrix(right = west, top = south, out = up)), Tolerance)
  }

  /**
   * The bug this fixes. Held upright the top edge points at the sky, so the flat-phone reading has
   * nothing left to measure — `atan2(0, 0)`, which is a confident, wrong `N` whichever way the
   * watcher is standing. Upright, the question is where the *back* of the phone points.
   */
  @Test
  fun heldUpright_readsTheBackOfThePhone() {
    assertEquals(0.0, bearing(matrix(right = east, top = up, out = south)), Tolerance)
    assertEquals(90.0, bearing(matrix(right = south, top = up, out = west)), Tolerance)
    assertEquals(180.0, bearing(matrix(right = west, top = up, out = north)), Tolerance)
  }

  /**
   * And the two agree either side of the changeover, which is what lets it happen without
   * hysteresis: the same phone, tipped up by degrees, keeps naming the same compass point.
   */
  @Test
  fun tippingUp_doesNotChangeTheBearing() {
    for (pitch in 0..90 step 5) {
      val radians = Math.toRadians(pitch.toDouble())
      val cos = kotlin.math.cos(radians).toFloat()
      val sin = kotlin.math.sin(radians).toFloat()
      // Pitched about the device's own X axis, top edge swinging up from north to overhead.
      val rotation = matrix(
          right = east,
          top = Triple(0f, cos, sin),
          out = Triple(0f, -sin, cos),
      )
      assertEquals("at $pitch°", 0.0, bearing(rotation), Tolerance)
    }
  }

  /** Half a degree. The plate reads one of eight points; this is well inside any of them. */
  private companion object {
    const val Tolerance = 0.5
  }
}
