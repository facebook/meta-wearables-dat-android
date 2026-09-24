/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import android.content.Context
import com.meta.pixelandtexel.birdspotter.data.demo.DemoSettingsStore
import com.meta.pixelandtexel.birdspotter.domain.DemoAmbientCall
import com.meta.pixelandtexel.birdspotter.domain.DemoPhotoResponse
import com.meta.pixelandtexel.birdspotter.domain.DemoPreset
import com.meta.pixelandtexel.birdspotter.domain.DemoQuestion
import com.meta.pixelandtexel.birdspotter.domain.DemoResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Demo Director settings screens' policy: what plays is one dropdown-shaped decision, presets
 * are seeded then freely edited, rows upsert by id, and "Reset to starter" is offered exactly while
 * the shipped script is missing from the list.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
@RunWith(RobolectricTestRunner::class)
class DemoDirectorViewModelTest {

  // ── Arming ─────────────────────────────────────────────────────────────

  @Test
  fun startsWithTheSeededPresetPlaying() {
    val model = model()

    assertEquals(listOf("shipped"), model.uiState.value.presets.map { it.id })
    assertEquals("shipped", model.uiState.value.armedId)
    assertEquals("Full Flow", model.uiState.value.armedName)
  }

  @Test
  fun arm_choosesWhatPlays() {
    val model = model()
    val copyId = model.duplicate("shipped")!!

    model.arm(copyId)

    assertEquals(copyId, model.uiState.value.armedId)
  }

  @Test
  fun arm_withNullIsTheNoneOption() {
    val model = model()

    model.arm(null)

    assertNull(model.uiState.value.armedId)
    assertEquals("None", model.uiState.value.armedName)
  }

  @Test
  fun delete_ofThePlayingPresetFallsBackToNone() {
    val model = model()

    model.delete("shipped")

    assertNull(model.uiState.value.armedId)
    assertTrue(model.uiState.value.presets.isEmpty())
  }

  // ── Reset to starter ───────────────────────────────────────────────────

  @Test
  fun resetIsNotOfferedWhileTheShippedPresetIsUntouched() {
    val model = model()

    assertFalse(model.uiState.value.canResetToShipped)
  }

  @Test
  fun resetIsOfferedOnceTheShippedPresetIsEdited() {
    val model = model()

    model.rename("shipped", "Mine now")

    assertTrue(model.uiState.value.canResetToShipped)
  }

  @Test
  fun resetIsOfferedOnceTheShippedPresetIsDeleted() {
    val model = model()

    model.delete("shipped")

    assertTrue(model.uiState.value.canResetToShipped)
  }

  @Test
  fun resetToShipped_putsItBackAndStopsBeingOffered() {
    val model = model()
    model.rename("shipped", "Mine now")

    model.resetToShipped()

    assertFalse(model.uiState.value.canResetToShipped)
    assertEquals("Full Flow", model.uiState.value.preset("shipped")?.name)
  }

  // ── Presets ────────────────────────────────────────────────────────────

  @Test
  fun duplicate_makesAUniquelyIdentifiedCopy() {
    val model = model()

    val first = model.duplicate("shipped")!!
    val second = model.duplicate("shipped")!!

    assertNotEquals("shipped", first)
    assertNotEquals(first, second)
    assertEquals(3, model.uiState.value.presets.size)
  }

  @Test
  fun duplicate_reIdentifiesItsRows() {
    val model = model()
    model.saveQuestion("shipped", QUESTION)

    val copyId = model.duplicate("shipped")!!

    // Rows are addressed by id; a shared one would make an edit to either land on both.
    val original = model.uiState.value.preset("shipped")!!.questions.single()
    val copied = model.uiState.value.preset(copyId)!!.questions.single()
    assertNotEquals(original.id, copied.id)
    assertEquals(original.answer, copied.answer)
  }

  @Test
  fun newPreset_startsEmptyButPlayable() {
    val model = model()

    val id = model.newPreset()

    val created = model.uiState.value.preset(id)!!
    assertEquals("New preset", created.name)
    assertTrue(created.questions.isEmpty())
    assertTrue(created.photoResponses.isEmpty())
    assertTrue(created.ambientCalls.isEmpty())
  }

  @Test
  fun rename_renamesAPreset() {
    val model = model()

    model.rename("shipped", "Booth — Tuesday")

    assertEquals("Booth — Tuesday", model.uiState.value.preset("shipped")?.name)
  }

