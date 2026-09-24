/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.domain.PhotoIdentification
import com.meta.pixelandtexel.birdspotter.domain.RealtimeSession
import com.meta.pixelandtexel.birdspotter.domain.SessionEvent
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.NotesField
import com.meta.pixelandtexel.birdspotter.ui.components.OpenPhoto
import com.meta.pixelandtexel.birdspotter.ui.components.PhotoLightboxHost
import com.meta.pixelandtexel.birdspotter.ui.components.PhotoLightboxScope
import com.meta.pixelandtexel.birdspotter.ui.components.PhotoLightboxSource
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import kotlin.math.roundToInt

/**
 * The confirmation — the second screen of the real-time flow, after the stop.
 *
 * The session is over by the time this appears and nothing has been written: the stop answered *is
 * the microphone still open*, and this screen answers *is this worth keeping*. What it offers, in
 * the order a watcher wants it: the timeline read back whole, the birds separable from the rest, a
 * place for notes, and Save or Discard.
 *
 * The birds take a tap: a saved outing's sightings are the ones a watcher **confirmed**, not the
 * ones a detector offered, and dropping a bad detection here is the whole reason a confirmation
 * exists rather than an autosave. Dropping one keeps its row on the timeline — the journal records
 * what happened either way; what changes is what enters the life list.
 *
 * **Discard is a real button, and it does not ask twice.** A rehearsal before a demo and a session
 * started by accident are both sessions somebody stopped, and neither is a journal entry; a journal
 * that fills with rehearsals is a journal nobody reads.
 *
 * Runs in the cover's dark palette like the session it reviews — one flow, one cabinet.
 */
@Composable
fun SessionReviewScreen(
    session: RealtimeSession,
    review: SessionReview,
    onToggleBird: (Double) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors

  /** The photograph being looked at full screen, or `null` — see [PhotoLightbox]. */
  var openPhoto by remember { mutableStateOf<OpenPhoto?>(null) }

  // Hoisted above the host, because its content is unmounted while a photograph is open and a
  // scroll position remembered inside would not survive the trip — a watcher who opened a photo
  // from the foot of a long timeline should come back to the foot of it.
  val listState = rememberLazyListState()

  PhotoLightboxHost(
      openPhoto = openPhoto,
      onClose = { openPhoto = null },
      modifier = modifier.fillMaxSize(),
  ) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
    ) {
      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .padding(horizontal = space.gutter)
                  .padding(top = space.separate),
          verticalArrangement = Arrangement.spacedBy(space.tight),
      ) {
        Text(
            text = "Review",
            style = BirdSpotterTheme.type.title,
            color = colors.textPrimary,
        )
        Text(
            text = reviewSubtitle(session, review),
            style = BirdSpotterTheme.type.label,
            color = colors.textSecondary,
        )
        // The rows have been tappable since this screen existed and nothing said so — the
        // click label read "Drop this bird" to a screen reader while everyone else got a
        // chip that looks like every other status plate in the app. Said once, at the top,
        // and only where there is actually a bird to tap.
        if (remember(session) { session.events.any { it.namedBird != null } }) {
          Text(
              text = "Tap a bird to drop it from your life list",
              style = BirdSpotterTheme.type.caption,
              color = colors.textFaint,
          )
        }
      }

      Spacer(Modifier.height(space.separate))
      HairlineRule()

      // The timeline read back whole: oldest first, because a stopped session is a story
      // being read rather than a report still arriving — the live log's newest-first is
      // for the opposite situation.
      Box(Modifier.weight(1f)) {
        LazyColumn(
            state = listState,
            contentPadding =
                PaddingValues(
                    start = space.gutter,
                    end = space.gutter,
                    top = space.separate,
                    bottom = space.separate,
                ),
            verticalArrangement = Arrangement.spacedBy(space.related),
        ) {
          items(session.events, key = { it.at }) { event ->
            ReviewRow(
                event = event,
                isDropped = event.at in review.droppedBirds,
                onToggle = { onToggleBird(event.at) },
                onOpenPhoto = { openPhoto = it },
            )
          }
        }

        // The journal saves an outing with or without birds by design — forty minutes
        // of wind still happened somewhere. Said plainly rather than left blank.
        if (session.events.isEmpty()) {
          PlateLabel(
              text = "Nothing landed on the timeline",
              color = colors.textFaint,
              modifier = Modifier.align(Alignment.TopCenter).padding(top = space.section),
          )
        }
      }

      HairlineRule()

      // The one thing a person can contribute that no sensor can, asked for at the moment
      // they still remember what the morning was like.
      //
      // In the app's own box — see [NotesField]. This screen forces the dark palette, and a
      // Material field takes its colours from a scheme that would not know.
      NotesField(
          text = review.notes,
          onTextChange = onNotesChange,
          placeholder = "Notes — what the morning was like",
          enabled = !review.isSaving && review.savedOutingId == null,
          modifier = Modifier.padding(horizontal = space.gutter).padding(top = space.separate),
      )

      review.saveErrorMessage?.let { message ->
        Text(
            text = message,
            style = BirdSpotterTheme.type.label,
            color = colors.textSecondary,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = space.gutter)
                    .padding(top = space.snug),
        )
      }

      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .windowInsetsPadding(WindowInsets.navigationBars)
                  .padding(space.gutter),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
            onClick = onDiscard,
            enabled = !review.isSaving,
            modifier =
                Modifier.semantics {
                  contentDescription = "Discard this session without saving"
                },
        ) {
          Text(
              text = "Discard",
              style = BirdSpotterTheme.type.headline,
              color = colors.textSecondary,
          )
        }

        Spacer(Modifier.weight(1f))

        SaveButton(
            isSaving = review.isSaving,
            isSaved = review.savedOutingId != null,
            onClick = onSave,
        )
      }
    }
  }
}

