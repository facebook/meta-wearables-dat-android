/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.domain.DemoAmbientCall
import com.meta.pixelandtexel.birdspotter.domain.DemoPreset
import com.meta.pixelandtexel.birdspotter.domain.DemoResult
import com.meta.pixelandtexel.birdspotter.domain.SpokenCertainty
import com.meta.pixelandtexel.birdspotter.domain.heardAloud
import com.meta.pixelandtexel.birdspotter.features.identify.sessionStamp
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButton
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButtonTone
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.components.Chip
import com.meta.pixelandtexel.birdspotter.ui.components.ChipTone
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import kotlin.math.roundToInt

/**
 * What a row's [DemoResult] reads as in a card's summary.
 *
 * Species print as their slugs (`american-robin`) rather than resolved names: this is a developer
 * panel, and the slug is the value actually stored — seeing it is how you catch one the catalog
 * cannot resolve.
 */
fun resultLabel(result: DemoResult): String =
    when (result) {
      is DemoResult.Species -> result.speciesId
      is DemoResult.Ambiguous -> "ambiguous · ${result.candidateIds.joinToString(" or ")}"
      DemoResult.NoIdentification -> "no identification"
    }

/** The confidence a result carries, as a percentage chip, or null where it carries none. */
fun confidenceLabel(result: DemoResult): String? =
    when (result) {
      is DemoResult.Species -> "${(result.confidence * 100).roundToInt()}%"
      else -> null
    }

/**
 * How an ambient row will open when it is said out loud, or null where the row says nothing.
 *
 * **The opening rather than the word "speaks".** Every heard bird speaks, so a chip saying so would
 * be a column of identical labels; what actually differs down the list is whether the app sounds
 * sure, and that is decided by a slider two screens away. Printing the first three words puts the
 * difference where it can be scanned — and the row's own editor prints the whole sentence. See
 * [heardAloud].
 *
 * A row with a line of its own is read the same way, off its own words: what the chip promises is
 * the first thing the wearer hears, and a row that opens on somebody's own sentence is exactly the
 * one worth spotting from the list.
 */
fun spokenOpeningLabel(call: DemoAmbientCall): String? {
  val species = call.result as? DemoResult.Species ?: return null
  val line =
      call.spokenLine ?: return "${SpokenCertainty.of(species.confidence.toFloat()).opening}…"
  return "${opening(line)}…"
}

/**
 * The first few words of a line, for a chip that has room for a few words.
 *
 * Three, because that is the length of the composed openings this stands beside — a chip that grew
 * with the sentence would make one row of the list taller than the rest of it.
 */
private fun opening(line: String): String = line.split(" ").take(3).joinToString(" ")

/** `2200` ms as the page prints a duration: `2.2s`. */
fun secondsLabel(millis: Int): String {
  val seconds = millis / 1000.0
  return if (seconds == seconds.toInt().toDouble()) "${seconds.toInt()}s" else "${seconds}s"
}

/**
 * One preset's page: a card per section, each row summarised in chips, and every row a way into its
 * own editor.
 *
 * The cards are the shape of the model — STT input, Photo, Ambient input — so the page reads as the
 * three lists the doc describes rather than as a form. What a row *is* stays in chips (`green-jay`,
 * `92%`, `2.2s`) so a run of rows can be scanned down a column without reading sentences.
 */
