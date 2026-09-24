/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.meta.pixelandtexel.birdspotter.data.catalog.CatalogAssetStore
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMedia
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * Hero photos are decoded to roughly a phone's width. The bundled files are 1200 px, so this halves
 * them once on most devices and keeps a 4 MB bitmap off the heap.
 */
const val HeroPhotoWidthPx = 720

/**
 * Browse-list thumbnails are 64 dp on a 3× screen. Decoding the full 1200 px file for a row that
 * small is what turns a 93-row scroll into a memory problem.
 */
const val ThumbnailWidthPx = 200

/**
 * A bundled catalog photo, decoded off the main thread.
 *
 * Renders nothing at all until the bitmap arrives rather than flashing a grey box for one frame —
 * the decode of a local JPEG usually beats the next frame anyway. Previews skip it entirely:
 * `LocalInspectionMode` is the signal that there is no real `AssetManager` behind `LocalContext`.
 *
 * The crop fills the frame and keeps the photo's [FocalPointAlignment] target in view — harvested
 * photos put the bird anywhere, and a plain center crop routinely beheads it.
 *
 * [contentDescription] is set only where the photo is the sole thing identifying the bird. It stays
 * null on a card that prints the species name right underneath, where labelling the image would
 * make TalkBack read the bird twice.
 */
@Composable
fun CatalogPhoto(
    media: SpeciesMedia,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    maxWidthPx: Int = HeroPhotoWidthPx,
) {
  val context = LocalContext.current
  val isPreview = LocalInspectionMode.current

  val bitmap: ImageBitmap? by
      produceState<ImageBitmap?>(null, media.id, isPreview, maxWidthPx) {
        value =
            if (isPreview) {
              null
            } else {
              CatalogAssetStore.open(context).loadBitmap(media, maxWidthPx)?.asImageBitmap()
            }
      }

  Box(modifier.background(BirdSpotterTheme.colors.rule)) {
    bitmap?.let {
      Image(
          bitmap = it,
          contentDescription = contentDescription,
          contentScale = ContentScale.Crop,
          alignment = FocalPointAlignment(media.focalPoint),
          modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
