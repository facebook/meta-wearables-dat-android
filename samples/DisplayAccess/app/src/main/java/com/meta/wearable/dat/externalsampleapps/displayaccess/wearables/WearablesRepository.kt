/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.wearables

import android.app.Activity
import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.sdk.concurrency.coroutines.WearableCoroutineScopes
import com.meta.wearable.sdk.concurrency.coroutines.WearableDispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DisplaySampleWearablesRepo"

class WearablesRepository(
    private val applicationContext: Context,
    private val scope: CoroutineScope,
) {
  private val lock = Object()

  private val _registrationState =
      MutableStateFlow<RegistrationState>(RegistrationState.UNAVAILABLE)
  val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

  private val _devices = MutableStateFlow<Set<DeviceIdentifier>>(emptySet())
  val devices: StateFlow<Set<DeviceIdentifier>> = _devices.asStateFlow()

  private val _devicesMetadata = MutableStateFlow<Map<DeviceIdentifier, Device>>(emptyMap())
  val devicesMetadata: StateFlow<Map<DeviceIdentifier, Device>> = _devicesMetadata.asStateFlow()
  private val monitoringExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(TAG, "Wearables monitoring failed", throwable)
  }

  private var monitoringStarted = false

  fun startMonitoring() {
    if (monitoringStarted) return
    monitoringStarted = true

    scope.launch(monitoringExceptionHandler) {
      Wearables.registrationState.collect { value -> _registrationState.value = value }
    }
    scope.launch(monitoringExceptionHandler) {
      Wearables.registrationErrorStream.collect { error ->
        Log.e(TAG, error.getLocalizedDescription(applicationContext))
      }
    }
    scope.launch(monitoringExceptionHandler) {
      Wearables.devices.collect { identifiers -> updateDevices(identifiers) }
    }
  }

  private fun updateDevices(identifiers: Set<DeviceIdentifier>) {
    _devices.value = identifiers
    val current = _devicesMetadata.value
    val removed = current.keys - identifiers
    val added = identifiers - current.keys

    if (removed.isNotEmpty()) {
      _devicesMetadata.update { it.filterKeys { key -> key !in removed } }
    }

    for (id in added) {
      scope.launch(monitoringExceptionHandler) {
        Wearables.devicesMetadata[id]?.collect { device -> updateMetadata(id, device) }
      }
    }
  }

  private fun updateMetadata(id: DeviceIdentifier, device: Device) {
    synchronized(lock) { _devicesMetadata.update { it.toMutableMap().apply { put(id, device) } } }
  }

  fun startRegistration(activity: Activity) {
    if (_registrationState.value == RegistrationState.REGISTERED) {
      Log.d(TAG, "Already registered")
      return
    }
    Wearables.startRegistration(activity)
  }

  fun startUnregistration(activity: Activity) {
    if (_registrationState.value != RegistrationState.REGISTERED) {
      Log.d(TAG, "Not registered")
      return
    }
    Wearables.startUnregistration(activity)
  }

  companion object {
    @Volatile private var instance: WearablesRepository? = null

    fun getInstance(applicationContext: Context): WearablesRepository {
      return instance
          ?: synchronized(this) {
            instance
                ?: WearablesRepository(
                        applicationContext,
                        WearableCoroutineScopes.applicationScope(WearableDispatchers.main()),
                    )
                    .also { instance = it }
          }
    }
  }
}
