/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meta.pixelandtexel.birdspotter.data.journal.CaptureSource
import com.meta.pixelandtexel.birdspotter.domain.LiveWaveform
import com.meta.pixelandtexel.birdspotter.domain.PhotoIdentification
import com.meta.pixelandtexel.birdspotter.domain.RealtimeSession
import com.meta.pixelandtexel.birdspotter.domain.SessionEvent
import com.meta.pixelandtexel.birdspotter.domain.SonogramBins
import com.meta.pixelandtexel.birdspotter.domain.SonogramBuffer
import com.meta.pixelandtexel.birdspotter.domain.SonogramColumnsPerSecond
import com.meta.pixelandtexel.birdspotter.domain.SonogramPalette
import com.meta.pixelandtexel.birdspotter.domain.StripReading
import com.meta.pixelandtexel.birdspotter.ui.components.OpenPhoto
import com.meta.pixelandtexel.birdspotter.ui.components.PhotoLightboxHost
import com.meta.pixelandtexel.birdspotter.ui.components.PhotoLightboxScope
import com.meta.pixelandtexel.birdspotter.ui.components.PhotoLightboxSource
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.components.StopControl
import com.meta.pixelandtexel.birdspotter.ui.components.WorkingDots
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterColors
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The session's timeline: what it heard, and everything that has landed on it.
 *
 * Two parts, one above the other. The **strip** is the spine — a live sonogram, scrolling right to
 * left, with the right edge always *now*. Under it, the **log**: every photo, phrase and
 * identification, newest first, each against the second of the session it happened at.
 *
 * **The log is why the marks came off the strip.** They used to sit in two lanes either side of it,
 * pinned to their own column — which was a fine picture of the last eight seconds and nothing at
 * all after that. A detection nobody was looking at slid off the left edge and was gone. The
 * timeline's job is to say *when*; keeping the answers only where they were about to scroll away
 * was the one place it wasn't doing it. The stamp on a log row carries the same fact the mark's
 * position did, and carries it for the whole session.
 *
 * The strip still runs **edge to edge** — it is an instrument, and its right edge and the phone's
 * are the same edge. The log is set in a gutter like everything else in the app that is read rather
 * than watched.
 *
 * **The strip takes no touch.** While the microphone is open it shows now, and only now: a watcher
 * who has dragged back four seconds is a watcher no longer looking at what the app is hearing, and
 * it is the largest target on the screen. The log below it scrolls freely, which is where a
 * wandering thumb belongs — going back through a session ought to cost nothing, and nothing there
 * can move the live edge.
 */
@Composable
fun SessionTimeline(
    sonogram: SonogramBuffer,
    elapsed: Double,
    session: RealtimeSession,
    emptyLabel: String,
    footroom: Dp,
    onStop: () -> Unit,
    reading: StripReading,
    onReadingChange: (StripReading) -> Unit,
    onShowCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  // The window **ends at now**: the newest column sits on the right edge from the session's first
  // second, and everything older slides off the left.
  //
  // Deliberately not clamped to zero. A session younger than one window would otherwise fill
  // left-to-right and only begin scrolling once it was eight seconds old — two different motions
  // for one strip, and a right edge that means "now" only after the first eight seconds. Letting
  // the start go negative buys the same motion throughout: the session arrives at the right edge
  // over silence, because [SonogramBuffer.window] answers zero for a column before it began.
  val start = elapsed - TimelineWindowSeconds

  Column(modifier) {
    SonogramStrip(
        sonogram = sonogram,
        start = start,
        reading = reading,
        onStop = onStop,
        onReadingChange = onReadingChange,
    )

    SessionLog(
        session = session,
        emptyLabel = emptyLabel,
        footroom = footroom,
        onShowCard = onShowCard,
        modifier = Modifier.weight(1f),
    )
  }
}

