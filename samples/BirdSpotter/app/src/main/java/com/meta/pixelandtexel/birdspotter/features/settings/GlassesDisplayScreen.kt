/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesGroup
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButton
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButtonTone
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.NotesField
import com.meta.pixelandtexel.birdspotter.ui.components.SearchField
import com.meta.pixelandtexel.birdspotter.ui.components.SpeciesRow
import com.meta.pixelandtexel.birdspotter.ui.previews.PreviewCatalog
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * Display Screen — any bird's card put on the glasses' panel by hand, and held there.
 *
 * Pushed from the glasses settings screen. The session opens with the screen and the guide sits
 * under a search field; tapping a row sends that bird's card up, where it stays until another row
 * replaces it, **Clear screen** takes it down, or the screen is left. See [GlassesDisplayViewModel]
 * for why leaving is a clear.
 */
@Composable
fun GlassesDisplayScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val container = (LocalContext.current.applicationContext as BirdSpotterApplication).container
  val viewModel: GlassesDisplayViewModel = viewModel(
      factory =
          GlassesDisplayViewModel.factory(
              container.glassesSessionRepository,
              container.glassesDisplayRepository,
              container.birdCatalogRepository,
              container.displaySettingsStore,
          ),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  GlassesDisplayContent(
      uiState = uiState,
      onBack = onBack,
      onQueryChange = viewModel::search,
      onShow = viewModel::show,
      onClearScreen = viewModel::clearScreen,
      onPickCustomBird = viewModel::pickCustomBird,
      onCustomMessageChange = viewModel::editCustomMessage,
      onShowCustom = viewModel::showCustom,
      onRetry = viewModel::start,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassesDisplayContent(
    uiState: GlassesDisplayUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onShow: (SpeciesWithMedia) -> Unit,
    onClearScreen: () -> Unit,
    onPickCustomBird: (SpeciesWithMedia) -> Unit,
    onCustomMessageChange: (String) -> Unit,
    onShowCustom: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Display Screen") },
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
    // A lazy list rather than a scrolling column: the guide's rows decode a thumbnail
    // each, and only the ones about to be seen should pay for it. Per-child padding and
    // no `verticalArrangement`, deliberately — the rhythm down this page is not uniform,
    // and setting both would add the two together.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding =
            PaddingValues(
                start = space.gutter,
                end = space.gutter,
                top = space.separate,
                bottom = space.page,
            ),
    ) {
      item(key = "session") {
        SessionSection(uiState = uiState, onRetry = onRetry)
      }

      item(key = "on-the-glasses") {
        OnTheGlassesSection(
            shownBird = uiState.shownBird,
            onClearScreen = onClearScreen,
            modifier = Modifier.padding(top = space.section),
        )
      }

      item(key = "custom-card") {
        CustomCardSection(
            customBird = uiState.customBird,
            customMessage = uiState.customMessage,
            onPickBird = onPickCustomBird,
            onMessageChange = onCustomMessageChange,
            onShowCustom = onShowCustom,
            modifier = Modifier.padding(top = space.section),
        )
      }

      item(key = "search") {
        SearchField(
            text = uiState.query,
            onTextChange = onQueryChange,
            modifier = Modifier.padding(top = space.section, bottom = space.related),
        )
      }

      if (uiState.birds.isEmpty() && uiState.query.isNotBlank()) {
        item(key = "no-matches") {
          Text(
              text = "Nothing in the guide matches “${uiState.query.trim()}”.",
              style = BirdSpotterTheme.type.body,
              color = BirdSpotterTheme.colors.textSecondary,
              modifier = Modifier.padding(top = space.related),
          )
        }
      }

      items(uiState.birds, key = { it.species.id }) { bird ->
        SpeciesRow(bird = bird, onClick = { onShow(bird) })
        HairlineRule()
      }
    }
  }
}

/**
 * Where the session stands, and whether these glasses can draw at all — the one reading that
 * decides whether a tap below means anything.
 */
@Composable
private fun SessionSection(
    uiState: GlassesDisplayUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space.related)) {
    Text(
        text = "Session",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    ReadingRow(label = "Glasses", value = sessionValue(uiState))
    ReadingRow(
        label = "Display",
        value = displayValue(uiState.hasDisplay),
        support = displaySupport(uiState.hasDisplay),
    )
    uiState.failure?.let {
      // In the page's own ink rather than a red: the palette spends its one red on
      // controls that end something, and a line explaining why the session ended is
      // a statement, not a control.
      Text(
          text = it,
          style = MaterialTheme.typography.bodyMedium,
      )
    }
    if (!uiState.isRunning) {
      ActionButton(title = "Reconnect", onClick = onRetry)
    }
  }
}

/**
 * What the panel is carrying right now, and the way to take it down. The one destructive control on
 * the screen — it ends what the wearer is looking at.
 */
@Composable
private fun OnTheGlassesSection(
    shownBird: SpeciesWithMedia?,
    onClearScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space.related)) {
    Text(
        text = "On the glasses",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text =
            shownBird?.species?.commonName
                ?: "Nothing yet — tap a bird below and its card goes up.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (shownBird != null) {
      ActionButton(
          title = "Clear screen",
          onClick = onClearScreen,
          tone = ActionButtonTone.DESTRUCTIVE,
      )
    }
  }
}

/**
 * The custom card: a chosen bird's gallery with the presenter's own line under the photographs, in
 * place of the catalog's description.
 *
 * The bird is picked from a sheet rather than the list below, because the list's tap already means
 * *send this now* — a setup control and a trigger wearing the same gesture would put a card up
 * mid-sentence. The button appears only once there is a bird and a line to send; see
 * [GlassesDisplayViewModel.customCardReady].
 */
