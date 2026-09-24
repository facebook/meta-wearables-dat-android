/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.audio

import android.content.Context
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureSource
import com.meta.pixelandtexel.birdspotter.domain.AudioChunk
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import kotlinx.coroutines.flow.Flow

/**
 * The phone's microphone as an [AudioCaptureSource] — the spine of a real-time session until a pair
 * of glasses is connected, and what it falls back to when one disconnects.
 *
 * The recorder and the 16 kHz format both live in [MicrophoneCapture].
 */
class PhoneMicrophoneSource(context: Context) : AudioCaptureSource {

  private val capture = MicrophoneCapture(context)

  override val kind = CaptureSourceKind.PHONE

  override fun audioStream(): Flow<AudioChunk> = capture.audioStream()
}
