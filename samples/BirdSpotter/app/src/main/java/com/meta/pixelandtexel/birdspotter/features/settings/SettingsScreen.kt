/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.domain.GlassesRegistrationState
import com.meta.pixelandtexel.birdspotter.domain.PermissionStatus
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButton
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButtonTone
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.components.DisclosureRow
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import com.meta.wearable.dat.core.Wearables

/**
 * Settings — pushed from the Journal app bar.
 *
 * Lives inside the Journal tab's graph, so the bottom bar stays put and Journal is still the
 * selected tab while this is open.
 *
 * Three sections. **Glasses** leads: the set-up card until registration has happened, the Meta AI
 * Glasses row after — one doorway at a time, decided by [SettingsViewModel.registrationState].
 * **Demo / Developer** holds the Demo Director; its label is the honesty affordance from the design
 * doc. **Journal** sits last, because it holds the one control in the app that empties every entry
 * at once — nothing you were scrolling for should be underneath it.
 *
 * The registration raise itself happens here, not in the view model — Android can only hand off to
 * Meta AI from an Activity, so both platforms keep the raise beside the card (the same divergence
 * [com.meta.pixelandtexel.birdspotter.domain.PermissionsController] documents).
 *
 * The system back gesture is the [NavHost][androidx.navigation.compose.NavHost]'s to handle;
 * [onBack] is only for the arrow in the bar.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDemoDirector: () -> Unit,
    onOpenGlasses: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val container = (LocalContext.current.applicationContext as BirdSpotterApplication).container
  val viewModel: SettingsViewModel = viewModel(
      factory =
          SettingsViewModel.factory(
              container.glassesSessionRepository,
              container.journalRepository,
              container.mockDeviceRepository,
          ),
  )
  val registrationState by viewModel.registrationState.collectAsStateWithLifecycle()
  val isDeletingJournal by viewModel.isDeletingJournal.collectAsStateWithLifecycle()
  val isMockDeviceEnabled by viewModel.isMockDeviceEnabled.collectAsStateWithLifecycle()
  val isMockDeviceFlipping by viewModel.isMockDeviceFlipping.collectAsStateWithLifecycle()
  val context = LocalContext.current

  // Read once, not watched: the grant is asked for at launch, and the one way to change it
  // from here — the system's own app page — restarts the app on its way back.
  val bluetoothAccess = remember(context) { context.bluetoothAccess() }

  SettingsScreenContent(
      registrationState = registrationState,
      bluetoothAccess = bluetoothAccess,
      isDeletingJournal = isDeletingJournal,
      isMockDeviceEnabled = isMockDeviceEnabled,
      isMockDeviceFlipping = isMockDeviceFlipping,
      onBack = onBack,
      onOpenDemoDirector = onOpenDemoDirector,
      onOpenGlasses = onOpenGlasses,
      onOpenDiagnostics = onOpenDiagnostics,
      onDeleteAllJournalData = viewModel::deleteAllJournalData,
      onSetMockDeviceEnabled = viewModel::setMockDeviceEnabled,
      onSetUpGlasses = {
        // The raise itself, from the Activity — the un-mirrorable half the view model
        // deliberately does not carry. Meta AI takes the screen from here; the outcome
        // lands back in registrationStateStream(), which the card is already watching.
        context.findActivity()?.let { activity ->
          // docs:glasses-register:begin
          Wearables.startRegistration(activity)
          // docs:glasses-register:end
        }
      },
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    registrationState: GlassesRegistrationState?,
    bluetoothAccess: PermissionStatus,
    isDeletingJournal: Boolean,
    isMockDeviceEnabled: Boolean,
    isMockDeviceFlipping: Boolean,
    onBack: () -> Unit,
    onOpenDemoDirector: () -> Unit,
    onOpenGlasses: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDeleteAllJournalData: () -> Unit,
    onSetMockDeviceEnabled: (Boolean) -> Unit,
    onSetUpGlasses: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var confirmingDeleteAll by remember { mutableStateOf(false) }
  var presentedNotice by remember { mutableStateOf<SetupNotice?>(null) }
  val space = BirdSpotterTheme.space
  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Settings") },
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(horizontal = space.gutter, vertical = space.separate),
        verticalArrangement = Arrangement.spacedBy(space.section),
    ) {
      // One doorway at a time: nothing before the first reading has landed, the
      // set-up card before registration, the glasses row after it.
      when (registrationState) {
        null -> Unit
        GlassesRegistrationState.REGISTERED ->
            item {
              GlassesSection(onOpenGlasses = onOpenGlasses)
            }
        else ->
            item {
              GlassesSetupCard(
                  state = registrationState,
                  bluetoothAccess = bluetoothAccess,
                  // Every tap is answered. When the raise cannot happen, the reason is
                  // what comes back instead of nothing.
                  onTap = {
                    val notice = setupNotice(registrationState, bluetoothAccess)
                    if (notice == null) onSetUpGlasses() else presentedNotice = notice
                  },
              )
            }
      }
      item {
        DemoSection(
            isMockDeviceEnabled = isMockDeviceEnabled,
            isMockDeviceFlipping = isMockDeviceFlipping,
            onOpenDemoDirector = onOpenDemoDirector,
            onOpenDiagnostics = onOpenDiagnostics,
            onSetMockDeviceEnabled = onSetMockDeviceEnabled,
        )
      }
      item {
        JournalSection(
            isDeleting = isDeletingJournal,
            onDeleteAll = { confirmingDeleteAll = true },
        )
      }
    }
  }

  if (confirmingDeleteAll) {
    AlertDialog(
        onDismissRequest = { confirmingDeleteAll = false },
        title = { Text("Delete all journal data?") },
        text = {
          Text(
              "This removes every entry and everything they captured — photos, " +
                  "recordings, and the life list they add up to. It can't be undone.",
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                confirmingDeleteAll = false
                onDeleteAllJournalData()
              },
          ) {
            Text("Delete everything", color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmingDeleteAll = false }) { Text("Cancel") }
        },
    )
  }

  presentedNotice?.let { notice ->
    AlertDialog(
        onDismissRequest = { presentedNotice = null },
        title = { Text(notice.title) },
        text = { Text(notice.message) },
        confirmButton = {
          TextButton(onClick = { presentedNotice = null }) { Text("OK") }
        },
    )
  }
}

/**
 * Why the set-up card cannot do the thing it is offering to do — the answer a tap gets when
 * registration is not actually raisable.
 */
