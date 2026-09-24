/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.permissions.SystemPermissionsController
import com.meta.pixelandtexel.birdspotter.domain.Permission
import com.meta.pixelandtexel.birdspotter.domain.PermissionStatus
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * Identify — the two ways to put a name to a bird, once the app has the access they need.
 *
 * The page is all-or-nothing. Until camera, microphone, and location are all granted it is a single
 * access gate: each permission's status, and the one tap that turns it on — inline the first time,
 * then off to Settings. The moment the last one is granted the gate gives way to the two cards: a
 * step-by-step questionnaire that narrows the bundled guide, and a real-time flow over the camera
 * and mic (the glasses a hands-free bonus, never a requirement).
 *
 * This stateful half owns the permission state: an [IdentifyViewModel] refreshed on every resume
 * (Settings is where a status changes), a result launcher for the prompt, and the run to Settings
 * once the prompt won't return. The real-time cover is still raised by the shell (see
 * [onOpenRealtime]) so it can cover the bottom bar.
 */
@Composable
fun IdentifyScreen(
    onStartStepByStep: () -> Unit,
    onOpenRealtime: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: IdentifyViewModel = viewModel(
      factory = IdentifyViewModel.factory(application.container.permissionsController),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Settings is a round trip out of the app; coming back is when a granted/denied flip becomes
  // visible, so re-read the moment the screen resumes.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Permissions we have already raised the prompt for this session; a second tap on one still
  // not granted means the dialog will not return, so route to Settings instead.
  val prompted = remember { mutableSetOf<Permission>() }
  val launcher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    viewModel.refresh()
  }

  IdentifyScreen(
      uiState = uiState,
      onStartStepByStep = onStartStepByStep,
      onOpenRealtime = onOpenRealtime,
      onEnable = { permission ->
        if (permission in prompted) {
          viewModel.openSettings()
        } else {
          prompted.add(permission)
          launcher.launch(
              SystemPermissionsController.androidPermissions(permission).toTypedArray(),
          )
        }
      },
      modifier = modifier,
  )
}

/**
 * The stateless half, so Previews and UI tests can drive the gate and the cards without the app
 * graph.
 */
@Composable
fun IdentifyScreen(
    uiState: IdentifyUiState,
    onStartStepByStep: () -> Unit,
    onOpenRealtime: () -> Unit,
    onEnable: (Permission) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(BirdSpotterTheme.colors.paper)
              .windowInsetsPadding(WindowInsets.statusBars)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = space.gutter)
              .padding(top = space.section, bottom = space.page),
  ) {
    TitleArea()

    if (uiState.allGranted) {
      StepByStepCard(
          onClick = onStartStepByStep,
          modifier = Modifier.padding(top = space.section),
      )
      RealtimeCard(
          onClick = onOpenRealtime,
          modifier = Modifier.padding(top = space.separate),
      )
    } else {
      AccessGate(
          uiState = uiState,
          onEnable = onEnable,
          modifier = Modifier.padding(top = space.section),
      )
    }
  }
}

/** Eyebrow over a headline, no chrome above it — the same opening Explore makes. */
@Composable
private fun TitleArea(modifier: Modifier = Modifier) {
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.related),
  ) {
    PlateLabel(text = "Identify", color = BirdSpotterTheme.colors.gilt)
    Text(
        text = "What bird was that?",
        style = BirdSpotterTheme.type.display,
        color = BirdSpotterTheme.colors.textPrimary,
    )
  }
}

/** The wizard's front door: three questions against the bundled guide, fully offline. */
@Composable
private fun StepByStepCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  CardSurface(modifier = modifier, onClick = onClick) {
    Column(Modifier.padding(space.cardInset)) {
      PlateLabel(text = "Step by Step", color = BirdSpotterTheme.colors.verdigris)
      Spacer(Modifier.height(space.related))
      Text(
          text = "Answer three quick questions.",
          style = BirdSpotterTheme.type.title,
          color = BirdSpotterTheme.colors.textPrimary,
      )
      Spacer(Modifier.height(space.snug))
      Text(
          text =
              "Size, colors, and what it was doing — " +
                  "the guide narrows to the birds that match.",
          style = BirdSpotterTheme.type.body,
          color = BirdSpotterTheme.colors.textSecondary,
      )
    }
  }
}

