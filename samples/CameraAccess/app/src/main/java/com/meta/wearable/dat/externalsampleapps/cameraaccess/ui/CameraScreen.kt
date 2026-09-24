/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// CameraScreen - DAT camera capture screen
//
// A full-bleed camera preview with controls overlaid on a scrim. Walks the SDK's camera lifecycle
// as explicit steps (Start Session -> Start Preview -> Capture / Record -> Stop Preview -> End
// Session) and shows the live DeviceSessionState / StreamState so the state machine is legible.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.camera.CameraUiState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.camera.CameraViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

// Scrims behind the top/bottom bars so the white controls stay legible over the live feed. Hoisted
// so they're allocated once instead of on every bar recomposition (the recording timer ticks).
private val TopScrimBrush =
    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))
private val BottomScrimBrush =
    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
private const val PREVIEW_ASPECT_RATIO = 9f / 16f

@Composable
fun CameraScreen(
    wearablesViewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    onRequestRecordAudioPermission: suspend () -> Boolean,
    modifier: Modifier = Modifier,
    cameraViewModel: CameraViewModel = viewModel(
        factory =
            CameraViewModel.Factory(
                application = (LocalActivity.current as ComponentActivity).application,
                wearablesViewModel = wearablesViewModel,
            ),
    ),
) {
  val ui by cameraViewModel.uiState.collectAsStateWithLifecycle()
  val wearablesUi by wearablesViewModel.uiState.collectAsStateWithLifecycle()
  val activity = LocalActivity.current
  var showSettingsMenu by remember { mutableStateOf(false) }

  val isUpdateRequired = wearablesUi.isFirmwareUpdateRequired

  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    PreviewBackground(
        ui = ui,
        hasActiveDevice = wearablesUi.hasActiveDevice,
        isUpdateRequired = isUpdateRequired,
        onSurfaceChanged = cameraViewModel::setSurface,
    )

    // Tap outside the open settings menu dismisses it.
    if (showSettingsMenu) {
      Box(
          modifier =
              Modifier.fillMaxSize().clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
              ) {
                showSettingsMenu = false
              },
      )
    }

    Column(modifier = Modifier.fillMaxSize()) {
      TopBar(
          ui = ui,
          isDisconnectEnabled = wearablesUi.registrationState == RegistrationState.REGISTERED,
          showSettingsMenu = showSettingsMenu,
          onToggleSettings = { showSettingsMenu = !showSettingsMenu },
          onDisconnect = {
            activity?.let { wearablesViewModel.startUnregistration(it) }
            showSettingsMenu = false
          },
      )

      Spacer(modifier = Modifier.weight(1f))

      BottomBar(
          ui = ui,
          isUpdateRequired = isUpdateRequired,
          hasActiveDevice = wearablesUi.hasActiveDevice,
          onStartSession = cameraViewModel::startSession,
          onEndSession = cameraViewModel::endSession,
          onStartPreview = cameraViewModel::startStreaming,
          onStopPreview = cameraViewModel::stopStreaming,
          onCapturePhoto = cameraViewModel::capturePhoto,
          onToggleRecording = { cameraViewModel.toggleRecording(onRequestRecordAudioPermission) },
          onToggleMic = cameraViewModel::toggleMic,
          onUpdateFirmware = { activity?.let { wearablesViewModel.openFirmwareUpdate(it) } },
      )
    }

    ui.activePreview?.let { preview ->
      CapturePreviewScreen(
          preview = preview,
          onDismiss = { cameraViewModel.dismissCapturePreview() },
      )
    }

    if (ui.showCameraPermissionRedirectConfirm) {
      AlertDialog(
          onDismissRequest = { cameraViewModel.cancelCameraPermissionRedirect() },
          title = { Text(stringResource(R.string.camera_permission_redirect_title)) },
          text = { Text(stringResource(R.string.camera_permission_redirect_message)) },
          confirmButton = {
            TextButton(
                onClick = {
                  cameraViewModel.confirmCameraPermissionRedirect(onRequestWearablesPermission)
                },
            ) {
              Text(stringResource(R.string.camera_permission_continue))
            }
          },
          dismissButton = {
            TextButton(onClick = { cameraViewModel.cancelCameraPermissionRedirect() }) {
              Text(stringResource(R.string.camera_permission_cancel))
            }
          },
      )
    }
  }
}

