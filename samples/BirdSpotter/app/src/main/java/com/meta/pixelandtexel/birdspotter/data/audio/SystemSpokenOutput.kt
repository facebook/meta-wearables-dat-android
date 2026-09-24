/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.SpokenOutput
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The [SpokenOutput] the app ships: the platform's own speech synthesiser, playing into whatever
 * route a session's microphone is already holding.
 *
 * **Nothing here ever runs on the caller's thread, and that is not a nicety.** Every call into the
 * synthesiser crosses a process boundary — opening it, choosing the voice, setting the attributes,
 * handing over a line, stopping it — and the caller is a view model on the main thread. Opening the
 * engine binds a service, and asking it for a language can pull voice data off disk; done inline
 * those are seconds of a frozen screen on the first identification of a run, with a shorter stall
 * on every one after it. So the engine is opened lazily on [engineScope] and every later call goes
 * through it, leaving the callers with nothing but a suspension.
 *
 * **It never selects a route and never sets the audio mode.** Neither microphone sets one either:
 * the phone's records from the built-in input, and the glasses' arrives on the camera stream. What
 * this reads instead is the mode already in force, because that is what decides where a line comes
 * out: a communication use case — somebody's call — sends it down that call's link, and anything
 * else sends it to the media route, which is where a pair of glasses sits.
 *
 * **The consequence is that the app can only speak inside a live session**, which is also the only
 * time it has anything to say. An identification is a thing a session makes.
 */
class SystemSpokenOutput(context: Context) : SpokenOutput {

  private val context = context.applicationContext
  private val audioManager = this.context.getSystemService(AudioManager::class.java)

  /**
   * Where the synthesiser is opened and stopped. Never the caller's thread — see the note above.
   */
  private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /**
   * The synthesiser, opened on first use and never again — or `null` where the device has none.
   *
   * Lazy on purpose: constructing this class is what the composition root does at launch, and
   * binding to a speech service is what the *first identification* should pay for, not the splash
   * screen. Awaiting it is a suspension, so the caller keeps its thread either way.
   */
  private val engine: Deferred<TextToSpeech?> =
      engineScope.async(start = CoroutineStart.LAZY) { openEngine() }

  /**
   * The same engine once it is up, readable without waiting — what [silence] stops.
   *
   * A stop must not be able to *start* anything: reaching through [engine] would have a session
   * ending on a screen with nothing to say open a speech service in order to tell it to be quiet.
   */
  @Volatile private var live: TextToSpeech? = null

  /** Who is waiting on which line, so a finished utterance resumes the right caller. */
  private val waiting = ConcurrentHashMap<String, CancellableContinuation<Unit>>()

  /**
   * Lines handed over and not yet finished — a count rather than a flag, because lines queue: the
   * second one's caller must not clear a state the first one is still in.
   */
  private val linesInFlight = AtomicInteger()

  /** Utterance ids. Unique per line, which is all the synthesiser asks of them. */
  private val nextLineId = AtomicLong()

  private val progress =
      object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finish(utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

        @Deprecated("Superseded by the error-code form, which the platform prefers.")
        override fun onError(utteranceId: String?) = finish(utteranceId)
      }

  override val isSpeaking: Boolean
    get() = linesInFlight.get() > 0

