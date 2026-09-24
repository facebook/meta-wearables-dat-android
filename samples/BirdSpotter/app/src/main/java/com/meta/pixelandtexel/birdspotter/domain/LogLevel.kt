/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * How much a diagnostic line matters — the four every logging library has settled on.
 *
 * Ordered, and the order is the whole point: [BirdLog.minimumLevel] keeps a line only when its
 * level is at least that one, so raising the floor to [WARNING] silences the two below it without
 * any call site learning about it. Declaration order *is* severity order, which is what
 * `Comparable` on an enum class gives for free.
 *
 * **Four, not five.** No `TRACE`/`VERBOSE` tier: this app has one thing that would fill one — audio
 * buffers arriving eighty times a second — and a per-buffer line is not a diagnostic, it is a
 * denial of service against the file the diagnostics are in. Anything that frequent gets logged as
 * a *transition* instead.
 *
 * [id] is what a log file carries, so it is fixed by contract: changing one would orphan every line
 * already written.
 */
enum class LogLevel(val id: String) {

  /**
   * The step-by-step: a stream attaching, a fallback taken, a file written. Off by default in
   * release, on for the demo team.
   */
  DEBUG("debug"),

  /**
   * The beats worth reading back — a session starting, a photograph landing, an outing saved. What
   * a post-mortem is reconstructed from.
   */
  INFO("info"),

  /**
   * Something went the long way round but the app carried on: a capture that failed and fell back
   * to the phone, a preset that would not decode.
   */
  WARNING("warning"),

  /** Something the user can see is broken. */
  ERROR("error");

  /** Fixed width, so a column of them in a log file and in the viewer stays a column. */
  val fileLabel: String =
      when (id) {
        "debug" -> "DEBUG"
        "info" -> "INFO "
        "warning" -> "WARN "
        else -> "ERROR"
      }

  /** What the viewer prints. Trimmed — a screen has its own alignment. */
  val displayLabel: String
    get() = fileLabel.trim()

  companion object {
    /**
     * Parses a level back off a log line, or out of a stored preference.
     *
     * **Both spellings, because the two callers write different ones.** A log line carries
     * [fileLabel] (`WARN`) and a stored preference carries [id] (`warning`), and a parser that knew
     * only the second silently failed every warning line in every file.
     *
     * Unknown text is `null` rather than a guess: a line whose level cannot be read is better shown
     * as whatever the reader chooses than filed under a severity it never had.
     */
    fun parse(raw: String): LogLevel? {
      val normalized = raw.trim().lowercase()
      return entries.firstOrNull {
        it.id == normalized || it.displayLabel.lowercase() == normalized
      }
    }
  }
}
