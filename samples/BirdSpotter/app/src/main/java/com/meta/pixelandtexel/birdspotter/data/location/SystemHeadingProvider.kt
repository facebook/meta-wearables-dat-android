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
import com.meta.pixelandtexel.birdspotter.domain.HeadingProvider
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The [HeadingProvider] the app ships: `SensorManager`'s rotation vector, for as long as something
 * is collecting.
 *
 * The mirror is [HeadingProvider], not this class — the sensor and its listener are the platform
 * plumbing the architecture note keeps idiomatic.
 *
 * **The rotation vector, not the raw magnetometer.** `TYPE_ROTATION_VECTOR` is already fused with
 * the accelerometer and gyroscope, so it survives a phone held at a birdwatcher's angle rather than
 * flat; `getOrientation` on raw magnetic field would swing wildly the moment the phone tilts.
 *
 * **And it is read off whichever axis is actually pointing somewhere** — see [bearingFrom].
 * `getOrientation` answers for a device lying flat on its back and leaves the compensating to the
 * caller, where what the plate wants is a heading for the device as held.
 *
 * A device with no such sensor produces an empty flow, which is exactly what [HeadingProvider]
 * describes: nothing to say beyond not showing a bearing.
 */
class SystemHeadingProvider(context: Context) : HeadingProvider {

  private val sensors =
      context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

  override fun headingStream(): Flow<Double> = callbackFlow {
    val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (sensor == null) {
      close()
      return@callbackFlow
    }

    val rotation = FloatArray(9)
    val remapped = FloatArray(9)
    val orientation = FloatArray(3)

    val listener =
        object : SensorEventListener {
          override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            trySend(bearingFrom(rotation, remapped, orientation))
          }

          override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

    // UI rate, not the fastest available: a bearing shown as one of eight points redrawn at
    // 200 Hz would be a screen full of work for a plate that changes once a turn.
    sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    awaitClose { sensors.unregisterListener(listener) }
  }
      // The newest bearing is the only one worth having — an older one is where the phone used
      // to point, which is never what the plate should read.
      .conflate()
      // The sensor reports continuously whether or not the phone has moved. Rounding to whole
      // degrees before de-duplicating is what turns that into an event per actual turn.
      .distinctUntilChanged { old, new -> abs(old - new).roundToInt() < HeadingFilterDegrees }
}

/**
 * Which way the watcher is looking, in degrees clockwise from north, out of a rotation matrix.
 *
 * **`getOrientation` answers for a phone lying flat on its back**, and its azimuth is the compass
 * point the *top edge* is pointing at — read off the horizontal projection of the device's Y axis.
 * Lift the phone to look at something and that axis swings towards the sky, the projection it is
 * measured from shrinks to nothing, and the bearing degenerates into whatever noise is left. Held
 * fully upright it is meaningless. That is the bug: a phone in the pose a person actually holds one
 * in was being asked the one question it cannot answer.
 *
 * So the axis is chosen by how the phone is being held. `rotation[8]` is how much the screen faces
 * the sky — 1 flat on its back, 0 straight up — and past [FlatCosine] the matrix is remapped so the
 * azimuth is read off the **back of the phone** instead, which is where a raised phone points.
 *
 * The two agree either side of the changeover, so it needs no hysteresis: tipping a phone up pivots
 * the top edge and the back through the same compass point, and each is at its steadiest where the
 * other is running out. [FlatCosine] is 60° from flat, comfortably clear of both degeneracies.
 *
 * Takes its scratch arrays rather than allocating: this runs at sensor rate.
 */
internal fun bearingFrom(
    rotation: FloatArray,
    remapped: FloatArray,
    orientation: FloatArray,
): Double {
  val matrix =
      if (abs(rotation[8]) > FlatCosine) {
        rotation
      } else {
        SensorManager.remapCoordinateSystem(
            rotation,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remapped,
        )
        remapped
      }
  SensorManager.getOrientation(matrix, orientation)

  // Azimuth arrives in radians, ±π from north; the rest of the app speaks degrees clockwise, and
  // `compassPoint` wraps whatever it is handed.
  val degrees = Math.toDegrees(orientation[0].toDouble())
  return if (degrees < 0) degrees + 360 else degrees
}

/**
 * How far the phone has to turn before the bearing is reported again. Five degrees: the coarsest
 * filter that still turns an eight-point plate over promptly.
 */
private const val HeadingFilterDegrees = 5

/**
 * Where "flat enough to read off the top edge" ends: cos 60°. Anything more upright than this is
 * read off the back of the phone instead — see [bearingFrom].
 */
private const val FlatCosine = 0.5f
