/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

@file:Suppress("UseOfRunBlocking", "UseOfRunBlockingInTest")

package com.meta.pixelandtexel.birdspotter.data.journal

import android.database.sqlite.SQLiteDatabase
import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * `journal.db` carried forward from an older file, not created fresh.
 *
 * Every other suite opens an empty database, where Room builds the current schema straight from the
 * entities and no migration runs at all — which is why a broken migration can pass all of them and
 * still take down the app on the one device holding yesterday's file. This opens a real v4
 * database, with rows in it, and drives the real `MIGRATION_4_5`.
 *
 * The v4 DDL is transcribed from Room's own export (`app/schemas/…JournalDatabase/4.json`) rather
 * than retyped from memory, so "the database the previous build wrote" means precisely that.
 *
 * **Not mirrored yet.** A migrator that records applied steps by name in its own table, rather than
 * in `user_version`, needs that table seeded before an equivalent old file can be stood up — worth
 * settling deliberately before the twin is added.
 */
@RunWith(RobolectricTestRunner::class)
class JournalMigrationTest {

  private lateinit var dbFile: File
  private lateinit var mediaRoot: File
  private lateinit var mediaFileStore: MediaFileStore

  @Before
  fun setUp() {
    val context = RuntimeEnvironment.getApplication()
    dbFile = context.getDatabasePath(JournalDatabase.FILE_NAME)
    dbFile.parentFile?.mkdirs()
    dbFile.delete()
    mediaRoot = Files.createTempDirectory("birdspotter-migration").toFile()
    mediaFileStore = MediaFileStore(mediaRoot)
  }

  @After
  fun tearDown() {
    dbFile.delete()
    mediaRoot.deleteRecursively()
  }

  @Test
  fun v4ToV5_carriesOutingsThatHaveAFix() = runBlocking {
    writeV4Database {
      insertOuting(
          it,
          id = "kept",
          latitude = 39.2098,
          longitude = -84.4699,
          place = "Cedar Ridge Trail",
      )
      insertWizardAnswer(it, id = "kept-answer", outingId = "kept")
      insertSighting(it, id = "kept-bird", outingId = "kept", speciesId = "northern-cardinal")
    }

    val database = JournalDatabase.open(RuntimeEnvironment.getApplication())
    val opened = LocalJournalRepository(database.journalStore(), mediaFileStore).findById("kept")
    database.close()

    assertNotNull(opened)
    assertEquals(39.2098, opened!!.outing.latitude, 1e-9)
    assertEquals(-84.4699, opened.outing.longitude, 1e-9)
    // The link columns are new information; an old row has none and says so.
    assertNull(opened.sightings.single().sourceEventId)
    assertNull(opened.events.single().mediaId)
  }

  @Test
  fun v4ToV5_dropsOutingsWithNoFix() = runBlocking {
    writeV4Database {
      insertOuting(it, id = "kept", latitude = 39.2098, longitude = -84.4699)
      insertOuting(it, id = "nowhere", latitude = null, longitude = null)
      insertSighting(it, id = "nowhere-bird", outingId = "nowhere", speciesId = "blue-jay")
    }

    val database = JournalDatabase.open(RuntimeEnvironment.getApplication())
    val repository = LocalJournalRepository(database.journalStore(), mediaFileStore)
    val kept = repository.findById("kept")
    val gone = repository.findById("nowhere")
    database.close()

    assertNotNull(kept)
    assertNull(gone)
  }

  @Test
  fun v4ToV5_leavesTheJournalWritable() = runBlocking {
    // The bug this suite exists for: a migration that half-succeeds leaves a database that
    // opens and then refuses every write, which the wizard reports as "couldn't save".
    writeV4Database {
      insertOuting(it, id = "kept", latitude = 39.2098, longitude = -84.4699)
    }

    val database = JournalDatabase.open(RuntimeEnvironment.getApplication())
    val repository = LocalJournalRepository(database.journalStore(), mediaFileStore)
    val id =
        repository.saveOuting(
            OutingDraft(
                kind = OutingKind.MANUAL,
                startedAt = 500L,
                location = CaptureLocation(latitude = 39.1523, longitude = -84.3822),
                events = listOf(PendingEvent.wizardAnswer(WizardTrait.SIZE, "2")),
                sightings = listOf(PendingSighting(speciesId = "northern-cardinal")),
            ),
        )
    val saved = repository.findById(id)
    database.close()

    assertNotNull(saved)
    assertEquals("northern-cardinal", saved!!.sightings.single().speciesId)
    assertTrue(saved.outing.latitude in 39.0..40.0)
  }