// MARK: - Preview background

@Composable
private fun PreviewBackground(
    ui: CameraUiState,
    hasActiveDevice: Boolean,
    isUpdateRequired: Boolean,
    onSurfaceChanged: (android.view.Surface?) -> Unit,
) {
  val liveDescription = stringResource(R.string.live_preview)
  Box(modifier = Modifier.fillMaxSize()) {
    if (ui.hasStream) {
      AndroidExternalSurface(
          modifier =
              Modifier.align(Alignment.Center)
                  .wrapContentWidth(align = Alignment.CenterHorizontally, unbounded = true)
                  .fillMaxHeight()
                  .aspectRatio(PREVIEW_ASPECT_RATIO, matchHeightConstraintsFirst = true)
                  .semantics { contentDescription = liveDescription },
      ) {
        onSurface { surface, _, _ ->
          onSurfaceChanged(surface)
          surface.onDestroyed { onSurfaceChanged(null) }
        }
      }
    } else if (!ui.isBusy) {
      StatusPlaceholder(
          ui = ui,
          hasActiveDevice = hasActiveDevice,
          isUpdateRequired = isUpdateRequired,
      )
    }

    // Paused (device-initiated): the surface stays mounted (hasStream is true) so the last frame
    // freezes; dim it and badge it so a held frame reads as intentionally paused, not stalled.
    if (ui.isPaused) {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .background(Color.Black.copy(alpha = 0.35f))
                  .testTag("paused_overlay"),
          contentAlignment = Alignment.Center,
      ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
              imageVector = Icons.Filled.Pause,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(36.dp),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
              text = stringResource(R.string.paused_title),
              color = Color.White,
              fontSize = 20.sp,
              fontWeight = FontWeight.SemiBold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
              text = stringResource(R.string.paused_subtitle),
              color = Color.White.copy(alpha = 0.7f),
              fontSize = 15.sp,
              textAlign = TextAlign.Center,
          )
        }
      }
    }

    if ((ui.isBusy || (ui.hasStream && !ui.hasReceivedFirstFrame)) && !ui.isPaused) {
      Box(
          modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
          contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(color = Color.White)
      }
    }
  }
}

@Composable
private fun StatusPlaceholder(
    ui: CameraUiState,
    hasActiveDevice: Boolean,
    isUpdateRequired: Boolean,
) {
  val title: String
  val subtitle: String?
  val showWaitingRow: Boolean
  when {
    !hasActiveDevice -> {
      title = stringResource(R.string.placeholder_put_on_glasses)
      subtitle = null
      showWaitingRow = true
    }
    isUpdateRequired -> {
      title = stringResource(R.string.update_required_title)
      subtitle = stringResource(R.string.update_required_subtitle)
      showWaitingRow = false
    }
    !ui.hasSession -> {
      title = stringResource(R.string.placeholder_ready_title)
      subtitle = stringResource(R.string.placeholder_ready_subtitle)
      showWaitingRow = false
    }
    else -> {
      title = stringResource(R.string.placeholder_session_started_title)
      subtitle = stringResource(R.string.placeholder_session_started_subtitle)
      showWaitingRow = false
    }
  }

  Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Icon(
        painter = painterResource(id = R.drawable.camera_access_icon),
        contentDescription = stringResource(R.string.camera_access_icon_description),
        tint = Color.White,
        modifier = Modifier.size(88.dp),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    if (subtitle != null) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
    }
    if (showWaitingRow) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
          text = stringResource(R.string.waiting_for_active_device),
          color = Color.White.copy(alpha = 0.7f),
          fontSize = 14.sp,
      )
    }
  }
}

// MARK: - Top bar

