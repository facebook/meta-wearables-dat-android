/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.wearables

import android.app.Activity
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WearablesViewModel(application: Application) : AndroidViewModel(application) {
  private companion object {
    private const val TAG = "DisplaySampleWearablesVM"
  }

  private val repository: WearablesRepository = WearablesRepository.getInstance(application)
  private val _uiState = MutableStateFlow(WearablesUiState())
  val uiState: StateFlow<WearablesUiState> = _uiState.asStateFlow()
  private val collectionExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(TAG, "Wearables state observation failed", throwable)
  }

  private var observingStarted = false

  fun startObserving() {
    if (observingStarted) return
    observingStarted = true

    repository.startMonitoring()

    viewModelScope.launch(collectionExceptionHandler) {
      repository.registrationState.collect { state ->
        _uiState.update { it.copy(registrationState = state) }
      }
    }
    viewModelScope.launch(collectionExceptionHandler) {
      repository.devices.collect { devices -> _uiState.update { it.copy(devices = devices) } }
    }
    viewModelScope.launch(collectionExceptionHandler) {
      repository.devicesMetadata.collect { metadata ->
        _uiState.update {
          it.copy(
              devicesMetadata = metadata,
              isFirmwareUpdateRequired =
                  metadata.values.any { device ->
                    device.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED
                  },
          )
        }
      }
    }
  }

  fun startRegistration(activity: Activity) {
    if (repository.registrationState.value == RegistrationState.REGISTERED) {
      Toast.makeText(getApplication(), "Already connected", Toast.LENGTH_SHORT).show()
      return
    }
    repository.startRegistration(activity)
  }

  fun startUnregistration(activity: Activity) {
    if (repository.registrationState.value != RegistrationState.REGISTERED) {
      Toast.makeText(getApplication(), "Not connected", Toast.LENGTH_SHORT).show()
      return
    }
    repository.startUnregistration(activity)
  }

  fun openFirmwareUpdate(activity: Activity) {
    Wearables.openFirmwareUpdate(activity).onFailure { error, _ ->
      Toast.makeText(getApplication(), error.description, Toast.LENGTH_SHORT).show()
    }
  }

  fun openDATGlassesAppUpdate(activity: Activity) {
    Wearables.openDATGlassesAppUpdate(activity).onFailure { error, _ ->
      Toast.makeText(getApplication(), error.description, Toast.LENGTH_SHORT).show()
    }
  }
}
