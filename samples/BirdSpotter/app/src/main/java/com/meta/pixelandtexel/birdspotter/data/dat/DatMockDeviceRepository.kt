/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.dat

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.View
import com.meta.pixelandtexel.birdspotter.data.mockdevice.MockDeviceSettingsStore
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesThermalLevel
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.MockCameraFacing
import com.meta.pixelandtexel.birdspotter.domain.MockCapturePress
import com.meta.pixelandtexel.birdspotter.domain.MockDeviceError
import com.meta.pixelandtexel.birdspotter.domain.MockDeviceInfo
import com.meta.pixelandtexel.birdspotter.domain.MockDeviceRepository
import com.meta.pixelandtexel.birdspotter.domain.MockGlassesModel
import com.meta.pixelandtexel.birdspotter.domain.MockMotionPose
import com.meta.pixelandtexel.birdspotter.domain.MockNavDirection
import com.meta.pixelandtexel.birdspotter.domain.MockSpeechSource
import com.meta.wearable.dat.core.types.ChargingState
import com.meta.wearable.dat.core.types.PermissionStatus as DatPermissionStatus
import com.meta.wearable.dat.core.types.ThermalLevel
import com.meta.wearable.dat.inputs.types.ButtonType
import com.meta.wearable.dat.inputs.types.CapturePressType
import com.meta.wearable.dat.inputs.types.InputSource
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitConfig
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import com.meta.wearable.dat.mockdevice.api.camera.CameraFacing
import com.meta.wearable.dat.mockdevice.api.speech.MockSpeechSource as DatSpeechSource
import com.meta.wearable.dat.motion.types.MotionSample
import com.meta.wearable.dat.motion.types.MotionSource
import com.meta.wearable.dat.motion.types.Vector3
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [MockDeviceRepository] over Meta's Mock Device Kit.
 *
 * **Construction restores the last choice.** The kit is a process singleton that forgets everything
 * at exit, so an app relaunched with the mock switched on would otherwise come up on real glasses
 * until somebody found the switch again. Building this repository reads [MockDeviceSettingsStore],
 * and if the mock was on it enables the kit and pairs the model that was paired last — powered,
 * unfolded and worn, the way [pair] always leaves a pair — before anything above it has asked a
 * question.
 *
 * **Flipping is ordered.** The kit swaps the SDK's registration and device providers live, and the
 * app's own flows follow the swap without being re-collected; what the swap does not do is end a
 * lease that is open at the moment — a session, or the voice channel, each of which is bound to a
 * device the swap is about to take away — which is why both directions ask the session repository
 * and the voice repository to close theirs and wait for them to go before touching the kit.
 *
 * The kit hands back its pairs as interface objects with no lookup by identifier, so this keeps its
 * own table — the reason every control here is a map read followed by one SDK call.
 *
 * Takes [link] not only for the leases but for its `init`: the kit's own `enable` initialises the
 * SDK if nothing has, and this app wants that to have happened on the session repository's terms,
 * not the kit's.
 */
