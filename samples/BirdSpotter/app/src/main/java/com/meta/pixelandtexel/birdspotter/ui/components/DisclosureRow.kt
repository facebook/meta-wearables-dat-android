/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/** A row's own metrics. */
object RowMetrics {
  /**
   * The floor a row's tappable box is held to, whatever its ink measures.
   *
   * Not a spacing value and deliberately not on the scale: this is the size of a fingertip, which
   * is a fact about hands rather than a decision about rhythm. 48 rather than a per-platform
   * figure, so every screen lays out to one number.
   */
  val MinTapHeight = 48.dp
}

/**
 * A row that opens something: its content on the left, the disclosure caret on the right, and — the
 * whole reason it exists — **the entire width tappable**.
 *
 * `clickable` takes the row's whole box, so the gap beside a short label is tappable too. The
 * component exists so every screen gets that box from the same call rather than from a rule each
 * one has to remember.
 *
 * [onClick] is on the row rather than on a link wrapping it — the same split [CardSurface] has.
 */
@Composable
fun DisclosureRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
  val space = BirdSpotterTheme.space

  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clickable(onClick = onClick)
              // A floor on the box, deliberately *not* padding: padding here would add to the
              // spacing its column already sets and put a gap between two rows that neither of
              // them wrote down. The ink centres in whatever height this leaves.
              .defaultMinSize(minHeight = RowMetrics.MinTapHeight),
      horizontalArrangement = Arrangement.spacedBy(space.related),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(space.tight),
        content = content,
    )
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.disclosure),
        contentDescription = contentDescription,
        tint = BirdSpotterTheme.colors.textSecondary,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun DisclosureRowPreview() {
  BirdSpotterTheme {
    Column(modifier = Modifier.padding(BirdSpotterTheme.space.gutter)) {
      DisclosureRow(onClick = {}) {
        Text(
            text = "Meta AI Glasses",
            style = BirdSpotterTheme.type.headline,
            color = BirdSpotterTheme.colors.textPrimary,
        )
        Text(
            text = "Status and connection",
            style = BirdSpotterTheme.type.label,
            color = BirdSpotterTheme.colors.textSecondary,
        )
      }
      HairlineRule()
      DisclosureRow(onClick = {}) {
        Text(
            text = "Demo Director",
            style = BirdSpotterTheme.type.headline,
            color = BirdSpotterTheme.colors.textPrimary,
        )
      }
    }
  }
}
