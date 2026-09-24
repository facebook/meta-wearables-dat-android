/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

@file:Suppress("UseOfRunBlocking", "UseOfRunBlockingInTest")

package com.meta.pixelandtexel.birdspotter.data.journal

import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.domain.JournalError
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The journal storage stack end to end: `journal.db`, the media directory, and the ordering the
 * repository imposes between them — against the outing model.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
@RunWith(RobolectricTestRunner::class)
class JournalRepositoryTest {

  private lateinit var database: JournalDatabase
  private lateinit var mediaRoot: File
  private lateinit var mediaFileStore: MediaFileStore
  private lateinit var repository: JournalRepository

  /** Pinned so `createdAt`/`updatedAt` assertions are exact rather than approximate. */
  private var clock = 1_000L
  private var idCounter = 0

  @Before
  fun setUp() {
    database = JournalDatabase.openInMemory(RuntimeEnvironment.getApplication())
    mediaRoot = Files.createTempDirectory("birdspotter-media").toFile()
    mediaFileStore = MediaFileStore(mediaRoot)
    repository =
        LocalJournalRepository(
            store = database.journalStore(),
            mediaFileStore = mediaFileStore,
            now = { clock },
            newId = { "id-${++idCounter}" },
        )
  }

  @After
  fun tearDown() {
    database.close()
    mediaRoot.deleteRecursively()
  }

