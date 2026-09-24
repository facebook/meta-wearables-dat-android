/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.mockdevice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.data.mockdevice.MockDeviceButtonPosition
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import kotlin.math.roundToInt

/**
 * The floating way into the mock panel: the glasses glyph in a dark capsule, wearing a vermilion
 * dot so it reads as *simulated* from across the room.
 *
 * **Small, and out of the way by design.** It floats over every screen in the app while the kit is
 * on, so it has to be the least of what is on screen: one glyph, one capsule, at the same depth
 * every other piece of chrome laid over a picture in this app uses (`StopControl`'s ink at 0.6).
 * The dot is the honesty affordance — the same reason the Settings section is labelled *Demo /
 * Developer* — so nobody watching mistakes a simulated pair for the real thing.
 *
 * A tap opens the panel. A press-and-hold picks the button up, a drag carries it, and letting go
 * pins it where it was left, remembered across launches through `MockDeviceSettingsStore`.
 * Hold-then-drag rather than a plain drag, so a thumb that brushes past it on the way to something
 * else does not move it.
 *
 * @param position Where the button sits, in fractions of [bounds].
 * @param bounds The area the button may be dragged over — the whole overlay, in pixels.
 */
@Composable
fun MockDeviceButton(
    position: MockDeviceButtonPosition,
    bounds: IntSize,
    onTap: () -> Unit,
    onMove: (MockDeviceButtonPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val density = LocalDensity.current
  val sizePx = with(density) { MockButtonSize.roundToPx() }

  // The drag in flight — the offset from where the button was pinned.
  var dragOffset by remember { mutableStateOf(Offset.Zero) }
  var isLifted by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (isLifted) MockButtonLiftedScale else 1f, label = "lift")

  val pinnedX = (position.x * bounds.width).toFloat()
  val pinnedY = (position.y * bounds.height).toFloat()

  Box(
      modifier =
          modifier
              .offset {
                IntOffset(
                    (pinnedX + dragOffset.x - sizePx / 2).roundToInt(),
                    (pinnedY + dragOffset.y - sizePx / 2).roundToInt(),
                )
              }
              .scale(scale)
              .pointerInput(bounds, position) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { isLifted = true },
                    onDrag = { change, dragAmount ->
                      change.consume()
                      dragOffset += dragAmount
                    },
                    onDragEnd = {
                      val landed = Offset(pinnedX + dragOffset.x, pinnedY + dragOffset.y)
                      val inset = sizePx / 2f + with(density) { space.separate.toPx() }
                      onMove(pin(landed, bounds, inset))
                      isLifted = false
                      dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                      isLifted = false
                      dragOffset = Offset.Zero
                    },
                )
              }
              .clickable(onClick = onTap)
              .semantics { contentDescription = "Mock device controls" },
  ) {
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.glasses),
        contentDescription = null,
        tint = colors.paper,
        modifier =
            Modifier.background(colors.ink.copy(alpha = MockButtonGroundOpacity), CircleShape)
                .padding(space.snug)
                .size(MockButtonGlyphSize),
    )
    Box(
        modifier =
            Modifier.align(Alignment.TopEnd)
                .size(MockButtonDotSize)
                .background(colors.vermilion, CircleShape),
    )
  }
}

/**
 * Where a dropped button is pinned: the point it was dropped at, kept far enough inside the bounds
 * that the whole button stays on screen.
 */
internal fun pin(point: Offset, bounds: IntSize, inset: Float): MockDeviceButtonPosition {
  if (bounds.width <= 0 || bounds.height <= 0) return MockDeviceButtonPosition.Initial
  val x = point.x.coerceIn(inset, bounds.width - inset) / bounds.width
  val y = point.y.coerceIn(inset, bounds.height - inset) / bounds.height
  return MockDeviceButtonPosition(x.toDouble(), y.toDouble())
}

/** The glyph's box, and what the capsule adds around it — together the button's footprint. */
private val MockButtonGlyphSize: Dp = 20.dp
internal val MockButtonSize: Dp = 36.dp

/** The dot: small enough to sit on the capsule's shoulder, big enough to be red from a distance. */
private val MockButtonDotSize: Dp = 8.dp

/** How dark the capsule is — the one depth this app gives chrome laid over a picture. */
private const val MockButtonGroundOpacity = 0.6f

/**
 * How much a lifted button grows — enough to say *picked up*, not enough to hide what is under it.
 */
private const val MockButtonLiftedScale = 1.15f
