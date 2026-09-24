/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.birddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMedia
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.domain.AudioClipPlayer
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * What the bird detail screen renders.
 *
 * [NotFound] is not an error state. [BirdCatalogRepository.findById] answers null for an id the
 * catalog does not have, which is exactly what an address outlives when a seed drops a species — or
 * what a hand-typed deep link looks like. The screen says so plainly rather than spinning forever.
 */
sealed interface BirdDetailUiState {
  data object Loading : BirdDetailUiState

  data class Ready(val bird: SpeciesWithMedia) : BirdDetailUiState

  data object NotFound : BirdDetailUiState
}

/**
 * The bird page's transport state — what the play button and the sonogram's playhead read.
 *
 * Held apart from [BirdDetailUiState] because it changes many times a second while the clip plays,
 * where the page's content changes once; folding the two together would recompose the whole page on
 * every tick.
 */
data class Playback(
    val isPlaying: Boolean = false,
    /** How far through the reference clip, `0..1`. */
    val progress: Double = 0.0,
)

/**
 * The bird page's state holder.
 *
 * Takes the slug rather than a `SpeciesWithMedia` so the screen is reachable by address alone:
 * every tab navigates to `Route.BirdDetail(speciesId)`, and none of them has to be holding the row
 * already.
 */
class BirdDetailViewModel(
    private val speciesId: String,
    private val birdCatalog: BirdCatalogRepository,
    private val player: AudioClipPlayer,
) : ViewModel() {

  private val _uiState = MutableStateFlow<BirdDetailUiState>(BirdDetailUiState.Loading)
  val uiState: StateFlow<BirdDetailUiState> = _uiState.asStateFlow()

  private val _playback = MutableStateFlow(Playback())
  val playback: StateFlow<Playback> = _playback.asStateFlow()

  /**
   * Samples [AudioClipPlayer.progress] at the screen's cadence while a clip plays. The player holds
   * the true position; this decides how often the playhead moves.
   */
  private var tickerJob: Job? = null

  init {
    player.onFinish = { finishPlayback() }
    load()
  }

  /** The clip the play button plays — the primary song or call, once the page is ready. */
  private val referenceAudio: SpeciesMedia?
    get() = (_uiState.value as? BirdDetailUiState.Ready)?.bird?.referenceAudio

  private fun load() {
    viewModelScope.launch {
      val bird = birdCatalog.findById(speciesId)
      _uiState.value = bird?.let(BirdDetailUiState::Ready) ?: BirdDetailUiState.NotFound
    }
  }

  /**
   * Play the reference clip, or pause it if it is already playing. A page with no bundled
   * vocalization has no play button, so this is a no-op there.
   */
  fun togglePlayback() {
    val audio = referenceAudio ?: return
    if (_playback.value.isPlaying) {
      player.pause()
      _playback.update { it.copy(isPlaying = false) } // hold position; toggling resumes
    } else {
      player.play(audio)
      _playback.update { it.copy(isPlaying = true) }
      startTicker()
    }
  }

  /**
   * Move the play position to [progress] (`0..1`) — the sonogram's scrub. Works whether the clip is
   * playing, paused, or has never started: when it plays on, the ticker carries the playhead from
   * the new spot; when it does not, the spot waits there for the next play.
   */
  fun seek(progress: Double) {
    val audio = referenceAudio ?: return
    val clamped = progress.coerceIn(0.0, 1.0)
    player.seek(audio, clamped)
    _playback.update { it.copy(progress = clamped) }
  }

  /**
   * The clip reached its own end: rewind the transport so the play button offers it again from the
   * top rather than sitting spent at the finish line.
   */
  private fun finishPlayback() {
    tickerJob?.cancel()
    _playback.value = Playback()
  }

  private fun startTicker() {
    tickerJob?.cancel()
    tickerJob = viewModelScope.launch {
      while (isActive && player.isPlaying) {
        _playback.update { it.copy(progress = player.progress) }
        delay(TICK_INTERVAL_MS)
      }
    }
  }

  /**
   * Silences the clip and resets the transport. [onCleared] is the idiomatic trigger, firing when
   * the page leaves the back stack, so a call does not keep playing over the screen the user moved
   * on to.
   */
  fun stopPlayback() {
    player.stop()
    tickerJob?.cancel()
    _playback.value = Playback()
  }

  override fun onCleared() {
    stopPlayback()
  }

  companion object {
    /** ~30 Hz — smooth for a playhead sweeping a ten-second clip, cheap to run. */
    private const val TICK_INTERVAL_MS = 33L

    /**
     * Hands the slug, the repository, and the player to the ViewModel without a DI framework — what
     * `viewModel(factory = ...)` needs, since Compose cannot call a constructor that takes
     * arguments.
     *
     * The slug comes through here rather than through `SavedStateHandle`, so the ViewModel takes it
     * the way any other dependency arrives. The route already survives process death by being on
     * the back stack.
     */
    fun factory(
        speciesId: String,
        birdCatalog: BirdCatalogRepository,
        player: AudioClipPlayer,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return BirdDetailViewModel(speciesId, birdCatalog, player) as T
          }
        }
  }
}
