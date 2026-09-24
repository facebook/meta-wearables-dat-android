/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * *We know where this is.* A small verdigris dot with a halo breathing out of it.
 *
 * It appears only once a fix has actually landed. There is no searching state and no failed state,
 * because `LocationProvider` answers `null` for every way of not knowing — denied, disabled, or
 * simply never arrived — and a dot that stayed lit to mean "still trying" would be the one
 * dishonest mark on a screen whose whole job is saying what it really heard.
 *
 * Verdigris rather than a signal green: the palette's green is the palette's green, and a colour
 * borrowed from a system status bar would be the first thing on this screen not drawn from the
 * cabinet. It is a drawn shape rather than a glyph for the same reason a rule is — there is no icon
 * here to name, only a circle and a ring, and the pulse is the half that carries the meaning.
 *
 * **It belongs beside the pin and nowhere else.** The dot is a fact about the fix, so it goes next
 * to the thing that means *where*; beside the source plate it read as a second, vaguer claim about
 * the session in general.
 */
@Composable
fun LocationDot(isLocated: Boolean, modifier: Modifier = Modifier) {
  // Runs whether or not there is a fix, so the dot fades in mid-breath when one lands — the way a
  // live indicator does — rather than starting its first pulse from nothing.
  val transition = rememberInfiniteTransition(label = "halo")
  val pulse by
      transition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
              infiniteRepeatable(
                  animation = tween(PulsePeriodMillis, easing = LinearOutSlowInEasing),
                  repeatMode = RepeatMode.Restart,
              ),
          label = "pulse",
      )

  val presence by animateFloatAsState(
      targetValue = if (isLocated) 1f else 0f,
      animationSpec = tween(ArrivalMillis),
      label = "presence",
  )

  val verdigris = BirdSpotterTheme.colors.verdigris

  // The halo's full extent, reserved: a size that grew with the pulse would push the pin beside it
  // back and forth once a second.
  Canvas(modifier.size(DotSize * HaloScale)) {
    val radius = DotSize.toPx() / 2f
    drawCircle(
        color = verdigris,
        radius = radius * (1f + (HaloScale - 1f) * pulse),
        alpha = HaloOpacity * (1f - pulse) * presence,
    )
    drawCircle(color = verdigris, radius = radius, alpha = presence)
  }
}

/** The dot itself. Small — it is a state, not a control. */
private val DotSize = 8.dp

/** How far the halo travels before it is gone, and how much of it there is to begin with. */
private const val HaloScale = 2.6f
private const val HaloOpacity = 0.5f

/** One breath. Slow enough to read as alive rather than as an alert. */
private const val PulsePeriodMillis = 1800

/** How long the dot takes to arrive once the fix does. */
private const val ArrivalMillis = 400

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Located")
@Composable
private fun LocationDotLocatedPreview() {
  BirdSpotterTheme(darkTheme = true) {
    LocationDot(isLocated = true, modifier = Modifier.padding(BirdSpotterTheme.space.page))
  }
}

@Preview(showBackground = true, name = "No fix")
@Composable
private fun LocationDotNoFixPreview() {
  BirdSpotterTheme(darkTheme = true) {
    LocationDot(isLocated = false, modifier = Modifier.padding(BirdSpotterTheme.space.page))
  }
}
