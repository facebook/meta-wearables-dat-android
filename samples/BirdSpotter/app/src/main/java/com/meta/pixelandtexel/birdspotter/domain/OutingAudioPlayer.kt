/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * Plays one saved outing's timeline — the transport the journal entry page's play button drives,
 * over the schedule an [OutingPlayback] holds.
 *
 * The deliberate twin of [AudioClipPlayer], one seam over: that one plays a bundled catalog clip by
 * asset, this one plays an outing's timeline by milliseconds, and the page reads position at its
 * own cadence from a plain getter for the same reason that one does. Position is in ms rather than
 * `0..1` because everything else on the page speaks the timeline's clock — an event's stamp *is* a
 * position here, which is what makes tapping a row a seek.
 *
 * The engine behind it — `AudioTrack` — is audio plumbing the architecture note keeps idiomatic;
 * the interface is the mirrored surface.
 */
interface OutingAudioPlayer {

  /** True between [play] and the timeline's end, a [pause], or a [release]. */
  val isPlaying: Boolean

  /** Where the playhead is on the outing's timeline, in ms. Sampled, never pushed. */
  val positionMs: Long

  /**
   * Called when the timeline plays out on its own — never for [pause] or [release]. The view model
   * returns the transport to the start here.
   */
  var onFinish: (() -> Unit)?

  /** Hands over the timeline to play. Stops anything already loaded; position returns to 0. */
  fun load(playback: OutingPlayback)

  /** Plays from the current position. A no-op before [load], and after the end until a seek. */
  fun play()

  /** Holds position. [play] resumes from here. */
  fun pause()

  /**
   * Moves the playhead. Whole ms of timeline, clamped to it — and it pauses a playing transport
   * rather than chasing the finger: a streamed engine restarts on every seek, and sixty restarts a
   * second is a zipper, not a scrub. The page plays on from wherever the finger lets go.
   */
  fun seek(toMs: Long)

  /** Stops and lets the engine go. The player is done; a new page makes a new one. */
  fun release()
}
