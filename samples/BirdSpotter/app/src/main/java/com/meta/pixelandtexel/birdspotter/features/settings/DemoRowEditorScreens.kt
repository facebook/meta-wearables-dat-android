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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.domain.DemoAmbientCall
import com.meta.pixelandtexel.birdspotter.domain.DemoPhotoResponse
import com.meta.pixelandtexel.birdspotter.domain.DemoQuestion
import com.meta.pixelandtexel.birdspotter.domain.DemoResult
import com.meta.pixelandtexel.birdspotter.domain.SureAloudConfidence
import com.meta.pixelandtexel.birdspotter.domain.heardAloud
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButton
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButtonTone
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.components.Chip
import com.meta.pixelandtexel.birdspotter.ui.components.ChipTone
import com.meta.pixelandtexel.birdspotter.ui.components.NotesField
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.components.SearchField
import com.meta.pixelandtexel.birdspotter.ui.components.SpeciesRow
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import kotlin.math.roundToInt

/**
 * Which kind of answer a result row lands. The editor picks the *kind* first, because the fields
 * that follow depend on it — a species needs a bird and a confidence, an ambiguous pair needs two
 * birds, and no identification needs neither.
 *
 * There is no "low confidence" kind: that is a species with the slider down. Nor a transport
 * failure, which is not something a script says.
 */
enum class DemoResultKind(val label: String) {
  SPECIES("Species"),
  AMBIGUOUS("Ambiguous"),
  NO_IDENTIFICATION("No identification"),
}

/**
 * The kinds a photo row may answer with: a bird, or nothing.
 *
 * A photo is one capture and one answer. "Green Jay or Blue Jay?" is an exchange, and an exchange
 * is settled by the watcher saying which — so ambiguity belongs to the lanes a spoken answer can
 * reach, not to a shutter press.
 */
val PHOTO_RESULT_KINDS: List<DemoResultKind> =
    listOf(DemoResultKind.SPECIES, DemoResultKind.NO_IDENTIFICATION)

/** The kind a stored result is. */
fun kindOf(result: DemoResult): DemoResultKind =
    when (result) {
      is DemoResult.Species -> DemoResultKind.SPECIES
      is DemoResult.Ambiguous -> DemoResultKind.AMBIGUOUS
      DemoResult.NoIdentification -> DemoResultKind.NO_IDENTIFICATION
    }

/**
 * A result of [kind], keeping whatever the old one can still carry.
 *
 * The bird survives a switch to and from "Ambiguous", so changing your mind about the shape of an
 * answer does not make the operator pick the bird again.
 */
fun resultOf(kind: DemoResultKind, previous: DemoResult): DemoResult {
  val speciesId =
      when (previous) {
        is DemoResult.Species -> previous.speciesId
        is DemoResult.Ambiguous -> previous.candidateIds.firstOrNull().orEmpty()
        else -> ""
      }
  val confidence =
      when (previous) {
        is DemoResult.Species -> previous.confidence
        else -> 0.87
      }
  return when (kind) {
    DemoResultKind.SPECIES -> DemoResult.Species(speciesId, confidence)
    DemoResultKind.AMBIGUOUS ->
        DemoResult.Ambiguous(
            (previous as? DemoResult.Ambiguous)?.candidateIds
                ?: listOfNotNull(speciesId.takeIf { it.isNotEmpty() }),
        )
    DemoResultKind.NO_IDENTIFICATION -> DemoResult.NoIdentification
  }
}

// ─── STT input ─────────────────────────────────────────────────────────────

/**
 * The editor for one STT-input row: the questions that match it, the answer, an optional bird, and
 * the time to compose.
 */