/**
 * Everything that has happened this session, newest first.
 *
 * **Newest first, rather than a feed that grows downwards.** A log that appended at the bottom
 * would have to be scrolled to on every arrival to be read at all. This way the new row appears
 * directly under the strip that caused it — where the eye already is.
 *
 * Rows are keyed by their moment, which is what stops an arrival at the top renumbering every row
 * under it and rebuilding the lot.
 *
 * **And the list returns to the top when one lands.** Keying is what makes that necessary rather
 * than redundant: a keyed list deliberately holds its place against the row it was already showing,
 * so an arrival *above* that row scrolls in off the top of the viewport and the one event worth
 * seeing is the one event not on screen. Anchoring is the right behaviour for a list someone is
 * reading and the wrong one for a list that is reporting — this is reporting, and the newest row is
 * the whole point of it.
 *
 * A user's own drag outranks this: Compose gives touch the higher mutation priority, so a finger on
 * the list cancels the animation rather than fighting it, and a watcher who is deliberately reading
 * back through the session keeps their place until they let go.
 *
 * **It runs to the bottom of the phone, under the shutter, and fades out on the way.** A list that
 * stopped short of the control would be spending an inch of a small screen on saying "the rows end
 * here" about a list that does not end — and a hard edge there reads as a bug, as though something
 * clipped it. Instead the rows keep going and dim into the ground across the band the shutter sits
 * in, which says *there is more, and it is going under this*.
 *
 * [footroom] does that twice over: the last row can be scrolled up clear of the control, and the
 * fade is exactly that tall — so a row is dimming precisely while it is passing behind the shutter,
 * rather than at some other height that happens to look right.
 *
 * **The head of the list fades too, but only once there is something above it.** The strip's bottom
 * edge is hard — it is an instrument, and instruments have edges — so a row sliding up to meet it
 * collides with it rather than passing under it. A fade there says the rows continue behind the
 * strip, which is exactly what has happened. At rest it is *absent*, not merely faint: a list
 * already at its top has nothing hidden above the first row, and dimming it would be the screen
 * implying there is more to see in a direction there is nothing in. It arrives over the first
 * [LogHeadroom] of scroll, which is the same distance the fade itself is tall.
 *
 * The fade is a mask rather than a gradient laid on top, because a gradient would have to be the
 * colour of the ground, and the ground is the ground's business. `DstIn` over an offscreen layer is
 * what makes it one: the gradient's *alpha* becomes the content's.
 */
@Composable
fun SessionLog(
    session: RealtimeSession,
    emptyLabel: String,
    footroom: Dp,
    onShowCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val density = LocalDensity.current
  val footroomPx = with(density) { footroom.toPx() }
  val headroomPx = with(density) { LogHeadroom.toPx() }

  // Back to the newest row whenever one lands — see the note above for why a keyed list needs
  // telling. Keyed on the count rather than on the list, so it fires once per arrival and not
  // again on every recomposition the session's other state causes.
  val listState = rememberLazyListState()
  LaunchedEffect(session.events.size) {
    if (session.events.isNotEmpty()) listState.animateScrollToItem(0)
  }

  // How much of the head's fade is showing: nothing at the top of the list, all of it once a
  // headroom's worth of rows has gone under the strip. `derivedStateOf` so the scroll's own
  // frame-rate churn stops here — only a *changed* fade recomposes anything.
  val headFade by remember {
    derivedStateOf {
      if (listState.firstVisibleItemIndex > 0) {
        1f
      } else {
        (listState.firstVisibleItemScrollOffset / headroomPx).coerceIn(0f, 1f)
      }
    }
  }

  /**
   * The photograph being looked at full screen, or `null` — see [PhotoLightbox].
   *
   * **The session does not pause behind it.** The microphone belongs to the screen, not to this
   * list, and a watcher who opened a photograph has not stopped birding — the strip keeps scrolling
   * and rows keep landing underneath. What the picture does take away is the stop control, which is
   * the right trade: ending a recording is not something to do by accident through a photograph.
   */
  var openPhoto by remember { mutableStateOf<OpenPhoto?>(null) }

  PhotoLightboxHost(
      openPhoto = openPhoto,
      onClose = { openPhoto = null },
      modifier = modifier.fillMaxWidth(),
  ) {
    Box(Modifier.fillMaxWidth()) {
      LazyColumn(
          state = listState,
          contentPadding =
              PaddingValues(
                  start = space.gutter,
                  end = space.gutter,
                  top = space.separate,
                  bottom = footroom,
              ),
          verticalArrangement = Arrangement.spacedBy(space.related),
          modifier =
              Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                  .drawWithContent {
                    drawContent()
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height - footroomPx,
                                endY = size.height,
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                    // Opaque at both ends when nothing is hidden above, so this band costs the head
                    // of the list nothing until it has something to say — and opaque below the
                    // headroom either way, which is what keeps it from touching the foot's fade.
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(Color.Black.copy(alpha = 1f - headFade), Color.Black),
                                startY = 0f,
                                endY = headroomPx,
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                  },
      ) {
        items(session.events.asReversed(), key = { it.at }) { event ->
          LogRow(
              event = event,
              onOpenPhoto = { openPhoto = it },
              onShowCard = onShowCard,
          )
        }
      }

      // Not "no results": a session that has heard nothing yet is a session doing exactly what it
      // said it would, and it says which of the two that is — still opening the microphone, or
      // listening through one that is open.
      if (session.events.isEmpty()) {
        PlateLabel(
            text = emptyLabel,
            color = BirdSpotterTheme.colors.textFaint,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = space.section),
        )
      }
    }
  }
}

