/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.data.journal.OutingWithChildren
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the Journal screen renders.
 *
 * [Empty] is an honest state, not an error: a fresh install has logged nothing, so the Journal is
 * empty until the first outing saves. It is distinct from [Searching] with no results — one means
 * "you have no entries", the other "none match what you typed".
 */
sealed interface JournalUiState {
  data object Loading : JournalUiState

  data object Empty : JournalUiState

  /**
   * The Journal itself: every outing, newest first, under a heading per month. [lifeList] is the
   * count of distinct confirmed species — the header's stat — carried in the state so the header
   * never disagrees with the list under it.
   */
  data class Ready(val lifeList: Int, val months: List<JournalMonth>) : JournalUiState

  /**
   * What a non-empty query matched, flat and newest first — no month headings, for the same reason
   * Explore's search results have no section headings: a query pulls a handful of entries out of
   * many months, and heading each would put more headings on screen than results. Empty [results]
   * is the honest no-matches case.
   */
  data class Searching(val lifeList: Int, val results: List<JournalEntry>) : JournalUiState
}

/**
 * The Journal's state holder.
 *
 * Where `ExploreViewModel` reads a catalog that cannot change and so loads once, the Journal is
 * **live** — an outing saves while the screen is up — so this combines
 * [JournalRepository.journalStream] and [JournalRepository.lifeListCountStream] and rebuilds on
 * every emission. Each confirmed sighting's `speciesId` is resolved against the catalog (which *is*
 * fixed, so the lookups are memoised) to pair the outing with its birds.
 *
 * [zone] is injected so a test can pin the timezone the month buckets fall in — the same seam
 * `ExploreViewModel` opens with `today`.
 */