@Composable
fun DemoQuestionEditorScreen(
    presetId: String,
    questionId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: DemoDirectorViewModel = viewModel(
      factory = DemoDirectorViewModel.factory(application.container.demoSettingsStore),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val existing = questionId?.let { id ->
    uiState.preset(presetId)?.questions?.firstOrNull { it.id == id }
  }

  var draft by
      remember(questionId) {
        mutableStateOf(
            existing
                ?: DemoQuestion(
                    id = DemoDirectorViewModel.newId(),
                    prompts = listOf(""),
                    answer = "",
                    speciesId = null,
                    delayMillis = 1_500,
                ),
        )
      }

  EditorScaffold(
      title = if (existing == null) "New question" else "Question",
      canSave = draft.answer.isNotBlank() && draft.prompts.any { it.isNotBlank() },
      onBack = onBack,
      onSave = {
        viewModel.saveQuestion(
            presetId,
            draft.copy(prompts = draft.prompts.map { it.trim() }.filter { it.isNotBlank() }),
        )
        onBack()
      },
      onDelete =
          existing?.let {
            {
              viewModel.deleteQuestion(presetId, it.id)
              onBack()
            }
          },
      modifier = modifier,
  ) {
    // The questions come first: the row is authored the way it plays — the watcher asks,
    // then the app answers.
    item {
      EditorCard(
          title = "Questions",
          subtitle = "any of these, heard in a sentence, fires this row",
      ) {
        draft.prompts.forEachIndexed { index, prompt ->
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.snug),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            NotesField(
                text = prompt,
                onTextChange = { new ->
                  draft =
                      draft.copy(
                          prompts = draft.prompts.toMutableList().also { it[index] = new },
                      )
                },
                placeholder = "green with a yellow belly",
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 1,
            )
            IconButton(
                onClick = {
                  draft =
                      draft.copy(
                          prompts = draft.prompts.filterIndexed { i, _ -> i != index },
                      )
                },
                enabled = draft.prompts.size > 1,
            ) {
              Icon(
                  painter = glyph(BirdSpotterTheme.glyphs.close),
                  contentDescription = "Remove question",
              )
            }
          }
        }
        Text(
            text = "＋ Add question",
            style = BirdSpotterTheme.type.label,
            color = BirdSpotterTheme.colors.gilt,
            modifier =
                Modifier.fillMaxWidth().clickable {
                  draft = draft.copy(prompts = draft.prompts + "")
                },
        )
      }
    }

    item {
      EditorCard(title = "Answer", subtitle = "the one line the app says or shows") {
        NotesField(
            text = draft.answer,
            onTextChange = { draft = draft.copy(answer = it) },
            placeholder = "That's likely a Green Jay.",
            minLines = 1,
        )
      }
    }

    item {
      SpeciesField(
          title = "Bird",
          subtitle =
              "optional — surfaced as a card beside the answer, with no " +
                  "confidence, because the watcher supplied it. Leave it empty for a " +
                  "words-only reply",
          speciesId = draft.speciesId,
          onPick = { draft = draft.copy(speciesId = it) },
          allowClear = true,
      )
    }

    item {
      MillisField(
          title = "Time to compose",
          millis = draft.delayMillis,
          range = 0f..5_000f,
          onChange = { draft = draft.copy(delayMillis = it) },
          subtitle =
              "how long the app waits before answering — an instant reply " + "reads as canned",
      )
    }
  }
}

// ─── Photo ─────────────────────────────────────────────────────────────────

/**
 * The editor for one photo row.
 *
 * There is no editor for what happens past the end of the list: that is
 * [DemoPhotoResponse.pastTheEnd], fixed for every preset.
 */
