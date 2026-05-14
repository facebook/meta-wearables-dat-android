/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.externalsampleapps.displayaccess.R
import com.meta.wearable.dat.externalsampleapps.displayaccess.wearables.WearablesUiState

private val ScreenBackground = Color(0xFFF2F2F7)
private val CardBackground = Color.White
private val SectionLabel = Color(0xFF8E8E93)
private val ConnectedColor = Color(0xFF67C46A)
private val DisconnectedColor = Color(0xFFFF5E57)
private val DividerColor = Color(0xFFE5E5EA)
private val CompatibilityIssueBackground = Color(0xFFFFF4D6)
private val CompatibilityIssueColor = Color(0xFF8A4B00)

@Composable
fun ConnectScreen(
    uiState: WearablesUiState,
    selectedDisplayDeviceId: DeviceIdentifier?,
    isDisplayReady: Boolean,
    isPreparingDisplay: Boolean,
    isDatAppUpdateRequired: Boolean,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onOpenFirmwareUpdate: () -> Unit,
    onOpenDatAppUpdate: () -> Unit,
    onSelectDevice: (DeviceIdentifier) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(ScreenBackground)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 28.dp),
  ) {
    Spacer(modifier = Modifier.height(52.dp))

    Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    Spacer(modifier = Modifier.height(36.dp))

    Text(
        text = stringResource(R.string.system_section),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = SectionLabel,
    )

    Spacer(modifier = Modifier.height(14.dp))

    RegistrationCard(
        registrationState = uiState.registrationState,
        onRegister = onRegister,
        onUnregister = onUnregister,
    )

    if (uiState.isFirmwareUpdateRequired || isDatAppUpdateRequired) {
      Spacer(modifier = Modifier.height(14.dp))
      UpdateActionsCard(
          showFirmwareUpdate = uiState.isFirmwareUpdateRequired,
          showDatAppUpdate = isDatAppUpdateRequired,
          onOpenFirmwareUpdate = onOpenFirmwareUpdate,
          onOpenDatAppUpdate = onOpenDatAppUpdate,
      )
    }

    Spacer(modifier = Modifier.height(34.dp))

    Text(
        text = stringResource(R.string.devices_section),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = SectionLabel,
    )

    Spacer(modifier = Modifier.height(14.dp))

    DeviceListCard(
        uiState = uiState,
        selectedDisplayDeviceId = selectedDisplayDeviceId,
        isDisplayReady = isDisplayReady,
        isPreparingDisplay = isPreparingDisplay,
        onSelectDevice = onSelectDevice,
    )
  }
}

@Composable
private fun UpdateActionsCard(
    showFirmwareUpdate: Boolean,
    showDatAppUpdate: Boolean,
    onOpenFirmwareUpdate: () -> Unit,
    onOpenDatAppUpdate: () -> Unit,
) {
  val issueMessage =
      when {
        showFirmwareUpdate && showDatAppUpdate ->
            stringResource(R.string.compatibility_issue_both_message)
        showFirmwareUpdate -> stringResource(R.string.compatibility_issue_firmware_message)
        else -> stringResource(R.string.compatibility_issue_dat_app_message)
      }

  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(26.dp))
              .background(CompatibilityIssueBackground)
              .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
      Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = null,
          tint = CompatibilityIssueColor,
          modifier = Modifier.size(28.dp),
      )
      Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
            text = stringResource(R.string.compatibility_issue_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = CompatibilityIssueColor,
        )
        Text(
            text = issueMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = CompatibilityIssueColor,
        )
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (showFirmwareUpdate) {
        CompatibilityActionButton(
            text = stringResource(R.string.update_firmware_button),
            onClick = onOpenFirmwareUpdate,
        )
      }

      if (showDatAppUpdate) {
        CompatibilityActionButton(
            text = stringResource(R.string.update_dat_app_button),
            onClick = onOpenDatAppUpdate,
        )
      }
    }
  }
}

@Composable
private fun CompatibilityActionButton(
    text: String,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(999.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = CompatibilityIssueColor,
              contentColor = Color.White,
          ),
  ) {
    Text(text)
  }
}

