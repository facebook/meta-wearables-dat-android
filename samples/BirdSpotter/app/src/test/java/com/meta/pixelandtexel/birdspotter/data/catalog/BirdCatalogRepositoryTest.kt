/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

@file:Suppress("UseOfRunBlocking", "UseOfRunBlockingInTest")

package com.meta.pixelandtexel.birdspotter.data.catalog

import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.IdentifyQuery
import com.meta.pixelandtexel.birdspotter.domain.birdOfTheDayIndex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The catalog stack end to end, against the **real shipped `catalog.db`** rather than a fixture.
 *
 * That is the whole point of opening it here: `createFromAsset()` validates a prepackaged
 * database's schema against Room's own, and a mismatch is a launch-time crash on a user's phone,
 * not a compile error. Every read below only succeeds if the asset the seed pipeline built and
 * Room's expectations agree — so this test is really a check on the seed pipeline wearing the
 * clothes of a repository test.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
@RunWith(RobolectricTestRunner::class)
class BirdCatalogRepositoryTest {

  private lateinit var database: CatalogDatabase
  private lateinit var repository: BirdCatalogRepository

  @Before
  fun setUp() {
    database = CatalogDatabase.open(RuntimeEnvironment.getApplication())
    repository = LocalBirdCatalogRepository(database.speciesStore())
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun open_readsTheShippedCatalog() = runBlocking {
    val species = repository.allSpecies()

    assertEquals(93, species.size)
    assertTrue(species.any { it.id == "northern-cardinal" })
  }

  @Test
  fun allSpecies_isAlphabeticalByCommonName() = runBlocking {
    val names = repository.allSpecies().map { it.commonName }

    assertEquals(names.sorted(), names)
  }

  @Test
  fun browseGroups_coverTheWholeCatalogInSeedOrder() = runBlocking {
    val groups = repository.browseGroups()
    val flattened = groups.flatMap { it.species }

    assertEquals(93, flattened.size)
    assertEquals(93, flattened.map { it.species.id }.toSet().size)
    assertEquals(
        flattened.map { it.species.browseOrder }.sorted(),
        flattened.map { it.species.browseOrder },
    )
  }

  @Test
  fun browseGroups_areContiguousAndNeverEmpty() = runBlocking {
    val groups = repository.browseGroups()
    val names = groups.map { it.name }

    // Each group appears once. A name showing up twice means the seed let a group
    // fall in two places, which would print its header twice on Explore.
    assertEquals(names.size, names.toSet().size)
    assertTrue(groups.all { it.species.isNotEmpty() })
    // Every species agrees with the group it was filed under.
    assertTrue(groups.all { group -> group.species.all { it.species.groupName == group.name } })
  }

  @Test
  fun browseGroups_openTheGuideWithWaterfowl() = runBlocking {
    val groups = repository.browseGroups()

    // Checklist sequence, not the alphabet: a field guide starts at the waterfowl and
    // ends at the cardinals, and `browseOrder` is what carries that.
    assertEquals("Waterfowl & Game Birds", groups.first().name)
    assertEquals("Canada Goose", groups.first().species.first().species.commonName)
    assertEquals("Cardinals & Grosbeaks", groups.last().name)
  }

  @Test
  fun browseGroups_carryTheThumbnailEachRowNeeds() = runBlocking {
    val groups = repository.browseGroups()

    assertTrue(groups.flatMap { it.species }.all { it.heroPhoto != null })
  }

  @Test
  fun findById_returnsSpeciesWithItsBundledMedia() = runBlocking {
    val cardinal = repository.findById("northern-cardinal")

    assertNotNull(cardinal)
    assertEquals("Cardinalis cardinalis", cardinal!!.species.scientificName)
    // The external join keys survive the seed round-trip. Kept distinct from the
    // scientific name on purpose — see Species.wikidataId.
    assertEquals("Q726389", cardinal.species.wikidataId)
    assertEquals("norcar", cardinal.species.ebirdSpeciesCode)
    assertEquals(3, cardinal.photos.size)
    assertNotNull(cardinal.heroPhoto)
    assertTrue(cardinal.heroPhoto!!.isPrimary)
    assertNotNull(cardinal.referenceAudio)
  }

  @Test
  fun findById_returnsNullForUnknownSpecies() = runBlocking {
    assertNull(repository.findById("pterodactyl"))
  }

  /**
   * The identify wizard's demo query — a robin-to-crow bird, black, on a wire — finds the Common
   * Grackle, and everything else it finds is inside the size window. Pinned to the seed the way the
   * browse tests are: these answers are content, and content regressions should fail like code
   * ones.
   */
  @Test
  fun identifyCandidates_findTheBlackbirdOnTheWire() = runBlocking {
    val candidates =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 4,
                colors = setOf(PlumageColor.BLACK),
                behavior = BirdBehavior.ON_FENCE_OR_WIRE,
            ),
        )
    val ids = candidates.map { it.species.id }

