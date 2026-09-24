/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.data.catalog.PlumageColor
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * Station four: main colors, up to three. Every pick must be on the bird — the query's all-of rule,
 * which is why more picks mean a shorter list (see `SpeciesStore.identifyCandidates`).
 */
@Composable
internal fun WizardColorsStep(
    colors: Set<PlumageColor>,
    onToggleColor: (PlumageColor) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Column(
      modifier = modifier.fillMaxWidth().padding(horizontal = space.gutter),
  ) {
    Text(
        text = "Pick up to three.",
        style = BirdSpotterTheme.type.label,
        color = BirdSpotterTheme.colors.textSecondary,
    )

    // Three fixed rows of three, not a flow layout: nine swatches is a constant of
    // the palette, and a grid that can never reflow has nothing to compute.
    Column(
        modifier = Modifier.padding(top = space.section),
        verticalArrangement = Arrangement.spacedBy(space.gutter),
    ) {
      PlumageColor.entries.chunked(3).forEach { rowColors ->
        Row(horizontalArrangement = Arrangement.spacedBy(space.separate)) {
          rowColors.forEach { color ->
            ColorSwatch(
                color = color,
                selected = color in colors,
                onClick = { onToggleColor(color) },
                modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

/** A filled disc with its name under it; a check badges the chosen ones. */
@Composable
private fun ColorSwatch(
    color: PlumageColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Column(
      modifier =
          modifier.selectable(
              selected = selected,
              onClick = onClick,
              role = Role.Checkbox,
          ),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
        modifier =
            Modifier.size(52.dp)
                .background(color.swatch, CircleShape)
                // Every disc carries the hairline so the white swatch has an edge; the
                // chosen ones trade it for a heavier ring in the page's own ink.
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color =
                        if (selected) {
                          BirdSpotterTheme.colors.textPrimary
                        } else {
                          BirdSpotterTheme.colors.rule
                        },
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
      if (selected) {
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.selected),
            contentDescription = null,
            tint = color.checkTint,
            modifier = Modifier.size(24.dp),
        )
      }
    }
    Spacer(Modifier.height(space.snug))
    Text(
        text = color.label,
        style = BirdSpotterTheme.type.caption,
        color = BirdSpotterTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
  }
}

internal val PlumageColor.label: String
  get() =
      when (this) {
        PlumageColor.BLACK -> "Black"
        PlumageColor.GRAY -> "Gray"
        PlumageColor.WHITE -> "White"
        PlumageColor.BROWN -> "Brown"
        PlumageColor.RED -> "Red"
        PlumageColor.ORANGE -> "Orange"
        PlumageColor.YELLOW -> "Yellow"
        PlumageColor.GREEN -> "Green"
        PlumageColor.BLUE -> "Blue"
      }

/**
 * Feather colors, not brand colors — muted toward what plumage actually looks like, and the same
 * hex values on both platforms. Data visualization, not theme, which is why they live beside the
 * step that draws them rather than in `BirdSpotterColors`.
 */
private val PlumageColor.swatch: Color
  get() =
      when (this) {
        PlumageColor.BLACK -> Color(0xFF23282A)
        PlumageColor.GRAY -> Color(0xFF97A0A2)
        PlumageColor.WHITE -> Color(0xFFF4F2EA)
        PlumageColor.BROWN -> Color(0xFF77502E)
        PlumageColor.RED -> Color(0xFFB13A2C)
        PlumageColor.ORANGE -> Color(0xFFD07A2C)
        PlumageColor.YELLOW -> Color(0xFFE0B33C)
        PlumageColor.GREEN -> Color(0xFF5B7147)
        PlumageColor.BLUE -> Color(0xFF48708F)
      }

/** Ink on the light swatches, paper on the dark — the check has to survive its disc. */
private val PlumageColor.checkTint: Color
  get() =
      when (this) {
        PlumageColor.WHITE,
        PlumageColor.YELLOW,
        PlumageColor.GRAY -> Color(0xFF10171A)
        else -> Color(0xFFF3F5F0)
      }
