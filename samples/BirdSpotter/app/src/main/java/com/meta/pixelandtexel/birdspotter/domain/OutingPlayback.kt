/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * One stretch of a saved outing's sound, decoded and placed: the samples, and the clock reading
 * they start at. What an `AUDIO` media row becomes once its file is read.
 */
class PlaybackSegment(
    /** ms from the outing's start — the media row's `offsetMs`. */
    val offsetMs: Long,
    val samples: FloatArray,
)

/**
 * A saved outing's audio as one continuous timeline — the edit decision list the design doc asks
 * for (the design notes, "Playing a session back as one thing").
 *
 * Sorted by offset, an outing's segments say where there is sound and where there is none. This
 * answers *give me the audio at t*: the covering segment's samples where one covers it, zeros where
 * none does — silence played, never stored. The platform players pull from [read] and know nothing
 * about segments; the seam between a glasses segment and a phone one is inaudible because both are
 * 16 kHz mono by design.
 *
 * A scheduler, not a player: it holds no engine and no position, so it is the same small, boring
 * arithmetic in both languages, and the whole thing is testable without a speaker. `AudioTrack`
 * takes it from here.
 *
 * Frames rather than ms at this seam, because ms cannot name a sample: at 16 kHz a millisecond is
 * sixteen of them. [frameOf] is the one place the conversion lives.
 */
class OutingPlayback(segments: List<PlaybackSegment>, totalMs: Long) {

  private val segments = segments.sortedBy { it.offsetMs }

  /**
   * Makeup gain applied on [read] — because the capture is deliberately unprocessed (no AGC; see
   * [com.meta.pixelandtexel.birdspotter.data.audio.MicrophoneCapture]) and a quiet morning records
   * tens of decibels under full scale. The strip hides that: it draws against a −70 dB floor, which
   * is a visual AGC, so a recording that *looks* vivid can be nearly inaudible played back raw. The
   * gain is the playback half of that honesty: the file keeps what the microphone heard; the
   * speaker gets what an ear needs.
   *
   * Aimed so the outing's **[PlaybackLoudnessPercentile] loudness** lands at [PlaybackTargetPeak] —
   * a high percentile rather than the absolute peak, because one thump of the phone against a table
   * is one sample run that would otherwise pin the gain for the whole walk while everything worth
   * hearing stayed faint. What overshoots full scale under the lift (that thump) is clamped in
   * [read], which distorts the transient and nothing else.
   *
   * Never below 1 — a hot capture plays as captured, clipping and all, because the distortion is
   * the recording's fact, not this class's to soften. Capped at [PlaybackMaxGain], so an outing of
   * near-silence is lifted into "quiet room" rather than blasted into hiss.
   */
  val gain: Float

  /**
   * The playable length in frames. The outing's own duration when it is the longer — a session can
   * end in silence worth honouring — but never shorter than the last segment's end, so a duration
   * recorded slightly shy of the audio cannot cut it off.
   */
  val totalFrames: Int = maxOf(
      frameOf(totalMs),
      this.segments.lastOrNull()?.let { frameOf(it.offsetMs) + it.samples.size } ?: 0,
  )

  val totalMs: Long
    get() = totalFrames * 1000L / CaptureSampleRate

  init {
    // The loudness reading, by histogram rather than by sorting millions of samples:
    // 1000 buckets over 0..1 magnitude, walked from the top until the allowance of
    // louder-than-this samples is spent. O(n), no allocation proportional to n, and the
    // identical paragraph in both languages.
    var total = 0
    val histogram = IntArray(LoudnessBuckets)
    for (segment in this.segments) {
      for (sample in segment.samples) {
        val magnitude = if (sample < 0) -sample else sample
        val bucket = (magnitude * LoudnessBuckets).toInt().coerceAtMost(LoudnessBuckets - 1)
        histogram[bucket]++
        total++
      }
    }

    var allowed = (total * (1.0 - PlaybackLoudnessPercentile)).toInt()
    var loudness = 0f
    var bucket = LoudnessBuckets - 1
    while (bucket >= 0) {
      allowed -= histogram[bucket]
      if (allowed < 0) {
        loudness = (bucket + 1).toFloat() / LoudnessBuckets
        break
      }
      bucket--
    }

    gain =
        if (loudness > 0f) {
          (PlaybackTargetPeak / loudness).coerceIn(1f, PlaybackMaxGain)
        } else {
          1f
        }
  }

  /**
   * Fills [into] with the timeline starting at [fromFrame]: segment samples where a segment covers,
   * zeros where none does. Returns how many frames of the timeline remain valid — the full buffer
   * until the end approaches, then the remainder, then `0`, which is how a player knows it has
   * played out.
   */
  fun read(fromFrame: Int, into: FloatArray): Int {
    into.fill(0f)
    if (fromFrame >= totalFrames) return 0
    val frames = minOf(into.size, totalFrames - fromFrame)

    for (segment in segments) {
      val segmentStart = frameOf(segment.offsetMs)
      val segmentEnd = segmentStart + segment.samples.size
      if (segmentEnd <= fromFrame) continue
      if (segmentStart >= fromFrame + frames) break

      val copyFrom = maxOf(fromFrame, segmentStart)
      val copyUntil = minOf(fromFrame + frames, segmentEnd)
      segment.samples.copyInto(
          destination = into,
          destinationOffset = copyFrom - fromFrame,
          startIndex = copyFrom - segmentStart,
          endIndex = copyUntil - segmentStart,
      )
    }
    if (gain != 1f) {
      // Clamped, not left to the engine: the one transient the percentile ignored
      // overshoots full scale under the lift, and clipping it here is deterministic
      // and identical on both platforms.
      for (i in 0 until frames) {
        into[i] = (into[i] * gain).coerceIn(-1f, 1f)
      }
    }
    return frames
  }

  companion object {
    /** The one ms-to-frames conversion. Floor, so a frame never starts before its clock time. */
    fun frameOf(ms: Long): Int = (ms * CaptureSampleRate / 1000).toInt()
  }
}

/** Where the loudness reading is aimed: just shy of full scale, headroom for the seams. */
const val PlaybackTargetPeak = 0.9f

/**
 * The most [OutingPlayback.gain] will lift a recording — 32×, about +30 dB. Enough to bring an
 * unprocessed capture up to the level an AGC-assisted one arrives at on its own; little enough that
 * a near-silent outing plays back as a quiet room rather than a wall of amplified hiss.
 */
const val PlaybackMaxGain = 32f

/**
 * Which loudness the gain normalises: the level all but the loudest 0.1 % of samples sit under.
 * High enough to be the recording's real content, deaf to the lone transient — a knock of the phone
 * against a table is a millisecond that would otherwise set the volume of the whole walk.
 */
const val PlaybackLoudnessPercentile = 0.999

/** Resolution of the loudness histogram — 1000 buckets is 0.001 of full scale each. */
private const val LoudnessBuckets = 1000
