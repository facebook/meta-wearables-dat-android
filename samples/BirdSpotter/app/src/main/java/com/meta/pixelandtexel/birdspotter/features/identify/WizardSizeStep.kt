/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * Station three: apparent size, on the same seven stops the catalog's `Species.sizeClass` uses —
 * four silhouettes for the anchors (sparrow, robin, crow, goose) and unlabeled stops between them.
 * The match is a window, picked ± one stop; see `SpeciesStore.identifyCandidates`.
 *
 * Four species rather than one shape at four scales: the labels name real birds, and an anchor you
 * have to read the label to recognise is not doing its job. The drawings stay coarse — posture and
 * bulk, no plumage — so the row still asks "how big" rather than "which shape".
 *
 * They are drawn *to each other*, in one shared box with a common ground line, so all four render
 * at the same frame and the scale comes out of the artwork. See [BirdSpotterGlyphs]' size anchors,
 * and `licenses/icons/README.md` for why that box is the exception to the icon grid.
 */
@Composable
internal fun WizardSizeStep(
    sizeClass: Int?,
    onChooseSizeClass: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Column(
      modifier =
          modifier.fillMaxWidth().padding(horizontal = space.gutter).padding(top = space.section),
  ) {
    SizeScale(
        sizeClass = sizeClass,
        onChooseSizeClass = onChooseSizeClass,
    )

    CardSurface(modifier = Modifier.padding(top = space.section)) {
      Text(
          text = sizeClass?.let { sizeClassLabels[it - 1] } ?: "Pick the closest size",
          style = BirdSpotterTheme.type.body,
          color =
              if (sizeClass != null) {
                BirdSpotterTheme.colors.textPrimary
              } else {
                BirdSpotterTheme.colors.textFaint
              },
          modifier = Modifier.padding(space.cardInset),
      )
    }
  }
}

/** Index = sizeClass - 1. The words the picked stop turns into. */
internal val sizeClassLabels = listOf(
    "Sparrow-sized or smaller",
    "Between a sparrow and a robin",
    "Robin-sized",
    "Between a robin and a crow",
    "Crow-sized",
    "Between a crow and a goose",
    "Goose-sized or larger",
)

// The anchors' shared box, drawn 1:1. Not a spacing role, and not four heights either —
// the birds' sizes relative to one another are already in the drawings, so this is one
// frame that all four take.
private val AnchorBoxWidth = 64.dp
private val AnchorBoxHeight = 56.dp
private val StopSize = 28.dp
private val StopDotSize = 16.dp

/** Seven stops under four anchor glyphs; glyphs sit over stops 1, 3, 5, 7. */
@Composable
private fun SizeScale(
    sizeClass: Int?,
    onChooseSizeClass: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  // The anchor drawings, smallest to largest — one per odd stop.
  val anchorGlyphs =
      with(BirdSpotterTheme.glyphs) {
        listOf(sizeSparrow, sizeRobin, sizeCrow, sizeGoose)
      }
  Column(modifier.selectableGroup()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
      (1..7).forEach { stop ->
        Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
          if (stop % 2 == 1) {
            Icon(
                painter = glyph(anchorGlyphs[stop / 2]),
                // Decorative: the stop below carries the words.
                contentDescription = null,
                tint = BirdSpotterTheme.colors.ink,
                modifier = Modifier.size(AnchorBoxWidth, AnchorBoxHeight),
            )
          }
        }
      }
    }
    Spacer(Modifier.height(space.separate))
    Row(Modifier.fillMaxWidth()) {
      (1..7).forEach { stop ->
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
          SizeStop(
              selected = sizeClass == stop,
              onClick = { onChooseSizeClass(stop) },
              label = sizeClassLabels[stop - 1],
          )
        }
      }
    }
  }
}

/** A ring, filled while chosen — radio semantics without Material's radio styling. */
@Composable
private fun SizeStop(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
  val ringColor = BirdSpotterTheme.colors.textSecondary
  val fillColor = BirdSpotterTheme.colors.textPrimary
  Box(
      modifier =
          modifier
              // The visible ring is small; the touch target is the whole 44-dp stop.
              .size(44.dp)
              .selectable(
                  selected = selected,
                  onClick = onClick,
                  role = Role.RadioButton,
              )
              .semantics { contentDescription = label },
      contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(StopSize)) {
      drawCircle(
          color = ringColor,
          radius = (StopSize - 2.dp).toPx() / 2,
          style = Stroke(width = 2.dp.toPx()),
      )
      if (selected) {
        drawCircle(color = fillColor, radius = StopDotSize.toPx() / 2)
      }
    }
  }
}
