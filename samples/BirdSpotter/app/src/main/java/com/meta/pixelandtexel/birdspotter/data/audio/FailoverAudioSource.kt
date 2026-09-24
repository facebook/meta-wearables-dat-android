/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.audio

import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureError
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureSource
import com.meta.pixelandtexel.birdspotter.domain.AudioChunk
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen

/**
 * One microphone stream over two real ones, so a session can change its ears without changing its
 * spine.
 *
 * **The screen must not learn that failover exists.** This keeps the contract the session was built
 * on — 16 kHz mono, cold, continuous for as long as anyone collects it — while the actual
 * microphone underneath comes and goes.
 *
 * It cannot, though, keep [kind]. That is a constant on [AudioCaptureSource] — *this is the phone
 * microphone* — and a wrapper whose answer changes halfway through a session has no honest value to
 * put there, so it answers with the fallback it always falls back to. **Which microphone is live
 * moves onto the chunk**: [AudioChunk.source] carries it, and the pill reads the latest one.
 *
 * **The two transitions are asymmetric**, and building them as one thing gets the good half wrong.
 * Coming *back* to the glasses costs nothing, so it is only ever done on request. Losing them costs
 * what it costs — a disconnect is discovered rather than announced — so the phone is reopened the
 * moment the preferred stream fails, without asking anybody. *Never close a working microphone on
 * the promise of a better one; close it on the arrival of one.*
 *
 * **Still one microphone at a time.** The ideal in the design doc has both microphones open across
 * the handover, with the phone cut only when the first glasses chunk lands. The glasses now arrive
 * on the camera stream rather than as a route for the phone's own recorder, so the two *can* be
 * open at once — but this class still swaps them, and the handover costs a beat of silence until it
 * learns to overlap them. It is the one place that changes when it does.
 *
 * **A phone that will not open is waited for, not given up on.** The session's spine is this
 * stream, and a stream that fails ends the session — which is the wrong answer for the two ways the
 * phone's microphone ordinarily refuses: a session started with the phone locked in a pocket (the
 * platform will not hand a recorder to an app it cannot see) and a phone call taking the microphone
 * mid-walk. Both come back on their own, so the stream stays open, empty, and tries the phone again
 * every [fallbackRetryDelayMillis] — and the glasses, once asked for, open the moment they are
 * asked, because their microphone is not a recorder at all. The one refusal that is final is the
 * grant being gone.
 *
 * @property fallbackRetryDelayMillis How long a phone that would not open waits before it is tried
 *   again. Short enough that an unlock is heard within a breath, long enough that a phone refusing
 *   for a whole walk costs nothing worth measuring.
 */