class DatMockDeviceRepository(
    context: Context,
    private val link: DatGlassesSessionRepository,
    private val voice: DatGlassesVoiceRepository,
    private val settings: MockDeviceSettingsStore,
    private val kit: MockDeviceKitInterface = MockDeviceKit.getInstance(context.applicationContext),
) : MockDeviceRepository {

  private val lock = Any()

  /** The pairs the kit holds, in the order they were paired — the order the panel lists. */
  private val pairs = mutableListOf<Pair<MockDeviceInfo, MockGlasses>>()

  private val enabled = MutableStateFlow(kit.isEnabled)
  private val devices = MutableStateFlow<List<MockDeviceInfo>>(emptyList())

  /** Where the kit's own work runs — never the thread that asked for it. */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    // Off the launching thread: enabling the kit and pairing a device are the SDK's own
    // work, measured in whole seconds on a cold start, and the first frame must not wait on
    // them. The switch and the pair list are flows, so everything above here learns of the
    // restored pair the same way it would learn of one paired by hand.
    if (settings.isEnabled) {
      scope.launch { restore() }
    }
  }

  /** The relaunch path — see the class's own doc. */
  private fun restore() {
    val startedAt = SystemClock.elapsedRealtime()
    enableKit()
    runCatching { pairNow(settings.model) }
        .onFailure { error ->
          BirdLog.error(LogCategory.GLASSES) { "mock device — could not restore the pair: $error" }
        }
    BirdLog.info(LogCategory.GLASSES) {
      "mock device — restored in ${SystemClock.elapsedRealtime() - startedAt} ms"
    }
  }

  // The switch

  override val isEnabled: Boolean
    get() = kit.isEnabled

  override fun isEnabledStream(): Flow<Boolean> = enabled

  override suspend fun setEnabled(enabled: Boolean) {
    if (enabled == kit.isEnabled) return
    // The two things the swap cannot do for itself — see the class's doc. The voice channel
    // stays closed for the length of the swap, so its reopen lands on the swapped-in pair.
    link.endActiveSessions()
    val startedAt = SystemClock.elapsedRealtime()
    voice.withChannelClosed {
      // Off the caller's thread, which is the main one when it is the switch in Settings —
      // the swap is the SDK's own work, and slow.
      withContext(Dispatchers.Default) {
        if (enabled) {
          enableKit()
        } else {
          kit.stopTestServer()
          kit.disable()
          synchronized(lock) { pairs.clear() }
          publishDevices()
        }
      }
    }
    settings.isEnabled = enabled
    this.enabled.value = kit.isEnabled
    BirdLog.info(LogCategory.GLASSES) {
      "mock device — kit ${if (enabled) "enabled" else "disabled"} in " +
          "${SystemClock.elapsedRealtime() - startedAt} ms"
    }
  }

  private fun enableKit() {
    val startedAt = SystemClock.elapsedRealtime()
    kit.enable(MockDeviceKitConfig(initiallyRegistered = true, initialPermissionsGranted = true))
    enabled.value = kit.isEnabled
    BirdLog.debug(LogCategory.GLASSES) {
      "mock device — kit.enable took ${SystemClock.elapsedRealtime() - startedAt} ms"
    }
  }

  // The pairs

  override fun devicesStream(): Flow<List<MockDeviceInfo>> = devices

  override suspend fun pair(model: MockGlassesModel): MockDeviceInfo =
      // Off the caller's thread, for the reason the restore is: pairing is the SDK's own
      // work — a whole second on a cold start — and the thread that pressed the panel's
      // button is the main one.
      withContext(Dispatchers.Default) { pairNow(model) }

  /** The pair itself, on whatever thread is calling — the restore's and [pair]'s. */
  private fun pairNow(model: MockGlassesModel): MockDeviceInfo {
    val startedAt = SystemClock.elapsedRealtime()
    val glasses = kit.pairGlasses(model.datModel).getOrNull() ?: throw MockDeviceError.NotEnabled
    BirdLog.debug(LogCategory.GLASSES) {
      "mock device — kit.pairGlasses took ${SystemClock.elapsedRealtime() - startedAt} ms"
    }
    val info = MockDeviceInfo(id = glasses.deviceIdentifier.identifier, model = model)
    synchronized(lock) { pairs += info to glasses }
    // Up and ready, because a pair that has to be switched on, opened and put on before it
    // answers is three taps between the demo and the thing it is demonstrating.
    glasses.powerOn()
    glasses.unfold()
    glasses.don()
    // **And with a picture behind its camera, because a mock pair without one is a stream
    // that refuses to start.** The kit forgets the feed with everything else at exit, so a
    // pair restored at launch would otherwise come back with nothing behind its camera and
    // the first session opened on it would fail at the stream. The phone's back camera is
    // the default that always exists; the panel can swap in a file or the front camera
    // afterwards, the way it always could.
    glasses.services.camera.setCameraFeed(CameraFacing.BACK)
    settings.model = model
    publishDevices()
    BirdLog.info(LogCategory.GLASSES) { "mock device — paired ${model.displayName} as ${info.id}" }
    return info
  }

  override fun unpair(id: String) {
    val glasses =
        synchronized(lock) {
          val index = pairs.indexOfFirst { it.first.id == id }
          if (index < 0) null else pairs.removeAt(index).second
        } ?: return
    kit.unpairDevice(glasses)
    publishDevices()
  }

  private fun publishDevices() {
    devices.value = synchronized(lock) { pairs.map { it.first } }
  }

  /**
   * The pair a control is aimed at, or `null` when it has gone — in which case the control is
   * quietly a no-op, per the interface's doc.
   */
  private fun glasses(id: String): MockGlasses? =
      synchronized(lock) { pairs.firstOrNull { it.first.id == id }?.second }

  // Device state

  override fun powerOn(id: String) {
    glasses(id)?.powerOn()
  }

  override fun powerOff(id: String) {
    glasses(id)?.powerOff()
  }

  override fun don(id: String) {
    glasses(id)?.don()
  }

  override fun doff(id: String) {
    glasses(id)?.doff()
  }

  override fun fold(id: String) {
    glasses(id)?.fold()
  }

  override fun unfold(id: String) {
    glasses(id)?.unfold()
  }

  override fun setBatteryLevel(id: String, level: Int) {
    glasses(id)?.setBatteryLevel(level.coerceIn(0, 100))
  }

  override fun setCharging(id: String, isCharging: Boolean) {
    glasses(id)
        ?.setChargingState(
            if (isCharging) ChargingState.CHARGING else ChargingState.NOT_CHARGING,
        )
  }

  override fun setThermal(id: String, level: GlassesThermalLevel) {
    // The app collapses the SDK's ladder to three rungs; this picks one rung per word,
    // chosen so the collapse reads back the word that was set.
    val datLevel =
        when (level) {
          GlassesThermalLevel.NOMINAL -> ThermalLevel.NONE
          GlassesThermalLevel.ELEVATED -> ThermalLevel.MODERATE
          GlassesThermalLevel.CRITICAL -> ThermalLevel.CRITICAL
        }
    glasses(id)?.setThermalLevel(datLevel)
  }

  // Grants

  override fun setAccess(permission: GlassesPermission, access: GlassesAccess) {
    kit.permissions.set(permission.datPermission, access.datStatus)
  }

  override fun setRequestResult(permission: GlassesPermission, access: GlassesAccess) {
    kit.permissions.setRequestResult(permission.datPermission, access.datStatus)
  }

  // Camera

  override fun setCameraFeed(id: String, fileUri: Uri) {
    glasses(id)?.services?.camera?.setCameraFeed(fileUri)
  }

  override fun setCameraFeed(id: String, facing: MockCameraFacing) {
    val datFacing =
        when (facing) {
          MockCameraFacing.FRONT -> CameraFacing.FRONT
          MockCameraFacing.BACK -> CameraFacing.BACK
        }
    glasses(id)?.services?.camera?.setCameraFeed(datFacing)
  }

  override fun setCapturedPhoto(id: String, fileUri: Uri) {
    val services = glasses(id)?.services ?: return
    // Both routes a photograph can take out of the mock — the stream's own still and the
    // capture capability's — so the same picture comes back whichever one the app asks.
    services.camera.setCapturedImage(fileUri)
    services.cameraCapture.setCapturedPhoto(fileUri)
  }

  override fun simulateCaptureFailure(id: String) {
    glasses(id)?.services?.cameraCapture?.simulateCaptureFailure()
  }

  // Inputs

  override fun tap(id: String) {
    glasses(id)?.services?.captouch?.tap()
  }

  override fun tapAndHold(id: String) {
    glasses(id)?.services?.captouch?.tapAndHold()
  }

  override fun navigate(id: String, direction: MockNavDirection) {
    val input = glasses(id)?.services?.input ?: return
    when (direction) {
      MockNavDirection.UP -> input.navUp(InputSource.CAPTOUCH)
      MockNavDirection.DOWN -> input.navDown(InputSource.CAPTOUCH)
      MockNavDirection.LEFT -> input.navLeft(InputSource.CAPTOUCH)
      MockNavDirection.RIGHT -> input.navRight(InputSource.CAPTOUCH)
    }
  }

  override fun select(id: String) {
    glasses(id)?.services?.input?.select(InputSource.CAPTOUCH)
  }

  override fun back(id: String) {
    glasses(id)?.services?.input?.back(InputSource.CAPTOUCH)
  }

  override fun pressCapture(id: String, press: MockCapturePress) {
    val pressType =
        when (press) {
          MockCapturePress.SHORT_PRESS -> CapturePressType.SHORT_PRESS
          MockCapturePress.HOLD -> CapturePressType.HOLD
          MockCapturePress.DOUBLE_PRESS -> CapturePressType.DOUBLE_PRESS
        }
    glasses(id)?.services?.input?.capture(pressType)
  }

  override fun pressActionButton(id: String) {
    glasses(id)?.services?.input?.button(ButtonType.ACTION)
  }

  // Speech

  override fun setSpeechSource(id: String, source: MockSpeechSource) {
    val datSource =
        when (source) {
          MockSpeechSource.INJECTED -> DatSpeechSource.INJECTED
          MockSpeechSource.LIVE_DEVICE_ASR -> DatSpeechSource.LIVE_DEVICE_ASR
        }
    glasses(id)?.services?.speech?.setTranscriptionSource(datSource)
  }

  override fun simulateTranscription(id: String, text: String, isFinal: Boolean) {
    glasses(id)?.services?.speech?.simulateTranscription(text, isFinal, 1f)
  }

  override fun simulateSpeechError(id: String, message: String) {
    glasses(id)?.services?.speech?.simulateError(1, message)
  }

  override fun simulateSpeechCompletion(id: String) {
    glasses(id)?.services?.speech?.simulateCompletion()
  }

  // Motion

  override fun setMotionPose(id: String, pose: MockMotionPose) {
    glasses(id)?.services?.motion?.setMotionFeed(motionSamples(pose), true)
  }

  // Display

  override suspend fun startDisplayServer(): Int? =
      withContext(Dispatchers.IO) { kit.startTestServer(MockDisplayServerPort).getOrNull() }

  override fun stopDisplayServer() {
    kit.stopTestServer()
  }

  override fun displayPreview(id: String, context: Context): View? =
      glasses(id)?.services?.display?.createPreviewView(context)

  override fun sendDisplayClick(id: String, identifier: String): Boolean {
    val display = glasses(id)?.services?.display ?: return false
    display.sendClick(identifier)
    return true
  }

  // Voice

  override fun simulateVoiceLaunch(id: String): String? =
      glasses(id)?.services?.voiceInvocation?.simulateLaunchAppAction()
}

