/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

@file:Suppress("UseOfRunBlocking", "UseOfRunBlockingInTest")

package com.meta.pixelandtexel.birdspotter.data.demo

import android.content.Context
import com.meta.pixelandtexel.birdspotter.data.catalog.Species
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesGroup
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.DemoAmbientCall
import com.meta.pixelandtexel.birdspotter.domain.DemoPhotoResponse
import com.meta.pixelandtexel.birdspotter.domain.DemoPreset
import com.meta.pixelandtexel.birdspotter.domain.DemoQuestion
import com.meta.pixelandtexel.birdspotter.domain.DemoResult
import com.meta.pixelandtexel.birdspotter.domain.IdentifyQuery
import com.meta.pixelandtexel.birdspotter.domain.SessionFinding
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Demo Director's policy: the shipped starter decodes (the wire-format parity check — every
 * build decodes the *same file*), ambient calls land in order with names resolved from the catalog,
 * photos answer by index then fall back, spoken questions match on normalized words, and none of it
 * does anything with no preset armed.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
@RunWith(RobolectricTestRunner::class)
class DemoDirectorTest {

  // ── The shipped starter ────────────────────────────────────────────────

  @Test
  fun decodesTheShippedStarterPreset() {
    val preset = DemoPreset.decode(shippedStarterJson())

    assertEquals("starter-full-flow", preset.id)
    assertEquals("Full Flow", preset.name)
    assertEquals(1, preset.version)

    // One ambient row: the robin, eight seconds after the microphone opens.
    assertEquals(1, preset.ambientCalls.size)
    assertEquals(8_000, preset.ambientCalls[0].afterMillis)
    assertEquals(
        DemoResult.Species(speciesId = "american-robin", confidence = 0.87),
        preset.ambientCalls[0].result,
    )

    // One photo row on purpose — the second capture runs off the end of the list.
    assertEquals(1, preset.photoResponses.size)
    assertEquals(
        DemoResult.Species(speciesId = "northern-cardinal", confidence = 0.92),
        preset.photoResponses[0].result,
    )

    // The one question links a real catalog bird, and carries no confidence.
    assertEquals(1, preset.questions.size)
    assertEquals("green-jay", preset.questions[0].speciesId)
  }

  @Test
  fun decodesAnAmbientRowsOwnSpokenLine() {
    // Optional on the wire, both ways round: the shipped row composes its own sentence,
    // and a row that carries one keeps the words as written.
    val authored =
        DemoPreset.decode(
            shippedStarterJson()
                .replace(
                    "\"afterMillis\": 8000,",
                    "\"afterMillis\": 8000, \"spokenLine\": \"Hear that? That's our robin.\",",
                ),
        )
    val shipped = DemoPreset.decode(shippedStarterJson())

    assertEquals("Hear that? That's our robin.", authored.ambientCalls[0].spokenLine)
    assertNull(shipped.ambientCalls[0].spokenLine)
  }

  @Test
  fun decodingIgnoresFieldsItDoesNotKnow() {
    // A preset authored by a newer build must not take this one down on launch.
    val preset =
        DemoPreset.decode(
            shippedStarterJson()
                .replace("\"version\": 1,", "\"version\": 1, \"futureKnob\": true,"),
        )

    assertEquals("starter-full-flow", preset.id)
  }

  // ── Ambient input: the clock lane ──────────────────────────────────────

  @Test
  fun ambientCalls_landInOrderWithResolvedNames() {
    val director = director(
        preset =
            preset(
                ambientCalls =
                    listOf(
                        call("robin", 5, DemoResult.Species("american-robin", 0.87)),
                        call("jay", 5, DemoResult.Species("green-jay", 0.91)),
                    ),
            ),
    )

    val findings = runBlocking { director.findingStream().toList() }

    assertEquals(
        listOf(
            SessionFinding.Bird("american-robin", "American Robin", 0.87f),
            SessionFinding.Bird("green-jay", "Green Jay", 0.91f),
        ),
        findings,
    )
  }

  @Test
  fun ambientCalls_dropASpeciesTheCatalogCannotResolve() {
    val director = director(
        preset =
            preset(
                ambientCalls =
                    listOf(
                        call("ghost", 5, DemoResult.Species("no-such-bird", 0.9)),
                        call("robin", 5, DemoResult.Species("american-robin", 0.87)),
                    ),
            ),
    )

    val findings = runBlocking { director.findingStream().toList() }

    // The unresolvable row lands nothing; the session is not taken down by it.
    assertEquals(1, findings.size)
    assertEquals("american-robin", (findings[0] as SessionFinding.Bird).speciesId)
  }

