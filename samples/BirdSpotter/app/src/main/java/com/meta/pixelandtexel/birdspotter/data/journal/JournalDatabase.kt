/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.journal

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema version for `journal.db`.
 *
 * **Kept in lockstep with the exported schema** — the same number, the same equivalent DDL, in the
 * same order. `catalog.db` has no migrations by design and is not managed here; it is replaced
 * wholesale on a seed bump.
 */
const val JOURNAL_DATABASE_VERSION = 5

/**
 * `journal.db` — created empty on first launch, migrated in place forever after, survives every app
 * update. The only user-written store in the app.
 *
 * Never add `fallbackToDestructiveMigration`: this file is the user's Journal, and dropping it to
 * dodge a migration throws their life list away. (The *catalog* is the disposable one, and it lives
 * in a separate file precisely so that stays true.)
 *
 * See the design notes.
 */
@Database(
    entities = [Outing::class, OutingMedia::class, OutingEvent::class, Sighting::class],
    version = JOURNAL_DATABASE_VERSION,
    exportSchema = true,
)
abstract class JournalDatabase : RoomDatabase() {

  abstract fun journalStore(): JournalStore

  companion object {
    const val FILE_NAME = "journal.db"

    /**
     * v1 → v2: the outing reshape (the design notes).
     *
     * Drop-and-recreate rather than a data-carrying migration, deliberately: no build has left this
     * machine pair, so the v1 one-row-per-flow schema has no installed base to carry — only
     * dev-device files. The "migrated in place forever" covenant starts at the first external
     * build; from then on it is additive migrations only.
     */
    private val MIGRATION_1_2 =
        object : Migration(1, 2) {
          override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `SightingMedia`")
            db.execSQL("DROP TABLE IF EXISTS `Sighting`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `Outing` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `durationMs` INTEGER, " +
                    "`latitude` REAL, `longitude` REAL, `placeName` TEXT, `notes` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_Outing_startedAt` ON `Outing` (`startedAt` DESC)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `OutingMedia` (`id` TEXT NOT NULL, `outingId` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, `source` TEXT NOT NULL, `offsetMs` INTEGER, " +
                    "`filePath` TEXT NOT NULL, `width` INTEGER, `height` INTEGER, `durationMs` INTEGER, " +
                    "`gazeContext` TEXT, `bearingDeg` REAL, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), FOREIGN KEY(`outingId`) REFERENCES `Outing`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_OutingMedia_outingId` ON `OutingMedia` (`outingId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `OutingEvent` (`id` TEXT NOT NULL, `outingId` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, `offsetMs` INTEGER, `speciesId` TEXT, `confidence` REAL, " +
                    "`question` TEXT, `answer` TEXT, `trait` TEXT, `value` TEXT, `gazeContext` TEXT, " +
                    "`bearingDeg` REAL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`outingId`) REFERENCES `Outing`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_OutingEvent_outingId` ON `OutingEvent` (`outingId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `Sighting` (`id` TEXT NOT NULL, `outingId` TEXT NOT NULL, " +
                    "`speciesId` TEXT NOT NULL, `confidence` REAL, `offsetMs` INTEGER, " +
                    "`gazeContext` TEXT, `bearingDeg` REAL, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), FOREIGN KEY(`outingId`) REFERENCES `Outing`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_Sighting_outingId` ON `Sighting` (`outingId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_Sighting_speciesId` ON `Sighting` (`speciesId`)",
            )
          }
        }

