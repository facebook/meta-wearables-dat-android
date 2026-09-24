/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.dat

import android.graphics.Bitmap
import com.meta.pixelandtexel.birdspotter.data.catalog.CatalogAssetStore
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.GlassesDisplayRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesError
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle

/**
 * The DAT-backed [GlassesDisplayRepository].
 *
 * Thin where the camera's twin is thin — the display capability rides the running session, whose
 * handles live in [DatGlassesSessionRepository]. **The glasses hold no state**, and every send
 * replaces the screen whole; a card that says everything it has to say at once is the layout that
 * costs that model nothing. Nothing here is remembered between sends, so a new identification is a
 * new card and there is no page to keep straight.
 *
 * The photographs are the catalog's own, scaled once at load to the display's canvas: bytes sent up
 * a Bluetooth link are time the wearer spends waiting, and the bundled 1200 px originals are four
 * times the pixels the panel can draw.
 */
class DatGlassesDisplayRepository(
    private val link: DatGlassesSessionRepository,
    private val assets: CatalogAssetStore,
) : GlassesDisplayRepository {

  override suspend fun showGallery(bird: SpeciesWithMedia, message: String?) {
    // A photograph that will not load is dropped rather than shown as a hole; a bird
    // with none still sends — the names and the description are worth having.
    val photos = bird.photos.mapNotNull { assets.loadBitmap(it, DisplayPhotoMaxPixels) }
    if (photos.size < bird.photos.size) {
      BirdLog.warning(LogCategory.GLASSES) {
        "display — ${bird.photos.size - photos.size} of ${bird.photos.size} photographs" +
            " would not load for ${bird.species.commonName}; sending without them"
      }
    }
    try {
      BirdLog.debug(LogCategory.GLASSES) {
        "display — sending ${bird.species.commonName} (${photos.size} photographs)"
      }
      link.sendThroughActiveDisplay { card(bird, photos, message) }
      // The send reporting nothing back on success is why this line exists: without it,
      // a card that landed and a card that vanished read identically from the phone.
      BirdLog.info(LogCategory.GLASSES) {
        "display — ${bird.species.commonName} is on the glass"
      }
    } catch (_: GlassesError) {
      // A pair with no display, or none on the link — the identification already
      // landed on the timeline, so there is nobody to tell but the log.
      BirdLog.info(LogCategory.GLASSES) {
        "display — no display to carry ${bird.species.commonName}"
      }
    }
  }

  override suspend fun clear() {
    link.clearActiveDisplay()
  }

  /**
   * The card: the bird's two names at the top, its photographs stacked under them, and the
   * description last — read top to bottom, the order a field guide answers in. A written [message]
   * takes the description's place and nothing else moves: the card keeps one shape however its last
   * line was authored.
   */
  private fun ContentScope.card(bird: SpeciesWithMedia, photos: List<Bitmap>, message: String?) {
    flexBox(direction = Direction.COLUMN, gap = 12, padding = 16) {
      text(bird.species.commonName, style = TextStyle.HEADING)
      text(bird.species.scientificName, style = TextStyle.META, color = TextColor.SECONDARY)
      photos.forEach { photo ->
        image(bitmap = photo)
      }
      text(message ?: bird.species.aboutText, style = TextStyle.BODY)
    }
  }

  private companion object {
    /**
     * The display's canvas is 600 × 600, and a photograph scaled past it is bytes the link carries
     * for nothing.
     */
    private const val DisplayPhotoMaxPixels = 600
  }
}