@Composable
fun DemoPhotoEditorScreen(
    presetId: String,
    photoId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: DemoDirectorViewModel = viewModel(
      factory = DemoDirectorViewModel.factory(application.container.demoSettingsStore),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val preset = uiState.preset(presetId)
  val existing = photoId?.let { id -> preset?.photoResponses?.firstOrNull { it.id == id } }

  // Which capture this row answers — the whole of what a photo row is, so it is the
  // title. A new row takes the number it will have once it is saved.
  val rows = preset?.photoResponses.orEmpty()
  val photoNumber =
      rows.indexOfFirst { it.id == photoId }.takeIf { it >= 0 }?.plus(1) ?: (rows.size + 1)

  var draft by
      remember(photoId) {
        mutableStateOf(
            existing
                ?: DemoPhotoResponse(
                    id = DemoDirectorViewModel.newId(),
                    result = DemoResult.Species("", 0.87),
                    caption = "",
                    spokenLine = null,
                    delayMillis = 2_200,
                ),
        )
      }

  EditorScaffold(
      title = "Photo $photoNumber",
      canSave = draft.caption.isNotBlank() && draft.result.isComplete,
      onBack = onBack,
      onSave = {
        viewModel.savePhotoResponse(presetId, draft)
        onBack()
      },
      onDelete =
          existing?.let {
            {
              viewModel.deletePhotoResponse(presetId, it.id)
              onBack()
            }
          },
      modifier = modifier,
  ) {
    resultEditor(
        result = draft.result,
        onChange = { draft = draft.copy(result = it) },
        // A photo comes back with a bird or with nothing. "Which of these two?" is an
        // exchange, and an exchange needs a spoken answer to settle it — which is the
        // ambient lane's, not a single capture's.
        kinds = PHOTO_RESULT_KINDS,
    )

    item {
      EditorCard(title = "Caption", subtitle = "what appears on screen") {
        NotesField(
            text = draft.caption,
            onTextChange = { draft = draft.copy(caption = it) },
            placeholder = "Northern Cardinal",
            minLines = 1,
        )
      }
    }

    item {
      EditorCard(
          title = "Spoken line",
          subtitle = "said only when glasses are connected — the phone stays silent",
      ) {
        NotesField(
            text = draft.spokenLine.orEmpty(),
            onTextChange = { draft = draft.copy(spokenLine = it.ifBlank { null }) },
            placeholder = "Northern Cardinal, 92 percent.",
            minLines = 1,
        )
      }
    }

    item {
      MillisField(
          title = "Response delay",
          millis = draft.delayMillis,
          range = 0f..8_000f,
          onChange = { draft = draft.copy(delayMillis = it) },
          // The glasses' audio route has needed about two seconds to settle before they can
          // speak — see the routing note in the design doc.
          footnote =
              if (draft.spokenLine != null && draft.delayMillis < 2_000) {
                "Under 2s the spoken line may arrive late: the glasses' audio route " +
                    "takes about that long to settle."
              } else {
                null
              },
      )
    }
  }
}

// ─── Ambient input ─────────────────────────────────────────────────────────

/** The editor for one ambient-input row: the gap since the row before it, and the bird it lands. */
@Composable
fun DemoAmbientEditorScreen(
    presetId: String,
    callId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: DemoDirectorViewModel = viewModel(
      factory = DemoDirectorViewModel.factory(application.container.demoSettingsStore),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val preset = uiState.preset(presetId)
  val existing = callId?.let { id -> preset?.ambientCalls?.firstOrNull { it.id == id } }

  var draft by
      remember(callId) {
        mutableStateOf(
            existing
                ?: DemoAmbientCall(
                    id = DemoDirectorViewModel.newId(),
                    afterMillis = 8_000,
                    result = DemoResult.Species("", 0.87),
                ),
        )
      }

  // Where this row lands on the session clock, given the rows before it — the same
  // running total the preset page prints, so the editor and the card agree.
  val precedingMillis =
      preset?.ambientCalls.orEmpty().takeWhile { it.id != draft.id }.sumOf { it.afterMillis }

  EditorScaffold(
      title = if (existing == null) "New call" else "Ambient call",
      canSave = draft.result.isComplete,
      onBack = onBack,
      onSave = {
        // A row that names no bird says nothing out loud, so a line left behind by a change
        // of kind would be words that never play.
        val saved = if (draft.result is DemoResult.Species) draft else draft.copy(spokenLine = null)
        viewModel.saveAmbientCall(presetId, saved)
        onBack()
      },
      onDelete =
          existing?.let {
            {
              viewModel.deleteAmbientCall(presetId, it.id)
              onBack()
            }
          },
      modifier = modifier,
  ) {
    item {
      MillisField(
          title = "Gap from the row before",
          millis = draft.afterMillis,
          range = 0f..60_000f,
          onChange = { draft = draft.copy(afterMillis = it) },
          footnote =
              "Lands at " +
                  com.meta.pixelandtexel.birdspotter.features.identify.sessionStamp(
                      (precedingMillis + draft.afterMillis) / 1000.0,
                  ) +
                  " into the session. The first row's gap is measured from the microphone opening.",
      )
    }

    resultEditor(
        result = draft.result,
        onChange = { draft = draft.copy(result = it) },
    )

    (draft.result as? DemoResult.Species)?.let { species ->
      item {
        SpokenLineField(
            species = species,
            line = draft.spokenLine,
            onChange = { draft = draft.copy(spokenLine = it) },
        )
      }
    }
  }
}

// ─── Shared editor pieces ──────────────────────────────────────────────────

/**
 * What this row will say out loud — the composed sentence, and the field that replaces it.
 *
 * **Empty is the sentence, not nothing.** An ambient row composes its line from the bird and the
 * confidence ([heardAloud]), and that composed line is what stands in the field until somebody
 * types over it — so the words are the placeholder rather than a rule described in prose, and they
 * re-read as the slider moves. That is where the two openings become one visible thing: drag past
 * [SureAloudConfidence] and *I think I heard* becomes *I just heard*.
 *
 * **What is typed is said verbatim**, and the footnote keeps the composed line in view beside it,
 * because the reason to write one is usually that this row wants different words from the row above
 * it — which is a comparison.
 *
 * A bird the catalog cannot resolve composes nothing — the name is the catalog's to give, and a
 * sentence with a slug in the middle of it would be a preview of something that never happens. The
 * field still takes a line, because words somebody typed do not need a name resolving.
 */
@Composable
private fun SpokenLineField(
    species: DemoResult.Species,
    line: String?,
    onChange: (String?) -> Unit,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  var commonName by remember(species.speciesId) { mutableStateOf<String?>(null) }

  LaunchedEffect(species.speciesId) {
    commonName =
        species.speciesId
            .takeIf { it.isNotBlank() }
            ?.let { id ->
              runCatching { application.container.birdCatalogRepository.findById(id) }
                  .getOrNull()
                  ?.species
                  ?.commonName
            }
  }

  // The sentence this row makes for itself, or null while there is no name to make it from.
  val composed = commonName?.let { heardAloud(it, species.confidence.toFloat()) }

  EditorCard(
      title = "Spoken line",
      subtitle = "said only when there are glasses to say it into — the phone stays silent",
  ) {
    NotesField(
        text = line.orEmpty(),
        onTextChange = { onChange(it.ifEmpty { null }) },
        placeholder = composed ?: "Choose a bird to hear the line.",
        minLines = 1,
    )
    Text(
        text =
            when {
              line == null -> "Left empty, the line is composed from the bird and the confidence."
              composed == null -> "Said as written."
              else -> "Said instead of “$composed”"
            },
        style = BirdSpotterTheme.type.label,
        color = BirdSpotterTheme.colors.textSecondary,
    )
  }
}

/** True once a result has everything it needs to be saved — a bird, where one is required. */
private val DemoResult.isComplete: Boolean
  get() =
      when (this) {
        is DemoResult.Species -> speciesId.isNotBlank()
        is DemoResult.Ambiguous -> candidateIds.count { it.isNotBlank() } >= 2
        else -> true
      }

/** The kind picker, and whichever fields that kind needs. */
private fun androidx.compose.foundation.lazy.LazyListScope.resultEditor(
    result: DemoResult,
    onChange: (DemoResult) -> Unit,
    kinds: List<DemoResultKind> = DemoResultKind.entries,
) {
  item {
    EditorCard(title = "Result", subtitle = "what the app answers") {
      FlowingChips(kinds = kinds, current = kindOf(result)) {
        onChange(resultOf(it, result))
      }
    }
  }

  when (result) {
    is DemoResult.Species -> {
      item {
        SpeciesField(
            title = "Bird",
            subtitle = "resolved against the catalog when it plays",
            speciesId = result.speciesId.ifBlank { null },
            onPick = { onChange(result.copy(speciesId = it.orEmpty())) },
            allowClear = false,
        )
      }
      item {
        ConfidenceField(result.confidence) { onChange(result.copy(confidence = it)) }
      }
    }

    is DemoResult.Ambiguous -> {
      item {
        SpeciesField(
            title = "First candidate",
            subtitle = "\"Green Jay or Blue Jay?\" — settled by an STT answer",
            speciesId = result.candidateIds.getOrNull(0),
            onPick = { picked ->
              val ids = result.candidateIds.toMutableList()
              while (ids.size < 2) ids.add("")
              ids[0] = picked.orEmpty()
              onChange(DemoResult.Ambiguous(ids))
            },
            allowClear = false,
        )
      }
      item {
        SpeciesField(
            title = "Second candidate",
            subtitle = "the one the watcher rules out",
            speciesId = result.candidateIds.getOrNull(1),
            onPick = { picked ->
              val ids = result.candidateIds.toMutableList()
              while (ids.size < 2) ids.add("")
              ids[1] = picked.orEmpty()
              onChange(DemoResult.Ambiguous(ids))
            },
            allowClear = false,
        )
      }
    }

    DemoResult.NoIdentification -> Unit
  }
}

/**
 * The kind picker's chips, wrapping onto as many lines as they need.
 *
 * A single row squeezes them: "No identification" is three times the width of "Species", and on a
 * narrow phone the three together do not fit — so they were being crushed against each other rather
 * than falling onto a second line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowingChips(
    kinds: List<DemoResultKind>,
    current: DemoResultKind,
    onPick: (DemoResultKind) -> Unit,
) {
  val space = BirdSpotterTheme.space
  FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space.snug),
      verticalArrangement = Arrangement.spacedBy(space.snug),
  ) {
    kinds.forEach { kind -> KindChip(kind, current, onPick) }
  }
}

@Composable
private fun KindChip(
    kind: DemoResultKind,
    current: DemoResultKind,
    onPick: (DemoResultKind) -> Unit,
) {
  Chip(
      text = kind.label,
      tone = if (kind == current) ChipTone.ANSWER else ChipTone.NEUTRAL,
      modifier = Modifier.clickable { onPick(kind) },
  )
}

/**
 * A species, picked from the catalog rather than typed.
 *
 * Typing a slug is how a preset ends up naming a bird the catalog cannot resolve — which plays as a
 * silent no-op mid-demo. The picker only offers birds that exist.
 */
@Composable
private fun SpeciesField(
    title: String,
    subtitle: String,
    speciesId: String?,
    onPick: (String?) -> Unit,
    allowClear: Boolean,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  var isPicking by remember { mutableStateOf(false) }
  var resolved by remember(speciesId) { mutableStateOf<SpeciesWithMedia?>(null) }

  LaunchedEffect(speciesId) {
    resolved = speciesId?.let {
      runCatching { application.container.birdCatalogRepository.findById(it) }.getOrNull()
    }
  }

  EditorCard(title = title, subtitle = subtitle) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { isPicking = true },
        horizontalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text =
              resolved?.species?.commonName
                  ?: speciesId?.let { "$it — not in the catalog" }
                  ?: "Choose a bird",
          style = BirdSpotterTheme.type.body,
          color =
              if (speciesId != null && resolved == null) {
                MaterialTheme.colorScheme.error
              } else if (speciesId == null) {
                BirdSpotterTheme.colors.textSecondary
              } else {
                BirdSpotterTheme.colors.textPrimary
              },
          modifier = Modifier.weight(1f),
      )
      if (allowClear && speciesId != null) {
        IconButton(onClick = { onPick(null) }) {
          Icon(
              painter = glyph(BirdSpotterTheme.glyphs.close),
              contentDescription = "Clear bird",
          )
        }
      }
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.disclosure),
          contentDescription = null,
          tint = BirdSpotterTheme.colors.textSecondary,
      )
    }
  }

  if (isPicking) {
    SpeciesPickerSheet(
        onPick = {
          onPick(it)
          isPicking = false
        },
        onDismiss = { isPicking = false },
    )
  }
}

