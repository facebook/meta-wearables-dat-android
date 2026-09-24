/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import android.graphics.Bitmap
import com.meta.pixelandtexel.birdspotter.data.journal.CaptureSource
import com.meta.pixelandtexel.birdspotter.data.journal.GazeContext

/**
 * What a detector reports.
 *
 * **Time is deliberately not on it.** A detector knows *what* it heard, not *when* the session
 * thinks that was — and those are different clocks. Session time is measured in sonogram columns
 * (see [RealtimeSession]), which only start once the microphone actually opens; a detector that
 * stamped its own wall-clock offsets would put a bird on the timeline slightly ahead of the sound
 * that caused it, which is exactly the thing a timeline exists to get right. [at] is where the two
 * meet.
 */
sealed interface SessionFinding {

  /**
   * A bird, with the confidence to say how sure.
   *
   * [spokenLine] is words to say **instead of** the sentence the app would compose for this bird
   * ([heardAloud]), for a source that has better ones than a name and a number make. Something that
   * recognises birds reports what it heard rather than what to say about it, and leaves this empty;
   * a scripted call is the case that does not. Either way the words only reach a wearer where there
   * is somewhere to say them, and the timeline keeps no trace of them — what was said is not what
   * was found.
   */
  data class Bird(
      val speciesId: String,
      val commonName: String,
      val confidence: Float,
      val spokenLine: String? = null,
  ) : SessionFinding

  /** Something the watcher said out loud. */
  data class Speech(val text: String) : SessionFinding

  /**
   * The app answering in words rather than with a bird — an ambiguity it cannot settle ("Green Jay
   * or Blue Jay?"). The one finding that is a question back.
   */
  data class Answer(val text: String) : SessionFinding

  /** Stamps this finding at [seconds] into the session, turning it into a [SessionEvent]. */
  fun at(seconds: Double): SessionEvent =
      when (this) {
        is Bird -> SessionEvent.Bird(seconds, speciesId, commonName, confidence)
        is Speech -> SessionEvent.Speech(seconds, text)
        is Answer -> SessionEvent.Answer(seconds, text)
      }
}

/**
 * What looking at a photo came back with — or that nothing has, yet.
 *
 * **A photo's answer belongs to the photo.** It used to land as a second row a second or two
 * further down the log, which read as a bird the microphone had heard: two entries, no visible
 * relation, and the picture that produced the answer already scrolled away from it. Here the answer
 * is a field on the capture, so the row grows the result in place — the photo, then the wait, then
 * the name, on one line.
 *
 * [Pending] is the wait, and it is only ever set when an answer is genuinely coming: a capture with
 * nothing scripted for it carries `null`, and a photo whose answer named a bird the catalog cannot
 * resolve is *cleared* to `null` rather than left spinning at a row that will never arrive.
 */
sealed interface PhotoIdentification {

  /** The app is looking. What the log draws the working dots for. */
  data object Pending : PhotoIdentification

  /** A bird, and how sure — the app's answer, and what can enter the life list. */
  data class Bird(
      val speciesId: String,
      val commonName: String,
      val confidence: Float,
  ) : PhotoIdentification

  /**
   * The app answering in words instead: an ambiguity it will not settle from a picture ("Green Jay
   * or Blue Jay?"), or a decline. It names nobody, so nothing about it reaches the journal — the
   * same silence a spoken answer with no species keeps.
   */
  data class Words(val text: String) : PhotoIdentification
}

/**
 * One thing that happened during a session, at a known moment in it.
 *
 * [at] is **seconds since the session started**, not a wall clock: the timeline's x-axis is that
 * number, scrubbing is arithmetic on it, and a session survives the clock changing under it.
 *
 * Inputs and outputs are drawn on opposite sides of the strip — [Photo] and [Speech] are things
 * going in, [Bird] is the app's answer coming out — so which is which is a glance rather than a
 * read.
 */
sealed interface SessionEvent {

  /** Seconds since the session started. */
  val at: Double

  /**
   * A photo the watcher took, without leaving the session.
   *
   * It carries its own moment — which device took it, and where that device was aimed — because the
   * photo is the one event that *is* a moment of aiming, and the journal stores the aim on the
   * media row (see `MomentContext`). The aim comes from the same device as [source]: a glasses
   * photograph carries none, because the phone's compass in a lowered hand says nothing about where
   * the wearer was looking.
   *
   * It also carries its own [identification] — see [PhotoIdentification]. [index] is what a late
   * answer finds this row by: the Nth capture of the session, which is already the key the
   * Director's photo section is written against, and steadier than a stamp the collision nudge is
   * free to move.
   *
   * **[image] is nullable because a photograph off the glasses is a row before it is a picture.**
   * The Bluetooth crossing takes about a second, and the row is stamped at the press rather than at
   * the arrival so that the log moves the instant the shutter is pressed — see
   * `RealtimeViewModel.captureThroughGlasses`. `null` is that gap and nothing else: a phone capture
   * has its frame in hand and never passes through it, and a crossing that fails takes its row away
   * with it rather than leaving an empty one.
   */
  data class Photo(
      override val at: Double,
      val index: Int,
      val image: Bitmap?,
      val source: CaptureSource,
      val gazeContext: GazeContext?,
      val bearingDeg: Double?,
      val identification: PhotoIdentification? = null,
  ) : SessionEvent

