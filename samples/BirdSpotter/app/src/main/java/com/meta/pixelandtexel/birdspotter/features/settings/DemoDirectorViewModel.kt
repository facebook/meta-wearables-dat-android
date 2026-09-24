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
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.data.demo.DemoSettingsStore
import com.meta.pixelandtexel.birdspotter.domain.DemoAmbientCall
import com.meta.pixelandtexel.birdspotter.domain.DemoPhotoResponse
import com.meta.pixelandtexel.birdspotter.domain.DemoPreset
import com.meta.pixelandtexel.birdspotter.domain.DemoQuestion
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Each ambient row's position on the session clock, in ms — the running total the editor prints
 * beside the authored gap (`+15s · 0:23`), so rehearsing against a stopwatch needs no arithmetic.
 * Row gaps stay the stored truth; these are derived, never written back.
 */
fun ambientRunningTotals(calls: List<DemoAmbientCall>): List<Int> =
    calls.runningFold(0) { total, call -> total + call.afterMillis }.drop(1)

/**
 * What the Demo Director settings screens render: every preset, which one is armed, and whether the
 * shipped script is still among them.
 *
 * A snapshot of the store, re-read after every mutation rather than observed: preferences only
 * change through these screens, and a live stream over two prefs keys would be machinery without a
 * reader.
 */
data class DemoDirectorUiState(
    val presets: List<DemoPreset> = emptyList(),
    /** The armed preset's id, or null for the deliberate "none" state. */
    val armedId: String? = null,
    /**
     * Whether "Reset to starter" should be offered — true once no stored preset matches the shipped
     * one, because it was edited or deleted. See [DemoSettingsStore.isShippedPresent].
     */
    val canResetToShipped: Boolean = false,
) {

  fun preset(id: String): DemoPreset? = presets.firstOrNull { it.id == id }

  /** What the dropdown reads when nothing is armed. */
  val armedName: String
    get() = armedId?.let { preset(it)?.name } ?: "None"
}

/**
 * The Demo Director settings screens' state holder — the picker, a preset's page and the row
 * editors all share it, each over its own instance.
 *
 * Every action writes through [DemoSettingsStore] and re-reads, so what the screens show is exactly
 * what the next session will play. Row edits are **upserts by id**: an editor hands back a whole
 * row, and a row whose id is not in the list yet is an addition.
 */
class DemoDirectorViewModel(private val store: DemoSettingsStore) : ViewModel() {

  private val _uiState = MutableStateFlow(read())
  val uiState: StateFlow<DemoDirectorUiState> = _uiState.asStateFlow()

  // ── The preset list ────────────────────────────────────────────────────

  /** The dropdown's selection: arm [id], or null for none. */
  fun arm(id: String?) {
    store.armPreset(id)
    refresh()
  }

  /** The panic button, offered only while [DemoDirectorUiState.canResetToShipped]. */
  fun resetToShipped() {
    store.resetToShipped()
    refresh()
  }

  /**
   * A copy of [id] under a fresh identity, appended to the list. Returns the new preset's id so the
   * caller can navigate straight to it, or null when there is nothing to copy.
   *
   * The copy's rows are re-identified too: ids are unique per row, and two presets sharing one
   * would make an edit to either land on both.
   */
  fun duplicate(id: String): String? {
    val source = _uiState.value.preset(id) ?: return null
    val copy =
        source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} copy",
            questions = source.questions.map { it.copy(id = newId()) },
            photoResponses = source.photoResponses.map { it.copy(id = newId()) },
            ambientCalls = source.ambientCalls.map { it.copy(id = newId()) },
        )
    store.savePresets(store.presets() + copy)
    refresh()
    return copy.id
  }

  /** A preset with nothing scripted — the honest starting point for authoring one. */
  fun newPreset(): String {
    val created = DemoPreset(
        id = UUID.randomUUID().toString(),
        name = "New preset",
        unmatchedQuestion = "Sorry — didn't catch that.",
    )
    store.savePresets(store.presets() + created)
    refresh()
    return created.id
  }

  fun rename(id: String, name: String) = update(id) { it.copy(name = name) }

  /** Remove a preset. Deleting the armed one disarms — nothing is quietly re-armed. */
  fun delete(id: String) {
    if (store.armedPreset()?.id == id) store.armPreset(null)
    store.savePresets(store.presets().filterNot { it.id == id })
    refresh()
  }

  // ── Rows ───────────────────────────────────────────────────────────────

  fun saveQuestion(presetId: String, question: DemoQuestion) =
      update(presetId) { preset ->
        preset.copy(questions = preset.questions.upsert(question) { it.id })
      }

  fun deleteQuestion(presetId: String, questionId: String) =
      update(presetId) { preset ->
        preset.copy(questions = preset.questions.filterNot { it.id == questionId })
      }

  fun savePhotoResponse(presetId: String, response: DemoPhotoResponse) =
      update(presetId) { preset ->
        preset.copy(photoResponses = preset.photoResponses.upsert(response) { it.id })
      }

  fun deletePhotoResponse(presetId: String, responseId: String) =
      update(presetId) { preset ->
        preset.copy(photoResponses = preset.photoResponses.filterNot { it.id == responseId })
      }

  fun saveAmbientCall(presetId: String, call: DemoAmbientCall) =
      update(presetId) { preset ->
        preset.copy(ambientCalls = preset.ambientCalls.upsert(call) { it.id })
      }

  fun deleteAmbientCall(presetId: String, callId: String) =
      update(presetId) { preset ->
        preset.copy(ambientCalls = preset.ambientCalls.filterNot { it.id == callId })
      }

  /**
   * Re-read the store. Public because the screens hold separate instances over the same preferences
   * — returning to the picker after an edit deeper in must not show the snapshot from before it.
   */
  fun refresh() {
    _uiState.value = read()
  }

  private fun update(presetId: String, transform: (DemoPreset) -> DemoPreset) {
    store.savePresets(store.presets().map { if (it.id == presetId) transform(it) else it })
    refresh()
  }

  private fun read() = DemoDirectorUiState(
      presets = store.presets(),
      armedId = store.armedPreset()?.id,
      canResetToShipped = store.shipped != null && !store.isShippedPresent,
  )

  companion object {

    /** A fresh row id. Rows are addressed by id, so every new one needs its own. */
    fun newId(): String = UUID.randomUUID().toString()

    fun factory(store: DemoSettingsStore): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return DemoDirectorViewModel(store) as T
          }
        }
  }
}

/**
 * Replace the element sharing [item]'s id, or append it when none does — what "save a row" means
 * when the editor cannot know whether it is adding or amending.
 */
private fun <T> List<T>.upsert(item: T, id: (T) -> String): List<T> =
    if (any { id(it) == id(item) }) map { if (id(it) == id(item)) item else it } else this + item
