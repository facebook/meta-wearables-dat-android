/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.ui.previews.PreviewCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The display screen's two pieces of policy: **which birds a query offers to send**, and **when the
 * custom card is ready to go up.**
 *
 * The matching itself belongs to Explore and is pinned by `ExploreViewModelTest`; what is under
 * test here is only the seam this screen adds — a blank field offers the whole guide, anything else
 * offers Explore's matches — against `PreviewCatalog.guide` for the same reason that test uses it.
 * The custom card's rule is the other seam: a bird chosen and a message with ink in it, or the
 * button never appears.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class GlassesDisplayViewModelTest {

  private val guide = PreviewCatalog.guide

  private fun ids(species: List<SpeciesWithMedia>): List<String> = species.map { it.species.id }

  @Test
  fun aBlankQueryOffersTheWholeGuideInBrowseOrder() {
    // The screen opens ready to send: every bird on the table, in the order the guide
    // prints them, with nothing typed.
    val birds = GlassesDisplayViewModel.birdsFor("", guide)

    assertEquals(
        listOf(
            "american-crow",
            "blue-jay",
            "common-raven",
            "american-robin",
            "eastern-bluebird",
            "wood-thrush",
        ),
        ids(birds),
    )
  }

  @Test
  fun aWhitespaceQueryIsBlank() {
    // A field somebody spaced through must not read as a search for nothing.
    val birds = GlassesDisplayViewModel.birdsFor("   ", guide)

    assertEquals(6, birds.size)
  }

  @Test
  fun aQueryOffersWhatExploreWouldFind() {
    // One search policy in the app: what this field matches is exactly what Explore's
    // matches, so a bird findable there is sendable here by the same letters.
    val birds = GlassesDisplayViewModel.birdsFor("corvus", guide)

    assertEquals(listOf("american-crow", "common-raven"), ids(birds))
  }

  @Test
  fun aCustomCardNeedsBothABirdAndAMessage() {
    // Half a setup sends nothing: no bird means nowhere for the line to go, and no
    // line means the ordinary card already says it better.
    assertTrue(GlassesDisplayViewModel.customCardReady(PreviewCatalog.blueJay, "The one to watch."))
    assertFalse(GlassesDisplayViewModel.customCardReady(null, "The one to watch."))
    assertFalse(GlassesDisplayViewModel.customCardReady(PreviewCatalog.blueJay, ""))
  }

  @Test
  fun aWhitespaceMessageIsNoMessage() {
    // A message somebody spaced through must not put a card up with a blank last line.
    assertFalse(GlassesDisplayViewModel.customCardReady(PreviewCatalog.blueJay, "   \n"))
  }
}
