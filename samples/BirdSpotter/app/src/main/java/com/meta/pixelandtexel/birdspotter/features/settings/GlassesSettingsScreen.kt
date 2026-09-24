/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.dat.datPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesCompatibility
import com.meta.pixelandtexel.birdspotter.domain.GlassesDeviceInfo
import com.meta.pixelandtexel.birdspotter.domain.GlassesPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesRegistrationState
import com.meta.pixelandtexel.birdspotter.domain.GlassesThermalLevel
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButton
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButtonTone
import com.meta.pixelandtexel.birdspotter.ui.components.DisclosureRow
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import com.meta.wearable.dat.core.Wearables

/**
 * Meta AI Glasses — where the link stands, pushed from the Settings row once registration has
 * happened.
 *
 * Four readings and three raises. The readings ([GlassesSettingsViewModel]) are the registration
 * state, the glasses Meta AI lists, and DAT's two grants — camera and microphone. The raises are
 * those two grant flows (DAT's own Activity Result contract) and unlinking — all handed to the Meta
 * AI app, all landing their outcome back in the readings, and all here in the screen rather than
 * the view model for the Activity reason
 * [com.meta.pixelandtexel.birdspotter.domain.PermissionsController] documents.
 */
@Composable
fun GlassesSettingsScreen(
    onBack: () -> Unit,
    onOpenSpeechTest: () -> Unit,
    onOpenCameraScreen: () -> Unit,
    onOpenDisplayScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val container = (LocalContext.current.applicationContext as BirdSpotterApplication).container
  val viewModel: GlassesSettingsViewModel = viewModel(
      factory = GlassesSettingsViewModel.factory(container.glassesSessionRepository),
  )
  val registrationState by viewModel.registrationState.collectAsStateWithLifecycle()
  val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
  val cameraAccess by viewModel.cameraAccess.collectAsStateWithLifecycle()
  val microphoneAccess by viewModel.microphoneAccess.collectAsStateWithLifecycle()
  val context = LocalContext.current

  // The Meta AI grant flow, raised through DAT's own Activity Result contract. One
  // launcher for both grants — the contract takes the permission at launch, and the
  // result payload is not read either way: the view model re-asks instead, so a row can
  // never disagree with a grant that arrived some other way.
  val permissionLauncher = rememberLauncherForActivityResult(
      Wearables.RequestPermissionContract(),
  ) {
    viewModel.refreshAccess()
  }

  GlassesSettingsContent(
      registrationState = registrationState,
      deviceInfo = deviceInfo,
      cameraAccess = cameraAccess,
      microphoneAccess = microphoneAccess,
      onBack = onBack,
      onOpenSpeechTest = onOpenSpeechTest,
      onOpenCameraScreen = onOpenCameraScreen,
      onOpenDisplayScreen = onOpenDisplayScreen,
      onGrant = { permission -> permissionLauncher.launch(permission.datPermission) },
      onUnlink = {
        context.findActivity()?.let { activity ->
          Wearables.startUnregistration(activity)
        }
      },
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassesSettingsContent(
    registrationState: GlassesRegistrationState?,
    deviceInfo: GlassesDeviceInfo?,
    cameraAccess: GlassesAccess?,
    microphoneAccess: GlassesAccess?,
    onBack: () -> Unit,
    onOpenSpeechTest: () -> Unit,
    onOpenCameraScreen: () -> Unit,
    onOpenDisplayScreen: () -> Unit,
    onGrant: (GlassesPermission) -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Meta AI Glasses") },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(
                    painter = glyph(BirdSpotterTheme.glyphs.back),
                    contentDescription = "Back",
                )
              }
            },
        )
      },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter, vertical = space.separate),
        verticalArrangement = Arrangement.spacedBy(space.section),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
        Text(
            text = "Status",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        StatusRow(
            label = "Registration",
            value =
                when (registrationState) {
                  GlassesRegistrationState.REGISTERED -> "Linked"
                  GlassesRegistrationState.REGISTERING -> "Waiting on Meta AI"
                  GlassesRegistrationState.AVAILABLE -> "Not linked"
                  GlassesRegistrationState.UNAVAILABLE -> "Meta AI unreachable"
                  null -> "—"
                },
        )
        StatusRow(
            label = "Glasses",
            value = deviceInfo?.name ?: "None found",
            support = deviceInfo?.let(::glassesSupport),
        )
        deviceInfo?.let { device ->
          StatusRow(label = "Worn", value = wornValue(device))
          StatusRow(label = "Battery", value = batteryValue(device))
          StatusRow(
              label = "Temperature",
              value = thermalValue(device),
              support = thermalSupport(device),
          )
        }
        AccessRow(
            label = "Camera access",
            access = cameraAccess,
            support = "What the live session photographs through",
            onGrant = { onGrant(GlassesPermission.CAMERA) },
        )
        AccessRow(
            label = "Microphone access",
            access = microphoneAccess,
            support =
                "Meta AI's grant for the glasses' microphones — recording " +
                    "still takes the Bluetooth headset route",
            onGrant = { onGrant(GlassesPermission.MICROPHONE) },
        )
      }

      // The one reading on this screen that cannot be taken without opening a session,
      // so it is a door rather than a row. Speech is the only capability the app uses
      // that some pairs simply do not have, and nothing above tells them apart — see
      // [SpeechTestViewModel].
      Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
        Text(
            text = "Speech",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        DisclosureRow(onClick = onOpenSpeechTest) {
          Text(
              text = "Test ASR",
              style = BirdSpotterTheme.type.headline,
              color = BirdSpotterTheme.colors.textPrimary,
          )
          Text(
              text = "Opens a session and prints what the glasses hear",
              style = BirdSpotterTheme.type.label,
              color = BirdSpotterTheme.colors.textSecondary,
          )
        }
      }

      // The door to the shutter: a session opened to take one photograph at settings
      // chosen on the spot, and to print what the crossing cost. A measuring
      // instrument — see [GlassesCameraViewModel].
      Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
        Text(
            text = "Camera",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        DisclosureRow(onClick = onOpenCameraScreen) {
          Text(
              text = "Camera",
              style = BirdSpotterTheme.type.headline,
              color = BirdSpotterTheme.colors.textPrimary,
          )
          Text(
              text = "Takes one photo at a chosen size and quality, and times the " + "crossing",
              style = BirdSpotterTheme.type.label,
              color = BirdSpotterTheme.colors.textSecondary,
          )
        }
      }

      // The other door: like the ASR test, a reading that cannot be taken without
      // opening a session — this one puts a chosen bird on the panel and holds it
      // there, which is the live flow's card on cue instead of on an identification.
      Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
        Text(
            text = "Display",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        DisclosureRow(onClick = onOpenDisplayScreen) {
          Text(
              text = "Display Screen",
              style = BirdSpotterTheme.type.headline,
              color = BirdSpotterTheme.colors.textPrimary,
          )
          Text(
              text = "Puts any bird's card on the glasses and holds it there",
              style = BirdSpotterTheme.type.label,
              color = BirdSpotterTheme.colors.textSecondary,
          )
        }
      }

      // Unlinking, and what it costs — the caption first, then the plate.
      //
      // Destructive rather than secondary: this is the only control in the app that
      // takes the glasses away, and until now it wore the same weight as the two
      // status rows above it — a headline and a caption, clickable only if you
      // happened to try. The consequence belongs above the button where it is read
      // before the tap, not inside it.
      Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
        Text(
            text = "Link",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Settings goes back to the set-up card. Relinking is the same handshake again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionButton(
            title = "Unlink from Meta AI",
            onClick = onUnlink,
            tone = ActionButtonTone.DESTRUCTIVE,
        )
      }

      Text(
          text =
              "BirdSpotter appears under Developer mode apps in Meta AI → App " +
                  "connections; registration and both grants live there.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Why the glasses cannot be reached, most actionable answer first.
 *
 * **A version mismatch outranks "not in range"**, because it is the one that looks identical from
 * the outside and sends someone hunting for a Bluetooth fault they do not have: DAT pins Meta AI
 * app and firmware versions on both ends, and glasses that are on, paired and inches away will
 * still never link with either behind.
 *
 * What the link turns on, and nothing more: folding and range are what drop it — the hinge cuts
 * Bluetooth. Wear and battery have their own rows off the same snapshot; this line only ever
 * explains an unreachable pair.
 */
private fun glassesSupport(device: GlassesDeviceInfo): String? =
    when (device.compatibility) {
      GlassesCompatibility.DEVICE_UPDATE_REQUIRED ->
          "Your glasses need a firmware update — check them in the Meta AI app"
      GlassesCompatibility.SDK_UPDATE_REQUIRED ->
          "These glasses are newer than this app's Meta SDK — that one is ours to fix"
      // Never negotiated: a pair DAT has actually reached reports its compatibility, so
      // UNKNOWN alongside a dead link means the two have never spoken at all. Developer Mode
      // is the usual reason — it is **per pair of glasses**, it is not the same switch as the
      // one in the Meta AI app's own settings, and a firmware update silently turns it off.
      GlassesCompatibility.UNKNOWN ->
          if (device.isAvailable) {
            null
          } else {
            "Meta AI lists these but they have never answered — check Developer Mode is on " +
                "for this pair in the Meta AI app"
          }
      GlassesCompatibility.COMPATIBLE ->
          if (device.isAvailable) {
            null
          } else {
            "Not reachable right now — folding them or going out of range drops the link"
          }
    }

/**
 * The wear and battery rows read the snapshot the link fills in, so with the pair unreachable both
 * read "—" — which is the truth about what the phone can know, and the Glasses row above already
 * says why.
 */
private fun wornValue(device: GlassesDeviceInfo): String =
    when (device.isWorn) {
      true -> "On the face"
      false -> "Taken off"
      null -> "—"
    }

/**
 * A percent only when there is a reading — unknown is "—", never 0%. Charging rides the same row:
 * it answers the question the number raises.
 */
private fun batteryValue(device: GlassesDeviceInfo): String {
  val charging = device.isCharging == true
  val level = device.batteryLevel ?: return if (charging) "Charging" else "—"
  return if (charging) "$level% · charging" else "$level%"
}

/**
 * Heat, in the wearer's words rather than the SDK's ladder.
 *
 * **"Normal" is a real answer and worth printing.** Heat is the one reading here that is read
 * *because* something already went wrong — a run that throttled, a session that ended itself — and
 * a row that only appears when hot cannot answer the question "was it the heat?" with a no. The
 * support line below carries the consequence, so the ordinary case stays one quiet word.
 */
private fun thermalValue(device: GlassesDeviceInfo): String =
    when (device.thermal) {
      GlassesThermalLevel.NOMINAL -> "Normal"
      GlassesThermalLevel.ELEVATED -> "Warm"
      GlassesThermalLevel.CRITICAL -> "Too hot"
      null -> "—"
    }

/** Only the grades that cost something explain themselves. */
private fun thermalSupport(device: GlassesDeviceInfo): String? =
    when (device.thermal) {
      GlassesThermalLevel.ELEVATED -> "The glasses may slow the camera down to cool off"
      GlassesThermalLevel.CRITICAL ->
          "Sessions will stop until they cool — give them a few minutes off"
      GlassesThermalLevel.NOMINAL,
      null -> null
    }

@Composable
private fun StatusRow(label: String, value: String, support: String? = null) {
  val space = BirdSpotterTheme.space
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space.related),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = MaterialTheme.typography.titleMedium)
      support?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * The two rows with an action on them: Meta AI's grants, given through the Meta AI app. `Allow`
 * raises the grant flow; a grant that already happened is just read out.
 *
 * **The row always reads its state, and the action sits under it** rather than in the value column.
 * A permission is a status first — the column stays a column, so Registration, Glasses and the two
 * grants can be read straight down without one of them answering with a button instead of a word.
 *
 * **`Unknown` is offered no button, and says why instead.** DAT reads the grant off a connected
 * pair, so with the glasses linked but not connected there is nothing to ask and nothing to answer
 * — an `Allow` here raises a flow that cannot succeed, and reading the unreadable state as "Not
 * asked" told the wearer they had never granted something they had. The support line carries the
 * one action that helps, the same way the Glasses row above it explains an unreachable pair rather
 * than merely reporting one.
 */