/**
 * One thing that happened, and the second of the session it happened at.
 *
 * The stamp is a column of its own so the times line up down the page — a log read by running an
 * eye down the left edge, which is what the marks' x-positions used to be for.
 *
 * Inputs and answers are told apart by ink rather than by side: a bird is the app talking, in gilt;
 * a photo or a phrase is the watcher, in the quieter hand. That was the strip's two lanes, and it
 * survives the move down here intact.
 *
 * A right-hand gutter closes the row, holding the device mark a photograph wears — see
 * [SourceMark]. It is held open on every row, so the marks read down the page as a column.
 *
 * **A row that named a bird takes a tap, and puts its card back up** — see [namedBird]. Where that
 * card lands is not this row's business: on the glasses if the wearer has glass, and on the phone
 * if not, which is why the tap is here for every run rather than only the best-equipped one. It
 * wears no mark for it, deliberately: the gilt already says this row is the app's own answer, and a
 * badge repeating *this one is pressable* on every second row would be a column of ink saying what
 * the ink beside it says.
 */
@Composable
private fun PhotoLightboxScope.LogRow(
    event: SessionEvent,
    onOpenPhoto: (OpenPhoto) -> Unit,
    onShowCard: (String) -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors

  // The whole row is the target rather than the plate inside it, because the plate is a word and
  // a word is a poor thing to hit — and because the stamp beside it belongs to the same event.
  // The photograph keeps its own tap: a child that handles the press stops it here, so a
  // thumbnail still opens full frame and the caption beside it still sends.
  val sendable = event.namedBird

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .then(
                  if (sendable != null) {
                    Modifier.clickable(onClickLabel = "Show this bird's card") {
                      onShowCard(sendable)
                    }
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
        modifier = Modifier.width(StampWidth),
    )

    // **The content takes the row; the gutter keeps its place.** The weight lives here rather
    // than on a spacer after the fact, because an unweighted phrase is measured against the
    // whole width first: a long one would eat the row and leave the mark beyond its right edge.
    // Weighted, the content gets everything the stamp and the mark do not want, and a row that
    // has no mark still ends where every other row ends.
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart,
    ) {
      when (event) {
        is SessionEvent.Photo ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.related),
            ) {
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
                        Modifier.size(LogPhotoSize)
                            .clip(RoundedCornerShape(space.tight))
                            .photoTransitionSource(event.at.toString())
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
              } else {
                // The picture's own space, held open at exactly the size it will fill, so
                // the arrival is the tile filling rather than the row growing and shoving
                // the log about under a thumb.
                Box(
                    modifier =
                        Modifier.size(LogPhotoSize)
                            .clip(RoundedCornerShape(space.tight))
                            .background(colors.rule),
                )
              }
              PhotoAnswer(event.identification, isCrossing = event.image == null)
            }

        is SessionEvent.Speech ->
            Text(
                text = event.text,
                style = BirdSpotterTheme.type.body,
                color = colors.textSecondary,
            )

        is SessionEvent.Bird ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.snug),
            ) {
              PlateLabel(text = event.commonName, color = colors.gilt)
              PlateLabel(
                  text = "${(event.confidence * 100).roundToInt()}%",
                  color = colors.textSecondary,
              )
            }

        // The app talking in words — same gilt ink as a named bird, set as a sentence
        // rather than a plate because an answer is read, not scanned. A bird the script
        // linked wears the plate a named bird wears, with no confidence: the watcher
        // described it, and the app agreed.
        //
        // **The plate sits above the sentence rather than beside it.** Beside it, the two
        // shared the row's width, so a sentence of any length wrapped into a narrow column
        // with a name stranded out to the right of it — a paragraph and a label, laid out
        // as though they were two columns of a table. Stacked, the name lands where every
        // other row's name lands and the sentence gets the full width to be read across,
        // which is the shape both of them wanted: the plate is scanned down a column, the
        // words are read along one.
        is SessionEvent.Answer ->
            Column(
                verticalArrangement = Arrangement.spacedBy(space.tight),
            ) {
              event.commonName?.let { name ->
                PlateLabel(text = name, color = colors.gilt)
              }
              Text(
                  text = event.text,
                  style = BirdSpotterTheme.type.body,
                  color = colors.gilt,
              )
            }
      }
    }

    SourceMark((event as? SessionEvent.Photo)?.source)
  }
}