  override suspend fun speak(words: String) {
    val line = words.trim()
    if (line.isEmpty()) return
    val engine = engine.await() ?: return

    withContext(Dispatchers.IO) {
      // The device-presence rule, at the one place that can read the actual route — see
      // [SpokenOutput]. A dropped line is worth a log entry and nothing else: the timeline
      // has already said the same thing in writing, and the session goes on unchanged.
      if (!hasSomewhereToSpeak()) {
        BirdLog.info(LogCategory.AUDIO) { "nowhere to speak — the line stays unsaid" }
        return@withContext
      }

      val lineId = nextLineId.incrementAndGet().toString()
      suspendCancellableCoroutine { continuation ->
        // Registered before the synthesiser is handed the line, so a fast finish cannot
        // arrive at an empty table and leave the caller waiting forever.
        waiting[lineId] = continuation
        linesInFlight.incrementAndGet()
        continuation.invokeOnCancellation { finish(lineId) }

        // Set per line rather than once, because which usage is right depends on the route
        // in force *now*, and a session changes ears underneath this. See [usage].
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage())
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val queued = engine.speak(line, TextToSpeech.QUEUE_ADD, null, lineId)
        if (queued != TextToSpeech.SUCCESS) {
          BirdLog.warning(LogCategory.AUDIO) { "the synthesiser would not take the line" }
          finish(lineId)
        }
      }
    }
  }

  override fun silence() {
    // Every queued line goes with it, and each one's caller is resumed through `onStop`.
    val engine = live ?: return
    engineScope.launch { engine.stop() }
  }

  /**
   * Builds the synthesiser and asks it for an English voice.
   *
   * **Asked for rather than taken from the device**, because the words being read are a catalog's
   * own — "Green Jay" is a name, and a voice built for another language sounds it out phonetically
   * rather than saying it. A device with no English voice installed keeps whatever the synthesiser
   * would have chosen, which is a strange accent rather than silence.
   *
   * Nothing is asked *of* the engine from its own init callback: it is allowed to arrive before the
   * constructor has returned, so the callback does one thing — say whether it came up — and
   * everything else happens out here, where there is a reference to talk to.
   */
  private suspend fun openEngine(): TextToSpeech? {
    val started = CompletableDeferred<Boolean>()
    val engine =
        TextToSpeech(context) { status ->
          started.complete(status == TextToSpeech.SUCCESS)
        }
    if (!started.await()) {
      BirdLog.warning(LogCategory.AUDIO) { "no speech synthesiser — nothing will be said" }
      engine.shutdown()
      return null
    }

    engine.setOnUtteranceProgressListener(progress)
    val chosen = engine.setLanguage(Locale.US)
    if (chosen == TextToSpeech.LANG_MISSING_DATA || chosen == TextToSpeech.LANG_NOT_SUPPORTED) {
      BirdLog.warning(LogCategory.AUDIO) { "no English voice installed — using the default" }
    }
    live = engine
    return engine
  }

  /**
   * Which use case a line belongs to, and therefore which route it comes out of.
   *
   * **Read from the platform rather than passed in.** Whether the phone is in a communication use
   * case is a fact about the route that is up right now — one that changes underneath a session as
   * it hands the microphone between ears — and a caller that had to say would be guessing at
   * something it cannot see. A line spoken during a call goes down that call's link; a line spoken
   * with no such use case goes to the media route, in full bandwidth.
   */
  private fun usage(): Int =
      if (audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION) {
        AudioAttributes.USAGE_VOICE_COMMUNICATION
      } else {
        AudioAttributes.USAGE_MEDIA
      }

  /**
   * Whether anything said now would land in an ear rather than in the open air.
   *
   * **The route is the question, not the pairing.** A pair of glasses that has a session and no
   * audio profile is not somewhere to speak, and a Bluetooth output that is not a pair of glasses
   * is still an ear rather than a field full of birds — so what is asked is what the audio actually
   * has in front of it. The media profile is where a line lands; the hands-free one still counts,
   * because a headset that only offers that is an ear all the same.
   */
  private fun hasSomewhereToSpeak(): Boolean {
    val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return false
    return outputs.any { device ->
      device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
          device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
          device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
          device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }
  }

  /**
   * Resumes whoever was waiting on this line, however it ended.
   *
   * Finished, stopped, cancelled and refused are the same event from up here — the words are no
   * longer being said — and taking the waiter out of the table is what makes all four of them
   * idempotent between the listener and the caller's own cancellation.
   */
  private fun finish(utteranceId: String?) {
    val continuation = waiting.remove(utteranceId ?: return) ?: return
    linesInFlight.decrementAndGet()
    if (continuation.isActive) continuation.resume(Unit)
  }
}
