/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Every query against `journal.db`. Rows only — this layer never touches the filesystem; pairing a
 * row with its file is [com.meta.pixelandtexel.birdspotter.data.journal.LocalJournalRepository]'s
 * job.
 */
@Dao
interface JournalStore {

  /**
   * The Journal: every outing, newest first.
   *
   * Unfiltered on purpose. An outing reaches this table only at Save, so there is no such thing as
   * an unfinished row to exclude.
   */
  @Transaction
  @Query(
      """
        SELECT * FROM Outing
        ORDER BY startedAt DESC
        """,
  )
  fun journalStream(): Flow<List<OutingWithChildren>>

  /** "12 species spotted" — derived on every read, never stored. */
  @Query("SELECT COUNT(DISTINCT speciesId) FROM Sighting") fun lifeListCountStream(): Flow<Int>

  @Transaction
  @Query("SELECT * FROM Outing WHERE id = :id")
  suspend fun findById(id: String): OutingWithChildren?

  @Insert suspend fun insert(outing: Outing)

  @Update suspend fun update(outing: Outing)

  @Insert suspend fun insertMedia(media: List<OutingMedia>)

  @Insert suspend fun insertEvent(events: List<OutingEvent>)

  @Insert suspend fun insertSighting(sightings: List<Sighting>)

  /**
   * A whole outing in one transaction — the only way an outing enters the Journal.
   *
   * Atomic because a half-written outing is exactly what dropping the status column removed the
   * ability to describe: with no `DRAFT` to park it in, a partial insert would surface in the
   * Journal as a real entry missing its birds. Either the entry exists complete or it was never
   * here.
   */
  @Transaction
  suspend fun insertOuting(
      outing: Outing,
      media: List<OutingMedia>,
      events: List<OutingEvent>,
      sightings: List<Sighting>,
  ) {
    insert(outing)
    insertMedia(media)
    insertEvent(events)
    insertSighting(sightings)
  }

  /** Child rows go with it via `ON DELETE CASCADE`; the files do not. */
  @Query("DELETE FROM Outing WHERE id = :id") suspend fun deleteById(id: String)

  /** Every outing at once. Children cascade with them; the files do not. */
  @Query("DELETE FROM Outing") suspend fun deleteAll()
}
