/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.explore

import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesGroup
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.features.explore.ExploreViewModel.Companion.withoutSpecies
import com.meta.pixelandtexel.birdspotter.ui.previews.PreviewCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Explore's search and browse policy, against `PreviewCatalog.guide` rather than the shipped
 * database.
 *
 * The fixture is deliberate: `BirdCatalogRepositoryTest` already covers the real `catalog.db`, and
 * what is under test here is the rule — which query matches which bird, and what the screen shows
 * for it — not whether the seed staged. Six species in two sections is enough to pin every rule and
 * small enough that a failure names the bird.
 *
 * Everything below calls a companion function directly. `ExploreViewModel.state` is pure so that
 * the whole policy is reachable without driving a ViewModel through `viewModelScope` — which is
 * what keeps this a plain JUnit test, with no Robolectric runner and no main-dispatcher rule.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class ExploreViewModelTest {

  private val guide = PreviewCatalog.guide

  private fun bird(speciesId: String): SpeciesWithMedia =
      guide.flatMap { it.species }.first { it.species.id == speciesId }

  private fun ids(species: List<SpeciesWithMedia>): List<String> = species.map { it.species.id }

  // ── Matching ───────────────────────────────────────────────────────────

  @Test
  fun speciesMatching_matchesCommonNameRegardlessOfCase() {
    val matches = ExploreViewModel.speciesMatching("BLUE jAy", guide)

    assertEquals(listOf("blue-jay"), ids(matches))
  }

  @Test
  fun speciesMatching_matchesPartOfAName() {
    // "thrush" is inside "Wood Thrush" but is also the section — the point is only that a
    // fragment works, so this asserts the fragment of a *name* that no section shares.
    val matches = ExploreViewModel.speciesMatching("rave", guide)

    assertEquals(listOf("common-raven"), ids(matches))
  }

  @Test
  fun speciesMatching_matchesScientificName() {
    val matches = ExploreViewModel.speciesMatching("corvus", guide)

    assertEquals(listOf("american-crow", "common-raven"), ids(matches))
  }

  @Test
  fun speciesMatching_matchesFamilyName() {
    val matches = ExploreViewModel.speciesMatching("turdidae", guide)

    assertEquals(
        listOf("american-robin", "eastern-bluebird", "wood-thrush"),
        ids(matches),
    )
  }

  @Test
  fun speciesMatching_matchesGroupName() {
    val matches = ExploreViewModel.speciesMatching("jays", guide)

    assertEquals(listOf("american-crow", "blue-jay", "common-raven"), ids(matches))
  }

  @Test
  fun speciesMatching_keepsBrowseOrder() {
    val matches = ExploreViewModel.speciesMatching("a", guide)
    val orders = matches.map { it.species.browseOrder }

    assertTrue(matches.size > 1)
    assertEquals(orders.sorted(), orders)
  }

  @Test
  fun speciesMatching_ignoresSurroundingWhitespace() {
    val matches = ExploreViewModel.speciesMatching("   blue jay \n", guide)

    assertEquals(listOf("blue-jay"), ids(matches))
  }

  @Test
  fun speciesMatching_withNoMatch_isEmpty() {
    assertTrue(ExploreViewModel.speciesMatching("pelican", guide).isEmpty())
  }

  @Test
  fun speciesMatching_withBlankQuery_isEmpty() {
    // The blank case belongs to `state`, which browses instead of searching. Matching
    // returns nothing rather than everything, so a caller that skips that check shows an
    // empty result set instead of silently re-listing the whole guide.
    assertTrue(ExploreViewModel.speciesMatching("   ", guide).isEmpty())
  }

  // ── State ──────────────────────────────────────────────────────────────

  @Test
  fun state_withBlankQuery_browsesTheGuideWithoutTheFeaturedBird() {
    val featured = bird("blue-jay")

    val state = ExploreViewModel.state(query = "", birdOfTheDay = featured, groups = guide)

    assertTrue("expected Ready, got $state", state is ExploreUiState.Ready)
    val ready = state as ExploreUiState.Ready
    assertEquals("blue-jay", ready.birdOfTheDay.species.id)
    val remaining = ready.guide.flatMap { it.species }
    assertTrue(!ids(remaining).contains("blue-jay"))
    assertEquals(5, remaining.size)
  }

  @Test
  fun state_withAQuery_findsTheFeaturedBird() {
    // The regression this suite exists for. `Ready`'s guide has the featured bird removed,
    // so a search over *that* list makes today's bird the one bird nobody can look up — on
    // precisely the day it is being demoed.
    val featured = bird("blue-jay")

    val state =
        ExploreViewModel.state(
            query = "blue jay",
            birdOfTheDay = featured,
            groups = guide,
        )

    assertTrue("expected Searching, got $state", state is ExploreUiState.Searching)
    assertEquals(listOf("blue-jay"), ids((state as ExploreUiState.Searching).results))
  }

  @Test
  fun state_withAQueryNothingMatches_isSearchingWithNoResults() {
    val state =
        ExploreViewModel.state(
            query = "pelican",
            birdOfTheDay = bird("blue-jay"),
            groups = guide,
        )

    // Not `Empty`: the catalog is fine, the query just missed. The screen says so.
    assertEquals(ExploreUiState.Searching(emptyList()), state)
  }

  @Test
  fun state_withWhitespaceOnlyQuery_browses() {
    val state =
        ExploreViewModel.state(
            query = "  \n ",
            birdOfTheDay = bird("blue-jay"),
            groups = guide,
        )

    assertTrue("expected Ready, got $state", state is ExploreUiState.Ready)
  }

  @Test
  fun state_withNoBirdOfTheDay_isEmpty() {
    assertEquals(
        ExploreUiState.Empty,
        ExploreViewModel.state(query = "", birdOfTheDay = null, groups = emptyList()),
    )
    // Still empty with a query: nothing staged means nothing to search.
    assertEquals(
        ExploreUiState.Empty,
        ExploreViewModel.state(query = "jay", birdOfTheDay = null, groups = emptyList()),
    )
  }

  // ── Guide pruning ──────────────────────────────────────────────────────

  @Test
  fun withoutSpecies_dropsASectionItEmpties() {
    val single = listOf(SpeciesGroup(name = "Jays & Crows", species = listOf(bird("blue-jay"))))

    val pruned = single.withoutSpecies("blue-jay")

    // A header floating over nothing is worse than a missing header.
    assertTrue(pruned.isEmpty())
  }
}