/**
 * The bird this row named, or `null` for a row that named none — what decides whether it can be
 * sent back to the display.
 *
 * **The three cases are exactly the three that put a card up in the first place** (see
 * [RealtimeViewModel.sendToDisplay]): a detection, a photograph the script identified, and an
 * answer with a species behind it. A row that can be re-shown is a row that was shown, which is the
 * rule that keeps this from needing a list of its own to stay in step with.
 *
 * A photograph still crossing is deliberately included when its answer has landed — the picture and
 * the card are separate arrivals, and the card is the one being asked for.
 *
 * Everything else answers `null`: a phrase the watcher said, an ambiguity the app would not commit
 * to, a caption where there is no bird. The display never took those, so there is nothing to put
 * back.
 */
private val SessionEvent.namedBird: String?
  get() =
      when (this) {
        is SessionEvent.Bird -> speciesId
        is SessionEvent.Answer -> speciesId
        is SessionEvent.Photo -> (identification as? PhotoIdentification.Bird)?.speciesId
        is SessionEvent.Speech -> null
      }

/**
 * The device that took the photograph, in the row's right-hand gutter.
 *
 * **The source pill cannot answer this.** A session's photographs do not all come from one place:
 * the shutter routes per press, on whether the glasses can take one *at that moment* (see
 * [RealtimeViewModel.capturePhoto]), so a session that loses or gains the glasses halfway holds
 * both kinds. The pill reports what the session is riding *now* — it says nothing about the capture
 * three minutes up the log, and this mark is the only place that fact survives.
 *
 * **It is drawn from the press, not from the arrival**, which is why the crossing needs no words of
 * its own: a glasses photograph is marked as one for the whole second it is still in the air, so
 * the dots beside the empty tile only have to say *still coming*. A sentence there would be text
 * that dies before it is read and is replaced twice over — see [PhotoAnswer].
 *
 * Both devices are marked, deliberately. Marking only the glasses would leave an unmarked row
 * ambiguous between *the phone took it* and *we forgot to say*.
 *
 * The width is held whether or not there is a mark — `source` is null for everything the watcher
 * said and everything the app answered — so the gutter is a column rather than a ragged edge, and
 * so a row does not reflow the moment a photograph resolves.
 */
@Composable
private fun SourceMark(source: CaptureSource?) {
  Box(
      modifier = Modifier.width(LogSourceGlyphSize),
      contentAlignment = Alignment.Center,
  ) {
    if (source != null) {
      Icon(
          painter =
              glyph(
                  if (source == CaptureSource.GLASSES) {
                    BirdSpotterTheme.glyphs.glasses
                  } else {
                    BirdSpotterTheme.glyphs.device
                  },
              ),
          contentDescription =
              if (source == CaptureSource.GLASSES) {
                "Taken by the glasses"
              } else {
                "Taken by the phone"
              },
          tint = BirdSpotterTheme.colors.textFaint,
          modifier = Modifier.size(LogSourceGlyphSize),
      )
    }
  }
}

/**
 * What the app made of a photo, **on the photo's own row** — the space to the right of the
 * thumbnail, which the answer grows into when it arrives.
 *
 * **This is the whole reason a photo's answer moved onto its event.** The scripted response used to
 * land as a separate row a second or two below, which is a bird with no visible relation to the
 * picture that produced it — and by the time it appeared the log had already put another row
 * between them. Here nothing moves and nothing is inserted: the thumbnail lands, the dots run in
 * the space beside it, and the name replaces them in place. One row, one thing that happened.
 *
 * The five states are the five things that can be true of a capture, and each has its own drawing:
 *
 * - **still crossing** — [WorkingDots] in the watcher's quieter ink, beside the empty tile the
 *   picture will fill. Three things tell this wait from the one below it, and the ink is the
 *   weakest of them: the tile is empty where the other has a picture in it, the gutter already
 *   wears the glasses mark (see [SourceMark]), and the dots are in the watcher's own hand because
 *   this is their capture arriving — gilt is reserved for the app talking, and nothing is being
 *   composed yet. **Deliberately wordless**: the row is on screen for about a second before the
 *   dots are replaced by gilt dots and then by a name, and a sentence in that slot is text that
 *   dies before it can be read;
 * - **nothing coming** — `PHOTO`, the plate the row has always worn, in the watcher's quieter ink;
 * - **waiting** — [WorkingDots], in gilt, because what is composing is the app's answer;
 * - **a bird** — the same gilt name-and-confidence pair a heard detection wears in the row below,
 *   deliberately identical: a bird is a bird however it was reached;
 * - **words** — an ambiguity or a decline, set as a sentence rather than a plate, because an answer
 *   is read.
 */
