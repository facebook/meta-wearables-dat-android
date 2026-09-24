/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.location

import com.meta.pixelandtexel.birdspotter.domain.GazeProvider
import com.meta.pixelandtexel.birdspotter.domain.GlassesMotionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesMotionSample
import com.meta.pixelandtexel.birdspotter.domain.HeadingProvider
import com.meta.pixelandtexel.birdspotter.domain.Vector3
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile

// region The body frame

/**
 * Which way the glasses look, in their own axes.
 *
 * **Measured, not assumed.** The sensor documents its units — metres per second squared, microtesla
 * — and not which way its axes point on the frames, so this was read off a worn pair rather than
 * inferred. A head held level reports the whole of *g* on **Y** and nothing worth reading on the
 * other two: the sky axis is `+Y`, and the reading is the reaction to gravity rather than gravity
 * itself. The magnetometer says the same thing from the other side — a 53 µT field carrying 46 of
 * it on `-Y` is the local field dipping 60° into the ground, which only lands there if `-Y` is
 * down. **X** stays inside a couple of hundredths of *g* through every pose recorded, which makes
 * it the lateral axis and leaves `±Z` as the one the wearer looks along.
 *
 * `-Z` is forward, and the sign is the half of this that a still head cannot settle. With `+Z` a
 * wearer reading their phone — head down something like 37°, held there for a minute — was reported
 * at `+37°`, past `CanopyDegrees`: the chip said *canopy* while they were looking at their own
 * hand, and said *overhead* at a glance down to the desk.
 *
 * **How to re-check it on a different pair, in one sitting:** put the glasses on, look level at
 * something across the room, and read the gaze chip. *Horizon* means this is still right.
 * *Overhead* or *Ground* with a level head means the forward axis is not `±Z` — swap in the axis
 * that reads zero across every pose. A chip that moves the wrong way when the wearer looks up means
 * the sign is inverted, not the axis, and this constant is the whole of the fix.
 */
private val Forward = Vector3(0.0, 0.0, -1.0)

// endregion

// region What a sample says about where the wearer is aiming

/**
 * How high the wearer is looking, in degrees above the horizon — or `null` when the sample cannot
 * say.
 *
 * **Gravity is the whole instrument, and a still head is what makes it one.** At rest an
 * accelerometer reads the reaction to gravity, which points at the sky, so normalising it gives
 * *up* in the glasses' own axes; how much of the forward axis lies along that is the sine of the
 * angle above the horizon. A head that is turning fast adds its own acceleration to the reading and
 * tilts the answer for as long as the turn lasts — acceptable, because the answer is one of five
 * words and a wearer swinging their head is not reading it.
 *
 * Answers `null` only in freefall, where there is no up to be had. Clamped before the arcsine
 * because a normalised vector can land a hair past ±1 through rounding, and `asin` of that is `NaN`
 * — one bad sample would otherwise blank the chip.
 */
internal fun elevationFromAcceleration(acceleration: Vector3): Double? {
  val up = acceleration.normalized() ?: return null
  return Math.toDegrees(asin(Forward.dot(up).coerceIn(-1.0, 1.0)))
}

/**
 * Which way the wearer is facing, in degrees clockwise from magnetic north — or `null` when the
 * sample cannot say.
 *
 * **Tilt-compensated, because a head is never level.** A magnetometer reports the field in the
 * glasses' own axes, and the field dips steeply into the ground at most latitudes, so the raw
 * reading swings with pitch and roll. Taking gravity as *up*, the horizontal part of the field is
 * north, the cross product of the two is east, and the forward axis flattened into that same plane
 * is the bearing — which is the standard construction and the reason both vectors are needed to
 * answer a question that sounds like it only needs one.
 *
 * **Magnetic north, not true.** Correcting it needs a declination this app has no model for, and
 * the difference is single digits over the demo's ground — which the phone's own compass already
 * treats as the honest answer whenever true north is not available to it. A bearing shown as one of
 * eight points is a 45° sector; single digits do not usually move it.
 *
 * `null` covers every way of not knowing: no field reported, a pair in freefall, a field parallel
 * to gravity (which has no horizontal part to call north), and a wearer looking straight up or
 * straight down — where the forward axis has no horizontal part either, and *which way* stops
 * having an answer rather than getting a wrong one.
 */
