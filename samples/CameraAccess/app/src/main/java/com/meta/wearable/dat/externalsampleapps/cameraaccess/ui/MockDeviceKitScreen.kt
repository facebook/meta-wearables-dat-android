/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// MockDeviceKitScreen - DAT Testing Interface
//
// This screen allows developers to simulate wearable devices and test DAT functionality without
// hardware.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.mockdevicekit.MockDeviceInfo
import com.meta.wearable.dat.externalsampleapps.cameraaccess.mockdevicekit.MockDeviceKitViewModel
import com.meta.wearable.dat.mockdevice.api.camera.CameraFacing

@Composable
fun MockDeviceKitScreen(
    modifier: Modifier = Modifier,
    viewModel: MockDeviceKitViewModel = viewModel(LocalActivity.current as ComponentActivity),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = stringResource(R.string.mock_device_kit_title),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
          )
          Text(
              text = stringResource(R.string.devices_paired_count, uiState.pairedDevices.size),
              style = MaterialTheme.typography.bodyMedium,
              color = AppColor.Green,
              textAlign = TextAlign.Center,
          )
        }
        Text(
            text = stringResource(R.string.mock_device_kit_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()

        if (uiState.isEnabled) {
          ActionButton(
              modifier = Modifier.fillMaxWidth(),
              text = stringResource(R.string.disable_mock_device_kit),
              onClick = { viewModel.disable() },
              containerColor = AppColor.Red,
          )
        } else {
          ActionButton(
              modifier = Modifier.fillMaxWidth(),
              text = stringResource(R.string.enable_mock_device_kit),
              onClick = { viewModel.enable() },
              containerColor = AppColor.Green,
          )
        }

        if (uiState.isEnabled) {
          ActionButton(
              modifier = Modifier.fillMaxWidth(),
              text = stringResource(R.string.pair_rayban_meta),
              onClick = { viewModel.pairRaybanMeta() },
              enabled = uiState.pairedDevices.size < 3,
          )
        }
      }
    }

    if (uiState.isEnabled && uiState.pairedDevices.isNotEmpty()) {
      uiState.pairedDevices.forEach { deviceInfo ->
        MockDeviceCard(deviceInfo = deviceInfo, viewModel = viewModel)
      }
    }
  }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AppColor.DeepBlue,
    contentColor: Color = Color.White,
) {
  Button(
      modifier = modifier,
      colors =
          ButtonDefaults.buttonColors(
              containerColor = containerColor,
              contentColor = contentColor,
          ),
      onClick = onClick,
      enabled = enabled,
  ) {
    Text(text, fontWeight = FontWeight.Medium)
  }
}

