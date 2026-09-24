/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.previews

import com.meta.pixelandtexel.birdspotter.data.catalog.Species
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesGroup
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMedia
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMediaType
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.data.journal.Outing
import com.meta.pixelandtexel.birdspotter.data.journal.OutingEvent
import com.meta.pixelandtexel.birdspotter.data.journal.OutingEventType
import com.meta.pixelandtexel.birdspotter.data.journal.OutingKind
import com.meta.pixelandtexel.birdspotter.data.journal.OutingWithChildren
import com.meta.pixelandtexel.birdspotter.data.journal.Sighting
import com.meta.pixelandtexel.birdspotter.data.journal.WizardTrait
import com.meta.pixelandtexel.birdspotter.features.journal.ConfirmedBird
import com.meta.pixelandtexel.birdspotter.features.journal.JournalEntry
import com.meta.pixelandtexel.birdspotter.features.journal.JournalViewModel

/**
 * Preview fixtures — one real species, verbatim from the seed.
 *
 * Previews run without the app's database, so they get their rows directly. The rows are copied
 * from the seed data rather than invented, so the credits and durations on screen are the ones that
 * actually ship. The photos stay blank: a Compose preview has no `AssetManager`, which is what
 * `CatalogPhoto` checks `LocalInspectionMode` for.
 */
object PreviewCatalog {

  /** The Northern Cardinal as shipped: three photos, a song, and the song's sonogram. */
  val bird = SpeciesWithMedia(
      species =
          Species(
              id = "northern-cardinal",
              commonName = "Northern Cardinal",
              scientificName = "Cardinalis cardinalis",
              familyName = "Cardinalidae",
              browseOrder = 920,
              groupName = "Cardinals & Grosbeaks",
              sizeClass = 2,
              aboutText =
                  "A large, long-tailed songbird with a pointed crest and a heavy " +
                      "red-orange bill. Males are brilliant red; females are warm tan with red " +
                      "accents. Non-migratory and common at feeders across eastern and central " +
                      "North America.",
              habitatText =
                  "Woodland edges, thickets, backyards, and overgrown fields. " +
                      "Favors dense shrubby cover for nesting and forages on or near the ground.",
          ),
      media =
          listOf(
              SpeciesMedia(
                  id = "northern-cardinal-photo-01",
                  speciesId = "northern-cardinal",
                  type = SpeciesMediaType.PHOTO,
                  assetKey = "northern-cardinal/photo-01",
                  credit = "patricia pierce / Wikimedia Commons (CC BY 2.0)",
                  isPrimary = true,
                  focusX = 0.51,
                  focusY = 0.23,
                  sortOrder = 0,
              ),
              SpeciesMedia(
                  id = "northern-cardinal-photo-02",
                  speciesId = "northern-cardinal",
                  type = SpeciesMediaType.PHOTO,
                  assetKey = "northern-cardinal/photo-02",
                  credit = "lwolfartist / Wikimedia Commons (CC BY 2.0)",
                  isPrimary = false,
                  focusX = 0.62,
                  focusY = 0.31,
                  sortOrder = 1,
              ),
              SpeciesMedia(
                  id = "northern-cardinal-photo-03",
                  speciesId = "northern-cardinal",
                  type = SpeciesMediaType.PHOTO,
                  assetKey = "northern-cardinal/photo-03",
                  credit = "Daniel Dwyer Jr / Wikimedia Commons (CC BY 4.0)",
                  isPrimary = false,
                  focusX = 0.63,
                  focusY = 0.34,
                  sortOrder = 2,
              ),
              SpeciesMedia(
                  id = "northern-cardinal-song-01",
                  speciesId = "northern-cardinal",
                  type = SpeciesMediaType.SONG,
                  assetKey = "northern-cardinal/song-01",
                  credit = "steve / Xeno-canto (CC BY-SA 4.0)",
                  isPrimary = true,
                  durationMs = 12_000,
                  sex = "male",
                  stage = "adult",
                  sortOrder = 0,
              ),
              SpeciesMedia(
                  id = "northern-cardinal-sonogram-01",
                  speciesId = "northern-cardinal",
                  type = SpeciesMediaType.SONOGRAM,
                  assetKey = "northern-cardinal/sonogram-01",
                  credit = "steve / Xeno-canto (CC BY-SA 4.0)",
                  isPrimary = false,
                  sortOrder = 0,
              ),
          ),
  )

