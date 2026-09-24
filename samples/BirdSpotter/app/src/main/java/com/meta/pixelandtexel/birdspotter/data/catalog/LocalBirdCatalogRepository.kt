/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.catalog

import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.IdentifyQuery
import com.meta.pixelandtexel.birdspotter.domain.birdOfTheDayIndex

/**
 * The only implementation of [BirdCatalogRepository]: `catalog.db`, and nothing else.
 *
 * Thinner than [com.meta.pixelandtexel.birdspotter.data.journal.LocalJournalRepository] by a wide
 * margin, and that asymmetry is the point — the Journal has to keep a database and a media
 * directory consistent through writes, where the catalog is a file somebody else built. Bundled
 * bytes are [CatalogAssetStore]'s business and are resolved by the UI from the rows this returns,
 * so nothing here has to be told where assets live.
 */
class LocalBirdCatalogRepository(
    private val store: SpeciesStore,
) : BirdCatalogRepository {

  override suspend fun allSpecies(): List<Species> = store.allSpecies()

  /**
   * Chunks the guide into runs of equal `groupName`, in the order the rows arrive.
   *
   * A run, not a bucket. `groupBy` would gather a group that had somehow landed in two places back
   * into one — hiding the very mistake `build_seed_db.py`'s contiguity check exists to catch, and
   * quietly reordering the guide to do it. Walking the list keeps the seed's order the visible
   * truth.
   */
  override suspend fun browseGroups(): List<SpeciesGroup> {
    val runs = mutableListOf<MutableList<SpeciesWithMedia>>()
    for (bird in store.speciesInBrowseOrder()) {
      val current = runs.lastOrNull()
      if (current != null && current.first().species.groupName == bird.species.groupName) {
        current += bird
      } else {
        runs += mutableListOf(bird)
      }
    }
    return runs.map { SpeciesGroup(name = it.first().species.groupName, species = it) }
  }

  override suspend fun findById(speciesId: String): SpeciesWithMedia? = store.findById(speciesId)

  override suspend fun identifyCandidates(query: IdentifyQuery): List<SpeciesWithMedia> =
      store.identifyCandidates(
          sizeClass = query.sizeClass,
          colors = query.colors,
          colorCount = query.colors.size,
          behavior = query.behavior,
      )

  override suspend fun birdOfTheDay(epochDay: Long): SpeciesWithMedia? {
    val count = store.speciesCount()
    if (count == 0) return null
    return store.speciesAt(birdOfTheDayIndex(epochDay, count))
  }

  override suspend fun seedVersion(): Int? =
      store.metaValue(CatalogDatabase.SEED_VERSION_KEY)?.toIntOrNull()
}