/**
 * The one line under the title: how long it ran, and what it holds. Counts rather than a lecture —
 * the list below is the detail.
 *
 * **The bird count is the *kept* count, and that is the whole point of it.** It used to count every
 * row that named a bird, which meant dropping one changed the row under the thumb and nothing else
 * on the screen — the one interaction this screen exists for had no consequence anywhere a watcher
 * was looking. Counting what will actually enter the life list makes the header answer every tap,
 * which is also how the interaction teaches itself to somebody who found it by accident.
 */
private fun reviewSubtitle(session: RealtimeSession, review: SessionReview): String {
  val named = session.events.filter { it.namedBird != null }
  val kept = named.count { it.at !in review.droppedBirds }
  val photos = session.events.count { it is SessionEvent.Photo }
  val parts = buildList {
    add(sessionStamp(review.durationSeconds))
    if (kept > 0) add(if (kept == 1) "1 bird" else "$kept birds")
    if (photos > 0) add(if (photos == 1) "1 photo" else "$photos photos")
    // Two different nothings, and they are not the same sentence: a session that found no
    // birds, and a session whose birds the watcher threw all back. Saying "nothing
    // identified" for the second would be the screen forgetting what it just did.
    if (named.isEmpty() && photos == 0) {
      add("nothing identified")
    } else if (kept == 0 && named.isNotEmpty()) {
      add("no birds kept")
    }
  }
  return parts.joinToString(" · ")
}

/**
 * The bird this row would put in the life list, or `null` where it names none.
 *
 * **Three kinds of row can name one**, and the journal treats all three the same: a detection the
 * microphone found, an answer the script linked a species to, and — since a photo carries its own
 * identification — a photograph the app named a bird in. Asking one question of the event, here, is
 * what keeps the count in the subtitle, the tappability of a row and the chip on it from drifting
 * into three slightly different lists.
 */
private val SessionEvent.namedBird: String?
  get() =
      when (this) {
        is SessionEvent.Bird -> commonName
        is SessionEvent.Answer -> commonName.takeIf { speciesId != null }
        is SessionEvent.Photo -> (identification as? PhotoIdentification.Bird)?.commonName
        is SessionEvent.Speech -> null
      }

/**
 * One event, read back — the live log's row with its stamp and its inks, plus the one thing review
 * adds: anything that would enter the life list carries its keep-or-drop state, and the whole row
 * takes the tap, because the chip alone is a small target for a decision this screen exists for.
 *
 * See [namedBird] for what counts as naming one — a kept bird is a sighting, however it was
 * reached.
 */
