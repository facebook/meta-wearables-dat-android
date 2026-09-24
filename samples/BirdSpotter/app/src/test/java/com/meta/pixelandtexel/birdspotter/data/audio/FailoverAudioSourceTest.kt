/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

@file:Suppress("UseOfRunBlocking", "UseOfRunBlockingInTest")

package com.meta.pixelandtexel.birdspotter.data.audio

import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureError
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureSource
import com.meta.pixelandtexel.birdspotter.domain.AudioChunk
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One microphone stream over two real ones: the phone until the glasses are asked for, the glasses
 * while they answer, and the phone again — opened once — the moment they give out.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class FailoverAudioSourceTest {

  @Test
  fun audioStream_whenTheGlassesGiveOut_opensThePhoneOnce() = runBlocking {
    // The glasses fail on the spot; the phone answers for as long as it is asked to.
    val glasses = CountingAudioSource(CaptureSourceKind.GLASSES, fails = true)
    val phone = CountingAudioSource(CaptureSourceKind.PHONE, fails = false)
    val failover = FailoverAudioSource(preferred = glasses, fallback = phone)
    failover.usePreferred(true)

    val heard = failover.audioStream().take(3).toList().map { it.source }

    // The whole bug: a failover that reopened the phone once per chunk — or thousands of
    // times a second between chunks — instead of once.
    assertEquals(List(3) { CaptureSourceKind.PHONE }, heard)
    assertEquals(1, glasses.opens)
    assertEquals(1, phone.opens)
  }

  @Test
  fun audioStream_whenTheRequestChanges_handsOverOnce() = runBlocking {
    val glasses = CountingAudioSource(CaptureSourceKind.GLASSES, fails = false)
    val phone = CountingAudioSource(CaptureSourceKind.PHONE, fails = false)
    val failover = FailoverAudioSource(preferred = glasses, fallback = phone)

    val heard = mutableListOf<CaptureSourceKind>()
    failover.audioStream().take(4).collect { chunk ->
      heard += chunk.source
      if (heard.size == 2) failover.usePreferred(true)
    }

    assertEquals(listOf(CaptureSourceKind.PHONE, CaptureSourceKind.PHONE), heard.take(2))
    assertEquals(listOf(CaptureSourceKind.GLASSES, CaptureSourceKind.GLASSES), heard.drop(2))
    assertEquals(1, phone.opens)
    assertEquals(1, glasses.opens)
  }

  @Test
  fun audioStream_whenThePhoneWillNotOpen_keepsWaitingForIt() = runBlocking {
    // A phone locked in a pocket refuses its recorder until the app is seen again, and a
    // phone call takes it mid-walk: neither is the session ending. The phone is asked again
    // until it answers, and the stream never fails.
    val glasses = CountingAudioSource(CaptureSourceKind.GLASSES, fails = false)
    val phone = CountingAudioSource(CaptureSourceKind.PHONE, failsFirst = 2)
    val failover = FailoverAudioSource(
        preferred = glasses,
        fallback = phone,
        fallbackRetryDelayMillis = 5,
    )

    val heard = failover.audioStream().take(2).toList().map { it.source }

    assertEquals(List(2) { CaptureSourceKind.PHONE }, heard)
    assertEquals(3, phone.opens)
    assertEquals(0, glasses.opens)
  }

  @Test
  fun audioStream_whenMicrophoneAccessIsRevoked_endsTheSession() = runBlocking {
    // The one refusal nothing waits out: the grant is gone, and no amount of asking again
    // brings it back — that is the Identify gate's to fix.
    val glasses = CountingAudioSource(CaptureSourceKind.GLASSES, fails = false)
    val phone = CountingAudioSource(
        CaptureSourceKind.PHONE,
        failsFirst = Int.MAX_VALUE,
        failsWith = AudioCaptureError.AccessDenied,
    )
    val failover = FailoverAudioSource(
        preferred = glasses,
        fallback = phone,
        fallbackRetryDelayMillis = 5,
    )

    val failure = runCatching { failover.audioStream().toList() }.exceptionOrNull()

    assertEquals(AudioCaptureError.AccessDenied, failure)
    assertEquals(1, phone.opens)
  }
}

/**
 * A microphone that counts how many times it was opened, and either fails — at once, for the first
 * few opens, or with a particular refusal — or yields a chunk every few milliseconds until it is
 * cancelled.
 */
private class CountingAudioSource(
    override val kind: CaptureSourceKind,
    private val failsFirst: Int,
    private val failsWith: AudioCaptureError = AudioCaptureError.Unavailable,
) : AudioCaptureSource {

  constructor(
      kind: CaptureSourceKind,
      fails: Boolean,
  ) : this(kind, failsFirst = if (fails) Int.MAX_VALUE else 0)

  private val count = AtomicInteger()

  val opens: Int
    get() = count.get()

  override fun audioStream(): Flow<AudioChunk> = flow {
    if (count.incrementAndGet() <= failsFirst) throw failsWith
    while (true) {
      emit(AudioChunk(floatArrayOf(0.1f), source = kind))
      delay(5)
    }
  }
}
