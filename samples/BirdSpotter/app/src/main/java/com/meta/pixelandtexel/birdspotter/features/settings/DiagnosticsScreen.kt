/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsLogFile
import com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsLogStore
import com.meta.pixelandtexel.birdspotter.domain.LogLevel
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
import java.text.DateFormat
import java.util.Date

/**
 * Settings → Diagnostics: how the log is set, and the runs it has recorded.
 *
 * **Deliberately not called "Logs".** In this codebase logging already means the Journal — logging
 * *sightings* — and a second "Logs" in the same Settings screen is a genuine ambiguity, not a
 * pedantic one.
 *
 * The controls sit above the list because they change what the next run records, and the list is
 * history. "Delete all" sits last, for the same reason emptying the Journal does on the screen this
 * is pushed from: nothing you were scrolling for should be underneath the one control that throws
 * it away.
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val container = (LocalContext.current.applicationContext as BirdSpotterApplication).container
  val viewModel: DiagnosticsViewModel = viewModel(
      factory =
          DiagnosticsViewModel.factory(
              container.diagnosticsLogStore,
              container.diagnosticsSettings,
              container::installDiagnostics,
          ),
  )
  val files by viewModel.files.collectAsStateWithLifecycle()
  val isFileLoggingEnabled by viewModel.isFileLoggingEnabled.collectAsStateWithLifecycle()
  val minimumLevel by viewModel.minimumLevel.collectAsStateWithLifecycle()

  DiagnosticsScreenContent(
      files = files,
      isFileLoggingEnabled = isFileLoggingEnabled,
      minimumLevel = minimumLevel,
      levels = viewModel.levels,
      onBack = onBack,
      onOpenFile = onOpenFile,
      onSetFileLogging = viewModel::setFileLogging,
      onSetMinimumLevel = viewModel::setMinimumLevel,
      onDeleteAll = viewModel::deleteAll,
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsScreenContent(
    files: List<DiagnosticsLogFile>,
    isFileLoggingEnabled: Boolean,
    minimumLevel: LogLevel,
    levels: List<LogLevel>,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onSetFileLogging: (Boolean) -> Unit,
    onSetMinimumLevel: (LogLevel) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var confirmingDeleteAll by remember { mutableStateOf(false) }
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type
  val context = LocalContext.current

  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Diagnostics") },
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(horizontal = space.gutter, vertical = space.separate),
        verticalArrangement = Arrangement.spacedBy(space.section),
    ) {
      item {
        // What this is, in the two sentences somebody reads once.
        Text(
            text =
                "What the app did, run by run — the glasses, the microphones, the " +
                    "journal. A new file each launch; the oldest are dropped once there " +
                    "are ${DiagnosticsLogStore.MaxFiles}.",
            style = type.body,
            color = colors.textSecondary,
        )
      }
      item {
        RecordingSection(
            isFileLoggingEnabled = isFileLoggingEnabled,
            minimumLevel = minimumLevel,
            levels = levels,
            onSetFileLogging = onSetFileLogging,
            onSetMinimumLevel = onSetMinimumLevel,
        )
      }
      item { PlateLabel(text = "Runs", color = colors.gilt) }
      if (files.isEmpty()) {
        item {
          Text(
              text =
                  if (isFileLoggingEnabled) {
                    "Nothing recorded yet."
                  } else {
                    "Nothing recorded — writing to files is off."
                  },
              style = type.body,
              color = colors.textSecondary,
          )
        }
      }
      items(files, key = { it.name }) { file ->
        RunRow(file = file, context = context, onClick = { onOpenFile(file.name) })
      }
      if (files.isNotEmpty()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
            HairlineRule()
            Text(
                text = "Every recorded run, including the one happening now.",
                style = type.label,
                color = colors.textSecondary,
            )
            ActionButton(
                title = "Delete all logs",
                onClick = { confirmingDeleteAll = true },
                tone = ActionButtonTone.DESTRUCTIVE,
            )
          }
        }
      }
    }
  }

  if (confirmingDeleteAll) {
    AlertDialog(
        onDismissRequest = { confirmingDeleteAll = false },
        title = { Text("Delete all diagnostic logs?") },
        text = {
          Text(
              "Every recorded run goes, including this one. It can't be undone — " +
                  "share anything you still need first.",
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                confirmingDeleteAll = false
                onDeleteAll()
              },
          ) {
            Text("Delete everything", color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmingDeleteAll = false }) { Text("Cancel") }
        },
    )
  }
}

@Composable
private fun RecordingSection(
    isFileLoggingEnabled: Boolean,
    minimumLevel: LogLevel,
    levels: List<LogLevel>,
    onSetFileLogging: (Boolean) -> Unit,
    onSetMinimumLevel: (LogLevel) -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Recording", color = colors.gilt)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = "Write to files", style = type.headline, color = colors.textPrimary)
        Text(
            text = "Off means only logcat sees these — nothing is kept on the phone.",
            style = type.label,
            color = colors.textSecondary,
        )
      }
      Switch(
          checked = isFileLoggingEnabled,
          onCheckedChange = onSetFileLogging,
          colors =
              SwitchDefaults.colors(
                  checkedThumbColor = colors.paper,
                  checkedTrackColor = colors.verdigris,
              ),
      )
    }

    LevelPicker(
        minimumLevel = minimumLevel,
        levels = levels,
        onSetMinimumLevel = onSetMinimumLevel,
    )
  }
}

/**
 * The floor. A menu rather than a row of four, because the closed state is itself the readout — the
 * same reasoning as the Demo Director's armed picker.
 */
