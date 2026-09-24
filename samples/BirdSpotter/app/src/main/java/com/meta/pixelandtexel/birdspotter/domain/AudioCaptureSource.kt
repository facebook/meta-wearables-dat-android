/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import kotlinx.coroutines.flow.Flow

/**
 * The one sample rate the app records at, whichever microphone it is holding.
 *
 * 16 kHz mono is the narrowest format the glasses' camera stream will carry, and the phone is asked
 * for the *same* rather than kept wide. That is a demo decision as much as a technical one: a
 * sonogram drawn from full-bandwidth phone audio is visibly taller and busier than the same bird
 * heard through the glasses, so matching the format means switching sources changes the pill and
 * nothing else on screen.
 *
 * The ceiling that follows is **8 kHz** — half the sample rate, which takes in the fundamentals of
 * almost every songbird. Recording wider would buy harmonics the strip has no room to draw.
 */
const val CaptureSampleRate = 16000

/**
 * A slice of mono audio, normalised to −1..1.
 *
 * Floats rather than the raw PCM bytes the SDK hands over: everything downstream — the window, the
 * transform, the magnitudes — is arithmetic, and converting once at the source beats converting in
 * every consumer. A plain class rather than a `data class` because the payload is an array, and
 * generated `equals` over an array compares references, which is a trap dressed as a convenience.
 */
class AudioChunk(
    val samples: FloatArray,
    val sampleRate: Int = CaptureSampleRate,
    /**
     * Which microphone this slice came out of.
     *
     * **On the chunk rather than on the source**, because `FailoverAudioSource` is one stream whose
     * answer changes halfway through a session, and [AudioCaptureSource.kind] is a constant. The
     * pill reads the latest one, so what the screen claims is always a fact about audio that has
     * actually arrived — never about a microphone that has merely been asked for.
     */
    val source: CaptureSourceKind = CaptureSourceKind.PHONE,
)

/**
 * Why a session has no sound.
 *
 * The same three answers [CameraPreviewError] gives, for the same reason — they said no, there is
 * nothing to listen with, or something else took the microphone.
 */
sealed class AudioCaptureError(message: String) : Exception(message) {

  /** Microphone permission is not granted. The Identify gate should have caught this first. */
  data object AccessDenied : AudioCaptureError("Microphone access is not granted")

  /** No microphone to open — no route, or the glasses session is not up. */
  data object Unavailable : AudioCaptureError("No microphone is available")

  /** The microphone was opened and then lost: another app took it, or the session dropped. */
  data object Interrupted : AudioCaptureError("The audio stream was interrupted")
}

/**
 * A live microphone, as one cold stream of sample slices — the spine a real-time session is built
 * on.
 *
 * The twin of [CameraPreviewSource], deliberately: same shape, same lifecycle, same
 * [CaptureSourceKind], so the glasses' microphone drops in over DAT's camera stream exactly the way
 * a `GlassesPreviewSource` will over its frames. The session holds both and never stops holding
 * this one — a viewfinder can be closed, but a session that stopped listening is not a session.
 *
 * Cold, per the architecture contract: collecting starts the microphone and cancelling stops it.
 * The stream fails with an [AudioCaptureError]; it does not complete on its own.
 */
interface AudioCaptureSource {

  /** Which microphone this is — what the session's source pill reads. */
  val kind: CaptureSourceKind

  /** Sample slices until the collector goes away. Cold: collecting is what opens the mic. */
  fun audioStream(): Flow<AudioChunk>
}
