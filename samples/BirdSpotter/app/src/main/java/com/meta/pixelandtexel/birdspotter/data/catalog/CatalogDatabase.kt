/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import java.io.File

/**
 * Schema version for `catalog.db`.
 *
 * Unrelated to the *seed* version. This number describes the shape of the tables and changes when a
 * column does; [EXPECTED_SEED_VERSION] describes the content inside them and changes whenever a
 * bird does. There are no migrations either way — the file is replaced wholesale, which is the
 * entire reason the catalog lives apart from
 * [com.meta.pixelandtexel.birdspotter.data.journal.JournalDatabase].
 */
const val CATALOG_DATABASE_VERSION = 6

/**
 * `catalog.db` — the shipped field guide. Copied out of `assets/` on first launch, read-only
 * forever after, and thrown away without ceremony whenever the bundled seed is newer than the
 * installed one.
 *
 * **Room's exported schema for this class is the authoritative DDL for the seed pipeline.** The
 * seed pipeline reads `app/schemas/**/*CatalogDatabase/<version>.json` and builds the shipped file
 * from it, identity hash and all, because [RoomDatabase.Builder.createFromAsset] validates a
 * prepackaged database's schema *exactly* and rejects hand-written DDL at first launch. Change an
 * `@Entity` here and the next Android build regenerates and restages the asset; nothing else needs
 * touching.
 *
 * See the design notes.
 */
@Database(
    entities =
        [
            Species::class,
            SpeciesMedia::class,
            SpeciesColor::class,
            SpeciesBehavior::class,
            AppMeta::class,
        ],
    version = CATALOG_DATABASE_VERSION,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {

  abstract fun speciesStore(): SpeciesStore

  companion object {

    const val FILE_NAME = "catalog.db"
    const val ASSET_NAME = "catalog.db"

    /** The `AppMeta` row the replacement check turns on. */
    const val SEED_VERSION_KEY = "seed.version"

    /**
     * The seed version this build of the app ships.
     *
     * Must equal `seed.version` in the seed metadata; bump both together whenever catalog content
     * changes, or installs will keep serving the stale copy they already have.
     * `CatalogDatabaseTest` fails loudly if they drift.
     */
    const val EXPECTED_SEED_VERSION = 6

    /**
     * Opens the catalog, replacing an installed copy that the bundle supersedes.
     *
     * The staleness check is written by hand rather than delegated to
     * `fallbackToDestructiveMigration()`: the rule is small, and one written rule beats a framework
     * behaviour that only looks like one.
     */
    fun open(context: Context): CatalogDatabase {
      deleteIfSuperseded(context)
      return Room.databaseBuilder(context, CatalogDatabase::class.java, FILE_NAME)
          .createFromAsset(ASSET_NAME)
          .build()
    }

    /**
     * Drops the installed file when its seed predates [EXPECTED_SEED_VERSION], so the builder's
     * `createFromAsset` copies the bundled one in its place.
     *
     * Read with a bare [SQLiteDatabase] rather than through Room: an installed file old enough to
     * have a different *schema* would fail Room's validation before it could be asked what version
     * it is, turning a routine content bump into a crash loop. Anything unreadable is treated as
     * stale for the same reason — a catalog we can't interrogate is one we should replace, and
     * there is nothing in it worth preserving.
     */
    private fun deleteIfSuperseded(context: Context) {
      val installed = context.getDatabasePath(FILE_NAME)
      if (!installed.exists()) return

      val version = installedSeedVersion(installed)
      if (version == EXPECTED_SEED_VERSION) return

      BirdLog.info(LogCategory.CATALOG) {
        "replacing catalog seed v$version with bundled v$EXPECTED_SEED_VERSION"
      }
      // The sidecars belong to the file being dropped; leaving a -wal behind would
      // let SQLite replay it over the freshly copied catalog.
      listOf(installed, File("${installed.path}-wal"), File("${installed.path}-shm"))
          .filter { it.exists() }
          .forEach {
            if (!it.delete()) {
              BirdLog.warning(LogCategory.CATALOG) { "could not delete ${it.name}" }
            }
          }
    }

    /** The installed file's `seed.version`, or null if it cannot be read. */
    private fun installedSeedVersion(file: File): Int? =
        try {
          SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                    "SELECT value FROM AppMeta WHERE key = ?",
                    arrayOf(SEED_VERSION_KEY),
                )
                .use { cursor ->
                  if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() else null
                }
          }
        } catch (e: SQLiteException) {
          BirdLog.warning(LogCategory.CATALOG) {
            "installed catalog is unreadable; treating as stale — $e"
          }
          null
        }
  }
}