@Composable
private fun AccessRow(
    label: String,
    access: GlassesAccess?,
    support: String,
    onGrant: () -> Unit,
) {
  val space = BirdSpotterTheme.space
  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(
            text =
                if (access == GlassesAccess.UNKNOWN) {
                  "Meta AI can only answer this over a live link — connect your glasses"
                } else {
                  support
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
          text =
              when (access) {
                GlassesAccess.GRANTED -> "Granted"
                GlassesAccess.DENIED -> "Denied"
                GlassesAccess.UNKNOWN -> "Unknown"
                null -> "—"
              },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Denied covers the never-asked case too — DAT has no not-determined to tell them
    // apart, and `Allow` is the right offer for both.
    if (access == GlassesAccess.DENIED) {
      ActionButton(title = "Allow", onClick = onGrant)
    }
  }
}

/**
 * Linked, a pair on the link, the camera already given and the microphone still to give — the grant
 * rows in both of the states they answer in.
 */
@Preview(showBackground = true)
@Composable
fun GlassesSettingsScreenPreview() {
  BirdSpotterTheme {
    GlassesSettingsContent(
        registrationState = GlassesRegistrationState.REGISTERED,
        deviceInfo =
            GlassesDeviceInfo(
                name = "Chris's Ray-Ban Meta",
                isAvailable = true,
                compatibility = GlassesCompatibility.COMPATIBLE,
                isWorn = true,
                batteryLevel = 82,
                isCharging = false,
                thermal = GlassesThermalLevel.NOMINAL,
            ),
        cameraAccess = GlassesAccess.GRANTED,
        microphoneAccess = GlassesAccess.DENIED,
        onBack = {},
        onOpenSpeechTest = {},
        onOpenCameraScreen = {},
        onOpenDisplayScreen = {},
        onGrant = {},
        onUnlink = {},
    )
  }
}

/**
 * Linked, but nothing on the link — the state that used to read "Not asked" and offer an `Allow`
 * that could not succeed. Every row now explains the same one blocker.
 */
@Preview(showBackground = true)
@Composable
fun GlassesSettingsScreenDisconnectedPreview() {
  BirdSpotterTheme {
    GlassesSettingsContent(
        registrationState = GlassesRegistrationState.REGISTERED,
        deviceInfo =
            GlassesDeviceInfo(
                name = "Chris's Ray-Ban Meta",
                isAvailable = false,
                compatibility = GlassesCompatibility.COMPATIBLE,
            ),
        cameraAccess = GlassesAccess.UNKNOWN,
        microphoneAccess = GlassesAccess.UNKNOWN,
        onBack = {},
        onOpenSpeechTest = {},
        onOpenCameraScreen = {},
        onOpenDisplayScreen = {},
        onGrant = {},
        onUnlink = {},
    )
  }
}