@Composable
private fun MockDeviceCard(
    deviceInfo: MockDeviceInfo,
    viewModel: MockDeviceKitViewModel,
) {
  val videoPickerLauncher =
      rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri?
        ->
        uri?.let { selectedUri -> viewModel.setCameraFeed(deviceInfo, selectedUri) }
      }
  val imagePickerLauncher =
      rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri?
        ->
        uri?.let { selectedUri -> viewModel.setCapturedImage(deviceInfo, selectedUri) }
      }

  // Camera permission handling for phone camera source
  var pendingCameraFacing by remember { mutableStateOf<CameraFacing?>(null) }
  var showCameraPermissionAlert by remember { mutableStateOf(false) }
  val cameraPermissionLauncher =
      rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
          granted ->
        if (granted) {
          pendingCameraFacing?.let { facing -> viewModel.setCameraFeed(deviceInfo, facing) }
        } else {
          showCameraPermissionAlert = true
        }
        pendingCameraFacing = null
      }

  var expanded by remember { mutableStateOf(true) }

  val isPoweredOn = deviceInfo.isPoweredOn
  val isDonned = deviceInfo.isDonned
  val isUnfolded = deviceInfo.isUnfolded

  val isCameraSourceSelected =
      deviceInfo.cameraSource == CameraFacing.FRONT || deviceInfo.cameraSource == CameraFacing.BACK

  Card(
      modifier = Modifier.fillMaxWidth(),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // Header row: device name + unpair button, tappable to expand/collapse
      Row(
          modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = deviceInfo.deviceName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
          Text(
              text = deviceInfo.deviceId,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
        }
        Button(
            onClick = { viewModel.unpairDevice(deviceInfo) },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColor.Red,
                    contentColor = Color.White,
                ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp),
        ) {
          Text(
              text = stringResource(R.string.unpair),
              style = MaterialTheme.typography.labelMedium,
          )
        }
      }

      // Collapsible content
      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          HorizontalDivider(
              color = MaterialTheme.colorScheme.outlineVariant,
              modifier = Modifier.padding(vertical = 4.dp),
          )

          // Toggles with tighter spacing
          Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // Power toggle
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                  text = stringResource(R.string.power),
                  style = MaterialTheme.typography.bodyMedium,
              )
              Switch(
                  checked = isPoweredOn,
                  onCheckedChange = { checked ->
                    if (checked) viewModel.powerOn(deviceInfo) else viewModel.powerOff(deviceInfo)
                  },
                  colors = SwitchDefaults.colors(checkedTrackColor = AppColor.Green),
              )
            }

            // Donned toggle
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                  text = stringResource(R.string.donned),
                  style = MaterialTheme.typography.bodyMedium,
              )
              Switch(
                  checked = isDonned,
                  onCheckedChange = { checked ->
                    if (checked) viewModel.don(deviceInfo) else viewModel.doff(deviceInfo)
                  },
                  colors = SwitchDefaults.colors(checkedTrackColor = AppColor.Green),
              )
            }

            // Unfolded toggle
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                  text = stringResource(R.string.unfolded),
                  style = MaterialTheme.typography.bodyMedium,
              )
              Switch(
                  checked = isUnfolded,
                  onCheckedChange = { checked ->
                    if (checked) viewModel.unfold(deviceInfo) else viewModel.fold(deviceInfo)
                  },
                  colors = SwitchDefaults.colors(checkedTrackColor = AppColor.Green),
              )
            }
          }

          // Camera source dropdown
          CameraSourceDropdown(
              deviceInfo = deviceInfo,
              onFrontCamera = {
                pendingCameraFacing = CameraFacing.FRONT
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
              },
              onBackCamera = {
                pendingCameraFacing = CameraFacing.BACK
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
              },
              onVideoFile = { videoPickerLauncher.launch("video/*") },
          )

          // Captured image control — hidden when a camera source (front/back) is selected
          if (!isCameraSourceSelected) {
            if (deviceInfo.hasCapturedImage) {
              Text(
                  text = stringResource(R.string.has_captured_image),
                  style = MaterialTheme.typography.bodySmall,
                  color = AppColor.Green,
              )
            }
            ActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.select_image),
                onClick = { imagePickerLauncher.launch("image/*") },
            )
          }
        }
      }
    }
  }

  if (showCameraPermissionAlert) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { showCameraPermissionAlert = false },
        title = { Text(stringResource(R.string.camera_permission_alert_title)) },
        text = { Text(stringResource(R.string.camera_permission_alert_message)) },
        confirmButton = {
          TextButton(
              onClick = {
                showCameraPermissionAlert = false
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                      data = Uri.fromParts("package", context.packageName, null)
                    }
                context.startActivity(intent)
              },
          ) {
            Text(stringResource(R.string.camera_permission_open_settings))
          }
        },
        dismissButton = {
          TextButton(onClick = { showCameraPermissionAlert = false }) {
            Text(stringResource(R.string.camera_permission_cancel))
          }
        },
    )
  }
}

@Composable
private fun CameraSourceDropdown(
    deviceInfo: MockDeviceInfo,
    onFrontCamera: () -> Unit,
    onBackCamera: () -> Unit,
    onVideoFile: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  val currentSourceLabel =
      when {
        deviceInfo.cameraSource == CameraFacing.FRONT -> stringResource(R.string.front_camera)
        deviceInfo.cameraSource == CameraFacing.BACK -> stringResource(R.string.back_camera)
        deviceInfo.hasCameraFeed -> stringResource(R.string.camera_source_video_file)
        else -> stringResource(R.string.camera_source_none)
      }

  Box(modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Text(
          text = stringResource(R.string.camera_source_label, currentSourceLabel),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
      )
      Icon(
          imageVector = Icons.Default.ArrowDropDown,
          contentDescription = null,
      )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
      DropdownMenuItem(
          text = { Text(stringResource(R.string.front_camera)) },
          onClick = {
            onFrontCamera()
            expanded = false
          },
      )
      DropdownMenuItem(
          text = { Text(stringResource(R.string.back_camera)) },
          onClick = {
            onBackCamera()
            expanded = false
          },
      )
      DropdownMenuItem(
          text = { Text(stringResource(R.string.camera_source_video_file)) },
          onClick = {
            onVideoFile()
            expanded = false
          },
      )
    }
  }
}
