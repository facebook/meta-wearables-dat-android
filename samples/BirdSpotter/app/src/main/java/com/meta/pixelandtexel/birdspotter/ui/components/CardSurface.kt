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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * The card's own metrics. Not a token set: one value on one component, and a token set of size one
 * is ceremony.
 */
object CardMetrics {
  /** Barely rounded. A printed plate has a corner, not a pill. */
  val CornerRadius = 3.dp
}

/**
 * Raised paper inside a hairline rule — the one card treatment this app has.
 *
 * Squared off rather than rounded, and ruled rather than shadowed: a Material card's elevation
 * would read as a floating slab of UI, where this wants to read as something printed. The one
 * concession to the platform is [onClick]'s ripple, which is applied after the clip so it stops at
 * the card's edge.
 */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(CardMetrics.CornerRadius)
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape)
              .background(BirdSpotterTheme.colors.paperRaised)
              .border(1.dp, BirdSpotterTheme.colors.rule, shape)
              .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
      content = content,
  )
}

/** A one-dp divider in the rule colour. */
@Composable
fun HairlineRule(modifier: Modifier = Modifier) {
  Box(
      modifier.fillMaxWidth().height(1.dp).background(BirdSpotterTheme.colors.rule),
  )
}

/**
 * The small letterspaced caps that head a section — "ABOUT", "HABITAT", "BIRD OF THE DAY".
 *
 * [maxLines] is as many as it needs by default, which is right for the plates that stand alone and
 * read as a sentence in caps. **A plate that shares a row with anything wants `1`**: set in caps at
 * this tracking, a broken word does not read as a wrapped label; it reads as a rendering fault —
 * `LINKIN` over a lone `G` is what the session's source switch was doing the moment its dots
 * arrived and took the width the word was using.
 */
@Composable
fun PlateLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BirdSpotterTheme.colors.textFaint,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
  Text(
      text = text.uppercase(),
      style = BirdSpotterTheme.type.plate,
      color = color,
      textAlign = textAlign,
      maxLines = maxLines,
      modifier = modifier,
  )
}
