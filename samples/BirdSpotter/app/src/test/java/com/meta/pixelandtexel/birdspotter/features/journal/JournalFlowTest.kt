/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

@file:Suppress("UseOfRunBlocking", "UseOfRunBlockingInTest")

package com.meta.pixelandtexel.birdspotter.features.journal

import com.meta.pixelandtexel.birdspotter.data.journal.CaptureLocation
import com.meta.pixelandtexel.birdspotter.data.journal.CaptureSource
import com.meta.pixelandtexel.birdspotter.data.journal.JournalDatabase
import com.meta.pixelandtexel.birdspotter.data.journal.LocalJournalRepository
import com.meta.pixelandtexel.birdspotter.data.journal.OutingDraft
import com.meta.pixelandtexel.birdspotter.data.journal.OutingEventType
import com.meta.pixelandtexel.birdspotter.data.journal.OutingKind
import com.meta.pixelandtexel.birdspotter.data.journal.OutingMediaType
import com.meta.pixelandtexel.birdspotter.data.journal.PendingEvent
import com.meta.pixelandtexel.birdspotter.data.journal.PendingMedia
import com.meta.pixelandtexel.birdspotter.data.journal.PendingSighting
import com.meta.pixelandtexel.birdspotter.data.journal.WizardTrait
import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Journal's end-to-end flows, through the real store: an outing reaches the Journal, opens with
 * everything the detail screen reads, and deleting it takes the rows and files back out.
 *
 * Repository-level rather than screen-driven on purpose. The live spine is the draft the recording
 * flow will hand over at Save — segments and detections pinned to a timeline; the wizard spine is
 * the exact draft `IdentifyWizardViewModel.saveSighting` builds today. Driving those against a real
 * `LocalJournalRepository` over `journal.db` and a temp media directory tests the flow's spine
 * without an emulator, where the screens' own logic is covered by `JournalViewModelTest`.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
@RunWith(RobolectricTestRunner::class)
class JournalFlowTest {

  private lateinit var database: JournalDatabase
  private lateinit var mediaRoot: File
  private lateinit var mediaFileStore: MediaFileStore
  private lateinit var repository: JournalRepository

  @Before
  fun setUp() {
    database = JournalDatabase.openInMemory(RuntimeEnvironment.getApplication())
    mediaRoot = Files.createTempDirectory("birdspotter-flow").toFile()
    mediaFileStore = MediaFileStore(mediaRoot)
    repository =
        LocalJournalRepository(
            store = database.journalStore(),
            mediaFileStore = mediaFileStore,
        )
  }

  @After
  fun tearDown() {
    database.close()
    mediaRoot.deleteRecursively()
  }

  @Test
  fun journalFlow_outingAppearsOpensAndDeletes() = runBlocking {
    // The live spine: a walk's worth of capture, assembled in memory, saved in one call.
    val heard =
        PendingEvent.heard(
            speciesId = "american-robin",
            confidence = 0.87,
            offsetMs = 12_000L,
        )
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                durationMs = 30_000L,
                media =
                    listOf(
                        PendingMedia(
                            type = OutingMediaType.AUDIO,
                            source = CaptureSource.GLASSES,
                            bytes = byteArrayOf(0x1, 0x2),
                            fileExtension = "wav",
                            offsetMs = 0L,
                            durationMs = 5_000L,
                        ),
                        PendingMedia(
                            type = OutingMediaType.PHOTO,
                            source = CaptureSource.GLASSES,
                            bytes = byteArrayOf(0x3, 0x4, 0x5),
                            fileExtension = "jpg",
                            offsetMs = 21_000L,
                            width = 120,
                            height = 90,
                        ),
                    ),
                events = listOf(heard),
                sightings =
                    listOf(
                        PendingSighting(speciesId = "american-robin", confirming = heard),
                    ),
            ),
        )

    // Journal — the one outing, its species counted on the life list.
    assertEquals(listOf(id), repository.journalStream().first().map { it.outing.id })
    assertEquals(1, repository.lifeListCountStream().first())

    // Detail — what the entry page loads: the root, the confirmed bird, both files, the pin.
    val opened = repository.findById(id)!!
    assertEquals(30_000L, opened.outing.durationMs)
    assertEquals("american-robin", opened.sightings.single().speciesId)
    assertEquals(1, opened.photos.size)
    assertEquals(1, opened.audio.size)
    assertEquals(OutingEventType.DETECTION, opened.events.single().type)

    // Delete — the rows leave the journal and the files leave the disk.
    val paths = opened.media.map { it.filePath }
    repository.delete(id)
    assertNull(repository.findById(id))
    for (path in paths) assertFalse(mediaFileStore.exists(path))
    assertTrue(repository.journalStream().first().isEmpty())
    assertEquals(0, repository.lifeListCountStream().first())
  }

  @Test
  fun journalFlow_wizardEntryLandsInOneBreath() = runBlocking {
    // The exact draft `IdentifyWizardViewModel.saveSighting` builds on "This is my bird".
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.MANUAL,
                startedAt = 500L,
                events =
                    listOf(
                        PendingEvent.wizardAnswer(WizardTrait.SIZE, "2"),
                        PendingEvent.wizardAnswer(WizardTrait.COLORS, "BLACK,RED"),
                        PendingEvent.wizardAnswer(WizardTrait.BEHAVIOR, "IN_TREES_OR_BUSHES"),
                    ),
                sightings = listOf(PendingSighting(speciesId = "northern-cardinal")),
            ),
        )

    val opened = repository.findById(id)!!
    assertEquals(OutingKind.MANUAL, opened.outing.kind)
    // No clock: a wizard entry has no duration and nothing pinned to a timeline.
    assertNull(opened.outing.durationMs)
    assertTrue(opened.media.isEmpty())
    assertEquals("northern-cardinal", opened.sightings.single().speciesId)
    // Nothing detected this bird — the watcher named it — so there is no evidence to cite.
    assertNull(opened.sightings.single().sourceEventId)
    assertNull(opened.confidenceOf(opened.sightings.single()))

    val answers =
        opened.events
            .filter { it.type == OutingEventType.WIZARD_ANSWER }
            .sortedBy { it.trait!!.ordinal }
    assertEquals(listOf("2", "BLACK,RED", "IN_TREES_OR_BUSHES"), answers.map { it.value })

    assertEquals(1, repository.lifeListCountStream().first())
  }
}

/** Any fix will do where a test is not about location — an outing just cannot be without one. */
private val SOMEWHERE = CaptureLocation(latitude = 39.2098, longitude = -84.4699)
