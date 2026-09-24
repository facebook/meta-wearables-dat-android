/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.journal

import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.domain.JournalError
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The only implementation of [JournalRepository]: `journal.db` for rows, [MediaFileStore] for
 * bytes, and the ordering between them.
 *
 * [now] and [newId] are injected so tests can pin both. Everything else is the two data sources —
 * this class holds no state of its own.
 *
 * [newId] mints the outing's id and nothing else: every child arrived carrying the id it was born
 * with, because a detection points at its photo while both are still in memory. A sighting that
 * names an event the draft doesn't hold therefore fails the foreign key on insert, which is the
 * right noise for a caller that wired the draft up wrong.
 */
class LocalJournalRepository(
    private val store: JournalStore,
    private val mediaFileStore: MediaFileStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : JournalRepository {

  override fun journalStream(): Flow<List<OutingWithChildren>> = store.journalStream()

  override fun lifeListCountStream(): Flow<Int> = store.lifeListCountStream()

  override suspend fun findById(outingId: String): OutingWithChildren? = store.findById(outingId)

  override suspend fun saveOuting(draft: OutingDraft): String {
    val outingId = newId()
    val timestamp = now()

    // Bytes down first, so no row can ever point at a file that isn't there. Written
    // under the outing's own group, which is what makes delete a single sweep later.
    val written = mutableListOf<OutingMedia>()
    try {
      for (pending in draft.media) {
        // The draft minted this id when the capture happened, and anything that
        // points at this photo is already holding it — so it is written through
        // rather than replaced here.
        val filePath =
            mediaFileStore.write(
                pending.bytes,
                group = outingId,
                name = pending.id,
                fileExtension = pending.fileExtension,
            )
        written +=
            OutingMedia(
                id = pending.id,
                outingId = outingId,
                type = pending.type,
                source = pending.source,
                offsetMs = pending.offsetMs,
                filePath = filePath,
                width = pending.width,
                height = pending.height,
                durationMs = pending.durationMs,
                gazeContext = pending.moment.gazeContext,
                bearingDeg = pending.moment.bearingDeg?.let(::normalizeBearing),
                createdAt = timestamp,
            )
      }
    } catch (cause: Exception) {
      // Best-effort sweep of whatever landed before the failure: a save that did not
      // happen must not leave bytes behind that nothing will ever reference.
      runCatching { mediaFileStore.deleteGroup(outingId) }
      throw JournalError.MediaWriteFailed(outingId, cause)
    }

    // The strip, beside the audio it was measured from. Swallowed and given no row: it is
    // derived, and an outing that saved its recording but not its picture of it is a good
    // outing — the journal page recomputes, exactly as it did before this file existed.
    // Failing the save here would throw away a walk over a cache.
    draft.sonogram?.let { bytes ->
      runCatching {
        mediaFileStore.write(
            bytes,
            group = outingId,
            name = MediaFileStore.SonogramName,
            fileExtension = MediaFileStore.SonogramExtension,
        )
      }
    }

    val outing = Outing(
        id = outingId,
        kind = draft.kind,
        startedAt = draft.startedAt,
        durationMs = draft.durationMs,
        latitude = draft.location.latitude,
        longitude = draft.location.longitude,
        notes = draft.notes,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
    val events =
        draft.events.map { pending ->
          OutingEvent(
              id = pending.id,
              outingId = outingId,
              mediaId = pending.mediaId,
              type = pending.type,
              offsetMs = pending.offsetMs,
              speciesId = pending.speciesId,
              confidence = pending.confidence,
              question = pending.question,
              answer = pending.answer,
              trait = pending.trait,
              value = pending.value,
              createdAt = timestamp,
          )
        }
    val sightings =
        draft.sightings.map { pending ->
          Sighting(
              id = pending.id,
              outingId = outingId,
              sourceEventId = pending.confirming?.id,
              speciesId = pending.speciesId,
              createdAt = timestamp,
          )
        }

    try {
      store.insertOuting(outing, written, events, sightings)
    } catch (cause: Exception) {
      runCatching { mediaFileStore.deleteGroup(outingId) }
      throw JournalError.MediaWriteFailed(outingId, cause)
    }
    return outingId
  }

  override suspend fun updateNotes(outingId: String, notes: String?) {
    // Read, modify, write — with Outing.updatedAt bumped in code, never by a trigger.
    val current = store.findById(outingId)?.outing ?: throw JournalError.NotFound(outingId)
    store.update(current.copy(notes = notes, updatedAt = now()))
  }

  override suspend fun delete(outingId: String) {
    // Files first, then the row. Reversed, a crash between the two steps would strand
    // files on disk that nothing references and nothing will ever clean up.
    mediaFileStore.deleteGroup(outingId)
    store.deleteById(outingId)
  }

  override suspend fun deleteAll() {
    // Files first, then the rows — the same asymmetry, and one sweep of the media
    // directory rather than a group per outing, which also collects bytes from a
    // capture that never became a row.
    mediaFileStore.deleteAll()
    store.deleteAll()
  }
}

/**
 * Folds a heading into 0–360. Bearings are stored true north and normalized at write, so nothing
 * downstream has to wonder whether -90 means 270.
 */
private fun normalizeBearing(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
