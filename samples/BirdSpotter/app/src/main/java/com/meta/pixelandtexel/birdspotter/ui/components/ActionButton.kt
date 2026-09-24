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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * How much weight an [ActionButton] carries. Not a palette — three ranks, and the caller picks by
 * which action the screen would rather you took.
 */
enum class ActionButtonTone {
  /** The action the screen is offering. Verdigris, filled, one to a section. */
  PRIMARY,

  /**
   * Available, but not what you came for — a reset, a revert, an escape hatch. Ruled and unfilled,
   * so it reads as the same plate with the fill taken out of it — the ink stays the primary's
   * verdigris, because it is the same offer at a lower volume.
   */
  SECONDARY,

  /**
   * The one that takes something away and does not give it back. Ruled and unfilled like
   * [SECONDARY], but inked and ruled in `vermilion`.
   *
   * **Unfilled deliberately.** A solid vermilion bar would be the loudest mark on the page — louder
   * than the card it sits under, and louder than the primary action, which is not the ranking a
   * delete deserves. The colour is doing the warning; the plate stays the shape every other action
   * on the page has.
   */
  DESTRUCTIVE,
}

/**
 * A full-width squared button — the app's one button treatment.
 *
 * Cut to [CardMetrics.CornerRadius] and ruled like [CardSurface], for the same reason: a Material
 * pill would read as a slab of UI where this wants to read as something printed. It fills the width
 * it is given so a section's action lands as a bar rather than as a line of text that happens to be
 * clickable — which is what an accent `Text` in a stack of accent `Text`s looks like, and is
 * exactly how the Demo Director's foot became unreadable.
 *
 * **The plate is verdigris, not gilt.** Gilt is the ink the app *labels* in — every eyebrow, every
 * section plate, every named bird — and a gilt bar in a page of gilt plates is one more warm mark
 * rather than the thing to press. Verdigris is already the app's colour for *this commits
 * something*: the wizard's claim and the review screen's Save both wear it, and a button is the
 * same sentence wherever it is offered.
 *
 * **Primary reverses the page out of the verdigris.** The ink is `paper`, which sits at the far end
 * of the ramp from `verdigris` in both appearances — light on the day palette's deep green, dark on
 * the night palette's pale one — so one token reads correctly in both without an on-verdigris
 * colour of its own.
 */
@Composable
fun ActionButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionButtonTone = ActionButtonTone.PRIMARY,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val shape = RoundedCornerShape(CardMetrics.CornerRadius)

  val fill =
      when (tone) {
        ActionButtonTone.PRIMARY -> colors.verdigris
        ActionButtonTone.SECONDARY,
        ActionButtonTone.DESTRUCTIVE -> Color.Transparent
      }
  val ink =
      when (tone) {
        ActionButtonTone.PRIMARY -> colors.paper
        ActionButtonTone.SECONDARY -> colors.verdigris
        ActionButtonTone.DESTRUCTIVE -> colors.vermilion
      }
  val stroke =
      when (tone) {
        ActionButtonTone.PRIMARY -> Color.Transparent
        ActionButtonTone.SECONDARY -> colors.rule
        ActionButtonTone.DESTRUCTIVE -> colors.vermilion
      }

  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape)
              .background(fill)
              .border(1.dp, stroke, shape)
              .clickable(onClick = onClick)
              .defaultMinSize(minHeight = RowMetrics.MinTapHeight)
              .padding(vertical = space.related, horizontal = space.cardInset),
      contentAlignment = Alignment.Center,
  ) {
    Text(text = title, style = BirdSpotterTheme.type.headline, color = ink)
  }
}

@Preview(showBackground = true)
@Composable
private fun ActionButtonPreview() {
  BirdSpotterTheme {
    Column(
        modifier = Modifier.padding(BirdSpotterTheme.space.gutter),
        verticalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.separate),
    ) {
      ActionButton(title = "New preset", onClick = {})
      ActionButton(title = "Reset to starter", onClick = {}, tone = ActionButtonTone.SECONDARY)
      ActionButton(title = "Delete preset", onClick = {}, tone = ActionButtonTone.DESTRUCTIVE)
    }
  }
}