/** The catalog, searchable, in a sheet — the one way a species id gets into a preset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesPickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  var query by remember { mutableStateOf("") }
  var birds by remember { mutableStateOf<List<SpeciesWithMedia>>(emptyList()) }

  LaunchedEffect(Unit) {
    birds =
        runCatching {
              application.container.birdCatalogRepository.browseGroups().flatMap { it.species }
            }
            .getOrDefault(emptyList())
  }

  val matches =
      remember(query, birds) {
        if (query.isBlank()) birds
        else birds.filter { it.species.commonName.contains(query, ignoreCase = true) }
      }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
        modifier = Modifier.padding(horizontal = BirdSpotterTheme.space.gutter),
        verticalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.related),
    ) {
      SearchField(text = query, onTextChange = { query = it })
      LazyColumn(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(bottom = BirdSpotterTheme.space.page),
      ) {
        items(matches, key = { it.species.id }) { bird ->
          SpeciesRow(bird = bird, onClick = { onPick(bird.species.id) })
        }
      }
    }
  }
}

/** A confidence, as the percentage the card will show. */
@Composable
private fun ConfidenceField(confidence: Double, onChange: (Double) -> Unit) {
  EditorCard(title = "Confidence", subtitle = "the number the card shows") {
    Text(
        text = "${(confidence * 100).roundToInt()}%",
        style = BirdSpotterTheme.type.title,
        color = BirdSpotterTheme.colors.textPrimary,
    )
    Slider(
        value = confidence.toFloat(),
        onValueChange = { onChange((it * 100).roundToInt() / 100.0) },
        valueRange = 0f..1f,
    )
  }
}