@Composable
fun DemoDirectorPresetScreen(
    presetId: String,
    onBack: () -> Unit,
    onOpenQuestion: (String?) -> Unit,
    onOpenPhoto: (String?) -> Unit,
    onOpenAmbient: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: DemoDirectorViewModel = viewModel(
      factory = DemoDirectorViewModel.factory(application.container.demoSettingsStore),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val preset = uiState.preset(presetId)

  LifecycleResumeEffect(presetId) {
    viewModel.refresh()
    onPauseOrDispose {}
  }

  // Deleted out from under us (or a stale address): there is nothing to show, so leave.
  if (preset == null) {
    LaunchedEffect(presetId) { onBack() }
    return
  }

  DemoDirectorPresetScreen(
      preset = preset,
      isArmed = uiState.armedId == presetId,
      onBack = onBack,
      onRename = { viewModel.rename(presetId, it) },
      onDelete = {
        viewModel.delete(presetId)
        onBack()
      },
      onOpenQuestion = onOpenQuestion,
      onOpenPhoto = onOpenPhoto,
      onOpenAmbient = onOpenAmbient,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoDirectorPresetScreen(
    preset: DemoPreset,
    isArmed: Boolean,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenQuestion: (String?) -> Unit,
    onOpenPhoto: (String?) -> Unit,
    onOpenAmbient: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type
  var isRenaming by remember { mutableStateOf(false) }
  var isConfirmingDelete by remember { mutableStateOf(false) }

  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text(preset.name) },
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
      if (isArmed) {
        item {
          Text(
              text = "This preset is what the next session will play.",
              style = type.body,
              color = colors.textSecondary,
          )
        }
      }

      // ── STT input ──────────────────────────────────────────────────
      item {
        SectionCard(
            title = "STT input",
            heading = "Question From The User",
            subtitle = "matched on your words",
            onAdd = { onOpenQuestion(null) },
            isEmpty = preset.questions.isEmpty(),
            emptyLabel = "No questions — spoken asks go unanswered",
        ) {
          preset.questions.forEachIndexed { index, question ->
            if (index > 0) HairlineRule()
            RowSummary(
                headline = question.answer,
                chips =
                    listOfNotNull(
                        question.speciesId?.let { it to ChipTone.ANSWER },
                        "${question.prompts.size} question${if (question.prompts.size == 1) "" else "s"}" to
                            ChipTone.INPUT,
                        secondsLabel(question.delayMillis) to ChipTone.NEUTRAL,
                    ),
                onClick = { onOpenQuestion(question.id) },
            )
          }
        }
      }

      // ── Photo ──────────────────────────────────────────────────────
      item {
        SectionCard(
            title = "Photo",
            subtitle = "ordered by capture",
            onAdd = { onOpenPhoto(null) },
            isEmpty = preset.photoResponses.isEmpty(),
            emptyLabel = "No rows yet",
            // Not a row anyone can open: what happens past the end is fixed, and
            // saying so here is the whole of what there is to know about it.
            footnote =
                "Once the list runs out, every further photo comes back " +
                    "with no identification — however many are taken.",
        ) {
          preset.photoResponses.forEachIndexed { index, response ->
            if (index > 0) HairlineRule()
            RowSummary(
                headline = "${index + 1} · ${response.caption}",
                chips =
                    listOfNotNull(
                        resultLabel(response.result) to ChipTone.ANSWER,
                        confidenceLabel(response.result)?.let { it to ChipTone.ANSWER },
                        response.spokenLine?.let { "speaks" to ChipTone.INPUT },
                        secondsLabel(response.delayMillis) to ChipTone.NEUTRAL,
                    ),
                onClick = { onOpenPhoto(response.id) },
            )
          }
        }
      }

      // ── Ambient input ──────────────────────────────────────────────
      item {
        val totals = ambientRunningTotals(preset.ambientCalls)
        SectionCard(
            title = "Ambient input",
            subtitle = "fired on a clock",
            onAdd = { onOpenAmbient(null) },
            isEmpty = preset.ambientCalls.isEmpty(),
            emptyLabel = "No calls — the session listens and stays quiet",
        ) {
          preset.ambientCalls.forEachIndexed { index, call ->
            if (index > 0) HairlineRule()
            RowSummary(
                headline = resultLabel(call.result),
                chips =
                    listOfNotNull(
                        confidenceLabel(call.result)?.let { it to ChipTone.ANSWER },
                        spokenOpeningLabel(call)?.let { it to ChipTone.INPUT },
                        "+${secondsLabel(call.afterMillis)}" to ChipTone.INPUT,
                        sessionStamp(totals[index] / 1000.0) to ChipTone.NEUTRAL,
                    ),
                onClick = { onOpenAmbient(call.id) },
            )
          }
        }
      }

      // The preset itself, rather than its rows. Ruled off so the foot of the page is
      // plainly about this preset and not a fourth section — the same reason the
      // Director's own escape hatch is ruled off from the list above it.
      item {
        Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
          HairlineRule()
          ActionButton(
              title = "Rename",
              onClick = { isRenaming = true },
              tone = ActionButtonTone.SECONDARY,
          )
          ActionButton(
              title = "Delete preset",
              onClick = { isConfirmingDelete = true },
              tone = ActionButtonTone.DESTRUCTIVE,
          )
        }
      }
    }
  }

  if (isRenaming) {
    RenameDialog(
        currentName = preset.name,
        onRename = {
          onRename(it)
          isRenaming = false
        },
        onDismiss = { isRenaming = false },
    )
  }

  if (isConfirmingDelete) {
    AlertDialog(
        onDismissRequest = { isConfirmingDelete = false },
        title = { Text("Delete “${preset.name}”?") },
        text = {
          Text(
              "If it is playing, nothing will be — the app stops identifying until " +
                  "another preset is chosen.",
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                isConfirmingDelete = false
                onDelete()
              },
          ) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { isConfirmingDelete = false }) { Text("Cancel") }
        },
    )
  }
}

