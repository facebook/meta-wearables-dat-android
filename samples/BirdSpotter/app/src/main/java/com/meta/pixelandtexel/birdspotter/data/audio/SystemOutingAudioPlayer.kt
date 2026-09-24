/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.meta.pixelandtexel.birdspotter.domain.CaptureSampleRate
import com.meta.pixelandtexel.birdspotter.domain.OutingAudioPlayer
import com.meta.pixelandtexel.birdspotter.domain.OutingPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The [OutingAudioPlayer] the app ships: a streamed `AudioTrack` fed from [OutingPlayback].
 *
 * A feeder coroutine pulls the timeline through [OutingPlayback.read] and pushes it into the track
 * with blocking writes — gaps arrive as the zeros the schedule already put there, so the engine
 * never knows the outing had any. Position is the track's own playback head plus the frame the
 * current run started at; `flush` resets the head to zero, and every run starts with a flush, which
 * is what keeps that sum honest.
 *
 * Main-thread confined like the `MediaPlayer` behind [SystemAudioClipPlayer]: the transport methods
 * and [positionMs] belong to the screen's thread, the feeder touches nothing but the track and the
 * schedule, and the finish lands back on main before it mutates anything.
 *
 * The engine is plumbing, not the mirror. See [OutingAudioPlayer].
 */
class SystemOutingAudioPlayer : OutingAudioPlayer {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val mainHandler = Handler(Looper.getMainLooper())

  private var playback: OutingPlayback? = null
  private var track: AudioTrack? = null
  private var feeder: Job? = null

  /** The timeline frame the current play run started at — head zero, after its flush. */
  private var baseFrame = 0

  /** Where the playhead rests while nothing plays. */
  private var restingFrame = 0

  override var isPlaying = false
    private set

  override val positionMs: Long
    get() {
      val frame =
          if (isPlaying) {
            baseFrame + (track?.playbackHeadPosition ?: 0)
          } else {
            restingFrame
          }
      return frame * 1000L / CaptureSampleRate
    }

  override var onFinish: (() -> Unit)? = null

  override fun load(playback: OutingPlayback) {
    stopRun()
    this.playback = playback
    restingFrame = 0
  }

  override fun play() {
    val playback = playback ?: return
    if (isPlaying || restingFrame >= playback.totalFrames) return
    val track = track ?: (openTrack() ?: return).also { track = it }

    baseFrame = restingFrame
    isPlaying = true
    track.play()
    feeder = scope.launch { feed(playback, track, from = baseFrame) }
  }

  override fun pause() {
    if (!isPlaying) return
    restingFrame = baseFrame + (track?.playbackHeadPosition ?: 0)
    stopRun()
  }

  override fun seek(toMs: Long) {
    val playback = playback ?: return
    if (isPlaying) pause()
    restingFrame = OutingPlayback.frameOf(toMs).coerceIn(0, playback.totalFrames)
  }

  override fun release() {
    stopRun()
    track?.release()
    track = null
    playback = null
    scope.cancel()
  }

  /** Ends the current run, wherever it is: feeder stopped, track quiet and empty. */
  private fun stopRun() {
    isPlaying = false
    feeder?.cancel()
    feeder = null
    track?.let {
      // Pause before flush — flush is a no-op on a playing track — and flush so the
      // next run's head starts from zero, which the position sum relies on.
      runCatching { it.pause() }
      runCatching { it.flush() }
    }
  }

  /**
   * Pushes the timeline into the track until it runs out, then waits for the last buffer to
   * actually sound before calling the end an end.
   */
  private suspend fun feed(playback: OutingPlayback, track: AudioTrack, from: Int) {
    val buffer = FloatArray(FeedFrames)
    var frame = from

    while (currentCoroutineContext().isActive) {
      val valid = playback.read(frame, buffer)
      if (valid == 0) break
      val wrote = track.write(buffer, 0, valid, AudioTrack.WRITE_BLOCKING)
      // A cancelled run flushes the track and the blocked write returns short — the
      // loop check is what notices. Anything negative is the engine gone.
      if (wrote <= 0) return
      frame += wrote
      if (wrote < valid) continue
    }

    // Written is not played: the last second of the outing is still crossing the buffer.
    val framesQueued = frame - from
    while (currentCoroutineContext().isActive && track.playbackHeadPosition < framesQueued) {
      delay(PlayoutPollMillis)
    }
    if (!currentCoroutineContext().isActive) return

    mainHandler.post {
      // The run may have been re-driven from the main thread while this was in flight;
      // only the run that is still current gets to declare the outing over.
      if (isPlaying && feeder?.isActive != false) {
        restingFrame = playback.totalFrames
        stopRun()
        onFinish?.invoke()
      }
    }
  }

  private fun openTrack(): AudioTrack? {
    val minimumBytes =
        AudioTrack.getMinBufferSize(
            CaptureSampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
    if (minimumBytes <= 0) return null

    return try {
      AudioTrack.Builder()
          .setAudioAttributes(
              AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                  .build(),
          )
          .setAudioFormat(
              AudioFormat.Builder()
                  .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                  .setSampleRate(CaptureSampleRate)
                  .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                  .build(),
          )
          // Several feeds deep, so a scheduling hiccup on the feeder never underruns.
          .setBufferSizeInBytes(maxOf(minimumBytes, FeedFrames * BytesPerFloat * 4))
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()
          .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
    } catch (_: Exception) {
      null
    }
  }
}

/** Frames per feed — 128 ms at 16 kHz, the same slice the capture side moves audio in. */
private const val FeedFrames = 2048

/** How often the feeder checks whether the tail has sounded. */
private const val PlayoutPollMillis = 50L

private const val BytesPerFloat = 4