private data class SetupNotice(val title: String, val message: String)

/**
 * The notice a tap earns in [state], or `null` when the tap should raise registration instead.
 *
 * `REGISTERED` never reaches here — Settings has traded the card for the glasses row by then — but
 * it answers like `AVAILABLE` rather than inventing a third case.
 *
 * **[bluetoothAccess] is why this takes two arguments.** "Unavailable" is one reading with more
 * than one cause behind it, and a refused Bluetooth grant produces exactly the same one as a phone
 * with no Meta AI app on it: without the grant nothing can read the bonded-device list, so nothing
 * can find glasses, so there is nobody to register with. Blaming Meta AI for it sends someone
 * reinstalling an app that was never the problem, so the grant — the cause this app can name for
 * certain, and the only one it can fix — is checked first.
 */
private fun setupNotice(
    state: GlassesRegistrationState,
    bluetoothAccess: PermissionStatus,
): SetupNotice? =
    when (state) {
      GlassesRegistrationState.AVAILABLE,
      GlassesRegistrationState.REGISTERED -> null
      GlassesRegistrationState.REGISTERING ->
          SetupNotice(
              title = "Already waiting on Meta AI",
              message =
                  "The link is in flight. Approve it in the Meta AI app — this card leaves " +
                      "on its own when it lands.",
          )
      GlassesRegistrationState.UNAVAILABLE ->
          if (bluetoothAccess != PermissionStatus.GRANTED) {
            // "Nearby devices" rather than "Bluetooth": that is what the permission is called on
            // the phone's own app settings page, and looking there for the word Bluetooth finds
            // nothing.
            SetupNotice(
                title = "Bluetooth permission needed",
                message =
                    "Your glasses talk to BirdSpotter over Bluetooth, and the app has not " +
                        "been allowed to use it. Turn on Nearby devices for BirdSpotter in the " +
                        "phone's app settings, then open BirdSpotter again.",
            )
          } else {
            SetupNotice(
                title = "Meta AI app required",
                message =
                    "BirdSpotter reaches your glasses through the Meta AI app, and this " +
                        "phone can't find it. Install Meta AI, pair your glasses, and turn on " +
                        "Developer Mode for that pair — then come back here.",
            )
          }
    }

