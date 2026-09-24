/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.mockdevice

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesThermalLevel
import com.meta.pixelandtexel.birdspotter.domain.MockCameraFacing
import com.meta.pixelandtexel.birdspotter.domain.MockCapturePress
import com.meta.pixelandtexel.birdspotter.domain.MockDeviceInfo
import com.meta.pixelandtexel.birdspotter.domain.MockDeviceRepository
import com.meta.pixelandtexel.birdspotter.domain.MockGlassesModel
import com.meta.pixelandtexel.birdspotter.domain.MockMotionPose
import com.meta.pixelandtexel.birdspotter.domain.MockNavDirection
import com.meta.pixelandtexel.birdspotter.domain.MockSpeechSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the mock panel shows: the kit's switch, the pairs it holds and which one the controls are
 * aimed at, and the last value each control was set to — so a slider reads what the glasses were
 * told, not a default that has drifted from it.
 *
 * @property isEnabled Whether the kit is standing in for the real SDK.
 * @property isFlipping Set while a flip is in flight — the kit waits for the open session to end
 *   first, and the switch must not be thrown twice into that wait.
 * @property devices The pairs the kit holds, in the order they were paired.
 * @property selectedDeviceId The pair every control below is aimed at — `null` when the kit holds
 *   none.
 * @property model The model the next **Pair** makes.
 * @property notice The last thing the panel has to say — a file that was set, what the kit answered
 *   a voice launch with. One line, replaced by the next.
 */
data class MockDeviceUiState(
    val isEnabled: Boolean = false,
    val isFlipping: Boolean = false,
    val devices: List<MockDeviceInfo> = emptyList(),
    val selectedDeviceId: String? = null,
    val model: MockGlassesModel = MockGlassesModel.RAY_BAN_META,
    val batteryLevel: Int = 80,
    val isCharging: Boolean = false,
    val thermal: GlassesThermalLevel = GlassesThermalLevel.NOMINAL,
    val cameraAccess: GlassesAccess = GlassesAccess.GRANTED,
    val microphoneAccess: GlassesAccess = GlassesAccess.GRANTED,
    val speechSource: MockSpeechSource = MockSpeechSource.INJECTED,
    val speechText: String = "",
    val pose: MockMotionPose = MockMotionPose.LEVEL,
    val displayServerPort: Int? = null,
    val notice: String? = null,
) {
  val selectedDevice: MockDeviceInfo?
    get() = devices.firstOrNull { it.id == selectedDeviceId }

  /** Whether the controls have a pair to act on. */
  val hasSelection: Boolean
    get() = selectedDevice != null
}

/**
 * Drives the Mock Device Kit panel: the switch, the pairs, and every control the kit offers on the
 * selected pair.
 *
 * **Thin on purpose.** Each control is one call on [MockDeviceRepository]; what this class adds is
 * the choice of *which* pair the call goes to, kept steady as pairs come and go (see
 * [selectedDeviceId]), and the last-set values the panel reads back.
 */
