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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.domain.DemoAmbientCall
import com.meta.pixelandtexel.birdspotter.domain.DemoPhotoResponse
import com.meta.pixelandtexel.birdspotter.domain.DemoPreset
import com.meta.pixelandtexel.birdspotter.domain.DemoQuestion
import com.meta.pixelandtexel.birdspotter.domain.DemoResult
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButton
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButtonTone
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.components.Chip
import com.meta.pixelandtexel.birdspotter.ui.components.ChipTone
import com.meta.pixelandtexel.birdspotter.ui.components.DisclosureRow
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/** What a picker row says a preset holds, at a glance: `1 question · 1 photo · 1 call`. */
fun presetSummary(preset: DemoPreset): String {
  fun count(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"
  return listOf(
      count(preset.questions.size, "question"),
      count(preset.photoResponses.size, "photo"),
      count(preset.ambientCalls.size, "call"),
  )
      .joinToString(" · ")
}

/**
 * Settings → Demo Director: which preset is playing, and the presets to edit.
 *
 * **Arming is a dropdown, not a row's tap.** What plays is one decision — the presenter's
 * ninety-seconds-before-stage decision — and it belongs in one control at the top rather than
 * distributed across a list where selecting and opening compete for the same tap. The rows below
 * are for editing; opening one never changes what plays.
 *
 * `None` is an option in that dropdown, and deleting the armed preset falls back to it: the app
 * then never identifies, which is a deliberate state rather than a broken one.
 */
@Composable
fun DemoDirectorScreen(
    onBack: () -> Unit,
    onOpenPreset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: DemoDirectorViewModel = viewModel(
      factory = DemoDirectorViewModel.factory(application.container.demoSettingsStore),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Editing happens on pages pushed from here, through their own state holders; re-read
  // on the way back so the list never shows the snapshot from before.
  LifecycleResumeEffect(Unit) {
    viewModel.refresh()
    onPauseOrDispose {}
  }

  DemoDirectorScreen(
      uiState = uiState,
      onBack = onBack,
      onOpenPreset = onOpenPreset,
      onArm = viewModel::arm,
      onNewPreset = { onOpenPreset(viewModel.newPreset()) },
      onResetToShipped = viewModel::resetToShipped,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoDirectorScreen(
    uiState: DemoDirectorUiState,
    onBack: () -> Unit,
    onOpenPreset: (String) -> Unit,
    onArm: (String?) -> Unit,
    onNewPreset: () -> Unit,
    onResetToShipped: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Demo Director") },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(painter = glyph(BirdSpotterTheme.glyphs.back), contentDescription = "Back")
              }
            },
        )
      },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { innerPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(horizontal = space.gutter, vertical = space.separate),
        verticalArrangement = Arrangement.spacedBy(space.separate),
    ) {
      item {
        // The honesty affordance: this panel is for the team, not hidden from it.
        Text(
            text =
                "Everything a session identifies is scripted here. " +
                    "One preset plays at a time; None means the app never identifies.",
            style = type.body,
            color = colors.textSecondary,
        )
      }

      item {
        ArmedPicker(
            uiState = uiState,
            onArm = onArm,
        )
      }

      item { PlateLabel(text = "Presets", color = colors.gilt) }

      items(uiState.presets, key = { it.id }) { preset ->
        PresetRow(
            preset = preset,
            isArmed = uiState.armedId == preset.id,
            onOpen = { onOpenPreset(preset.id) },
        )
      }

      if (uiState.presets.isEmpty()) {
        item {
          Text(
              text = "No presets. Add one, or put the shipped script back.",
              style = type.body,
              color = colors.textSecondary,
          )
        }
      }

      item { ActionButton(title = "New preset", onClick = onNewPreset) }

      // Only once the shipped script is gone — edited or deleted. There is nothing
      // to reset while it is still sitting in the list untouched.
      //
      // Ruled off, and its own caption first: this is the page's escape hatch rather
      // than another item in the list above it, and running it straight on from
      // "New preset" in one rhythm of gilt lines is what made the foot of this screen
      // unreadable.
      if (uiState.canResetToShipped) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
            HairlineRule()
            Text(
                text = "Puts the shipped script back. It does not change what is playing.",
                style = type.label,
                color = colors.textSecondary,
            )
            ActionButton(
                title = "Reset to starter",
                onClick = onResetToShipped,
                tone = ActionButtonTone.SECONDARY,
            )
          }
        }
      }
    }
  }
}

