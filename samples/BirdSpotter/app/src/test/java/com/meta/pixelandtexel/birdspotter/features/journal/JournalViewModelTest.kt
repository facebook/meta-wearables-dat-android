/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import com.meta.pixelandtexel.birdspotter.ui.previews.PreviewCatalog
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Journal's search, group and browse policy, against `PreviewCatalog.journalEntries` rather
 * than a live store.
 *
 * Everything below calls a companion function directly. `JournalViewModel.state` and its helpers
 * are pure so the whole policy is reachable without driving a ViewModel through its streams — which
 * is what keeps this a plain JUnit test.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class JournalViewModelTest {

  private val entries = PreviewCatalog.journalEntries

  /**
   * UTC, so the fixture's midday-UTC timestamps fall in the same civil month wherever the test runs
   * and the buckets are deterministic.
   */
  private val utc: ZoneId = ZoneId.of("UTC")

  private fun ids(entries: List<JournalEntry>): List<String> = entries.map { it.id }

  // ── Matching ───────────────────────────────────────────────────────────

  @Test
  fun entriesMatching_matchesCommonNameRegardlessOfCase() {
    assertEquals(
        listOf("preview-outing-2"),
        ids(JournalViewModel.entriesMatching("BLUE jAy", entries)),
    )
  }

  @Test
  fun entriesMatching_matchesEveryBirdTheOutingConfirmed() {
    // The cardinal names outing 1 and rides second on outing 2 — both must match.
    assertEquals(
        listOf("preview-outing-1", "preview-outing-2"),
        ids(JournalViewModel.entriesMatching("cardinalis", entries)),
    )
  }

  @Test
  fun entriesMatching_matchesFamilyName() {
    assertEquals(
        listOf("preview-outing-2"),
        ids(JournalViewModel.entriesMatching("corvidae", entries)),
    )
  }

  @Test
  fun entriesMatching_withABirdlessOuting_findsNothingToMatch() {
    // A walk that confirmed nothing and left no note has no words in it. It is a
    // first-class journal entry and an unsearchable one, now that place is gone —
    // its coordinates are a pin on a map, not a phrase anybody types.
    assertTrue(JournalViewModel.entriesMatching("outing", entries).isEmpty())
  }

  @Test
  fun entriesMatching_matchesNotes() {
    assertEquals(
        listOf("preview-outing-1"),
        ids(JournalViewModel.entriesMatching("maple", entries)),
    )
  }

  @Test
  fun entriesMatching_ignoresSurroundingWhitespace() {
    assertEquals(
        listOf("preview-outing-2"),
        ids(JournalViewModel.entriesMatching("  blue jay \n", entries)),
    )
  }

  @Test
  fun entriesMatching_doesNotMatchScreenLabels() {
    // "No birds confirmed" is wording the screen prints, not something the outing
    // stores, so it is not searchable — a query for it finds nothing rather than every
    // birdless row.
    assertTrue(JournalViewModel.entriesMatching("confirmed", entries).isEmpty())
  }

  @Test
  fun entriesMatching_keepsNewestFirstOrder() {
    // The cardinal is on both bird-bearing outings; the input order (newest first) holds.
    val matches = JournalViewModel.entriesMatching("cardinal", entries)
    assertEquals(listOf("preview-outing-1", "preview-outing-2"), ids(matches))
  }

  @Test
  fun entriesMatching_withNoMatch_isEmpty() {
    assertTrue(JournalViewModel.entriesMatching("pelican", entries).isEmpty())
  }

  @Test
  fun entriesMatching_withBlankQuery_isEmpty() {
    assertTrue(JournalViewModel.entriesMatching("   ", entries).isEmpty())
  }

  // ── State ──────────────────────────────────────────────────────────────

  @Test
  fun state_withNoEntries_isEmpty() {
    assertEquals(
        JournalUiState.Empty,
        JournalViewModel.state(query = "", entries = emptyList(), lifeList = 0, zone = utc),
    )
    // Still empty with a query: nothing logged means nothing to search.
    assertEquals(
        JournalUiState.Empty,
        JournalViewModel.state(query = "jay", entries = emptyList(), lifeList = 5, zone = utc),
    )
  }

  @Test
  fun state_withBlankQuery_groupsByMonth() {
    val state = JournalViewModel.state(query = "", entries = entries, lifeList = 12, zone = utc)

    assertTrue("expected Ready, got $state", state is JournalUiState.Ready)
    val ready = state as JournalUiState.Ready
    assertEquals(12, ready.lifeList)
    assertEquals(listOf("2026-07", "2026-06"), ready.months.map { it.key })
    assertEquals(listOf("preview-outing-1", "preview-outing-2"), ids(ready.months[0].entries))
    assertEquals(listOf("preview-outing-3"), ids(ready.months[1].entries))
  }

  @Test
  fun state_withAQuery_searchesFlat() {
    val state =
        JournalViewModel.state(query = "blue jay", entries = entries, lifeList = 12, zone = utc)

    assertTrue("expected Searching, got $state", state is JournalUiState.Searching)
    val searching = state as JournalUiState.Searching
    assertEquals(12, searching.lifeList)
    assertEquals(listOf("preview-outing-2"), ids(searching.results))
  }

  @Test
  fun state_withAQueryNothingMatches_isSearchingWithNoResults() {
    val state =
        JournalViewModel.state(query = "pelican", entries = entries, lifeList = 12, zone = utc)
    // Not `Empty`: the journal is fine, the query just missed. The screen says so.
    assertEquals(JournalUiState.Searching(lifeList = 12, results = emptyList()), state)
  }

  @Test
  fun state_withWhitespaceOnlyQuery_groups() {
    val state =
        JournalViewModel.state(query = "  \n ", entries = entries, lifeList = 12, zone = utc)
    assertTrue("expected Ready, got $state", state is JournalUiState.Ready)
  }

  // ── Grouping ─────────────────────────────────────────────────────────────

  @Test
  fun groupByMonth_bucketsByCivilMonthNewestFirst() {
    val months = JournalViewModel.groupByMonth(entries, utc)

    assertEquals(listOf("2026-07", "2026-06"), months.map { it.key })
    assertEquals(listOf("preview-outing-1", "preview-outing-2"), ids(months[0].entries))
    assertEquals(listOf("preview-outing-3"), ids(months[1].entries))
  }

  // ── Results label ─────────────────────────────────────────────────────────

  @Test
  fun resultsLabel_readsSingularAndPlural() {
    assertEquals("No Matches", JournalViewModel.resultsLabel(0))
    assertEquals("1 Entry", JournalViewModel.resultsLabel(1))
    assertEquals("12 Entries", JournalViewModel.resultsLabel(12))
  }
}