@Composable
private fun TopBar(
    ui: CameraUiState,
    isDisconnectEnabled: Boolean,
    showSettingsMenu: Boolean,
    onToggleSettings: () -> Unit,
    onDisconnect: () -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .background(TopScrimBrush)
              .statusBarsPadding()
              .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalAlignment = Alignment.Top,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      StatusChip(
          label = stringResource(R.string.status_session),
          value = ui.sessionStateText,
          active = ui.isSessionActive,
          present = ui.hasSession,
      )
      StatusChip(
          label = stringResource(R.string.status_stream),
          value = ui.streamStateText,
          active = ui.isStreaming,
          present = ui.hasStream,
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    Box {
      // Pinned to TopEnd so it stays put when the Disconnect popover below widens this Box.
      Icon(
          imageVector = Icons.Filled.LinkOff,
          contentDescription = stringResource(R.string.unregister_button_title),
          tint = Color.White,
          modifier =
              Modifier.align(Alignment.TopEnd).size(28.dp).clickable(onClick = onToggleSettings),
      )
      if (showSettingsMenu) {
        SwitchButton(
            label = stringResource(R.string.unregister_button_title),
            onClick = onDisconnect,
            modifier = Modifier.align(Alignment.TopEnd).offset(y = 40.dp).width(150.dp),
            isDestructive = true,
            enabled = isDisconnectEnabled,
        )
      }
    }
  }
}

@Composable
private fun StatusChip(label: String, value: String, active: Boolean, present: Boolean) {
  val dotColor = if (active) AppColor.Green else if (present) AppColor.Yellow else Color.Gray
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
    Text(
        text = "$label: $value",
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
    )
  }
}

// MARK: - Bottom bar

@Composable
private fun BottomBar(
    ui: CameraUiState,
    isUpdateRequired: Boolean,
    hasActiveDevice: Boolean,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onCapturePhoto: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleMic: () -> Unit,
    onUpdateFirmware: () -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(BottomScrimBrush)
              .navigationBarsPadding()
              .padding(horizontal = 24.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (isUpdateRequired) {
      UpdateRequiredMessage()
      SwitchButton(
          label = stringResource(R.string.update_firmware_button_title),
          onClick = onUpdateFirmware,
      )
    } else {
      CaptureRow(
          ui = ui,
          onStartPreview = onStartPreview,
          onStopPreview = onStopPreview,
          onCapturePhoto = onCapturePhoto,
          onToggleRecording = onToggleRecording,
          onToggleMic = onToggleMic,
      )
      AnchoredPrimaryButton(
          ui = ui,
          hasActiveDevice = hasActiveDevice,
          onStartSession = onStartSession,
          onEndSession = onEndSession,
      )
    }
  }
}

@Composable
private fun CaptureRow(
    ui: CameraUiState,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onCapturePhoto: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleMic: () -> Unit,
) {
  // previewActive mirrors iOS `previewIsActive`: live, recording, or tearing down. PAUSED is
  // excluded, so while paused the pill reverts to the (inert) start affordance instead of a live
  // stop button — the paused stream can't be torn down from here.
  val previewActive = ui.isStreaming || ui.isRecording || ui.streamState == StreamState.STOPPING
  val previewDisabled =
      if (previewActive) ui.isRecording || ui.isBusy else !ui.isSessionActive || ui.isBusy
  val captureEnabled = ui.isStreaming
  val micEnabled = ui.isStreaming && !ui.isRecording
  val recordEnabled = ui.isStreaming || ui.isRecording

  Row(
      modifier = Modifier.fillMaxWidth().alpha(if (ui.hasSession) 1f else 0f),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // Preview pill (gateway).
    CapturePill(
        modifier =
            Modifier.weight(1f)
                .testTag(if (previewActive) "stop_preview_button" else "start_preview_button"),
        icon = if (previewActive) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
        label = stringResource(R.string.preview_label),
        contentDescription =
            if (previewActive) stringResource(R.string.stop_preview)
            else stringResource(R.string.start_preview),
        enabled = !previewDisabled,
        onClick = { if (previewActive) onStopPreview() else onStartPreview() },
    )

    // Photo capture.
    CircleIconButton(
        modifier = Modifier.testTag("capture_button"),
        icon = Icons.Filled.PhotoCamera,
        contentDescription = stringResource(R.string.capture_photo),
        enabled = captureEnabled,
        onClick = onCapturePhoto,
    )

    // Record / stop, morphing into a live timer.
    RecordPill(
        modifier = Modifier.weight(1f).testTag("record_button"),
        isRecording = ui.isRecording,
        elapsedSeconds = ui.recordingElapsedSeconds,
        enabled = recordEnabled,
        onClick = onToggleRecording,
    )

    // Sound-in-video toggle.
    CircleIconButton(
        modifier = Modifier.testTag("mic_toggle"),
        icon = if (ui.includeAudioInStream) Icons.Filled.Mic else Icons.Filled.MicOff,
        contentDescription =
            if (ui.includeAudioInStream) stringResource(R.string.mic_on)
            else stringResource(R.string.mic_off),
        enabled = micEnabled,
        tint = if (ui.includeAudioInStream) Color.White else Color.White.copy(alpha = 0.45f),
        onClick = onToggleMic,
    )
  }
}