    /**
     * v2 → v3: `Outing.status` is gone (the design notes).
     *
     * An outing is now written only at Save, so every row in this table is a finished one and the
     * column had no second value left to hold. SQLite before 3.35 cannot `DROP COLUMN`, and the
     * Android floor here is below that, so this is the standard twelve-step rewrite: build the new
     * table, carry the rows worth carrying, swap.
     *
     * Any `DRAFT` row is dropped rather than promoted. Under the old model a draft was an outing
     * the user never kept — a crash, or a rehearsal — and inventing a Save they never performed
     * would put entries in their Journal that they did not make.
     */
    private val MIGRATION_2_3 =
        object : Migration(2, 3) {
          override fun migrate(db: SupportSQLiteDatabase) {
            // Children go by hand, not by cascade: Room runs migrations with
            // `foreign_keys` off so a table can be rewritten, which is exactly when
            // `ON DELETE CASCADE` stops firing.
            for (table in listOf("OutingMedia", "OutingEvent", "Sighting")) {
              db.execSQL(
                  "DELETE FROM `$table` WHERE `outingId` IN " +
                      "(SELECT `id` FROM `Outing` WHERE `status` = 'DRAFT')",
              )
            }
            db.execSQL("DELETE FROM `Outing` WHERE `status` = 'DRAFT'")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `Outing_new` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, `durationMs` INTEGER, " +
                    "`latitude` REAL, `longitude` REAL, `placeName` TEXT, `notes` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "INSERT INTO `Outing_new` (`id`, `kind`, `startedAt`, `durationMs`, `latitude`, " +
                    "`longitude`, `placeName`, `notes`, `createdAt`, `updatedAt`) " +
                    "SELECT `id`, `kind`, `startedAt`, `durationMs`, `latitude`, `longitude`, " +
                    "`placeName`, `notes`, `createdAt`, `updatedAt` FROM `Outing`",
            )
            db.execSQL("DROP TABLE `Outing`")
            db.execSQL("ALTER TABLE `Outing_new` RENAME TO `Outing`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_Outing_startedAt` ON `Outing` (`startedAt` DESC)",
            )
          }
        }

    /**
     * v3 → v4: `GazeContext` widens from three strata to five.
     *
     * `GROUND` and `CANOPY` keep their spellings and their meanings. `LEVEL` becomes `HORIZON` —
     * the same stratum under the name the live session's chip has always used for it — so stored
     * rows are rewritten rather than left holding a token the enum no longer has a case for.
     *
     * No column changes: gaze is TEXT, and widening an enum only adds spellings that were never
     * written before. `UNDERSTORY` and `OVERHEAD` therefore appear on new rows only, which is
     * honest — a moment recorded under the old three had no way to mean either one, and inferring
     * one now would be inventing precision.
     */
    private val MIGRATION_3_4 =
        object : Migration(3, 4) {
          override fun migrate(db: SupportSQLiteDatabase) {
            for (table in listOf("OutingMedia", "OutingEvent", "Sighting")) {
              db.execSQL(
                  "UPDATE `$table` SET `gazeContext` = 'HORIZON' WHERE `gazeContext` = 'LEVEL'",
              )
            }
          }
        }