@Composable
private fun LevelPicker(
    minimumLevel: LogLevel,
    levels: List<LogLevel>,
    onSetMinimumLevel: (LogLevel) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Column {
    CardSurface(onClick = { expanded = true }) {
      Column(
          modifier = Modifier.fillMaxWidth().padding(space.cardInset),
          verticalArrangement = Arrangement.spacedBy(space.tight),
      ) {
        PlateLabel(text = "Detail", color = colors.gilt)
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = levelDescription(minimumLevel),
              style = type.title,
              color = colors.textPrimary,
              modifier = Modifier.weight(1f),
          )
          Icon(
              painter = glyph(BirdSpotterTheme.glyphs.disclosure),
              contentDescription = null,
              tint = colors.textSecondary,
          )
        }
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      levels.forEach { level ->
        DropdownMenuItem(
            text = { Text(levelDescription(level)) },
            onClick = {
              expanded = false
              onSetMinimumLevel(level)
            },
            trailingIcon =
                if (level == minimumLevel) {
                  {
                    Icon(
                        painter = glyph(BirdSpotterTheme.glyphs.selected),
                        contentDescription = null,
                        tint = colors.gilt,
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

@Composable
private fun RunRow(file: DiagnosticsLogFile, context: Context, onClick: () -> Unit) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  DisclosureRow(onClick = onClick) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space.snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = runTitle(file), style = type.headline, color = colors.textPrimary)
      if (file.isCurrent) Chip(text = "Now", tone = ChipTone.ANSWER)
    }
    Text(
        text = DiagnosticsViewModel.summary(context, file),
        style = type.label,
        color = colors.textSecondary,
    )
  }
}

/** A file's own heading — the moment the run started, in the reader's own formatting. */
private fun runTitle(file: DiagnosticsLogFile): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
        .format(Date(file.startedAtMillis))

/**
 * What a level promises, in the reader's terms rather than the logger's — "Errors only" says what
 * you will get where "error" says what you will not.
 */
fun levelDescription(level: LogLevel): String =
    when (level) {
      LogLevel.DEBUG -> "Everything"
      LogLevel.INFO -> "The main beats"
      LogLevel.WARNING -> "Warnings and errors"
      LogLevel.ERROR -> "Errors only"
    }

@Preview(showBackground = true)
@Composable
private fun DiagnosticsScreenPreview() {
  BirdSpotterTheme {
    DiagnosticsScreenContent(
        files =
            listOf(
                DiagnosticsLogFile("birdspotter-a.log", 1_753_142_400_000, 41_231, true),
                DiagnosticsLogFile("birdspotter-b.log", 1_753_138_800_000, 262_144, false),
            ),
        isFileLoggingEnabled = true,
        minimumLevel = LogLevel.DEBUG,
        levels = LogLevel.entries,
        onBack = {},
        onOpenFile = {},
        onSetFileLogging = {},
        onSetMinimumLevel = {},
        onDeleteAll = {},
    )
  }
}