/**
 * One section's card: its plate, its rows, and the one way to add another.
 *
 * [title] is the plate — what the section *is* in the model's words. [heading] is what the rows
 * under it *are* in the operator's words, set larger below the plate, so a section can name its own
 * contents ("STT input" → "Question From The User") without the plate losing the model's
 * vocabulary.
 *
 * [footnote] closes the card: the behaviour the section has that no row of it can express.
 */
@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    onAdd: () -> Unit,
    isEmpty: Boolean,
    heading: String? = null,
    emptyLabel: String = "",
    footnote: String? = null,
    content: @Composable () -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  CardSurface {
    Column(
        modifier = Modifier.padding(space.cardInset),
        verticalArrangement = Arrangement.spacedBy(space.related),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(space.tight)) {
        PlateLabel(text = title, color = colors.gilt)
        if (heading != null) {
          Text(text = heading, style = type.title, color = colors.textPrimary)
        }
        Text(text = subtitle, style = type.label, color = colors.textSecondary)
      }

      if (isEmpty) {
        Text(text = emptyLabel, style = type.body, color = colors.textSecondary)
      } else {
        content()
      }

      Text(
          text = "＋ Add",
          style = type.label,
          color = colors.gilt,
          modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd),
      )

      if (footnote != null) {
        HairlineRule()
        Text(text = footnote, style = type.label, color = colors.textSecondary)
      }
    }
  }
}

/** One row inside a card: what it says, what it carries, and the way into its editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowSummary(
    headline: String,
    chips: List<Pair<String, ChipTone>>,
    onClick: () -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Row(
      modifier =
          Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = space.snug),
      horizontalArrangement = Arrangement.spacedBy(space.related),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(space.snug),
    ) {
      Text(text = headline, style = type.body, color = colors.textPrimary)
      // Flowing, not a Row: four chips on a narrow phone would otherwise squeeze the
      // last one until its text set one character to a line.
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(space.snug),
          verticalArrangement = Arrangement.spacedBy(space.snug),
      ) {
        chips.forEach { (label, tone) -> Chip(text = label, tone = tone) }
      }
    }
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.disclosure),
        contentDescription = null,
        tint = colors.textSecondary,
    )
  }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(currentName) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Rename preset") },
      text = {
        OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
      },
      confirmButton = {
        TextButton(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) {
          Text("Rename")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Preview(showBackground = true)
@Composable
fun DemoDirectorPresetScreenPreview() {
  BirdSpotterTheme {
    DemoDirectorPresetScreen(
        preset = PreviewPreset,
        isArmed = true,
        onBack = {},
        onRename = {},
        onDelete = {},
        onOpenQuestion = {},
        onOpenPhoto = {},
        onOpenAmbient = {},
    )
  }
}
