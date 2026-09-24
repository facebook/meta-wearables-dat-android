/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.dat

import com.meta.pixelandtexel.birdspotter.domain.CaptureQuality
import com.meta.pixelandtexel.birdspotter.domain.CaptureResolution
import com.meta.pixelandtexel.birdspotter.domain.CapturedPhoto
import com.meta.pixelandtexel.birdspotter.domain.GlassesCameraRepository
import com.meta.pixelandtexel.birdspotter.domain.PhotoFormat

/**
 * The DAT-backed [GlassesCameraRepository].
 *
 * Thin on purpose: a capture is scoped to the running session's camera capability, and the session
 * — with its handles — lives in [DatGlassesSessionRepository]. This type exists so the *domain*
 * keeps camera and session apart the way the feature docs split them, while the data layer admits
 * they are one Bluetooth link underneath.
 */
class DatGlassesCameraRepository(
    private val link: DatGlassesSessionRepository,
) : GlassesCameraRepository {

  /**
   * **False, because stills take the stream's own channel here.** The capability that takes a size
   * and a compression is the one whose stills ride the file-transfer channel. The stream path is
   * what has been proven on hardware, and it is what the session still captures through.
   *
   * The request still carries both settings — the day the shutter path is proven on the glasses,
   * the settings are already being asked for and this reading is the one line that changes.
   */
  override val honoursCaptureSettings = false

  override suspend fun capturePhoto(
      format: PhotoFormat,
      resolution: CaptureResolution,
      quality: CaptureQuality,
  ): CapturedPhoto = link.captureThroughActiveCamera(format, resolution, quality)
}