@Composable
private fun AnchoredPrimaryButton(
    ui: CameraUiState,
    hasActiveDevice: Boolean,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
) {
  // One persistent button so it holds a fixed Y — only its label/style/action change. Start needs
  // an active device; End stays available mid-stream (the SDK cascades the stop), so only an
  // in-flight transition disables it.
  val hasSession = ui.hasSession
  val enabled = if (hasSession) !ui.isBusy else !ui.isBusy && hasActiveDevice
  SwitchButton(
      label =
          if (hasSession) stringResource(R.string.end_session_button)
          else stringResource(R.string.start_session_button),
      onClick = { if (hasSession) onEndSession() else onStartSession() },
      modifier = Modifier.testTag(if (hasSession) "end_session_button" else "start_session_button"),
      isDestructive = hasSession,
      enabled = enabled,
  )
}

@Composable
private fun CapturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier =
          modifier
              .height(50.dp)
              .clip(RoundedCornerShape(percent = 50))
              .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.08f))
              .clickable(enabled = enabled, onClick = onClick)
              .semantics { this.contentDescription = contentDescription }
              .padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
        modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        text = label,
        color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun RecordPill(
    isRecording: Boolean,
    elapsedSeconds: Long,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val background =
      if (isRecording) AppColor.RecordAccent.copy(alpha = 0.5f)
      else Color.White.copy(alpha = if (enabled) 0.18f else 0.08f)
  Row(
      modifier =
          modifier
              .height(50.dp)
              .clip(RoundedCornerShape(percent = 50))
              .background(background)
              .clickable(enabled = enabled, onClick = onClick),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (isRecording) {
      Icon(
          imageVector = Icons.Filled.Stop,
          contentDescription = stringResource(R.string.stop_recording),
          tint = Color.White,
          modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      val minutes = elapsedSeconds / 60
      val seconds = elapsedSeconds % 60
      Text(
          text = String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds),
          color = Color.White,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.testTag("recording_indicator"),
      )
    } else {
      Icon(
          imageVector = Icons.Filled.Videocam,
          contentDescription = stringResource(R.string.record_video),
          tint = if (enabled) AppColor.RecordAccent else Color.White.copy(alpha = 0.45f),
          modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
          text = stringResource(R.string.record_label),
          color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
  Box(
      modifier =
          modifier
              .size(50.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.08f))
              .clickable(enabled = enabled, onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (enabled) tint else Color.White.copy(alpha = 0.45f),
        modifier = Modifier.size(22.dp),
    )
  }
}

@Composable
private fun UpdateRequiredMessage(modifier: Modifier = Modifier) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(AppColor.UpdateRequiredBackground)
              .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
  ) {
    Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = null,
        tint = AppColor.UpdateRequiredForeground,
        modifier = Modifier.size(24.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          text = stringResource(R.string.update_required_title),
          color = AppColor.UpdateRequiredForeground,
          fontWeight = FontWeight.SemiBold,
          fontSize = 16.sp,
      )
      Text(
          text = stringResource(R.string.update_required_firmware_message),
          color = AppColor.UpdateRequiredForeground,
          fontSize = 15.sp,
      )
    }
  }
}
