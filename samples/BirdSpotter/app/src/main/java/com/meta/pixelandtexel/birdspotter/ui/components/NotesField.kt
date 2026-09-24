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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme

/**
 * How tall the box stands before anything is typed in it, and how far it will grow — the numbers
 * the notes on the review screen want, and the defaults every other caller inherits.
 *
 * Two rows empty, because one row reads as a search box — that one is asking for a sentence, and
 * the shape should say so before a word is in it. Four is where it stops growing and starts
 * scrolling: past that it is eating the timeline above it, which is the thing the notes are about.
 *
 * A field asking for a name or a line takes `minLines = 1` and stays a line; nothing else about it
 * changes, which is the point.
 */
object NotesFieldMetrics {
  const val MinLines = 2
  const val MaxLines = 4
}

/**
 * A washed box to write in — [SearchField]'s box, given height.
 *
 * The two are deliberately the same object at different sizes: one ruled `verdigrisWash` rectangle
 * is what typing into this app looks like, whether the thing being typed is a species, a morning,
 * or a line the Demo Director will say back. What changes here is that the text starts at the top
 * rather than centring, because it is going to grow downwards.
 *
 * **Every field in the app that is not the search box is this one.** The line-limit arguments are
 * the only thing a caller varies — a name is `1..1`, a spoken line is `1..4`, the notes are the
 * defaults. A form whose boxes are drawn by three different systems is how the Demo Director's
 * editors came to look like a settings screen from another app.
 *
 * **This replaced `OutlinedTextField`, and that was the whole reason it exists.** The Material
 * field brings its own container colour, indicator line and floating label out of
 * `MaterialTheme.colorScheme` — on the review screen, which forces the dark palette whatever the
 * phone is set to, that is a scheme with no idea which palette it is standing in. Same reason
 * [SearchField] is a [BasicTextField] and [CardSurface] is not a `Card`.
 */
@Composable
fun NotesField(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    // False while the outing is being written, and once it has been: the notes are part of what
    // was saved, and a field still taking keystrokes would be promising otherwise.
    enabled: Boolean = true,
    // How tall the box stands empty, and where it stops growing. Defaulted to the notes' own
    // two-to-four; a one-line field passes 1 to both.
    minLines: Int = NotesFieldMetrics.MinLines,
    maxLines: Int = NotesFieldMetrics.MaxLines,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val shape = RoundedCornerShape(CardMetrics.CornerRadius)

  BasicTextField(
      value = text,
      onValueChange = onTextChange,
      enabled = enabled,
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape)
              .background(colors.verdigrisWash)
              .border(1.dp, colors.rule, shape)
              // `related` on every side rather than the search box's leading-only inset: this one
              // holds a block of text instead of a line, so the ink wants clearing from the rule
              // above and below it as much as from the side.
              .padding(space.related)
              .semantics { contentDescription = placeholder },
      textStyle = BirdSpotterTheme.type.body.copy(color = colors.textPrimary),
      cursorBrush = SolidColor(colors.verdigris),
      // A one-line box takes Return as "done" rather than typing a newline into a box that
      // cannot show it.
      singleLine = maxLines == 1,
      minLines = minLines,
      maxLines = maxLines,
      decorationBox = { field ->
        // Drawn rather than delegated, for the reason [SearchField] gives: a Material
        // placeholder takes its colour from the scheme, which is the one piece of text on
        // the screen that would not be coming from the palette.
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
}

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun NotesFieldEmptyPreview() {
  BirdSpotterTheme {
    NotesField(
        text = "",
        onTextChange = {},
        placeholder = "Notes — what the morning was like",
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun NotesFieldWrittenInPreview() {
  BirdSpotterTheme {
    NotesField(
        text = "Cold, still, and the creek was loud. Three of us out before the light.",
        onTextChange = {},
        placeholder = "Notes — what the morning was like",
    )
  }
}
