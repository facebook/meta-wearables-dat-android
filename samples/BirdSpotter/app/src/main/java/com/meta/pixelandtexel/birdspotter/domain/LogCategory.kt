/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * Which part of the app a diagnostic line came from.
 *
 * **Category, because that is what the platform calls it.** It is the logcat tag a line is written
 * under, so a developer already knows what it is from the system log they read every day. (It
 * deliberately is *not* "channel" — that word is spoken for by the identify log, where it separates
 * discrete events from continuous ones.)
 *
 * **The list answers "was that the glasses or the phone?"** That is the question a demo post-mortem
 * opens with, and it is why [GLASSES] is its own category rather than a prefix on a message: the
 * viewer filters on these, so one tap gives you every line the wearable was responsible for and
 * nothing else. The rest of the list is the phone.
 *
 * [id] is what a log file carries, so it is fixed by contract — changing one would orphan every
 * line already written.
 */
enum class LogCategory(val id: String, val displayLabel: String) {

  /** Launch, the composition root, and anything about the process itself. */
  APP("app", "App"),

  /**
   * **The glasses.** DAT registration, the paired-device roster, the session, the camera stream,
   * and photographs crossing the link. Everything the wearable does and nothing the phone does —
   * see [com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesSessionRepository].
   */
  GLASSES("glasses", "Glasses"),

  /**
   * A real-time run, end to end: started, what it heard, what it confirmed, how it finished. The
   * app's own session, not the DAT one — that is [GLASSES].
   */
  SESSION("session", "Session"),

  /**
   * Microphones. Which one is live matters more than anything else here: a run silently falling
   * back from the glasses to the phone is the failure this category exists for. See
   * [com.meta.pixelandtexel.birdspotter.data.audio.FailoverAudioSource].
   */
  AUDIO("audio", "Audio"),

  /**
   * The viewfinder and the shutter — the phone's camera. A photograph taken *through the glasses*
   * is [GLASSES], because it is the link that decides whether it arrives.
   */
  CAMERA("camera", "Camera"),

  /** Outings saved, amended and deleted, and the media that goes with them. */
  JOURNAL("journal", "Journal"),

  /**
   * The bundled field guide: opening `catalog.db`, and the seed swap when a build ships a newer
   * one.
   */
  CATALOG("catalog", "Catalog"),

  /** Camera, microphone and location grants, on the phone. */
  PERMISSIONS("permissions", "Permissions"),

  /** The Demo Director — which preset is armed, and what it answered. */
  DEMO("demo", "Demo");

  companion object {
    /**
     * Parses a category back off a log line. Unknown text is `null` — a file written by a build
     * that had a category this one does not know is still readable, and the line keeps its text
     * rather than being filed under the wrong heading.
     */
    fun parse(raw: String): LogCategory? {
      val normalized = raw.trim().lowercase()
      return entries.firstOrNull { it.id == normalized }
    }
  }
}
