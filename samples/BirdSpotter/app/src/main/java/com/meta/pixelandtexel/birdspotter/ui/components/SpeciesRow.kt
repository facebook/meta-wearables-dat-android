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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * The browse list's own metric. Not a token set: one value on one component, and a token set of
 * size one is ceremony.
 */
object SpeciesRowMetrics {
  /** Square, and small enough that a screenful of them decodes without a stutter. */
  val ThumbnailSize = 64.dp
}

/**
 * One line of the field guide's index: thumbnail, common name, binomial.
 *
 * Rows sit directly on the paper and are parted by [HairlineRule] rather than each being its own
 * [CardSurface] — 92 stacked cards read as a feed, where a run of ruled lines reads as an index,
 * which is what this is. The card treatment is reserved for the one bird of the day above them.
 *
 * No family line: the section header directly above already says it, and repeating it on every row
 * is the kind of noise that makes a long list tiring to scan.
 */
@Composable
fun SpeciesRow(
    bird: SpeciesWithMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Row(
      modifier =
          modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = space.related),
      horizontalArrangement = Arrangement.spacedBy(space.separate),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    bird.heroPhoto?.let { photo ->
      CatalogPhoto(
          media = photo,
          maxWidthPx = ThumbnailWidthPx,
          modifier =
              Modifier.size(SpeciesRowMetrics.ThumbnailSize)
                  .clip(RoundedCornerShape(CardMetrics.CornerRadius)),
      )
    }

    // The row owns the rhythm between its two lines; neither adds its own gap on top.
    Column(verticalArrangement = Arrangement.spacedBy(space.tight)) {
      Text(
          text = bird.species.commonName,
          style = BirdSpotterTheme.type.body,
          color = BirdSpotterTheme.colors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text = bird.species.scientificName,
          style = BirdSpotterTheme.type.scientific,
          color = BirdSpotterTheme.colors.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