  @Test
  fun ambientCalls_landNothingForANoIdentificationRow() {
    val director = director(
        preset =
            preset(
                ambientCalls = listOf(call("quiet", 5, DemoResult.NoIdentification)),
            ),
    )

    val findings = runBlocking { director.findingStream().toList() }

    assertTrue(findings.isEmpty())
  }

  @Test
  fun ambientCalls_carryTheLineTheRowAuthored() {
    val director = director(
        preset =
            preset(
                ambientCalls =
                    listOf(
                        call(
                            "robin",
                            5,
                            DemoResult.Species("american-robin", 0.87),
                            spokenLine = "Hear that? That's the robin that nests by the gate.",
                        ),
                    ),
            ),
    )

    val findings = runBlocking { director.findingStream().toList() }

    assertEquals(
        "Hear that? That's the robin that nests by the gate.",
        (findings[0] as SessionFinding.Bird).spokenLine,
    )
  }

  @Test
  fun ambientCalls_carryNoLineWhereTheRowAuthoredNone() {
    // Nothing to say is not silence: it is the session composing the sentence itself.
    val director = director(
        preset =
            preset(
                ambientCalls = listOf(call("robin", 5, DemoResult.Species("american-robin", 0.87))),
            ),
    )

    val findings = runBlocking { director.findingStream().toList() }

    assertNull((findings[0] as SessionFinding.Bird).spokenLine)
  }

  @Test
  fun ambientCalls_landAnAmbiguousRowAsAQuestion() {
    // "Green Jay or Blue Jay?" — the entire reason the speech path exists; an STT row
    // is what settles it.
    val director = director(
        preset =
            preset(
                ambientCalls =
                    listOf(
                        call(
                            "which",
                            5,
                            DemoResult.Ambiguous(listOf("green-jay", "american-robin")),
                        ),
                    ),
            ),
    )

    val findings = runBlocking { director.findingStream().toList() }

    assertEquals(listOf(SessionFinding.Answer("Green Jay or American Robin?")), findings)
  }

  @Test
  fun ambientCalls_dropAnAmbiguousRowTheCatalogCannotFullyName() {
    // Half a question — "Green Jay or …?" — is worse than silence, so one unresolvable
    // candidate drops the whole row.
    val director = director(
        preset =
            preset(
                ambientCalls =
                    listOf(
                        call("which", 5, DemoResult.Ambiguous(listOf("green-jay", "no-such-bird"))),
                    ),
            ),
    )

    val findings = runBlocking { director.findingStream().toList() }

    assertTrue(findings.isEmpty())
  }

  @Test
  fun findingStream_isSilentWithNoPresetArmed() {
    val director = director(preset = null)

    val findings = runBlocking { director.findingStream().toList() }

    assertTrue(findings.isEmpty())
  }

  // ── Photo: the index lane ──────────────────────────────────────────────

  @Test
  fun responseToPhotoAt_walksTheListThenLandsNoIdentification() {
    val row = photoResponse("row-1", DemoResult.Species("northern-cardinal", 0.92))
    val director = director(preset = preset(photoResponses = listOf(row)))

    assertEquals(row, director.responseToPhotoAt(0))
    // Past the end is fixed, not authored: the same no-identification row forever.
    assertEquals(DemoPhotoResponse.pastTheEnd, director.responseToPhotoAt(1))
    assertEquals(DemoPhotoResponse.pastTheEnd, director.responseToPhotoAt(5))
    assertEquals(DemoResult.NoIdentification, DemoPhotoResponse.pastTheEnd.result)
    assertNull(DemoPhotoResponse.pastTheEnd.spokenLine)
  }

  @Test
  fun responseToPhotoAt_isNothingWithNoPresetArmed() {
    val director = director(preset = null)

    assertNull(director.responseToPhotoAt(0))
  }

  // ── STT input: the words lane ──────────────────────────────────────────

  @Test
  fun answerTo_matchesAPromptInsideASpokenSentence() {
    val director = director(preset = preset(questions = listOf(GREEN_JAY_QUESTION)))

    val matched = director.answerTo("Um, it's GREEN, with a yellow belly!")

    assertEquals(GREEN_JAY_QUESTION, matched)
  }

