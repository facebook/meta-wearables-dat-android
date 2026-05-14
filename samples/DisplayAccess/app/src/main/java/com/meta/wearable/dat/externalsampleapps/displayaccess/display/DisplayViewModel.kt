/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.display

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.types.VideoCodec
import com.meta.wearable.dat.display.types.VideoPlayerState
import com.meta.wearable.dat.display.types.VideoSource
import com.meta.wearable.dat.display.views.Alignment
import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.display.views.CornerRadius
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.FlexBoxScope
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.ImageSize
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import com.meta.wearable.dat.display.views.VideoPlayer
import com.meta.wearable.dat.externalsampleapps.displayaccess.SampleApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SuppressLint(
    "AutoCloseableUse",
    "AvoidVanillaCoroutineDispatcherOnWearables",
    "NavigatorCoroutineLaunchWithoutExceptionHandler",
)
class DisplayViewModel(
    application: Application,
) : AndroidViewModel(application) {

  private companion object {
    private const val TAG = "DisplayAccessVM"
    private const val TUTORIAL_VIDEO_URL =
        "https://github.com/facebook/meta-wearables-dat-android/raw/refs/heads/assets/video_266x150_faststart.mp4"

    private val carMaintenanceTutorials =
        listOf(
            CarMaintenanceTutorial(
                title = "Oil change",
                duration = "Easy • 45 min",
                imageUri = "https://www.facebook.com/assets/wearables_dat_display/oil.png",
                iconImageUri =
                    "https://www.facebook.com/assets/wearables_dat_display/oil_square.png",
                steps =
                    listOf(
                        CarMaintenanceTutorialStep(
                            description =
                                "Park on level ground and let the engine cool before opening the hood."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Drain the old oil, replace the filter, and tighten the drain plug."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Refill with fresh oil, run the engine briefly, and recheck the level."
                        ),
                    ),
            ),
            CarMaintenanceTutorial(
                title = "Fix a flat tire",
                duration = "Easy • 15 min",
                imageUri = "https://www.facebook.com/assets/wearables_dat_display/tire.png",
                iconImageUri =
                    "https://www.facebook.com/assets/wearables_dat_display/tire_square.png",
                steps =
                    listOf(
                        CarMaintenanceTutorialStep(
                            description =
                                "Park away from traffic, engage the brake, and place the wheel wedges."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Loosen the lug nuts slightly, raise the car, and remove the flat tire."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Mount the spare, tighten in a star pattern, and lower the vehicle."
                        ),
                    ),
            ),
            CarMaintenanceTutorial(
                title = "Replace headlight bulb",
                duration = "Very easy • 5 min",
                imageUri = "https://www.facebook.com/assets/wearables_dat_display/light.png",
                iconImageUri =
                    "https://www.facebook.com/assets/wearables_dat_display/light_square.png",
                steps =
                    listOf(
                        CarMaintenanceTutorialStep(
                            description =
                                "Open the rear access cover and disconnect the bulb connector."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Release the retaining clip, remove the old bulb, and insert the new one."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Reconnect power, close the cover, and verify the beam works properly."
                        ),
                    ),
            ),
            CarMaintenanceTutorial(
                title = "Check engine light",
                duration = "Hard • 2 hours",
                imageUri = "https://www.facebook.com/assets/wearables_dat_display/engine.png",
                iconImageUri =
                    "https://www.facebook.com/assets/wearables_dat_display/engine_square.png",
                steps =
                    listOf(
                        CarMaintenanceTutorialStep(
                            description =
                                "Check whether the light is steady or flashing, and stop driving if it is flashing."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Tighten the gas cap fully and look for obvious issues like low fluids or overheating."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Scan for diagnostic codes or schedule service if the light stays on after restarting."
                        ),
                    ),
            ),
            CarMaintenanceTutorial(
                title = "Change washer fluid",
                duration = "Very easy • 3 min",
                imageUri = "https://www.facebook.com/assets/wearables_dat_display/washer.png",
                iconImageUri =
                    "https://www.facebook.com/assets/wearables_dat_display/washer_square.png",
                steps =
                    listOf(
                        CarMaintenanceTutorialStep(
                            description =
                                "Open the hood and locate the washer fluid reservoir cap with the windshield symbol."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Pour washer fluid into the reservoir carefully until it reaches the fill line."
                        ),
                        CarMaintenanceTutorialStep(
                            description =
                                "Close the cap securely and test the sprayers to confirm proper flow."
                        ),
                    ),
            ),
        )
  }

  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
  private val sessionLock = Any()

  private val _uiState = MutableStateFlow(DisplayUIState())
  val uiState: StateFlow<DisplayUIState> = _uiState.asStateFlow()

  private val sessionObserverExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(TAG, "Session state observer failed", throwable)
  }
  private val displayObserverExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(TAG, "Display state observer failed", throwable)
  }
  private val sendContentExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(TAG, "Sending display content failed", throwable)
    _uiState.value =
        _uiState.value.copy(
            snackbarMessage = "Send failed: ${throwable.message ?: "unexpected error"}",
        )
  }

  @GuardedBy("sessionLock") private var session: DeviceSession? = null
  @GuardedBy("sessionLock") private var display: Display? = null
  @GuardedBy("sessionLock") private var sessionStateJob: Job? = null
  @GuardedBy("sessionLock") private var sessionErrorJob: Job? = null
  @GuardedBy("sessionLock") private var displayStateJob: Job? = null
  @GuardedBy("sessionLock") private var pendingDeviceId: DeviceIdentifier? = null
  private var tutorialVideoStateJob: Job? = null

  // -- Session lifecycle --

  fun startSession(deviceId: DeviceIdentifier) {
    Log.d(TAG, "Starting DAT session")
    _uiState.value =
        _uiState.value.copy(
            selectedDeviceId = deviceId,
            connectionState = DwaConnectionState.CONNECTING,
            isStartingSession = true,
            isPreparingDisplay = true,
            snackbarMessage = "Starting session...",
        )

    val result = Wearables.createSession(SpecificDeviceSelector(deviceId))
    result.fold(
        onSuccess = { newSession ->
          synchronized(sessionLock) { session = newSession }

          val stateJob =
              viewModelScope.launch(sessionObserverExceptionHandler) {
                newSession.state.collect { state ->
                  when (state) {
                    DeviceSessionState.STARTED -> {
                      Log.i(TAG, "Session started")
                      _uiState.value =
                          _uiState.value.copy(
                              selectedDeviceId = deviceId,
                              connectionState = DwaConnectionState.CONNECTED,
                              isSessionActive = true,
                              isStartingSession = false,
                              isDatAppUpdateRequired = false,
                              snackbarMessage = "Session started",
                          )
                      if (consumePendingDeviceId(deviceId)) {
                        attachDisplay()
                      }
                    }
                    DeviceSessionState.STOPPED -> {
                      Log.i(TAG, "Session stopped")
                      synchronized(sessionLock) { pendingDeviceId = null }
                      cleanupDisplay()
                      _uiState.value =
                          _uiState.value.copy(
                              connectionState = DwaConnectionState.DISCONNECTED,
                              isSessionActive = false,
                              isDisplayAttached = false,
                              isPreparingDisplay = false,
                              selectedDeviceId = null,
                              snackbarMessage = "Session stopped",
                          )
                    }
                    else -> {}
                  }
                }
              }
          replaceSessionStateJob(stateJob)

          val errorJob =
              viewModelScope.launch(sessionObserverExceptionHandler) {
                newSession.errors.collect { error -> handleSessionError(error) }
              }
          replaceSessionErrorJob(errorJob)

          newSession.start()
        },
        onFailure = { error, _ ->
          Log.e(TAG, "Failed to create session: ${error.description}")
          synchronized(sessionLock) { pendingDeviceId = null }
          _uiState.value =
              _uiState.value.copy(
                  connectionState = DwaConnectionState.DISCONNECTED,
                  isStartingSession = false,
                  isPreparingDisplay = false,
                  isDatAppUpdateRequired =
                      error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED,
                  selectedDeviceId = null,
                  snackbarMessage = "Failed: ${error.description}",
              )
        },
    )
  }

  fun prepareDisplayConnection(deviceId: DeviceIdentifier) {
    if (_uiState.value.selectedDeviceId != null && _uiState.value.selectedDeviceId != deviceId) {
      resetConnectionForNewDevice()
    }

    when {
      _uiState.value.isDisplayAttached && _uiState.value.selectedDeviceId == deviceId -> {
        _uiState.value =
            _uiState.value.copy(
                selectedDeviceId = deviceId,
                isPreparingDisplay = false,
                snackbarMessage = "Display ready",
            )
      }
      _uiState.value.isSessionActive && _uiState.value.selectedDeviceId == deviceId -> {
        _uiState.value =
            _uiState.value.copy(
                selectedDeviceId = deviceId,
                isPreparingDisplay = true,
                snackbarMessage = "Attaching display...",
            )
        attachDisplay()
      }
      else -> {
        synchronized(sessionLock) { pendingDeviceId = deviceId }
        startSession(deviceId)
      }
    }
  }

  fun attachDisplay() {
    val currentSession =
        synchronized(sessionLock) { session }
            ?: run {
              _uiState.value = _uiState.value.copy(snackbarMessage = "No active session")
              return
            }

    Log.d(TAG, "Attaching display")
    _uiState.value = _uiState.value.copy(snackbarMessage = "Attaching display...")

    currentSession
        .addDisplay()
        .fold(
            onSuccess = { newDisplay ->
              synchronized(sessionLock) { display = newDisplay }
              Log.i(TAG, "Display attached")
              _uiState.value =
                  _uiState.value.copy(
                      isDisplayAttached = true,
                      isPreparingDisplay = true,
                      snackbarMessage = "Display attached",
                  )

              val stateJob =
                  viewModelScope.launch(displayObserverExceptionHandler) {
                    var hasStarted = false
                    newDisplay.state.collect { state ->
                      Log.i(TAG, "Display state: $state")
                      _uiState.value =
                          _uiState.value.copy(
                              displayState = state,
                              isPreparingDisplay =
                                  state != DisplayState.STARTED && state != DisplayState.STOPPED,
                          )
                      if (state == DisplayState.STARTED) {
                        hasStarted = true
                        _uiState.value =
                            _uiState.value.copy(
                                isPreparingDisplay = false,
                                snackbarMessage = "Display ready",
                            )
                      }
                      if (state == DisplayState.STOPPED && hasStarted) {
                        _uiState.value =
                            _uiState.value.copy(
                                isPreparingDisplay = false,
                                snackbarMessage = "Display session stopped",
                            )
                      }
                    }
                  }
              replaceDisplayStateJob(stateJob)
            },
            onFailure = { error, _ ->
              Log.e(TAG, "Failed to attach display: ${error.description}")
              _uiState.value =
                  _uiState.value.copy(
                      isPreparingDisplay = false,
                      snackbarMessage = "Failed: ${error.description}",
                  )
            },
        )
  }

  // -- Content --

  fun sendContent(content: ContentScope.() -> Unit) {
    viewModelScope.launch(dispatcher + sendContentExceptionHandler) {
      val currentDisplay =
          synchronized(sessionLock) { display }
              ?: run {
                _uiState.value = _uiState.value.copy(snackbarMessage = "No display attached")
                return@launch
              }

      val result = currentDisplay.sendContent(content)
      result.fold(
          onSuccess = { _uiState.value = _uiState.value.copy(snackbarMessage = "Content sent") },
          onFailure = { error, _ ->
            _uiState.value =
                _uiState.value.copy(snackbarMessage = "Send failed: ${error.description}")
          },
      )
    }
  }

  // -- Sample management --

  fun sendSampleToDisplay(sample: SampleApp) {
    Log.i(TAG, "Sending sample to display: ${sample.name}")
    when (sample) {
      SampleApp.CAR_MAINTENANCE -> displayCarMaintenanceScreen()
    }
  }

  // -- Cleanup --

  fun clearSnackbarMessage() {
    _uiState.value = _uiState.value.copy(snackbarMessage = null)
  }

  fun detachDisplay() {
    Log.d(TAG, "Detaching display")
    synchronized(sessionLock) { session }?.removeDisplay()
    cleanupDisplay()
    _uiState.value =
        _uiState.value.copy(
            isDisplayAttached = false,
            isPreparingDisplay = false,
            snackbarMessage = "Display detached",
        )
  }

  fun stopSession() {
    Log.d(TAG, "Stopping session")
    synchronized(sessionLock) { pendingDeviceId = null }
    _uiState.value =
        _uiState.value.copy(
            isStoppingSession = true,
            isPreparingDisplay = false,
            snackbarMessage = "Stopping session...",
        )

    detachDisplay()
    clearSessionStateJob()?.cancel()
    clearSessionErrorJob()?.cancel()
    clearSession()?.stop()

    _uiState.value =
        _uiState.value.copy(
            connectionState = DwaConnectionState.DISCONNECTED,
            isSessionActive = false,
            isStoppingSession = false,
            selectedDeviceId = null,
            snackbarMessage = "Session stopped",
        )
  }

  private fun resetConnectionForNewDevice() {
    synchronized(sessionLock) { pendingDeviceId = null }
    synchronized(sessionLock) { session }?.removeDisplay()
    cleanupDisplay()
    clearSessionStateJob()?.cancel()
    clearSessionErrorJob()?.cancel()
    clearSession()?.stop()
    _uiState.value =
        _uiState.value.copy(
            isSessionActive = false,
            isDisplayAttached = false,
            connectionState = DwaConnectionState.DISCONNECTED,
            isStartingSession = false,
            isPreparingDisplay = false,
            displayState = null,
        )
  }

  private fun cleanupDisplay() {
    tutorialVideoStateJob?.cancel()
    tutorialVideoStateJob = null
    clearDisplayStateJob()?.cancel()
    synchronized(sessionLock) { display = null }
  }

  private fun handleSessionError(error: DeviceSessionError) {
    Log.e(TAG, "Session error: ${error.description}")
    _uiState.value =
        _uiState.value.copy(
            connectionState = DwaConnectionState.DISCONNECTED,
            isSessionActive = false,
            isStartingSession = false,
            isPreparingDisplay = false,
            isDatAppUpdateRequired =
                error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED,
            selectedDeviceId = null,
            snackbarMessage = error.description,
        )
  }

  private fun displayCarMaintenanceScreen() {
    sendContent { carMaintenanceTutorialListContent() }
  }

  private fun displayCarMaintenanceTutorialDetail(tutorial: CarMaintenanceTutorial) {
    sendContent {
      flexBox(direction = Direction.COLUMN, gap = 12) {
        flexBox(padding = 24, background = FlexBoxBackground.CARD) {
          tutorial.imageUri?.let { imageUri ->
            image(
                uri = imageUri,
                sizePreset = ImageSize.FILL,
                cornerRadius = CornerRadius.MEDIUM,
            )
          }
          text(tutorial.title, style = TextStyle.HEADING)
          text(tutorial.duration, style = TextStyle.META, color = TextColor.SECONDARY)
        }
        flexBox(
            direction = Direction.ROW,
            gap = 8,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
            wrap = true,
        ) {
          button(
              "Back",
              onClick = { displayCarMaintenanceScreen() },
          )
          button(
              "Start",
              onClick = { displayCarMaintenanceTutorialStep(tutorial, 0) },
          )
        }
      }
    }
  }

  private fun displayTutorialVideo(
      tutorial: CarMaintenanceTutorial,
      stepIndex: Int,
  ) {
    viewModelScope.launch(dispatcher + sendContentExceptionHandler) {
      val currentDisplay =
          synchronized(sessionLock) { display }
              ?: run {
                _uiState.value = _uiState.value.copy(snackbarMessage = "No display attached")
                return@launch
              }

      val player =
          VideoPlayer(
              source = VideoSource.Url(TUTORIAL_VIDEO_URL),
              codec = VideoCodec.MP4,
          )

      val result = currentDisplay.sendContent { video(player = player) }
      result.fold(
          onSuccess = {
            tutorialVideoStateJob?.cancel()
            tutorialVideoStateJob = viewModelScope.launch {
              player.state.collect { state ->
                if (state == VideoPlayerState.ENDED) {
                  tutorialVideoStateJob?.cancel()
                  tutorialVideoStateJob = null
                  displayCarMaintenanceTutorialStep(tutorial, stepIndex)
                }
              }
            }
            _uiState.value = _uiState.value.copy(snackbarMessage = "Starting video...")
            player.play()
          },
          onFailure = { error, _ ->
            _uiState.value =
                _uiState.value.copy(snackbarMessage = "Send failed: ${error.description}")
          },
      )
    }
  }

  private fun displayCarMaintenanceTutorialStep(
      tutorial: CarMaintenanceTutorial,
      stepIndex: Int,
  ) {
    val clampedIndex = stepIndex.coerceIn(0, tutorial.steps.lastIndex)
    val step = tutorial.steps[clampedIndex]
    sendContent {
      flexBox(direction = Direction.COLUMN, gap = 12) {
        flexBox(padding = 24, background = FlexBoxBackground.CARD) {
          text(
              "Step ${clampedIndex + 1}",
              style = TextStyle.META,
              color = TextColor.SECONDARY,
          )
          text(step.description, style = TextStyle.BODY)
        }

        flexBox(
            direction = Direction.ROW,
            gap = 8,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
        ) {
          val isLastStep = clampedIndex == tutorial.steps.lastIndex
          button(
              "Previous",
              style = ButtonStyle.PRIMARY,
              iconName = IconName.TRIANGLE_LEFT_VERTICAL_LINE,
              onClick = {
                if (clampedIndex == 0) {
                  displayCarMaintenanceTutorialDetail(tutorial)
                } else {
                  displayCarMaintenanceTutorialStep(tutorial, clampedIndex - 1)
                }
              },
          )
          button(
              if (isLastStep) "Done" else "Next",
              style = ButtonStyle.PRIMARY,
              iconName =
                  if (isLastStep) {
                    IconName.CHECKMARK
                  } else {
                    IconName.TRIANGLE_RIGHT_VERTICAL_LINE
                  },
              onClick = {
                if (isLastStep) {
                  displayCarMaintenanceScreen()
                } else if (clampedIndex < tutorial.steps.lastIndex) {
                  displayCarMaintenanceTutorialStep(tutorial, clampedIndex + 1)
                }
              },
          )
          button(
              "Watch video",
              style = ButtonStyle.SECONDARY,
              iconName = IconName.VIDEO_CAMERA,
              onClick = { displayTutorialVideo(tutorial, clampedIndex) },
          )
        }
      }
    }
  }

  private fun ContentScope.carMaintenanceTutorialListContent() {
    flexBox(direction = Direction.COLUMN, gap = 10) {
      carMaintenanceTutorials.forEach { tutorial -> maintenanceTutorialItem(tutorial) }
    }
  }

  private fun FlexBoxScope.maintenanceTutorialItem(tutorial: CarMaintenanceTutorial) {
    flexBox(
        padding = 24,
        background = FlexBoxBackground.CARD,
        onClick = { displayCarMaintenanceTutorialDetail(tutorial) },
    ) {
      flexBox(direction = Direction.ROW, gap = 12, crossAlignment = Alignment.CENTER) {
        tutorial.iconImageUri?.let { imageUri ->
          flexBox(direction = Direction.COLUMN, flexGrow = 1f) {
            image(
                uri = imageUri,
                sizePreset = ImageSize.FILL,
                cornerRadius = CornerRadius.MEDIUM,
            )
          }
        }
        flexBox(direction = Direction.COLUMN, flexGrow = 7f) {
          text(tutorial.title, style = TextStyle.BODY)
          text(tutorial.duration, style = TextStyle.META, color = TextColor.SECONDARY)
        }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopSession()
  }

  private fun consumePendingDeviceId(deviceId: DeviceIdentifier): Boolean =
      synchronized(sessionLock) {
        if (pendingDeviceId == deviceId && !_uiState.value.isDisplayAttached) {
          pendingDeviceId = null
          true
        } else {
          false
        }
      }

  private fun replaceSessionStateJob(job: Job) {
    synchronized(sessionLock) {
      sessionStateJob?.cancel()
      sessionStateJob = job
    }
  }

  private fun replaceSessionErrorJob(job: Job) {
    synchronized(sessionLock) {
      sessionErrorJob?.cancel()
      sessionErrorJob = job
    }
  }

  private fun replaceDisplayStateJob(job: Job) {
    synchronized(sessionLock) {
      displayStateJob?.cancel()
      displayStateJob = job
    }
  }

  private fun clearSessionStateJob(): Job? =
      synchronized(sessionLock) {
        val currentJob = sessionStateJob
        sessionStateJob = null
        currentJob
      }

  private fun clearSessionErrorJob(): Job? =
      synchronized(sessionLock) {
        val currentJob = sessionErrorJob
        sessionErrorJob = null
        currentJob
      }

  private fun clearDisplayStateJob(): Job? =
      synchronized(sessionLock) {
        val currentJob = displayStateJob
        displayStateJob = null
        currentJob
      }

  private fun clearSession(): DeviceSession? =
      synchronized(sessionLock) {
        val currentSession = session
        session = null
        currentSession
      }
}

private data class CarMaintenanceTutorial(
    val title: String,
    val duration: String,
    val imageUri: String? = null,
    val iconImageUri: String? = null,
    val steps: List<CarMaintenanceTutorialStep>,
)

private data class CarMaintenanceTutorialStep(
    val description: String,
)
