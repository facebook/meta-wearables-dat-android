/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.data.journal.Outing
import com.meta.pixelandtexel.birdspotter.data.journal.OutingMedia
import com.meta.pixelandtexel.birdspotter.data.journal.OutingWithChildren
import com.meta.pixelandtexel.birdspotter.data.journal.Sighting
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import com.meta.pixelandtexel.birdspotter.domain.SightingLocation

/**
 * A confirmed bird paired with its catalog entry.
 *
 * [species] is null when the sighting's `speciesId` no longer resolves — legitimate, per
 * `JournalRepository`, and rendered as unlabeled rather than as an error. Labeling walks past an
 * unresolvable bird the way it walks past an outing that confirmed nothing.
 */
data class ConfirmedBird(
    val sighting: Sighting,
    /** The resolved catalog bird, or null when the id no longer resolves. */
    val species: SpeciesWithMedia?,
)

/**
 * One line of the Journal: an outing joined with the birds it confirmed.
 *
 * The store hands up an [OutingWithChildren] — the root row and everything that landed on it — but
 * not the birds, because `speciesId` points across into `catalog.db` and the two files don't join
 * in SQL. [JournalViewModel] resolves each confirmed sighting against the catalog and pairs them
 * here, so a row or the detail screen has everything it draws in one value.
 *
 * [birds] is in story order — earliest confirmed first — which is what makes [primaryBird] "the
 * outing's first trophy" rather than an arbitrary row. Empty is the Merlin case: an outing saved
 * with nothing confirmed.
 */
data class JournalEntry(
    val withChildren: OutingWithChildren,
    /** Confirmed birds in story order, resolved against the catalog. */
    val birds: List<ConfirmedBird>,
) {
  val id: String
    get() = withChildren.outing.id

  /** The stored root — `startedAt`, `kind`, the coordinates, `notes`, and the rest. */
  val outing: Outing
    get() = withChildren.outing

  /** The files the outing actually left behind. Empty for a `MANUAL` entry. */
  val photos: List<OutingMedia>
    get() = withChildren.photos

  val audio: List<OutingMedia>
    get() = withChildren.audio

  /**
   * The bird that names this entry — the earliest confirmed one the catalog still resolves. Null
   * when nothing was confirmed (or nothing resolves), in which case the entry is named by its date.
   */
  val primaryBird: ConfirmedBird?
    get() = birds.firstOrNull { it.species != null }

  /** How many confirmed birds ride behind [primaryBird] — the "+ n more" count. */
  val extraBirdCount: Int
    get() = primaryBird?.let { primary -> birds.count { it.species != null } - 1 } ?: 0

  /**
   * The pin this entry drops on a map. Never null: an outing knows where it happened or it was
   * never saved. The title is the primary bird, or a plain word when nothing resolved — the entry
   * is still a place the watcher stood.
   */
  val location: SightingLocation
    get() = SightingLocation(
        coordinate = Coordinate(outing.latitude, outing.longitude),
        title = primaryBird?.species?.species?.commonName ?: "Outing",
    )

  companion object {
    /**
     * Story order for confirmed birds: by moment on the timeline, then by write order for rows with
     * no clock (`MANUAL`, and ties). One definition, used by the list and the detail screen, so
     * "the first bird" never disagrees between them.
     *
     * Takes the whole outing rather than its sightings because a moment is not a column on a
     * `Sighting` any more — it is read down the evidence links, and only the outing's children have
     * what that needs.
     */
    fun storyOrder(withChildren: OutingWithChildren): List<Sighting> =
        withChildren.sightings.sortedWith(
            compareBy(
                { withChildren.offsetOf(it) ?: Long.MAX_VALUE },
                { it.createdAt },
            ),
        )
  }
}

/**
 * A month's worth of outings, under one heading in the Journal.
 *
 * [key] is the stable, sortable bucket ("2026-07") that also serves as the section's identity;
 * [title] is what the heading prints ("July 2026"). The Journal lists months newest first, and the
 * entries within each newest first, which is the order [JournalViewModel.groupByMonth] builds them
 * in.
 */
data class JournalMonth(
    val key: String,
    val title: String,
    val entries: List<JournalEntry>,
)
