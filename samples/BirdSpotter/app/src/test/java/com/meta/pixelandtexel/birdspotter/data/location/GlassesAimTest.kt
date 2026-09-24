/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.location

import com.meta.pixelandtexel.birdspotter.domain.GlassesMotionSample
import com.meta.pixelandtexel.birdspotter.domain.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a wearer is aiming, out of one motion sample.
 *
 * **This one is mirrored, unlike [SystemGazeProviderTest].** That test covers a line of sensor
 * plumbing and takes the platform-plumbing carve-out; this covers the geometry both apps derive
 * identically from the same two vectors, which is design and belongs under the testing-parity rule.
 *
 * The poses below are built by hand rather than captured, so each one says what it means: gravity
 * is whichever way *up* is in the glasses' own axes, and the field is whichever way north is from
 * there.
 */
class GlassesAimTest {

  // region Elevation

  @Test
  fun elevation_withALevelHead_readsTheHorizon() {
    // Level: up runs along the glasses' own up axis, and the forward axis lies flat.
    assertEquals(0.0, elevationFromAcceleration(Vector3(0.0, Gravity, 0.0))!!, Tolerance)
  }

  @Test
  fun elevation_withAHeadTiltedUp_readsAboveTheHorizon() {
    // Pitched up 30°: up leans back over the wearer, which tips the forward axis at the sky.
    val up = Vector3(0.0, Gravity * 0.8660254, Gravity * -0.5)

    assertEquals(30.0, elevationFromAcceleration(up)!!, Tolerance)
  }

  @Test
  fun elevation_withAHeadTiltedDown_readsBelowTheHorizon() {
    // The mirror of the pose above, and the reading mirrors with it. This is the pose a wearer
    // reading their phone holds, and the one the forward axis' sign was settled against.
    val up = Vector3(0.0, Gravity * 0.8660254, Gravity * 0.5)

    assertEquals(-30.0, elevationFromAcceleration(up)!!, Tolerance)
  }

  @Test
  fun elevation_inFreefall_saysNothing() {
    // No gravity, no up, no answer — the one case where there is genuinely nothing to report
    // rather than something to report badly.
    assertNull(elevationFromAcceleration(Vector3(0.0, 0.0, 0.0)))
  }

  // endregion

  // region Bearing

  @Test
  fun bearing_facingNorth_readsNorth() {
    // Level head, field running out along the forward axis: the wearer is looking up the
    // field lines, which is what facing north is.
    val sample = GlassesMotionSample(
        acceleration = Vector3(0.0, Gravity, 0.0),
        magneticField = Vector3(0.0, 0.0, -FieldStrength),
    )

    assertEquals(0.0, bearingFromMotion(sample)!!, Tolerance)
  }

  @Test
  fun bearing_facingEast_readsAQuarterTurn() {
    // Same level head, north now off the wearer's left shoulder — so they are facing east.
    val sample = GlassesMotionSample(
        acceleration = Vector3(0.0, Gravity, 0.0),
        magneticField = Vector3(-FieldStrength, 0.0, 0.0),
    )

    assertEquals(90.0, bearingFromMotion(sample)!!, Tolerance)
  }

  @Test
  fun bearing_withATiltedHeadInADippingField_stillReadsNorth() {
    // **The test the whole tilt compensation exists for.** The field dips 60° into the ground
    // at these latitudes and the wearer is looking 30° up, so the raw reading is nowhere near
    // horizontal — and the bearing is still due north, because gravity is what flattens it.
    // Without the compensation this is the sample that reads wildly wrong while looking
    // perfectly reasonable.
    val sample = GlassesMotionSample(
        acceleration = Vector3(0.0, Gravity * 0.8660254, Gravity * -0.5),
        magneticField = Vector3(0.0, -FieldStrength, 0.0),
    )

    assertEquals(0.0, bearingFromMotion(sample)!!, Tolerance)
  }

  @Test
  fun bearing_withNoFieldReported_saysNothing() {
    // Optional at the sensor, so a pair that reports motion without a magnetometer has no
    // compass to offer and says so rather than guessing from gravity alone.
    val sample = GlassesMotionSample(acceleration = Vector3(0.0, Gravity, 0.0))

    assertNull(bearingFromMotion(sample))
  }

  @Test
  fun bearing_lookingStraightUp_saysNothing() {
    // The forward axis is vertical, so it has no shadow on the ground to take a bearing from.
    // *Which way* stops having an answer here rather than getting a wrong one.
    //
    // The field is deliberately left horizontal, so north is perfectly findable and the forward
    // axis is the only thing that has run out — otherwise this would pass for the wrong reason.
    val sample = GlassesMotionSample(
        acceleration = Vector3(0.0, 0.0, -Gravity),
        magneticField = Vector3(FieldStrength, 0.0, 0.0),
    )

    assertNull(bearingFromMotion(sample))
  }

  // endregion

  private companion object {
    /**
     * Roughly what a still pair reads, in m/s². The exact value never matters — only the direction
     * does — but a realistic magnitude keeps the poses honest.
     */
    const val Gravity = 9.81

    /** Roughly the Earth's field in µT. Same story: only the direction is read. */
    const val FieldStrength = 48.0

    /**
     * In degrees: wide enough to absorb the trig constants above being written to seven places
     * rather than exactly, and still four orders of magnitude tighter than the nearest thing that
     * would change an answer — a band boundary 17.5° away.
     */
    const val Tolerance = 1e-4
  }
}
