/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.birddetail

import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMedia
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How the vocalization card labels a recording from its sex and stage — the "who is singing" line
 * the bird page shows in place of restating "Song"/"Call".
 *
 * Pure formatting, so it is reachable without driving a ViewModel through `viewModelScope` or a
 * player — which is what keeps this plain JUnit. The capitalization case earns its place: a stock
 * title-case helper raises every word of a two-word value, so only the first letter is raised by
 * hand, and this pins that.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class VoiceDescriptionTest {

  private fun recording(sex: String? = null, stage: String? = null) = SpeciesMedia(
      id = "x-song-01",
      speciesId = "x",
      type = SpeciesMediaType.SONG,
      assetKey = "x/song-01",
      isPrimary = true,
      sex = sex,
      stage = stage,
      sortOrder = 0,
  )

  @Test
  fun voiceDescription_withSexAndStage_joinsThemWithADot() {
    assertEquals("Male · Adult", recording(sex = "male", stage = "adult").voiceDescription)
  }

  @Test
  fun voiceDescription_withSexOnly_isTheSexAlone() {
    assertEquals("Female", recording(sex = "female").voiceDescription)
  }

  @Test
  fun voiceDescription_withStageOnly_isTheStageAlone() {
    assertEquals("Juvenile", recording(stage = "juvenile").voiceDescription)
  }

  @Test
  fun voiceDescription_withNeither_isNil() {
    assertNull(recording().voiceDescription)
  }

  @Test
  fun voiceDescription_withBlankValues_ignoresThem() {
    assertEquals("Adult", recording(sex = "", stage = "adult").voiceDescription)
  }

  @Test
  fun voiceDescription_capitalizesOnlyTheFirstLetter() {
    // A Xeno-canto value can carry two words; only the first letter is raised, so this
    // reads "Male, female" rather than a word-by-word "Male, Female".
    assertEquals("Male, female", recording(sex = "male, female").voiceDescription)
  }
}