  // ── Rows ───────────────────────────────────────────────────────────────

  @Test
  fun saveQuestion_addsThenAmendsById() {
    val model = model()

    model.saveQuestion("shipped", QUESTION)
    assertEquals(1, model.uiState.value.preset("shipped")?.questions?.size)

    model.saveQuestion("shipped", QUESTION.copy(answer = "Changed"))

    val questions = model.uiState.value.preset("shipped")!!.questions
    assertEquals(1, questions.size)
    assertEquals("Changed", questions.single().answer)
  }

  @Test
  fun deleteQuestion_removesIt() {
    val model = model()
    model.saveQuestion("shipped", QUESTION)

    model.deleteQuestion("shipped", QUESTION.id)

    assertTrue(model.uiState.value.preset("shipped")!!.questions.isEmpty())
  }

  @Test
  fun savePhotoResponse_addsInOrder() {
    val model = model()

    model.savePhotoResponse("shipped", photo("a", "First"))
    model.savePhotoResponse("shipped", photo("b", "Second"))

    // Order is the semantic: the Nth capture gets the Nth row.
    assertEquals(
        listOf("First", "Second"),
        model.uiState.value.preset("shipped")!!.photoResponses.map { it.caption },
    )
  }

  @Test
  fun saveAmbientCall_addsThenAmendsById() {
    val model = model()

    model.saveAmbientCall("shipped", CALL)
    model.saveAmbientCall("shipped", CALL.copy(afterMillis = 12_000))

    val calls = model.uiState.value.preset("shipped")!!.ambientCalls
    assertEquals(1, calls.size)
    assertEquals(12_000, calls.single().afterMillis)
  }

  @Test
  fun deleteAmbientCall_removesIt() {
    val model = model()
    model.saveAmbientCall("shipped", CALL)

    model.deleteAmbientCall("shipped", CALL.id)

    assertTrue(model.uiState.value.preset("shipped")!!.ambientCalls.isEmpty())
  }

  @Test
  fun ambientRunningTotals_accumulateGaps() {
    val totals = ambientRunningTotals(
        listOf(
            DemoAmbientCall("a", 8_000, DemoResult.NoIdentification),
            DemoAmbientCall("b", 15_000, DemoResult.NoIdentification),
            DemoAmbientCall("c", 18_000, DemoResult.NoIdentification),
        ),
    )

    assertEquals(listOf(8_000, 23_000, 41_000), totals)
  }

  // ── Result editing ─────────────────────────────────────────────────────

  @Test
  fun resultOf_keepsTheBirdWhenTheKindChanges() {
    val species = DemoResult.Species("green-jay", 0.91)

    val ambiguous = resultOf(DemoResultKind.AMBIGUOUS, species)
    val back = resultOf(DemoResultKind.SPECIES, ambiguous)

    // Flipping the kind must not make the operator pick the bird again.
    assertEquals(DemoResult.Ambiguous(listOf("green-jay")), ambiguous)
    assertEquals(DemoResult.Species("green-jay", 0.87), back)
  }

  @Test
  fun resultOf_dropsTheBirdForAKindThatCarriesNone() {
    val result = resultOf(DemoResultKind.NO_IDENTIFICATION, DemoResult.Species("green-jay", 0.91))

    assertEquals(DemoResult.NoIdentification, result)
  }

  // ── Fixtures ───────────────────────────────────────────────────────────

  private fun model(): DemoDirectorViewModel {
    val context: Context = RuntimeEnvironment.getApplication()
    val prefs = context.getSharedPreferences("demo_settings_vm_test", Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
    return DemoDirectorViewModel(DemoSettingsStore(prefs = prefs, shipped = SHIPPED))
  }

  private fun photo(id: String, caption: String) = DemoPhotoResponse(
      id = id,
      result = DemoResult.NoIdentification,
      caption = caption,
      delayMillis = 10,
  )

  private companion object {
    val SHIPPED = DemoPreset(
        id = "shipped",
        name = "Full Flow",
        unmatchedQuestion = "Sorry — didn't catch that.",
    )

    val QUESTION = DemoQuestion(
        id = "q1",
        prompts = listOf("green with a yellow belly"),
        answer = "That's likely a Green Jay.",
        speciesId = "green-jay",
        delayMillis = 1_500,
    )

    val CALL = DemoAmbientCall(
        id = "c1",
        afterMillis = 8_000,
        result = DemoResult.Species("american-robin", 0.87),
    )
  }
}
