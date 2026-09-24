/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesGroup
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the Explore screen renders.
 *
 * [Empty] is not an error state and not a placeholder — it is what an empty catalog honestly looks
 * like, which is possible only if the seed failed to stage. The screen shows its title area
 * regardless; only the card below it has anything to be missing.
 */
sealed interface ExploreUiState {
  data object Loading : ExploreUiState

  /**
   * [guide] is the rest of the field guide, in checklist order and already sectioned —
   * [birdOfTheDay] is not in it, because it is on screen directly above and printing it twice reads
   * as a bug. A group never empties out from that removal: the smallest one the seed ships holds
   * two species.
   */
  data class Ready(
      val birdOfTheDay: SpeciesWithMedia,
      val guide: List<SpeciesGroup>,
  ) : ExploreUiState

  /**
   * What a non-empty query matched, in browse order.
   *
   * A state of its own rather than a filter applied to [Ready], because a query replaces the whole
   * page below the title: the featured card is not a search result, and leaving it above the
   * matches would make the field look like it had missed something. Empty [results] is the honest
   * no-matches case, not a placeholder.
   */
  data class Searching(val results: List<SpeciesWithMedia>) : ExploreUiState

  data object Empty : ExploreUiState
}

/**
 * Explore's state holder.
 *
 * [today] is injected so a test can pin the date without touching the clock — the same seam
 * `LocalJournalRepository` opens with its `now` parameter.
 */
class ExploreViewModel(
    private val birdCatalog: BirdCatalogRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

  private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
  val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

  /**
   * What is in the search field. Writable only through [search], so it can never disagree with the
   * state that was computed from it.
   */
  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query.asStateFlow()

  /**
   * The whole guide, today's featured bird included.
   *
   * Kept alongside [_uiState] rather than read back out of it, because [ExploreUiState.Ready]'s
   * `guide` has the featured bird taken *out* — searching that copy would make today's bird the one
   * bird in the catalog you cannot look up.
   */
  private var groups: List<SpeciesGroup> = emptyList()
  private var birdOfTheDay: SpeciesWithMedia? = null
  private var isLoaded = false

  init {
    load()
  }

  private fun load() {
    viewModelScope.launch {
      // The catalog decides which bird, but not which day: `toEpochDay()` resolves
      // the device's own calendar here, so "today" means the user's midnight rather
      // than UTC's. The repository takes the day number already settled.
      val bird = birdCatalog.birdOfTheDay(today().toEpochDay())
      birdOfTheDay = bird
      groups = if (bird == null) emptyList() else birdCatalog.browseGroups()
      isLoaded = true
      refresh()
    }
  }

  /**
   * Runs a query against the guide, or clears it when [query] is blank.
   *
   * Synchronous on purpose. [load] already holds all 93 species in memory, so this is a few hundred
   * string comparisons — a keystroke has no reason to wait on a database round trip, and a `LIKE`
   * query would be one more surface to mirror and test in two languages for no gain. See
   * [speciesMatching].
   */
  fun search(query: String) {
    _query.value = query
    refresh()
  }

  /**
   * Recomputes what the screen shows from the query and whatever [load] fetched.
   *
   * Called from both, so a query typed while the first read is still in flight is honoured the
   * moment that read lands rather than quietly dropped.
   */
  private fun refresh() {
    // Still loading: the field is on screen and typeable, but there is nothing to search
    // yet. The query is held and applied when `load()` finishes.
    if (!isLoaded) return
    _uiState.value = state(query = _query.value, birdOfTheDay = birdOfTheDay, groups = groups)
  }

  companion object {

    /**
     * What the screen shows, given a query and what the catalog returned.
     *
     * Pure and static so the whole policy — a blank query browses, a real one searches, and the
     * search covers today's bird as well — is one function a test can call directly, instead of
     * behaviour only reachable by driving a ViewModel through `viewModelScope`. That is what keeps
     * `ExploreViewModelTest` in plain JUnit, with no Robolectric and no main-dispatcher rule.
     */
    fun state(
        query: String,
        birdOfTheDay: SpeciesWithMedia?,
        groups: List<SpeciesGroup>,
    ): ExploreUiState {
      if (birdOfTheDay == null) return ExploreUiState.Empty

      val trimmed = query.trim()
      if (trimmed.isEmpty()) {
        return ExploreUiState.Ready(
            birdOfTheDay = birdOfTheDay,
            guide = groups.withoutSpecies(birdOfTheDay.species.id),
        )
      }
      return ExploreUiState.Searching(speciesMatching(trimmed, groups))
    }

    /**
     * Every species whose common name, binomial, family or section contains [query].
     *
     * Reads [groups] whole, today's featured bird included — see the note on the property of the
     * same name. Matches come back in browse order for free, since that is the order [groups] is
     * already in and flattening preserves it. Nothing is ranked: 93 rows do not need it, and a
     * ranking is one more thing that would have to score identically in two languages.
     *
     * Family and section are searched as well as the two names so that "warbler" finds a section
     * and "corvidae" a family, without either being its own feature.
     *
     * `lowercase()` and a plain `contains`, deliberately, rather than `contains(ignoreCase =
     * true)`: this one folds by the root locale, where a locale-aware fold would make the same word
     * match different rows on two phones set to different regions (`I` → `ı` in Turkish). A
     * locale-independent fold is what makes them agree — the concern `birdOfTheDayIndex` documents
     * at greater length. The shipped catalog is ASCII throughout, so nothing here folds diacritics;
     * a seed that introduces one needs that added deliberately.
     */
    fun speciesMatching(
        query: String,
        groups: List<SpeciesGroup>,
    ): List<SpeciesWithMedia> {
      val needle = query.trim().lowercase()
      if (needle.isEmpty()) return emptyList()

      return groups
          .flatMap { it.species }
          .filter { bird ->
            listOf(
                bird.species.commonName,
                bird.species.scientificName,
                bird.species.familyName,
                bird.species.groupName,
            )
                .any { it.lowercase().contains(needle) }
          }
    }

    /**
     * The guide without one species, and without any section it just emptied.
     *
     * The empty-section guard cannot fire against today's seed — every group ships at least two
     * birds — but it costs one predicate and it is the difference between a later thin group and a
     * header floating over nothing.
     */
    fun List<SpeciesGroup>.withoutSpecies(speciesId: String): List<SpeciesGroup> =
        mapNotNull { group ->
          val kept = group.species.filterNot { it.species.id == speciesId }
          if (kept.isEmpty()) null else group.copy(species = kept)
        }

    /**
     * Hands the repository to the ViewModel without a DI framework — what `viewModel(factory =
     * ...)` needs, since Compose cannot call a constructor that takes arguments.
     *
     * Capturing [birdCatalog] is safe precisely because
     * [com.meta.pixelandtexel.birdspotter.AppContainer] already outlives every Activity; the
     * factory is a short-lived wrapper around a reference that was going to exist for the life of
     * the process anyway.
     */
    fun factory(birdCatalog: BirdCatalogRepository): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(
              modelClass: Class<T>,
              extras: CreationExtras,
          ): T {
            @Suppress("UNCHECKED_CAST")
            return ExploreViewModel(birdCatalog) as T
          }
        }
  }
}
