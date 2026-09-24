/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import kotlin.math.PI
import kotlin.math.cos

/**
 * *Working on it.* Three dots, brightening and fading one after the other.
 *
 * The app's one indeterminate indicator, and it is **drawn rather than borrowed**. A Material
 * `CircularProgressIndicator` is a picture the platform draws at a rate the platform chooses —
 * exactly the drift the glyph set exists to prevent — where a cosine over three circles is
 * arithmetic, and therefore the same animation wherever it runs.
 *
 * It says *waiting on something that has not answered*, and it is used in the two places on the
 * real-time screen where that is true: the source pill while a pair of glasses is being reached
 * for, and a photo on the log whose identification is still composing. It carries **no progress** —
 * nothing here knows how far along anything is, and a bar that filled would be inventing a number.
 *
 * A row of dots rather than a spinner, deliberately. It sits inline, beside a word or a thumbnail,
 * at the height of a line of text — a spinning ring at that size is a smudge, and three dots read
 * as *thinking* at any size a plate does.
 */
@Composable
fun WorkingDots(
    color: Color = BirdSpotterTheme.colors.textSecondary,
    modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "working")
  // One cycle, linear, read by all three dots at different offsets — one clock rather than three,
  // so the wave cannot drift apart.
  val phase by
      transition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
              infiniteRepeatable(
                  animation = tween(CyclePeriodMillis, easing = LinearEasing),
                  repeatMode = RepeatMode.Restart,
              ),
          label = "phase",
      )

  Canvas(
      modifier.size(
          width = DotSize * DotCount + DotGap * (DotCount - 1),
          height = DotSize,
      ),
  ) {
    val radius = DotSize.toPx() / 2f
    val step = (DotSize + DotGap).toPx()
    repeat(DotCount) { index ->
      drawCircle(
          color = color,
          radius = radius,
          center = Offset(radius + step * index, size.height / 2f),
          alpha = dotOpacity(phase, index),
      )
    }
  }
}

/**
 * How lit a dot is at this point in the cycle: a cosine, so the wave has no corner in it and the
 * three dots hand off to one another rather than blinking.
 *
 * The stagger is a third of a cycle per dot, which is what makes the light look like it is
 * *travelling* along the row rather than three lamps pulsing near each other.
 */
internal fun dotOpacity(phase: Float, index: Int): Float {
  val offset = phase - index.toFloat() / DotCount
  val wave = (cos(2.0 * PI * offset).toFloat() + 1f) / 2f
  return DimOpacity + (1f - DimOpacity) * wave
}

/** How many dots there are — and the stagger's denominator. */
internal const val DotCount = 3

/** A dot. Small enough to sit on a line of text without setting the line's height. */
private val DotSize = 5.dp

/** The air between them. Under a dot's width, so the three read as one mark. */
private val DotGap = 3.dp

/**
 * How far down a dot goes at its dimmest. Never to nothing — a row that empties reads as broken.
 */
private const val DimOpacity = 0.25f

/** One pass of the light along the row. Unhurried; this is a wait, not an alarm. */
private const val CyclePeriodMillis = 1200

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Working")
@Composable
private fun WorkingDotsPreview() {
  BirdSpotterTheme(darkTheme = true) {
    WorkingDots(modifier = Modifier.padding(BirdSpotterTheme.space.page))
  }
}

@Preview(showBackground = true, name = "Working, in gilt")
@Composable
private fun WorkingDotsGiltPreview() {
  BirdSpotterTheme(darkTheme = true) {
    WorkingDots(
        color = BirdSpotterTheme.colors.gilt,
        modifier = Modifier.padding(BirdSpotterTheme.space.page),
    )
  }
}
