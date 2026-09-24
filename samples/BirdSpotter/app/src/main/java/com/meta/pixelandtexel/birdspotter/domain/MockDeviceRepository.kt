/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import android.content.Context
import android.net.Uri
import android.view.View
import kotlinx.coroutines.flow.Flow

/**
 * The glasses the mock can stand in for — one entry per model the kit knows how to fake.
 *
 * A model rather than a device: what is chosen here is which *kind* of pair to simulate, and only
 * one of them carries a display. Choosing the display model is what turns the Display row on the
 * glasses screen into something the mock can answer.
 *
 * @property displayName What the picker calls it.
 * @property hasDisplay Whether this model has a panel to draw on — the one fact the panel needs
 *   before offering the display preview.
 */
enum class MockGlassesModel(val displayName: String, val hasDisplay: Boolean = false) {
  RAY_BAN_META("Ray-Ban Meta"),
  OAKLEY_META_HSTN("Oakley Meta HSTN"),
  OAKLEY_META_VANGUARD("Oakley Meta Vanguard"),
  RAY_BAN_META_OPTICS("Ray-Ban Meta Optics"),
  META_GLASSES("Meta Glasses"),
  META_RAY_BAN_DISPLAY("Meta Ray-Ban Display", hasDisplay = true),
}

/**
 * A simulated pair the kit is holding: enough to name it in a list and address it in a call. The
 * readings themselves — worn, battery, heat — land in [GlassesDeviceInfo] like any real pair's,
 * because the whole point is that nothing above the data layer can tell.
 *
 * @property id The kit's own identifier for the pair, the key every control takes.
 */
data class MockDeviceInfo(val id: String, val model: MockGlassesModel)

/** Which phone camera stands in for the glasses' when no video file has been chosen. */
enum class MockCameraFacing {
  FRONT,
  BACK,
}

/** The ways the capture button can be pressed, as the inputs capability distinguishes them. */
enum class MockCapturePress {
  SHORT_PRESS,
  HOLD,
  DOUBLE_PRESS,
}

/** A swipe on the temple. */
enum class MockNavDirection {
  UP,
  DOWN,
  LEFT,
  RIGHT,
}

/**
 * Where the mock recogniser's words come from: strings typed into the panel, or the phone's own
 * recogniser hearing the room — the way to rehearse the ASR flow out loud.
 */
enum class MockSpeechSource {
  INJECTED,
  LIVE_DEVICE_ASR,
}

/**
 * A head position the motion feed can hold — the handful the aim readings care about.
 *
 * Poses rather than raw samples because the app only ever reads gravity out of the accelerometer to
 * answer *how high is the wearer looking*; a panel offering three floats per axis would be an
 * instrument nobody could play during a demo.
 */
enum class MockMotionPose(val displayName: String) {
  LEVEL("Level"),
  LOOKING_UP("Looking up"),
  LOOKING_DOWN("Looking down"),
  TILTED_LEFT("Tilted left"),
  TILTED_RIGHT("Tilted right"),
}

/** What went wrong with the mock, in the cases the panel can say something about. */
sealed class MockDeviceError : Exception() {
  /** A control was used with the kit switched off. */
  data object NotEnabled : MockDeviceError() {
    private fun readResolve(): Any = NotEnabled
  }

  /** The pair the control named is no longer held by the kit. */
  data object NoSuchDevice : MockDeviceError() {
    private fun readResolve(): Any = NoSuchDevice
  }
}

/**
 * Meta's Mock Device Kit, as the app drives it: a switch that swaps the SDK's registration and
 * connectivity for simulated ones, and the controls on the simulated pair.
 *
 * **The switch is live.** Enabling swaps the providers under the running SDK, and every stream this
 * app already holds open — registration, the device list — carries the change on its own; disabling
 * swaps them back. What the swap does *not* do is end a session that was open at the moment of the
 * flip, so both directions end the app's own leases first.
 *
 * Every device control takes the pair's [MockDeviceInfo.id], because the kit holds more than one
 * and the panel chooses which is being driven. A control on a pair the kit no longer holds does
 * nothing, deliberately: the panel's list is a beat behind the kit, and a press on a row that has
 * just gone is not an error worth a dialog.
 */
