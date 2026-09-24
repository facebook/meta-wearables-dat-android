/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import kotlinx.coroutines.flow.Flow

/**
 * Three components in the glasses' own axes. Whatever the sample measured, in whatever unit that
 * quantity is measured in — this type carries the triple and nothing else.
 */
data class Vector3(val x: Double, val y: Double, val z: Double)

/**
 * One reading off the glasses' motion sensors.
 *
 * **Two fields, where the sensor offers five.** The pair also report a gyroscope and a fused
 * orientation quaternion, and neither is here: this app asks the IMU exactly two questions — *how
 * high is the wearer looking* and *which way are they facing* — and both are answered by where down
 * is and where magnetic north is. A rate of turn has no reader, and a quaternion is expressed
 * against a reference frame the sensor does not document, which would make it the one value in the
 * app whose meaning nobody could check. Adding either is a line in the data layer the day something
 * needs it.
 *
 * Samples arriving from anything other than the glasses are dropped before they reach here. A
 * single motion feed can interleave two rigid bodies, and averaging a head with a wrist produces a
 * number that describes neither.
 *
 * @property acceleration Proper acceleration in m/s², gravity included — so a still pair reads
 *   roughly `9.81` along whichever axis is pointing at the sky.
 * @property magneticField The magnetic field in µT, or `null` when this pair does not report one.
 *   Optional at the source and therefore optional here: a compass derived from it is best-effort by
 *   construction, which is why [HeadingProvider] is allowed to say nothing.
 */
data class GlassesMotionSample(
    val acceleration: Vector3,
    val magneticField: Vector3? = null,
)

/**
 * The glasses' motion sensors, for as long as something is listening.
 *
 * A cold stream, like every other sense the session opens: collecting starts the sensor and
 * cancelling stops it, so a screen that has gone away is not one still costing the wearer battery.
 *
 * **Not throwing, and never empty by way of an error.** A pair with no session, no IMU, or a
 * capability that refused to attach produces a flow that simply emits nothing — the same shape
 * [HeadingProvider] and [GazeProvider] already use, and for the same reason: there is nothing to
 * tell a watcher about a sensor that isn't there beyond not showing them a reading.
 */
interface GlassesMotionRepository {
  /** Readings as they are measured. */
  fun motionStream(): Flow<GlassesMotionSample>
}