@Composable
private fun GlassesSection(onOpenGlasses: () -> Unit) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Glasses", color = colors.gilt)
    DisclosureRow(onClick = onOpenGlasses) {
      Text(text = "Meta AI Glasses", style = type.headline, color = colors.textPrimary)
      Text(
          text = "Status and connection",
          style = type.label,
          color = colors.textSecondary,
      )
    }
  }
}

/**
 * The invitation, before registration has happened.
 *
 * **Tappable in every state it appears in, including the ones that cannot raise Meta AI.** It was
 * inert while unavailable once, and a card that reads "Set up your glasses", is styled like a
 * button and answers a tap with nothing is indistinguishable from a bug — the support line
 * underneath is not something anyone reads before tapping. [onTap] now always fires, and
 * [setupNotice] decides whether it earns a registration raise or an explanation.
 */
@Composable
private fun GlassesSetupCard(
    state: GlassesRegistrationState,
    bluetoothAccess: PermissionStatus,
    onTap: () -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  CardSurface(onClick = onTap) {
    Column(
        modifier = Modifier.padding(space.cardInset),
        verticalArrangement = Arrangement.spacedBy(space.tight),
    ) {
      PlateLabel(text = "Meta AI Glasses", color = colors.gilt)
      Row(
          horizontalArrangement = Arrangement.spacedBy(space.related),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text =
                  when (state) {
                    GlassesRegistrationState.REGISTERING -> "Finishing up in Meta AI"
                    else -> "Set up your glasses"
                  },
              style = type.title,
              color = colors.textPrimary,
          )
          Text(
              // The support line names the same blocker the tap will, so the card
              // is not still recommending an install while the dialog under it
              // talks about Bluetooth.
              text =
                  when {
                    state == GlassesRegistrationState.REGISTERING ->
                        "Approve the link there — this card leaves when it lands."
                    state == GlassesRegistrationState.UNAVAILABLE &&
                        bluetoothAccess != PermissionStatus.GRANTED ->
                        "Allow BirdSpotter to use Bluetooth first."
                    state == GlassesRegistrationState.UNAVAILABLE ->
                        "Install the Meta AI app and pair your glasses first."
                    else -> "Link BirdSpotter with the Meta AI app to spot through them."
                  },
              style = type.label,
              color = colors.textSecondary,
          )
        }
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.glasses),
            contentDescription = null,
            tint = colors.gilt,
        )
      }
    }
  }
}

/**
 * Emptying the Journal, and what it costs — the caption above the button, where it is read before
 * the tap, the way `GlassesSettingsScreen` states the cost of unlinking. The dialog is the second
 * ask; this is the first one.
 */
@Composable
private fun JournalSection(isDeleting: Boolean, onDeleteAll: () -> Unit) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Journal", color = colors.gilt)
    Text(
        text =
            "Every outing, the photos and recordings they captured, and the life " +
                "list they add up to.",
        style = type.label,
        color = colors.textSecondary,
    )
    // No disabled state: the sweep is a local delete and over in a blink, and the view
    // model already refuses a second one. The title is the whole feedback.
    ActionButton(
        title = if (isDeleting) "Deleting…" else "Delete all journal data",
        onClick = onDeleteAll,
        tone = ActionButtonTone.DESTRUCTIVE,
    )
  }
}

