/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * One diagnostic line: when, how much it matters, which part of the app, and what happened.
 *
 * **A line, singular.** Every way of making one folds the runs of whitespace in the message down to
 * single spaces, so an entry can never be two lines in a file — which is why the constructor is
 * private and [Companion.invoke] is the only door in. That is not tidiness: it is what lets [parse]
 * be a regular expression instead of a state machine, and what stops a multi-line SDK error from
 * making every line after it unreadable.
 *
 * This type owns the file format, both directions: [fileLine] writes it and [parse] reads it back
 * for the viewer. One place, so the two can never drift — a log the app cannot read is a log nobody
 * will.
 *
 * [timestampMillis] is epoch milliseconds, the same convention every timestamp in `journal.db`
 * uses.
 */
@ConsistentCopyVisibility
data class LogEntry
private constructor(
    val timestampMillis: Long,
    val level: LogLevel,
    val category: LogCategory,
    /** Already collapsed to one line — see the type's own doc. */
    val message: String,
) {

  /**
   * `2026-07-29T09:14:22.913-05:00 [INFO ] [glasses] glasses reading — paired: 2`
   *
   * **Local time, with the offset spelled out.** A demo post-mortem is someone saying "it froze
   * around quarter past nine", and a column of UTC makes them do arithmetic before they can start.
   * The offset keeps it unambiguous for anyone reading the file somewhere else.
   *
   * The level is padded and both fields bracketed so the messages line up in a text editor — these
   * files get opened in one far more often than in the viewer.
   */
  val fileLine: String
    get() {
      val stamp =
          TimestampFormatter.format(
              ZonedDateTime.ofInstant(
                  Instant.ofEpochMilli(timestampMillis),
                  ZoneId.systemDefault(),
              ),
          )
      return "$stamp [${level.fileLabel}] [${category.id}] $message"
    }

  companion object {

    /** The one way to make an entry — and the one that guarantees it is a single line. */
    operator fun invoke(
        timestampMillis: Long,
        level: LogLevel,
        category: LogCategory,
        message: CharSequence,
    ): LogEntry = LogEntry(timestampMillis, level, category, collapse(message))

    /**
     * Reads a line back, or `null` if it is not one of ours.
     *
     * Unknown levels and categories fail the parse rather than defaulting, so a file written by a
     * future build degrades to "some lines the viewer shows raw" instead of one quietly mis-filed
     * under the wrong headings — see
     * [com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsLogStore.entries] for what
     * becomes of the leftovers.
     */
    fun parse(line: String): LogEntry? {
      val match = FilePattern.matchEntire(line) ?: return null
      val (stamp, rawLevel, rawCategory, message) = match.destructured
      val level = LogLevel.parse(rawLevel) ?: return null
      val category = LogCategory.parse(rawCategory) ?: return null
      val millis =
          runCatching {
            ZonedDateTime.parse(stamp, TimestampFormatter).toInstant().toEpochMilli()
          }
              .getOrNull() ?: return null
      return LogEntry(millis, level, category, message)
    }

    /** The one regular expression, matching what [fileLine] writes. */
    private val FilePattern = Regex("""(\S+) \[([A-Za-z]+) *] \[([a-z]+)] (.*)""")

    /**
     * ISO 8601 to the millisecond, in the phone's own time zone.
     *
     * `java.time` rather than `SimpleDateFormat`, which is not thread-safe — and this is formatted
     * from whichever thread logged the line.
     *
     * **`uuuu`, not `yyyy`.** The two format identically for every date this app will ever see, but
     * `yyyy` is year-*of-era* and needs an era to parse back — which the line does not carry, so a
     * `yyyy` pattern reads a line it wrote itself and fails. The bytes on disk are
     * `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` either way.
     */
    private val TimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX")

    /** Every run of whitespace — newlines included — down to one space. */
    private fun collapse(message: CharSequence): String =
        message.split(Whitespace).filter { it.isNotEmpty() }.joinToString(" ")

    private val Whitespace = Regex("""\s+""")
  }
}
