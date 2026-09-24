/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.domain.SonogramBins
import com.meta.pixelandtexel.birdspotter.domain.SonogramBuffer
import com.meta.pixelandtexel.birdspotter.domain.SonogramColumnsPerSecond
import com.meta.pixelandtexel.birdspotter.domain.SonogramPalette
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import kotlin.math.floor

/**
 * A saved outing's sonogram, playing back — the live strip's other half, exactly as the design doc
 * promises it: no touch while recording, all touch afterwards.
 *
 * The same instrument, read the other way round. The live strip pins *now* to the right edge and
 * the session slides under it; here the **playhead holds the centre** and the outing slides under
 * that — pressing play is what scrolls it. The whole recording is reachable the other way too: a
 * drag scrubs, a tap seeks within the window, and both hand the position up rather than keeping any
 * state of their own.
 *
 * Colours come from [SonogramPalette] rather than the theme, like every strip, so what the journal
 * shows is what the session showed. Rasterised per redraw from the buffer's window, the way
 * `SonogramStrip` does it — one bitmap, one pass, however long the outing ran.
 */
@Composable
fun OutingSonogram(
    sonogram: SonogramBuffer,
    positionMs: Long,
    totalMs: Long,
    /** The playhead under a moving finger, reported every frame of the drag. */
    onScrub: (Long) -> Unit,
    /**
     * Where the finger settled — the end of a drag, or a tap. Parted from [onScrub] so a recording
     * that was playing can pick up here rather than at every frame of the drag.
     */
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
  // The window, centred on the playhead. Read through `rememberUpdatedState` in the
  // gesture closures, which outlive any single composition — a drag must move the outing
  // from where it *is*, not from where it was when the finger first landed.
  val currentPosition by rememberUpdatedState(positionMs)
  val currentTotal by rememberUpdatedState(totalMs)

  val start = positionMs / 1000.0 - PlaybackWindowSeconds / 2
  val firstColumn = floor(start * SonogramColumnsPerSecond).toInt()
  val visibleColumns = (PlaybackWindowSeconds * SonogramColumnsPerSecond).toInt()

  // Rebuilt exactly when the window moves — and never on an unrelated recomposition.
  val strip: ImageBitmap =
      remember(sonogram.count, firstColumn) {
        val greyscale = sonogram.window(firstColumn, visibleColumns)
        val argb = IntArray(greyscale.size)
        for (i in greyscale.indices) argb[i] = MagmaRamp[greyscale[i].toInt() and 0xFF]
        Bitmap.createBitmap(argb, visibleColumns, SonogramBins, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
      }

  val playhead = BirdSpotterTheme.colors.gilt

  Box(
      modifier
          .fillMaxWidth()
          .height(PlaybackStripHeight)
          // Rounded like the reference sonogram on a bird's page: this one is read in the
          // page's gutter, not watched edge to edge like the live instrument.
          .clip(RoundedCornerShape(BirdSpotterTheme.space.snug))
          .background(StripGround)
          .semantics { contentDescription = "Recording sonogram" }
          .pointerInput(Unit) {
            detectTapGestures { offset ->
              val windowStart = currentPosition - PlaybackWindowMillis / 2
              val tapped = windowStart + (offset.x / size.width * PlaybackWindowMillis).toLong()
              onSeek(tapped.coerceIn(0L, currentTotal))
            }
          }
          .pointerInput(Unit) {
            // Dragging moves the recording under the fixed playhead, so it runs the way
            // paper would: content follows the finger, the playhead stays put.
            //
            // The drag carries its own playhead rather than reading the page's back each
            // frame: the position it reports has a composition's latency on it, and a
            // scrub that walks over its own echo drifts under a fast finger.
            var scrubbedMs = 0L
            detectHorizontalDragGestures(
                onDragStart = { scrubbedMs = currentPosition },
                // Settled — where the finger let go is where playback resumes.
                onDragEnd = { onSeek(scrubbedMs) },
                onDragCancel = { onSeek(scrubbedMs) },
            ) { change, dragAmount ->
              change.consume()
              val dragged = (dragAmount / size.width * PlaybackWindowMillis).toLong()
              scrubbedMs = (scrubbedMs - dragged).coerceIn(0L, currentTotal)
              onScrub(scrubbedMs)
            }
          },
  ) {
    Image(
        bitmap = strip,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize(),
    )

    // The playhead, dead centre — always drawn: unlike the live strip's edge line it
    // marks a position that exists at every moment of a finished recording.
    Canvas(Modifier.fillMaxSize()) {
      drawLine(
          color = playhead,
          start = Offset(size.width / 2f, 0f),
          end = Offset(size.width / 2f, size.height),
          strokeWidth = PlaybackPlayheadWidth,
      )
    }
  }
}

/**
 * Seconds across the strip — the live timeline's eight, so a bird's call is the same width on the
 * journal page as it was on the session that heard it.
 */
private const val PlaybackWindowSeconds = 8.0

private const val PlaybackWindowMillis = (PlaybackWindowSeconds * 1000).toLong()

/** The live strip's height, kept — same instrument, same proportions. */
private val PlaybackStripHeight = 132.dp

/** The playhead, in pixels — a hairline would disappear against a bright column. */
private const val PlaybackPlayheadWidth = 2f

/** The ramp, packed as ARGB once — see `SessionTimeline`'s note on why it is precomputed. */
private val MagmaRamp: IntArray =
    SonogramPalette.ramp()
        .map { 0xFF shl 24 or (it.red shl 16) or (it.green shl 8) or it.blue }
        .toIntArray()

/** Magma's darkest end — what the strip shows where the outing held no sound. */
private val StripGround = Color(MagmaRamp[0])