interface MockDeviceRepository {

  /** Whether the kit is standing in for the real SDK right now. */
  val isEnabled: Boolean

  /** Cold stream of [isEnabled], current value first. */
  fun isEnabledStream(): Flow<Boolean>

  /**
   * Flips the kit, ending any open session first. Enabling remembers the choice, so the next launch
   * comes up simulated too.
   */
  suspend fun setEnabled(enabled: Boolean)

  /** Cold stream of the simulated pairs the kit holds, current list first. */
  fun devicesStream(): Flow<List<MockDeviceInfo>>

  /**
   * Adds a simulated pair and brings it up — powered, unfolded and worn — so it is a pair a session
   * can start on straight away. Suspending because pairing is the kit's own work, measured in whole
   * seconds on a cold start, and never the caller's thread to sit on.
   *
   * @throws MockDeviceError when the kit is off.
   */
  suspend fun pair(model: MockGlassesModel): MockDeviceInfo

  fun unpair(id: String)

  // Device state

  fun powerOn(id: String)

  fun powerOff(id: String)

  fun don(id: String)

  fun doff(id: String)

  fun fold(id: String)

  fun unfold(id: String)

  fun setBatteryLevel(id: String, level: Int)

  fun setCharging(id: String, isCharging: Boolean)

  fun setThermal(id: String, level: GlassesThermalLevel)

  // Grants

  /**
   * What [GlassesSessionRepository.access] will read back. [GlassesAccess.UNKNOWN] is not settable
   * — the kit answers with a grant or a refusal, never silence.
   */
  fun setAccess(permission: GlassesPermission, access: GlassesAccess)

  /**
   * What the next request for the grant will come back with — the wearer's answer in the Meta AI
   * app, scripted.
   */
  fun setRequestResult(permission: GlassesPermission, access: GlassesAccess)

  // Camera

  /** A video file the stream plays in place of the glasses' camera. */
  fun setCameraFeed(id: String, fileUri: Uri)

  /** The phone's own camera in place of the glasses'. */
  fun setCameraFeed(id: String, facing: MockCameraFacing)

  /** The photograph every capture comes back with. */
  fun setCapturedPhoto(id: String, fileUri: Uri)

  /** The next capture fails the way a real crossing can. */
  fun simulateCaptureFailure(id: String)

  // Inputs

  fun tap(id: String)

  fun tapAndHold(id: String)

  fun navigate(id: String, direction: MockNavDirection)

  fun select(id: String)

  fun back(id: String)

  fun pressCapture(id: String, press: MockCapturePress)

  fun pressActionButton(id: String)

  // Speech

  fun setSpeechSource(id: String, source: MockSpeechSource)

  fun simulateTranscription(id: String, text: String, isFinal: Boolean)

  fun simulateSpeechError(id: String, message: String)

  fun simulateSpeechCompletion(id: String)

  // Motion

  /** Holds the pair's motion feed at a pose for as long as something is listening. */
  fun setMotionPose(id: String, pose: MockMotionPose)

  // Display

  suspend fun startDisplayServer(): Int?

  fun stopDisplayServer()

  /**
   * A live view of the simulated panel, drawn by the kit itself, or `null` when the pair has no
   * panel. The kit keeps it current as the app draws; the caller only hosts it. A fresh view per
   * call — one per selection, dropped when the selection moves.
   */
  fun displayPreview(id: String, context: Context): View?

  /**
   * Presses a button the app drew, by the identifier the app gave it. `false` when the panel holds
   * no such button.
   */
  fun sendDisplayClick(id: String, identifier: String): Boolean

  // Voice

  /**
   * "Hey Meta, open BirdSpotter", spoken to the simulated pair. Returns the kit's own description
   * of what it sent, or `null` when nothing was listening.
   */
  fun simulateVoiceLaunch(id: String): String?
}