  @Test
  fun saveOuting_putsItStraightIntoTheJournal() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(location = SOMEWHERE, kind = OutingKind.LIVE, startedAt = 500L),
        )

    assertEquals(listOf(id), repository.journalStream().first().map { it.outing.id })
  }

  @Test
  fun saveOuting_withNothingConfirmed_savesAnyway() = runBlocking {
    // The Merlin case: a walk that heard no bird is still an outing worth keeping.
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                durationMs = 840_000L,
            ),
        )

    assertEquals(listOf(id), repository.journalStream().first().map { it.outing.id })
    assertTrue(repository.findById(id)!!.sightings.isEmpty())
    assertEquals(0, repository.lifeListCountStream().first())
  }

  @Test
  fun saveOuting_persistsConfirmedSightings() = runBlocking {
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
                events = listOf(heard),
                sightings =
                    listOf(
                        PendingSighting(speciesId = "american-robin", confirming = heard),
                    ),
            ),
        )

    val saved = repository.findById(id)!!
    val confirmed = saved.sightings.single()
    assertEquals("american-robin", confirmed.speciesId)
    // The moment and the score are read down the link, not off the row.
    assertEquals(heard.id, confirmed.sourceEventId)
    assertEquals(12_000L, saved.offsetOf(confirmed))
    assertEquals(0.87, saved.confidenceOf(confirmed)!!, 1e-9)
  }

  @Test
  fun saveOuting_withoutADetection_leavesTheSightingUnbacked() = runBlocking {
    // The wizard's shape: a bird named by answering questions, with nothing to cite.
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.MANUAL,
                startedAt = 500L,
                sightings = listOf(PendingSighting(speciesId = "northern-cardinal")),
            ),
        )

    val saved = repository.findById(id)!!
    val confirmed = saved.sightings.single()
    assertNull(confirmed.sourceEventId)
    assertNull(saved.offsetOf(confirmed))
    assertNull(saved.confidenceOf(confirmed))
  }

  @Test
  fun saveOuting_confidenceIsNullWhenTheWatcherNamedAnotherBird() = runBlocking {
    // The detector proposed a crow; the watcher looked at the photo and said Fish Crow.
    // 0.91 scored the label they rejected, so it is not this bird's confidence.
    val photo = PendingMedia(
        type = OutingMediaType.PHOTO,
        source = CaptureSource.GLASSES,
        bytes = byteArrayOf(0x1),
        fileExtension = "heic",
        offsetMs = 8_000L,
    )
    val proposed =
        PendingEvent.seen(
            speciesId = "american-crow",
            confidence = 0.91,
            inPhoto = photo,
        )
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                media = listOf(photo),
                events = listOf(proposed),
                sightings = listOf(PendingSighting(speciesId = "fish-crow", confirming = proposed)),
            ),
        )

    val saved = repository.findById(id)!!
    val confirmed = saved.sightings.single()
    assertNull(saved.confidenceOf(confirmed))
    // The link still holds, so the moment behind the confirmation is still readable.
    assertEquals(8_000L, saved.offsetOf(confirmed))
  }

  @Test
  fun saveOuting_normalizesBearingToTrueNorthRange() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                media =
                    listOf(
                        PendingMedia(
                            type = OutingMediaType.PHOTO,
                            source = CaptureSource.PHONE,
                            bytes = byteArrayOf(0x1),
                            fileExtension = "jpg",
                            offsetMs = 4_000L,
                            moment = MomentContext(bearingDeg = -90.0),
                        ),
                    ),
            ),
        )

    assertEquals(270.0, repository.findById(id)!!.media.single().bearingDeg!!, 1e-9)
  }

  @Test
  fun saveOuting_writesPhotoFileThenRow() = runBlocking {
    val bytes = byteArrayOf(0x1, 0x2, 0x3, 0x4)

    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                media =
                    listOf(
                        PendingMedia(
                            type = OutingMediaType.PHOTO,
                            source = CaptureSource.GLASSES,
                            bytes = bytes,
                            fileExtension = "heic",
                            offsetMs = 42_000L,
                            width = 4032,
                            height = 3024,
                            moment = MomentContext(gazeContext = GazeContext.CANOPY),
                        ),
                    ),
            ),
        )

    val media = repository.findById(id)!!.media.single()
    assertEquals("$id/${media.id}.heic", media.filePath)
    assertTrue(mediaFileStore.exists(media.filePath))
    assertArrayEquals(bytes, mediaFileStore.resolve(media.filePath).readBytes())
    assertEquals(42_000L, media.offsetMs)
    assertEquals(CaptureSource.GLASSES, media.source)
    assertEquals(GazeContext.CANOPY, media.gazeContext)
  }

  @Test
  fun saveOuting_recordsAudioDurationAndNoDimensions() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                media =
                    listOf(
                        PendingMedia(
                            type = OutingMediaType.AUDIO,
                            source = CaptureSource.GLASSES,
                            bytes = byteArrayOf(0x9),
                            fileExtension = "wav",
                            offsetMs = 5_000L,
                            durationMs = 10_000L,
                        ),
                    ),
            ),
        )

    val media = repository.findById(id)!!.media.single()
    assertEquals(OutingMediaType.AUDIO, media.type)
    assertEquals(10_000L, media.durationMs)
    assertNull(media.width)
    assertNull(media.height)
  }

  @Test
  fun saveOuting_keepsAudioSegmentsAsSeparateRows() = runBlocking {
    // Two microphones across one unbroken session — the handover is the two rows.
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                media =
                    listOf(
                        PendingMedia(
                            type = OutingMediaType.AUDIO,
                            source = CaptureSource.GLASSES,
                            bytes = byteArrayOf(0x1),
                            fileExtension = "wav",
                            offsetMs = 0L,
                            durationMs = 30_000L,
                        ),
                        PendingMedia(
                            type = OutingMediaType.AUDIO,
                            source = CaptureSource.PHONE,
                            bytes = byteArrayOf(0x2),
                            fileExtension = "wav",
                            offsetMs = 30_000L,
                            durationMs = 15_000L,
                        ),
                    ),
            ),
        )

    val segments = repository.findById(id)!!.media.sortedBy { it.offsetMs }
    assertEquals(
        listOf(CaptureSource.GLASSES, CaptureSource.PHONE),
        segments.map { it.source },
    )
    // Distinct files, so one row can never hold two microphones.
    assertEquals(2, segments.map { it.filePath }.toSet().size)
  }

  @Test
  fun saveOuting_whenMediaWriteFails_leavesNothingBehind() = runBlocking {
    // A root that is a file, not a directory: every write under it fails.
    val blocked = File(mediaRoot, "blocked").also { it.writeBytes(byteArrayOf(0x0)) }
    val failing = LocalJournalRepository(
        store = database.journalStore(),
        mediaFileStore = MediaFileStore(blocked),
        now = { clock },
        newId = { "id-${++idCounter}" },
    )

    assertThrows(JournalError.MediaWriteFailed::class.java) {
      runBlocking {
        failing.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                media =
                    listOf(
                        PendingMedia(
                            type = OutingMediaType.PHOTO,
                            source = CaptureSource.PHONE,
                            bytes = byteArrayOf(0x1),
                            fileExtension = "jpg",
                        ),
                    ),
            ),
        )
      }
    }

    // No row, and nothing half-written: the Journal is exactly as it was.
    assertTrue(repository.journalStream().first().isEmpty())
  }

  @Test
  fun saveOuting_persistsWizardAnswers() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.MANUAL,
                startedAt = 500L,
                events =
                    listOf(
                        PendingEvent.wizardAnswer(WizardTrait.SIZE, "4"),
                        PendingEvent.wizardAnswer(WizardTrait.COLORS, "BLACK,RED"),
                        PendingEvent.wizardAnswer(WizardTrait.BEHAVIOR, "AT_FEEDER"),
                    ),
            ),
        )

    val answers =
        repository
            .findById(id)!!
            .events
            .filter { it.type == OutingEventType.WIZARD_ANSWER }
            .sortedBy { it.trait!!.ordinal }
    assertEquals(
        listOf(WizardTrait.SIZE, WizardTrait.COLORS, WizardTrait.BEHAVIOR),
        answers.map { it.trait },
    )
    assertEquals(listOf("4", "BLACK,RED", "AT_FEEDER"), answers.map { it.value })
    // A wizard answer has no clock to pin to.
    assertTrue(answers.all { it.offsetMs == null })
  }

  @Test
  fun saveOuting_persistsAHeardDetectionAgainstTheClock() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                events =
                    listOf(
                        PendingEvent.heard(
                            speciesId = "american-robin",
                            confidence = 0.87,
                            offsetMs = 21_000L,
                        ),
                    ),
            ),
        )

    val saved = repository.findById(id)!!
    val event = saved.events.single()
    assertEquals(OutingEventType.DETECTION, event.type)
    assertEquals("american-robin", event.speciesId)
    assertEquals(0.87, event.confidence!!, 1e-9)
    assertEquals(21_000L, event.offsetMs)
    // Nothing aimed a camera, so there is no photo to reach and no aim to report.
    assertNull(event.mediaId)
    assertNull(saved.momentOf(event).gazeContext)
    assertNull(saved.momentOf(event).bearingDeg)
  }

  @Test
  fun saveOuting_pinsASeenDetectionToItsPhotoRatherThanTheClock() = runBlocking {
    val photo = PendingMedia(
        type = OutingMediaType.PHOTO,
        source = CaptureSource.GLASSES,
        bytes = byteArrayOf(0x1),
        fileExtension = "heic",
        offsetMs = 21_000L,
        moment = MomentContext(gazeContext = GazeContext.CANOPY, bearingDeg = 292.0),
    )
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                media = listOf(photo),
                events =
                    listOf(
                        PendingEvent.seen(
                            speciesId = "american-robin",
                            confidence = 0.87,
                            inPhoto = photo,
                        ),
                    ),
            ),
        )

    val saved = repository.findById(id)!!
    val event = saved.events.single()
    assertEquals(photo.id, event.mediaId)
    // The photo owns the moment; the event stores no copy of any of it.
    assertNull(event.offsetMs)
    assertEquals(21_000L, saved.offsetOf(event))
    assertEquals(GazeContext.CANOPY, saved.momentOf(event).gazeContext)
    assertEquals(292.0, saved.momentOf(event).bearingDeg!!, 1e-9)
  }

  @Test
  fun saveOuting_persistsExchangeQuestionAndAnswer() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                events =
                    listOf(
                        PendingEvent.exchange(
                            question = "It's green with a yellow belly",
                            answer = "That's likely a Green Jay",
                            offsetMs = 65_000L,
                        ),
                    ),
            ),
        )

    val event = repository.findById(id)!!.events.single()
    assertEquals(OutingEventType.QA, event.type)
    assertEquals("It's green with a yellow belly", event.question)
    assertEquals("That's likely a Green Jay", event.answer)
  }

  @Test
  fun saveOuting_stampsTheDurationItWasGiven() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                durationMs = 90_000L,
            ),
        )

    assertEquals(90_000L, repository.findById(id)!!.outing.durationMs)
  }

  @Test
  fun saveOuting_leavesManualDurationNull() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.MANUAL,
                startedAt = 500L,
                events = listOf(PendingEvent.wizardAnswer(WizardTrait.SIZE, "2")),
                sightings = listOf(PendingSighting(speciesId = "northern-cardinal")),
            ),
        )

    assertNull(repository.findById(id)!!.outing.durationMs)
  }

  @Test
  fun saveOuting_stampsTimestampsWithoutTouchingStartedAt() = runBlocking {
    clock = 2_000L

    val id =
        repository.saveOuting(
            OutingDraft(location = SOMEWHERE, kind = OutingKind.LIVE, startedAt = 500L),
        )

    val saved = repository.findById(id)!!.outing
    // startedAt is capture time and may predate createdAt when a transfer lags.
    assertEquals(500L, saved.startedAt)
    assertEquals(2_000L, saved.createdAt)
    assertEquals(2_000L, saved.updatedAt)
  }

  @Test
  fun updateNotes_bumpsUpdatedAtButNotStartedAt() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(location = SOMEWHERE, kind = OutingKind.LIVE, startedAt = 500L),
        )

    clock = 2_000L
    repository.updateNotes(id, "Loud dawn chorus by the creek")

    val updated = repository.findById(id)!!.outing
    assertEquals("Loud dawn chorus by the creek", updated.notes)
    assertEquals(500L, updated.startedAt)
    assertEquals(1_000L, updated.createdAt)
    assertEquals(2_000L, updated.updatedAt)
  }

  @Test
  fun updateNotes_whenOutingUnknown_throwsNotFound() {
    assertThrows(JournalError.NotFound::class.java) {
      runBlocking { repository.updateNotes("no-such-outing", "anything") }
    }
  }

  @Test
  fun delete_removesFilesAndRow() = runBlocking {
    val id =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                media =
                    listOf(
                        PendingMedia(
                            type = OutingMediaType.PHOTO,
                            source = CaptureSource.GLASSES,
                            bytes = byteArrayOf(0x1),
                            fileExtension = "jpg",
                            offsetMs = 1_000L,
                            width = 100,
                            height = 100,
                        ),
                        PendingMedia(
                            type = OutingMediaType.AUDIO,
                            source = CaptureSource.GLASSES,
                            bytes = byteArrayOf(0x2),
                            fileExtension = "wav",
                            offsetMs = 0L,
                            durationMs = 3_000L,
                        ),
                    ),
                sightings = listOf(PendingSighting(speciesId = "blue-jay")),
            ),
        )
    val paths = repository.findById(id)!!.media.map { it.filePath }
    assertNotNull(repository.findById(id))

    repository.delete(id)

    assertNull(repository.findById(id))
    for (path in paths) assertFalse(mediaFileStore.exists(path))
    assertFalse(File(mediaRoot, id).exists())
    assertTrue(repository.journalStream().first().isEmpty())
    assertEquals(0, repository.lifeListCountStream().first())
  }

  @Test
  fun deleteAll_emptiesTheJournalAndItsMediaDirectory() = runBlocking {
    // Settings' one destructive control: the whole Journal, not an entry of it.
    val withPhoto =
        repository.saveOuting(
            OutingDraft(
                location = SOMEWHERE,
                kind = OutingKind.LIVE,
                startedAt = 500L,
                media =
                    listOf(
                        PendingMedia(
                            type = OutingMediaType.PHOTO,
                            source = CaptureSource.GLASSES,
                            bytes = byteArrayOf(0x1),
                            fileExtension = "jpg",
                            offsetMs = 1_000L,
                            width = 100,
                            height = 100,
                        ),
                    ),
                sightings = listOf(PendingSighting(speciesId = "blue-jay")),
            ),
        )
    savedOuting(startedAt = 900L, speciesId = "northern-cardinal")
    val paths = repository.findById(withPhoto)!!.media.map { it.filePath }

    repository.deleteAll()

    assertTrue(repository.journalStream().first().isEmpty())
    assertEquals(0, repository.lifeListCountStream().first())
    for (path in paths) assertFalse(mediaFileStore.exists(path))
    // Not one group swept but the whole directory — bytes from a capture that never
    // became a row have no id to look them up by, and this is what collects them.
    assertEquals(0, mediaRoot.listFiles()!!.size)
  }

  @Test
  fun deleteAll_onAnEmptyJournal_succeeds() = runBlocking {
    // The second confirmed tap, and the first one on a fresh install.
    repository.deleteAll()

    assertTrue(repository.journalStream().first().isEmpty())
  }

  @Test
  fun journal_ordersByStartedAtNewestFirst() = runBlocking {
    val older = savedOuting(startedAt = 100L)
    val newer = savedOuting(startedAt = 900L)

    assertEquals(
        listOf(newer, older),
        repository.journalStream().first().map { it.outing.id },
    )
  }

  @Test
  fun lifeListCount_countsDistinctSpecies_ignoringBirdless() = runBlocking {
    savedOuting(startedAt = 100L, speciesId = "northern-cardinal")
    savedOuting(startedAt = 200L, speciesId = "northern-cardinal")
    savedOuting(startedAt = 300L, speciesId = "blue-jay")
    // A birdless outing saves, but confirms nothing toward the life list.
    savedOuting(startedAt = 400L)

    assertEquals(2, repository.lifeListCountStream().first())
  }

  /**
   * The other tests run against an in-memory database. This one uses the real file, so the on-disk
   * path — WAL, and a migration replayed against an already-migrated file — is covered rather than
   * assumed.
   */
  @Test
  fun open_onDisk_createsSchemaAndSurvivesReopen() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    context.getDatabasePath(JournalDatabase.FILE_NAME).delete()

    val first = JournalDatabase.open(context)
    val id =
        LocalJournalRepository(first.journalStore(), mediaFileStore)
            .saveOuting(
                OutingDraft(
                    location = SOMEWHERE,
                    kind = OutingKind.MANUAL,
                    startedAt = 500L,
                    events = listOf(PendingEvent.wizardAnswer(WizardTrait.SIZE, "2")),
                    sightings = listOf(PendingSighting(speciesId = "northern-cardinal")),
                ),
            )
    first.close()

    val second = JournalDatabase.open(context)
    val reopened = LocalJournalRepository(second.journalStore(), mediaFileStore).findById(id)
    second.close()

    assertNotNull(reopened)
    assertEquals(OutingKind.MANUAL, reopened!!.outing.kind)
    assertEquals("northern-cardinal", reopened.sightings.single().speciesId)
    assertEquals("2", reopened.events.single().value)
  }

  private suspend fun savedOuting(startedAt: Long, speciesId: String? = null): String =
      repository.saveOuting(
          OutingDraft(
              location = SOMEWHERE,
              kind = OutingKind.LIVE,
              startedAt = startedAt,
              sightings = speciesId?.let { listOf(PendingSighting(speciesId = it)) } ?: emptyList(),
          ),
      )
}

/** Any fix will do where a test is not about location — an outing just cannot be without one. */
private val SOMEWHERE = CaptureLocation(latitude = 39.2098, longitude = -84.4699)