  // ── The v4 file ────────────────────────────────────────────────────────

  /** Builds `journal.db` exactly as the v4 build left it, then hands it to [rows]. */
  private fun writeV4Database(rows: (SQLiteDatabase) -> Unit) {
    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    for (statement in V4_SCHEMA) db.execSQL(statement)
    rows(db)
    db.version = 4
    db.close()
  }

  private fun insertOuting(
      db: SQLiteDatabase,
      id: String,
      latitude: Double?,
      longitude: Double?,
      place: String? = null,
  ) =
      db.execSQL(
          "INSERT INTO `Outing` (`id`, `kind`, `startedAt`, `durationMs`, `latitude`, `longitude`, " +
              "`placeName`, `notes`, `createdAt`, `updatedAt`) VALUES (?, 'MANUAL', 500, NULL, ?, ?, ?, NULL, 500, 500)",
          arrayOf<Any?>(id, latitude, longitude, place),
      )

  private fun insertWizardAnswer(db: SQLiteDatabase, id: String, outingId: String) =
      db.execSQL(
          "INSERT INTO `OutingEvent` (`id`, `outingId`, `type`, `offsetMs`, `speciesId`, `confidence`, " +
              "`question`, `answer`, `trait`, `value`, `gazeContext`, `bearingDeg`, `createdAt`) " +
              "VALUES (?, ?, 'WIZARD_ANSWER', NULL, NULL, NULL, NULL, NULL, 'SIZE', '2', NULL, NULL, 500)",
          arrayOf(id, outingId),
      )

  private fun insertSighting(db: SQLiteDatabase, id: String, outingId: String, speciesId: String) =
      db.execSQL(
          "INSERT INTO `Sighting` (`id`, `outingId`, `speciesId`, `confidence`, `offsetMs`, " +
              "`gazeContext`, `bearingDeg`, `createdAt`) VALUES (?, ?, ?, NULL, NULL, NULL, NULL, 500)",
          arrayOf(id, outingId, speciesId),
      )
}

/** Room's exported v4 schema, verbatim. */
private val V4_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `Outing` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
        "`startedAt` INTEGER NOT NULL, `durationMs` INTEGER, `latitude` REAL, `longitude` REAL, " +
        "`placeName` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
        "PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_Outing_startedAt` ON `Outing` (`startedAt` DESC)",
    "CREATE TABLE IF NOT EXISTS `OutingMedia` (`id` TEXT NOT NULL, `outingId` TEXT NOT NULL, " +
        "`type` TEXT NOT NULL, `source` TEXT NOT NULL, `offsetMs` INTEGER, `filePath` TEXT NOT NULL, " +
        "`width` INTEGER, `height` INTEGER, `durationMs` INTEGER, `gazeContext` TEXT, `bearingDeg` REAL, " +
        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`outingId`) REFERENCES `Outing`(`id`) " +
        "ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_OutingMedia_outingId` ON `OutingMedia` (`outingId`)",
    "CREATE TABLE IF NOT EXISTS `OutingEvent` (`id` TEXT NOT NULL, `outingId` TEXT NOT NULL, " +
        "`type` TEXT NOT NULL, `offsetMs` INTEGER, `speciesId` TEXT, `confidence` REAL, `question` TEXT, " +
        "`answer` TEXT, `trait` TEXT, `value` TEXT, `gazeContext` TEXT, `bearingDeg` REAL, " +
        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`outingId`) REFERENCES `Outing`(`id`) " +
        "ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_OutingEvent_outingId` ON `OutingEvent` (`outingId`)",
    "CREATE TABLE IF NOT EXISTS `Sighting` (`id` TEXT NOT NULL, `outingId` TEXT NOT NULL, " +
        "`speciesId` TEXT NOT NULL, `confidence` REAL, `offsetMs` INTEGER, `gazeContext` TEXT, " +
        "`bearingDeg` REAL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
        "FOREIGN KEY(`outingId`) REFERENCES `Outing`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_Sighting_outingId` ON `Sighting` (`outingId`)",
    "CREATE INDEX IF NOT EXISTS `index_Sighting_speciesId` ON `Sighting` (`speciesId`)",
)
