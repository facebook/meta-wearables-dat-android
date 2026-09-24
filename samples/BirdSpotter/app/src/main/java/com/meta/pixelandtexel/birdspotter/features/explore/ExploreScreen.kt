/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.explore

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesGroup
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.components.CatalogPhoto
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.components.SearchField
import com.meta.pixelandtexel.birdspotter.ui.components.SpeciesRow
import com.meta.pixelandtexel.birdspotter.ui.previews.PreviewCatalog
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * Explore — the field guide's front page.
 *
 * Three sections: a title area, the day's featured bird, and the guide itself. The glasses status
 * pill and the Spot/Listen calls to action land here later.
 *
 * A [LazyColumn] rather than a scrolling [Column], and that is the whole of the "loads as you
 * scroll" behaviour. 93 rows arrive from `catalog.db` in one query — they are cheap. The photos are
 * not, and a lazy list composes a row, and so decodes its thumbnail, only once the row is about to
 * be seen. Paging the query would add state to every layer to save microseconds; see
 * `SpeciesStore.speciesInBrowseOrder`.
 *
 * Takes [onOpenBird] rather than a `NavController` so the screen stays testable and previewable
 * without a navigation graph, rather than reaching for a router.
 */
@Composable
fun ExploreScreen(onOpenBird: (String) -> Unit, modifier: Modifier = Modifier) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: ExploreViewModel = viewModel(
      factory = ExploreViewModel.factory(application.container.birdCatalogRepository),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val query by viewModel.query.collectAsStateWithLifecycle()

  ExploreScreen(
      uiState = uiState,
      query = query,
      onQueryChange = viewModel::search,
      onOpenBird = onOpenBird,
      modifier = modifier,
  )
}

/** The stateless half, so Previews and UI tests can drive every state without a database. */
@Composable
fun ExploreScreen(
    uiState: ExploreUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenBird: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  LazyColumn(
      modifier =
          modifier
              .fillMaxSize()
              .background(BirdSpotterTheme.colors.paper)
              // Nothing sits above this screen, so clearing the status bar is its own job.
              // The background goes on first so the paper runs up behind the clock instead
              // of stopping short of it.
              .windowInsetsPadding(WindowInsets.statusBars),
      // Per-child padding and no `verticalArrangement`, deliberately: the rhythm down this
      // page is not uniform — a section gap above each heading, none at all between the
      // ruled rows of a list — and setting both would add the two together.
      contentPadding =
          PaddingValues(
              start = space.gutter,
              end = space.gutter,
              top = space.section,
              bottom = space.page,
          ),
  ) {
    item(key = "title") { TitleArea() }

    // Above the featured card, not below it: search is the page's way in, and buried
    // under a 4:3 photo it would be a flick off screen. What keeps that honest is that a
    // query replaces everything under this line — see `ExploreUiState.Searching`.
    item(key = "search") {
      SearchField(
          text = query,
          onTextChange = onQueryChange,
          modifier = Modifier.padding(top = space.separate),
      )
    }

    when (uiState) {
      is ExploreUiState.Loading ->
          item(key = "bird-of-the-day") {
            BirdOfTheDayPlaceholder(Modifier.padding(top = space.section))
          }

      is ExploreUiState.Empty ->
          item(key = "bird-of-the-day") {
            CatalogUnavailable(Modifier.padding(top = space.section))
          }

      is ExploreUiState.Ready -> {
        val ready = uiState
        item(key = "bird-of-the-day") {
          BirdOfTheDayCard(
              bird = ready.birdOfTheDay,
              onClick = { onOpenBird(ready.birdOfTheDay.species.id) },
              modifier = Modifier.padding(top = space.section),
          )
        }
        guide(groups = ready.guide, onOpenBird = onOpenBird)
      }

      is ExploreUiState.Searching ->
          searchResults(
              results = uiState.results,
              query = query,
              onOpenBird = onOpenBird,
          )
    }
  }
}

/**
 * The rest of the guide: a heading per section, then its birds as ruled rows.
 *
 * A `LazyListScope` extension rather than a composable, because a composable cannot emit lazy items
 * — wrapping the groups in one `item {}` would compose all 92 rows at once and decode 92 photos
 * with them, which is exactly what the lazy list is here to avoid.
 *
 * Keys are species ids and group names, both stable by contract, so a state change never makes
 * Compose rebuild a row that only moved.
 */
