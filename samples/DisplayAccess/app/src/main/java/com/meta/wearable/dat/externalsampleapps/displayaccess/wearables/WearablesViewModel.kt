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
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.externalsampleapps.displayaccess.R
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DeveloperPreviewMode {
  CHROME,
  IN_APP,
}

class WearablesViewModel(application: Application) : AndroidViewModel(application) {
  private companion object {
    private const val TAG = "DisplaySampleWearablesVM"
  }

  private val repository: WearablesRepository = WearablesRepository.getInstance(application)
  private val _uiState = MutableStateFlow(WearablesUiState())
  val uiState: StateFlow<WearablesUiState> = _uiState.asStateFlow()
  val mockDisplayGlasses: StateFlow<MockGlasses?> = repository.mockDisplayGlasses
  val chromePreviewInfo: StateFlow<ChromePreviewInfo?> = repository.chromePreviewInfo
  private val _developerPreviewMode = MutableStateFlow<DeveloperPreviewMode?>(null)
  val developerPreviewMode: StateFlow<DeveloperPreviewMode?> = _developerPreviewMode.asStateFlow()
  private val _isDeveloperPreviewChanging = MutableStateFlow(false)
  val isDeveloperPreviewChanging: StateFlow<Boolean> = _isDeveloperPreviewChanging.asStateFlow()
  private val _developerPreviewErrorMessage = MutableStateFlow<String?>(null)
  val developerPreviewErrorMessage: StateFlow<String?> = _developerPreviewErrorMessage.asStateFlow()
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

  suspend fun setDeveloperPreviewMode(mode: DeveloperPreviewMode?): DeviceIdentifier? {
    if (!_isDeveloperPreviewChanging.compareAndSet(expect = false, update = true)) return null
    _developerPreviewErrorMessage.value = null
    return try {
      withContext(Dispatchers.IO) { applyDeveloperPreviewMode(mode) }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      reportDeveloperPreviewFailure(error)
      null
    } finally {
      _isDeveloperPreviewChanging.value = false
    }
  }

  fun reportDeveloperPreviewFailure(error: Exception) {
    Log.e(TAG, "Developer preview operation failed", error)
    _developerPreviewErrorMessage.value =
        getApplication<Application>().getString(R.string.developer_preview_error)
  }

  private fun applyDeveloperPreviewMode(mode: DeveloperPreviewMode?): DeviceIdentifier? {
    if (mode == null) {
      repository.disablePhonePreview()
      _developerPreviewMode.value = null
      return null
    }

    val previewGlasses =
        repository
            .enablePhonePreview()
            .onFailure { error, _ ->
              _developerPreviewErrorMessage.value = error.getLocalizedDescription(getApplication())
            }
            .getOrNull() ?: return null

    if (mode == DeveloperPreviewMode.IN_APP) {
      repository.stopChromePreview()
      _developerPreviewMode.value = mode
      return previewGlasses.deviceIdentifier
    }

    val chromePreview =
        repository
            .startChromePreview()
            .onFailure { error, _ ->
              _developerPreviewErrorMessage.value = error.getLocalizedDescription(getApplication())
            }
            .getOrNull()
    if (chromePreview == null) {
      repository.disablePhonePreview()
      _developerPreviewMode.value = null
      return null
    }

    _developerPreviewMode.value = mode
    return previewGlasses.deviceIdentifier
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

  override fun onCleared() {
    repository.disablePhonePreview()
    super.onCleared()
  }
}
