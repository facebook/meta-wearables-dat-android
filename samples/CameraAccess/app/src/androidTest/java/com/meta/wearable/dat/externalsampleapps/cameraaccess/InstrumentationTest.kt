/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// InstrumentationTest - DAT Integration Testing Suite
//
// End-to-end tests for the reworked CameraAccess lifecycle flow, driven by MockDeviceKit (which
// simulates Ray-Ban Meta glasses and auto-grants the wearable camera permission) and Compose UI
// automation. Mirrors the iOS CameraAccessUITests scenarios.

package com.meta.wearable.dat.externalsampleapps.cameraaccess

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.VideoCaptureHandler
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitConfig
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class InstrumentationTest {

  companion object {
    private const val TAG = "InstrumentationTest"
    private const val DEFAULT_TIMEOUT = 5000L
    private const val STREAM_TIMEOUT = 20000L
  }

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()
  private val targetContext: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

  @Before
  fun setup() {
    grantPermissions()
  }

  @After
  fun tearDown() {
    MockDeviceKit.getInstance(targetContext).disable()
  }

  // MARK: - Device pairing & navigation

  @Test
  fun showsHomeScreenOnLaunch() {
    composeTestRule.waitUntilExactlyOneExists(
        hasText(targetContext.getString(R.string.home_tip_session)),
        timeoutMillis = DEFAULT_TIMEOUT,
    )
  }

  @Test
  fun registerAndPairShowsCameraScreen() {
    pairActiveDevice(withCameraFeed = false, withCapturedImage = false)
    waitForActiveDevice()
  }

  // MARK: - Device activity

  @Test
  fun powerCycleAffectsDeviceActivity() {
    val device = pairActiveDevice(withCameraFeed = false, withCapturedImage = false)
    waitForActiveDevice()

    device.powerOff()
    waitForInactiveDevice()

    device.powerOn()
    device.don()
    waitForActiveDevice()
  }

  // MARK: - Lifecycle

  @Test
  fun startSessionThenStreaming() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()
  }

  @Test
  fun captureDisabledUntilStreaming() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    waitForActiveDevice()

    composeTestRule.onNodeWithTag("start_session_button").performClick()

    // Session started, no stream yet → the capture row is present but disabled until streaming.
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("capture_button").assertIsNotEnabled()
  }

  @Test
  fun stopStreamingKeepsSession() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()

    composeTestRule.onNodeWithTag("stop_preview_button").performClick()

    // Session stays active → Start Preview returns and Start Session does not.
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("start_session_button").assertDoesNotExist()

    // Preview restarts on the same session.
    composeTestRule.onNodeWithTag("start_preview_button").performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("stop_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  @Test
  fun endSession() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()

    composeTestRule.onNodeWithTag("stop_preview_button").performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )

    composeTestRule.onNodeWithTag("end_session_button").performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_session_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  @Test
  fun endSessionAvailableWhileStreaming() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()

    // End Session is the bottom button while streaming; tapping it cascades the stop.
    composeTestRule.onNodeWithTag("end_session_button").performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_session_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  @Test
  fun disconnectWhileStreamingThenReconnectShowsStartSession() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()

    disconnect()

    val reconnectLabel = targetContext.getString(R.string.register_button_title)
    composeTestRule.waitUntilExactlyOneExists(
        hasText(reconnectLabel),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithText(reconnectLabel).performClick()

    // Unregistering does not stop the SDK session, and the camera view model is scoped to the
    // activity rather than the screen, so re-registering must land on the pre-session screen off
    // the app's own teardown — not on a camera screen still reporting the old stream.
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_session_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("end_session_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("stop_preview_button").assertDoesNotExist()
  }

  @Test
  fun cameraPermissionDeniedKeepsSessionReady() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    // Deny the permission REQUEST too (not just the initial check), so confirming the redirect
    // resolves to Denied instead of the mock's default grant.
    MockDeviceKit.getInstance(targetContext)
        .permissions
        .setRequestResult(Permission.CAMERA, PermissionStatus.Denied)

    waitForActiveDevice()
    composeTestRule.onNodeWithTag("start_session_button").performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_preview_button").and(isEnabled()),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("start_preview_button").performClick()

    // Camera is denied, so previewing surfaces the redirect confirm; tap Continue.
    val continueLabel = targetContext.getString(R.string.camera_permission_continue)
    composeTestRule.waitUntilExactlyOneExists(
        hasText(continueLabel),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithText(continueLabel).performClick()

    // The request resolves Denied → the error snackbar shows and the screen stays session-ready
    // (Start Preview is still offered; it never advances to a stream).
    val deniedMessage = targetContext.getString(R.string.error_camera_permission_denied)
    composeTestRule.waitUntilExactlyOneExists(
        hasText(deniedMessage),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  // MARK: - Capture

  @Test
  fun videoRecordStartAndStop() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()
    recordVideoOnly()

    waitForRecordingCapturing()
    composeTestRule.onNodeWithTag("record_button").performClick()

    // Stopping the recording opens the video preview.
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("close_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )

    // The recording must be a finalized, playable MP4 — not just enough to open a preview.
    assertLatestRecordingIsPlayable()

    composeTestRule.onNodeWithTag("close_preview_button").performClick()

    // Back on the camera screen, ready to capture again.
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("record_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  @Test
  fun videoRecordWithAudioFinalizes() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()

    // Record with sound-in-video left ON (the default) — do NOT toggle the mic off. The emulator's
    // virtual mic delivers no PCM, so this exercises the audio-stall fallback: the recording must
    // still finalize a playable, video-only file instead of hanging on a never-ready audio track.
    composeTestRule.onNodeWithTag("record_button").performClick()
    waitForRecordingIndicator()

    waitForRecordingCapturing()
    composeTestRule.onNodeWithTag("record_button").performClick()

    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("close_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    assertLatestRecordingIsPlayable()
    composeTestRule.onNodeWithTag("close_preview_button").performClick()
  }

  // Drives VideoCaptureHandler directly with a valid HEVC CSD but only non-keyframe NALs, so no
  // keyframe is ever detected — the device-camera path that previously stuck at 00:00 (timer never
  // ran) and crashed on stop with "Failed to add the track to the muxer" (0x0 dimensions). The
  // grace fallback must open the track on dimensions alone and finalize a playable file. Driven
  // directly because remote test emulators have no usable camera to reproduce it end-to-end.
  @Test
  fun recordingFinalizesWithoutDetectableKeyframe() {
    val (csd, width, height) = hevcCsdAndSizeFromAsset("plant.mp4")
    val handler = VideoCaptureHandler()
    val dir = File(targetContext.cacheDir, "recordings").apply { mkdirs() }
    val out = File(dir, "synthetic_${System.currentTimeMillis()}.mp4")

    handler.prepare(out.canonicalPath, includeAudio = false)
    handler.setInitialCodecConfig(csd)

    // Minimal HEVC TRAIL_R (non-keyframe) NAL: start code + header (type 1) + dummy payload.
    val nonKeyframe = byteArrayOf(0, 0, 0, 1, 0x02, 0x01) + ByteArray(64) { 0x42 }
    var started = false
    for (i in 0 until 70) {
      if (handler.writeVideoFrame(nonKeyframe, i * 33_333L, width, height)) started = true
    }
    assertTrue("Recording should start via the grace fallback even without a keyframe", started)
    assertTrue("stopRecording should report a video track", handler.stopRecording())
    assertFileHasVideoTrackAndSamples(out)
  }

  // Audio enabled (sound-in-video on, the default) but the mic never delivers audio (no
  // RECORD_AUDIO permission, or a silent emulator mic). The keyframe opens the video track while
  // the audio track is still pending, then markAudioUnavailable() switches to video-only. This must
  // not re-add the video track — the duplicate, sampleless track previously left a 0-byte file
  // ("No video data was recorded"). Recording must finalize a playable, video-only file.
  @Test
  fun recordingFinalizesVideoOnlyWhenMicUnavailable() {
    val (csd, width, height) = hevcCsdAndSizeFromAsset("plant.mp4")
    val handler = VideoCaptureHandler()
    val dir = File(targetContext.cacheDir, "recordings").apply { mkdirs() }
    val out = File(dir, "synthetic_${System.currentTimeMillis()}.mp4")

    handler.prepare(out.canonicalPath, includeAudio = true)
    handler.setInitialCodecConfig(csd)

    val keyframe = byteArrayOf(0, 0, 0, 1, 0x2A, 0x01) + ByteArray(64) { 0x37 } // CRA (type 21)
    val nonKeyframe = byteArrayOf(0, 0, 0, 1, 0x02, 0x01) + ByteArray(64) { 0x42 } // TRAIL_R

    // First frame opens the video track while the audio track is still pending.
    assertTrue(handler.writeVideoFrame(keyframe, 0L, width, height))
    // Mic turns out unavailable → switch to video-only (previously double-added the video track).
    handler.markAudioUnavailable()
    for (i in 1..5) handler.writeVideoFrame(nonKeyframe, i * 33_333L, width, height)

    assertTrue("stopRecording should report a video track", handler.stopRecording())
    assertFileHasVideoTrackAndSamples(out)
  }

  @Test
  fun videoPreviewShowsShare() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()
    recordVideoOnly()

    waitForRecordingCapturing()
    composeTestRule.onNodeWithTag("record_button").performClick()

    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("close_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("share_button").assertExists()
    composeTestRule.onNodeWithTag("close_preview_button").performClick()
  }

  @Test
  fun photoCaptureWhileRecording() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = true)
    startSessionAndStream()
    recordVideoOnly()

    // Capture a photo mid-recording → preview appears; dismiss it.
    composeTestRule.onNodeWithTag("capture_button").performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("close_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("close_preview_button").performClick()

    // Recording continues after dismissing the photo preview.
    waitForRecordingIndicator()
    waitForRecordingCapturing()

    // Stop the recording → video preview appears; dismiss it.
    composeTestRule.onNodeWithTag("record_button").performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("close_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("close_preview_button").performClick()
  }

  @Test
  fun photoCaptureAndDismiss() {
    pairActiveDevice(withCameraFeed = true, withCapturedImage = true)
    startSessionAndStream()

    composeTestRule.onNodeWithTag("capture_button").performClick()

    // Photo preview appears and offers Share.
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("close_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("share_button").assertExists()

    composeTestRule.onNodeWithTag("close_preview_button").performClick()

    // Remain on the camera screen with capture available.
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("capture_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  @Test
  fun foldingGlassesStopsPreview() {
    val device = pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()

    device.fold()

    // Folding takes the glasses off the face and closes the hinge, which ends the session. The
    // device stays powered and connected, so the screen returns to the pre-session state with Start
    // Session offered again.
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_session_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  @Test
  fun singleTapPausesPreviewThenResumes() {
    val device = pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()

    // A single cap-touch tap pauses the stream (StreamState.PAUSED). The preview surface stays
    // mounted on its last frame, a "Paused" badge overlays it, and capture is disabled.
    device.services.captouch.tap()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("paused_overlay"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("capture_button").assertIsNotEnabled()
    // The live stop control must be gone while paused so the stream can't be torn down mid-pause
    // (matches iOS, which flips the pill to the inert start affordance).
    composeTestRule.onNodeWithTag("stop_preview_button").assertDoesNotExist()
    // A recording can't be started while paused — the record pill is disabled when not recording.
    composeTestRule.onNodeWithTag("record_button").assertIsNotEnabled()

    // Tapping again resumes streaming → the badge clears and capture re-enables.
    device.services.captouch.tap()
    composeTestRule.waitUntil(timeoutMillis = STREAM_TIMEOUT) {
      composeTestRule.onAllNodesWithTag("paused_overlay").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("capture_button").and(isEnabled()),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  @Test
  fun stopRecordingEnabledWhilePaused() {
    val device = pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()
    recordVideoOnly()

    waitForRecordingCapturing()

    // Pause mid-recording. Recording keeps running, so the record pill must stay enabled as a Stop
    // control — an in-progress recording can be ended without resuming first.
    device.services.captouch.tap()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("paused_overlay"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("record_button").assertIsEnabled()

    // Stop while still paused → the clip finalizes and its preview appears.
    composeTestRule.onNodeWithTag("record_button").performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("close_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("close_preview_button").performClick()
  }

  @Test
  fun recordingSavesAcrossPauseResume() {
    val device = pairActiveDevice(withCameraFeed = true, withCapturedImage = false)
    startSessionAndStream()
    recordVideoOnly()

    waitForRecordingCapturing()

    // Pause, then resume mid-recording. PTS is non-monotonic across the resume; without the
    // recorder's monotonic floor clamp MediaMuxer rejects the frame and discards the whole clip.
    device.services.captouch.tap()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("paused_overlay"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    device.services.captouch.tap()
    composeTestRule.waitUntil(timeoutMillis = STREAM_TIMEOUT) {
      composeTestRule.onAllNodesWithTag("paused_overlay").fetchSemanticsNodes().isEmpty()
    }

    // Record a little more after resuming, then stop → the clip must finalize and its preview
    // appear.
    waitForRecordingIndicator()
    waitForRecordingToAdvance()
    composeTestRule.onNodeWithTag("record_button").performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("close_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    assertLatestRecordingIsPlayable()
    composeTestRule.onNodeWithTag("close_preview_button").performClick()
  }

  // MARK: - Helpers

  // Pairs a powered, donned, unfolded mock device and returns it. Camera permission is explicitly
  // denied so the preview flow exercises the real redirect-confirm (the mock grants it on request).
  // Set explicitly — MockDeviceKit is a process singleton and a prior test's grant can leak in.
  private fun pairActiveDevice(withCameraFeed: Boolean, withCapturedImage: Boolean): MockGlasses {
    val mockDeviceKit = MockDeviceKit.getInstance(targetContext)
    mockDeviceKit.enable(MockDeviceKitConfig(initialPermissionsGranted = false))
    mockDeviceKit.permissions.set(Permission.CAMERA, PermissionStatus.Denied)
    val device = mockDeviceKit.pairGlasses(GlassesModel.RAYBAN_META).getOrThrow()
    device.powerOn()
    device.don()
    device.unfold()
    if (withCameraFeed) device.services.camera.setCameraFeed(getFileUri("plant.mp4"))
    if (withCapturedImage) device.services.camera.setCapturedImage(getFileUri("plant.png"))
    return device
  }

  // Opens the top-bar settings popover and taps Disconnect. The icon and the popover button share
  // the Disconnect string — the icon carries it as a content description, the button as text — so
  // the two matchers address them separately.
  private fun disconnect() {
    val disconnectLabel = targetContext.getString(R.string.unregister_button_title)
    composeTestRule.onNodeWithContentDescription(disconnectLabel).performClick()
    composeTestRule.waitUntilExactlyOneExists(
        hasText(disconnectLabel),
        timeoutMillis = DEFAULT_TIMEOUT,
    )
    composeTestRule.onNodeWithText(disconnectLabel).performClick()
  }

  // Active device → Start Session is shown and enabled.
  private fun waitForActiveDevice() {
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_session_button").and(isEnabled()),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  // Inactive device → the waiting placeholder is shown.
  private fun waitForInactiveDevice() {
    composeTestRule.waitUntilExactlyOneExists(
        hasText(targetContext.getString(R.string.waiting_for_active_device)),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  // Starts a session, then the preview, confirms the camera-permission redirect, and waits until it
  // is actively streaming (Stop Preview shown).
  private fun startSessionAndStream() {
    waitForActiveDevice()
    composeTestRule.onNodeWithTag("start_session_button").performClick()

    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("start_preview_button").and(isEnabled()),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("start_preview_button").performClick()

    // Camera is denied by default, so the first preview surfaces the permission-redirect confirm.
    // Tap Continue → the mock grants the request and persists it (later previews skip the dialog).
    val continueLabel = targetContext.getString(R.string.camera_permission_continue)
    composeTestRule.waitUntilExactlyOneExists(
        hasText(continueLabel),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithText(continueLabel).performClick()

    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("stop_preview_button"),
        timeoutMillis = STREAM_TIMEOUT,
    )
    // Capture controls enable only once StreamState is STREAMING.
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("capture_button").and(isEnabled()),
        timeoutMillis = STREAM_TIMEOUT,
    )
  }

  // Turns sound-in-video off, then starts recording, and waits for the recording indicator. The
  // emulator's virtual microphone is unreliable, so video-only recording keeps these tests stable;
  // the sound-in-video path is exercised on real devices.
  private fun recordVideoOnly() {
    composeTestRule.waitUntilExactlyOneExists(
        hasTestTag("mic_toggle").and(isEnabled()),
        timeoutMillis = STREAM_TIMEOUT,
    )
    composeTestRule.onNodeWithTag("mic_toggle").performClick()

    composeTestRule.onNodeWithTag("record_button").performClick()
    waitForRecordingIndicator()
  }

  // The recording timer lives inside the record pill, whose clickable merges its descendants, so
  // its tag is only visible in the unmerged tree.
  private fun waitForRecordingIndicator() {
    composeTestRule.waitUntil(timeoutMillis = STREAM_TIMEOUT) {
      composeTestRule
          .onAllNodesWithTag("recording_indicator", useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  // Current value of the recording timer text ("MM:SS"), or null if not recording.
  private fun recordingElapsedText(): String? =
      composeTestRule
          .onAllNodesWithTag("recording_indicator", useUnmergedTree = true)
          .fetchSemanticsNodes()
          .firstOrNull()
          ?.config
          ?.getOrNull(SemanticsProperties.Text)
          ?.firstOrNull()
          ?.text

  // Waits until the recording timer ticks past 00:00. The timer starts only once the muxer writes
  // the first keyframe, so this proves the clip has begun capturing and will finalize on stop —
  // unlike a fixed sleep, which gambles on the emulator's frame pacing.
  private fun waitForRecordingCapturing() {
    composeTestRule.waitUntil(timeoutMillis = STREAM_TIMEOUT) {
      recordingElapsedText().let { it != null && it != "00:00" }
    }
  }

  // Waits for the recording timer to tick at least once more, proving footage was captured past
  // this point (used to record on both sides of a pause/resume). Samples a concrete starting value
  // first so "changed from baseline" can't be satisfied by the timer merely appearing.
  private fun waitForRecordingToAdvance() {
    composeTestRule.waitUntil(timeoutMillis = STREAM_TIMEOUT) { recordingElapsedText() != null }
    val baseline = recordingElapsedText()
    composeTestRule.waitUntil(timeoutMillis = STREAM_TIMEOUT) {
      recordingElapsedText().let { it != null && it != baseline }
    }
  }

  private fun grantPermissions() {
    listOf(
        "android.permission.BLUETOOTH",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.CAMERA",
        "android.permission.INTERNET",
        "android.permission.RECORD_AUDIO",
    )
        .forEach { grantPermission(it) }
  }

  private fun grantPermission(permission: String) {
    try {
      InstrumentationRegistry.getInstrumentation()
          .uiAutomation
          .executeShellCommand("pm grant ${targetContext.packageName} $permission")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to grant permission $permission", e)
    }
  }

  // Verifies the most recent recording is a finalized, playable MP4 with a video track and at least
  // one sample — proving the muxer actually closed the file, not just that a preview opened.
  private fun assertLatestRecordingIsPlayable() {
    val dir = File(targetContext.cacheDir, "recordings")
    val file = dir.listFiles()?.filter { it.extension == "mp4" }?.maxByOrNull { it.lastModified() }
    assertNotNull("Expected a recorded .mp4 in the cache", file)
    assertTrue("Recording file is empty", file!!.length() > 0)

    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(file.canonicalPath)
      var videoTrack = -1
      for (i in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("video/")) videoTrack = i
      }
      assertTrue("Recording has no video track", videoTrack >= 0)
      extractor.selectTrack(videoTrack)
      val sampleSize = extractor.readSampleData(ByteBuffer.allocate(1 shl 21), 0)
      assertTrue("Recording has no video samples", sampleSize > 0)
    } finally {
      extractor.release()
    }
  }

  // Asserts the file is a finalized MP4 with a video track and at least one sample, then deletes
  // it.
  private fun assertFileHasVideoTrackAndSamples(file: File) {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(file.canonicalPath)
      var videoTrack = -1
      for (i in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("video/")) videoTrack = i
      }
      assertTrue("Recording has no video track", videoTrack >= 0)
      extractor.selectTrack(videoTrack)
      assertTrue(
          "Recording has no video samples",
          extractor.readSampleData(ByteBuffer.allocate(1 shl 20), 0) > 0,
      )
    } finally {
      extractor.release()
      file.delete()
    }
  }

  // Returns the HEVC codec-specific data (Annex-B VPS+SPS+PPS) and dimensions of a bundled video
  // asset, for priming VideoCaptureHandler in the keyframe-less recording test.
  private fun hevcCsdAndSizeFromAsset(assetName: String): Triple<ByteArray, Int, Int> {
    val file = File(targetContext.cacheDir, assetName)
    InstrumentationRegistry.getInstrumentation().context.assets.open(assetName).use { input ->
      FileOutputStream(file).use { output -> input.copyTo(output) }
    }
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(file.canonicalPath)
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        if (format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")) {
          val buffer =
              checkNotNull(format.getByteBuffer("csd-0")) { "Asset $assetName has no csd-0" }
          val csd = ByteArray(buffer.remaining())
          buffer.get(csd)
          return Triple(
              csd,
              format.getInteger(MediaFormat.KEY_WIDTH),
              format.getInteger(MediaFormat.KEY_HEIGHT),
          )
        }
      }
    } finally {
      extractor.release()
    }
    throw IllegalStateException("No video track in $assetName")
  }

  private fun getFileUri(assetName: String): Uri {
    val outFile = File(targetContext.cacheDir, assetName)
    InstrumentationRegistry.getInstrumentation().context.assets.open(assetName).use { input ->
      FileOutputStream(outFile).use { output -> input.copyTo(output) }
    }
    return Uri.fromFile(outFile)
  }
}
