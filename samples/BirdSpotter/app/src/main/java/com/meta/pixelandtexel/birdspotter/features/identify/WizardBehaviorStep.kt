/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.meta.pixelandtexel.birdspotter.data.catalog.BirdBehavior
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * Station five: what the bird was doing, one answer. The six contexts mirror `BirdBehavior` one to
 * one — the row order here is the enum's order, so the list and the schema can never disagree about
 * what the choices are.
 */
@Composable
internal fun WizardBehaviorStep(
    behavior: BirdBehavior?,
    onChooseBehavior: (BirdBehavior) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .padding(horizontal = BirdSpotterTheme.space.gutter)
              .selectableGroup(),
  ) {
    BirdBehavior.entries.forEach { candidate ->
      BehaviorRow(
          label = candidate.label,
          selected = candidate == behavior,
          onClick = { onChooseBehavior(candidate) },
      )
      HairlineRule()
    }
  }
}

@Composable
private fun BehaviorRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
              .padding(vertical = space.separate),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = label,
        style = if (selected) BirdSpotterTheme.type.headline else BirdSpotterTheme.type.body,
        color = BirdSpotterTheme.colors.textPrimary,
        modifier = Modifier.weight(1f),
    )
    if (selected) {
      Spacer(Modifier.width(space.separate))
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.selected),
          contentDescription = null,
          tint = BirdSpotterTheme.colors.verdigris,
      )
    }
  }
}

internal val BirdBehavior.label: String
  get() =
      when (this) {
        BirdBehavior.AT_FEEDER -> "Eating at a feeder"
        BirdBehavior.SWIMMING_OR_WADING -> "Swimming or wading"
        BirdBehavior.ON_GROUND -> "On the ground"
        BirdBehavior.IN_TREES_OR_BUSHES -> "In trees or bushes"
        BirdBehavior.ON_FENCE_OR_WIRE -> "On a fence or wire"
        BirdBehavior.SOARING_OR_FLYING -> "Soaring or flying"
      }
