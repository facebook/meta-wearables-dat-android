/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.catalog

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A species and everything bundled for it — what a species card renders.
 *
 * Unlike its Journal counterpart, [media] is never empty in practice: the seed pipeline refuses to
 * ship a species without three photos and one vocalization. The accessors still return nullables,
 * because a shipped asset going missing should degrade the card, not crash it.
 */
data class SpeciesWithMedia(
    @Embedded val species: Species,
    @Relation(parentColumn = "id", entityColumn = "speciesId")
    val media: List<SpeciesMedia> = emptyList(),
) {
  /** Every photo, hero first, then in the order the catalog set. */
  val photos: List<SpeciesMedia>
    get() =
        media
            .filter { it.type == SpeciesMediaType.PHOTO }
            .sortedWith(compareByDescending<SpeciesMedia> { it.isPrimary }.thenBy { it.sortOrder })

  /**
   * Song where the species has one, call otherwise — one clip per species.
   *
   * Named types rather than "everything that isn't a photo": the catalog also ships a
   * [SpeciesMediaType.SONOGRAM], which is an image, and the old test would have handed it to the
   * player as a clip to play.
   */
  val audio: List<SpeciesMedia>
    get() =
        media
            .filter { it.type == SpeciesMediaType.SONG || it.type == SpeciesMediaType.CALL }
            .sortedBy { it.sortOrder }

  /** The spectrogram of [referenceAudio], drawn beside it. One per species, or none. */
  val sonogram: SpeciesMedia?
    get() = media.firstOrNull { it.type == SpeciesMediaType.SONOGRAM }

  /** The card's lead image. */
  val heroPhoto: SpeciesMedia?
    get() = photos.firstOrNull()

  /** What the play button plays. */
  val referenceAudio: SpeciesMedia?
    get() = audio.firstOrNull { it.isPrimary } ?: audio.firstOrNull()
}
