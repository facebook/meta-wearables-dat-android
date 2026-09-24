/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.meta.pixelandtexel.birdspotter.domain.GazeProvider
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The [GazeProvider] the app ships: `SensorManager`'s rotation vector again, read for elevation
 * rather than for bearing.
 *
 * The mirror is [GazeProvider], not this class — the sensor and its listener are the platform
 * plumbing the architecture note keeps idiomatic.
 *
 * **The same sensor as [SystemHeadingProvider], and a second listener on it.** Registering twice is
 * cheap — Android fans one sensor out to every listener and runs it at the fastest rate any of them
 * asked for — and the alternative, one provider answering both questions, is the interface
 * [GazeProvider] explains a compass cannot fully answer.
 *
 * A device with no such sensor produces an empty flow, which is exactly what [GazeProvider]
 * describes: nothing to say beyond not showing a band.
 */
class SystemGazeProvider(context: Context) : GazeProvider {

  private val sensors =
      context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

  override fun gazeStream(): Flow<Double> = callbackFlow {
    val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (sensor == null) {
      close()
      return@callbackFlow
    }

    val rotation = FloatArray(9)

    val listener =
        object : SensorEventListener {
          override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            trySend(elevationFrom(rotation) + PhonePoseBias)
          }

          override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

    // UI rate, not the fastest available — the same reasoning as the bearing's: a band named in
    // one word, redrawn at 200 Hz, is a screen full of work for a plate that changes when
    // someone lifts their arm.
    sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    awaitClose { sensors.unregisterListener(listener) }
  }
      // The newest elevation is the only one worth having — an older one is where the phone used
      // to point, which is never what the chip should read.
      .conflate()
      // The sensor reports continuously whether or not the phone has moved. Rounding to whole
      // degrees before de-duplicating is what turns that into an event per actual tilt.
      .distinctUntilChanged { old, new -> abs(old - new).roundToInt() < GazeFilterDegrees }
}

/**
 * How high the phone's camera is aimed, in degrees above the horizon, out of a rotation matrix.
 *
 * **One number, and it is already in the matrix.** `rotation[8]` is how much the device's own Z
 * axis — straight out of the screen, towards the watcher's face — points at the sky: `1` with the
 * phone flat on its back, `0` held upright. The camera looks the other way, out of the back, so its
 * elevation is the arcsine of the *negation* of that. Flat on its back the camera is aimed at the
 * watcher's shoes and this reads `-90`; held upright it reads `0`; face down at the sky, `+90`.
 *
 * No remapping and no `getOrientation` call, which is the whole reason elevation is read here
 * rather than taken from the azimuth's neighbour in that array: `getOrientation`'s pitch is stated
 * for a device lying flat and degenerates in exactly the pose [bearingFrom] has to work around,
 * where this is a single component of a matrix that is always valid.
 *
 * Clamped before the arcsine because a rotation matrix built from a noisy vector can land a hair
 * past ±1, and `asin` of that is `NaN` — one bad sample would otherwise blank the chip.
 *
 * Takes its scratch array rather than allocating: this runs at sensor rate.
 */
internal fun elevationFrom(rotation: FloatArray): Double =
    Math.toDegrees(asin((-rotation[8]).coerceIn(-1f, 1f).toDouble()))

/**
 * How far the phone has to tilt before the elevation is reported again. Two degrees rather than the
 * bearing's five: a band boundary crossed slowly should turn the chip over as it happens, and a
 * tilt has no equivalent of a compass's wander to filter out.
 */
private const val GazeFilterDegrees = 2

/**
 * How far below the watcher's own idea of level this device reads, in degrees.
 *
 * **The correction for a pose, applied where the pose is.** A phone aimed at something level is not
 * held vertical: the screen is tipped back towards the face so it can be read, which points the
 * camera down by roughly this much. Left uncorrected, a watcher looking straight out at a bird gets
 * told they are aiming into the understory.
 *
 * It lives here rather than in the bands because it is a fact about holding *this* device, and
 * [com.meta.pixelandtexel.birdspotter.domain.gazeBand] is about where a bird is. An instrument that
 * points where its wearer looks adds nothing and lands on the same thresholds correctly.
 *
 * Worth re-tuning against a few real hands; it is the pose of an average grip, not a measurement.
 */
private const val PhonePoseBias = 7.5
