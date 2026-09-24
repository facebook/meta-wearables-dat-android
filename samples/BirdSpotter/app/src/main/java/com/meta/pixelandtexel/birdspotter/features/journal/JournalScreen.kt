/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.ui.components.CardMetrics
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.components.SearchField
import com.meta.pixelandtexel.birdspotter.ui.components.SpeciesRowMetrics
import com.meta.pixelandtexel.birdspotter.ui.previews.PreviewCatalog
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * Journal tab — the field notes: every outing saved, searchable, newest first, under a heading per
 * month.
 *
 * Built to the same page recipe as `ExploreScreen` — a chrome-less page that opens with the page, a
 * search field that replaces everything below it, ruled rows on the paper — so the two tabs read as
 * one app. Split into a stateful wrapper that owns the view model and a stateless `JournalScreen`
 * that draws it, so the Previews below render every state without a database.
 *
 * Takes [onOpenSettings] and [onOpenEntry] rather than a `NavController`, so the screen stays
 * testable and previewable without a navigation graph — the same reason `ExploreScreen` takes
 * `onOpenBird`.
 */
@Composable
fun JournalScreen(
    onOpenSettings: () -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: JournalViewModel = viewModel(
      factory =
          JournalViewModel.factory(
              journal = application.container.journalRepository,
              birdCatalog = application.container.birdCatalogRepository,
          ),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val query by viewModel.query.collectAsStateWithLifecycle()

  JournalScreen(
      uiState = uiState,
      query = query,
      mediaFileStore = application.container.mediaFileStore,
      onQueryChange = viewModel::search,
      onOpenSettings = onOpenSettings,
      onOpenEntry = onOpenEntry,
      modifier = modifier,
  )
}

/** The stateless half, so Previews and UI tests can drive every state without a database. */
@Composable
fun JournalScreen(
    uiState: JournalUiState,
    query: String,
    mediaFileStore: MediaFileStore?,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  LazyColumn(
      modifier =
          modifier
              .fillMaxSize()
              .background(BirdSpotterTheme.colors.paper)
              // Nothing sits above this screen, so clearing the status bar is its own job. The
              // background goes on first so the paper runs up behind the clock.
              .windowInsetsPadding(WindowInsets.statusBars),
      // Per-child padding and no `verticalArrangement`, deliberately: the rhythm down this
      // page is not uniform — a section gap above each month, none between the ruled rows —
      // and setting both would add the two together.
      contentPadding =
          PaddingValues(
              start = space.gutter,
              end = space.gutter,
              top = space.section,
              bottom = space.page,
          ),
  ) {
    item(key = "header") { Header(uiState = uiState, onOpenSettings = onOpenSettings) }

    item(key = "search") {
      SearchField(
          text = query,
          onTextChange = onQueryChange,
          placeholder = "Find a sighting",
          modifier = Modifier.padding(top = space.separate),
      )
    }

    when (uiState) {
      is JournalUiState.Loading ->
          item(key = "loading") {
            JournalLoading(Modifier.padding(top = space.section))
          }

      is JournalUiState.Empty ->
          item(key = "empty") {
            JournalEmpty(Modifier.padding(top = space.section))
          }

      is JournalUiState.Ready ->
          monthSections(
              months = uiState.months,
              mediaFileStore = mediaFileStore,
              onOpenEntry = onOpenEntry,
          )

      is JournalUiState.Searching ->
          searchResults(
              results = uiState.results,
              query = query,
              mediaFileStore = mediaFileStore,
              onOpenEntry = onOpenEntry,
          )
    }
  }
}

/**
 * The Journal proper: a month heading, then its entries as ruled rows — the same shape Explore's
 * guide sections take, so the two lists rhyme.
 *
 * A `LazyListScope` extension rather than a composable, for the reason Explore's `guide` is one: a
 * composable cannot emit lazy items, so wrapping the rows in a single `item {}` would compose and
 * decode every one of them at once.
 */
private fun LazyListScope.monthSections(
    months: List<JournalMonth>,
    mediaFileStore: MediaFileStore?,
    onOpenEntry: (String) -> Unit,
) {
  months.forEach { month ->
    item(key = "month-${month.key}") { MonthHeader(month.title) }

    items(month.entries, key = { it.id }) { entry ->
      JournalRow(
          entry = entry,
          mediaFileStore = mediaFileStore,
          onClick = { onOpenEntry(entry.id) },
      )
      HairlineRule()
    }
  }
}

/**
 * A query's matches: a count where a month heading would go, then the same ruled rows — flat, no
 * month headings, matching Explore's search results.
 */
private fun LazyListScope.searchResults(
    results: List<JournalEntry>,
    query: String,
    mediaFileStore: MediaFileStore?,
    onOpenEntry: (String) -> Unit,
) {
  item(key = "results-header") { ResultsHeader(results.size) }

  if (results.isEmpty()) {
    item(key = "no-matches") { NoMatches(query) }
    return
  }

  items(results, key = { it.id }) { entry ->
    JournalRow(
        entry = entry,
        mediaFileStore = mediaFileStore,
        onClick = { onOpenEntry(entry.id) },
    )
    HairlineRule()
  }
}

/**
 * Eyebrow, headline and the life-list stat, with Settings to the right — the page's own bar, since
 * it draws no top app bar and Settings has to live somewhere.
 */
@Composable
private fun Header(
    uiState: JournalUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.related),
    ) {
      PlateLabel(text = "The Journal", color = BirdSpotterTheme.colors.gilt)
      Text(
          text = "Field notes.",
          style = BirdSpotterTheme.type.display,
          color = BirdSpotterTheme.colors.textPrimary,
      )
      lifeListStat(uiState)?.let { stat ->
        PlateLabel(text = stat, color = BirdSpotterTheme.colors.verdigris)
      }
    }

    IconButton(onClick = onOpenSettings) {
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.settings),
          contentDescription = "Settings",
          tint = BirdSpotterTheme.colors.textSecondary,
      )
    }
  }
}

