/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import com.meta.pixelandtexel.birdspotter.data.journal.OutingMedia
import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.ui.components.ThumbnailWidthPx
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A photo the user captured, decoded off the main thread.
 *
 * The Journal's counterpart to `CatalogPhoto`: the same downsampled decode, but the bytes come from
 * the app's captured-media directory ([MediaFileStore]) rather than `assets/`. Captured photos
 * carry no focal point, since a phone or the glasses framed them and not our pipeline, so the crop
 * is a plain centre fill rather than `CatalogPhoto`'s focal one.
 *
 * Renders nothing until the bitmap arrives, and skips the decode entirely in Previews —
 * `LocalInspectionMode` is the signal that there is no real file behind the path.
 */
@Composable
fun OutingPhoto(
    media: OutingMedia,
    mediaFileStore: MediaFileStore,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    maxWidthPx: Int = ThumbnailWidthPx,
    /**
     * Whether the photo fills its frame and loses its edges to the crop, or fits inside it whole.
     *
     * Filling is right nearly everywhere — a thumbnail in a row is a square by design, and a bird
     * recognised at 44dp is recognised in the middle of the frame. Fitting exists for the one place
     * the *composition* is the point rather than the subject: opened full screen, a photograph
     * cropped to the phone's aspect would be a photograph with its edges quietly taken away, which
     * is precisely what somebody who tapped to see it properly did not ask for.
     */
    fillsFrame: Boolean = true,
) {
  val isPreview = LocalInspectionMode.current

  val bitmap: ImageBitmap? by
      produceState<ImageBitmap?>(null, media.id, isPreview, maxWidthPx) {
        value =
            if (isPreview) {
              null
            } else {
              withContext(Dispatchers.IO) {
                decodeSampled(mediaFileStore.resolve(media.filePath), maxWidthPx)?.asImageBitmap()
              }
            }
      }

  Box(modifier.background(BirdSpotterTheme.colors.rule)) {
    bitmap?.let {
      Image(
          bitmap = it,
          contentDescription = contentDescription,
          contentScale = if (fillsFrame) ContentScale.Crop else ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/**
 * Decodes [file] downsampled to roughly [maxWidthPx], the same intent as `CatalogAssetStore`'s
 * loader: read the bounds first, halve `inSampleSize` until the next halving would drop below the
 * target, then decode once at that scale so the full-size bitmap is never materialised.
 */
private fun decodeSampled(file: File, maxWidthPx: Int): Bitmap? {
  if (!file.exists()) return null

  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(file.absolutePath, bounds)
  if (bounds.outWidth <= 0) return null

  var sample = 1
  while (bounds.outWidth / (sample * 2) >= maxWidthPx) sample *= 2

  val options = BitmapFactory.Options().apply { inSampleSize = sample }
  return BitmapFactory.decodeFile(file.absolutePath, options)
}
