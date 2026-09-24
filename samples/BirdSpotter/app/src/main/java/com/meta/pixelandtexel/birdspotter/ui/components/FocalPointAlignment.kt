/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.meta.pixelandtexel.birdspotter.data.catalog.FocalPoint
import kotlin.math.roundToInt

/**
 * Aligns a Crop-scaled image so its [FocalPoint] sits as close to the frame's center as the image
 * allows.
 *
 * The ideal placement puts the focal point exactly on the frame's center line; each axis then
 * clamps so the image never pulls away from an edge — a bird by the top of its photo pins the crop
 * to the top rather than revealing a strip of nothing below. With the default center point this
 * lands on the same pixel as [Alignment.Center], which is why an uncurated photo looks exactly as
 * it always did.
 *
 * Deliberately ignores [LayoutDirection], unlike `BiasAlignment`: the point is a place in the
 * *photograph*, and a bird does not move to the other side of its picture in RTL.
 */
data class FocalPointAlignment(private val focus: FocalPoint) : Alignment {

  override fun align(
      size: IntSize,
      space: IntSize,
      layoutDirection: LayoutDirection,
  ): IntOffset = IntOffset(
      offsetFor(content = size.width, container = space.width, focus = focus.x),
      offsetFor(content = size.height, container = space.height, focus = focus.y),
  )

  /** Top-left offset of the scaled content along one axis, in the container's space. */
  private fun offsetFor(content: Int, container: Int, focus: Double): Int {
    val ideal = container / 2.0 - focus * content
    val overflow = (container - content).toDouble()
    // Crop scaling makes content >= container, so the range is [overflow, 0] — but
    // written order-agnostically, a rounding-thin or undersized image stays in bounds
    // instead of crashing coerceIn with an inverted range.
    return ideal.coerceIn(minOf(overflow, 0.0), maxOf(overflow, 0.0)).roundToInt()
  }
}
