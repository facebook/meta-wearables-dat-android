/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import com.meta.pixelandtexel.birdspotter.data.journal.CaptureSource
import com.meta.pixelandtexel.birdspotter.data.journal.GazeContext
import com.meta.pixelandtexel.birdspotter.data.journal.OutingKind
import com.meta.pixelandtexel.birdspotter.data.journal.WizardTrait
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pieces of `JournalFormatting` with real logic — the bearing bucketing, the duration
 * arithmetic, the wizard-answer unfolding — and the per-cent rounding beside them. The date helpers
 * are platform formatters and aren't asserted.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class JournalFormattingTest {

  @Test
  fun bearingLabel_readsTheFourCardinals() {
    assertEquals("N", JournalFormatting.bearingLabel(0.0))
    assertEquals("E", JournalFormatting.bearingLabel(90.0))
    assertEquals("S", JournalFormatting.bearingLabel(180.0))
    assertEquals("W", JournalFormatting.bearingLabel(270.0))
  }

  @Test
  fun bearingLabel_readsTheIntercardinals() {
    assertEquals("NE", JournalFormatting.bearingLabel(45.0))
    assertEquals("SE", JournalFormatting.bearingLabel(135.0))
    assertEquals("SW", JournalFormatting.bearingLabel(225.0))
    assertEquals("NW", JournalFormatting.bearingLabel(315.0))
  }

  @Test
  fun bearingLabel_readsTheSixteenPoint() {
    assertEquals("NNE", JournalFormatting.bearingLabel(22.5))
    assertEquals("WNW", JournalFormatting.bearingLabel(292.5))
  }

  @Test
  fun bearingLabel_roundsToTheNearestBucket() {
    // Buckets are 22.5° wide, centred on each point; N and NNE part at 11.25.
    assertEquals("N", JournalFormatting.bearingLabel(11.0))
    assertEquals("NNE", JournalFormatting.bearingLabel(12.0))
  }

  @Test
  fun bearingLabel_foldsPastNorthBackToNorth() {
    // 349 sits inside N's half-open bucket [348.75, 360); so does 360 itself.
    assertEquals("N", JournalFormatting.bearingLabel(349.0))
    assertEquals("N", JournalFormatting.bearingLabel(360.0))
  }

  @Test
  fun bearingLabel_normalizesOutOfRange() {
    assertEquals("NE", JournalFormatting.bearingLabel(405.0)) // 405 - 360 = 45
    assertEquals("NW", JournalFormatting.bearingLabel(-45.0)) // -45 + 360 = 315
  }

  @Test
  fun confidenceLabel_readsWholePerCent() {
    assertEquals("87%", JournalFormatting.confidenceLabel(0.87))
    assertEquals("100%", JournalFormatting.confidenceLabel(1.0))
    assertEquals("0%", JournalFormatting.confidenceLabel(0.0))
  }

  @Test
  fun durationLabel_roundsUpToWholeMinutes() {
    assertEquals("1 min", JournalFormatting.durationLabel(45_000L))
    assertEquals("2 min", JournalFormatting.durationLabel(61_000L))
    assertEquals("14 min", JournalFormatting.durationLabel(14 * 60_000L))
  }

  @Test
  fun durationLabel_floorsAtOneMinute() {
    assertEquals("1 min", JournalFormatting.durationLabel(0L))
    assertEquals("1 min", JournalFormatting.durationLabel(1L))
  }

  @Test
  fun durationLabel_foldsHours() {
    assertEquals("1 hr 5 min", JournalFormatting.durationLabel(65 * 60_000L))
    assertEquals("2 hr", JournalFormatting.durationLabel(120 * 60_000L))
  }

  @Test
  fun wizardAnswerLabel_readsEachTrait() {
    assertEquals("4 of 7", JournalFormatting.wizardAnswerLabel(WizardTrait.SIZE, "4"))
    assertEquals("Black, red", JournalFormatting.wizardAnswerLabel(WizardTrait.COLORS, "BLACK,RED"))
    assertEquals(
        "On fence or wire",
        JournalFormatting.wizardAnswerLabel(WizardTrait.BEHAVIOR, "ON_FENCE_OR_WIRE"),
    )
  }

  @Test
  fun coordinates_readFixedToFourDecimals() {
    assertEquals(
        "39.1031, -84.5120",
        JournalFormatting.coordinates(Coordinate(39.10312, -84.51200)),
    )
    // A dot decimal whatever the JVM's locale, so both phones print the same digits.
    assertEquals("0.0000, 0.0000", JournalFormatting.coordinates(Coordinate(0.0, 0.0)))
  }

  @Test
  fun labels_readEachEnum() {
    assertEquals("Live outing", JournalFormatting.kindLabel(OutingKind.LIVE))
    assertEquals("Wizard entry", JournalFormatting.kindLabel(OutingKind.MANUAL))
    assertEquals("Glasses", JournalFormatting.sourceLabel(CaptureSource.GLASSES))
  }

  /**
   * All five, because this is the only place a stratum becomes words — the live chip reads it too.
   */
  @Test
  fun gazeLabel_namesEachOfTheFiveStrata() {
    assertEquals(
        listOf("Ground", "Understory", "Horizon", "Canopy", "Overhead"),
        GazeContext.entries.map(JournalFormatting::gazeLabel),
    )
  }
}
