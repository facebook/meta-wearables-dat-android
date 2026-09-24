/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

/**
 * The eight-point compass name for a bearing in degrees clockwise from north.
 *
 * Eight points, not sixteen: this answers *which way was the watcher looking*, and `NNW` claims a
 * precision a phone held in one hand while the other holds binoculars does not have. Any bearing is
 * accepted — negative, or past a full turn — because a magnetometer reading arrives as whatever the
 * sensor last computed, and wrapping it is this function's job rather than every caller's.
 */
fun compassPoint(degrees: Double): String {
  val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
  // Each point owns 45°, centred on its own bearing — so N runs from 337.5° round to 22.5°, which
  // is what rounding to the nearest sector rather than truncating into one buys.
  val wrapped = degrees % 360
  val positive = if (wrapped < 0) wrapped + 360 else wrapped
  return points[(positive / 45).roundToInt() % points.size]
}

/**
 * Which way the watcher is facing, as a stream of bearings in degrees clockwise from north.
 *
 * A stream where [LocationProvider] is one shot, and for the same reason each is what it is: a
 * sighting is stamped at one moment and stays stamped, but a heading is only true while you are
 * standing that way, so the screen shows it live and stops showing it when the session ends.
 *
 * **Never empty by way of an error.** A phone with no magnetometer, or one whose compass has not
 * settled, produces a flow that simply emits nothing — the same shape as [LocationProvider]'s
 * `null`, and for the same reason: there is nothing to tell the user about a compass that isn't
 * there beyond not showing them a bearing.
 *
 * The interface is the mirrored surface. The engine behind it — `SensorManager` — is the platform
 * plumbing the architecture note keeps idiomatic.
 */
interface HeadingProvider {
  /** Bearings as they change, in degrees clockwise from north. */
  fun headingStream(): Flow<Double>
}