  @Test
  fun answerTo_matchesAcrossWordsTheWearerAddedInTheMiddle() {
    // **The question that went unanswered on real glasses.** The prompt is "green with a
    // yellow belly"; what was actually said was "Green bird with a yellow belly" — one word
    // nobody thought to author, and containment misses the lot.
    val director = director(preset = preset(questions = listOf(GREEN_JAY_QUESTION)))

    val matched = director.answerTo("Green bird with a yellow belly.")

    assertEquals(GREEN_JAY_QUESTION, matched)
  }

  @Test
  fun answerTo_matchesAPromptBuriedInALongerSentence() {
    // A wearer narrating rather than querying, which is how a question actually gets asked
    // out loud when nothing is framing it.
    val director = director(preset = preset(questions = listOf(GREEN_JAY_QUESTION)))

    val matched =
        director.answerTo(
            "I think I just saw a green sort of bird with a yellow belly over there",
        )

    assertEquals(GREEN_JAY_QUESTION, matched)
  }

  @Test
  fun answerTo_isNothingWhenThePromptsWordsAreOutOfOrder() {
    // **The line between forgiving and meaningless.** Order is the whole of what stops this
    // being a bag of words: a sentence that happens to contain the same words scattered
    // through it did not ask the question.
    val director = director(preset = preset(questions = listOf(GREEN_JAY_QUESTION)))

    assertNull(director.answerTo("the belly was yellow and it was green"))
  }

  @Test
  fun answerTo_isNothingWhenOnlySomeOfThePromptWasSaid() {
    // Every word has to be there. A prompt half-said is a sentence about something else.
    val director = director(preset = preset(questions = listOf(GREEN_JAY_QUESTION)))

    assertNull(director.answerTo("it was green"))
  }

  @Test
  fun answerTo_isNothingWhenNoPromptMatches() {
    val director = director(preset = preset(questions = listOf(GREEN_JAY_QUESTION)))

    assertNull(director.answerTo("what does it eat"))
  }

  @Test
  fun answerTo_isNothingWithNoPresetArmed() {
    val director = director(preset = null)

    assertNull(director.answerTo("it's green with a yellow belly"))
  }

  // ── The store: seeding, arming, resetting ──────────────────────────────

  @Test
  fun firstRunSeedsTheShippedPresetAndArmsIt() {
    val store = freshStore()

    // The shipped file is a template: on first run it is copied in, and the copy is
    // what plays.
    assertEquals(listOf(store.shipped), store.presets())
    assertEquals(store.shipped?.id, store.armedPreset()?.id)
  }

  @Test
  fun anEmptiedListIsNotReseeded() {
    val store = freshStore()

    store.savePresets(emptyList())

    // Deleting every preset is allowed, and nothing refills behind the operator.
    assertTrue(store.presets().isEmpty())
    assertNull(store.armedPreset())
  }

  @Test
  fun disarmingPersistsAsExplicitlyNone() {
    val store = freshStore()

    store.armPreset(null)

    assertNull(store.armedPreset())
  }

  @Test
  fun savedPresetsSurviveARoundTripAndCanBeArmed() {
    val store = freshStore()
    val booth = DemoPreset.decode(shippedStarterJson()).copy(id = "booth", name = "Booth")

    store.savePresets(store.presets() + booth)
    store.armPreset("booth")

    assertEquals("booth", store.armedPreset()?.id)
    assertEquals("Booth", store.armedPreset()?.name)
  }

  @Test
  fun armingAnUnknownIdArmsNothing() {
    val store = freshStore()

    store.armPreset("no-such-preset")

    assertNull(store.armedPreset())
  }

  @Test
  fun theShippedPresetIsPresentUntilItIsEdited() {
    val store = freshStore()
    assertTrue(store.isShippedPresent)

    store.savePresets(store.presets().map { it.copy(name = "Edited") })

    // Value equality, not an id check: editing the copy is as much a departure from
    // the shipped script as deleting it, and both are worth being able to undo.
    assertFalse(store.isShippedPresent)
  }

  @Test
  fun resetPutsTheShippedPresetBackAfterAnEdit() {
    val store = freshStore()
    store.savePresets(store.presets().map { it.copy(name = "Edited") })

    store.resetToShipped()

    assertTrue(store.isShippedPresent)
    assertEquals(1, store.presets().size)
  }

