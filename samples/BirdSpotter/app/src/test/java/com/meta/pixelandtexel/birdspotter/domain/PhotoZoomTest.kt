/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The lightbox's arithmetic: what magnification is allowed, how far a magnified picture may be
 * dragged, and what the two gestures agree about at the boundary between them.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class PhotoZoomTest {

  /**
   * A viewport wider than it is tall, and a picture fitted into it letterboxed — the shape a 4:3
   * capture actually takes on a phone held upright, and the one where the two axes have genuinely
   * different answers.
   */
  private val viewport = Size(400f, 800f)
  private val content = Size(400f, 300f)

  @Test
  fun idle_isFittedAndCentred() {
    assertEquals(1f, PhotoZoom.IDLE.scale, 0f)
    assertEquals(0f, PhotoZoom.IDLE.offsetX, 0f)
    assertEquals(0f, PhotoZoom.IDLE.offsetY, 0f)
    assertFalse(PhotoZoom.IDLE.isZoomed)
  }

  @Test
  fun isZoomed_ignoresTheResidueOfAPinchThatReturned() {
    // What a pinch back to 1 actually lands on. Reading this as magnified would leave a
    // picture that cannot be dismissed for a reason nobody can see.
    val settled = PhotoZoom(1.00000001f, 0f, 0f)

    assertFalse(settled.isZoomed)
  }

  @Test
  fun settled_clampsScaleToTheCeiling() {
    val zoomed = PhotoZoom(99f, 0f, 0f).settled(viewport, content)

    assertEquals(MAX_SCALE, zoomed.scale, 0f)
  }

  @Test
  fun settled_clampsScaleToTheFittedPicture() {
    val shrunk = PhotoZoom(0.2f, 0f, 0f).settled(viewport, content)

    assertEquals(1f, shrunk.scale, 0f)
  }

  @Test
  fun settled_holdsAnUnmagnifiedPictureCentred() {
    // At 1 the picture fits by construction, so there is nowhere to drag it — every offset
    // clamps back to nothing.
    val dragged = PhotoZoom(1f, 120f, -90f).settled(viewport, content)

    assertEquals(0f, dragged.offsetX, 0f)
    assertEquals(0f, dragged.offsetY, 0f)
  }

  @Test
  fun settled_letsAnEdgeReachTheViewportAndNoFurther() {
    // 400 wide at 2× is 800 across a 400 viewport: 400 of overhang, split between two edges.
    val dragged = PhotoZoom(2f, 999f, 0f).settled(viewport, content)

    assertEquals(200f, dragged.offsetX, 0f)
  }

  @Test
  fun settled_clampsEachAxisAgainstItsOwnOverhang() {
    // The picture is letterboxed: at 2× it overhangs the width and still falls short of the
    // height, so one axis pans and the other cannot.
    val dragged = PhotoZoom(2f, 999f, 999f).settled(viewport, content)

    assertEquals(200f, dragged.offsetX, 0f)
    assertEquals(0f, dragged.offsetY, 0f)
  }

  @Test
  fun settled_pullsThePictureBackAsItShrinks() {
    // The ordering this type depends on: a picture dragged to its limit at 4× and then zoomed
    // out has to be brought back towards the centre, or it sits off to one side with a band of
    // black beside it.
    val atLimit = PhotoZoom(4f, 999f, 0f).settled(viewport, content)
    val zoomedOut = PhotoZoom(2f, atLimit.offsetX, 0f).settled(viewport, content)

    assertEquals(600f, atLimit.offsetX, 0f)
    assertEquals(200f, zoomedOut.offsetX, 0f)
  }

  @Test
  fun scaledBy_magnifiesAboutTheCentre() {
    val stepped = PhotoZoom(1.5f, 40f, 20f).scaledBy(2f, viewport, content)

    assertEquals(3f, stepped.scale, 0f)
    // The offset grew with the picture, so whatever was under the fingers stayed there.
    assertEquals(80f, stepped.offsetX, 0f)
    assertEquals(40f, stepped.offsetY, 0f)
  }

  @Test
  fun pannedBy_movesWithTheFinger() {
    // 3× rather than 2×, because the picture is letterboxed: at 2× it is 600 tall in an 800
    // viewport and the vertical drag has nowhere to go, which would test the clamp instead of
    // the travel. At 3× both axes genuinely overhang.
    val dragged = PhotoZoom(3f, 0f, 0f).pannedBy(30f, -15f, viewport, content)

    assertEquals(30f, dragged.offsetX, 0f)
    assertEquals(-15f, dragged.offsetY, 0f)
  }

  @Test
  fun pannedBy_cannotPullAnEdgeInsideTheViewport() {
    val dragged = PhotoZoom(2f, 150f, 0f).pannedBy(500f, 0f, viewport, content)

    assertEquals(200f, dragged.offsetX, 0f)
  }

  @Test
  fun toggledZoom_magnifiesFromRest() {
    val tapped = PhotoZoom.IDLE.toggledZoom(viewport, content)

    assertEquals(DOUBLE_TAP_SCALE, tapped.scale, 0f)
    assertEquals(0f, tapped.offsetX, 0f)
    assertEquals(0f, tapped.offsetY, 0f)
  }

  @Test
  fun toggledZoom_goesAllTheWayOutFromAnyMagnification() {
    // Out wins whether the picture was double-tapped to 2.5 or pinched past it — a double tap
    // is the gesture for undoing what was just done, so it never zooms further.
    val fromDoubleTap = PhotoZoom(DOUBLE_TAP_SCALE, 0f, 0f).toggledZoom(viewport, content)
    val fromCeiling = PhotoZoom(MAX_SCALE, 600f, 0f).toggledZoom(viewport, content)

    assertEquals(PhotoZoom.IDLE, fromDoubleTap)
    assertEquals(PhotoZoom.IDLE, fromCeiling)
  }

  @Test
  fun settled_survivesAViewportWithNoAreaYet() {
    // The first layout pass hands over zeroes, and the arithmetic runs before the picture has
    // been measured. Nothing here may divide by that.
    val measured = PhotoZoom(2f, 50f, 50f).settled(Size.Zero, Size.Zero)

    assertEquals(2f, measured.scale, 0f)
    assertEquals(0f, measured.offsetX, 0f)
    assertEquals(0f, measured.offsetY, 0f)
  }
}