@Composable
private fun RegistrationCard(
    registrationState: RegistrationState,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(26.dp))
              .background(CardBackground)
              .padding(20.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      val (label, color, icon) =
          when (registrationState) {
            RegistrationState.UNAVAILABLE ->
                Triple(
                    stringResource(R.string.status_unavailable),
                    DisconnectedColor,
                    Icons.Filled.RemoveCircle,
                )
            RegistrationState.AVAILABLE ->
                Triple(
                    stringResource(R.string.status_available),
                    Color(0xFFFFB84D),
                    Icons.Filled.RemoveCircle,
                )
            RegistrationState.REGISTERING ->
                Triple(
                    stringResource(R.string.status_registering),
                    Color(0xFFFFB84D),
                    Icons.Filled.Pending,
                )
            RegistrationState.UNREGISTERING ->
                Triple(
                    stringResource(R.string.status_unregistering),
                    Color(0xFFFFB84D),
                    Icons.Filled.Pending,
                )
            RegistrationState.REGISTERED ->
                Triple(
                    stringResource(R.string.status_registered),
                    ConnectedColor,
                    Icons.Filled.CheckCircle,
                )
          }

      Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(34.dp))
      Spacer(modifier = Modifier.width(14.dp))
      Text(
          text = label,
          style = MaterialTheme.typography.headlineSmall,
          color = color,
      )
      Spacer(modifier = Modifier.weight(1f))

      when (registrationState) {
        RegistrationState.REGISTERED -> {
          Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = stringResource(R.string.unregister_button),
              tint = DisconnectedColor,
              modifier = Modifier.size(30.dp).clickable(onClick = onUnregister),
          )
        }
        RegistrationState.AVAILABLE,
        RegistrationState.UNAVAILABLE -> {
          Button(
              onClick = onRegister,
              shape = RoundedCornerShape(999.dp),
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = Color(0xFF3478F6),
                      contentColor = Color.White,
                  ),
          ) {
            Text(stringResource(R.string.register_button))
          }
        }
        RegistrationState.REGISTERING,
        RegistrationState.UNREGISTERING -> Unit
      }
    }
  }
}

@Composable
private fun DeviceListCard(
    uiState: WearablesUiState,
    selectedDisplayDeviceId: DeviceIdentifier?,
    isDisplayReady: Boolean,
    isPreparingDisplay: Boolean,
    onSelectDevice: (DeviceIdentifier) -> Unit,
) {
  val deviceEntries =
      uiState.devicesMetadata.entries.sortedWith(
          compareByDescending<Map.Entry<DeviceIdentifier, Device>> { entry ->
                entry.value.linkState == LinkState.CONNECTED
              }
              .thenBy { entry -> entry.value.name.lowercase() },
      )
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(30.dp))
              .background(CardBackground)
              .padding(18.dp),
  ) {
    if (deviceEntries.isEmpty()) {
      Text(
          text = stringResource(R.string.no_devices_found),
          style = MaterialTheme.typography.bodyLarge,
          color = SectionLabel,
      )
    } else {
      Column {
        deviceEntries.forEachIndexed { index, entry ->
          DeviceRow(
              deviceId = entry.key,
              device = entry.value,
              selectedDisplayDeviceId = selectedDisplayDeviceId,
              isDisplayReady = isDisplayReady,
              isPreparingDisplay = isPreparingDisplay,
              onSelectDevice = onSelectDevice,
          )
          if (index != deviceEntries.lastIndex) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = DividerColor,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DeviceRow(
    deviceId: DeviceIdentifier,
    device: Device,
    selectedDisplayDeviceId: DeviceIdentifier?,
    isDisplayReady: Boolean,
    isPreparingDisplay: Boolean,
    onSelectDevice: (DeviceIdentifier) -> Unit,
) {
  val isDisplayDevice = device.isDisplayCapable()
  val isConnected = device.linkState == LinkState.CONNECTED
  val isSelected = selectedDisplayDeviceId == deviceId
  val isFirmwareUpdateRequired = device.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED
  val statusText =
      when {
        isFirmwareUpdateRequired -> stringResource(R.string.device_update_required)
        isSelected && isPreparingDisplay -> stringResource(R.string.device_preparing)
        isSelected && isDisplayReady -> stringResource(R.string.device_connected)
        isConnected -> stringResource(R.string.device_connected)
        else -> stringResource(R.string.device_disconnected)
      }
  val statusColor =
      when {
        isFirmwareUpdateRequired -> CompatibilityIssueColor
        isSelected && isPreparingDisplay -> Color(0xFFFFB84D)
        isSelected && isDisplayReady -> ConnectedColor
        isConnected -> ConnectedColor
        else -> DisconnectedColor
      }

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(enabled = isConnected && isDisplayDevice) { onSelectDevice(deviceId) }
              .padding(vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = device.name,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text = device.deviceType.description,
          style = MaterialTheme.typography.bodyMedium,
          color = SectionLabel,
          modifier = Modifier.padding(top = 4.dp),
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    Text(
        text = statusText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = statusColor,
    )
  }
}
