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
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The crop arithmetic behind every [CatalogPhoto]: a 400×600 portrait or an 800×300 panorama
 * landing in a 400×300 frame, the shapes Crop scaling actually produces.
 *
 * Scenario names are fixed by the testing-parity rule — `align_ignoresLayoutDirection` excepted,
 * since it pins Compose layout plumbing rather than the shared arithmetic.
 */
class FocalPointAlignmentTest {

  private fun align(
      focus: FocalPoint,
      content: IntSize,
      container: IntSize,
      direction: LayoutDirection = LayoutDirection.Ltr,
  ) = FocalPointAlignment(focus).align(content, container, direction)

  /** An uncurated photo must land on the exact pixel the old center crop chose. */
  @Test
  fun align_centeredFocus_matchesCenterCrop() {
    val content = IntSize(400, 600)
    val container = IntSize(400, 300)

    assertEquals(
        Alignment.Center.align(content, container, LayoutDirection.Ltr),
        align(FocalPoint(0.5, 0.5), content, container),
    )
    assertEquals(IntOffset(0, -150), align(FocalPoint(0.5, 0.5), content, container))
  }

  /** With room to move, the focal point sits exactly on the frame's center line. */
  @Test
  fun align_offCenterFocus_centersTheSubject() {
    // Focus at 0.4 of a 600px-tall image is pixel 240; offset -90 puts it at the
    // 300px frame's midline (240 - 90 = 150).
    assertEquals(
        IntOffset(0, -90),
        align(FocalPoint(0.5, 0.4), IntSize(400, 600), IntSize(400, 300)),
    )
  }

  /** A bird by the photo's edge pins the crop there rather than revealing a void. */
  @Test
  fun align_focusNearTheEdge_clampsToTheImageEdge() {
    val content = IntSize(400, 600)
    val container = IntSize(400, 300)

    assertEquals(IntOffset(0, 0), align(FocalPoint(0.5, 0.05), content, container))
    assertEquals(IntOffset(0, -300), align(FocalPoint(0.5, 0.95), content, container))
  }

  /** The same arithmetic, sideways — a panorama pans instead of lifting. */
  @Test
  fun align_landscapeCrop_pansHorizontally() {
    assertEquals(
        IntOffset(-360, 0),
        align(FocalPoint(0.7, 0.5), IntSize(800, 300), IntSize(400, 300)),
    )
  }

  /** The point is a place in the photograph; RTL must not move the bird. */
  @Test
  fun align_ignoresLayoutDirection() {
    val content = IntSize(800, 300)
    val container = IntSize(400, 300)
    val focus = FocalPoint(0.7, 0.5)

    assertEquals(
        align(focus, content, container, LayoutDirection.Ltr),
        align(focus, content, container, LayoutDirection.Rtl),
    )
  }
}