@Composable
private fun PhotoAnswer(
    identification: PhotoIdentification?,
    isCrossing: Boolean,
    modifier: Modifier = Modifier,
) {
  val colors = BirdSpotterTheme.colors
  if (isCrossing) {
    WorkingDots(
        color = colors.textFaint,
        modifier =
            modifier.semantics {
              contentDescription = "Receiving this photo from the glasses"
            },
    )
    return
  }
  when (identification) {
    null -> PlateLabel(text = "Photo", color = colors.textSecondary, modifier = modifier)

    PhotoIdentification.Pending ->
        WorkingDots(
            color = colors.gilt,
            modifier = modifier.semantics { contentDescription = "Identifying this photo" },
        )

    is PhotoIdentification.Bird ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.snug),
            modifier = modifier,
        ) {
          PlateLabel(text = identification.commonName, color = colors.gilt)
          PlateLabel(
              text = "${(identification.confidence * 100).roundToInt()}%",
              color = colors.textSecondary,
          )
        }

    is PhotoIdentification.Words ->
        Text(
            text = identification.text,
            style = BirdSpotterTheme.type.body,
            color = colors.gilt,
            modifier = modifier,
        )
  }
}

/**
 * The spine — in whichever of its two readings is up — and the two controls that belong on the
 * instrument rather than beside it.
 *
 * **The strip still takes no touch; the two things laid over it do.** A tap anywhere else on it
 * does nothing at all, which is the whole reason the live edge cannot be dragged away from now.
 * What sits on it is a stop and a choice of reading, both of which are about the instrument and
 * would be homeless anywhere else on the screen.
 */
@Composable
private fun SonogramStrip(
    sonogram: SonogramBuffer,
    start: Double,
    reading: StripReading,
    onStop: () -> Unit,
    onReadingChange: (StripReading) -> Unit,
) {
  val space = BirdSpotterTheme.space

  Box(
      Modifier.fillMaxWidth()
          .height(StripHeight)
          // Square and full-bleed: the strip is an instrument reading edge to edge, and a
          // rounded corner would be the app rounding off the seconds at either end of the window.
          //
          // The palette's own ground rather than `lacquer`, so a strip with nothing on it yet is
          // the same black as one the pipeline rendered. It is the ground under both readings —
          // whatever is drawn on top, an empty instrument is the same black.
          .background(StripGround)
          .semantics { contentDescription = reading.label },
  ) {
    when (reading) {
      StripReading.SONOGRAM -> SonogramReading(sonogram = sonogram, start = start)
      StripReading.WAVEFORM -> WaveformReading(sonogram = sonogram)
    }

    // The instrument's foot: the choice of reading at one end, the stop at the other, **on one
    // line**.
    //
    // **One row rather than two alignments, and that is the whole point of it.** The two used
    // to be placed separately — the stop bottom-centre, the switch bottom-end — which meant two
    // capsules of slightly different heights sitting on a shared bottom edge and therefore on
    // two different centre lines. A row centres them on each other, so the difference in their
    // heights is spent symmetrically and the strip's foot reads as one band of chrome instead
    // of two things that nearly line up.
    //
    // Neither child is padded from here: each carries its own `related` inset, which is both
    // its tap target and its clearance from the strip's edges. Padding the row as well would be
    // a second number in a gap that already has one.
    Row(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      ReadingSwitch(
          reading = reading,
          onReadingChange = onReadingChange,
          modifier = Modifier.padding(space.related),
      )

      // The stop, at the foot of the instrument it stops.
      //
      // **It used to sit at the head of the screen, and it is here because that is where the
      // recording is.** In the header it was a red disc opposite the source switch — two
      // unrelated controls sharing a bar, one of which ends the session. Down here it is on
      // the one thing on the screen that is visibly running, which is what it is the control
      // for; the header is left to say whose ears these are and nothing else.
      //
      // **The trailing corner, not the centre.** Centred it sat under the middle of the
      // instrument, which is the part of the strip a watcher is actually reading, and it put
      // the control that ends a recording where a thumb passes on its way to everything else.
      // In the corner it is still the largest, reddest thing on the strip's foot — findable
      // in a hurry — and it is nowhere the eye needs while the session is running.
      //
      // **The inset around the drawing is the tap target, and it is doing both jobs.** A
      // capsule of plate type is about twenty dp tall, which is a small thing to hit for the
      // one control that ends a recording — so the clickable is a `related` larger than the
      // pill on every side. That same inset is what holds the pill clear of the strip's
      // edges, which is why there is no second padding around it: one value, one gap, no
      // arithmetic between two numbers nobody wrote down.
      Box(
          Modifier.clip(CircleShape).clickable(onClick = onStop).padding(space.related).semantics {
            contentDescription = "End session"
          },
      ) {
        StopControl()
      }
    }
  }
}

