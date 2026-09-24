/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import com.meta.pixelandtexel.birdspotter.domain.Transcription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ASR test screen's one piece of policy: **what an arriving transcript does to what is on
 * screen**.
 *
 * The recogniser delivers the same utterance several times as it develops and once when it is over,
 * so a screen that treated every arrival alike would print a sentence a word at a time down the
 * page — and one that kept only finals would show nothing at all while somebody was speaking, which
 * is the reading this screen exists to give.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class SpeechTestViewModelTest {

  @Test
  fun aPartialReplacesTheUtteranceInProgress() {
    // The sentence developing. Three arrivals, one line — anything else is a page of
    // prefixes.
    val state =
        SpeechTestUiState()
            .hearing(Transcription("its", isFinal = false))
            .hearing(Transcription("its green", isFinal = false))
            .hearing(Transcription("its green with", isFinal = false))

    assertEquals("its green with", state.partial)
    assertEquals(emptyList<Transcription>(), state.heard)
  }

  @Test
  fun aFinalClosesTheUtteranceAndJoinsTheList() {
    // The commit. The line in progress has to go with it, or the screen shows the same
    // sentence twice — once italic and once not.
    val state =
        SpeechTestUiState()
            .hearing(Transcription("its green with a yellow", isFinal = false))
            .hearing(Transcription("It's green with a yellow belly", isFinal = true))

    assertNull(state.partial)
    assertEquals(listOf("It's green with a yellow belly"), state.heard.map { it.text })
  }

  @Test
  fun theNewestUtteranceIsFirst() {
    // A diagnostic is read from the top: the thing just said is the thing being checked.
    val state =
        SpeechTestUiState()
            .hearing(Transcription("first", isFinal = true))
            .hearing(Transcription("second", isFinal = true))

    assertEquals(listOf("second", "first"), state.heard.map { it.text })
  }

  @Test
  fun theListStopsGrowingAtItsLimit() {
    // A test left running is a test nobody is watching, and an unbounded list is the one
    // way a diagnostic screen can itself become the fault.
    val state =
        (1..80).fold(SpeechTestUiState()) { state, index ->
          state.hearing(Transcription("utterance $index", isFinal = true))
        }

    assertEquals(50, state.heard.size)
    assertEquals("utterance 80", state.heard.first().text)
    assertEquals("utterance 31", state.heard.last().text)
  }

  @Test
  fun aConfidenceTheRecogniserWithheldStaysWithheld() {
    // Carried rather than defaulted. A number the glasses would not give must not turn into
    // a number on screen, which is the whole reason the field is optional.
    val state =
        SpeechTestUiState()
            .hearing(Transcription("what bird is that", isFinal = true, confidence = null))

    assertNull(state.heard.first().confidence)
  }
}
