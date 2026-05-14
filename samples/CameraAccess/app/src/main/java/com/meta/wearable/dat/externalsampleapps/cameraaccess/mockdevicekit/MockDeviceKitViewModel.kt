/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// MockDeviceKitViewModel - DAT SDK Testing Infrastructure
//
// This ViewModel demonstrates the MockDeviceKit testing framework provided by the DAT SDK.
// MockDeviceKit allows developers to test DAT applications without physical wearable devices.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.mockdevicekit

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockRaybanMeta
import com.meta.wearable.dat.mockdevice.api.camera.CameraFacing
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MockDeviceKitViewModel(application: Application) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "MockDeviceKitViewModel"
  }

  private val mockDeviceKit = MockDeviceKit.getInstance(application.applicationContext)

  private val _uiState = MutableStateFlow(MockDeviceKitUiState())
  val uiState: StateFlow<MockDeviceKitUiState> = _uiState.asStateFlow()

  fun enable() {
    mockDeviceKit.enable()
    _uiState.update { it.copy(isEnabled = true) }
  }

  fun disable() {
    mockDeviceKit.disable()
    _uiState.update { it.copy(isEnabled = false, pairedDevices = emptyList()) }
  }

  // Create a simulated Ray-Ban Meta glasses device
  fun pairRaybanMeta() {
    viewModelScope.launch {
      try {
        Log.d(TAG, "Pairing RayBan Meta device")
        val mockDevice = mockDeviceKit.pairRaybanMeta()
        val deviceName = "RayBan Meta Glasses"
        val deviceInfo =
            MockDeviceInfo(
                device = mockDevice,
                deviceId = UUID.randomUUID().toString(),
                deviceName = deviceName,
            )
        _uiState.update { currentState ->
          currentState.copy(pairedDevices = currentState.pairedDevices + deviceInfo)
        }
        Log.d(TAG, "Successfully paired RayBan Meta device: ${deviceInfo.deviceId}")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to pair RayBan Meta device", e)
      }
    }
  }

  fun unpairDevice(deviceInfo: MockDeviceInfo) {
    viewModelScope.launch {
      try {
        Log.d(TAG, "Unpairing device with ID: ${deviceInfo.deviceId}")
        mockDeviceKit.unpairDevice(deviceInfo.device)
        _uiState.update { currentState ->
          currentState.copy(pairedDevices = currentState.pairedDevices - deviceInfo)
        }
        Log.d(TAG, "Successfully unpaired device: ${deviceInfo.deviceId}")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to unpair device with ID: ${deviceInfo.deviceId}", e)
      }
    }
  }

  fun powerOn(deviceInfo: MockDeviceInfo) {
    executeMockDeviceOperation(deviceInfo, "Powering on", deviceInfo.copy(isPoweredOn = true)) {
        device ->
      device.powerOn()
    }
  }

  fun powerOff(deviceInfo: MockDeviceInfo) {
    executeMockDeviceOperation(
        deviceInfo,
        "Powering off",
        deviceInfo.copy(isPoweredOn = false, isDonned = false, isUnfolded = false),
    ) { device ->
      device.powerOff()
    }
  }

  fun don(deviceInfo: MockDeviceInfo) {
    executeMockDeviceOperation(
        deviceInfo,
        "Donning",
        deviceInfo.copy(isDonned = true, isUnfolded = true),
    ) { device ->
      device.don()
    }
  }

  fun doff(deviceInfo: MockDeviceInfo) {
    executeMockDeviceOperation(deviceInfo, "Doffing", deviceInfo.copy(isDonned = false)) { device ->
      device.doff()
    }
  }

  fun fold(deviceInfo: MockDeviceInfo) {
    executeMockDeviceOperation(
        deviceInfo,
        "Folding",
        deviceInfo.copy(isUnfolded = false, isDonned = false),
    ) { device ->
      device.fold()
    }
  }

  fun unfold(deviceInfo: MockDeviceInfo) {
    executeMockDeviceOperation(deviceInfo, "Unfolding", deviceInfo.copy(isUnfolded = true)) { device
      ->
      device.unfold()
    }
  }

  fun setCameraFeed(deviceInfo: MockDeviceInfo, uri: Uri) {
    viewModelScope.launch {
      try {
        Log.d(TAG, "Setting camera feed from URI: $uri for device: ${deviceInfo.deviceId}")
        // services.camera.setCameraFeed() sets video content for streaming
        // This video will be streamed when Stream.videoStream is active
        deviceInfo.device.services.camera.setCameraFeed(uri)
        updateDeviceInfo(deviceInfo.copy(hasCameraFeed = true, cameraSource = null))
        Log.d(TAG, "Successfully set camera feed for device: ${deviceInfo.deviceId}")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to set camera feed for device: ${deviceInfo.deviceId}", e)
      }
    }
  }

  fun setCapturedImage(deviceInfo: MockDeviceInfo, uri: Uri) {
    viewModelScope.launch {
      try {
        Log.d(TAG, "Setting captured image from URI: $uri for device: ${deviceInfo.deviceId}")
        // services.camera.setCapturedImage() sets photo for capture operations
        // This image will be returned when Stream.capturePhoto() is called
        deviceInfo.device.services.camera.setCapturedImage(uri)
        updateDeviceInfo(deviceInfo.copy(hasCapturedImage = true))
        Log.d(TAG, "Successfully set captured image for device: ${deviceInfo.deviceId}")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to set captured image for device: ${deviceInfo.deviceId}", e)
      }
    }
  }

  fun setCameraFeed(deviceInfo: MockDeviceInfo, cameraFacing: CameraFacing) {
    viewModelScope.launch {
      try {
        Log.d(
            TAG,
            "Setting camera feed to $cameraFacing for device: ${deviceInfo.deviceId}",
        )
        // services.camera.setCameraFeed() streams from the phone's camera
        // This is mutually exclusive with setCameraFeed(Uri)
        deviceInfo.device.services.camera.setCameraFeed(cameraFacing)
        updateDeviceInfo(deviceInfo.copy(cameraSource = cameraFacing, hasCameraFeed = false))
        Log.d(TAG, "Successfully set camera feed for device: ${deviceInfo.deviceId}")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to set camera feed for device: ${deviceInfo.deviceId}", e)
      }
    }
  }

  private fun updateDeviceInfo(newDeviceInfo: MockDeviceInfo) {
    _uiState.update { currentState ->
      val updatedDevices =
          currentState.pairedDevices.map { device ->
            if (device.deviceId == newDeviceInfo.deviceId) {
              newDeviceInfo
            } else {
              device
            }
          }
      currentState.copy(pairedDevices = updatedDevices)
    }
  }

  private fun executeMockDeviceOperation(
      deviceInfo: MockDeviceInfo,
      operationName: String,
      updatedDeviceInfo: MockDeviceInfo,
      operation: (MockRaybanMeta) -> Unit,
  ) {
    viewModelScope.launch {
      try {
        Log.d(TAG, "$operationName device with ID: ${deviceInfo.deviceId}")
        operation(deviceInfo.device)
        updateDeviceInfo(updatedDeviceInfo)
        Log.d(TAG, "Successfully executed $operationName on device: ${deviceInfo.deviceId}")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to $operationName device with ID: ${deviceInfo.deviceId}", e)
      }
    }
  }
}
