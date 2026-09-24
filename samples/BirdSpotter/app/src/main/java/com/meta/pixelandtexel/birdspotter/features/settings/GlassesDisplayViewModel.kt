/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesGroup
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.data.display.DisplaySettingsStore
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.GlassesDisplayRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesError
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.features.explore.ExploreViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the display screen shows.
 *
 * @property isRunning Whether a session has been asked for and not yet hung up on.
 * @property sessionState Where the session is — `null` before the first reading.
 * @property hasDisplay Whether this pair has a panel at all — `null` until a pair is listed.
 * @property query What is in the search field.
 * @property birds The pickable guide: everything on a blank query, the matches otherwise.
 * @property shownBird The bird whose card is up on the glasses, or `null` when none is.
 * @property customBird The custom card's chosen bird, or `null` when none has been chosen.
 * @property customMessage The custom card's line — what will stand in for the catalog's description
 *   when the custom card is sent.
 * @property failure Why the run ended, when it ended badly.
 */
data class GlassesDisplayUiState(
    val isRunning: Boolean = false,
    val sessionState: GlassesSessionState? = null,
    val hasDisplay: Boolean? = null,
    val query: String = "",
    val birds: List<SpeciesWithMedia> = emptyList(),
    val shownBird: SpeciesWithMedia? = null,
    val customBird: SpeciesWithMedia? = null,
    val customMessage: String = "",
    val failure: String? = null,
)

/**
 * A session opened for one purpose: to put any bird's card on the glasses' panel, by hand.
 *
 * **A demo control, not a feature.** The live flow already sends a card the moment a bird is
 * identified; what it cannot do is put a *chosen* bird up on cue, hold it there, and take it down
 * again — which is exactly what showing the display to somebody calls for. So this screen opens a
 * session the ordinary way, waits for nothing, and sends whatever row is tapped. The card stays up
 * until another row replaces it, **Clear screen** takes it down, or the screen is left — leaving is
 * a clear, because a card nobody is presenting is a card nobody asked for.
 *
 * The session comes up through [GlassesSessionRepository.displaySessionStream] the moment the
 * screen opens: the whole point of being here is to send, and a screen that made somebody press
 * Connect first would just be adding a step to every rehearsal. The display-only lease,
 * deliberately — this screen draws and does nothing else, so no camera is lit and no recogniser
 * started on its behalf.
 */