  @Test
  fun resetPutsTheShippedPresetBackAfterADelete() {
    val store = freshStore()
    // Deleting through the panel disarms first — see `DemoDirectorViewModel.delete`.
    store.armPreset(null)
    store.savePresets(emptyList())

    store.resetToShipped()

    assertEquals(listOf(store.shipped), store.presets())
    // Restoring a script is not choosing to run it: arming is left alone.
    assertNull(store.armedPreset())
  }

  // ── Fixtures ───────────────────────────────────────────────────────────

  /**
   * The committed preset, found by walking up from the test's working directory to the app's
   * bundled assets.
   */
  private fun shippedStarterJson(): String {
    var dir: File? = File(System.getProperty("user.dir")!!)
    val candidates = listOf(
        "app/src/main/assets/presets/full-flow.json",
        "src/main/assets/presets/full-flow.json",
        "assets/presets/full-flow.json",
    )
    while (dir != null) {
      for (relative in candidates) {
        val candidate = File(dir, relative)
        if (candidate.exists()) return candidate.readText()
      }
      dir = dir.parentFile
    }
    error("presets/full-flow.json not found above ${System.getProperty("user.dir")}")
  }

  private fun freshStore(
      shipped: DemoPreset? = DemoPreset.decode(shippedStarterJson()),
  ): DemoSettingsStore {
    val context: Context = RuntimeEnvironment.getApplication()
    val prefs = context.getSharedPreferences("demo_settings_test", Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
    return DemoSettingsStore(prefs = prefs, shipped = shipped)
  }

  /** A director over a store holding exactly [preset], armed — or holding nothing. */
  private fun director(preset: DemoPreset?): PresetDemoDirector {
    val store = freshStore(shipped = preset)
    if (preset == null) store.armPreset(null)
    return PresetDemoDirector(store = store, catalog = FakeCatalog)
  }

  private fun preset(
      questions: List<DemoQuestion> = emptyList(),
      photoResponses: List<DemoPhotoResponse> = emptyList(),
      ambientCalls: List<DemoAmbientCall> = emptyList(),
  ) = DemoPreset(
      id = "test-preset",
      name = "Test",
      questions = questions,
      photoResponses = photoResponses,
      ambientCalls = ambientCalls,
      unmatchedQuestion = "Sorry — didn't catch that.",
  )

  private fun call(
      id: String,
      afterMillis: Int,
      result: DemoResult,
      spokenLine: String? = null,
  ) = DemoAmbientCall(
      id = id,
      afterMillis = afterMillis,
      result = result,
      spokenLine = spokenLine,
  )

  private fun photoResponse(id: String, result: DemoResult) = DemoPhotoResponse(
      id = id,
      result = result,
      caption = "caption",
      delayMillis = 10,
  )

  private companion object {
    val GREEN_JAY_QUESTION = DemoQuestion(
        id = "green-yellow-belly",
        prompts = listOf("it's green with a yellow belly", "green bird yellow belly"),
        answer = "That's likely a Green Jay.",
        speciesId = "green-jay",
        delayMillis = 10,
    )
  }
}

/** Two birds and nothing else — what the Director resolves names against. */
private object FakeCatalog : BirdCatalogRepository {

  private val birds = mapOf(
      "american-robin" to "American Robin",
      "green-jay" to "Green Jay",
  )

  override suspend fun findById(speciesId: String): SpeciesWithMedia? {
    val commonName = birds[speciesId] ?: return null
    return SpeciesWithMedia(
        species =
            Species(
                id = speciesId,
                commonName = commonName,
                scientificName = "Testus $speciesId",
                familyName = "Testidae",
                browseOrder = 10,
                groupName = "Test Birds",
                sizeClass = 3,
                aboutText = "",
                habitatText = "",
            ),
    )
  }

  override suspend fun allSpecies(): List<Species> = emptyList()

  override suspend fun browseGroups(): List<SpeciesGroup> = emptyList()

  override suspend fun identifyCandidates(query: IdentifyQuery): List<SpeciesWithMedia> =
      emptyList()

  override suspend fun birdOfTheDay(epochDay: Long): SpeciesWithMedia? = null

  override suspend fun seedVersion(): Int? = null
}
