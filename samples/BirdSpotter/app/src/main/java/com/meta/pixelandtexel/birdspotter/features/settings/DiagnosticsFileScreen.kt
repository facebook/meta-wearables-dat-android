/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.LogEntry
import com.meta.pixelandtexel.birdspotter.domain.LogLevel
import com.meta.pixelandtexel.birdspotter.ui.components.Chip
import com.meta.pixelandtexel.birdspotter.ui.components.ChipTone
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One recorded run, read back — the screen somebody opens after a demo went sideways.
 *
 * **Newest first, and it says so.** See [DiagnosticsFileViewModel] for why.
 *
 * **The category chips are the point of the whole feature.** One tap on *Glasses* is the answer to
 * "was that the wearable or the phone?", which is the first question anybody asks. Only the
 * categories the file actually contains are offered.
 *
 * The share button hands out the file itself rather than what is on screen: a filtered view is for
 * reading here, and a log mailed to somebody else should be the whole thing.
 */
@Composable
fun DiagnosticsFileScreen(
    fileName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val container = (context.applicationContext as BirdSpotterApplication).container
  val viewModel: DiagnosticsFileViewModel = viewModel(
      factory = DiagnosticsFileViewModel.factory(container.diagnosticsLogStore, fileName),
      key = fileName,
  )
  val entries by viewModel.entries.collectAsStateWithLifecycle()
  val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()
  val categories by viewModel.availableCategories.collectAsStateWithLifecycle()
  val category by viewModel.category.collectAsStateWithLifecycle()
  val level by viewModel.level.collectAsStateWithLifecycle()
  val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

  DiagnosticsFileScreenContent(
      entries = entries,
      totalCount = allEntries.size,
      categories = categories,
      selectedCategory = category,
      level = level,
      isLoading = isLoading,
      onBack = onBack,
      onSetCategory = viewModel::setCategory,
      onSetLevel = viewModel::setLevel,
      onShare = { shareLog(context, viewModel.shareFile) },
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsFileScreenContent(
    entries: List<LogEntry>,
    totalCount: Int,
    categories: List<LogCategory>,
    selectedCategory: LogCategory?,
    level: LogLevel,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSetCategory: (LogCategory?) -> Unit,
    onSetLevel: (LogLevel) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Run") },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(
                    painter = glyph(BirdSpotterTheme.glyphs.back),
                    contentDescription = "Back",
                )
              }
            },
            actions = {
              TextButton(onClick = onShare) { Text("Share", style = type.label) }
            },
        )
      },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { innerPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(horizontal = space.gutter, vertical = space.separate),
        verticalArrangement = Arrangement.spacedBy(space.related),
    ) {
      item {
        Filters(
            categories = categories,
            selectedCategory = selectedCategory,
            level = level,
            shownCount = entries.size,
            totalCount = totalCount,
            onSetCategory = onSetCategory,
            onSetLevel = onSetLevel,
        )
      }
      if (isLoading) {
        item { Text("Reading…", style = type.body, color = colors.textSecondary) }
      } else if (entries.isEmpty()) {
        item {
          Text(
              text =
                  if (totalCount == 0) {
                    "This run recorded nothing."
                  } else {
                    "Nothing in this run matches those filters."
                  },
              style = type.body,
              color = colors.textSecondary,
          )
        }
      } else {
        items(entries) { entry -> EntryRow(entry) }
      }
    }
  }
}

/** The two filters, and the note that the newest line is at the top. */
@Composable
private fun Filters(
    categories: List<LogCategory>,
    selectedCategory: LogCategory?,
    level: LogLevel,
    shownCount: Int,
    totalCount: Int,
    onSetCategory: (LogCategory?) -> Unit,
    onSetLevel: (LogLevel) -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Column(verticalArrangement = Arrangement.spacedBy(space.snug)) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(space.snug),
    ) {
      FilterChip(title = "All", isOn = selectedCategory == null) { onSetCategory(null) }
      categories.forEach { category ->
        FilterChip(
            title = category.displayLabel,
            isOn = selectedCategory == category,
        ) {
          onSetCategory(category)
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(space.snug)) {
      LogLevel.entries.forEach { entry ->
        FilterChip(title = entry.displayLabel, isOn = level == entry) { onSetLevel(entry) }
      }
    }
    Text(
        text = "Newest first · $shownCount of $totalCount lines",
        style = type.caption,
        color = colors.textFaint,
    )
  }
}