/** A section heading in the Journal — "JULY 2026". Gilt, matching Explore's guide headers. */
@Composable
private fun MonthHeader(title: String, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  PlateLabel(
      text = title,
      color = BirdSpotterTheme.colors.gilt,
      modifier = modifier.padding(top = space.section, bottom = space.related),
  )
}

/** The plate over a query's matches — "12 ENTRIES", "1 ENTRY", "NO MATCHES". */
@Composable
private fun ResultsHeader(count: Int, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  PlateLabel(
      text = JournalViewModel.resultsLabel(count),
      color = BirdSpotterTheme.colors.gilt,
      modifier = modifier.padding(top = space.section, bottom = space.related),
  )
}

/**
 * Plain text on the paper rather than a [CardSurface]. [JournalEmpty] earns a card by being a
 * permanent state; this one comes and goes between keystrokes, and a card flashing in and out reads
 * as a fault — the same call Explore makes.
 */
@Composable
private fun NoMatches(query: String, modifier: Modifier = Modifier) {
  Text(
      text = "Nothing in your journal matches “$query”.",
      style = BirdSpotterTheme.type.body,
      color = BirdSpotterTheme.colors.textSecondary,
      modifier = modifier,
  )
}

/** Shown when nothing has been logged yet — a fresh install, honestly, not an error. */
@Composable
private fun JournalEmpty(modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  CardSurface(modifier = modifier) {
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = space.cardInset, vertical = space.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.related),
    ) {
      PlateLabel(text = "The Journal Is Empty")
      Text(
          text =
              "Every outing you save appears here, newest first — birds or no birds. Head to Identify to log your first.",
          style = BirdSpotterTheme.type.body,
          color = BirdSpotterTheme.colors.textSecondary,
          textAlign = TextAlign.Center,
      )
    }
  }
}

/** A few ruled ghost rows while the first read lands, so the page does not jump when it does. */
@Composable
private fun JournalLoading(modifier: Modifier = Modifier) {
  Column(modifier) {
    repeat(4) {
      JournalRowPlaceholder()
      HairlineRule()
    }
  }
}

@Composable
private fun JournalRowPlaceholder(modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  Row(
      modifier = modifier.fillMaxWidth().padding(vertical = space.related),
      horizontalArrangement = Arrangement.spacedBy(space.separate),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        Modifier.size(SpeciesRowMetrics.ThumbnailSize)
            .clip(RoundedCornerShape(CardMetrics.CornerRadius))
            .background(BirdSpotterTheme.colors.rule),
    )
    Column(verticalArrangement = Arrangement.spacedBy(space.snug)) {
      Box(Modifier.size(width = 150.dp, height = 15.dp).background(BirdSpotterTheme.colors.rule))
      Box(Modifier.size(width = 96.dp, height = 12.dp).background(BirdSpotterTheme.colors.rule))
    }
  }
}

private fun lifeListStat(uiState: JournalUiState): String? {
  val count =
      when (uiState) {
        is JournalUiState.Ready -> uiState.lifeList
        is JournalUiState.Searching -> uiState.lifeList
        else -> 0
      }
  return when {
    count <= 0 -> null
    count == 1 -> "1 species logged"
    else -> "$count species logged"
  }
}

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun JournalScreenPreview() {
  BirdSpotterTheme {
    JournalScreen(
        uiState = JournalUiState.Ready(lifeList = 12, months = PreviewCatalog.journalMonths),
        query = "",
        mediaFileStore = null,
        onQueryChange = {},
        onOpenSettings = {},
        onOpenEntry = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JournalScreenDarkPreview() {
  BirdSpotterTheme(darkTheme = true) {
    JournalScreen(
        uiState = JournalUiState.Ready(lifeList = 12, months = PreviewCatalog.journalMonths),
        query = "",
        mediaFileStore = null,
        onQueryChange = {},
        onOpenSettings = {},
        onOpenEntry = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun JournalScreenSearchingPreview() {
  BirdSpotterTheme {
    JournalScreen(
        uiState = JournalUiState.Searching(lifeList = 12, results = PreviewCatalog.journalEntries),
        query = "jay",
        mediaFileStore = null,
        onQueryChange = {},
        onOpenSettings = {},
        onOpenEntry = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun JournalScreenNoMatchesPreview() {
  BirdSpotterTheme {
    JournalScreen(
        uiState = JournalUiState.Searching(lifeList = 12, results = emptyList()),
        query = "pelican",
        mediaFileStore = null,
        onQueryChange = {},
        onOpenSettings = {},
        onOpenEntry = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun JournalScreenEmptyPreview() {
  BirdSpotterTheme {
    JournalScreen(
        uiState = JournalUiState.Empty,
        query = "",
        mediaFileStore = null,
        onQueryChange = {},
        onOpenSettings = {},
        onOpenEntry = {},
    )
  }
}