private fun LazyListScope.guide(
    groups: List<SpeciesGroup>,
    onOpenBird: (String) -> Unit,
) {
  groups.forEach { group ->
    item(key = "group-${group.name}") { GuideSectionHeader(group.name) }

    items(group.species, key = { it.species.id }) { bird ->
      SpeciesRow(bird = bird, onClick = { onOpenBird(bird.species.id) })
      // Under every row, including the section's last: a rule closes an index entry
      // the way a printed guide does, and the one before the next heading is what
      // gives that heading something to sit against.
      HairlineRule()
    }
  }
}

/**
 * A query's matches: a count in the slot a section heading would take, then the same ruled rows the
 * guide is built from.
 *
 * Flat, with no section headings. A three-letter query pulls a handful of birds out of as many
 * different sections, and heading each one would put more headings on screen than results.
 *
 * A `LazyListScope` extension for the same reason [guide] is one — a composable cannot emit lazy
 * items, and wrapping the matches in a single `item {}` would compose and decode every one of them
 * at once.
 */
private fun LazyListScope.searchResults(
    results: List<SpeciesWithMedia>,
    query: String,
    onOpenBird: (String) -> Unit,
) {
  item(key = "results-header") { SearchResultsHeader(results.size) }

  if (results.isEmpty()) {
    item(key = "no-matches") { NoMatches(query) }
    return
  }

  items(results, key = { it.species.id }) { bird ->
    SpeciesRow(bird = bird, onClick = { onOpenBird(bird.species.id) })
    HairlineRule()
  }
}

/**
 * The plate over a query's matches — "12 BIRDS", "1 BIRD", "NO MATCHES".
 *
 * Gilt and in the same slot a group header occupies, because that is what it is: the heading of the
 * section you are looking at. A count rather than a bare "RESULTS" since it may as well say the one
 * thing a group heading there cannot.
 */
@Composable
private fun SearchResultsHeader(count: Int, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  PlateLabel(
      text = resultsLabel(count),
      color = BirdSpotterTheme.colors.gilt,
      modifier = modifier.padding(top = space.section, bottom = space.related),
  )
}

/**
 * Plain text on the paper rather than a [CardSurface]. [CatalogUnavailable] earns a card by being a
 * permanent, unusual state; this one comes and goes between keystrokes, and a card flashing in and
 * out reads as a fault.
 */
@Composable
private fun NoMatches(query: String, modifier: Modifier = Modifier) {
  Text(
      text = "Nothing in the guide matches “$query”.",
      style = BirdSpotterTheme.type.body,
      color = BirdSpotterTheme.colors.textSecondary,
      modifier = modifier,
  )
}

/**  */
private fun resultsLabel(count: Int): String =
    when (count) {
      0 -> "No Matches"
      1 -> "1 Bird"
      else -> "$count Birds"
    }

/**
 * A section heading in the guide — "BIRDS OF PREY", "WARBLERS".
 *
 * Gilt, matching the page's own eyebrow rather than the verdigris the featured card uses: the two
 * gilt marks are the page's structure, and the verdigris one is the thing the page is about.
 */
@Composable
private fun GuideSectionHeader(name: String, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  PlateLabel(
      text = name,
      color = BirdSpotterTheme.colors.gilt,
      modifier = modifier.padding(top = space.section, bottom = space.related),
  )
}

/**
 * Eyebrow over a headline, with no chrome above it.
 *
 * There is no top app bar on this screen — an `Explore` title in a bar directly above a headline
 * says the same thing twice, and the reason a field guide opens well is that the page starts with
 * the page, not with furniture.
 */
@Composable
private fun TitleArea(modifier: Modifier = Modifier) {
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.related),
  ) {
    PlateLabel(text = "The Field Guide", color = BirdSpotterTheme.colors.gilt)
    Text(
        text = "Venture into the wild.",
        style = BirdSpotterTheme.type.display,
        color = BirdSpotterTheme.colors.textPrimary,
    )
  }
}

