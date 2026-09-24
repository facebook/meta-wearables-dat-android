/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.location

import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.GazeProvider
import com.meta.pixelandtexel.birdspotter.domain.HeadingProvider
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion

/**
 * One stream of readings over two real ones, so a session can change instruments without changing
 * its spine.
 *
 * **The screen must not learn that failover exists.** Both aim readings are cold flows of degrees
 * that run for as long as anyone collects them, and that contract holds while the instrument
 * underneath comes and goes.
 *
 * **The two transitions are asymmetric**, the same way the microphone's are, and building them as
 * one thing gets the good half wrong. Crossing *to* the glasses costs nothing, so it is only ever
 * done on request. Losing them is discovered rather than announced — a flow that ends is all the
 * notice there is — so the fallback is reopened immediately, without asking anybody.
 *
 * **A flow that ends is the whole failure signal here**, because neither reading throws. Both
 * providers answer a device that cannot help by emitting nothing and completing, so *the glasses
 * have no compass* and *the glasses went away* arrive identically, and identically is how they
 * should be handled: go back to the instrument in the watcher's hand.
 */
class FailoverReadings(
    private val reading: String,
    private val preferred: () -> Flow<Double>,
    private val fallback: () -> Flow<Double>,
) {
  private val wantsPreferred = MutableStateFlow(false)

  /**
   * Ask for the preferred instrument, or hand the session back to the fallback.
   *
   * A request, not a promise. Safe to call when nothing is collecting — the answer is remembered
   * for the next one.
   */
  fun usePreferred(wanted: Boolean) {
    wantsPreferred.value = wanted
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun stream(): Flow<Double> = wantsPreferred.flatMapLatest { wanted ->
    // **Which instrument is answering is invisible from anywhere else**, by design: that is
    // the whole point of the wrapper. It is also the first thing anyone asks when a reading on
    // screen does not move with the wearer's head, and there is no other way to find out.
    BirdLog.info(LogCategory.GLASSES) {
      "$reading — reading from the ${if (wanted) "glasses" else "phone"}"
    }
    if (!wanted) {
      // The fallback ending is this whole reading ending: there is nowhere left to go, which
      // is the silence a device with no such instrument is entitled to.
      fallback()
    } else {
      preferred().onCompletion { cause ->
        // **Only a flow that ended by itself hands back.** A cancellation here is this
        // very switch being thrown — the request already changed, and flipping it again
        // would fight the collector that is replacing us.
        if (cause == null) {
          // The one handback nothing else records: the instrument was asked for, said it
          // had nothing, and the question went quietly back to the phone.
          BirdLog.info(LogCategory.GLASSES) {
            "$reading — the glasses had none to give; handing back to the phone"
          }
          wantsPreferred.value = false
        }
      }
    }
  }
}

/**
 * Which way the watcher is facing, from the glasses when the session has them and the phone when it
 * does not.
 *
 * The bearing worth having is the one the camera that took the photograph was pointing along, and
 * on a glasses capture that camera is on the wearer's face — so the glasses win whenever they are
 * live, and the phone in the hand is what the session falls back to.
 */
class FailoverHeadingProvider(
    preferred: HeadingProvider,
    fallback: HeadingProvider,
) : HeadingProvider {

  private val readings = FailoverReadings(
      reading = "bearing",
      preferred = { preferred.headingStream() },
      fallback = { fallback.headingStream() },
  )

  /** Ask for the glasses' compass, or hand the session back to the phone's. */
  fun usePreferred(wanted: Boolean) = readings.usePreferred(wanted)

  override fun headingStream(): Flow<Double> = readings.stream()
}

/**
 * How high the watcher is aiming, from the glasses when the session has them and the phone when it
 * does not.
 *
 * The strongest case of the two for preferring the glasses: a phone reports where the *phone* is
 * pointing, so a watcher looking up into the canopy with their hand at their side is a watcher the
 * phone reads as staring at the grass. A head is the thing actually aimed at the bird.
 */
class FailoverGazeProvider(
    preferred: GazeProvider,
    fallback: GazeProvider,
) : GazeProvider {

  private val readings = FailoverReadings(
      reading = "elevation",
      preferred = { preferred.gazeStream() },
      fallback = { fallback.gazeStream() },
  )

  /** Ask for the glasses' attitude, or hand the session back to the phone's. */
  fun usePreferred(wanted: Boolean) = readings.usePreferred(wanted)

  override fun gazeStream(): Flow<Double> = readings.stream()
}