  /** The app's answer: a bird, and how sure it is. */
  data class Bird(
      override val at: Double,
      val speciesId: String,
      val commonName: String,
      val confidence: Float,
  ) : SessionEvent

  /** What the watcher said, transcribed. */
  data class Speech(override val at: Double, val text: String) : SessionEvent

  /**
   * The app answering in words — a question it cannot settle, a spoken description agreed with, a
   * photo it declines to name. Set in gilt like [Bird], because it is the app talking.
   *
   * [question] carries the watcher's words it answered, when it answered any, which is what lets
   * the journal keep the exchange whole. [speciesId] and [commonName] are the bird surfaced
   * *alongside* the answer, when the script links one — a real identification, deliberately without
   * a confidence: the app did not guess, the watcher described it, and a number there would be
   * inventing precision.
   */
  data class Answer(
      override val at: Double,
      val text: String,
      val question: String? = null,
      val speciesId: String? = null,
      val commonName: String? = null,
  ) : SessionEvent
}

/**
 * A run of the real-time screen: when it started, and everything that has landed on it since.
 *
 * Elapsed time is **not** stored here, because it is not this type's to know: the session's clock
 * is the sonogram itself — one column every [SonogramHop] samples — so elapsed is derived from how
 * many columns have been drawn. Audio and timeline therefore cannot drift apart, which a separate
 * wall-clock timer would eventually manage within a minute or two.
 *
 * [startedAt] is epoch milliseconds, matching the journal's convention, and is here for the day a
 * finished session becomes an outing. Nothing writes it yet — a session whose detections are
 * scripted is not something to persist.
 */
data class RealtimeSession(
    val startedAt: Long,
    val events: List<SessionEvent> = emptyList(),
) {

  /**
   * The session with one more thing on it, still in time order.
   *
   * Sorted rather than appended, because a photo is stamped when the shutter fires and a detection
   * when the detector speaks, and nothing guarantees those arrive in the order they happened.
   *
   * **Stamps are also identities** — the log keys its rows by them — so an exact collision is
   * nudged a millisecond forward until it is alone. Two readings of a millisecond clock inside one
   * millisecond are how a photo and its zero-delay scripted response would otherwise land on the
   * same instant, and a timeline whose rows are keyed by *when* cannot hold two whens that are the
   * same number.
   */
  fun adding(event: SessionEvent): RealtimeSession {
    var stamped = event
    while (events.any { it.at == stamped.at }) {
      stamped = stamped.withAt(stamped.at + StampNudgeSeconds)
    }
    return copy(events = (events + stamped).sortedBy { it.at })
  }

  /**
   * The session with the [index]th photo's answer filled in — the arrival half of
   * [PhotoIdentification.Pending].
   *
   * Found by the capture's index rather than by its stamp, because the stamp is an identity the
   * collision nudge may already have moved and the index is the same number the answer was asked
   * for under. A photo that is no longer here — a fresh session, a stopped one — is simply not
   * found, and the session comes back unchanged.
   *
   * The order does not move: an answer landing changes what a row says, not when it happened.
   */
  fun resolvingPhoto(index: Int, identification: PhotoIdentification?): RealtimeSession = copy(
      events =
          events.map { event ->
            if (event is SessionEvent.Photo && event.index == index) {
              event.copy(identification = identification)
            } else {
              event
            }
          },
  )

  /**
   * The session with the [index]th photo's **picture** filled in — the arrival half of a crossing
   * from the glasses, where [resolvingPhoto] is the arrival half of the answer about it.
   *
   * Two methods rather than one taking both, because the two arrivals are genuinely separate
   * moments: the picture lands when Bluetooth finishes, the answer when the Director has composed
   * one, and only the first of those exists on the phone. Found by index for the same reason, and
   * unchanged when the row has gone.
   */
  fun resolvingPhoto(index: Int, image: Bitmap): RealtimeSession = copy(
      events =
          events.map { event ->
            if (event is SessionEvent.Photo && event.index == index) {
              event.copy(image = image)
            } else {
              event
            }
          },
  )

  /**
   * The session without the [index]th photo — a crossing that failed, taking its own row with it.
   *
   * **The row leaves rather than staying to say it failed.** A photograph that never arrived is not
   * a thing that happened during the session; it is a thing that did not. What says so is the one
   * line under the header, which is where every other way of losing the glasses is already
   * explained, and a permanent gravestone on the timeline would outlive the sentence that made
   * sense of it.
   */
  fun discardingPhoto(index: Int): RealtimeSession =
      copy(events = events.filterNot { it is SessionEvent.Photo && it.index == index })

  /**
   * Everything inside a window of the timeline, in order — what the strip draws.
   *
   * Inclusive at both ends: an event exactly on the edge belongs to the window it is on the edge
   * of, which matters when the window is the live one and [to] is *now*.
   */
  fun eventsBetween(from: Double, to: Double): List<SessionEvent> = events.filter {
    it.at >= from && it.at <= to
  }
}

/** How far [RealtimeSession.adding] moves a colliding stamp: one millisecond, the clock's grain. */
private const val StampNudgeSeconds = 0.001

/** The same event a moment later — what the collision nudge is made of. */
private fun SessionEvent.withAt(seconds: Double): SessionEvent =
    when (this) {
      is SessionEvent.Photo -> copy(at = seconds)
      is SessionEvent.Bird -> copy(at = seconds)
      is SessionEvent.Speech -> copy(at = seconds)
      is SessionEvent.Answer -> copy(at = seconds)
    }
