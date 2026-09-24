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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * The search field's own metrics. Not a token set: two values on one component, and a token set of
 * size two is ceremony.
 */
object SearchFieldMetrics {
  /**
   * The row's height, held constant rather than left to its contents.
   *
   * A tap target rather than a gap, so it is a *size* and legitimately not on the spacing scale —
   * and 48 rather than 44, so one number clears every platform floor this design has to sit inside.
   * Fixing the height also means the field does not grow the moment the clear button appears.
   */
  val FieldHeight = 48.dp

  /**
   * The magnifier and the clear button. Sized against the line beside them — the whole row is the
   * target, so these do not have to be.
   */
  val IconSize = 18.dp
}

/**
 * A washed box to type in, with a magnifier at one end — the ruled box of a printed form.
 *
 * **Deliberately not Material's `SearchBar`.** That is a pill that expands into a full-screen
 * overlay with its own scrim and back handling, which is not a line in the page. Building the field
 * out of this app's own vocabulary is what lets the design render the same everywhere.
 *
 * [BasicTextField] rather than `TextField` for the same reason [CardSurface] is not a `Card`: the
 * Material one brings a container colour, an indicator line and a floating label, all of which
 * would have to be turned off one at a time.
 *
 * [text] plus [onTextChange] rather than hoisting a `TextFieldState`, so the text itself stays on
 * the ViewModel. That is also what will let the glasses' speech capability drop a spoken query
 * straight in here later, with no keyboard involved.
 */
@Composable
fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    // The empty-field prompt. Defaulted so Explore's call stays unchanged; the Journal passes
    // "Find a sighting".
    placeholder: String = "Find a bird",
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val keyboard = LocalSoftwareKeyboardController.current
  val shape = RoundedCornerShape(CardMetrics.CornerRadius)

  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .height(SearchFieldMetrics.FieldHeight)
              // Washed rather than raised, and squared off like everything else here: a
              // printed form has a ruled box you write in, which is the same idea as
              // [CardSurface] at a lower volume — the card treatment stays reserved for the
              // bird of the day.
              //
              // Green rather than gold — see [BirdSpotterColors.verdigrisWash]. The wash is the
              // one thing every typing surface in the app shares, so it is the token that says
              // "this is yours to fill in", and gilt was already saying something else.
              .clip(shape)
              .background(colors.verdigrisWash)
              .border(1.dp, colors.rule, shape)
              // `related` rather than `cardInset`: 20 is scaled for a card's block of text,
              // and on a 48dp control it pushes the magnifier conspicuously clear of the
              // headline's left edge. The trailing inset comes off when the clear button is
              // up, because that button already carries a `snug` of its own on that side.
              .padding(
                  start = space.related,
                  end = if (text.isEmpty()) space.related else 0.dp,
              ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(space.snug),
  ) {
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.search),
        // Decorative: the field beside it already carries the label.
        contentDescription = null,
        // Verdigris rather than faint: it is the box's one mark, and it wants to be the
        // same pigment as the wash it sits in — a gilt magnifier in a green box is the
        // field wearing two accents at once.
        tint = colors.verdigris,
        modifier = Modifier.size(SearchFieldMetrics.IconSize),
    )

    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.weight(1f),
        textStyle = BirdSpotterTheme.type.body.copy(color = colors.textPrimary),
        singleLine = true,
        cursorBrush = SolidColor(colors.verdigris),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
        // Nothing to submit — the list is already filtered by the time the key is
        // pressed — so Search only puts the keyboard away.
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { field ->
          // The placeholder is drawn rather than delegated, so it takes its colour
          // from the palette like every other piece of text on the screen.
          Box {
            if (text.isEmpty()) {
              Text(
                  text = placeholder,
                  style = BirdSpotterTheme.type.body,
                  color = colors.textFaint,
              )
            }
            field()
          }
        },
    )

    if (text.isNotEmpty()) {
      Box(
          // The glyph is 18dp; the target is the full height of the box and a `snug`
          // either side of the mark, which is what carries it past the minimum
          // without a metric of its own.
          modifier =
              Modifier.width(SearchFieldMetrics.IconSize + space.snug * 2)
                  .fillMaxHeight()
                  .clickable { onTextChange("") },
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.clearSearch),
            contentDescription = "Clear search",
            tint = colors.textFaint,
            modifier = Modifier.size(SearchFieldMetrics.IconSize),
        )
      }
    }
  }
}

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SearchFieldEmptyPreview() {
  BirdSpotterTheme {
    SearchField(text = "", onTextChange = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun SearchFieldTypedPreview() {
  BirdSpotterTheme {
    SearchField(text = "warbler", onTextChange = {})
  }
}