class MockDeviceViewModel(
    private val mockDevice: MockDeviceRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MockDeviceUiState(isEnabled = mockDevice.isEnabled))
  val uiState: StateFlow<MockDeviceUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      mockDevice.isEnabledStream().collect { isEnabled ->
        _uiState.update { it.copy(isEnabled = isEnabled) }
        if (isEnabled) {
          val port = mockDevice.startDisplayServer()
          _uiState.update {
            it.copy(
                displayServerPort = port,
                notice = if (port == null) DisplayServerErrorMessage else it.notice,
            )
          }
        } else {
          mockDevice.stopDisplayServer()
          _uiState.update { it.copy(displayServerPort = null) }
        }
      }
    }
    viewModelScope.launch {
      mockDevice.devicesStream().collect { devices ->
        _uiState.update { it.copy(devices = devices) }
        select(selectedDeviceId(devices, _uiState.value.selectedDeviceId))
      }
    }
  }

  // The switch

  fun setEnabled(enabled: Boolean) {
    val state = _uiState.value
    if (state.isFlipping || enabled == state.isEnabled) return
    _uiState.update { it.copy(isFlipping = true) }
    viewModelScope.launch {
      try {
        mockDevice.setEnabled(enabled)
      } finally {
        // However the flip ends — a throw from the kit included — the switch is
        // handed back, or it stays greyed out for the life of the panel.
        _uiState.update { it.copy(isFlipping = false) }
      }
    }
  }

  // The pairs

  fun choose(model: MockGlassesModel) {
    _uiState.update { it.copy(model = model) }
  }

  fun pair() {
    val model = _uiState.value.model
    viewModelScope.launch {
      runCatching { mockDevice.pair(model) }
          .onSuccess { paired -> select(paired.id) }
          .onFailure { _uiState.update { it.copy(notice = "The kit is not enabled.") } }
    }
  }

  fun unpair() {
    val id = _uiState.value.selectedDeviceId ?: return
    mockDevice.unpair(id)
  }

  fun select(id: String?) {
    _uiState.update { it.copy(selectedDeviceId = id) }
  }

  /**
   * The selected pair's panel as the kit draws it, or `null` when the pair has none. The view
   * belongs to the pair it was made for, so the screen asks again whenever the selection moves.
   */
  fun displayPreview(context: Context): View? {
    val device = _uiState.value.selectedDevice ?: return null
    if (!device.model.hasDisplay) return null
    return mockDevice.displayPreview(device.id, context)
  }

  // Device state

  fun powerOn() = onSelected(mockDevice::powerOn)

  fun powerOff() = onSelected(mockDevice::powerOff)

  fun don() = onSelected(mockDevice::don)

  fun doff() = onSelected(mockDevice::doff)

  fun fold() = onSelected(mockDevice::fold)

  fun unfold() = onSelected(mockDevice::unfold)

  fun setBatteryLevel(level: Int) {
    _uiState.update { it.copy(batteryLevel = level) }
    onSelected { mockDevice.setBatteryLevel(it, level) }
  }

  fun setCharging(isCharging: Boolean) {
    _uiState.update { it.copy(isCharging = isCharging) }
    onSelected { mockDevice.setCharging(it, isCharging) }
  }

  fun setThermal(level: GlassesThermalLevel) {
    _uiState.update { it.copy(thermal = level) }
    onSelected { mockDevice.setThermal(it, level) }
  }

  // Grants

  /**
   * Sets both what a read answers and what a request comes back with: the panel offers one switch
   * per grant, and a grant that reads denied but is granted on request is a state nobody demoing
   * needs.
   */
  fun setAccess(permission: GlassesPermission, access: GlassesAccess) {
    _uiState.update {
      when (permission) {
        GlassesPermission.CAMERA -> it.copy(cameraAccess = access)
        GlassesPermission.MICROPHONE -> it.copy(microphoneAccess = access)
      }
    }
    mockDevice.setAccess(permission, access)
    mockDevice.setRequestResult(permission, access)
  }

  // Camera

  fun useCameraFeed(uri: Uri) {
    onSelected { mockDevice.setCameraFeed(it, uri) }
    _uiState.update { it.copy(notice = "Camera feed: ${uri.lastPathSegment ?: uri}") }
  }

  fun usePhoneCamera(facing: MockCameraFacing) {
    onSelected { mockDevice.setCameraFeed(it, facing) }
    val side = if (facing == MockCameraFacing.FRONT) "front" else "back"
    _uiState.update { it.copy(notice = "Camera feed: the phone's $side camera") }
  }

  fun useCapturedPhoto(uri: Uri) {
    onSelected { mockDevice.setCapturedPhoto(it, uri) }
    _uiState.update { it.copy(notice = "Captured photo: ${uri.lastPathSegment ?: uri}") }
  }

  fun failNextCapture() {
    onSelected(mockDevice::simulateCaptureFailure)
    _uiState.update { it.copy(notice = "The next capture will fail.") }
  }

  // Inputs

  fun tap() = onSelected(mockDevice::tap)

  fun tapAndHold() = onSelected(mockDevice::tapAndHold)

  fun navigate(direction: MockNavDirection) = onSelected { mockDevice.navigate(it, direction) }

  fun select() = onSelected(mockDevice::select)

  fun back() = onSelected(mockDevice::back)

  fun pressCapture(press: MockCapturePress) = onSelected { mockDevice.pressCapture(it, press) }

  fun pressActionButton() = onSelected(mockDevice::pressActionButton)

  // Speech

  fun setSpeechSource(source: MockSpeechSource) {
    _uiState.update { it.copy(speechSource = source) }
    onSelected { mockDevice.setSpeechSource(it, source) }
  }

  fun setSpeechText(text: String) {
    _uiState.update { it.copy(speechText = text) }
  }

  fun sendTranscription(isFinal: Boolean) {
    val text = _uiState.value.speechText.trim()
    if (text.isEmpty()) return
    onSelected { mockDevice.simulateTranscription(it, text, isFinal) }
    if (isFinal) _uiState.update { it.copy(speechText = "") }
  }

  fun sendSpeechError() {
    onSelected { mockDevice.simulateSpeechError(it, "Simulated recogniser failure") }
  }

  fun completeSpeech() = onSelected(mockDevice::simulateSpeechCompletion)

  // Motion

  fun setPose(pose: MockMotionPose) {
    _uiState.update { it.copy(pose = pose) }
    onSelected { mockDevice.setMotionPose(it, pose) }
  }

  // Voice

  fun simulateVoiceLaunch() {
    val id = _uiState.value.selectedDeviceId ?: return
    val answer = mockDevice.simulateVoiceLaunch(id)
    _uiState.update {
      it.copy(
          notice =
              answer?.let { said -> "Voice launch: $said" }
                  ?: "Voice launch: nothing was listening.",
      )
    }
  }

  private inline fun onSelected(control: (String) -> Unit) {
    val id = _uiState.value.selectedDeviceId ?: return
    control(id)
  }

  override fun onCleared() {
    mockDevice.stopDisplayServer()
    super.onCleared()
  }

  companion object {
    fun factory(mockDevice: MockDeviceRepository): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return MockDeviceViewModel(mockDevice) as T
          }
        }
  }
}

private const val DisplayServerErrorMessage = "The Chrome preview server could not be started."

/**
 * Which pair the controls should be aimed at once the kit's list has changed.
 *
 * The current pair, for as long as the kit still holds it — a new pair being added must not yank
 * the controls off the one somebody is driving. When it has gone, the pair added most recently:
 * after a pair, that is the pair just made, and after an unpair it is the closest thing to where
 * the controls were. Nothing, when the kit holds nothing.
 */
internal fun selectedDeviceId(devices: List<MockDeviceInfo>, current: String?): String? {
  if (current != null && devices.any { it.id == current }) return current
  return devices.lastOrNull()?.id
}
