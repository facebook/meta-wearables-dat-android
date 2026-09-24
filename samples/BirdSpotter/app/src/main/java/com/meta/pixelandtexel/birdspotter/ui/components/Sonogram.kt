/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.meta.pixelandtexel.birdspotter.data.catalog.CatalogAssetStore
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMedia
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMediaType
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/** The strip is 640 × 160 out of the pipeline. */
private const val SonogramAspect = 4f
private const val SonogramWidthPx = 1200

/**
 * The spectrogram of a bird's reference clip, drawn under the play button with a playhead that
 * tracks playback.
 *
 * The seed pipeline renders the strip from the same trimmed audio the play button plays, so the
 * playhead crossing a mark and the ear hearing it line up. Shown whole, never cropped: a
 * spectrogram is data — a center crop would lop off the top of every bird's frequency range — so
 * this fits the image where [CatalogPhoto] fills its frame. Previews skip the decode:
 * `LocalInspectionMode` is the signal that there is no real `AssetManager` behind `LocalContext`,
 * exactly as in [CatalogPhoto].
 *
 * [onScrub], when given, makes the strip a scrubber: a tap or a horizontal drag reports a `0..1`
 * position, which the bird page hands to the view model's `seek`. The playhead follows the finger
 * from local state while dragging, so it stays put under the touch even as the clip plays on and
 * the ticker keeps writing [progress] underneath.
 */
@Composable
fun Sonogram(
    media: SpeciesMedia,
    progress: Double,
    modifier: Modifier = Modifier,
    onScrub: ((Double) -> Unit)? = null,
) {
  val context = LocalContext.current
  val isPreview = LocalInspectionMode.current

  val bitmap: ImageBitmap? by
      produceState<ImageBitmap?>(null, media.id, isPreview) {
        value =
            if (isPreview) {
              null
            } else {
              CatalogAssetStore.open(context).loadBitmap(media, SonogramWidthPx)?.asImageBitmap()
            }
      }

  // Where the finger is during a scrub, which the playhead follows instead of [progress].
  var dragFraction by remember { mutableStateOf<Double?>(null) }
  val position = dragFraction ?: progress

  // Read here so the Canvas closure closes over colors, not the theme's composition local.
  val scrim = BirdSpotterTheme.colors.lacquer
  val playhead = BirdSpotterTheme.colors.gilt

  // A tap or a horizontal drag scrubs; detectHorizontalDragGestures leaves a vertical swipe to
  // the page's scroll, so a scroll that happens to start on the strip still scrolls.
  val scrub = onScrub
  val scrubModifier =
      if (scrub != null) {
        Modifier.pointerInput(Unit) {
              detectTapGestures { offset ->
                scrub((offset.x / size.width).coerceIn(0f, 1f).toDouble())
              }
            }
            .pointerInput(Unit) {
              detectHorizontalDragGestures(
                  onDragStart = { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f).toDouble()
                    dragFraction = fraction
                    scrub(fraction)
                  },
                  onDragEnd = { dragFraction = null },
                  onDragCancel = { dragFraction = null },
              ) { change, _ ->
                val fraction = (change.position.x / size.width).coerceIn(0f, 1f).toDouble()
                dragFraction = fraction
                scrub(fraction)
              }
            }
      } else {
        Modifier
      }

  Box(
      modifier
          .fillMaxWidth()
          .aspectRatio(SonogramAspect)
          .clip(RoundedCornerShape(BirdSpotterTheme.space.snug))
          // Ground and placeholder both read as the strip's own black, so a slow decode or a
          // missing file leaves a dark plate rather than a flash of paper.
          .background(BirdSpotterTheme.colors.lacquer)
          // Labelled once on the container, so the image and the playhead overlay do not
          // read as two nodes.
          .semantics { contentDescription = "Sonogram" }
          .then(scrubModifier),
  ) {
    bitmap?.let {
      Image(
          bitmap = it,
          contentDescription = null,
          contentScale = ContentScale.FillWidth,
          modifier = Modifier.fillMaxSize(),
      )
    }

    if (position > 0.0) {
      Canvas(Modifier.fillMaxSize()) {
        val x = (size.width * position.coerceIn(0.0, 1.0)).toFloat()

        // Dim what has not played yet; the swept head stays at full strength.
        drawRect(
            color = scrim.copy(alpha = 0.5f),
            topLeft = Offset(x, 0f),
            size = Size(size.width - x, size.height),
        )

        // The playhead, a touch heavier under the finger.
        val headWidth = if (dragFraction != null) 3f else 2f
        drawRect(
            color = playhead,
            topLeft = Offset(x - headWidth / 2f, 0f),
            size = Size(headWidth, size.height),
        )
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SonogramPreview() {
  BirdSpotterTheme {
    Sonogram(
        media =
            SpeciesMedia(
                id = "northern-cardinal-sonogram-01",
                speciesId = "northern-cardinal",
                type = SpeciesMediaType.SONOGRAM,
                assetKey = "northern-cardinal/sonogram-01",
                isPrimary = false,
                sortOrder = 0,
            ),
        progress = 0.45,
    )
  }
}