class GlassesDisplayViewModel(
    private val glassesSession: GlassesSessionRepository,
    private val glassesDisplay: GlassesDisplayRepository,
    private val birdCatalog: BirdCatalogRepository,
    private val settings: DisplaySettingsStore,
) : ViewModel() {

  private val _uiState = MutableStateFlow(GlassesDisplayUiState())
  val uiState: StateFlow<GlassesDisplayUiState> = _uiState.asStateFlow()

  /**
   * The whole guide, kept beside the state for re-filtering — the same reason Explore keeps its own
   * unfiltered copy.
   */
  private var guide: List<SpeciesGroup> = emptyList()

  /** The run: the session's lease, and the device watcher that lives inside it. */
  private var run: Job? = null

  /**
   * The send in flight. The next tap cancels it rather than queueing behind it, so cards land in
   * tap order and a slow photo load can never put an older bird over a newer one.
   */
  private var send: Job? = null

  init {
    _uiState.update { it.copy(customMessage = settings.customMessage) }
    viewModelScope.launch {
      guide = birdCatalog.browseGroups()
      // The stored bird resolves against the same load; a slug the catalog no longer
      // carries reads as unchosen rather than as an error to explain.
      val customBird = settings.customBirdId?.let { birdCatalog.findById(it) }
      _uiState.update { it.copy(birds = birdsFor(it.query, guide), customBird = customBird) }
    }
    start()
  }

  /**
   * Opens the session. Idempotent — called once from init, and again only by the retry offered
   * after a failure.
   */
  fun start() {
    if (run?.isActive == true) return
    _uiState.update { it.copy(isRunning = true, failure = null) }
    run = viewModelScope.launch {
      try {
        hold()
      } finally {
        // The card must not outlive the screen: whatever ends the run — a failure,
        // the session's own end, or this model being cleared on the way out — the
        // panel is wiped on the way down. Non-cancellable because the usual way out
        // *is* cancellation, and a clear that dies with its scope clears nothing.
        withContext(NonCancellable) { glassesDisplay.clear() }
      }
    }
  }

  /** Runs a query against the guide, or restores the whole of it when blank. */
  fun search(query: String) {
    _uiState.update { it.copy(query = query, birds = birdsFor(query, guide)) }
  }

  /** Sends the bird's card up. Replaces whatever the panel was showing. */
  fun show(bird: SpeciesWithMedia) {
    send?.cancel()
    send = viewModelScope.launch {
      glassesDisplay.showGallery(bird)
      _uiState.update { it.copy(shownBird = bird) }
    }
  }

  /** Takes the card down and says so. */
  fun clearScreen() {
    send?.cancel()
    send = viewModelScope.launch {
      glassesDisplay.clear()
      _uiState.update { it.copy(shownBird = null) }
    }
  }

  /** The custom card's bird. Remembered, so the setup survives the app being restarted. */
  fun pickCustomBird(bird: SpeciesWithMedia) {
    settings.customBirdId = bird.species.id
    _uiState.update { it.copy(customBird = bird) }
  }

  /** The custom card's line, saved as it is typed — see [DisplaySettingsStore] for why. */
  fun editCustomMessage(message: String) {
    settings.customMessage = message
    _uiState.update { it.copy(customMessage = message) }
  }

  /**
   * Sends the custom card up: the chosen bird's gallery, with the presenter's line in place of the
   * catalog's description. Replaces whatever the panel was showing, exactly as a row tap does.
   */
  fun showCustom() {
    val state = _uiState.value
    val bird = state.customBird ?: return
    if (!customCardReady(bird, state.customMessage)) return
    send?.cancel()
    send = viewModelScope.launch {
      glassesDisplay.showGallery(bird, state.customMessage.trim())
      _uiState.update { it.copy(shownBird = bird) }
    }
  }

  /**
   * The run itself: the session held open, and the device snapshot read for as long as it is — the
   * snapshot is where [GlassesDisplayUiState.hasDisplay] comes from, and it is the one reading that
   * decides whether a tap here means anything.
   */
  private suspend fun hold() {
    try {
      coroutineScope {
        // The watcher outlives nothing: the device stream never completes on its
        // own, so it is cancelled by hand once the session's stream has ended.
        val watcher = launch {
          glassesSession.deviceInfoStream().collect { device ->
            _uiState.update { it.copy(hasDisplay = device?.hasDisplay) }
          }
        }
        glassesSession.displaySessionStream().collect { state ->
          _uiState.update { it.copy(sessionState = state) }
        }
        watcher.cancel()
      }
      // A session that ends of its own accord — a doff, a fold, a long press — takes
      // the card with it, so the screen must stop claiming one is up.
      _uiState.update { it.copy(isRunning = false, shownBird = null) }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      BirdLog.error(LogCategory.GLASSES, error) {
        "display screen — the session ended in failure"
      }
      _uiState.update {
        it.copy(isRunning = false, shownBird = null, failure = partingFor(error))
      }
    }
  }

  /**
   * Why the run ended, in a line. The same two answers the realtime screen gives, because they are
   * the only two the app can tell apart — the log line beside this one carries the rest.
   */
  private fun partingFor(error: Throwable): String =
      when (error) {
        GlassesError.GlassesUpdateRequired ->
            "Your glasses need a firmware update — check them in the Meta AI app"
        else -> "The glasses session ended — check they are connected and try again"
      }

  companion object {

    /**
     * The pickable list for a query: the whole guide flattened when it is blank, the matches
     * otherwise — through [ExploreViewModel.speciesMatching], deliberately, so "search" means the
     * same thing on this screen as it does on Explore and the locale-fold reasoning documented
     * there is written once.
     */
    fun birdsFor(query: String, groups: List<SpeciesGroup>): List<SpeciesWithMedia> {
      val trimmed = query.trim()
      return if (trimmed.isEmpty()) {
        groups.flatMap { it.species }
      } else {
        ExploreViewModel.speciesMatching(trimmed, groups)
      }
    }

    /**
     * Whether the custom card can be sent: a bird chosen, and a message with ink in it. A
     * whitespace message is no message — a card whose last line is blank would read as the send
     * having dropped the description.
     */
    fun customCardReady(bird: SpeciesWithMedia?, message: String): Boolean =
        bird != null && message.isNotBlank()

    fun factory(
        glassesSession: GlassesSessionRepository,
        glassesDisplay: GlassesDisplayRepository,
        birdCatalog: BirdCatalogRepository,
        settings: DisplaySettingsStore,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return GlassesDisplayViewModel(glassesSession, glassesDisplay, birdCatalog, settings)
                as T
          }
        }
  }
}