  /**
   * Two sections of the browse list, in the order the seed puts them.
   *
   * Real ids, names and `browseOrder`s, so a preview shows the guide's actual sequence rather than
   * a plausible-looking invention — and so a group that stopped being contiguous would look wrong
   * here too. Hero photos only: the list never asks a row for anything else.
   */
  val guide = listOf(
      group(
          "Jays & Crows",
          row("american-crow", "American Crow", "Corvus brachyrhynchos", "Corvidae", 370, 5),
          row("blue-jay", "Blue Jay", "Cyanocitta cristata", "Corvidae", 390, 4),
          row("common-raven", "Common Raven", "Corvus corax", "Corvidae", 410, 6),
      ),
      group(
          "Thrushes",
          row("american-robin", "American Robin", "Turdus migratorius", "Turdidae", 600, 3),
          row("eastern-bluebird", "Eastern Bluebird", "Sialia sialis", "Turdidae", 610, 2),
          row("wood-thrush", "Wood Thrush", "Hylocichla mustelina", "Turdidae", 630, 3),
      ),
  )

  /** A Blue Jay, identified — the second bird a preview Journal needs beyond the cardinal. */
  val blueJay = SpeciesWithMedia(
      species =
          Species(
              id = "blue-jay",
              commonName = "Blue Jay",
              scientificName = "Cyanocitta cristata",
              familyName = "Corvidae",
              browseOrder = 390,
              groupName = "Jays & Crows",
              sizeClass = 4,
              aboutText = "",
              habitatText = "",
          ),
      media =
          listOf(
              SpeciesMedia(
                  id = "blue-jay-photo-01",
                  speciesId = "blue-jay",
                  type = SpeciesMediaType.PHOTO,
                  assetKey = "blue-jay/photo-01",
                  isPrimary = true,
                  sortOrder = 0,
              ),
          ),
  )

  /**
   * Three outings across two months — a wizard entry with its answers, a live outing that confirmed
   * two birds, and a live outing that confirmed none — so a preview Journal shows a grouped list,
   * the search results, and every headline branch of the labeling rule (bird-led, bird + n more,
   * date-led). Timestamps are fixed (mid-2026, midday UTC) so the month buckets are deterministic,
   * and the coordinates are real Cincinnati-area points so the detail screen's map draws somewhere
   * plausible.
   */
  val journalEntries: List<JournalEntry> = listOf(
      run {
        val outing = outing(
            id = "preview-outing-1",
            kind = OutingKind.MANUAL,
            startedAt = 1_784_203_200_000L,
            latitude = 39.2098,
            longitude = -84.4699,
            notes = "Singing from the very top of a bare maple — unmistakable once it started.",
        )
        val confirmed = sighting(
            id = "preview-sighting-1",
            outingId = outing.id,
            speciesId = "northern-cardinal",
            createdAt = outing.startedAt,
        )
        JournalEntry(
            withChildren =
                OutingWithChildren(
                    outing = outing,
                    events =
                        wizardAnswers(
                            outing,
                            size = "2",
                            colors = "BLACK,RED",
                            behavior = "IN_TREES_OR_BUSHES",
                        ),
                    sightings = listOf(confirmed),
                ),
            birds = listOf(ConfirmedBird(confirmed, bird)),
        )
      },
      run {
        val outing = outing(
            id = "preview-outing-2",
            kind = OutingKind.LIVE,
            startedAt = 1_783_155_600_000L,
            durationMs = 14 * 60_000L,
            latitude = 39.1523,
            longitude = -84.3822,
        )
        // Both birds were heard, so each carries its moment and its score on the
        // detection behind it and nothing on the sighting itself. Neither gets a
        // "Looking"/"Facing" row on the detail screen, which is the honest rendering
        // for a bird nothing aimed a camera at.
        val firstHeard = heardDetection(
            id = "preview-detection-1",
            outingId = outing.id,
            speciesId = "blue-jay",
            confidence = 0.87,
            offsetMs = 320_000L,
            createdAt = outing.startedAt + 320_000L,
        )
        val secondHeard = heardDetection(
            id = "preview-detection-2",
            outingId = outing.id,
            speciesId = "northern-cardinal",
            confidence = 0.74,
            offsetMs = 610_000L,
            createdAt = outing.startedAt + 610_000L,
        )
        val first = sighting(
            id = "preview-sighting-2",
            outingId = outing.id,
            speciesId = "blue-jay",
            sourceEventId = firstHeard.id,
            createdAt = outing.startedAt + 320_000L,
        )
        val second = sighting(
            id = "preview-sighting-3",
            outingId = outing.id,
            speciesId = "northern-cardinal",
            sourceEventId = secondHeard.id,
            createdAt = outing.startedAt + 610_000L,
        )
        JournalEntry(
            withChildren =
                OutingWithChildren(
                    outing = outing,
                    events = listOf(firstHeard, secondHeard),
                    sightings = listOf(first, second),
                ),
            birds = listOf(ConfirmedBird(first, blueJay), ConfirmedBird(second, bird)),
        )
      },
      run {
        val outing = outing(
            id = "preview-outing-3",
            kind = OutingKind.LIVE,
            startedAt = 1_781_940_600_000L,
            durationMs = 6 * 60_000L,
            latitude = 39.2277,
            longitude = -84.4530,
        )
        JournalEntry(withChildren = OutingWithChildren(outing = outing), birds = emptyList())
      },
  )

