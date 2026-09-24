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
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.GlassesError
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechState
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.Transcription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the ASR test screen shows.
 *
 * @property isRunning Whether a session has been asked for and not yet hung up on.
 * @property sessionState Where the session is — `null` before the first reading.
 * @property speechState Where the recogniser is, which is the reading the screen exists for.
 * @property partial The utterance in progress, or `null` between them.
 * @property heard Finished utterances, newest first.
 * @property failure Why the run ended, when it ended badly.
 */
data class SpeechTestUiState(
    val isRunning: Boolean = false,
    val sessionState: GlassesSessionState? = null,
    val speechState: GlassesSpeechState = GlassesSpeechState.IDLE,
    val partial: String? = null,
    val heard: List<Transcription> = emptyList(),
    val failure: String? = null,
)

/**
 * One transcript folded into what is on screen: a partial replaces the line in progress, a final
 * closes it and joins the list.
 *
 * **Both are kept, unlike anywhere else in the app.** A matcher downstream would read finals only —
 * a partial containing an authored prompt would fire an answer mid-sentence — but the partial
 * cadence is exactly what this screen is for: how fast they arrive, and how much of a sentence has
 * to be said before the recogniser commits to it.
 *
 * Internal, and a function of the state rather than a method on the view model, so the mirrored
 * tests can pin the folding without a dispatcher under `viewModelScope` — the same seam
 * `RealtimeViewModel.deliverAnswer` keeps.
 */
internal fun SpeechTestUiState.hearing(transcription: Transcription): SpeechTestUiState =
    if (transcription.isFinal) {
      copy(partial = null, heard = (listOf(transcription) + heard).take(HeardLimit))
    } else {
      copy(partial = transcription.text)
    }

/**
 * How many finished utterances the screen keeps. A test run is read from the top and nobody scrolls
 * to the bottom of one; the log has every line either way.
 */
private const val HeardLimit = 50

/**
 * A session opened for one purpose: to find out whether these glasses transcribe, and what they
 * hear when they do.
 *
 * **A diagnostic, not a feature.** Nothing here identifies a bird, matches a preset or records
 * anything — it starts a session, attaches nothing of its own, and prints what the recogniser
 * sends. The three questions it answers are the three that only hardware can:
 *
 * 1. does the speech capability attach on this pair at all — `UNAVAILABLE` says no;
 * 2. does it reach `LISTENING`, and how long after the session does;
 * 3. what the transcripts actually look like — partial cadence, punctuation, casing, confidence —
 *    which is what a matcher downstream has to be written against.
 *
 * The session comes up the ordinary way, through [GlassesSessionRepository.sessionStream]: the
 * recogniser rides a running session, so there is no shorter path to one, and running the ordinary
 * path is also what makes a failure here mean something about the app people will actually use.
 */
class SpeechTestViewModel(
    private val glassesSession: GlassesSessionRepository,
    private val glassesSpeech: GlassesSpeechRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SpeechTestUiState())
  val uiState: StateFlow<SpeechTestUiState> = _uiState.asStateFlow()

  /** The run: the session's lease, and the two subscriptions that live inside it. */
  private var run: Job? = null

  /**
   * Opens a session and starts printing. Idempotent — a second tap on a running test is a tap
   * nobody meant, and restarting the link underneath one would look like the very instability this
   * screen is here to rule out.
   */
  fun start() {
    if (run?.isActive == true) return
    _uiState.value = SpeechTestUiState(isRunning = true)
    run = viewModelScope.launch { listen() }
  }

  /**
   * Hangs up. The readings stay on screen: the run that just ended is the thing being read, and
   * clearing it at the moment somebody stops to look at it would be the wrong instinct.
   */
  fun stop() {
    run?.cancel()
    run = null
    _uiState.update { it.copy(isRunning = false) }
  }

  override fun onCleared() {
    stop()
    super.onCleared()
  }

  /**
   * The run itself: the session held open, and the recogniser read for as long as it is.
   *
   * The two subscriptions are children of the session's own collection, so hanging up takes them
   * with it — the same lease every other consumer of these streams holds.
   */
  private suspend fun listen() {
    try {
      coroutineScope {
        launch {
          glassesSpeech.speechStateStream().collect { state ->
            _uiState.update { it.copy(speechState = state) }
          }
        }
        launch {
          glassesSpeech.transcriptionStream().collect { heard ->
            _uiState.update { it.hearing(heard) }
          }
        }
        glassesSession.sessionStream().collect { state ->
          _uiState.update { it.copy(sessionState = state) }
        }
      }
      // A session that ends of its own accord — a doff, a fold, a long press — is not a
      // failure, and the switch should go back to offering another run.
      _uiState.update { it.copy(isRunning = false) }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      BirdLog.error(LogCategory.GLASSES, error) { "ASR test — the session ended in failure" }
      _uiState.update { it.copy(isRunning = false, failure = partingFor(error)) }
    }
  }

  /**
   * Why the run ended, in a line. The same two answers the realtime screen gives, because they are
   * the only two the app can tell apart — and the log line beside this one carries the rest.
   */
  private fun partingFor(error: Throwable): String =
      when (error) {
        GlassesError.GlassesUpdateRequired ->
            "Your glasses need a firmware update — check them in the Meta AI app"
        else -> "The glasses session ended — check they are connected and try again"
      }

  companion object {
    fun factory(
        glassesSession: GlassesSessionRepository,
        glassesSpeech: GlassesSpeechRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return SpeechTestViewModel(glassesSession, glassesSpeech) as T
          }
        }
  }
}