/**
 * Eight seconds of history, scrolling, with the line marking now.
 *
 * Coloured from [SonogramPalette] rather than from the theme, so the live strip and the reference
 * strip on a bird's page are the same instrument — see the palette's note. The waveform is filled
 * off the identical ramp for the identical reason, one step further in: the two readings are the
 * same instrument as each other.
 *
 * The window is rasterised into one bitmap per redraw — [SonogramBuffer.window] fills it in a
 * single pass — and stretched to the strip's width. Drawing 500 columns as 500 rectangles would
 * cost the same picture and thousands of draw calls.
 *
 * A fresh bitmap per column is 256 KB of garbage sixty times a second, which is real but an order
 * of magnitude under what the camera's frames already cost, and it buys a draw path with nothing
 * mutable in it. If the strip ever stutters, reusing one bitmap and pushing pixels into it is the
 * first thing to try.
 */
@Composable
private fun SonogramReading(sonogram: SonogramBuffer, start: Double) {
  val gilt = BirdSpotterTheme.colors.gilt

  // Floored, not truncated: a window that has scrolled off the beginning has a negative start,
  // and truncation rounds those towards zero — a one-column stutter as the session's opening
  // seconds cross the left edge.
  val firstColumn = floor(start * SonogramColumnsPerSecond).toInt()
  val visibleColumns = (TimelineWindowSeconds * SonogramColumnsPerSecond).toInt()

  // Keyed on the count as well as the window, so the raster is rebuilt exactly when a column
  // lands or the window moves — and never on an unrelated recomposition. The clock's tick is what
  // brings us back here; these two decide whether there is anything new to draw.
  val strip: ImageBitmap =
      remember(sonogram.count, firstColumn) {
        val greyscale = sonogram.window(firstColumn, visibleColumns)
        val argb = IntArray(greyscale.size)
        for (i in greyscale.indices) argb[i] = MagmaRamp[greyscale[i].toInt() and 0xFF]
        Bitmap.createBitmap(argb, visibleColumns, SonogramBins, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
      }

  Image(
      bitmap = strip,
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
      modifier = Modifier.fillMaxSize(),
  )

  // Now, hard against the right edge. Always drawn, because the window is always the live one —
  // there is no scrubbed state left in which it could point at a moment that has already gone.
  //
  // **It belongs to this reading alone.** The waveform has no time axis, so a line saying "this
  // edge is the present" would be pointing at a frequency.
  Canvas(Modifier.fillMaxSize()) {
    drawLine(
        color = gilt,
        start = Offset(size.width - PlayheadWidth / 2f, 0f),
        end = Offset(size.width - PlayheadWidth / 2f, size.height),
        strokeWidth = PlayheadWidth,
    )
  }
}

/**
 * What the microphone is hearing **this instant** — see [LiveWaveform].
 *
 * **Each curve is drawn as the region between itself and its own reflection**, which is what turns
 * a sine into the chain of lenses this shape is recognised by: the wave pinches to a point wherever
 * it crosses zero and opens to its full height between. Filling one shape per curve rather than
 * stroking two lines also means there is no line weight to keep matched between two drawing APIs.
 *
 * The three are laid over each other in the app's own three inks, part-transparent, so where they
 * overlap the colour builds — see [WaveColours].
 *
 * A hairline down the middle underneath them all, always: an open microphone hearing nothing is
 * still an open microphone, and a strip drawing literally nothing reads as one that has stopped.
 *
 * **`withInfiniteAnimationFrameNanos`, not the session's tick.** The screen's clock runs at thirty
 * a second, which is right for a strip that scrolls four dp at a time and plainly wrong for a wave
 * that is meant to glide — at thirty the travel reads as a flip-book. This asks for every frame the
 * display can show, so the motion is as smooth as the phone is, and the loop only runs while this
 * reading is the one on screen.
 *
 * **The phase is read inside the draw lambda on purpose.** A state read there invalidates the draw
 * phase alone: the frame clock never recomposes anything, never re-measures anything, and the whole
 * per-frame cost is one pass of arithmetic and three paths. Reading it up in the composable would
 * recompose this subtree at the display's refresh rate to draw the identical layout.
 */
@Composable
private fun WaveformReading(sonogram: SonogramBuffer) {
  val colors = BirdSpotterTheme.colors
  val phase = remember { mutableDoubleStateOf(0.0) }
  LaunchedEffect(Unit) {
    while (true) {
      withInfiniteAnimationFrameNanos { nanos ->
        phase.doubleValue = nanos / NanosPerSecond
      }
    }
  }

  Canvas(Modifier.fillMaxSize()) {
    val curves =
        LiveWaveform.curves(
            bins = sonogram.recentBins(LiveWaveformColumns),
            level = sonogram.recentLevel(LiveWaveformColumns),
            phase = phase.doubleValue,
        )

    val centre = size.height / 2f
    // A little short of the strip's own edge: a wave that touched the top would read as
    // clipped rather than as loud.
    val reach = centre - WaveformInset.toPx()
    val hairline = WaveformHairline.toPx()

    drawRect(
        color = colors.textFaint.copy(alpha = 0.5f),
        topLeft = Offset(0f, centre - hairline / 2f),
        size = Size(size.width, hairline),
    )

    val inks = WaveColours(colors)
    curves.forEachIndexed { index, curve ->
      if (curve.size < 2) return@forEachIndexed
      val step = size.width / (curve.size - 1)

      val path = Path()
      curve.forEachIndexed { i, height ->
        val y = centre - height * reach
        if (i == 0) path.moveTo(0f, y) else path.lineTo(i * step, y)
      }
      // Back along the reflection, so the curve closes into its own lens chain.
      for (i in curve.indices.reversed()) {
        path.lineTo(i * step, centre + curve[i] * reach)
      }
      path.close()

      drawPath(path = path, color = inks[index])
    }
  }
}

/**
 * Which reading the strip is drawn as — **both sides always shown**, in the corner of the
 * instrument they belong to.
 *
 * The same argument the source switch makes, at a quieter volume: a control that showed only the
 * reading you are already looking at is a control nobody knows they can press, and this one has no
 * other clue anywhere on the screen. Two words in a track says there is a choice here, and which
 * way it is set, without anybody touching it.
 *
 * **Not gilt.** Gilt is what the app answers in — a named bird, a source that is live — and
 * choosing how to draw a picture is not the app answering anything. The lit side takes the ordinary
 * ink on a raised lacquer track; the unlit side is faint. Bottom-left, opposite the stop — the
 * quieter of the strip's two controls, at the end a hand is not resting on, and where nothing a
 * watcher is reading passes underneath.
 */
@Composable
private fun ReadingSwitch(
    reading: StripReading,
    onReadingChange: (StripReading) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors

  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          modifier
              .clip(CircleShape)
              // The track the two sides sit in, at the switch's own hairline inset — see
              // `SourceSwitch`.
              .background(colors.ink.copy(alpha = ReadingTrackOpacity))
              .padding(SwitchTrackInset),
  ) {
    StripReading.entries.forEach { option ->
      val isSelected = option == reading
      val ink by animateColorAsState(
          targetValue = if (isSelected) colors.textPrimary else colors.textFaint,
          animationSpec = tween(ReadingThrowMillis),
          label = "readingInk",
      )
      val wash by animateColorAsState(
          targetValue = if (isSelected) colors.lacquerHigh else Color.Transparent,
          animationSpec = tween(ReadingThrowMillis),
          label = "readingWash",
      )

      PlateLabel(
          text = option.plate,
          color = ink,
          modifier =
              Modifier.clip(CircleShape)
                  .background(wash)
                  // Pressing the lit side is deliberately inert, exactly as it is on the source
                  // switch: there is nothing on this side of the choice left to choose.
                  .then(
                      if (isSelected) {
                        Modifier
                      } else {
                        Modifier.clickable { onReadingChange(option) }
                      },
                  )
                  .padding(horizontal = space.snug, vertical = space.tight)
                  .semantics { contentDescription = option.label },
      )
    }
  }
}

