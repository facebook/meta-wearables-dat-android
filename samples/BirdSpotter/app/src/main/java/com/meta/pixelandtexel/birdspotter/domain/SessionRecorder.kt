/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * One unbroken stretch of recording: which microphone, where it starts on the session's timeline,
 * and every sample it heard.
 *
 * [durationMs] is measured from the samples rather than read off the clock a second time — `samples
 * / 16000 × 1000`, per the design notes. Within a segment the samples place themselves; only the
 * start needs the clock.
 */
class RecordedSegment(
    val source: CaptureSourceKind,
    /** The clock reading at the segment's first sample — ms from the session's start. */
    val offsetMs: Long,
    val samples: FloatArray,
) {
  val durationMs: Long
    get() = samples.size * 1000L / CaptureSampleRate
}

/**
 * Keeps what a session hears, as the segments the journal will store — one per unbroken stretch,
 * split where the microphone changed or genuinely stopped.
 *
 * The strip and this recorder listen to the same chunks and must agree about time, so the boundary
 * rule is [placement]'s, reused rather than restated: ordinary buffer jitter never splits a
 * segment, and a silence long enough to draw on the strip is long enough to be a gap between two
 * rows. A gap is never padded with zeros — that would record a quiet that was never heard — it is
 * simply the distance between one segment's end and the next one's start.
 *
 * Memory, not disk: at 16 kHz a minute of floats is under 4 MB, and the photos the session already
 * holds cost more. The staging directory the design doc asks for — where the media lives before
 * Save — is still the right answer for the forty-minute pocket session; when it lands, this class
 * is where the samples leave through.
 *
 * Not thread-safe, and not required to be: chunks arrive on the session's one collect loop, and
 * [segments] is read at Save, after they have stopped.
 */
class SessionRecorder {

  private val closed = mutableListOf<RecordedSegment>()

  private var openSource: CaptureSourceKind? = null
  private var openOffsetMs = 0L
  private val openChunks = mutableListOf<FloatArray>()
  private var openSampleCount = 0

  /**
   * Adds one chunk, splitting the take where the chunk says the microphone changed or the clock
   * says it stopped. [atSeconds] is the session clock at the chunk's arrival — the same reading the
   * strip places it with.
   */
  fun record(chunk: AudioChunk, atSeconds: Double) {
    if (chunk.samples.isEmpty()) return

    val clockColumn = (atSeconds * SonogramColumnsPerSecond).toInt()
    val headColumn = (headMs() / 1000.0 * SonogramColumnsPerSecond).toInt()
    val changedSource = openSource != null && openSource != chunk.source
    val stopped = openSource != null && placement(clockColumn, headColumn) != headColumn

    if (openSource == null || changedSource || stopped) {
      close()
      openSource = chunk.source
      openOffsetMs = (atSeconds * 1000).toLong()
    }

    openChunks += chunk.samples
    openSampleCount += chunk.samples.size
  }

  /**
   * Every segment so far, the still-open one included — Save reads this, and a save that fails and
   * is retried reads it again, so nothing is consumed.
   */
  fun segments(): List<RecordedSegment> {
    val open = openSource?.let { source ->
      RecordedSegment(source = source, offsetMs = openOffsetMs, samples = concatOpen())
    }
    return closed + listOfNotNull(open)
  }

  /** Forgets the session. The next one records onto nothing, like the strip. */
  fun reset() {
    closed.clear()
    openSource = null
    openOffsetMs = 0L
    openChunks.clear()
    openSampleCount = 0
  }

  /** Where the open segment's audio has reached on the timeline. */
  private fun headMs(): Long = openOffsetMs + openSampleCount * 1000L / CaptureSampleRate

  private fun close() {
    val source = openSource ?: return
    closed += RecordedSegment(source = source, offsetMs = openOffsetMs, samples = concatOpen())
    openSource = null
    openChunks.clear()
    openSampleCount = 0
  }

  /** The open chunks as one array — paid per split and per Save, never per chunk. */
  private fun concatOpen(): FloatArray {
    val all = FloatArray(openSampleCount)
    var at = 0
    for (chunk in openChunks) {
      chunk.copyInto(all, at)
      at += chunk.size
    }
    return all
  }
}