/**
 * The real-time flow's front door. The mic is the way in; the camera and the glasses are both
 * optional.
 *
 * **No "Preview" plate.** It was hedging from when the session was half-built, and it stopped being
 * true — the session records, identifies, and writes an outing. A card that apologises for itself
 * in gilt is the first thing an audience reads on the tab this demo is *about*.
 */
@Composable
private fun RealtimeCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  CardSurface(modifier = modifier, onClick = onClick) {
    Column(Modifier.padding(space.cardInset)) {
      PlateLabel(text = "Real-Time", color = BirdSpotterTheme.colors.verdigris)
      Spacer(Modifier.height(space.related))
      Text(
          text = "Start listening.",
          style = BirdSpotterTheme.type.title,
          color = BirdSpotterTheme.colors.textPrimary,
      )
      Spacer(Modifier.height(space.snug))
      Text(
          text =
              "The mic runs while you watch. Take a photo, say what you see, " +
                  "and it all lands on one timeline.",
          style = BirdSpotterTheme.type.body,
          color = BirdSpotterTheme.colors.textSecondary,
      )
    }
  }
}

/**
 * The access gate: the whole page until every permission is granted. A line of why, then one row
 * per permission — each reading its status and, when it is not granted, offering the one tap that
 * enables it.
 */
@Composable
private fun AccessGate(
    uiState: IdentifyUiState,
    onEnable: (Permission) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Text(
        text = "Turn on camera, microphone, and location to start identifying birds.",
        style = BirdSpotterTheme.type.body,
        color = BirdSpotterTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(BirdSpotterTheme.space.separate))
    CardSurface {
      Column {
        PermissionRow(name = "Camera", status = uiState.cameraStatus) {
          onEnable(Permission.CAMERA)
        }
        HairlineRule()
        PermissionRow(name = "Microphone", status = uiState.microphoneStatus) {
          onEnable(Permission.MICROPHONE)
        }
        HairlineRule()
        PermissionRow(name = "Location", status = uiState.locationStatus) {
          onEnable(Permission.LOCATION)
        }
      }
    }
  }
}

/**
 * One permission's line: its name, and either a quiet "On" or a verdigris "Enable" that takes the
 * tap. A granted row is settled — it does not invite a tap it has nothing to do with.
 */
@Composable
private fun PermissionRow(name: String, status: PermissionStatus, onEnable: () -> Unit) {
  val space = BirdSpotterTheme.space
  val granted = status == PermissionStatus.GRANTED
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .then(if (granted) Modifier else Modifier.clickable(onClick = onEnable))
              .padding(horizontal = space.cardInset, vertical = space.related),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = name,
        style = BirdSpotterTheme.type.body,
        color = BirdSpotterTheme.colors.textPrimary,
        modifier = Modifier.weight(1f),
    )
    PlateLabel(
        text = if (granted) "On" else "Enable",
        color =
            if (granted) BirdSpotterTheme.colors.textFaint else BirdSpotterTheme.colors.verdigris,
    )
  }
}

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Access gate")
@Composable
private fun IdentifyScreenGatePreview() {
  BirdSpotterTheme {
    // Camera and mic on, location still to grant — the gate, with one row left to enable.
    IdentifyScreen(
        uiState =
            IdentifyUiState(
                cameraStatus = PermissionStatus.GRANTED,
                microphoneStatus = PermissionStatus.GRANTED,
                locationStatus = PermissionStatus.NOT_DETERMINED,
            ),
        onStartStepByStep = {},
        onOpenRealtime = {},
        onEnable = {},
    )
  }
}

@Preview(showBackground = true, name = "Granted", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IdentifyScreenGrantedDarkPreview() {
  BirdSpotterTheme(darkTheme = true) {
    IdentifyScreen(
        uiState =
            IdentifyUiState(
                cameraStatus = PermissionStatus.GRANTED,
                microphoneStatus = PermissionStatus.GRANTED,
                locationStatus = PermissionStatus.GRANTED,
            ),
        onStartStepByStep = {},
        onOpenRealtime = {},
        onEnable = {},
    )
  }
}