@Composable
private fun PhotoLightboxScope.ReviewRow(
    event: SessionEvent,
    isDropped: Boolean,
    onToggle: () -> Unit,
    onOpenPhoto: (OpenPhoto) -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val named = event.namedBird
  val isConfirmable = named != null

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .then(
                  if (isConfirmable) {
                    Modifier.clickable(
                        onClickLabel = if (isDropped) "Keep this bird" else "Drop this bird",
                        onClick = onToggle,
                    )
                  } else {
                    Modifier
                  },
              ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(space.related),
  ) {
    Text(
        text = sessionStamp(event.at),
        style = BirdSpotterTheme.type.data,
        color = colors.textFaint,
        modifier = Modifier.width(ReviewStampWidth),
    )

    when (event) {
      is SessionEvent.Photo -> {
        // Always present by the time the review opens — `RealtimeViewModel.stopSession`
        // drops any capture whose picture was still crossing, because that photograph is
        // never arriving now and an empty tile is not a thing to decide about.
        val bitmap = remember(event.image) { event.image?.asImageBitmap() }
        if (bitmap != null) {
          val caption =
              when (val identification = event.identification) {
                is PhotoIdentification.Bird -> identification.commonName
                is PhotoIdentification.Words -> identification.text
                else -> null
              }
          Image(
              bitmap = bitmap,
              contentDescription = "View this photo",
              contentScale = ContentScale.Crop,
              modifier =
                  Modifier.size(ReviewPhotoSize)
                      .clip(RoundedCornerShape(space.tight))
                      .photoTransitionSource(event.at.toString())
                      // The row's own click still toggles keep-or-drop; this sits inside it
                      // and takes the 44dp the photograph actually covers, so a thumbnail
                      // opens the picture and the rest of the row decides about the bird.
                      .clickable {
                        onOpenPhoto(
                            OpenPhoto(
                                id = event.at.toString(),
                                source = PhotoLightboxSource.Image(bitmap),
                                caption = caption,
                            ),
                        )
                      },
          )
        }

        // What the app made of it, beside it — the same place the live log put it, so
        // reading a session back and watching it happen are the same picture. A capture
        // still waiting when the stop came is simply a photograph: the session is over,
        // and there is nothing left to be waiting for.
        when (val identification = event.identification) {
          is PhotoIdentification.Bird -> {
            PlateLabel(
                text = identification.commonName,
                color = if (isDropped) colors.textFaint else colors.gilt,
            )
            PlateLabel(
                text = "${(identification.confidence * 100).roundToInt()}%",
                color = if (isDropped) colors.textFaint else colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            KeepChip(name = identification.commonName, isDropped = isDropped)
          }

          is PhotoIdentification.Words ->
              Text(
                  text = identification.text,
                  style = BirdSpotterTheme.type.body,
                  color = colors.gilt,
                  modifier = Modifier.weight(1f),
              )

          else -> PlateLabel(text = "Photo", color = colors.textSecondary)
        }
      }

      is SessionEvent.Speech ->
          Text(
              text = event.text,
              style = BirdSpotterTheme.type.body,
              color = colors.textSecondary,
              modifier = Modifier.weight(1f),
          )

      is SessionEvent.Answer -> {
        Text(
            text = event.text,
            style = BirdSpotterTheme.type.body,
            color = if (isDropped) colors.textFaint else colors.gilt,
            modifier = Modifier.weight(1f, fill = false),
        )
        event.commonName?.let { name ->
          PlateLabel(
              text = name,
              color = if (isDropped) colors.textFaint else colors.gilt,
          )
        }
        if (isConfirmable) {
          Spacer(Modifier.weight(1f))
          KeepChip(name = event.commonName ?: "this bird", isDropped = isDropped)
        }
      }

      is SessionEvent.Bird -> {
        // A dropped bird keeps its row but loses its ink: the timeline records what
        // happened; the gilt is for what the journal will call a sighting.
        PlateLabel(
            text = event.commonName,
            color = if (isDropped) colors.textFaint else colors.gilt,
        )
        PlateLabel(
            text = "${(event.confidence * 100).roundToInt()}%",
            color = if (isDropped) colors.textFaint else colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        KeepChip(name = event.commonName, isDropped = isDropped)
      }
    }
  }
}

/** The keep-or-drop state, worn identically by every row that names a bird. */
@Composable
private fun KeepChip(name: String, isDropped: Boolean) {
  PlateLabel(
      text = if (isDropped) "Dropped" else "Sighting",
      color =
          if (isDropped) {
            BirdSpotterTheme.colors.textFaint
          } else {
            BirdSpotterTheme.colors.verdigris
          },
      modifier =
          Modifier.semantics {
            contentDescription =
                if (isDropped) {
                  "$name dropped — tap to keep it"
                } else {
                  "$name kept as a sighting — tap to drop it"
                }
          },
  )
}

/**
 * Save, in the same verdigris the wizard's claim wears: one color for "this enters the journal",
 * wherever it is offered. The saved state is only ever seen for the beat before the cover falls.
 */
@Composable
private fun SaveButton(
    isSaving: Boolean,
    isSaved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Button(
      onClick = onClick,
      enabled = !isSaving && !isSaved,
      shape = RoundedCornerShape(3.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = BirdSpotterTheme.colors.verdigris,
              contentColor = BirdSpotterTheme.colors.paperRaised,
              disabledContainerColor =
                  if (isSaved) {
                    BirdSpotterTheme.colors.verdigris
                  } else {
                    BirdSpotterTheme.colors.rule
                  },
              disabledContentColor =
                  if (isSaved) {
                    BirdSpotterTheme.colors.paperRaised
                  } else {
                    BirdSpotterTheme.colors.textFaint
                  },
          ),
      modifier = modifier.height(48.dp),
  ) {
    Text(
        text =
            when {
              isSaved -> "In your journal"
              isSaving -> "Saving…"
              else -> "Save to Journal"
            },
        style = BirdSpotterTheme.type.headline,
    )
  }
}

/** The stamp column, same width as the live log's, so the flow reads as one instrument. */
private val ReviewStampWidth = 44.dp

/** A photo in the review — the log's size; recognising the bird is still the job. */
private val ReviewPhotoSize = 44.dp
