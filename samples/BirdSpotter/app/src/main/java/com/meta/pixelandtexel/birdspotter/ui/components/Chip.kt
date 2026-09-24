/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * Which ink a [Chip] is set in. Not a palette — three roles, and the caller picks by meaning rather
 * than by colour.
 */
enum class ChipTone {
  /** The default: a fact about the row, in the quiet hand. */
  NEUTRAL,

  /** The app's own answer — a bird, a confidence. Gilt, as everywhere else. */
  ANSWER,

  /** Something the watcher or the device supplies — an input, a source. */
  INPUT,
}

/** A chip's own metrics. */
object ChipMetrics {
  /** Squared off like [CardMetrics], for the same reason: printed, not padded. */
  val CornerRadius = 2.dp
}

/**
 * A small ruled tag carrying one fact — `92%`, `2.2s`, `green-jay`.
 *
 * Chips are how a summary row says several short things without becoming a sentence: the Demo
 * Director's preset page prints a row's confidence, delay and species as three of these rather than
 * one comma-spliced line.
 *
 * Ruled and washed rather than filled, so a run of them reads as annotation beside the row's own
 * text rather than as a row of buttons.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.NEUTRAL,
) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val ink: Color =
      when (tone) {
        ChipTone.NEUTRAL -> colors.textSecondary
        ChipTone.ANSWER -> colors.gilt
        ChipTone.INPUT -> colors.verdigris
      }
  val shape = RoundedCornerShape(ChipMetrics.CornerRadius)
  Text(
      text = text,
      style = BirdSpotterTheme.type.label,
      color = ink,
      modifier =
          modifier
              .background(
                  color = if (tone == ChipTone.NEUTRAL) Color.Transparent else colors.giltWash,
                  shape = shape,
              )
              .border(width = 1.dp, color = colors.rule, shape = shape)
              .padding(horizontal = space.snug, vertical = space.tight),
  )
}

@Preview(showBackground = true)
@Composable
private fun ChipPreview() {
  BirdSpotterTheme {
    Chip(text = "green-jay · 92%", tone = ChipTone.ANSWER)
  }
}