class JournalViewModel(
    private val journal: JournalRepository,
    private val birdCatalog: BirdCatalogRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

  /**
   * What is in the search field. Writable only through [search], so it can never disagree with the
   * state computed from it.
   */
  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query.asStateFlow()

  /**
   * `speciesId` → resolved bird. The catalog cannot change while the app runs, so a species is
   * looked up once however many sightings reference it. A stored value of null is a real answer —
   * an id the guide doesn't have — and is not looked up again. Accessed only from the single
   * sequential collector below, so it needs no lock.
   */
  private val speciesCache = mutableMapOf<String, SpeciesWithMedia?>()

  /**
   * The Journal, the life-list count and the query, folded into one state.
   *
   * `combine` waits for all three before its first value, so the screen holds [Loading] until the
   * streams land — the initial value `stateIn` supplies. Each stream `catch`es to a floor of its
   * own so a broken read shows as empty rather than tearing the whole state down: journal to no
   * rows (matching how Explore treats a catalog that won't open), the count to zero.
   */
  val uiState: StateFlow<JournalUiState> = combine(
      journal.journalStream().map { resolveEntries(it) }.catch { emit(emptyList()) },
      journal.lifeListCountStream().catch { emit(0) },
      _query,
  ) { entries, lifeList, query ->
    state(query = query, entries = entries, lifeList = lifeList, zone = zone)
  }
      .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5_000),
          initialValue = JournalUiState.Loading,
      )

  /**
   * Runs a query against the loaded Journal, or clears it when [query] is blank.
   *
   * Synchronous, like Explore's: the entries are already in memory, so filtering them is a few
   * string comparisons a keystroke has no reason to wait on.
   */
  fun search(query: String) {
    _query.value = query
  }

  // ── Resolution ───────────────────────────────────────────────────────────

  /**
   * Pairs each outing with its confirmed birds — story order, resolved through the memoised
   * catalog.
   */
  private suspend fun resolveEntries(snapshot: List<OutingWithChildren>): List<JournalEntry> {
    val resolved = ArrayList<JournalEntry>(snapshot.size)
    for (withChildren in snapshot) {
      val birds =
          JournalEntry.storyOrder(withChildren).map { sighting ->
            ConfirmedBird(sighting, resolveSpecies(sighting.speciesId))
          }
      resolved += JournalEntry(withChildren, birds)
    }
    return resolved
  }

  private suspend fun resolveSpecies(speciesId: String): SpeciesWithMedia? {
    // A present key — even one whose value is null — is a settled answer.
    if (speciesCache.containsKey(speciesId)) return speciesCache[speciesId]
    val resolved = runCatching { birdCatalog.findById(speciesId) }.getOrNull()
    speciesCache[speciesId] = resolved
    return resolved
  }

  companion object {

    /**
     * What the screen shows, given the query and what the streams loaded.
     *
     * Pure and static so the whole policy — no rows browses to empty, a blank query groups by
     * month, a real one searches flat — is one function a test calls directly, the same shape as
     * `ExploreViewModel.state`. That keeps `JournalViewModelTest` in plain JUnit.
     */
    fun state(
        query: String,
        entries: List<JournalEntry>,
        lifeList: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): JournalUiState {
      if (entries.isEmpty()) return JournalUiState.Empty

      val trimmed = query.trim()
      return if (trimmed.isEmpty()) {
        JournalUiState.Ready(lifeList, groupByMonth(entries, zone))
      } else {
        JournalUiState.Searching(lifeList, entriesMatching(trimmed, entries))
      }
    }

    /**
     * Every entry any of whose birds (common, scientific or family name), place or notes contain
     * [query]. A birdless outing has no names to match, but its place and notes still count.
     *
     * `lowercase()` and a plain `contains`, deliberately, rather than `contains(ignoreCase =
     * true)`: the locale-independent fold is what makes two phones side by side return the same
     * rows for the same word — the same reason, spelled out at length, that
     * `ExploreViewModel.speciesMatching` folds this way.
     */
    fun entriesMatching(query: String, entries: List<JournalEntry>): List<JournalEntry> {
      val needle = query.trim().lowercase()
      if (needle.isEmpty()) return emptyList()

      return entries.filter { entry ->
        searchableStrings(entry).any { it.lowercase().contains(needle) }
      }
    }

    private fun searchableStrings(entry: JournalEntry): List<String> = buildList {
      for (bird in entry.birds) {
        bird.species?.species?.let {
          add(it.commonName)
          add(it.scientificName)
          add(it.familyName)
        }
      }
      entry.outing.notes?.let { add(it) }
    }

    /**
     * Buckets entries by the civil month of their `startedAt`, newest month first, entries within
     * each newest first.
     *
     * Relies on the stream arriving sorted `startedAt` descending: the [LinkedHashMap]'s insertion
     * order is then already newest-first, so no month or entry needs re-sorting. [zone] decides
     * which day a midnight-adjacent outing falls in — pinned in tests, the system zone in the app.
     */
    fun groupByMonth(
        entries: List<JournalEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<JournalMonth> {
      val buckets = LinkedHashMap<String, MutableList<JournalEntry>>()
      val titles = HashMap<String, String>()

      for (entry in entries) {
        val startedAt = entry.outing.startedAt
        val key = JournalFormatting.monthKey(startedAt, zone)
        buckets
            .getOrPut(key) {
              titles[key] = JournalFormatting.monthTitle(startedAt, zone)
              mutableListOf()
            }
            .add(entry)
      }

      return buckets.map { (key, monthEntries) ->
        JournalMonth(key = key, title = titles.getValue(key), entries = monthEntries)
      }
    }

    /**
     * The plate over a query's matches — "12 ENTRIES", "1 ENTRY", "NO MATCHES". Entries, not
     * sightings: a journal row is an outing, and an outing with nothing confirmed is still a row.
     */
    fun resultsLabel(count: Int): String =
        when (count) {
          0 -> "No Matches"
          1 -> "1 Entry"
          else -> "$count Entries"
        }

    /**
     * Hands the repositories to the ViewModel without a DI framework — what `viewModel(factory =
     * ...)` needs, since Compose cannot call a constructor that takes arguments. Safe to capture
     * them because [com.meta.pixelandtexel.birdspotter.AppContainer] already outlives every
     * Activity.
     */
    fun factory(
        journal: JournalRepository,
        birdCatalog: BirdCatalogRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return JournalViewModel(journal, birdCatalog) as T
          }
        }
  }
}
