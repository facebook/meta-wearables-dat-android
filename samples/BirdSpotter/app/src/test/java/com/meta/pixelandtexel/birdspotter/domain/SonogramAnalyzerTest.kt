/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live sonogram's arithmetic: that a known tone lands in the bin it belongs to, that silence
 * sits on the floor, and that columns come out at the rate the strip is drawn at.
 *
 * These are the tests that keep every build drawing the *same picture*. The transform is written
 * out longhand precisely so it can be pinned like this — a framework FFT and a hand-rolled loop can
 * both be correct and still disagree about windowing or scaling, and nobody would notice until two
 * strips were side by side on a slide.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class SonogramAnalyzerTest {

  /** A full-scale tone at `frequency`, sampled at [CaptureSampleRate]. */
  private fun tone(frequency: Double, count: Int): AudioChunk = AudioChunk(
      FloatArray(count) { n -> sin(2.0 * PI * frequency * n / CaptureSampleRate).toFloat() },
  )

  private fun silence(count: Int) = AudioChunk(FloatArray(count))

  private fun peakBin(column: SonogramColumn): Int =
      column.magnitudes.indices.maxByOrNull { column.magnitudes[it] } ?: -1

  // ── Windowing ──────────────────────────────────────────────────────────

  @Test
  fun analyze_withAChunkShorterThanAWindow_returnsNothingAndKeepsTheSamples() {
    val analyzer = SonogramAnalyzer()

    // 400 is short of the 512 a column needs, so nothing can be said yet — but the samples
    // must not be thrown away, which the second chunk proves.
    assertTrue(analyzer.analyze(silence(400)).isEmpty())
    assertEquals(2, analyzer.analyze(silence(400)).size)
  }

  @Test
  fun analyze_producesAColumnEveryHop() {
    val analyzer = SonogramAnalyzer()

    // One second of audio: windows start at 0, 256, … up to the last one that still has 512
    // samples behind it. 62.5 columns a second, rounded down to whole windows.
    val columns = analyzer.analyze(silence(CaptureSampleRate))

    assertEquals(61, columns.size)
  }

  @Test
  fun reset_forgetsTheTail() {
    val analyzer = SonogramAnalyzer()
    analyzer.analyze(silence(400))

    analyzer.reset()

    // Without the reset these 400 would have completed a window alongside the first 400.
    assertTrue(analyzer.analyze(silence(400)).isEmpty())
  }

  // ── The transform ──────────────────────────────────────────────────────

  @Test
  fun analyze_withASineAtABinCentre_peaksInThatBin() {
    // 1000 Hz is exactly bin 32 at 31.25 Hz a bin, so there is no scalloping to allow for:
    // the tone belongs to one bin and the test can say which.
    val analyzer = SonogramAnalyzer()

    val columns = analyzer.analyze(tone(frequency = 1000.0, count = 2048))

    assertTrue(columns.isNotEmpty())
    columns.forEach { assertEquals(32, peakBin(it)) }
  }

  @Test
  fun analyze_withAFullScaleSine_reachesTheTopOfTheScale() {
    val analyzer = SonogramAnalyzer()

    val column = analyzer.analyze(tone(frequency = 1000.0, count = 1024)).first()

    // The scale is chosen so a full-scale sine reads 0 dB, which normalises to 1. Anything
    // less and the loudest thing the phone can hear would still draw dim.
    assertEquals(1f, column.magnitudes[32], 0.02f)
  }

  @Test
  fun analyze_withSilence_sitsOnTheFloor() {
    val analyzer = SonogramAnalyzer()

    val column = analyzer.analyze(silence(1024)).first()

    column.magnitudes.forEach { assertEquals(0f, it, 0.0001f) }
  }

  @Test
  fun analyze_measuresTheColumnsOwnLoudness() {
    val analyzer = SonogramAnalyzer()

    val loud = analyzer.analyze(tone(frequency = 1000.0, count = 1024)).first()
    analyzer.reset()
    val quiet = analyzer.analyze(silence(1024)).first()

    // RMS, so a full-scale sine is 0.707 — which is -3 dB, near the top of the scale without
    // pinning to it. Silence is the floor, the same floor the magnitudes are measured against.
    assertEquals(0.957f, loud.level, 0.01f)
    assertEquals(0f, quiet.level, 0.0001f)
  }

  @Test
  fun analyze_withASine_leavesTheRestOfTheColumnQuiet() {
    val analyzer = SonogramAnalyzer()

    val column = analyzer.analyze(tone(frequency = 1000.0, count = 1024)).first()

    // Hann leaks into the neighbours, so the check skips them; three bins out the column
    // should be near the floor. A rectangular window would fail this everywhere.
    column.magnitudes.forEachIndexed { bin, magnitude ->
      if (bin < 29 || bin > 35) {
        assertTrue("bin $bin was $magnitude", magnitude < 0.35f)
      }
    }
  }
}
