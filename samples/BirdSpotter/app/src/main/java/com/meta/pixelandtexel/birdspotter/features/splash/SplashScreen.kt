/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import kotlinx.coroutines.delay

/**
 * How the splash moves.
 *
 * The curves are spelled as cubic béziers at the call sites (`0.42 / 0 / 0.58 / 1` for the spin and
 * the crossfade) because a named platform ease is whatever that platform says it is; the numbers
 * are the mirrored part.
 */
object SplashMotion {
  /** The rings' travel, eased in and out. */
  const val spinMillis = 800

  /**
   * One whole turn, so the rings land back in the badge's drawn orientation — hand-drawn blobs
   * ending mid-turn would sit visibly askew of the mark.
   */
  const val spinDegrees = 360

  /** A beat at rest before the reveal. */
  const val holdMillis = 60

  /**
   * The splash dissolving into the shell. The caller owns this one — see
   * [com.meta.pixelandtexel.birdspotter.BirdSpotterApp].
   */
  const val crossfadeMillis = 160
}

/**
 * The launch moment: the badge on bare paper, its rings turning about a still swallow while the app
 * opens, then a beat at rest before the caller crossfades the whole thing away.
 *
 * The system splash frame shown before any of this is the same bare paper —
 * `windowSplashScreenBackground` in themes.xml, with the icon suppressed, because a custom
 * system-splash icon is laid out at a size the system chooses, which would jump visibly against
 * this 168 dp badge. The badge instead lands whole with the app's first frame, already at rest, and
 * begins to turn at once.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
  val spinDegrees = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    spinDegrees.animateTo(
        SplashMotion.spinDegrees.toFloat(),
        tween(SplashMotion.spinMillis, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
    )
    delay(SplashMotion.holdMillis.toLong())
    onFinished()
  }

  // Artwork, not layout: the badge is a plate drawn at the size the launch moment
  // wants, so it is deliberately not a `BirdSpotterTheme.space` role.
  val badgeSize = 168.dp

  Box(
      // Full-bleed on purpose: the splash owns the whole frame, bars and all.
      modifier = Modifier.fillMaxSize().background(BirdSpotterTheme.colors.paper),
      contentAlignment = Alignment.Center,
  ) {
    val glyphs = BirdSpotterTheme.glyphs
    val tint = BirdSpotterTheme.colors.textPrimary

    Box(modifier = Modifier.size(badgeSize)) {
      // Decoration, so no contentDescription. TalkBack users get the shell as
      // soon as it is interactive; the splash never holds focus.
      Icon(
          painter = glyph(glyphs.markRings),
          contentDescription = null,
          modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = spinDegrees.value },
          tint = tint,
      )
      Icon(
          painter = glyph(glyphs.markBird),
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          tint = tint,
      )
    }
  }
}