/**
 * The day's species: photo, plate label, name, binomial, and the credit the licence requires.
 * Tapping it opens the full page.
 */
@Composable
private fun BirdOfTheDayCard(
    bird: SpeciesWithMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  CardSurface(modifier = modifier, onClick = onClick) {
    bird.heroPhoto?.let { photo ->
      CatalogPhoto(
          media = photo,
          modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
      )
      HairlineRule()
    }

    // `Spacer`s rather than an arrangement: these gaps are deliberately different
    // sizes, and a uniform column rhythm would flatten the hierarchy.
    val space = BirdSpotterTheme.space
    Column(Modifier.padding(space.cardInset)) {
      PlateLabel(text = "Bird of the Day", color = BirdSpotterTheme.colors.verdigris)
      Spacer(Modifier.height(space.related))
      Text(
          text = bird.species.commonName,
          style = BirdSpotterTheme.type.display,
          color = BirdSpotterTheme.colors.textPrimary,
      )
      Spacer(Modifier.height(space.tight))
      Text(
          text = bird.species.scientificName,
          style = BirdSpotterTheme.type.scientific,
          color = BirdSpotterTheme.colors.textSecondary,
      )
      Spacer(Modifier.height(space.separate))
      Text(
          text = bird.species.familyName,
          style = BirdSpotterTheme.type.label,
          color = BirdSpotterTheme.colors.textFaint,
      )

      bird.heroPhoto?.credit?.let { credit ->
        Spacer(Modifier.height(space.separate))
        HairlineRule()
        Spacer(Modifier.height(space.separate))
        // Not decoration: everything bundled is openly licensed on the condition
        // that the photographer is named. See licenses/ATTRIBUTION.md.
        Text(
            text = "Photograph: $credit",
            style = BirdSpotterTheme.type.caption,
            color = BirdSpotterTheme.colors.textFaint,
        )
      }
    }
  }
}

/** The card's silhouette while the first query runs, so the layout does not jump. */
@Composable
private fun BirdOfTheDayPlaceholder(modifier: Modifier = Modifier) {
  CardSurface(modifier = modifier) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(BirdSpotterTheme.colors.rule),
    )
    Box(Modifier.height(150.dp))
  }
}

/**
 * Shown when the catalog has no species at all — which means the seed never staged, not that the
 * user did anything. Says so plainly rather than blaming the network.
 */
@Composable
private fun CatalogUnavailable(modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  CardSurface(modifier = modifier) {
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = space.cardInset, vertical = space.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.related),
    ) {
      PlateLabel(text = "The Guide Is Empty")
      Text(
          text = "No species were bundled with this build.",
          style = BirdSpotterTheme.type.body,
          color = BirdSpotterTheme.colors.textSecondary,
          textAlign = TextAlign.Center,
      )
    }
  }
}

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun ExploreScreenPreview() {
  BirdSpotterTheme {
    ExploreScreen(
        uiState = ExploreUiState.Ready(PreviewCatalog.bird, PreviewCatalog.guide),
        query = "",
        onQueryChange = {},
        onOpenBird = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExploreScreenDarkPreview() {
  BirdSpotterTheme(darkTheme = true) {
    ExploreScreen(
        uiState = ExploreUiState.Ready(PreviewCatalog.bird, PreviewCatalog.guide),
        query = "",
        onQueryChange = {},
        onOpenBird = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ExploreScreenEmptyPreview() {
  BirdSpotterTheme {
    ExploreScreen(
        uiState = ExploreUiState.Empty,
        query = "",
        onQueryChange = {},
        onOpenBird = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ExploreScreenSearchingPreview() {
  BirdSpotterTheme {
    ExploreScreen(
        uiState =
            ExploreUiState.Searching(
                ExploreViewModel.speciesMatching("corv", PreviewCatalog.guide),
            ),
        query = "corv",
        onQueryChange = {},
        onOpenBird = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun ExploreScreenNoMatchesPreview() {
  BirdSpotterTheme {
    ExploreScreen(
        uiState = ExploreUiState.Searching(emptyList()),
        query = "pelican",
        onQueryChange = {},
        onOpenBird = {},
    )
  }
}
