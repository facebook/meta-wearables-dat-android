/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * The surface a panel stands on before it has anything to show: the lacquer, **screened** — an
 * ordered dither laid over it in the app's cream, fine enough to read as frost rather than as
 * pattern.
 *
 * **Dither, not blur.** A frosted panel usually means a backdrop blur, and a backdrop blur is a
 * thing each OS hands over on its own terms, or not at all — so building one would be choosing the
 * exact drift the mirrored-architecture rule exists to prevent. An ordered dither is arithmetic:
 * the same 8×8 matrix, the same tile, the same picture anywhere, with nothing borrowed from the OS.
 *
 * It is also the *better* answer for this cabinet. Everything here is printed — plate labels,
 * engraved rules, a sonogram rendered like a plate — and a screened tone is how printing has made a
 * half-tint since the aquatint. A system glass effect would be the one surface in the app that came
 * from somewhere else.
 *
 * **Bayer, specifically**, because it is the dither with no randomness in it: a `Random()` speckle
 * would be a different picture on each platform, on each launch, and on each redraw. The matrix
 * below is the standard 8×8 — every value 0–63 exactly once, arranged so that thresholding it at
 * any level gives the most even spread of dots that level allows.
 *
 * The tile carries **alpha only** and is tinted as it is drawn, so the colour still comes from the
 * theme rather than being baked into pixels. That is what lets the ink be `textPrimary` and follow
 * the palette instead of being a cream hex sitting outside it.
 */
@Composable
fun FrostedGround(modifier: Modifier = Modifier) {
  val ground = BirdSpotterTheme.colors.lacquerRaised
  val ink = BirdSpotterTheme.colors.textPrimary

  val screen = remember {
    ShaderBrush(
        ImageShader(
            image = bayerTile().asImageBitmap(),
            tileModeX = TileMode.Repeated,
            tileModeY = TileMode.Repeated,
        ),
    )
  }

  Canvas(modifier.fillMaxSize()) {
    drawRect(color = ground)
    // A `ShaderBrush` takes no "do not interpolate" request, so the cells are bilinear rather
    // than hard-edged. At one dp that is the difference between a screen and a slightly softer
    // screen — worth knowing about, not worth a second drawing path to fix.
    drawRect(brush = screen, alpha = FrostOpacity, colorFilter = ColorFilter.tint(ink))
  }
}

/**
 * The screen itself: one 8×8 tile. White throughout, carrying the matrix in its alpha — which is
 * the half the tint keeps.
 */
private fun bayerTile(): Bitmap {
  val side = BayerSide
  val argb = IntArray(side * side)
  for (i in argb.indices) {
    val alpha = BayerMatrix[i] * 255 / (side * side - 1)
    argb[i] = (alpha shl 24) or 0x00FFFFFF
  }
  return Bitmap.createBitmap(argb, side, side, Bitmap.Config.ARGB_8888)
}

/**
 * The standard 8×8 Bayer threshold matrix, row-major — every value 0–63 exactly once.
 *
 * If one side is ever changed the two grounds stop being the same picture, which is the only thing
 * this drawing has to get right.
 */
private val BayerMatrix = intArrayOf(
    0,
    32,
    8,
    40,
    2,
    34,
    10,
    42,
    48,
    16,
    56,
    24,
    50,
    18,
    58,
    26,
    12,
    44,
    4,
    36,
    14,
    46,
    6,
    38,
    60,
    28,
    52,
    20,
    62,
    30,
    54,
    22,
    3,
    35,
    11,
    43,
    1,
    33,
    9,
    41,
    51,
    19,
    59,
    27,
    49,
    17,
    57,
    25,
    15,
    47,
    7,
    39,
    13,
    45,
    5,
    37,
    63,
    31,
    55,
    23,
    61,
    29,
    53,
    21,
)

private const val BayerSide = 8

/**
 * How much cream the screen carries at its heaviest. Frost is a *hint* of light on a dark ground —
 * past this the panel stops reading as lacquer with a tint and starts reading as grey.
 */
private const val FrostOpacity = 0.12f

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Frosted")
@Composable
private fun FrostedGroundPreview() {
  BirdSpotterTheme(darkTheme = true) {
    FrostedGround(modifier = Modifier.size(300.dp, 200.dp))
  }
}
