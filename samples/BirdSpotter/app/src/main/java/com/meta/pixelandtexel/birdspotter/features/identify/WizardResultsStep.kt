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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.components.CatalogPhoto
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The wizard's answer: every species the three answers leave standing, in checklist order, each
 * with its plates and the two things to do about it — claim it, or read about it first.
 *
 * "This is my bird" writes the sighting and the card says so in place; the flow does not yank the
 * user elsewhere at its one moment of success. One claim per wizard run — the other cards' buttons
 * quiet down once a bird is taken.
 */
@Composable
internal fun WizardResultsStep(
    coordinate: Coordinate?,
    spottedOn: LocalDate,
    candidates: List<SpeciesWithMedia>?,
    savingSpeciesId: String?,
    savedSpeciesId: String?,
    saveError: IdentifyWizardSaveError?,
    onSaveSighting: (String) -> Unit,
    onOpenBird: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Column(modifier.fillMaxSize()) {
    AnswersStrip(coordinate = coordinate, spottedOn = spottedOn)
    saveError?.let { SaveErrorNotice(it) }

    when {
      candidates == null -> Searching(Modifier.weight(1f))
      candidates.isEmpty() -> NoMatches(Modifier.weight(1f))
      else ->
          LazyColumn(
              modifier = Modifier.weight(1f),
              contentPadding =
                  PaddingValues(
                      start = space.gutter,
                      end = space.gutter,
                      top = space.separate,
                      bottom = space.page,
                  ),
              verticalArrangement = Arrangement.spacedBy(space.separate),
          ) {
            items(candidates, key = { it.species.id }) { bird ->
              CandidateCard(
                  bird = bird,
                  saving = savingSpeciesId == bird.species.id,
                  saved = savedSpeciesId == bird.species.id,
                  claimTaken = savedSpeciesId != null,
                  onSaveSighting = { onSaveSighting(bird.species.id) },
                  onOpenBird = { onOpenBird(bird.species.id) },
              )
            }
          }
    }
  }
}

/**
 * Why the last "This is my bird" did not stick, in the one place the tap happened.
 *
 * A tap that writes nothing has to say so. Nothing was saved either way, so the wording is about
 * what to do next rather than about what broke — and the buttons below stay live, because trying
 * again is the entire remedy for both cases.
 */
@Composable
private fun SaveErrorNotice(error: IdentifyWizardSaveError, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  Text(
      text =
          when (error) {
            IdentifyWizardSaveError.NO_LOCATION_FIX ->
                "Couldn't get your location, and an entry needs one. Try again in a moment, " +
                    "or step somewhere with a clearer view of the sky."
            IdentifyWizardSaveError.WRITE_FAILED ->
                "Couldn't save that entry. Nothing was written — try again."
          },
      style = BirdSpotterTheme.type.body,
      color = BirdSpotterTheme.colors.textSecondary,
      modifier =
          modifier.fillMaxWidth().padding(horizontal = space.gutter).padding(top = space.related),
  )
}

/**
 * What was taken for the sighting — the day, and that the spot was stamped — riding above the list
 * the answers produced.
 */
@Composable
private fun AnswersStrip(
    coordinate: Coordinate?,
    spottedOn: LocalDate,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val date = spottedOn.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
  Column(modifier) {
    HairlineRule()
    Text(
        text = if (coordinate != null) "Current location · $date" else date,
        style = BirdSpotterTheme.type.caption,
        color = BirdSpotterTheme.colors.textSecondary,
        modifier =
            Modifier.fillMaxWidth()
                .background(BirdSpotterTheme.colors.paperRaised)
                .padding(horizontal = space.gutter, vertical = space.related),
    )
    HairlineRule()
  }
}

