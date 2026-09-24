/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.journal

import androidx.room.Embedded
import androidx.room.Relation

/**
 * An outing and everything that landed on it — what the Journal renders.
 *
 * [media] is empty for an outing that captured nothing, or whose only transfer failed — a wizard
 * entry is always this. [sightings] is empty for an outing that confirmed nothing — the Merlin "0
 * birds, saved anyway" case, which is a first-class journal entry, not a failure.
 */
data class OutingWithChildren(
    @Embedded val outing: Outing,
    @Relation(parentColumn = "id", entityColumn = "outingId")
    val media: List<OutingMedia> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "outingId")
    val events: List<OutingEvent> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "outingId")
    val sightings: List<Sighting> = emptyList(),
) {
  val photos: List<OutingMedia>
    get() = media.filter { it.type == OutingMediaType.PHOTO }

  val audio: List<OutingMedia>
    get() = media.filter { it.type == OutingMediaType.AUDIO }

  // ── Following the evidence links ────────────────────────────────────────
  //
  // A moment is stored once, on the row that recorded it, and everything downstream
  // reads through — see the design notes, "What owns a
  // moment". These are that read, in one place, so no screen walks the links by hand.
  // Lookups are `first { }` rather than maps: an outing's children are two digits.

  /** The capture an event was produced from, or null when it came from the clock alone. */
  fun mediaFor(event: OutingEvent): OutingMedia? =
      event.mediaId?.let { id -> media.firstOrNull { it.id == id } }

  /** The detection a bird was confirmed from, or null for a wizard entry. */
  fun sourceEventOf(sighting: Sighting): OutingEvent? =
      sighting.sourceEventId?.let { id -> events.firstOrNull { it.id == id } }

  /** Where an event sits on the timeline — its own reading, or its photo's. */
  fun offsetOf(event: OutingEvent): Long? = event.offsetMs ?: mediaFor(event)?.offsetMs

  /** Where a confirmed bird sits on the timeline. Null for an outing with no clock. */
  fun offsetOf(sighting: Sighting): Long? = sourceEventOf(sighting)?.let { offsetOf(it) }

  /** Where the camera was aimed for an event — empty unless a photo is behind it. */
  fun momentOf(event: OutingEvent): MomentContext =
      mediaFor(event)?.let {
        MomentContext(gazeContext = it.gazeContext, bearingDeg = it.bearingDeg)
      } ?: MomentContext()

  /** Where the camera was aimed for a confirmed bird. Empty for anything heard. */
  fun momentOf(sighting: Sighting): MomentContext =
      sourceEventOf(sighting)?.let { momentOf(it) } ?: MomentContext()

  /**
   * How confident the detector was **in this bird** — null unless the watcher confirmed the species
   * the detection actually proposed.
   *
   * A watcher can look at the photo behind an *American Crow, 0.91* and name a Fish Crow. That 0.91
   * scored the label they rejected, so returning it here would put a number against a bird nothing
   * ever scored. This is why the column is not on [Sighting]: a copy taken at confirmation time
   * cannot tell the two cases apart afterwards.
   */
  fun confidenceOf(sighting: Sighting): Double? =
      sourceEventOf(sighting)?.takeIf { it.speciesId == sighting.speciesId }?.confidence
}
