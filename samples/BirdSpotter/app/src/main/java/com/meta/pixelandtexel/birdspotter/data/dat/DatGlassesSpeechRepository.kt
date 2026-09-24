/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.dat

import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechState
import com.meta.pixelandtexel.birdspotter.domain.Transcription
import kotlinx.coroutines.flow.Flow

/**
 * The DAT-backed [GlassesSpeechRepository].
 *
 * Thin on purpose, for the same reason [DatGlassesMotionRepository] is: the speech capability is
 * scoped to the running session, and the session — with its handles — lives in
 * [DatGlassesSessionRepository]. This type exists so the *domain* keeps the recogniser and the
 * session apart the way the feature docs split them, while the data layer admits they are one
 * Bluetooth link underneath.
 */
class DatGlassesSpeechRepository(
    private val link: DatGlassesSessionRepository,
) : GlassesSpeechRepository {

  override fun transcriptionStream(): Flow<Transcription> = link.transcriptionsFromActiveSession()

  override fun speechStateStream(): Flow<GlassesSpeechState> = link.speechStateFromActiveSession()
}