@Composable
private fun DemoSection(
    isMockDeviceEnabled: Boolean,
    isMockDeviceFlipping: Boolean,
    onOpenDemoDirector: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSetMockDeviceEnabled: (Boolean) -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Demo / Developer", color = colors.gilt)
    DisclosureRow(onClick = onOpenDemoDirector) {
      Text(text = "Demo Director", style = type.headline, color = colors.textPrimary)
      Text(
          text = "Scripts what the app identifies",
          style = type.label,
          color = colors.textSecondary,
      )
    }
    // "Diagnostics", not "Logs": logging already means the Journal in this app.
    DisclosureRow(onClick = onOpenDiagnostics) {
      Text(text = "Diagnostics", style = type.headline, color = colors.textPrimary)
      Text(
          text = "What the app did, run by run",
          style = type.label,
          color = colors.textSecondary,
      )
    }
    // Here rather than on the glasses screen, because that screen only exists once
    // registration has happened — and the mock is for when it cannot.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = "Mock Device Kit", style = type.headline, color = colors.textPrimary)
        Text(
            text =
                "Simulated glasses in place of the real SDK. A floating button opens the controls.",
            style = type.label,
            color = colors.textSecondary,
        )
      }
      Switch(
          checked = isMockDeviceEnabled,
          onCheckedChange = onSetMockDeviceEnabled,
          enabled = !isMockDeviceFlipping,
          colors =
              SwitchDefaults.colors(
                  checkedThumbColor = colors.paper,
                  checkedTrackColor = colors.verdigris,
              ),
      )
    }
  }
}

/**
 * The Activity under this composition, unwrapped the long way. Compose hands screens a
 * `ContextWrapper`; DAT's raises (registration, the permission contract's fallback) insist on the
 * Activity itself.
 */
internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
      is Activity -> this
      is ContextWrapper -> baseContext.findActivity()
      else -> null
    }

/**
 * Whether the app may use Bluetooth — the gate underneath every glasses reading, since the SDK
 * finds glasses by asking which devices this phone is bonded to.
 *
 * Not-granted reads as [PermissionStatus.NOT_DETERMINED] for the reason
 * [com.meta.pixelandtexel.birdspotter.data.permissions.SystemPermissionsController] gives: a
 * refusal and a never-asked look identical from outside an Activity, and nothing here needs to tell
 * them apart.
 */
private fun Context.bluetoothAccess(): PermissionStatus =
    if (
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    ) {
      PermissionStatus.GRANTED
    } else {
      PermissionStatus.NOT_DETERMINED
    }

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
  BirdSpotterTheme {
    SettingsScreenContent(
        registrationState = GlassesRegistrationState.AVAILABLE,
        bluetoothAccess = PermissionStatus.GRANTED,
        isDeletingJournal = false,
        isMockDeviceEnabled = false,
        isMockDeviceFlipping = false,
        onBack = {},
        onOpenDemoDirector = {},
        onOpenGlasses = {},
        onOpenDiagnostics = {},
        onDeleteAllJournalData = {},
        onSetMockDeviceEnabled = {},
        onSetUpGlasses = {},
    )
  }
}

/**
 * The card with the Bluetooth grant missing — the state that read "Install the Meta AI app" on a
 * phone that had Meta AI installed, running, and paired to the glasses the whole time.
 */
@Preview(showBackground = true)
@Composable
fun SettingsScreenBluetoothDeniedPreview() {
  BirdSpotterTheme {
    SettingsScreenContent(
        registrationState = GlassesRegistrationState.UNAVAILABLE,
        bluetoothAccess = PermissionStatus.NOT_DETERMINED,
        isDeletingJournal = false,
        isMockDeviceEnabled = false,
        isMockDeviceFlipping = false,
        onBack = {},
        onOpenDemoDirector = {},
        onOpenGlasses = {},
        onOpenDiagnostics = {},
        onDeleteAllJournalData = {},
        onSetMockDeviceEnabled = {},
        onSetUpGlasses = {},
    )
  }
}