/**
 * A [Chip] wearing a tap. The tone carries the state — `ANSWER` is the gilt the app names things
 * in, which is what a chosen filter is.
 */
@Composable
private fun FilterChip(title: String, isOn: Boolean, onClick: () -> Unit) {
  TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
    Chip(text = title, tone = if (isOn) ChipTone.ANSWER else ChipTone.NEUTRAL)
  }
}

/**
 * One line: when, how loud, from where, and what happened.
 *
 * The time and the category sit on their own row above the message rather than beside it — a phone
 * is 390dp wide, and a message pushed into the remaining third wraps to four lines and stops being
 * scannable.
 */
@Composable
private fun EntryRow(entry: LogEntry) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val type = BirdSpotterTheme.type

  Column(verticalArrangement = Arrangement.spacedBy(space.tight)) {
    Row(horizontalArrangement = Arrangement.spacedBy(space.snug)) {
      Text(text = timestamp(entry), style = type.data, color = colors.textFaint)
      Text(text = entry.level.displayLabel, style = type.label, color = ink(entry.level))
      Text(
          text = entry.category.displayLabel,
          style = type.label,
          color = colors.textSecondary,
      )
    }
    Text(text = entry.message, style = type.body, color = colors.textPrimary)
    HairlineRule()
  }
}

/**
 * **Only the two that mean something get a colour.** `vermilion` is the ink of stopping and undoing
 * everywhere else in the app, and an error is the one line here that is that; a warning takes gilt,
 * the app's own emphasis. Debug and info stay in the body hand, because a screen where every row is
 * coloured is a screen with no emphasis at all — see
 * [com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterColors.vermilion].
 */
@Composable
private fun ink(level: LogLevel): Color =
    when (level) {
      LogLevel.ERROR -> BirdSpotterTheme.colors.vermilion
      LogLevel.WARNING -> BirdSpotterTheme.colors.gilt
      LogLevel.INFO,
      LogLevel.DEBUG -> BirdSpotterTheme.colors.textSecondary
    }

/**
 * The line's own timestamp, to the second — the column the eye scans down.
 *
 * Seconds, not milliseconds: the file carries them for ordering two lines inside one tick, which is
 * a machine's concern, and a screen full of `.913` is three characters of noise on every row.
 */
private fun timestamp(entry: LogEntry): String = TimeFormat.format(Date(entry.timestampMillis))

private val TimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

/**
 * Hands the file out through the app's [FileProvider] — see the provider declaration in
 * `AndroidManifest.xml`. A `file://` URI would throw `FileUriExposedException`, and the receiving
 * app could not read this app's private storage in any case.
 */
private fun shareLog(context: Context, file: File) {
  if (!file.exists()) return
  val uri =
      FileProvider.getUriForFile(
          context,
          "${context.packageName}.diagnostics",
          file,
      )
  val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
  context.startActivity(Intent.createChooser(send, "Share log"))
}

@Preview(showBackground = true)
@Composable
private fun DiagnosticsFileScreenPreview() {
  BirdSpotterTheme {
    DiagnosticsFileScreenContent(
        entries =
            listOf(
                LogEntry(
                    1_753_142_405_210,
                    LogLevel.INFO,
                    LogCategory.GLASSES,
                    "capture — 812_340 bytes crossed in 1_284 ms",
                ),
                LogEntry(
                    1_753_142_400_470,
                    LogLevel.WARNING,
                    LogCategory.AUDIO,
                    "glasses microphone gave out — falling back to the phone",
                ),
            ),
        totalCount = 2,
        categories = listOf(LogCategory.GLASSES, LogCategory.AUDIO),
        selectedCategory = null,
        level = LogLevel.DEBUG,
        isLoading = false,
        onBack = {},
        onSetCategory = {},
        onSetLevel = {},
        onShare = {},
    )
  }
}