  /** The birdless live outing — the Merlin "0 birds, saved anyway" state, first-class. */
  val birdlessJournalEntry = journalEntries[2]

  /**
   * The entries grouped as the Journal shows them — through the real grouping, so a preview
   * reflects what ships.
   */
  val journalMonths = JournalViewModel.groupByMonth(journalEntries)

  private fun outing(
      id: String,
      kind: OutingKind,
      startedAt: Long,
      durationMs: Long? = null,
      latitude: Double,
      longitude: Double,
      notes: String? = null,
  ): Outing = Outing(
      id = id,
      kind = kind,
      startedAt = startedAt,
      durationMs = durationMs,
      latitude = latitude,
      longitude = longitude,
      notes = notes,
      createdAt = startedAt,
      updatedAt = startedAt,
  )

  private fun sighting(
      id: String,
      outingId: String,
      speciesId: String,
      sourceEventId: String? = null,
      createdAt: Long,
  ): Sighting = Sighting(
      id = id,
      outingId = outingId,
      sourceEventId = sourceEventId,
      speciesId = speciesId,
      createdAt = createdAt,
  )

  /** A bird the classifier heard — the moment and the score live here, not on the sighting. */
  private fun heardDetection(
      id: String,
      outingId: String,
      speciesId: String,
      confidence: Double,
      offsetMs: Long,
      createdAt: Long,
  ): OutingEvent = OutingEvent(
      id = id,
      outingId = outingId,
      type = OutingEventType.DETECTION,
      offsetMs = offsetMs,
      speciesId = speciesId,
      confidence = confidence,
      createdAt = createdAt,
  )

  /** The wizard's three answers, in trait order, the way `saveSighting` writes them. */
  private fun wizardAnswers(
      outing: Outing,
      size: String,
      colors: String,
      behavior: String,
  ): List<OutingEvent> = listOf(
      WizardTrait.SIZE to size,
      WizardTrait.COLORS to colors,
      WizardTrait.BEHAVIOR to behavior,
  )
      .mapIndexed { index, (trait, value) ->
        OutingEvent(
            id = "${outing.id}-answer-$index",
            outingId = outing.id,
            type = OutingEventType.WIZARD_ANSWER,
            trait = trait,
            value = value,
            createdAt = outing.startedAt,
        )
      }

  private fun group(name: String, vararg species: (String) -> SpeciesWithMedia) =
      SpeciesGroup(name = name, species = species.map { it(name) })

  private fun row(
      id: String,
      commonName: String,
      scientificName: String,
      familyName: String,
      browseOrder: Int,
      sizeClass: Int,
  ): (String) -> SpeciesWithMedia = { groupName ->
    SpeciesWithMedia(
        species =
            Species(
                id = id,
                commonName = commonName,
                scientificName = scientificName,
                familyName = familyName,
                browseOrder = browseOrder,
                groupName = groupName,
                sizeClass = sizeClass,
                aboutText = "",
                habitatText = "",
            ),
        media =
            listOf(
                SpeciesMedia(
                    id = "$id-photo-01",
                    speciesId = id,
                    type = SpeciesMediaType.PHOTO,
                    assetKey = "$id/photo-01",
                    isPrimary = true,
                    sortOrder = 0,
                ),
            ),
    )
  }
}