@Composable
private fun CandidateCard(
    bird: SpeciesWithMedia,
    saving: Boolean,
    saved: Boolean,
    claimTaken: Boolean,
    onSaveSighting: () -> Unit,
    onOpenBird: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  CardSurface(modifier = modifier) {
    if (bird.photos.isNotEmpty()) {
      val pager = rememberPagerState(pageCount = { bird.photos.size })
      HorizontalPager(
          state = pager,
          modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
      ) { page ->
        CatalogPhoto(
            media = bird.photos[page],
            contentDescription = "${bird.species.commonName}, plate ${page + 1}",
            modifier = Modifier.fillMaxSize(),
        )
      }
      // Same caption the detail page prints: the credit rides with its photograph.
      HairlineRule()
      Row(
          Modifier.fillMaxWidth()
              .background(BirdSpotterTheme.colors.paperRaised)
              .padding(horizontal = space.cardInset, vertical = space.snug),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = bird.photos.getOrNull(pager.currentPage)?.credit.orEmpty(),
            style = BirdSpotterTheme.type.caption,
            color = BirdSpotterTheme.colors.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(space.separate))
        Text(
            text = "${pager.currentPage + 1} of ${bird.photos.size}",
            style = BirdSpotterTheme.type.data,
            color = BirdSpotterTheme.colors.textSecondary,
        )
      }
      HairlineRule()
    }

    Column(Modifier.padding(space.cardInset)) {
      Text(
          text = bird.species.commonName,
          style = BirdSpotterTheme.type.title,
          color = BirdSpotterTheme.colors.textPrimary,
      )
      Spacer(Modifier.height(space.tight))
      Text(
          text = bird.species.scientificName,
          style = BirdSpotterTheme.type.scientific,
          color = BirdSpotterTheme.colors.textSecondary,
      )
      Spacer(Modifier.height(space.related))
      Text(
          text = bird.species.aboutText,
          style = BirdSpotterTheme.type.body,
          color = BirdSpotterTheme.colors.textSecondary,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(space.separate))
      Row(verticalAlignment = Alignment.CenterVertically) {
        ClaimButton(
            saving = saving,
            saved = saved,
            claimTaken = claimTaken,
            onClick = onSaveSighting,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(space.related))
        InfoButton(onClick = onOpenBird)
      }
    }
  }
}

/** "This is my bird", and its three quieter moods: saving, saved, or too late. */
@Composable
private fun ClaimButton(
    saving: Boolean,
    saved: Boolean,
    claimTaken: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Button(
      onClick = onClick,
      enabled = !saving && !claimTaken,
      shape = RoundedCornerShape(3.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = BirdSpotterTheme.colors.verdigris,
              contentColor = BirdSpotterTheme.colors.paperRaised,
              // A saved card keeps its color: "in your journal" is a state, not a dead
              // control, and graying it out would read as an error.
              disabledContainerColor =
                  if (saved) {
                    BirdSpotterTheme.colors.verdigris
                  } else {
                    BirdSpotterTheme.colors.rule
                  },
              disabledContentColor =
                  if (saved) {
                    BirdSpotterTheme.colors.paperRaised
                  } else {
                    BirdSpotterTheme.colors.textFaint
                  },
          ),
      modifier = modifier.height(48.dp),
  ) {
    if (saved) {
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.selected),
          contentDescription = null,
          modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(BirdSpotterTheme.space.snug))
    }
    Text(
        text =
            when {
              saved -> "In your journal"
              saving -> "Saving…"
              else -> "This is my bird"
            },
        style = BirdSpotterTheme.type.headline,
    )
  }
}

/** The quieter companion: read the guide's page before deciding. */
@Composable
private fun InfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(3.dp)
  Box(
      modifier = modifier.size(48.dp).border(1.dp, BirdSpotterTheme.colors.rule, shape),
      contentAlignment = Alignment.Center,
  ) {
    IconButton(onClick = onClick) {
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.info),
          contentDescription = "About this bird",
          tint = BirdSpotterTheme.colors.verdigris,
      )
    }
  }
}

@Composable
private fun Searching(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = BirdSpotterTheme.colors.verdigris)
  }
}

/** An honest empty answer, with the way out named rather than implied. */
@Composable
private fun NoMatches(modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  Column(
      modifier = modifier.fillMaxWidth().padding(horizontal = space.gutter),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    PlateLabel(text = "No Matches")
    Spacer(Modifier.height(space.related))
    Text(
        text =
            "No bird in the guide fits everything you picked. " +
                "Go back and drop a color, or try the next size.",
        style = BirdSpotterTheme.type.body,
        color = BirdSpotterTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
  }
}
