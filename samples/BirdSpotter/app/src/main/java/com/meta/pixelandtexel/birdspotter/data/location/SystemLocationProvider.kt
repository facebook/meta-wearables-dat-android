/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import com.meta.pixelandtexel.birdspotter.domain.LocationProvider
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The [LocationProvider] the app ships: one `LocationManager.getCurrentLocation` fix per call.
 *
 * The mirror is [LocationProvider], not this class — the manager and the permission dance are the
 * platform plumbing the architecture note keeps idiomatic. The platform one-shot
 * (`getCurrentLocation`, `minSdk 31` so no compat shim, no Play Services) returns exactly one fix
 * and times out on its own, which is the whole reason a one-shot needs no manual timer.
 *
 * Authorization is the Identify gate's concern, not this type's: the wizard is reachable only once
 * `LOCATION` is granted, so [currentCoordinate] assumes it and returns `null` rather than prompting
 * if it finds otherwise — the same `null` a failed fix returns.
 */
class SystemLocationProvider(context: Context) : LocationProvider {

  private val appContext = context.applicationContext
  private val locationManager =
      appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

  @SuppressLint("MissingPermission") // Guarded by hasLocationPermission() below.
  override suspend fun currentCoordinate(): Coordinate? {
    if (!hasLocationPermission()) return null
    val provider = bestProvider() ?: return null
    return suspendCancellableCoroutine { continuation ->
      val signal = CancellationSignal()
      continuation.invokeOnCancellation { signal.cancel() }
      locationManager.getCurrentLocation(
          provider,
          signal,
          ContextCompat.getMainExecutor(appContext),
      ) { location ->
        continuation.resume(location?.let { Coordinate(it.latitude, it.longitude) })
      }
    }
  }

  private fun hasLocationPermission(): Boolean =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED ||
          ContextCompat.checkSelfPermission(
              appContext,
              Manifest.permission.ACCESS_COARSE_LOCATION,
          ) == PackageManager.PERMISSION_GRANTED

  /** Fused where the device offers it (`minSdk 31`), then GPS, then network — first that exists. */
  private fun bestProvider(): String? =
      when {
        locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER) ->
            LocationManager.FUSED_PROVIDER
        locationManager.allProviders.contains(LocationManager.GPS_PROVIDER) ->
            LocationManager.GPS_PROVIDER
        locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER) ->
            LocationManager.NETWORK_PROVIDER
        else -> null
      }
}
