/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsLogStore
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.LogEntry
import com.meta.pixelandtexel.birdspotter.domain.LogLevel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One log file, read and filtered — what the reader is actually hunting through.
 *
 * **Newest first.** A log reads naturally oldest-to-newest, but nobody opens this at the beginning:
 * they open it because something just went wrong, and the answer is the last few lines. So the file
 * is reversed once, here, and the screen says so.
 *
 * **The filters are the feature.** "Was that the glasses or the phone?" is a category tap, and a
 * level floor drops the step-by-step once the shape of the failure is clear. Both are applied over
 * the whole file rather than a page of it — these top out at 256 KB, so filtering in memory is a
 * few thousand comparisons.
 */
class DiagnosticsFileViewModel(
    private val store: DiagnosticsLogStore,
    val fileName: String,
) : ViewModel() {

  /** Everything in the file, newest first. Filtering reads from this rather than the disk. */
  private val _allEntries = MutableStateFlow<List<LogEntry>>(emptyList())
  val allEntries: StateFlow<List<LogEntry>> = _allEntries.asStateFlow()

  /** `null` means every category — the state the screen opens in. */
  private val _category = MutableStateFlow<LogCategory?>(null)
  val category: StateFlow<LogCategory?> = _category.asStateFlow()

  private val _level = MutableStateFlow(LogLevel.DEBUG)
  val level: StateFlow<LogLevel> = _level.asStateFlow()

  private val _isLoading = MutableStateFlow(true)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  /** The lines the screen draws. */
  val entries: StateFlow<List<LogEntry>> =
      combine(_allEntries, _category, _level) { all, category, level ->
            all.filter { it.level >= level && (category == null || it.category == category) }
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /**
   * Only the categories this file actually contains, in the canonical order.
   *
   * Offering all nine would be nine chips of which six find nothing — a filter that can only
   * disappoint is worse than no filter.
   */
  val availableCategories: StateFlow<List<LogCategory>> =
      _allEntries
          .map { all ->
            val present = all.mapTo(mutableSetOf()) { it.category }
            LogCategory.entries.filter { it in present }
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** What the toolbar shares out: the file exactly as written. */
  val shareFile: File
    get() = store.resolve(fileName)

  init {
    viewModelScope.launch {
      _allEntries.value =
          withContext(Dispatchers.IO) {
            store.entries(fileName).asReversed()
          }
      _isLoading.value = false
    }
  }

  fun setCategory(category: LogCategory?) {
    _category.value = category
  }

  fun setLevel(level: LogLevel) {
    _level.value = level
  }

  companion object {
    fun factory(
        store: DiagnosticsLogStore,
        fileName: String,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { DiagnosticsFileViewModel(store, fileName) }
    }
  }
}