internal fun bearingFromMotion(sample: GlassesMotionSample): Double? {
  val field = sample.magneticField ?: return null
  val up = sample.acceleration.normalized() ?: return null
  val north = field.perpendicularTo(up)?.normalized() ?: return null
  val aim = Forward.perpendicularTo(up)?.normalized() ?: return null

  val east = north.cross(up)
  val degrees = Math.toDegrees(atan2(aim.dot(east), aim.dot(north)))
  return if (degrees < 0) degrees + 360 else degrees
}

// endregion

// region The providers

/**
 * The [GazeProvider] backed by the glasses' own IMU.
 *
 * A wearer's head is the thing actually aimed at the bird, which is what makes this the better
 * answer than the phone's attitude whenever it is available — the phone reports where the *phone*
 * is pointing, and a watcher looking up while their hand hangs at their side is a watcher the phone
 * reads as staring at the grass.
 *
 * Samples that cannot answer are dropped rather than published as a value, so the chip keeps the
 * last stratum it had instead of flickering to nothing at the top of a swing.
 */
class GlassesGazeProvider(
    private val motion: GlassesMotionRepository,
) : GazeProvider {

  override fun gazeStream(): Flow<Double> =
      motion.motionStream().mapNotNull { elevationFromAcceleration(it.acceleration) }
}

/**
 * The [HeadingProvider] backed by the glasses' own IMU.
 *
 * The same argument the gaze provider makes: the bearing worth stamping on a photograph is the one
 * the camera that took it was pointing along, and on a glasses capture that camera is on the
 * wearer's face.
 *
 * **A pair with no magnetometer ends the stream rather than going quiet on it**, and the difference
 * matters more than it looks. Silence and *there is no compass here* are the same thing to anything
 * waiting on a value, so a pair that reports motion without a field would hold the failover open
 * forever on a bearing that is never coming — and the phone's compass, which was working, would
 * never be asked again. A sample that arrives without a field is a fact about the hardware, so it
 * ends this flow and hands the question back.
 *
 * The other silences are transient and stay silent: the bearing is undefined at the top and bottom
 * of a swing, where *which way* stops having an answer for a moment rather than for good.
 */
class GlassesHeadingProvider(
    private val motion: GlassesMotionRepository,
) : HeadingProvider {

  override fun headingStream(): Flow<Double> =
      motion.motionStream().takeWhile { it.magneticField != null }.mapNotNull(::bearingFromMotion)
}

// endregion

// region Vector arithmetic

/**
 * The operations the two derivations above are built from. Kept here rather than on [Vector3]
 * itself so the domain type stays what it says it is — a triple, in whatever unit the thing it
 * measures is measured in — and the geometry lives with the geometry.
 */
private fun Vector3.length(): Double = sqrt(x * x + y * y + z * z)

/**
 * The same direction at unit length, or `null` for a vector too short to have a direction. The
 * threshold is well under any real reading and well over the rounding noise around zero.
 */
private fun Vector3.normalized(): Vector3? {
  val length = length()
  return if (length > 1e-9) Vector3(x / length, y / length, z / length) else null
}

private fun Vector3.dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z

private fun Vector3.cross(other: Vector3): Vector3 = Vector3(
    y * other.z - z * other.y,
    z * other.x - x * other.z,
    x * other.y - y * other.x,
)

/**
 * This vector with everything along [axis] taken out of it — its shadow on the plane [axis] stands
 * on. `null` when nothing measurable is left, which is what a vector parallel to the axis reduces
 * to.
 */
private fun Vector3.perpendicularTo(axis: Vector3): Vector3? {
  val along = dot(axis)
  val flattened = Vector3(x - along * axis.x, y - along * axis.y, z - along * axis.z)
  return if (flattened.length() > 1e-9) flattened else null
}

// endregion
