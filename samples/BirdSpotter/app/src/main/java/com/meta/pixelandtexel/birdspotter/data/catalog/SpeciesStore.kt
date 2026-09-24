/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.catalog

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Every query against `catalog.db`. Reads only — nothing in the app writes here.
 *
 * Note what is missing: there are no `Flow` returns anywhere on this store, where
 * [com.meta.pixelandtexel.birdspotter.data.journal.SightingStore] is mostly streams. The catalog is
 * immutable for the life of the process, so an observable query would emit once and then never
 * again — a `suspend` read says that honestly. The `*Stream()` convention applies to sources that
 * actually change.
 */
@Dao
interface SpeciesStore {

  /** The full guide by name, for looking a bird up when you already know what it is. */
  @Query("SELECT * FROM Species ORDER BY commonName ASC") suspend fun allSpecies(): List<Species>

  /**
   * The full guide with its media, in checklist sequence — what Explore browses.
   *
   * One statement for all 93 species rather than a page at a time. The rows are the cheap part; the
   * photos are not, and a lazy list decodes only the thumbnails it actually shows. Paging the
   * *query* would add state to every layer and save microseconds. If the catalog ever outgrows a
   * single read, this method is the seam — nothing above it knows how the list arrives.
   */
  @Transaction
  @Query("SELECT * FROM Species ORDER BY browseOrder ASC")
  suspend fun speciesInBrowseOrder(): List<SpeciesWithMedia>

  @Transaction
  @Query("SELECT * FROM Species WHERE id = :speciesId")
  suspend fun findById(speciesId: String): SpeciesWithMedia?

  /**
   * The identify wizard's one query. A candidate matches when:
   * - its [Species.sizeClass] is within one stop of the picked size — nobody judges a bird against
   *   a silhouette more precisely than that;
   * - it is tagged with the picked behavior;
   * - it wears **every** picked color. Each color the user names is on the bird, so naming more
   *   narrows the list. The COUNT-equals trick is that rule in SQL: the species' colors ∩ picked =
   *   picked, with [colorCount] = the picked set's size (Room can expand a collection into IN (…)
   *   but cannot ask it its length).
   *
   * Results come back in [Species.browseOrder] — checklist sequence, the order the rest of the
   * guide reads in. We have no likelihood data to rank by, and pretending otherwise would be a fake
   * confidence score; see the design notes.
   */
  @Transaction
  @Query(
      """
        SELECT * FROM Species
        WHERE sizeClass BETWEEN :sizeClass - 1 AND :sizeClass + 1
          AND EXISTS (SELECT 1 FROM SpeciesBehavior
                      WHERE speciesId = Species.id AND behavior = :behavior)
          AND (SELECT COUNT(*) FROM SpeciesColor
               WHERE speciesId = Species.id AND color IN (:colors)) = :colorCount
        ORDER BY browseOrder ASC
        """,
  )
  suspend fun identifyCandidates(
      sizeClass: Int,
      colors: Set<PlumageColor>,
      colorCount: Int,
      behavior: BirdBehavior,
  ): List<SpeciesWithMedia>

  @Query("SELECT COUNT(*) FROM Species") suspend fun speciesCount(): Int

  /**
   * The species at [index] in slug order — the rotation
   * [com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository.birdOfTheDay] indexes into.
   *
   * Ordered by `id` rather than `commonName` deliberately: slugs are frozen by contract, so the
   * rotation a given day produces stays put even if a common name is corrected.
   */
  @Transaction
  @Query("SELECT * FROM Species ORDER BY id ASC LIMIT 1 OFFSET :index")
  suspend fun speciesAt(index: Int): SpeciesWithMedia?

  /** `seed.version`, `seed.builtAt`. Null for an unknown key. */
  @Query("SELECT value FROM AppMeta WHERE key = :key") suspend fun metaValue(key: String): String?
}
