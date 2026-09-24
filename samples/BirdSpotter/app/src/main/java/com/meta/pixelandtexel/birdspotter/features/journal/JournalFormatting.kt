/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import com.meta.pixelandtexel.birdspotter.data.journal.CaptureSource
import com.meta.pixelandtexel.birdspotter.data.journal.GazeContext
import com.meta.pixelandtexel.birdspotter.data.journal.OutingKind
import com.meta.pixelandtexel.birdspotter.data.journal.WizardTrait
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turns an outing's stored facts into the strings the Journal prints.
 *
 * Display, not data: the database keeps degrees, epoch milliseconds and raw enums, and the UI is
 * where those become "WNW", "23 Jul 2026" and "14 min". Kept in one place, and out of the
 * composables, so the Journal row and the detail screen word the same fact the same way — and so
 * the pieces with real logic, [bearingLabel] and [durationLabel], have somewhere to be tested.
 *
 * The labels are the same words on both platforms; the dates use the same fixed patterns rather
 * than each platform's localized ordering, because the demo runs the two phones side by side and
 * "23 Jul 2026" should read identically on each.
 */
object JournalFormatting {

  /**
   * The sixteen points of the compass, N at index 0, clockwise. Spelled out rather than localized —
   * the label a bearing lands on has to match on both phones.
   */
  private val compassPoints = listOf(
      "N",
      "NNE",
      "NE",
      "ENE",
      "E",
      "ESE",
      "SE",
      "SSE",
      "S",
      "SSW",
      "SW",
      "WSW",
      "W",
      "WNW",
      "NW",
      "NNW",
  )

  /**
   * A heading in degrees (0–360, true north) as a 16-point compass label — "WNW".
   *
   * 22.5° buckets, because that is the precision a magnetometer actually has: the values are
   * routinely off by tens of degrees near metal, so a degree readout would imply a certainty that
   * isn't there. See the design notes.
   *
   * The value is stored already normalized, but this folds again so a caller passing a raw reading
   * still gets a sane answer. Rounding is half-up, which for a bearing — normalized, so never
   * negative — is the same as half-away-from-zero.
   */
  fun bearingLabel(degrees: Double): String {
    val normalized = ((degrees % 360) + 360) % 360
    val index = (normalized / 22.5).roundToInt() % compassPoints.size
    return compassPoints[index]
  }

  /**
   * Where the observer was looking, as one of the five strata.
   *
   * **The only place a [GazeContext] becomes words**, on either screen — the live session's chip
   * over the viewfinder reads out of here too. That is the point of it living in one function: the
   * chip used to make its own strings and said *Horizon* where this said *Eye level*, which is one
   * stratum wearing two names depending on which screen you were on.
   */
  fun gazeLabel(gaze: GazeContext): String =
      when (gaze) {
        GazeContext.OVERHEAD -> "Overhead"
        GazeContext.CANOPY -> "Canopy"
        GazeContext.HORIZON -> "Horizon"
        GazeContext.UNDERSTORY -> "Understory"
        GazeContext.GROUND -> "Ground"
      }

  /** What shape the outing took — the detail page's eyebrow when no bird leads it. */
  fun kindLabel(kind: OutingKind): String =
      when (kind) {
        OutingKind.LIVE -> "Live outing"
        OutingKind.MANUAL -> "Wizard entry"
      }

  /** Which device made a capture. */
  fun sourceLabel(source: CaptureSource): String =
      when (source) {
        CaptureSource.GLASSES -> "Glasses"
        CaptureSource.PHONE -> "Phone"
      }

  /** A 0–1 confidence as a whole per-cent — "87%". */
  fun confidenceLabel(confidence: Double): String = "${(confidence * 100).roundToInt()}%"

  /**
   * An outing's length — "14 min", "1 hr 5 min". Whole minutes, rounded up, floored at one: the
   * Journal's clock is a memory cue, and "0 min" is not a length anyone stood outside for. Integer
   * arithmetic only, so the two platforms cannot round apart.
   */
  fun durationLabel(durationMs: Long): String {
    val totalMinutes = ((durationMs + 59_999) / 60_000).coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
      hours <= 0 -> "$totalMinutes min"
      minutes <= 0 -> "$hours hr"
      else -> "$hours hr $minutes min"
    }
  }

  /**
   * A `WIZARD_ANSWER` value as the detail page prints it, per trait: `SIZE`'s digit becomes "4 of
   * 7" (the sparrow-to-goose scale), `COLORS` and `BEHAVIOR` unfold their SCREAMING_SNAKE tokens —
   * "BLACK,RED" to "Black, red", "ON_FENCE_OR_WIRE" to "On fence or wire".
   */
  fun wizardAnswerLabel(trait: WizardTrait, value: String): String =
      when (trait) {
        WizardTrait.SIZE -> "$value of 7"
        WizardTrait.COLORS ->
            value
                .split(",")
                .filter { it.isNotEmpty() }
                .joinToString(", ") { tokenWords(it) }
                .replaceFirstChar { it.titlecase(Locale.US) }
        WizardTrait.BEHAVIOR -> tokenWords(value).replaceFirstChar { it.titlecase(Locale.US) }
      }

  /** The row label over a wizard answer — "Size", "Colors", "Behavior". */
  fun wizardTraitLabel(trait: WizardTrait): String =
      when (trait) {
        WizardTrait.SIZE -> "Size"
        WizardTrait.COLORS -> "Colors"
        WizardTrait.BEHAVIOR -> "Behavior"
      }

  /**
   * "BLACK" → "black", "ON_FENCE_OR_WIRE" → "on fence or wire". [Locale.US]: enum tokens are ASCII
   * by contract.
   */
  private fun tokenWords(token: String): String =
      token.trim().lowercase(Locale.US).replace('_', ' ')

  /**
   * "39.1031, -84.5120" — the stamped point, fixed to four decimals (~11 m), which is all a phone
   * fix is good for and all the map needs. [Locale.US] so the decimal is always a dot and the
   * digits don't shift with the device's region.
   */
  fun coordinates(coordinate: Coordinate): String =
      String.format(Locale.US, "%.4f, %.4f", coordinate.latitude, coordinate.longitude)

  /** "23 Jul 2026" — the date a Journal row prints. */
  fun date(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
      dateFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))

  /** "23 Jul 2026 at 7:14 AM" — the fuller stamp the detail screen prints. */
  fun dateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
      dateTimeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))

  /**
   * A stable, sortable bucket for a timestamp's civil month — "2026-07".
   *
   * The bucketing that [JournalViewModel.groupByMonth] runs on, so it takes the same `zone` seam a
   * test pins to a fixed timezone.
   */
  fun monthKey(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
      monthKeyFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))

  /** The heading over a month's outings — "July 2026". */
  fun monthTitle(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
      monthTitleFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))

  /**
   * Fixed patterns rather than localized styles, so the day/month/year order is the same wherever
   * the phone is set. The month name still localizes.
   */
  private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy 'at' h:mm a")
  private val monthKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
  private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
}
