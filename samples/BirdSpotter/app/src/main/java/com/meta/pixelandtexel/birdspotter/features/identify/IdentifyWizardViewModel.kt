/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.data.catalog.BirdBehavior
import com.meta.pixelandtexel.birdspotter.data.catalog.PlumageColor
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.data.journal.CaptureLocation
import com.meta.pixelandtexel.birdspotter.data.journal.OutingDraft
import com.meta.pixelandtexel.birdspotter.data.journal.OutingKind
import com.meta.pixelandtexel.birdspotter.data.journal.PendingEvent
import com.meta.pixelandtexel.birdspotter.data.journal.PendingSighting
import com.meta.pixelandtexel.birdspotter.data.journal.WizardTrait
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import com.meta.pixelandtexel.birdspotter.domain.IdentifyQuery
import com.meta.pixelandtexel.birdspotter.domain.JournalError
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import com.meta.pixelandtexel.birdspotter.domain.LocationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The wizard's stations, in walking order. The first three are questions ("1 of 3"); [RESULTS] is
 * the list they narrow to.
 */
enum class IdentifyWizardStep {
  SIZE,
  COLORS,
  BEHAVIOR,
  RESULTS;

  /** 1-based position for the "2 of 3" header. Meaningless for [RESULTS]. */
  val questionNumber: Int
    get() = ordinal + 1
}

/**
 * Why a save did not happen, for the one message the Results station can show.
 *
 * [NO_LOCATION_FIX] is its own case rather than folded into [WRITE_FAILED] because the two ask
 * different things of the user: a failed write is worth simply tapping again, while a missing fix
 * is worth stepping outside first. Both leave the button live.
 */
enum class IdentifyWizardSaveError {
  NO_LOCATION_FIX,
  WRITE_FAILED,
}

/**
 * Everything the wizard screen renders — one value, because the four stations share the answers and
 * the answers outlive the station that asked for them (going back must not forget what was picked).
 *
 * [spottedOn] and [coordinate] are *stamped*, not asked: the flow takes *when* (today) and *where*
 * (the phone's fix) on its own, so the only questions left are about the bird. Only [sizeClass],
 * [colors], and [behavior] filter the catalog — see `IdentifyQuery`. [candidates] is null while the
 * results query runs, then the answer, possibly empty.
 */
data class IdentifyWizardUiState(
    val step: IdentifyWizardStep = IdentifyWizardStep.SIZE,
    val spottedOn: LocalDate,
    /**
     * The phone's fix, stamped onto the outing. Null only until
     * [IdentifyWizardViewModel.captureLocation] returns; a save that arrives before it does asks
     * again rather than proceeding without one.
     */
    val coordinate: Coordinate? = null,
    val sizeClass: Int? = null,
    val colors: Set<PlumageColor> = emptySet(),
    val behavior: BirdBehavior? = null,
    val candidates: List<SpeciesWithMedia>? = null,
    /** Species id of a save in flight — at most one, ever. */
    val savingSpeciesId: String? = null,
    /** Species id the user confirmed. One per wizard run; set once, never cleared. */
    val savedSpeciesId: String? = null,
    /** Why the last save did not happen. Cleared the moment another is attempted. */
    val saveError: IdentifyWizardSaveError? = null,
) {
  /**
   * Whether the pinned Next button is live. Each question earns it once answered; Results has
   * nowhere to advance to.
   */
  val canAdvance: Boolean
    get() =
        when (step) {
          IdentifyWizardStep.SIZE -> sizeClass != null
          IdentifyWizardStep.COLORS -> colors.isNotEmpty()
          IdentifyWizardStep.BEHAVIOR -> behavior != null
          IdentifyWizardStep.RESULTS -> false
        }
}

/**
 * The step-by-step identify flow: three answers in, a shortlist out, and — when the user taps "This
 * is my bird" — one `MANUAL` outing written in one breath: the outing, its three `WIZARD_ANSWER`
 * events (the provenance the journal shows for the ID), and the one confirmed sighting.
 *
 * *When* and *where* are taken automatically: [IdentifyWizardUiState.spottedOn] is today, and a
 * one-shot [LocationProvider] fix stamps the outing's coordinates. The place and date questions the
 * flow used to ask are gone — the Identify tab gates entry on the location permission instead, so
 * by the time the wizard runs a fix is expected. Expected, not guaranteed: the permission is not a
 * fix, so a save with none in hand asks once more and then declines, because `Outing`'s coordinates
 * are required.
 *
 * [today] and [now] are injected so tests can pin the clock, same seam as everywhere else.
 */
