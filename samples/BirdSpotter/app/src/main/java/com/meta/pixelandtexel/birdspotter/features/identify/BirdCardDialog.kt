/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.ui.components.CatalogPhoto
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * The identified bird's card, on the phone — the same card the glasses would have drawn, for every
 * run that has no glass to draw on.
 *
 * **It is the display's fallback, not a second design.** The order is the display's own — the two
 * names, the photographs, the description last — and so are its two measurements: `related` between
 * the parts, `separate` around them, because those are the numbers the component tree sent up to
 * the panel carries. A card that read differently in the two places would make the phone a poor
 * rehearsal for the glasses.
 *
 * What it adds is the one thing a phone has and a panel does not: somewhere to scroll. Three
 * photographs and two blocks of text overflow a canvas built for exactly this, and they overflow a
 * phone by more.
 *
 * **The close mark is the one thing here the glasses' card does not have, and it earns its place.**
 * A dialog that could only be dismissed by pressing away from it asks for a tap in the thin margin
 * around a card that fills most of the screen — a target that is hard to hit and invisible until
 * you have missed it. The mark shares the heading's line rather than taking a row of its own, so
 * the card still opens on the bird's name. Pressing outside still works; pressing the card itself
 * no longer does, because a body tap and a scroll are the same gesture until you have finished
 * making it.
 */
@Composable
fun BirdCardDialog(bird: SpeciesWithMedia, onDismiss: () -> Unit) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors

  Dialog(onDismissRequest = onDismiss) {
    Column(
        modifier =
            Modifier.clip(RoundedCornerShape(space.related))
                .background(colors.paper)
                .verticalScroll(rememberScrollState())
                .padding(space.separate),
        verticalArrangement = Arrangement.spacedBy(space.related),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = bird.species.commonName,
            style = BirdSpotterTheme.type.title,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
          Icon(
              painter = glyph(BirdSpotterTheme.glyphs.close),
              contentDescription = "Close this card",
              tint = colors.textSecondary,
          )
        }
      }
      Text(
          text = bird.species.scientificName,
          style = BirdSpotterTheme.type.label,
          color = colors.textSecondary,
      )
      bird.photos.forEach { photo ->
        CatalogPhoto(
            media = photo,
            // Unlabelled for the reason the component documents: the name is printed
            // directly above, and describing three photographs of it would have the
            // screen reader name the bird four times over.
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(CardPhotoAspect)
                    .clip(RoundedCornerShape(space.snug)),
        )
      }
      Text(
          text = bird.species.aboutText,
          style = BirdSpotterTheme.type.body,
          color = colors.textPrimary,
      )
    }
  }
}

/**
 * The shape a catalog photograph is cropped to here — the same 4:3 the field guide's own pages use,
 * so a bird looks like itself wherever it is met.
 */
private const val CardPhotoAspect = 4f / 3f