// The poses, as samples

/**
 * A second of a still head held at [pose], at the rate the real sensor reports.
 *
 * The axes are the ones `GlassesAim` measured off a worn pair: `+X` at the sky, `+Z` forward, `Y`
 * lateral. A level head therefore reads the whole of gravity's reaction on `X`; looking up tips
 * some of it onto `+Z`, looking down onto `-Z`, and a roll onto `±Y`.
 */
private fun motionSamples(pose: MockMotionPose): List<MotionSample> {
  val g = MockGravityMetersPerSecondSquared
  val up =
      when (pose) {
        MockMotionPose.LEVEL -> Vector3(g, 0f, 0f)
        MockMotionPose.LOOKING_UP ->
            Vector3(g * cos(MockPitchRadians), 0f, g * sin(MockPitchRadians))
        MockMotionPose.LOOKING_DOWN ->
            Vector3(g * cos(MockPitchRadians), 0f, -g * sin(MockPitchRadians))
        MockMotionPose.TILTED_LEFT ->
            Vector3(g * cos(MockRollRadians), g * sin(MockRollRadians), 0f)
        MockMotionPose.TILTED_RIGHT ->
            Vector3(g * cos(MockRollRadians), -g * sin(MockRollRadians), 0f)
      }
  val intervalNs = 1_000_000_000L / MockSampleHertz
  return List(MockSampleHertz) { index ->
    MotionSample(index * intervalNs, up, Vector3(0f, 0f, 0f), null, null, MotionSource.GLASSES)
  }
}

