/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.data.journal.OutingKind
import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.ui.components.CardMetrics
import com.meta.pixelandtexel.birdspotter.ui.components.CatalogPhoto
import com.meta.pixelandtexel.birdspotter.ui.components.SpeciesRowMetrics
import com.meta.pixelandtexel.birdspotter.ui.components.ThumbnailWidthPx
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * One line of the Journal: a thumbnail, what the outing earned, and when and where.
 *
 * The outing counterpart to `SpeciesRow`, and built to the same metric so a Journal and a guide
 * index read as the same list at two addresses — rows on the paper, parted by a
 * [com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule] the screen draws, never each in
 * its own card.
 *
 * Labeling follows the design doc's one rule — the strongest confirmed fact leads, never a switch
 * on `kind`: the earliest confirmed bird names the row ("Green Jay + 2 more"), a birdless outing
 * falls back to its place, and a placeless one to its date. The thumbnail walks the same ladder:
 * captured photo, the primary bird's own plate, then a marked tile. (A sonogram strip takes the
 * third slot once the live flow can render one from a captured segment — no captured audio exists
 * before that flow ships.)
 */
@Composable
fun JournalRow(
    entry: JournalEntry,
    mediaFileStore: MediaFileStore?,
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
    Thumbnail(
        entry = entry,
        mediaFileStore = mediaFileStore,
        modifier =
            Modifier.size(SpeciesRowMetrics.ThumbnailSize)
                .clip(RoundedCornerShape(CardMetrics.CornerRadius)),
    )

    // The row owns the rhythm between its lines; none adds its own gap on top.
    Column(verticalArrangement = Arrangement.spacedBy(space.tight)) {
      Text(
          text = headline(entry),
          style = BirdSpotterTheme.type.body,
          // Not an error colour: an outing saves with or without a bird, and a
          // birdless row is just the quieter entry.
          color =
              if (entry.primaryBird != null) {
                BirdSpotterTheme.colors.textPrimary
              } else {
                BirdSpotterTheme.colors.textSecondary
              },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )

      secondLine(entry)?.let { line ->
        Text(
            text = line,
            style = BirdSpotterTheme.type.scientific,
            color = BirdSpotterTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }

      Text(
          text = metadata(entry),
          style = BirdSpotterTheme.type.caption,
          color = BirdSpotterTheme.colors.textFaint,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun Thumbnail(
    entry: JournalEntry,
    mediaFileStore: MediaFileStore?,
    modifier: Modifier = Modifier,
) {
  val photo = entry.photos.firstOrNull()
  val plate = entry.primaryBird?.species?.heroPhoto
  when {
    photo != null && mediaFileStore != null ->
        OutingPhoto(
            media = photo,
            mediaFileStore = mediaFileStore,
            maxWidthPx = ThumbnailWidthPx,
            modifier = modifier,
        )

    plate != null -> CatalogPhoto(media = plate, maxWidthPx = ThumbnailWidthPx, modifier = modifier)

    else ->
        Box(
            modifier = modifier.background(BirdSpotterTheme.colors.giltWash),
            contentAlignment = Alignment.Center,
        ) {
          Icon(
              painter =
                  glyph(
                      if (entry.audio.isNotEmpty()) {
                        BirdSpotterTheme.glyphs.call
                      } else {
                        BirdSpotterTheme.glyphs.journal
                      },
                  ),
              contentDescription = null,
              tint = BirdSpotterTheme.colors.textFaint,
              modifier = Modifier.size(22.dp),
          )
        }
  }
}

/** The strongest confirmed fact: bird (+ count), else the date itself. */
private fun headline(entry: JournalEntry): String {
  val primary = entry.primaryBird ?: return JournalFormatting.date(entry.outing.startedAt)
  val name = primary.species!!.species.commonName
  val extra = entry.extraBirdCount
  return if (extra > 0) "$name + $extra more" else name
}

/** Scientific name under a single bird, a species count under several, nothing otherwise. */
private fun secondLine(entry: JournalEntry): String? {
  val primary = entry.primaryBird ?: return null
  return if (entry.extraBirdCount > 0) {
    "${entry.extraBirdCount + 1} species"
  } else {
    primary.species!!.species.scientificName
  }
}

/** Date, and a live outing's length. */
private fun metadata(entry: JournalEntry): String = buildList {
  add(JournalFormatting.date(entry.outing.startedAt))
  if (entry.outing.kind == OutingKind.LIVE) {
    entry.outing.durationMs?.let { add(JournalFormatting.durationLabel(it)) }
  }
}
    .joinToString(" · ")