@Composable
private fun CustomCardSection(
    customBird: SpeciesWithMedia?,
    customMessage: String,
    onPickBird: (SpeciesWithMedia) -> Unit,
    onMessageChange: (String) -> Unit,
    onShowCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  var isPicking by remember { mutableStateOf(false) }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space.related)) {
    Text(
        text = "Custom card",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text =
            "The bird's card with your own line under the photos, in place of the " +
                "guide's description. Remembered until you change it.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth().clickable { isPicking = true },
        horizontalArrangement = Arrangement.spacedBy(space.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = customBird?.species?.commonName ?: "Choose a bird",
          style = BirdSpotterTheme.type.body,
          color =
              if (customBird == null) {
                BirdSpotterTheme.colors.textSecondary
              } else {
                BirdSpotterTheme.colors.textPrimary
              },
          modifier = Modifier.weight(1f),
      )
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.disclosure),
          contentDescription = null,
          tint = BirdSpotterTheme.colors.textSecondary,
      )
    }
    NotesField(
        text = customMessage,
        onTextChange = onMessageChange,
        placeholder = "The line to show under the photos",
    )
    if (GlassesDisplayViewModel.customCardReady(customBird, customMessage)) {
      ActionButton(title = "Show custom card", onClick = onShowCustom)
    }
  }

  if (isPicking) {
    CustomBirdPickerSheet(
        onPick = { bird ->
          onPickBird(bird)
          isPicking = false
        },
        onDismiss = { isPicking = false },
    )
  }
}

/**
 * The catalog, searchable, in a sheet — the way the custom card's bird is chosen. Matching goes
 * through [GlassesDisplayViewModel.birdsFor], so this field and the one on the screen under it find
 * the same birds by the same letters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomBirdPickerSheet(onPick: (SpeciesWithMedia) -> Unit, onDismiss: () -> Unit) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  var query by remember { mutableStateOf("") }
  var groups by remember { mutableStateOf<List<SpeciesGroup>>(emptyList()) }

  LaunchedEffect(Unit) {
    groups =
        runCatching { application.container.birdCatalogRepository.browseGroups() }
            .getOrDefault(emptyList())
  }

  val matches = remember(query, groups) { GlassesDisplayViewModel.birdsFor(query, groups) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
        modifier = Modifier.padding(horizontal = BirdSpotterTheme.space.gutter),
        verticalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.related),
    ) {
      SearchField(text = query, onTextChange = { query = it })
      LazyColumn(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(bottom = BirdSpotterTheme.space.page),
      ) {
        items(matches, key = { it.species.id }) { bird ->
          SpeciesRow(bird = bird, onClick = { onPick(bird) })
        }
      }
    }
  }
}

/**
 * What the session is doing, in the words the pill on the realtime screen uses — one vocabulary for
 * the link across the app, so a reading here is comparable with a reading there.
 */
private fun sessionValue(uiState: GlassesDisplayUiState): String =
    when (uiState.sessionState) {
      GlassesSessionState.STARTING -> "Connecting"
      GlassesSessionState.STARTED -> "Connected"
      GlassesSessionState.PAUSED -> "Paused"
      GlassesSessionState.STOPPING -> "Stopping"
      GlassesSessionState.STOPPED -> "Stopped"
      null -> if (uiState.isRunning) "Connecting" else "Not started"
    }

private fun displayValue(hasDisplay: Boolean?): String =
    when (hasDisplay) {
      true -> "Ready"
      false -> "None on this pair"
      null -> "—"
    }

/**
 * Only the reading that changes what a tap does explains itself: a pair with no panel takes every
 * card quietly and shows none of them, which is otherwise indistinguishable from it working.
 */
private fun displaySupport(hasDisplay: Boolean?): String? =
    when (hasDisplay) {
      false -> "These glasses have no panel, so a card sent here lands nowhere"
      true,
      null -> null
    }

@Composable
private fun ReadingRow(label: String, value: String, support: String? = null) {
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

/** Mid-rehearsal: connected, a card up, and the guide ready for the next one. */
@Preview(showBackground = true, heightDp = 900)
@Composable
fun GlassesDisplayScreenPreview() {
  BirdSpotterTheme {
    GlassesDisplayContent(
        uiState =
            GlassesDisplayUiState(
                isRunning = true,
                sessionState = GlassesSessionState.STARTED,
                hasDisplay = true,
                birds = GlassesDisplayViewModel.birdsFor("", PreviewCatalog.guide),
                shownBird = PreviewCatalog.blueJay,
                customBird = PreviewCatalog.blueJay,
                customMessage = "Our loudest regular — listen for the pump-handle call.",
            ),
        onBack = {},
        onQueryChange = {},
        onShow = {},
        onClearScreen = {},
        onPickCustomBird = {},
        onCustomMessageChange = {},
        onShowCustom = {},
        onRetry = {},
    )
  }
}

/** The reading that matters: a pair with nothing to draw on, saying so before a tap. */
@Preview(showBackground = true, heightDp = 900)
@Composable
fun GlassesDisplayScreenNoDisplayPreview() {
  BirdSpotterTheme {
    GlassesDisplayContent(
        uiState =
            GlassesDisplayUiState(
                isRunning = true,
                sessionState = GlassesSessionState.STARTED,
                hasDisplay = false,
                birds = GlassesDisplayViewModel.birdsFor("", PreviewCatalog.guide),
            ),
        onBack = {},
        onQueryChange = {},
        onShow = {},
        onClearScreen = {},
        onPickCustomBird = {},
        onCustomMessageChange = {},
        onShowCustom = {},
        onRetry = {},
    )
  }
}