/**
 * Standard gravity, the unit convention the mock's accelerometer holds to — metres per second
 * squared, so a still pair reads this much along whichever axis points at the sky.
 */
private const val MockGravityMetersPerSecondSquared = 9.80665f

/**
 * How far the posed head looks up or down — past the gaze chip's canopy line, so the chip visibly
 * changes its word.
 */
private const val MockPitchRadians = 45f * Math.PI.toFloat() / 180f
private const val MockRollRadians = 30f * Math.PI.toFloat() / 180f
private const val MockSampleHertz = 50
private const val MockDisplayServerPort = 8237

// Domain ↔ SDK

private val MockGlassesModel.datModel: GlassesModel
  get() =
      when (this) {
        MockGlassesModel.RAY_BAN_META -> GlassesModel.RAYBAN_META
        MockGlassesModel.OAKLEY_META_HSTN -> GlassesModel.OAKLEY_META_HSTN
        MockGlassesModel.OAKLEY_META_VANGUARD -> GlassesModel.OAKLEY_META_VANGUARD
        MockGlassesModel.RAY_BAN_META_OPTICS -> GlassesModel.RAYBAN_META_OPTICS
        MockGlassesModel.META_GLASSES -> GlassesModel.META_GLASSES
        MockGlassesModel.META_RAY_BAN_DISPLAY -> GlassesModel.META_RAYBAN_DISPLAY
      }

/**
 * The kit answers granted or denied, never unknown — so unknown is asked for as denied, the honest
 * reading of a grant nobody has given.
 */
private val GlassesAccess.datStatus: DatPermissionStatus
  get() =
      if (this == GlassesAccess.GRANTED) DatPermissionStatus.Granted else DatPermissionStatus.Denied