    assertTrue(ids.contains("common-grackle"))
    assertTrue(candidates.all { it.species.sizeClass in 3..5 })
  }

  /** Size matches within one stop either way — a 4 finds 3s and 5s, never a 6. */
  @Test
  fun identifyCandidates_matchWithinOneSizeStop() = runBlocking {
    val candidates =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 6,
                colors = setOf(PlumageColor.BROWN),
                behavior = BirdBehavior.SOARING_OR_FLYING,
            ),
        )

    assertTrue(candidates.isNotEmpty())
    assertTrue(candidates.all { it.species.sizeClass in 5..7 })
    // The Bald Eagle is a 7 — inside a 6's window, outside a 4's.
    assertTrue(candidates.any { it.species.id == "bald-eagle" })
    val narrower =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 4,
                colors = setOf(PlumageColor.BROWN),
                behavior = BirdBehavior.SOARING_OR_FLYING,
            ),
        )
    assertTrue(narrower.none { it.species.id == "bald-eagle" })
  }

  /**
   * Every picked color must be on the bird, so adding a color can only narrow the list.
   * Black-and-blue keeps the grackle (it wears both) and drops the Red-winged Blackbird (black, but
   * never blue).
   */
  @Test
  fun identifyCandidates_requireEveryPickedColor() = runBlocking {
    val black =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 4,
                colors = setOf(PlumageColor.BLACK),
                behavior = BirdBehavior.ON_FENCE_OR_WIRE,
            ),
        )
    val blackAndBlue =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 4,
                colors = setOf(PlumageColor.BLACK, PlumageColor.BLUE),
                behavior = BirdBehavior.ON_FENCE_OR_WIRE,
            ),
        )
    val blackIds = black.map { it.species.id }.toSet()
    val blackAndBlueIds = blackAndBlue.map { it.species.id }.toSet()

    assertTrue(blackIds.containsAll(blackAndBlueIds))
    assertTrue(blackIds.contains("red-winged-blackbird"))
    assertTrue(blackAndBlueIds.contains("common-grackle"))
    assertTrue(!blackAndBlueIds.contains("red-winged-blackbird"))
  }

  /** Behavior is a hard filter: the same bird appears where it lives, not elsewhere. */
  @Test
  fun identifyCandidates_filterByBehavior() = runBlocking {
    val swimming =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 6,
                colors = setOf(PlumageColor.GREEN),
                behavior = BirdBehavior.SWIMMING_OR_WADING,
            ),
        )
    val atFeeder =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 6,
                colors = setOf(PlumageColor.GREEN),
                behavior = BirdBehavior.AT_FEEDER,
            ),
        )

    assertTrue(swimming.any { it.species.id == "mallard" })
    assertTrue(atFeeder.none { it.species.id == "mallard" })
  }

  /** Results read in checklist sequence, like every other list in the guide. */
  @Test
  fun identifyCandidates_arriveInBrowseOrder() = runBlocking {
    val candidates =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 1,
                colors = setOf(PlumageColor.YELLOW),
                behavior = BirdBehavior.IN_TREES_OR_BUSHES,
            ),
        )

    assertTrue(candidates.size >= 2)
    assertEquals(
        candidates.map { it.species.browseOrder }.sorted(),
        candidates.map { it.species.browseOrder },
    )
  }

  /** The results cards need plates; every candidate arrives with its media. */
  @Test
  fun identifyCandidates_carryTheMediaTheirCardsNeed() = runBlocking {
    val candidates =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 2,
                colors = setOf(PlumageColor.RED),
                behavior = BirdBehavior.AT_FEEDER,
            ),
        )

    assertTrue(candidates.any { it.species.id == "northern-cardinal" })
    assertTrue(candidates.all { it.heroPhoto != null })
  }

  /** An impossible ask — a goose-sized green feeder bird — is empty, not an error. */
  @Test
  fun identifyCandidates_emptyWhenNothingFits() = runBlocking {
    val candidates =
        repository.identifyCandidates(
            IdentifyQuery(
                sizeClass = 7,
                colors = setOf(PlumageColor.GREEN),
                behavior = BirdBehavior.AT_FEEDER,
            ),
        )

    assertEquals(emptyList<String>(), candidates.map { it.species.id })
  }

  /** Every species earns its card: three photos and one vocalization, no exceptions. */
  @Test
  fun everySpecies_shipsPhotosAndAudio() = runBlocking {
    val thin =
        repository
            .allSpecies()
            .map { repository.findById(it.id)!! }
            .filter { it.photos.size < 3 || it.referenceAudio == null }

    assertEquals(emptyList<String>(), thin.map { it.species.id })
  }

  /** The clip earns a picture too: whatever a species can play, it can also show. */
  @Test
  fun everySpecies_shipsASonogramForItsClip() = runBlocking {
    val missing =
        repository
            .allSpecies()
            .map { repository.findById(it.id)!! }
            .filter { it.referenceAudio != null && it.sonogram == null }

    assertEquals(emptyList<String>(), missing.map { it.species.id })
  }

  /**
   * A sonogram is an image of the clip, never a clip. Guards the partition directly: `audio` was
   * once "everything that isn't a photo", which handed the player a PNG the moment the catalog
   * started shipping sonograms.
   */
  @Test
  fun sonogram_isNeverServedAsAudio() = runBlocking {
    val cardinal = repository.findById("northern-cardinal")!!

    assertNotNull(cardinal.sonogram)
    assertEquals(SpeciesMediaType.SONOGRAM, cardinal.sonogram!!.type)
    assertFalse(cardinal.sonogram!!.isPrimary)
    assertTrue(cardinal.audio.none { it.type == SpeciesMediaType.SONOGRAM })
    assertTrue(cardinal.referenceAudio!!.type != SpeciesMediaType.SONOGRAM)
  }

  /**
   * The seed writes SCREAMING_SNAKE that Room maps back onto [ConservationStatus]; a raw IUCN label
   * like "least concern" would fail to decode rather than arrive wrong. Nullable is legitimate —
   * six birds have no assessment on Wikidata.
   */
  @Test
  fun conservationStatus_decodesWhereTheSeedHasOne() = runBlocking {
    val statuses = repository.allSpecies().mapNotNull { it.conservationStatus }

    assertTrue(statuses.isNotEmpty())
    assertTrue(statuses.contains(ConservationStatus.LEAST_CONCERN))
  }

  /** Bundled bytes exist for every row that claims them. */
  @Test
  fun everyMediaRow_resolvesToABundledAsset() = runBlocking {
    val assets = CatalogAssetStore.open(RuntimeEnvironment.getApplication())
    val missing =
        repository
            .allSpecies()
            .flatMap { repository.findById(it.id)!!.media }
            .filterNot { assets.exists(it) }

    assertEquals(emptyList<String>(), missing.map { assets.assetPath(it) })
  }

  @Test
  fun birdOfTheDay_isStableForADayAndMovesTheNext() = runBlocking {
    val today = repository.birdOfTheDay(20_656)
    val again = repository.birdOfTheDay(20_656)
    val tomorrow = repository.birdOfTheDay(20_657)

    assertNotNull(today)
    assertEquals(today!!.species.id, again!!.species.id)
    assertTrue(today.species.id != tomorrow!!.species.id)
  }

  @Test
  fun birdOfTheDay_carriesTheMediaItsCardNeeds() = runBlocking {
    val bird = repository.birdOfTheDay(20_656)!!

    assertNotNull(bird.heroPhoto)
    assertNotNull(bird.referenceAudio)
    assertTrue(bird.species.commonName.isNotBlank())
  }

  /** The rotation visits every species before repeating any — for any catalog size. */
  @Test
  fun birdOfTheDay_coversTheWholeCatalogBeforeRepeating() = runBlocking {
    val count = repository.allSpecies().size
    val seen = (0 until count).map { birdOfTheDayIndex(20_656L + it, count) }.toSet()

    assertEquals(count, seen.size)

    // Sizes the catalog might plausibly grow to, including ones sharing a factor
    // with the stride. The property has to hold by construction, not by luck.
    for (size in listOf(1, 2, 3, 40, 93, 100, 150)) {
      val cycle = (0 until size).map { birdOfTheDayIndex(it.toLong(), size) }.toSet()
      assertEquals("catalog of $size", size, cycle.size)
    }
  }

  /** Consecutive days land far apart, rather than marching down the slug order. */
  @Test
  fun birdOfTheDay_doesNotWalkTheCatalogInOrder() {
    val week = (0 until 6).map { birdOfTheDayIndex(20_656L + it, 93) }

    assertEquals(listOf(2, 58, 21, 77, 40, 3), week)
  }

  @Test
  fun birdOfTheDayIndex_matchesThePinnedValues() {
    // Pinned values: these exact pairs are what keeps a given day landing on a given bird.
    // If they ever drift, the demo shows different birds on the two phones on stage.
    assertEquals(2, birdOfTheDayIndex(20_656L, 93))
    assertEquals(58, birdOfTheDayIndex(20_657L, 93))
    assertEquals(0, birdOfTheDayIndex(0L, 93))
    assertEquals(56, birdOfTheDayIndex(1L, 93))
  }

  /**
   * The constant the replacement check compares against has to match what the pipeline actually
   * stamped, or an install keeps serving a catalog the app thinks it replaced.
   */
  @Test
  fun seedVersion_matchesTheExpectedConstant() = runBlocking {
    assertEquals(CatalogDatabase.EXPECTED_SEED_VERSION, repository.seedVersion())
  }
}