/**
 * The one control that decides what plays: a dropdown listing every preset plus None.
 *
 * A menu rather than a row of choices because the answer is single-valued and the list grows — and
 * because the closed state is itself the readout, so a presenter can see what is armed without
 * opening anything.
 */
@Composable
private fun ArmedPicker(
    uiState: DemoDirectorUiState,
    onArm: (String?) -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type
  var isExpanded by remember { mutableStateOf(false) }

  CardSurface(onClick = { isExpanded = true }) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(space.cardInset),
        verticalArrangement = Arrangement.spacedBy(space.tight),
    ) {
      PlateLabel(text = "Playing", color = colors.gilt)
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(space.related),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = uiState.armedName,
            style = type.title,
            color = if (uiState.armedId == null) colors.textSecondary else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.disclosure),
            contentDescription = "Choose what plays",
            tint = colors.textSecondary,
        )
      }

      DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
        uiState.presets.forEach { preset ->
          DropdownMenuItem(
              text = { Text(preset.name) },
              onClick = {
                onArm(preset.id)
                isExpanded = false
              },
              trailingIcon =
                  if (uiState.armedId == preset.id) {
                    {
                      Icon(
                          painter = glyph(BirdSpotterTheme.glyphs.selected),
                          contentDescription = null,
                      )
                    }
                  } else {
                    null
                  },
          )
        }
        DropdownMenuItem(
            text = { Text("None — never identify") },
            onClick = {
              onArm(null)
              isExpanded = false
            },
            trailingIcon =
                if (uiState.armedId == null) {
                  {
                    Icon(
                        painter = glyph(BirdSpotterTheme.glyphs.selected),
                        contentDescription = null,
                    )
                  }
                } else {
                  null
                },
        )
      }
    }
  }
}

/** One preset in the list: what it holds, and the way into editing it. */
@Composable
private fun PresetRow(
    preset: DemoPreset,
    isArmed: Boolean,
    onOpen: () -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  DisclosureRow(onClick = onOpen, contentDescription = "Edit ${preset.name}") {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space.snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = preset.name,
          style = type.headline,
          color = colors.textPrimary,
          // The name yields, not the chip: a long name should end in an ellipsis
          // rather than squeeze "Playing" into a column of letters. Compose has to be
          // told which child yields.
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
      )
      if (isArmed) Chip(text = "Playing", tone = ChipTone.ANSWER)
    }
    Text(text = presetSummary(preset), style = type.label, color = colors.textSecondary)
  }
}

@Preview(showBackground = true)
@Composable
fun DemoDirectorScreenPreview() {
  BirdSpotterTheme {
    DemoDirectorScreen(
        uiState =
            DemoDirectorUiState(
                presets =
                    listOf(
                        PreviewPreset,
                        PreviewPreset.copy(id = "booth", name = "Booth — quiet room"),
                    ),
                armedId = PreviewPreset.id,
                canResetToShipped = true,
            ),
        onBack = {},
        onOpenPreset = {},
        onArm = {},
        onNewPreset = {},
        onResetToShipped = {},
    )
  }
}

/** A small preset for previews — shared with the preset page's preview. */
internal val PreviewPreset = DemoPreset(
    id = "starter-full-flow",
    name = "Full Flow",
    questions =
        listOf(
            DemoQuestion(
                id = "green-yellow-belly",
                prompts = listOf("it's green with a yellow belly", "green with a yellow belly"),
                answer = "That's likely a Green Jay.",
                speciesId = "green-jay",
                delayMillis = 1_500,
            ),
        ),
    photoResponses =
        listOf(
            DemoPhotoResponse(
                id = "photo-1",
                result = DemoResult.Species("northern-cardinal", 0.92),
                caption = "Northern Cardinal",
                spokenLine = "Northern Cardinal, 92 percent.",
                delayMillis = 2_200,
            ),
        ),
    ambientCalls =
        listOf(
            DemoAmbientCall(
                id = "robin",
                afterMillis = 8_000,
                result = DemoResult.Species("american-robin", 0.87),
            ),
        ),
    unmatchedQuestion = "Sorry — didn't catch that.",
)