    /**
     * v4 → v5: the evidence links, and location becomes a fact rather than a hope (the design
     * notes).
     *
     * `Outing` drops `placeName` and makes its coordinates NOT NULL — see the note in the body for
     * what that does to rows that never got a fix.
     *
     * `OutingEvent` gains `mediaId` — the photo a detection was made by looking at — and loses gaze
     * and bearing, which are facts about an aimed camera and belong to that photo. `Sighting` gains
     * `sourceEventId` and loses `confidence`, `offsetMs`, gaze and bearing for the same reason: it
     * now says who was confirmed and what backs it, and reads the rest through the link.
     *
     * Both tables are rewritten rather than altered — SQLite before 3.35 has no `DROP COLUMN` and
     * the Android floor is below that. `OutingEvent` goes first, so that `Sighting`'s new foreign
     * key has a table to point at.
     *
     * **Existing rows get null links.** Which photo a detection came from is new information;
     * picking the nearest by `offsetMs` would be manufacturing a provenance the app never observed.
     * The values dropped along the way are the ones this change exists to stop writing — an
     * unattributed `Sighting.confidence` most of all — and in practice the dev journals hold none,
     * since the only path that saves a sighting today is the wizard, which has no detection to copy
     * from.
     */
    private val MIGRATION_4_5 =
        object : Migration(4, 5) {
          override fun migrate(db: SupportSQLiteDatabase) {
            // `Outing` first: it loses `placeName`, which nothing ever wrote — the wizard
            // stopped asking *where* and no reverse geocode replaced the question — and
            // its coordinates become NOT NULL, because Identify is gated on location and
            // a flow that cannot get a fix now declines to save.
            //
            // **An outing with no fix is deleted, not backfilled.** It cannot be carried
            // into a NOT NULL column, and stamping it with a coordinate nobody recorded
            // would put a bird somewhere it was never seen — the same call v2 → v3 made
            // about `DRAFT` rows. Children go by hand, since migrations run with
            // `foreign_keys` off and `ON DELETE CASCADE` is not firing. Their media files
            // are left on disk: a migration has no filesystem, and `MediaFileStore` has no
            // sweep yet (the design notes, "Where a draft lives").
            for (table in listOf("OutingMedia", "OutingEvent", "Sighting")) {
              db.execSQL(
                  "DELETE FROM `$table` WHERE `outingId` IN (SELECT `id` FROM `Outing` " +
                      "WHERE `latitude` IS NULL OR `longitude` IS NULL)",
              )
            }
            db.execSQL("DELETE FROM `Outing` WHERE `latitude` IS NULL OR `longitude` IS NULL")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `Outing_new` (`id` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `durationMs` INTEGER, " +
                    "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `notes` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "INSERT INTO `Outing_new` (`id`, `kind`, `startedAt`, `durationMs`, " +
                    "`latitude`, `longitude`, `notes`, `createdAt`, `updatedAt`) " +
                    "SELECT `id`, `kind`, `startedAt`, `durationMs`, `latitude`, `longitude`, " +
                    "`notes`, `createdAt`, `updatedAt` FROM `Outing`",
            )
            db.execSQL("DROP TABLE `Outing`")
            db.execSQL("ALTER TABLE `Outing_new` RENAME TO `Outing`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_Outing_startedAt` ON `Outing` (`startedAt` DESC)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `OutingEvent_new` (`id` TEXT NOT NULL, " +
                    "`outingId` TEXT NOT NULL, `mediaId` TEXT, `type` TEXT NOT NULL, " +
                    "`offsetMs` INTEGER, `speciesId` TEXT, `confidence` REAL, `question` TEXT, " +
                    "`answer` TEXT, `trait` TEXT, `value` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`outingId`) REFERENCES `Outing`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`mediaId`) REFERENCES `OutingMedia`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL)",
            )
            db.execSQL(
                "INSERT INTO `OutingEvent_new` (`id`, `outingId`, `type`, `offsetMs`, " +
                    "`speciesId`, `confidence`, `question`, `answer`, `trait`, `value`, `createdAt`) " +
                    "SELECT `id`, `outingId`, `type`, `offsetMs`, `speciesId`, `confidence`, " +
                    "`question`, `answer`, `trait`, `value`, `createdAt` FROM `OutingEvent`",
            )
            db.execSQL("DROP TABLE `OutingEvent`")
            db.execSQL("ALTER TABLE `OutingEvent_new` RENAME TO `OutingEvent`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_OutingEvent_outingId` ON `OutingEvent` (`outingId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_OutingEvent_mediaId` ON `OutingEvent` (`mediaId`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `Sighting_new` (`id` TEXT NOT NULL, " +
                    "`outingId` TEXT NOT NULL, `sourceEventId` TEXT, `speciesId` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`outingId`) REFERENCES `Outing`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`sourceEventId`) REFERENCES `OutingEvent`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL)",
            )
            db.execSQL(
                "INSERT INTO `Sighting_new` (`id`, `outingId`, `speciesId`, `createdAt`) " +
                    "SELECT `id`, `outingId`, `speciesId`, `createdAt` FROM `Sighting`",
            )
            db.execSQL("DROP TABLE `Sighting`")
            db.execSQL("ALTER TABLE `Sighting_new` RENAME TO `Sighting`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_Sighting_outingId` ON `Sighting` (`outingId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_Sighting_speciesId` ON `Sighting` (`speciesId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_Sighting_sourceEventId` " +
                    "ON `Sighting` (`sourceEventId`)",
            )
          }
        }

    /**
     * Migrations, oldest first. Every entry added here needs its matching numbered step in the
     * exported schema.
     */
    val MIGRATIONS: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

    /**
     * Opens (creating on first call) the on-disk journal.
     *
     * WAL is explicit rather than left to Room's default, so the rule is written down:
     * capture-thread writes must not block Journal reads. Room turns on `PRAGMA foreign_keys`
     * itself, so the children's cascade is enforced.
     */
    fun open(context: Context): JournalDatabase =
        Room.databaseBuilder(context, JournalDatabase::class.java, FILE_NAME)
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(*MIGRATIONS)
            .build()

    /** Ephemeral journal for tests. Same schema, same migrations, no file. */
    fun openInMemory(context: Context): JournalDatabase =
        Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java)
            .addMigrations(*MIGRATIONS)
            .build()
  }
}
