/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sentence the app says about a bird it heard: which of the two openings a confidence earns,
 * and the two-letter word in the middle that decides whether it sounds like English.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class SpokenOutputTest {

  @Test
  fun heardAloud_wellAboveTheThreshold_saysItPlainly() {
    assertEquals("I just heard a Green Jay.", heardAloud("Green Jay", 0.92f))
  }

  @Test
  fun heardAloud_belowTheThreshold_hedges() {
    assertEquals("I think I heard a Green Jay.", heardAloud("Green Jay", 0.74f))
  }

  @Test
  fun heardAloud_exactlyAtTheThreshold_saysItPlainly() {
    // The boundary belongs to the sure side: the threshold is the point at which the app has
    // stopped hedging, not the last point at which it still is.
    assertEquals(
        "I just heard a Green Jay.",
        heardAloud("Green Jay", SureAloudConfidence),
    )
  }

  @Test
  fun heardAloud_carriesTheArticleTheNameNeeds() {
    assertEquals("I just heard an American Robin.", heardAloud("American Robin", 0.9f))
  }

  @Test
  fun spokenCertainty_readsTheConfidence() {
    assertEquals(SpokenCertainty.SURE, SpokenCertainty.of(0.9f))
    assertEquals(SpokenCertainty.HEDGED, SpokenCertainty.of(0.5f))
  }

  @Test
  fun indefiniteArticle_aConsonantTakesA() {
    assertEquals("a", indefiniteArticle("Green Jay"))
    assertEquals("a", indefiniteArticle("Northern Cardinal"))
  }

  @Test
  fun indefiniteArticle_aVowelTakesAn() {
    assertEquals("an", indefiniteArticle("American Robin"))
    assertEquals("an", indefiniteArticle("Osprey"))
    assertEquals("an", indefiniteArticle("Indigo Bunting"))
    assertEquals("an", indefiniteArticle("Eastern Bluebird"))
  }

  @Test
  fun indefiniteArticle_theOnesSpeltWithAVowelAndSaidWithAYTakeA() {
    // The whole reason the rule is not "does it start with a vowel" — and the only two names
    // in the catalog that need the exception.
    assertEquals("a", indefiniteArticle("European Starling"))
    assertEquals("a", indefiniteArticle("Eurasian Collared-Dove"))
  }

  @Test
  fun indefiniteArticle_anEmptyNameFallsBackRatherThanFailing() {
    assertEquals("a", indefiniteArticle(""))
  }
}