/** Tall enough that 256 bins are more than a smear, short enough to leave the log room. */
private val StripHeight = 132.dp

/** The playhead, in pixels — a hairline would disappear against a bright column. */
private const val PlayheadWidth = 2f

/**
 * The band the log's head fades across, and the distance of scroll that brings it in.
 *
 * `space.section`'s 32, written out because a top-level constant has no theme to read — deep enough
 * that a row is dimming for a moment rather than winking out, and shallow enough that the row under
 * it is still a row you can read. The foot's fade is measured the same way, off the shutter's band
 * rather than off the scale, for the same reason: a fade should be as tall as whatever it is fading
 * behind.
 */
private val LogHeadroom = 32.dp

/**
 * The air between the reading switch's track and the side sitting in it. The same hairline the
 * source switch uses, and for the same reason — at `tight` the track reads as a second, larger pill
 * around the first.
 */
private val SwitchTrackInset = 2.dp

/**
 * How dark the reading switch's track is. A step under the stop's capsule, because this is a
 * setting on the instrument rather than a way out of it: it should be findable, not noticed.
 */
private const val ReadingTrackOpacity = 0.45f

/**
 * How long the reading switch takes to throw. Short — the picture has already changed, and this is
 * only the ink catching up.
 */
private const val ReadingThrowMillis = 180

/**
 * Room for `12:00` in the stamp column, so the log's second column starts in the same place all the
 * way down however long the session runs.
 */
private val StampWidth = 44.dp

