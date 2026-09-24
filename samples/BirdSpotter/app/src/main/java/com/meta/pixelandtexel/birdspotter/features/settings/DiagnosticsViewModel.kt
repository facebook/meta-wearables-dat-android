/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsLogFile
import com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsLogStore
import com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsSettingsStore
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the Diagnostics screen shows: how the log is set, and the files it has written.
 *
 * **Reads on demand rather than watching.** A log that redrew the list under the reader as lines
 * landed would be a screen nobody could scroll — and the *current* file grows on every tap, so the
 * sizes would never sit still. The list is re-read on entering, which is when the answer changed
 * anyway.
 */
class DiagnosticsViewModel(
    private val store: DiagnosticsLogStore,
    private val settings: DiagnosticsSettingsStore,
    /**
     * Re-run whenever a setting changes, so the sinks match what the screen says. The composition
     * root's own method — see [com.meta.pixelandtexel.birdspotter.AppContainer.installDiagnostics].
     */
    private val reinstall: () -> Unit,
) : ViewModel() {

  private val _files = MutableStateFlow<List<DiagnosticsLogFile>>(emptyList())
  val files: StateFlow<List<DiagnosticsLogFile>> = _files.asStateFlow()

  private val _isFileLoggingEnabled = MutableStateFlow(settings.isFileLoggingEnabled)
  val isFileLoggingEnabled: StateFlow<Boolean> = _isFileLoggingEnabled.asStateFlow()

  private val _minimumLevel = MutableStateFlow(settings.minimumLevel)
  val minimumLevel: StateFlow<LogLevel> = _minimumLevel.asStateFlow()

  /**
   * Every level, for the picker. Held here rather than read off [LogLevel] in the composable so the
   * order the screen offers is a decision this class owns.
   */
  val levels: List<LogLevel> = LogLevel.entries

  init {
    refresh()
  }

  /**
   * Re-reads the directory. Cheap — a dozen `length()` calls — and off the main thread all the
   * same, because it is a filesystem walk.
   */
  fun refresh() {
    viewModelScope.launch {
      _files.value = withContext(Dispatchers.IO) { store.files() }
    }
  }

  fun setFileLogging(enabled: Boolean) {
    if (enabled == _isFileLoggingEnabled.value) return
    _isFileLoggingEnabled.value = enabled
    settings.isFileLoggingEnabled = enabled
    // Through the composition root, so "which sinks are installed" stays written in one
    // place — and so switching files back on opens a fresh one rather than appending to a
    // run that stopped being recorded halfway through.
    reinstall()
    refresh()
  }

  fun setMinimumLevel(level: LogLevel) {
    if (level == _minimumLevel.value) return
    _minimumLevel.value = level
    settings.minimumLevel = level
    reinstall()
  }

  /** Empties the directory. The screen asks first — nothing here asks a second time. */
  fun deleteAll() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { store.deleteAll() }
      BirdLog.info(LogCategory.APP) { "diagnostics — every log file deleted from Settings" }
      refresh()
    }
  }

  companion object {
    fun factory(
        store: DiagnosticsLogStore,
        settings: DiagnosticsSettingsStore,
        reinstall: () -> Unit,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { DiagnosticsViewModel(store, settings, reinstall) }
    }

    /**
     * What a row says under its title: how big, and whether it is the run happening now.
     *
     * A companion function taking a `Context` rather than a method, because
     * [Formatter.formatShortFileSize] needs one and a `ViewModel` holding a `Context` is the leak
     * every Android review opens with.
     */
    fun summary(context: android.content.Context, file: DiagnosticsLogFile): String {
      val size = Formatter.formatShortFileSize(context, file.sizeBytes)
      return if (file.isCurrent) "$size · recording now" else size
    }
  }
}
