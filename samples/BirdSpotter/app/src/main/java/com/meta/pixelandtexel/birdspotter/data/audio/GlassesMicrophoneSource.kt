/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.audio

import com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureError
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureSource
import com.meta.pixelandtexel.birdspotter.domain.AudioChunk
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * The glasses' microphone as an [AudioCaptureSource].
 *
 * **It rides the camera stream.** DAT carries the glasses' microphone as audio on the same stream
 * that wakes the camera, so there is no communication device to select and no audio mode to set:
 * the samples arrive from the SDK already the glasses', at the 16 kHz mono the stream was asked
 * for, and [DatGlassesSessionRepository] turns them into [AudioChunk]s. This class is the lease on
 * that feed and nothing more.
 *
 * The consequence the platform's own audio stack used to hide is that **the phone and the glasses
 * are now two independent microphones.** Opening one no longer closes the other, and a line spoken
 * while the glasses listen goes out over the media profile at full bandwidth.
 *
 * Fails with [AudioCaptureError.Unavailable] when there is nothing to hear — no session, a session
 * opened without Meta AI's microphone grant, or a stream that never delivers a first buffer inside
 * [FirstChunkPatienceMillis] — which is the signal `FailoverAudioSource` needs to keep the phone.
 */
class GlassesMicrophoneSource(private val link: DatGlassesSessionRepository) : AudioCaptureSource {

  override val kind = CaptureSourceKind.GLASSES

  override fun audioStream(): Flow<AudioChunk> = channelFlow {
    val heard = AtomicBoolean(false)
    // **A stream that never speaks is not a microphone.** A session can be up with audio on
    // its configuration and still hand over nothing — the grant read one way and the glasses
    // another — and from up here that is indistinguishable from a quiet room. The failover
    // only moves on a failure, so the silence is made into one.
    val watchdog = launch {
      delay(FirstChunkPatienceMillis)
      if (!heard.get()) {
        BirdLog.warning(LogCategory.AUDIO) {
          "the glasses' microphone never delivered a first buffer"
        }
        throw AudioCaptureError.Unavailable
      }
    }
    link.audioChunksFromActiveSession().collect { chunk ->
      heard.set(true)
      send(chunk)
    }
    watchdog.cancel()
  }
}

/**
 * How long a freshly opened glasses stream gets to deliver its first buffer before it is given up
 * on — see [GlassesMicrophoneSource]. Generous, because giving up too early is a pair of glasses
 * that looks like it has no microphone, and waiting too long only delays a fallback the phone is
 * ready for.
 */
private const val FirstChunkPatienceMillis = 5_000L
