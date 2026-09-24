/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// CameraUiState - Camera screen state
//
// Stores the SDK's own DeviceSessionState / StreamState directly and derives booleans from
// them, so the sample surfaces the real SDK state machine rather than a remapped one.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.camera

import android.graphics.Bitmap
import android.net.Uri
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.core.session.DeviceSessionState

/** A capture awaiting preview/share — a still photo or a recorded video file. */
sealed interface CapturePreview {
  data class Photo(val bitmap: Bitmap) : CapturePreview

  data class Video(val uri: Uri) : CapturePreview
}

data class CameraUiState(
    // Bound directly to the SDK state machines.
    val sessionState: DeviceSessionState = DeviceSessionState.IDLE,
    val streamState: StreamState = StreamState.STOPPED,
    // Flips once when the first preview frame arrives; drives the loading→preview swap. A one-shot
    // boolean (not a per-frame counter) keeps the screen from recomposing at the frame rate — the
    // SurfaceView renders frames independently of Compose.
    val hasReceivedFirstFrame: Boolean = false,
    // Capture / recording intent.
    val isCapturingPhoto: Boolean = false,
    val isRecording: Boolean = false,
    val recordingElapsedSeconds: Long = 0L,
    // Sound-in-video toggle (glasses mic over HFP), locked once recording starts.
    val includeAudioInStream: Boolean = true,
    // The capture currently shown in the shared preview/share sheet (photo or video).
    val activePreview: CapturePreview? = null,
    // Drives the confirm prompt shown before the camera-permission redirect to the Meta AI app.
    val showCameraPermissionRedirectConfirm: Boolean = false,
    // True while the stream-start flow is in flight before the SDK stream state turns STARTING —
    // i.e. while the camera-permission check runs. Folded into isBusy so the Preview button stays
    // disabled for the whole flow, closing the gap where the SDK state machine hasn't moved yet.
    val isStartingStream: Boolean = false,
) {
  /** A session exists and is connected (or connecting); a stream can be started. */
  val hasSession: Boolean
    get() =
        sessionState == DeviceSessionState.STARTING ||
            sessionState == DeviceSessionState.STARTED ||
            sessionState == DeviceSessionState.PAUSED ||
            sessionState == DeviceSessionState.STOPPING

  val isSessionStarting: Boolean
    get() = sessionState == DeviceSessionState.STARTING

  val isSessionActive: Boolean
    get() = sessionState == DeviceSessionState.STARTED

  val isStreaming: Boolean
    get() = streamState == StreamState.STREAMING

  /**
   * The stream is paused by the device (single cap-touch tap); the last frame stays frozen on
   * screen.
   */
  val isPaused: Boolean
    get() = streamState == StreamState.PAUSED

  /** A stream is attached and not in a terminal state. */
  val hasStream: Boolean
    get() = streamState != StreamState.STOPPED && streamState != StreamState.CLOSED

  /**
   * A session or stream start/stop is in flight. The UI shows a spinner and disables controls until
   * the SDK settles on a stable state, so transient states never need their own controls.
   */
  val isBusy: Boolean
    get() =
        isSessionStarting ||
            sessionState == DeviceSessionState.STOPPING ||
            isStartingStream ||
            streamState == StreamState.STARTING ||
            streamState == StreamState.STARTED ||
            streamState == StreamState.STOPPING

  /** Human-readable session/stream state for the status chips. */
  val sessionStateText: String
    get() = sessionState.name.lowercase()

  val streamStateText: String
    get() = streamState.name.lowercase()
}
