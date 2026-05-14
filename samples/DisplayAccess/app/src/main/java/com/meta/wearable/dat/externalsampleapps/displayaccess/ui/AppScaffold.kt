/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.externalsampleapps.displayaccess.R
import com.meta.wearable.dat.externalsampleapps.displayaccess.SampleApp
import com.meta.wearable.dat.externalsampleapps.displayaccess.display.DisplayViewModel
import com.meta.wearable.dat.externalsampleapps.displayaccess.wearables.WearablesViewModel

object Routes {
  const val CONNECT = "connect"
  const val SAMPLES_LIST = "samples_list"
}

private val BackgroundColor = Color(0xFFF2F2F7)
private val TabContainerColor = Color(0xFFF6F6FA)
private val ActiveTabColor = Color(0xFFE7E7ED)
private val ActiveBlue = Color(0xFF3478F6)

@Composable
fun AppScaffold(
    wearablesViewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
) {
  val navController = rememberNavController()
  val wearablesState by wearablesViewModel.uiState.collectAsStateWithLifecycle()
  val displayViewModel: DisplayViewModel = viewModel()
  val displayState by displayViewModel.uiState.collectAsStateWithLifecycle()
  val isCapabilityReady = displayState.displayState == DisplayState.STARTED
  val lastKnownSessionActive = remember { mutableStateOf(displayState.isSessionActive) }
  val activity = LocalActivity.current
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: Routes.CONNECT

  fun openSamples() {
    navController.navigate(Routes.SAMPLES_LIST) {
      popUpTo(Routes.CONNECT)
      launchSingleTop = true
    }
  }

  fun openSettings() {
    navController.navigate(Routes.CONNECT) {
      popUpTo(Routes.CONNECT)
      launchSingleTop = true
    }
  }

  DisposableEffect(displayState.isSessionActive) {
    val sessionJustStarted = !lastKnownSessionActive.value && displayState.isSessionActive
    lastKnownSessionActive.value = displayState.isSessionActive
    if (sessionJustStarted && currentRoute == Routes.CONNECT) {
      openSamples()
    }
    onDispose {}
  }

  Scaffold(
      modifier = modifier,
      containerColor = BackgroundColor,
      bottomBar = {
        BottomTabBar(
            currentRoute = currentRoute,
            samplesEnabled = true,
            onOpenSamples = ::openSamples,
            onOpenSettings = ::openSettings,
        )
      },
  ) { contentPadding ->
    NavHost(
        navController = navController,
        startDestination = Routes.CONNECT,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
      composable(Routes.CONNECT) {
        ConnectScreen(
            uiState = wearablesState,
            selectedDisplayDeviceId = displayState.selectedDeviceId,
            isDisplayReady = isCapabilityReady,
            isPreparingDisplay = displayState.isPreparingDisplay,
            isDatAppUpdateRequired = displayState.isDatAppUpdateRequired,
            onRegister = { activity?.let { wearablesViewModel.startRegistration(it) } },
            onUnregister = {
              displayViewModel.stopSession()
              activity?.let { wearablesViewModel.startUnregistration(it) }
            },
            onOpenFirmwareUpdate = { activity?.let { wearablesViewModel.openFirmwareUpdate(it) } },
            onOpenDatAppUpdate = {
              activity?.let { wearablesViewModel.openDATGlassesAppUpdate(it) }
            },
            onSelectDevice = { deviceId -> displayViewModel.prepareDisplayConnection(deviceId) },
        )
      }

      composable(Routes.SAMPLES_LIST) {
        SamplesListScreen(
            isTryItEnabled = displayState.isSessionActive && isCapabilityReady,
            onSampleSelected = displayViewModel::sendSampleToDisplay,
        )
      }

      SampleApp.entries.forEach { sample ->
        composable(sample.route) {
          SamplePlaceholderScreen(
              sample = sample,
              onBack = { navController.popBackStack() },
          )
        }
      }
    }
  }
}

@Composable
private fun BottomTabBar(
    currentRoute: String,
    samplesEnabled: Boolean,
    onOpenSamples: () -> Unit,
    onOpenSettings: () -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 72.dp, vertical = 18.dp)
              .clip(RoundedCornerShape(999.dp))
              .background(TabContainerColor)
              .padding(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    BottomTab(
        label = stringResource(R.string.samples_tab),
        icon = {
          Icon(
              imageVector = Icons.Outlined.RemoveRedEye,
              contentDescription = null,
              modifier = Modifier.size(26.dp),
          )
        },
        selected = currentRoute == Routes.SAMPLES_LIST,
        enabled = samplesEnabled,
        activeContentColor = Color.Black,
        inactiveContentColor = Color.Black.copy(alpha = 0.35f),
        onClick = onOpenSamples,
        modifier = Modifier.weight(1f),
    )
    BottomTab(
        label = stringResource(R.string.settings_tab),
        icon = {
          Icon(
              imageVector = Icons.Filled.Settings,
              contentDescription = null,
              modifier = Modifier.size(28.dp),
          )
        },
        selected = currentRoute == Routes.CONNECT,
        enabled = true,
        activeContentColor = ActiveBlue,
        inactiveContentColor = Color.Black.copy(alpha = 0.35f),
        onClick = onOpenSettings,
        modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun BottomTab(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    enabled: Boolean,
    activeContentColor: Color,
    inactiveContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .clip(CircleShape)
              .background(if (selected) ActiveTabColor else Color.Transparent)
              .clickable(enabled = enabled, onClick = onClick)
              .padding(vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val contentColor =
        when {
          !enabled -> inactiveContentColor
          selected -> activeContentColor
          else -> inactiveContentColor
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
      androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.material3.LocalContentColor provides contentColor,
      ) {
        icon()
      }
    }
    Text(
        text = label,
        color = contentColor,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 6.dp),
    )
  }
}
