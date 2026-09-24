/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.catalog

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a catalog [SpeciesMedia.assetKey] into bytes on this platform.
 *
 * The shipped database stores logical keys (`northern-cardinal/photo-01`) and no paths, so this is
 * the one class that knows they live under `assets/birds/`. Everything above the data layer passes
 * rows around and never builds a path.
 *
 * The read-only mirror of [com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore], which
 * owns what the user captured. Neither knows about the other.
 */
class CatalogAssetStore(private val assets: AssetManager) {

  /** Path within `assets/`, e.g. `birds/northern-cardinal/photo-01.jpg`. */
  fun assetPath(media: SpeciesMedia): String = "$ROOT/${media.assetKey}.${media.type.fileExtension}"

  /**
   * The same file as a URI, for APIs that take one — `MediaPlayer.setDataSource`, and any image
   * loader we adopt later.
   */
  fun assetUri(media: SpeciesMedia): String = "file:///android_asset/${assetPath(media)}"

  suspend fun exists(media: SpeciesMedia): Boolean =
      withContext(Dispatchers.IO) {
        try {
          assets.open(assetPath(media)).close()
          true
        } catch (_: FileNotFoundException) {
          false
        }
      }

  /** Caller closes it. */
  fun openStream(media: SpeciesMedia): InputStream = assets.open(assetPath(media))

  /**
   * An open descriptor for [media], for APIs that read a byte range rather than a stream —
   * `MediaPlayer.setDataSource`. MP3s ship uncompressed in the APK (aapt leaves already- compressed
   * formats alone), so `openFd` returns a real range here where it would throw for a deflated
   * asset. Caller closes it.
   */
  fun openFd(media: SpeciesMedia): AssetFileDescriptor = assets.openFd(assetPath(media))

  /**
   * Decodes a bundled photo, halving it until it is no wider than [maxWidthPx].
   *
   * Returns null instead of throwing when the asset is missing: a shipped file that went astray
   * should leave a card without its photo, not take the screen down.
   *
   * Deliberately no cache and no third-party image loader. The screens built so far show one photo
   * at a time from local storage, where a decode is a few milliseconds and a dependency would be
   * the more expensive thing to justify in a sample app that Meta will publish. A carousel or a
   * Journal grid changes that calculus — reach for Coil then, and this method is the seam it slots
   * into.
   */
  suspend fun loadBitmap(media: SpeciesMedia, maxWidthPx: Int): Bitmap? =
      withContext(Dispatchers.IO) {
        try {
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          openStream(media).use { BitmapFactory.decodeStream(it, null, bounds) }

          val options =
              BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, maxWidthPx)
              }
          openStream(media).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: FileNotFoundException) {
          null
        }
      }

  companion object {
    private const val ROOT = "birds"

    fun open(context: Context): CatalogAssetStore = CatalogAssetStore(context.assets)

    /** Largest power-of-two downscale that keeps the decode at or above [maxWidthPx]. */
    internal fun sampleSizeFor(sourceWidth: Int, maxWidthPx: Int): Int {
      if (sourceWidth <= 0 || maxWidthPx <= 0) return 1
      var sample = 1
      while (sourceWidth / (sample * 2) >= maxWidthPx) sample *= 2
      return sample
    }
  }
}

/**
 * The file extension the seed pipeline guarantees for each media type: photos ship as JPEG,
 * vocalizations as MP3, sonograms as PNG (the seed pipeline). Storing it per row would be a column
 * that is the same value 465 times over.
 *
 * PNG rather than JPEG for the sonogram because it is a synthetic image of hard-edged marks on a
 * flat ground — exactly what JPEG smears and what a small palette compresses better than JPEG can.
 */
internal val SpeciesMediaType.fileExtension: String
  get() =
      when (this) {
        SpeciesMediaType.PHOTO -> "jpg"
        SpeciesMediaType.SONG,
        SpeciesMediaType.CALL -> "mp3"
        SpeciesMediaType.SONOGRAM -> "png"
      }