class IdentifyWizardViewModel(
    private val birdCatalog: BirdCatalogRepository,
    private val journal: JournalRepository,
    private val locationProvider: LocationProvider,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  private val _uiState = MutableStateFlow(IdentifyWizardUiState(spottedOn = today()))
  val uiState: StateFlow<IdentifyWizardUiState> = _uiState.asStateFlow()

  /**
   * Grabs the phone's position once, in the background, and stamps it onto the in-flight answers.
   * The screen calls this when the wizard appears, so the fix is normally waiting long before the
   * user reaches Results. Idempotent enough — a later fix replaces an earlier one, and
   * [saveSighting] asks again itself if none ever landed.
   */
  fun captureLocation() {
    viewModelScope.launch {
      val fix = locationProvider.currentCoordinate()
      _uiState.update { it.copy(coordinate = fix) }
    }
  }

  fun chooseSizeClass(sizeClass: Int) {
    require(sizeClass in 1..7) { "sizeClass is 1-7 on the sparrow-to-goose scale" }
    _uiState.update { it.copy(sizeClass = sizeClass) }
  }

  /** Toggles a swatch. A fourth color is ignored, not queued — same as the reference app. */
  fun toggleColor(color: PlumageColor) = _uiState.update {
    when {
      color in it.colors -> it.copy(colors = it.colors - color)
      it.colors.size < MAX_COLORS -> it.copy(colors = it.colors + color)
      else -> it
    }
  }

  fun chooseBehavior(behavior: BirdBehavior) = _uiState.update { it.copy(behavior = behavior) }

  fun advance() {
    val state = _uiState.value
    if (!state.canAdvance) return
    val next = IdentifyWizardStep.entries[state.step.ordinal + 1]
    // Candidates reset on every entry: coming back and changing an answer must never show the
    // previous answer's list, even for the moment the query takes.
    _uiState.update { it.copy(step = next, candidates = null) }
    if (next == IdentifyWizardStep.RESULTS) loadCandidates()
  }

  /**
   * One station back. False at the first station — that back press belongs to navigation, and the
   * caller pops the wizard itself.
   */
  fun goBack(): Boolean {
    val step = _uiState.value.step
    if (step == IdentifyWizardStep.SIZE) return false
    _uiState.update { it.copy(step = IdentifyWizardStep.entries[step.ordinal - 1]) }
    return true
  }

  private fun loadCandidates() {
    val state = _uiState.value
    val sizeClass = state.sizeClass ?: return
    val behavior = state.behavior ?: return
    viewModelScope.launch {
      val found =
          birdCatalog.identifyCandidates(
              IdentifyQuery(sizeClass = sizeClass, colors = state.colors, behavior = behavior),
          )
      // Stale-guard: only publish onto the results station. Backing out mid-query and
      // re-advancing restarts the load against the edited answers.
      _uiState.update {
        if (it.step == IdentifyWizardStep.RESULTS) it.copy(candidates = found) else it
      }
    }
  }

  /**
   * "This is my bird": the wizard's answers, which have been sitting in [uiState] since the user
   * gave them, assembled into one `MANUAL` [OutingDraft] and written in a single call. No capture,
   * so no media, no confidence, no timeline offsets; the location is the phone's own fix rather
   * than a glasses one.
   *
   * The answers are stored as `WIZARD_ANSWER` events in the catalog enums' own spellings — colors
   * sorted so the stored string is deterministic on both platforms, where the selection order of a
   * set is not.
   */
  fun saveSighting(speciesId: String) {
    val state = _uiState.value
    if (state.savingSpeciesId != null || state.savedSpeciesId != null) return
    // Results is unreachable without all three answers; bail rather than store a partial set.
    val sizeClass = state.sizeClass ?: return
    val behavior = state.behavior ?: return
    _uiState.update { it.copy(savingSpeciesId = speciesId, saveError = null) }
    viewModelScope.launch {
      try {
        // An outing has to know where it happened. The tab is gated on the location
        // permission, so by here a fix is normally already in hand; when the ask is
        // still in flight — or when it came back empty on a phone that has the
        // permission but no signal — ask once more and then decline rather than
        // logging a bird nowhere. Declining lands in the same state a failed write
        // does: the button stays live.
        val coordinate =
            _uiState.value.coordinate
                ?: locationProvider.currentCoordinate()
                ?: throw JournalError.NoLocationFix
        journal.saveOuting(
            OutingDraft(
                kind = OutingKind.MANUAL,
                startedAt = now(),
                location =
                    CaptureLocation(
                        latitude = coordinate.latitude,
                        longitude = coordinate.longitude,
                    ),
                events =
                    listOf(
                        PendingEvent.wizardAnswer(WizardTrait.SIZE, sizeClass.toString()),
                        PendingEvent.wizardAnswer(
                            WizardTrait.COLORS,
                            state.colors.map { it.name }.sorted().joinToString(","),
                        ),
                        PendingEvent.wizardAnswer(WizardTrait.BEHAVIOR, behavior.name),
                    ),
                sightings = listOf(PendingSighting(speciesId = speciesId)),
            ),
        )
        _uiState.update { it.copy(savingSpeciesId = null, savedSpeciesId = speciesId) }
      } catch (e: Exception) {
        // A failure leaves the button live to try again *and says so*. Clearing the
        // spinner alone was the bug: the tap looked like it did nothing, which reads as
        // a broken app rather than as a phone that could not place itself. The write is
        // one transaction, so the Journal is exactly as it was either way.
        val reason =
            if (e is JournalError.NoLocationFix) {
              IdentifyWizardSaveError.NO_LOCATION_FIX
            } else {
              IdentifyWizardSaveError.WRITE_FAILED
            }
        _uiState.update { it.copy(savingSpeciesId = null, saveError = reason) }
      }
    }
  }

  companion object {
    /** The color step's cap — naming more than three "main colors" stops being true. */
    const val MAX_COLORS = 3

    fun factory(
        birdCatalog: BirdCatalogRepository,
        journal: JournalRepository,
        locationProvider: LocationProvider,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(
              modelClass: Class<T>,
              extras: CreationExtras,
          ): T {
            @Suppress("UNCHECKED_CAST")
            return IdentifyWizardViewModel(birdCatalog, journal, locationProvider) as T
          }
        }
  }
}
