/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import android.os.Looper
import com.meta.pixelandtexel.birdspotter.data.catalog.BirdBehavior
import com.meta.pixelandtexel.birdspotter.data.catalog.PlumageColor
import com.meta.pixelandtexel.birdspotter.data.catalog.Species
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesGroup
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.data.journal.OutingDraft
import com.meta.pixelandtexel.birdspotter.data.journal.OutingWithChildren
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import com.meta.pixelandtexel.birdspotter.domain.IdentifyQuery
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import com.meta.pixelandtexel.birdspotter.domain.LocationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The wizard's save, which is the only path that writes a `MANUAL` outing today.
 *
 * The location cases are the point of this suite. `Outing`'s coordinates are required, and the
 * Identify gate grants the *permission* rather than handing over a fix — so what the flow does with
 * no fix is a real branch, and it was briefly a silent no-op that looked exactly like a broken
 * button.
 *
 * Robolectric so `viewModelScope` has a main looper to launch on; [settle] runs what it queued.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
@RunWith(RobolectricTestRunner::class)
class IdentifyWizardViewModelTest {

  @Test
  fun saveSighting_withAFix_writesTheOuting() {
    val journal = RecordingJournal()
    val model = viewModel(journal, FixedLocationProvider(Coordinate(39.2098, -84.4699)))
    model.captureLocation()
    settle()

    answerEverything(model)
    model.saveSighting("northern-cardinal")
    settle()

    val draft = journal.saved.single()
    assertEquals(39.2098, draft.location.latitude, 1e-9)
    assertEquals(-84.4699, draft.location.longitude, 1e-9)
    assertEquals("northern-cardinal", model.uiState.value.savedSpeciesId)
    assertNull(model.uiState.value.saveError)
  }

  @Test
  fun saveSighting_withNoFixYet_asksAgainRatherThanGivingUp() {
    // The screen's `captureLocation` is still in flight when the user reaches Results —
    // the save asks for itself rather than failing on an empty ui state.
    val journal = RecordingJournal()
    val model = viewModel(journal, FixedLocationProvider(Coordinate(1.0, 2.0)))

    answerEverything(model)
    model.saveSighting("northern-cardinal")
    settle()

    assertEquals(1.0, journal.saved.single().location.latitude, 1e-9)
  }

  @Test
  fun saveSighting_withNoFixAtAll_savesNothingAndSaysSo() {
    // The emulator with no location set, and the phone indoors at a venue. Nothing is
    // written — an outing cannot claim to be nowhere — but the failure has to reach the
    // screen, because a button that does nothing at all reads as a broken app.
    val journal = RecordingJournal()
    val model = viewModel(journal, FixedLocationProvider(null))
    model.captureLocation()
    settle()

    answerEverything(model)
    model.saveSighting("northern-cardinal")
    settle()

    assertTrue(journal.saved.isEmpty())
    assertNull(model.uiState.value.savedSpeciesId)
    assertEquals(IdentifyWizardSaveError.NO_LOCATION_FIX, model.uiState.value.saveError)
    // And the button is live again, because asking once more is the whole remedy.
    assertNull(model.uiState.value.savingSpeciesId)
  }

  @Test
  fun saveSighting_afterAFailedSave_clearsTheErrorOnTheNextTry() {
    val journal = RecordingJournal()
    val model = viewModel(journal, ScriptedLocationProvider(listOf(null, Coordinate(3.0, 4.0))))

    answerEverything(model)
    model.saveSighting("northern-cardinal")
    settle()
    assertEquals(IdentifyWizardSaveError.NO_LOCATION_FIX, model.uiState.value.saveError)

    model.saveSighting("northern-cardinal")
    settle()

    assertNull(model.uiState.value.saveError)
    assertEquals(3.0, journal.saved.single().location.latitude, 1e-9)
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  /** Runs whatever `viewModelScope` queued on the main looper, which Robolectric pauses. */
  private fun settle() = shadowOf(Looper.getMainLooper()).idle()

  private fun answerEverything(model: IdentifyWizardViewModel) {
    model.chooseSizeClass(2)
    model.toggleColor(PlumageColor.RED)
    model.chooseBehavior(BirdBehavior.AT_FEEDER)
  }

  private fun viewModel(
      journal: JournalRepository,
      location: LocationProvider,
  ) = IdentifyWizardViewModel(
      birdCatalog = EmptyCatalog(),
      journal = journal,
      locationProvider = location,
      today = { LocalDate.of(2026, 7, 26) },
      now = { 1_000L },
  )
}

/** Keeps every draft it is handed, so a test can read what the wizard actually assembled. */
private class RecordingJournal : JournalRepository {
  val saved = mutableListOf<OutingDraft>()

  override fun journalStream(): Flow<List<OutingWithChildren>> = flowOf(emptyList())

  override fun lifeListCountStream(): Flow<Int> = flowOf(0)

  override suspend fun findById(outingId: String): OutingWithChildren? = null

  override suspend fun saveOuting(draft: OutingDraft): String {
    saved += draft
    return "outing-${saved.size}"
  }

  override suspend fun updateNotes(outingId: String, notes: String?) = Unit

  override suspend fun delete(outingId: String) = Unit

  override suspend fun deleteAll() = Unit
}

private class FixedLocationProvider(private val fix: Coordinate?) : LocationProvider {
  override suspend fun currentCoordinate(): Coordinate? = fix
}

/** A phone that fails to fix, then succeeds — the retry the error message invites. */
private class ScriptedLocationProvider(private val fixes: List<Coordinate?>) : LocationProvider {
  private var index = 0

  override suspend fun currentCoordinate(): Coordinate? = fixes[minOf(index++, fixes.lastIndex)]
}

/** The wizard's catalog reads are not what this suite is about. */
private class EmptyCatalog : BirdCatalogRepository {
  override suspend fun allSpecies(): List<Species> = emptyList()

  override suspend fun browseGroups(): List<SpeciesGroup> = emptyList()

  override suspend fun findById(speciesId: String): SpeciesWithMedia? = null

  override suspend fun identifyCandidates(query: IdentifyQuery): List<SpeciesWithMedia> =
      emptyList()

  override suspend fun birdOfTheDay(epochDay: Long): SpeciesWithMedia? = null

  override suspend fun seedVersion(): Int? = null
}
