/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import com.meta.pixelandtexel.birdspotter.data.journal.CaptureLocation
import com.meta.pixelandtexel.birdspotter.data.journal.Outing
import com.meta.pixelandtexel.birdspotter.data.journal.OutingDraft
import com.meta.pixelandtexel.birdspotter.data.journal.OutingWithChildren
import com.meta.pixelandtexel.birdspotter.data.journal.Sighting
import kotlinx.coroutines.flow.Flow

/** Failures callers are expected to handle. */
sealed class JournalError(message: String, cause: Throwable? = null) : Exception(message, cause) {

  class NotFound(val outingId: String) : JournalError("No outing with id $outingId")

  class MediaWriteFailed(val outingId: String, cause: Throwable) :
      JournalError("Could not attach media to outing $outingId", cause)

  /**
   * No fix, so no outing. Raised by the *caller* assembling a draft, never by the repository —
   * [CaptureLocation] is required, so a draft without coordinates cannot be built at all, and this
   * is what a screen throws instead of inventing them.
   */
  object NoLocationFix : JournalError("Cannot log an outing without knowing where it happened")
}

/**
 * The Journal — the app's only user-written store, and the only writer to `journal.db`.
 *
 * One [Outing] row is the root of everything a journal entry holds; media, timeline events, and
 * confirmed birds are children carrying its id. The Journal itself is a query — outings newest
 * first — and the life list is a count over [Sighting] rows, both derived, never stored.
 *
 * **An outing is written once, at Save.** While it is happening it is an [OutingDraft] held by the
 * screen running it — the wizard's three answers across three questions, a live session's
 * detections and segments across a walk — and it reaches this repository complete or not at all.
 * There is no half-written outing in the database, which is why there is no status column to
 * describe one and no query here that filters for it.
 *
 * Sits above two data sources it is responsible for keeping consistent: the database
 * (`JournalStore`) and the captured-media directory (`MediaFileStore`). Neither should be driven
 * directly from a ViewModel, because the ordering between them is not symmetric:
 *
 * - **Writing** puts the files down first, then the rows, so a row never points at a file that
 *   isn't there.
 * - **Deleting** removes the files first, then the row, for the same reason — a row pointing at a
 *   missing file is recoverable and visible; an orphaned file is neither.
 *
 * Timestamps are epoch milliseconds UTC; formatting is the UI's job. `offsetMs` values are
 * positions on the outing's timeline — ms from `startedAt` — and null where there is no clock,
 * which is every child of a `MANUAL` outing.
 *
 * See the design notes.
 */
interface JournalRepository {

  /** The Journal: every outing, newest first. */
  fun journalStream(): Flow<List<OutingWithChildren>>

  /** Distinct confirmed species across every outing. Derived on every read, never stored. */
  fun lifeListCountStream(): Flow<Int>

  suspend fun findById(outingId: String): OutingWithChildren?

  /**
   * Writes a whole outing — Save, birds or no birds — and returns its id.
   *
   * Files first, then every row in one transaction: a failure leaves the Journal exactly as it was,
   * with nothing partial to find and nothing to clean up. This is the only way an outing enters the
   * Journal.
   *
   * Bearings on the draft's moments must already be true north — they are normalized to 0–360 here
   * — and must come from the same device as their capture's `source`: never the phone's compass for
   * a glasses capture.
   *
   * Throws [JournalError.MediaWriteFailed] if the bytes cannot be stored.
   */
  suspend fun saveOuting(draft: OutingDraft): String

  /**
   * Edits the notes on an outing already in the Journal. Throws [JournalError.NotFound] for an
   * unknown id.
   */
  suspend fun updateNotes(outingId: String, notes: String?)

  /** Removes the files, then the row. Child rows cascade. Idempotent. */
  suspend fun delete(outingId: String)

  /**
   * Empties the Journal: every outing, every child row, every captured file.
   *
   * Same ordering as [delete] and for the same reason — the media directory is swept first, then
   * the table — but as one sweep rather than a loop over ids, so a failure part-way cannot leave
   * rows pointing at bytes that are already gone. Idempotent: an empty Journal deletes cleanly.
   */
  suspend fun deleteAll()
}
