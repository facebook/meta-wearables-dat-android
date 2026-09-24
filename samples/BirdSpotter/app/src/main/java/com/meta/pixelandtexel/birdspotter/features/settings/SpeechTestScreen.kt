/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechState
import com.meta.pixelandtexel.birdspotter.domain.Transcription
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButton
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButtonTone
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import kotlin.math.roundToInt

/**
 * Test ASR — a session opened to hear the wearer and print what came back, and nothing else.
 *
 * Pushed from the glasses settings screen, where the rest of the hardware readings live. See
 * [SpeechTestViewModel] for what the three readings on it are actually diagnosing.
 */
@Composable
fun SpeechTestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val container = (LocalContext.current.applicationContext as BirdSpotterApplication).container
  val viewModel: SpeechTestViewModel = viewModel(
      factory =
          SpeechTestViewModel.factory(
              container.glassesSessionRepository,
              container.glassesSpeechRepository,
          ),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  SpeechTestContent(
      uiState = uiState,
      onBack = onBack,
      onStart = viewModel::start,
      onStop = viewModel::stop,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeechTestContent(
    uiState: SpeechTestUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Test ASR") },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(
                    painter = glyph(BirdSpotterTheme.glyphs.back),
                    contentDescription = "Back",
                )
              }
            },
        )
      },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter, vertical = space.separate),
        verticalArrangement = Arrangement.spacedBy(space.section),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
        Text(
            text = "Session",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text =
                "Recognition runs on the glasses — no audio reaches the phone, and " +
                    "the microphone here stays free.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReadingRow(label = "Glasses", value = sessionValue(uiState))
        ReadingRow(
            label = "Recogniser",
            value = speechValue(uiState.speechState),
            support = speechSupport(uiState.speechState),
        )
        uiState.failure?.let {
          // In the page's own ink rather than a red: the palette spends its one red on
          // controls that end something, and a line explaining why a test stopped is a
          // statement, not a control.
          Text(
              text = it,
              style = MaterialTheme.typography.bodyMedium,
          )
        }
        if (uiState.isRunning) {
          ActionButton(
              title = "Stop",
              onClick = onStop,
              tone = ActionButtonTone.DESTRUCTIVE,
          )
        } else {
          ActionButton(title = "Start listening", onClick = onStart)
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
        Text(
            text = "Heard",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        // The utterance in progress, italic and above the finished ones — the line that
        // says the glasses are hearing *you* rather than merely listening.
        uiState.partial?.let {
          Text(
              text = it,
              style = MaterialTheme.typography.bodyLarge,
              fontStyle = FontStyle.Italic,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (uiState.heard.isEmpty() && uiState.partial == null) {
          Text(
              text = emptyLine(uiState),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        uiState.heard.forEach { heard ->
          HairlineRule()
          HeardRow(heard)
        }
      }
    }
  }
}

/**
 * What the session is doing, in the words the pill on the realtime screen uses — one vocabulary for
 * the link across the app, so a reading here is comparable with a reading there.
 */
private fun sessionValue(uiState: SpeechTestUiState): String =
    when (uiState.sessionState) {
      GlassesSessionState.STARTING -> "Connecting"
      GlassesSessionState.STARTED -> "Connected"
      GlassesSessionState.PAUSED -> "Paused"
      GlassesSessionState.STOPPING -> "Stopping"
      GlassesSessionState.STOPPED -> "Stopped"
      null -> if (uiState.isRunning) "Connecting" else "Not started"
    }

private fun speechValue(state: GlassesSpeechState): String =
    when (state) {
      GlassesSpeechState.IDLE -> "Not started"
      GlassesSpeechState.STARTING -> "Starting"
      GlassesSpeechState.LISTENING -> "Listening"
      GlassesSpeechState.STOPPED -> "Stopped"
      GlassesSpeechState.UNAVAILABLE -> "Unavailable"
    }

/**
 * Only the readings that need explaining explain themselves — and the one that matters is
 * `UNAVAILABLE`, which is otherwise indistinguishable from a quiet room.
 */
private fun speechSupport(state: GlassesSpeechState): String? =
    when (state) {
      GlassesSpeechState.UNAVAILABLE ->
          "This pair has no on-device recognition, so nothing here will ever be heard"
      GlassesSpeechState.STOPPED ->
          "The recogniser was listening and went quiet — usually the link, not the speech"
      GlassesSpeechState.IDLE,
      GlassesSpeechState.STARTING,
      GlassesSpeechState.LISTENING -> null
    }

/** What an empty list means, which depends entirely on what the recogniser is doing. */
private fun emptyLine(uiState: SpeechTestUiState): String =
    when {
      uiState.speechState == GlassesSpeechState.LISTENING -> "Listening — say something."
      uiState.isRunning -> "Waiting for the glasses."
      else -> "Nothing yet."
    }

/** One finished utterance: what was heard, and how sure the glasses were about it. */
@Composable
private fun HeardRow(heard: Transcription) {
  val space = BirdSpotterTheme.space
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space.related),
      verticalAlignment = Alignment.Top,
  ) {
    Text(
        text = heard.text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
    )
    Text(
        // A confidence the recogniser would not give is a dash, never a number — see
        // [Transcription].
        text = heard.confidence?.let { "${(it * 100).roundToInt()}%" } ?: "—",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ReadingRow(label: String, value: String, support: String? = null) {
  val space = BirdSpotterTheme.space
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space.related),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = MaterialTheme.typography.titleMedium)
      support?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** A run in progress: the glasses listening, one sentence landed and another mid-air. */
@Preview(showBackground = true)
@Composable
fun SpeechTestScreenPreview() {
  BirdSpotterTheme {
    SpeechTestContent(
        uiState =
            SpeechTestUiState(
                isRunning = true,
                sessionState = GlassesSessionState.STARTED,
                speechState = GlassesSpeechState.LISTENING,
                partial = "and it has a yellow",
                heard =
                    listOf(
                        Transcription(
                            "It's green with a yellow belly",
                            isFinal = true,
                            confidence = 0.91f,
                        ),
                        Transcription("What bird is that", isFinal = true, confidence = null),
                    ),
            ),
        onBack = {},
        onStart = {},
        onStop = {},
    )
  }
}

/** The reading the screen exists to produce: a pair that cannot do this at all. */
@Preview(showBackground = true)
@Composable
fun SpeechTestScreenUnavailablePreview() {
  BirdSpotterTheme {
    SpeechTestContent(
        uiState =
            SpeechTestUiState(
                isRunning = true,
                sessionState = GlassesSessionState.STARTED,
                speechState = GlassesSpeechState.UNAVAILABLE,
            ),
        onBack = {},
        onStart = {},
        onStop = {},
    )
  }
}
