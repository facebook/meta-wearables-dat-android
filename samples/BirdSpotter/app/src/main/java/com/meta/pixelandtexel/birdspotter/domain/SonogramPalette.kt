/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import kotlin.math.roundToInt

/**
 * One colour on the sonogram ramp, 0–255 per channel — a value, not a `Color`, because the strip is
 * built as pixels and never as composables.
 */
data class SonogramColour(val red: Int, val green: Int, val blue: Int)

/**
 * The colours a sonogram is drawn in: **magma**, black ground through purple and red to a pale
 * yellow, and the loudest thing on the strip is very nearly white.
 *
 * **Not read off the theme, and that is the point.** The catalog's reference strips are rendered by
 * the seed pipeline with `ffmpeg`'s `showspectrumpic=color=magma` — see `render_sonogram` in the
 * seed pipeline — so a live session drawn in the app's own gilt and verdigris looked like a
 * different instrument to the picture on the bird's page. A watcher comparing what they just heard
 * against the reference is comparing two spectrograms, and two spectrograms of the same bird should
 * not be two different colours. Matching the pipeline is worth more here than matching the palette.
 *
 * The stops are ffmpeg's own table, read back off the colour bar `showspectrumpic=legend=enabled`
 * draws, and thinned to the fewest that reproduce it: piecewise-linear through these eight is
 * within 3/255 of the real curve everywhere, which is under a quantization step of the 32-colour
 * PNGs the pipeline actually ships. The positions are uneven because magma is — it turns hard
 * through the purples and barely at all across the reds.
 */
object SonogramPalette {

  /** Level, then the colour at it. Ascending, first at 0 and last at 1. */
  val stops: List<Pair<Double, SonogramColour>> = listOf(
      0.0000 to SonogramColour(0, 0, 0),
      0.1006 to SonogramColour(10, 14, 105),
      0.2327 to SonogramColour(64, 22, 94),
      0.3522 to SonogramColour(155, 46, 101),
      0.4843 to SonogramColour(180, 53, 94),
      0.6415 to SonogramColour(246, 75, 81),
      0.9245 to SonogramColour(236, 204, 117),
      1.0000 to SonogramColour(253, 253, 243),
  )

  /** One entry per value a magnitude byte can take. */
  const val RampSize = 256

  /**
   * The colour at [level], 0..1. Anything outside that clamps to an end — a magnitude is already
   * normalised by the time it reaches here, and a strip needs an answer rather than an exception.
   */
  fun colour(level: Double): SonogramColour {
    if (level <= 0.0) return stops.first().second
    if (level >= 1.0) return stops.last().second

    for (index in 1 until stops.size) {
      val (highLevel, high) = stops[index]
      if (level > highLevel) continue
      val (lowLevel, low) = stops[index - 1]
      val span = highLevel - lowLevel
      val fraction = if (span > 0) (level - lowLevel) / span else 0.0
      return SonogramColour(
          red = mix(low.red, high.red, fraction),
          green = mix(low.green, high.green, fraction),
          blue = mix(low.blue, high.blue, fraction),
      )
    }
    return stops.last().second
  }

  /**
   * The whole ramp as one lookup, one entry per byte of magnitude.
   *
   * Built once and read per pixel: a window is 64,000 of them and interpolating at each would be
   * 64,000 walks of the stop list to produce 256 distinct answers.
   */
  fun ramp(): List<SonogramColour> =
      (0 until RampSize).map { colour(it.toDouble() / (RampSize - 1)) }

  private fun mix(from: Int, to: Int, fraction: Double): Int =
      (from + (to - from) * fraction).roundToInt().coerceIn(0, 255)
}