/** A photo in the log. Big enough to recognise the bird in it, small enough that a row is a row. */
private val LogPhotoSize = 44.dp

/**
 * The device mark in a row's gutter — smaller than the source pill's 16, because the pill is the
 * session announcing what it is riding and this is a footnote on one row.
 */
private val LogSourceGlyphSize = 14.dp

/**
 * The ramp, packed as ARGB once. It no longer depends on anything that can change at runtime, so
 * rebuilding it per redraw would be 256 interpolations to reach the same 256 answers.
 */
private val MagmaRamp: IntArray =
    SonogramPalette.ramp()
        .map { 0xFF shl 24 or (it.red shl 16) or (it.green shl 8) or it.blue }
        .toIntArray()

/** Magma's darkest end — what the strip shows where nothing has been heard. */
private val StripGround = Color(MagmaRamp[0])

/**
 * How many of the newest columns the live waves take their heights from — see
 * [SonogramBuffer.recentBins].
 *
 * Eight at 62.5 columns a second is about an eighth of a second. **This is the one number that
 * decides whether the picture is smooth**: the sine and the envelope have no jitter in them, so
 * everything that can move suddenly moves through here. Four was visibly twitchy; much beyond eight
 * and a call has finished before the wave finishes rising to it.
 */
private const val LiveWaveformColumns = 8

/**
 * How far the waves stay clear of the strip's top and bottom edges. A loud moment should read as
 * loud, not as clipped, and the only way to see the difference is to leave somewhere to clip to.
 */
private val WaveformInset = 10.dp

/** The line down the middle, under everything — the instrument's own zero. */
private val WaveformHairline = 1.5.dp

/** Nanoseconds to seconds, for the frame clock. */
private const val NanosPerSecond = 1_000_000_000.0

/**
 * The three curves' inks, outermost first — **the cabinet's own colours, not the instrument's
 * ramp**.
 *
 * The sonogram is magma because it is a *reading*, and magma is the map the reference strip on a
 * bird's page is rendered in; a live column and a printed one have to be comparable. The wave is an
 * indicator, and an indicator is the app talking. Drawing it in the app's inks is what says which
 * of the two you are looking at, before reading the switch.
 *
 * **Blue, green, gold — low band to high, cool to warm.** The three curves carry the bottom, middle
 * and top thirds of the spectrum (see [LiveWaveform]), and running the inks up the same way the
 * pitch runs means the picture says which third of the room is loud without anybody being told the
 * mapping: a voice lifts the blue, a bird lifts the gold. Gilt lands on the high band, which is
 * both the brightest ink here and the band a bird sings in.
 *
 * Cream used to hold the high band, and it went because a near-white curve was legible as *bright*
 * rather than as a colour — at three overlapping shapes it bleached the two under it wherever they
 * crossed. [Smalt] is the one pigment of the three the cabinet does not otherwise stock, and lives
 * in this file for that reason.
 *
 * **Vermilion is deliberately not among them** — it is the ink of *this ends something*, spent on
 * exactly two controls, and one of those is the stop pill sitting on this very strip. A red wave
 * beside a red stop is two reds meaning two different things a hand's width apart.
 *
 * Part-transparent so the overlaps build a fourth colour rather than the last one drawn simply
 * winning — that layering is most of what the shape is. One alpha across all three now that none of
 * them is near-white: the pigments are of a weight, so the picture has no accidental hierarchy
 * beyond the one the bands themselves put there.
 *
 * A function rather than a `val`: two of the three come off the theme, which a top-level constant
 * cannot read.
 */
private fun WaveColours(colors: BirdSpotterColors) = listOf(
    Smalt.copy(alpha = 0.72f),
    colors.verdigris.copy(alpha = 0.72f),
    colors.gilt.copy(alpha = 0.72f),
)

/**
 * The wave's third pigment: **smalt**, the cabinet's blue, and the one ink in this app that lives
 * in a screen rather than in the palette.
 *
 * It is here rather than on [BirdSpotterColors] on purpose. The palette is a small set of inks that
 * each mean something everywhere they appear — gilt is the app answering, verdigris is the app
 * asking you to act, vermilion is *this ends something* — and a fourth pigment added for one
 * drawing would be a colour with no such job, waiting to be reached for by the next screen that
 * wanted a blue. This has exactly one use: the low band of the live waveform, on a strip that only
 * ever renders in the dark palette, which is why one value serves and there is no light twin.
 *
 * Chosen against [BirdSpotterColors.verdigris] rather than in the abstract — the same muted,
 * mid-luminance register, far enough round the wheel that the two curves are plainly two colours
 * where they cross and not a green that has gone slightly cold.
 */
private val Smalt = Color(0xFF6E86B4)
