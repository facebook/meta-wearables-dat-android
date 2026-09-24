/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.mockdevice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication

/**
 * What floats over the whole app while the mock is on: the button, or — once it is tapped — the
 * panel of controls in its place.
 *
 * Nothing at all while the kit is off, which is the common case: the overlay is composed once in
 * the shell and stays, and switching the mock off in Settings simply empties it.
 *
 * Placed last in the shell's root box, above the real-time cover and the splash, because the
 * controls have to reach whatever is on screen — most of all the session the cover holds. A box
 * with nothing to touch lets every touch through to what is under it; only the button and the open
 * panel are ever hit targets.
 */
@Composable
fun MockDeviceOverlay(modifier: Modifier = Modifier) {
  val container = (LocalContext.current.applicationContext as BirdSpotterApplication).container
  val viewModel: MockDeviceViewModel = viewModel(
      factory = MockDeviceViewModel.factory(container.mockDeviceRepository),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Saveable so a rotation does not close the panel mid-demo.
  var isPanelOpen by rememberSaveable { mutableStateOf(false) }
  var position by remember { mutableStateOf(container.mockDeviceSettingsStore.buttonPosition) }

  LaunchedEffect(uiState.isEnabled) {
    if (!uiState.isEnabled) isPanelOpen = false
  }

  if (!uiState.isEnabled) return

  if (isPanelOpen) {
    MockDeviceScreen(
        viewModel = viewModel,
        onClose = { isPanelOpen = false },
        modifier = modifier.fillMaxSize(),
    )
  } else {
    // The button roams the whole screen, insets included — it is chrome over chrome, and
    // a corner under the navigation bar is a fine place to park it.
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier.fillMaxSize().onSizeChanged { bounds = it },
    ) {
      if (bounds != IntSize.Zero) {
        MockDeviceButton(
            position = position,
            bounds = bounds,
            onTap = { isPanelOpen = true },
            onMove = { moved ->
              position = moved
              container.mockDeviceSettingsStore.buttonPosition = moved
            },
        )
      }
    }
  }
}