/** A duration in whole tenths of a second, stored as the ms the wire format carries. */
@Composable
private fun MillisField(
    title: String,
    millis: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
    subtitle: String? = null,
    footnote: String? = null,
) {
  EditorCard(title = title, subtitle = subtitle) {
    Text(
        text = secondsLabel(millis),
        style = BirdSpotterTheme.type.title,
        color = BirdSpotterTheme.colors.textPrimary,
    )
    Slider(
        value = millis.toFloat(),
        onValueChange = { onChange(((it / 100).roundToInt()) * 100) },
        valueRange = range,
    )
    if (footnote != null) {
      Text(
          text = footnote,
          style = BirdSpotterTheme.type.label,
          color = BirdSpotterTheme.colors.textSecondary,
      )
    }
  }
}

@Composable
private fun EditorCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
  val space = BirdSpotterTheme.space
  CardSurface {
    Column(
        modifier = Modifier.padding(space.cardInset),
        verticalArrangement = Arrangement.spacedBy(space.related),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(space.tight)) {
        PlateLabel(text = title, color = BirdSpotterTheme.colors.gilt)
        if (subtitle != null) {
          Text(
              text = subtitle,
              style = BirdSpotterTheme.type.label,
              color = BirdSpotterTheme.colors.textSecondary,
          )
        }
      }
      content()
    }
  }
}

/**
 * The frame every row editor shares: a bar with Save, the cards, and Delete where the row can be
 * removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(
    title: String,
    canSave: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
  val space = BirdSpotterTheme.space
  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text(title) },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(painter = glyph(BirdSpotterTheme.glyphs.back), contentDescription = "Back")
              }
            },
            actions = {
              TextButton(onClick = onSave, enabled = canSave) { Text("Save") }
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
      content()

      if (onDelete != null) {
        item {
          ActionButton(
              title = "Delete row",
              onClick = onDelete,
              tone = ActionButtonTone.DESTRUCTIVE,
          )
        }
      }
    }
  }
}