class FailoverAudioSource(
    private val preferred: AudioCaptureSource,
    private val fallback: AudioCaptureSource,
    private val fallbackRetryDelayMillis: Long = 2_000L,
) : AudioCaptureSource {

  private val wantsPreferred = MutableStateFlow(false)

  /**
   * Every time the preferred microphone has been asked for and could not be had — see
   * [preferredLost].
   *
   * Buffered by one and dropping the oldest, because what a listener needs is *the glasses are not
   * the ears* and not a count of how many times that has been true.
   */
  private val preferredLosses = MutableSharedFlow<Unit>(
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /**
   * The fallback's, because that is the one this can always honour. What is actually live is on
   * each [AudioChunk].
   */
  override val kind: CaptureSourceKind
    get() = fallback.kind

  /**
   * Says when the preferred microphone was asked for and could not be had, so that whoever asked
   * can stop waiting for it.
   *
   * **Falling back is silent to the stream on purpose, and that silence has to end somewhere.** The
   * chunks go on arriving and the session never notices, which is the whole point of this class —
   * but the screen above it asked a question, and *the answer is no* is not something it can read
   * off a stream that looks exactly as it did before. Without this the request is outstanding
   * forever: the pill promises a crossing that has already been abandoned.
   *
   * It fires on the losing, not on the state — a collector arriving afterwards has missed it, which
   * is right for something the screen turns into a sentence about what just happened.
   */
  fun preferredLost(): Flow<Unit> = preferredLosses.asSharedFlow()

  /**
   * Ask for the preferred microphone, or hand the session back to the fallback.
   *
   * A request, not a promise: the pill only claims the glasses once a chunk has actually come out
   * of them. Safe to call when no stream is running — the answer is remembered for the next one.
   */
  fun usePreferred(wanted: Boolean) {
    if (wantsPreferred.value == wanted) return
    wantsPreferred.value = wanted
    BirdLog.info(LogCategory.AUDIO) {
      "microphone requested — ${if (wanted) "glasses" else "phone"}"
    }
  }

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  override fun audioStream(): Flow<AudioChunk> = wantsPreferred.flatMapLatest { wanted ->
    // `flatMapLatest` is the whole handover: a change of request cancels the microphone that
    // was open and opens the other one — the one-at-a-time swap described above.
    if (wanted) glassesThenPhone() else phoneUntilItOpens()
  }

  /**
   * The phone, tried again every [fallbackRetryDelayMillis] for as long as it refuses — see the
   * class's doc. A change of request cancels the wait along with the rest, so the glasses are
   * opened the moment they are asked for.
   */
  private fun phoneUntilItOpens(): Flow<AudioChunk> =
      stamped(fallback).retryWhen { error, _ ->
        // The one refusal nothing waits out: the grant is gone, and the Identify gate is
        // where that gets fixed.
        if (error is AudioCaptureError.AccessDenied) {
          BirdLog.error(LogCategory.AUDIO, error) {
            "the phone microphone is not allowed — the session is deaf"
          }
          return@retryWhen false
        }
        BirdLog.warning(LogCategory.AUDIO) {
          "the phone microphone would not open — waiting to try again"
        }
        BirdLog.debug(LogCategory.AUDIO) { "the microphone's own error was $error" }
        delay(fallbackRetryDelayMillis)
        true
      }

  /**
   * The glasses, falling back to the phone the moment they give out.
   *
   * Discovered, not announced — so this does not wait to be told, and it does not end the session
   * over a microphone that can be replaced.
   */
  private fun glassesThenPhone(): Flow<AudioChunk> = flow {
    var lost = false
    stamped(preferred)
        .catch { error ->
          if (error is CancellationException) throw error
          BirdLog.warning(LogCategory.AUDIO) {
            "glasses microphone gave out — falling back to the phone"
          }
          BirdLog.debug(LogCategory.AUDIO) { "the microphone's own error was $error" }
          lost = true
        }
        .collect { emit(it) }
    if (lost) {
      // Said before the phone reopens, so whoever asked for the glasses hears the answer
      // rather than watching chunks arrive and drawing their own conclusion.
      preferredLosses.tryEmit(Unit)
      // **The phone is opened by the switch in [audioStream], not here.** Changing the
      // request is what makes `flatMapLatest` replace this flow with the phone's — and a
      // phone opened here as well was opened twice: once by this line, cancelled a beat
      // later when the switch caught up, and once more by the switch. On a real recorder
      // that is an open, a close and an open in the space of a few milliseconds.
      wantsPreferred.value = false
    }
  }

  /**
   * One source's chunks, stamped with what it actually is — read here rather than trusted from the
   * chunk, so the one fact the pill reads is the one this class chose.
   */
  private fun stamped(source: AudioCaptureSource): Flow<AudioChunk> =
      source
          .audioStream()
          .onStart {
            // **The line this whole category exists for.** "The session went quiet" and
            // "the session was listening to the wrong microphone the whole time" are the
            // same report from the far side of a room, and this is what separates them
            // afterwards.
            BirdLog.info(LogCategory.AUDIO) { "microphone open — ${source.kind}" }
          }
          .map { chunk ->
            AudioChunk(
                samples = chunk.samples,
                sampleRate = chunk.sampleRate,
                source = source.kind,
            )
          }
}
