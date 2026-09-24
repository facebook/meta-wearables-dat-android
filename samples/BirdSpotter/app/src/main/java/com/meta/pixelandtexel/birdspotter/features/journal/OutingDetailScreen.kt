/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.audio.SystemOutingAudioPlayer
import com.meta.pixelandtexel.birdspotter.data.journal.OutingEventType
import com.meta.pixelandtexel.birdspotter.data.journal.OutingKind
import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.domain.SightingLocation
import com.meta.pixelandtexel.birdspotter.features.identify.sessionStamp
import com.meta.pixelandtexel.birdspotter.ui.components.CardMetrics
import com.meta.pixelandtexel.birdspotter.ui.components.CatalogPhoto
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.HeroPhotoWidthPx
import com.meta.pixelandtexel.birdspotter.ui.components.OpenPhoto
import com.meta.pixelandtexel.birdspotter.ui.components.PhotoLightboxHost
import com.meta.pixelandtexel.birdspotter.ui.components.PhotoLightboxScope
import com.meta.pixelandtexel.birdspotter.ui.components.PhotoLightboxSource
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.components.SightingMap
import com.meta.pixelandtexel.birdspotter.ui.previews.PreviewCatalog
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * One journal entry's page — the plate, the birds it confirmed, the recording a live outing left
 * (played back under its whole sonogram), the timeline of what landed on it, and the field notes.
 * One surface, two entry points (stop, and a Journal row), per the design doc; a `MANUAL` entry
 * simply has no recording or timeline to show.
 *
 * Pushed by `Route.OutingDetail` from a Journal row. Where `BirdDetailScreen` draws its own bar to
 * run a plate full-bleed under the status bar, this keeps an ordinary [CenterAlignedTopAppBar]: it
 * is opened from one place and wants a plain back. Navigation chrome is one of the things
 * deliberately left un-mirrored.
 *
 * Split into a stateful wrapper and a stateless overload so the Previews render a page without a
 * store behind it.
 */
@Composable
fun OutingDetailScreen(
    outingId: String,
    onBack: () -> Unit,
    onOpenBird: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: OutingDetailViewModel = viewModel(
      key = outingId,
      factory =
          OutingDetailViewModel.factory(
              outingId = outingId,
              journal = application.container.journalRepository,
              birdCatalog = application.container.birdCatalogRepository,
              mediaFileStore = application.container.mediaFileStore,
              // Owned by the view model from here — released in `onCleared`, like the bird
              // page's clip player.
              player = SystemOutingAudioPlayer(),
          ),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val playback by viewModel.playback.collectAsStateWithLifecycle()

  OutingDetailScreen(
      uiState = uiState,
      playback = playback,
      mediaFileStore = application.container.mediaFileStore,
      onBack = onBack,
      onOpenBird = onOpenBird,
      onOpenInMaps = { location -> application.container.mapLauncher.open(location) },
      onTogglePlayback = viewModel::togglePlayback,
      onScrub = viewModel::scrub,
      onSeek = viewModel::seek,
      onSeekRow = viewModel::seekTo,
      // Pop on success — the store is the source of truth, so the screen leaves once the row
      // is actually gone.
      onDelete = { viewModel.delete(onDeleted = onBack) },
      modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutingDetailScreen(
    uiState: OutingDetailUiState,
    playback: OutingPlaybackUiState?,
    mediaFileStore: MediaFileStore?,
    onBack: () -> Unit,
    onOpenBird: (String) -> Unit,
    onOpenInMaps: (SightingLocation) -> Unit,
    onTogglePlayback: () -> Unit,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekRow: (TimelineRow) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var confirmingDelete by remember { mutableStateOf(false) }
  val entry = (uiState as? OutingDetailUiState.Loaded)?.entry
  val title = entry?.primaryBird?.species?.species?.commonName ?: "Journal Entry"

  Scaffold(
      modifier = modifier,
      containerColor = BirdSpotterTheme.colors.paper,
      topBar = {
        // Parted from the page by a rule, the way the bottom bar is — Material draws no
        // divider of its own until the content scrolls under it.
        Column {
          CenterAlignedTopAppBar(
              title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
              navigationIcon = {
                IconButton(onClick = onBack) {
                  Icon(
                      painter = glyph(BirdSpotterTheme.glyphs.back),
                      contentDescription = "Back",
                  )
                }
              },
              colors =
                  TopAppBarDefaults.topAppBarColors(
                      containerColor = BirdSpotterTheme.colors.paper,
                      scrolledContainerColor = BirdSpotterTheme.colors.paper,
                      titleContentColor = BirdSpotterTheme.colors.textPrimary,
                      navigationIconContentColor = BirdSpotterTheme.colors.textPrimary,
                  ),
          )
          HairlineRule()
        }
      },
      // The app shell already reserved the bottom bar and the horizontal edges; the top bar
      // above handles the status bar itself, so the content owes no more insets.
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { innerPadding ->
    when (uiState) {
      is OutingDetailUiState.Loading -> OutingDetailPlaceholder(Modifier.padding(innerPadding))

      is OutingDetailUiState.NotFound -> OutingNotFound(Modifier.padding(innerPadding))

      is OutingDetailUiState.Loaded ->
          OutingDetailLoaded(
              entry = uiState.entry,
              timeline = uiState.timeline,
              playback = playback,
              mediaFileStore = mediaFileStore,
              contentPadding = innerPadding,
              onOpenBird = onOpenBird,
              onOpenInMaps = onOpenInMaps,
              onTogglePlayback = onTogglePlayback,
              onScrub = onScrub,
              onSeek = onSeek,
              onSeekRow = onSeekRow,
              onDelete = { confirmingDelete = true },
          )
    }
  }

  if (confirmingDelete) {
    AlertDialog(
        onDismissRequest = { confirmingDelete = false },
        title = { Text("Delete this entry?") },
        text = {
          Text(
              "This removes the entry and anything it captured — photos, audio, and its birds. It can't be undone.",
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                confirmingDelete = false
                onDelete()
              },
          ) {
            Text("Delete")
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
        },
    )
  }
}

/**
 * The loaded page: a plate, the nameplate — and for a live outing, the recording itself, playable
 * under the whole session's sonogram, the timeline of what landed on it, and the birds the watcher
 * confirmed out of it. Then the field notes (the wizard's answers included, as provenance for the
 * ID), any written note, a way into the guide per confirmed bird, and the delete affordance.
 */
@Composable
private fun OutingDetailLoaded(
    entry: JournalEntry,
    timeline: List<TimelineRow>,
    playback: OutingPlaybackUiState?,
    mediaFileStore: MediaFileStore?,
    contentPadding: PaddingValues,
    onOpenBird: (String) -> Unit,
    onOpenInMaps: (SightingLocation) -> Unit,
    onTogglePlayback: () -> Unit,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekRow: (TimelineRow) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space

  /** The photograph being looked at full screen, or `null` — see [PhotoLightbox]. */
  var openPhoto by remember { mutableStateOf<OpenPhoto?>(null) }

  // Hoisted above the host, because the page is unmounted while a photograph is open and a
  // scroll position remembered inside would not survive the trip — a reader who opened a photo
  // from the timeline should come back to the timeline, not to the top of the entry.
  val scrollState = rememberScrollState()

  PhotoLightboxHost(
      openPhoto = openPhoto,
      onClose = { openPhoto = null },
      modifier = modifier.fillMaxSize(),
      mediaFileStore = mediaFileStore,
  ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).verticalScroll(scrollState),
    ) {
      // Full-bleed, like Explore's and the guide's plates — a deliberate exception to the
      // gutter.
      OutingHero(
          entry = entry,
          mediaFileStore = mediaFileStore,
          onOpenPhoto = { openPhoto = it },
          modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
      )

      Column(
          modifier =
              Modifier.padding(horizontal = space.gutter)
                  .padding(top = space.section, bottom = space.page),
          verticalArrangement = Arrangement.spacedBy(space.section),
      ) {
        Nameplate(entry)

        playback?.let { state ->
          RecordingSection(
              playback = state,
              onTogglePlayback = onTogglePlayback,
              onScrub = onScrub,
              onSeek = onSeek,
          )
        }

        // A live outing that carries no audio rows says so, plainly — the absence is a
        // fact about the entry (saved before recording shipped, or a save that dropped
        // its audio), and a page that hides the whole idea reads as a mystery. Keyed on
        // the rows rather than on [playback], which is legitimately null for a beat
        // while a real recording decodes.
        if (entry.outing.kind == OutingKind.LIVE && entry.audio.isEmpty()) {
          DetailSection(title = "Recording") {
            Text(
                text = "No recording was kept with this entry.",
                style = BirdSpotterTheme.type.body,
                color = BirdSpotterTheme.colors.textFaint,
            )
          }
        }

        if (timeline.isNotEmpty()) {
          TimelineSection(
              timeline = timeline,
              mediaFileStore = mediaFileStore,
              onSeekRow = onSeekRow,
              onOpenPhoto = { openPhoto = it },
          )
        }

        if (entry.outing.kind == OutingKind.LIVE && entry.birds.isNotEmpty()) {
          ConfirmedSection(entry = entry, onOpenBird = onOpenBird)
        }

        DetailSection(title = "Field Notes") {
          Column {
            detailRows(entry).forEachIndexed { index, (label, value) ->
              if (index > 0) HairlineRule()
              DetailRow(label = label, value = value)
            }
          }
        }

        entry.location.let { location ->
          OutingLocationSection(location = location, onOpenInMaps = onOpenInMaps)
        }

        entry.outing.notes
            ?.takeIf { it.isNotEmpty() }
            ?.let { notes ->
              DetailSection(title = "Notes") {
                Text(
                    text = notes,
                    style = BirdSpotterTheme.type.body,
                    color = BirdSpotterTheme.colors.textSecondary,
                )
              }
            }

        // A live outing's guide links live on its Confirmed rows; the wizard's one bird
        // keeps the plain row it always had.
        if (entry.outing.kind != OutingKind.LIVE) {
          InTheGuide(entry, onOpenBird)
        }

        DeleteButton(onDelete)
      }
    }
  }
}

/**
 * The recording, whole: the session's sonogram with the playhead held centre, and the transport
 * under it — play scrolls the strip, a drag or a tapped row moves it by hand. See [OutingSonogram]
 * for why the strip runs the way it does.
 */
@Composable
private fun RecordingSection(
    playback: OutingPlaybackUiState,
    onTogglePlayback: () -> Unit,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  DetailSection(title = "Recording", modifier = modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
      OutingSonogram(
          sonogram = playback.sonogram,
          positionMs = playback.positionMs,
          totalMs = playback.totalMs,
          onScrub = onScrub,
          onSeek = onSeek,
      )
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(space.separate),
      ) {
        RecordingPlayButton(
            isPlaying = playback.isPlaying,
            onClick = onTogglePlayback,
        )
        Text(
            text =
                sessionStamp(playback.positionMs / 1000.0) +
                    " / " +
                    sessionStamp(playback.totalMs / 1000.0),
            style = BirdSpotterTheme.type.data,
            color = BirdSpotterTheme.colors.textSecondary,
        )
      }
    }
  }
}

/** The transport, worn exactly as the bird page's clip button wears it. */
@Composable
private fun RecordingPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier
          .size(RecordingPlaySize)
          .clip(CircleShape)
          .background(BirdSpotterTheme.colors.verdigris)
          .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painter =
            glyph(
                if (isPlaying) BirdSpotterTheme.glyphs.pause else BirdSpotterTheme.glyphs.play,
            ),
        contentDescription = if (isPlaying) "Pause recording" else "Play recording",
        tint = BirdSpotterTheme.colors.paper,
        modifier = Modifier.size(RecordingPlayGlyphSize),
    )
  }
}

/**
 * Everything that landed on the session, oldest first — the live log, read back at leisure. Every
 * row on the clock takes a tap, and the tap moves the playhead to its moment: the timeline is the
 * recording's index, not a second list beside it.
 */
@Composable
private fun PhotoLightboxScope.TimelineSection(
    timeline: List<TimelineRow>,
    mediaFileStore: MediaFileStore?,
    onSeekRow: (TimelineRow) -> Unit,
    onOpenPhoto: (OpenPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
  DetailSection(title = "Timeline", modifier = modifier) {
    Column {
      timeline.forEachIndexed { index, row ->
        if (index > 0) HairlineRule()
        TimelineRowView(
            row = row,
            mediaFileStore = mediaFileStore,
            onSeek = { onSeekRow(row) },
            onOpenPhoto = onOpenPhoto,
        )
      }
    }
  }
}

/**
 * One moment, in the log's own inks: gilt for what the journal calls a sighting, the quieter hand
 * for everything else — the same rule the review screen taught, so a reader can tell a confirmed
 * bird from a passed-over one without a legend.
 */
@Composable
private fun PhotoLightboxScope.TimelineRowView(
    row: TimelineRow,
    mediaFileStore: MediaFileStore?,
    onSeek: () -> Unit,
    onOpenPhoto: (OpenPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .then(
                  if (row.offsetMs != null) {
                    Modifier.clickable(onClickLabel = "Play from here", onClick = onSeek)
                  } else {
                    Modifier
                  },
              )
              .padding(vertical = space.related),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(space.related),
  ) {
    Text(
        text = row.offsetMs?.let { sessionStamp(it / 1000.0) } ?: "—",
        style = BirdSpotterTheme.type.data,
        color = colors.textFaint,
        modifier = Modifier.width(TimelineStampWidth),
    )

    when (row) {
      is TimelineRow.Detection -> {
        row.photo?.let { photo ->
          if (mediaFileStore != null) {
            OutingPhoto(
                media = photo,
                mediaFileStore = mediaFileStore,
                maxWidthPx = TimelinePhotoPx,
                contentDescription = "View this photo",
                modifier =
                    Modifier.size(TimelinePhotoSize)
                        .clip(RoundedCornerShape(space.tight))
                        .photoTransitionSource(photo.id)
                        // The row's own click still moves the playhead; this sits inside
                        // it and takes the 44dp the photograph covers, so a thumbnail
                        // opens the picture and the rest of the row seeks.
                        .clickable {
                          onOpenPhoto(
                              OpenPhoto(
                                  id = photo.id,
                                  source = PhotoLightboxSource.Media(photo),
                                  caption = row.commonName,
                              ),
                          )
                        },
            )
          }
        }
        PlateLabel(
            text = row.commonName ?: "Unidentified",
            color = if (row.isConfirmed) colors.gilt else colors.textSecondary,
        )
        row.confidence?.let { confidence ->
          PlateLabel(
              text = JournalFormatting.confidenceLabel(confidence),
              color = if (row.isConfirmed) colors.textSecondary else colors.textFaint,
          )
        }
        if (row.isConfirmed) {
          Spacer(Modifier.weight(1f))
          PlateLabel(text = "Confirmed", color = colors.gilt)
        }
      }

      is TimelineRow.Exchange ->
          Column(Modifier.weight(1f)) {
            Text(
                text = row.question,
                style = BirdSpotterTheme.type.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(space.tight))
            Text(
                text = row.answer,
                style = BirdSpotterTheme.type.body,
                color = colors.gilt,
            )
          }

      is TimelineRow.Photo -> {
        if (mediaFileStore != null) {
          OutingPhoto(
              media = row.media,
              mediaFileStore = mediaFileStore,
              maxWidthPx = TimelinePhotoPx,
              contentDescription = "View this photo",
              modifier =
                  Modifier.size(TimelinePhotoSize)
                      .clip(RoundedCornerShape(space.tight))
                      .photoTransitionSource(row.media.id)
                      .clickable {
                        onOpenPhoto(
                            OpenPhoto(
                                id = row.media.id,
                                source = PhotoLightboxSource.Media(row.media),
                            ),
                        )
                      },
          )
        }
        PlateLabel(text = "Photo", color = colors.textSecondary)
      }
    }
  }
}

/**
 * The birds this outing put in the life list, each against the moment it was confirmed from and the
 * confidence the detector offered — where one actually scored the bird the watcher named (see
 * [OutingWithChildren.confidenceOf]). The row is the way into the guide, which is why a live outing
 * has no separate guide list.
 */
@Composable
private fun ConfirmedSection(
    entry: JournalEntry,
    onOpenBird: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  DetailSection(title = "Confirmed", modifier = modifier) {
    Column {
      entry.birds.forEachIndexed { index, bird ->
        if (index > 0) HairlineRule()
        val species = bird.species
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .then(
                        if (species != null) {
                          Modifier.clickable { onOpenBird(species.species.id) }
                        } else {
                          Modifier
                        },
                    )
                    .padding(vertical = space.related),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.snug),
        ) {
          Column(Modifier.weight(1f)) {
            Text(
                // The unresolvable id renders as a bird all the same — see
                // JournalRepository: an unidentified sighting, never an error.
                text = species?.species?.commonName ?: "Unidentified",
                style = BirdSpotterTheme.type.body,
                color = colors.textPrimary,
            )
            val stamp =
                entry.withChildren.offsetOf(bird.sighting)?.let { sessionStamp(it / 1000.0) }
            val confidence =
                entry.withChildren.confidenceOf(bird.sighting)?.let {
                  JournalFormatting.confidenceLabel(it)
                }
            val detail = listOfNotNull(stamp, confidence).joinToString(" · ")
            if (detail.isNotEmpty()) {
              Text(
                  text = detail,
                  style = BirdSpotterTheme.type.caption,
                  color = colors.textFaint,
              )
            }
          }
          if (species != null) {
            Icon(
                painter = glyph(BirdSpotterTheme.glyphs.disclosure),
                contentDescription = null,
                tint = colors.textFaint,
                modifier = Modifier.size(14.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun Nameplate(entry: JournalEntry, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  Column(modifier = modifier.fillMaxWidth()) {
    val primary = entry.primaryBird?.species
    if (primary != null) {
      PlateLabel(text = primary.species.familyName, color = BirdSpotterTheme.colors.verdigris)
      Spacer(Modifier.height(space.related))
      Text(
          text = primary.species.commonName,
          style = BirdSpotterTheme.type.display,
          color = BirdSpotterTheme.colors.textPrimary,
      )
      Spacer(Modifier.height(space.tight))
      Text(
          text =
              if (entry.extraBirdCount > 0) {
                "and ${entry.extraBirdCount} more"
              } else {
                primary.species.scientificName
              },
          style = BirdSpotterTheme.type.scientific,
          color = BirdSpotterTheme.colors.textSecondary,
      )
    } else {
      PlateLabel(
          text = JournalFormatting.kindLabel(entry.outing.kind),
          color = BirdSpotterTheme.colors.verdigris,
      )
      Spacer(Modifier.height(space.related))
      Text(
          // The Merlin case, first-class: saved, just nothing confirmed.
          text = "No birds confirmed",
          style = BirdSpotterTheme.type.display,
          color = BirdSpotterTheme.colors.textSecondary,
      )
    }
  }
}

/** A tappable guide row per confirmed bird — one bird keeps the old single-line wording. */
@Composable
private fun InTheGuide(
    entry: JournalEntry,
    onOpenBird: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val resolved = entry.birds.mapNotNull { it.species }
  if (resolved.isEmpty()) return

  Column(modifier = modifier.fillMaxWidth()) {
    resolved.forEachIndexed { index, species ->
      if (index > 0) HairlineRule()
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .clickable { onOpenBird(species.species.id) }
                  .padding(vertical = space.related),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(space.snug),
      ) {
        Text(
            text =
                if (resolved.size == 1) {
                  "View in the field guide"
                } else {
                  species.species.commonName
                },
            style = BirdSpotterTheme.type.body,
            color = BirdSpotterTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.disclosure),
            contentDescription = null,
            tint = BirdSpotterTheme.colors.textFaint,
            modifier = Modifier.size(14.dp),
        )
      }
    }
  }
}

/**
 * The outing's spot as a small map, with the coordinate and a tap-through to the maps app beneath
 * it — shown only when the outing actually carries a fix. The map is a static record here, not a
 * navigator: a tap hands off rather than panning.
 */
@Composable
private fun OutingLocationSection(
    location: SightingLocation,
    onOpenInMaps: (SightingLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  DetailSection(title = "Location", modifier = modifier) {
    Column(Modifier.fillMaxWidth()) {
      SightingMap(
          location = location,
          onTap = { onOpenInMaps(location) },
          modifier =
              Modifier.fillMaxWidth()
                  // A record's map, not a navigator's — tall enough to place the pin in its
                  // blocks, short enough to leave the field notes above the fold.
                  .height(180.dp)
                  .clip(RoundedCornerShape(CardMetrics.CornerRadius)),
      )
      // "Open in Maps  ›" — worded and weighted like the guide rows, with the
      // coordinate as its quiet second line. Its own vertical padding is the gap above it
      // (the map sits flush), so the column sets no rhythm of its own.
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .clickable { onOpenInMaps(location) }
                  .padding(vertical = space.related),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(space.snug),
      ) {
        Column(Modifier.weight(1f)) {
          Text(
              text = "Open in Maps",
              style = BirdSpotterTheme.type.body,
              color = BirdSpotterTheme.colors.textPrimary,
          )
          Text(
              text = JournalFormatting.coordinates(location.coordinate),
              style = BirdSpotterTheme.type.caption,
              color = BirdSpotterTheme.colors.textFaint,
          )
        }
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.disclosure),
            contentDescription = null,
            tint = BirdSpotterTheme.colors.textFaint,
            modifier = Modifier.size(14.dp),
        )
      }
    }
  }
}

/**
 * Understated on the page — a ruled button, not a red one — with the destructive weight carried by
 * the confirmation dialog. The palette has no red, and a loud control here would be the one
 * off-palette mark on the screen.
 */
@Composable
private fun DeleteButton(onDelete: () -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(CardMetrics.CornerRadius)
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape)
              .border(1.dp, BirdSpotterTheme.colors.rule, shape)
              .clickable(onClick = onDelete)
              .padding(vertical = BirdSpotterTheme.space.related),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = "Delete Entry",
        style = BirdSpotterTheme.type.label,
        color = BirdSpotterTheme.colors.textSecondary,
    )
  }
}

/**
 * A plate-capped heading over its content, at the page's one section rhythm — the entry page's own
 * guide section.
 */
@Composable
private fun DetailSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.related),
  ) {
    PlateLabel(text = title, color = BirdSpotterTheme.colors.gilt)
    content()
  }
}

/** One fact in the field notes: a small-caps label and its value, on one ruled line. */
@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
  Row(
      modifier = modifier.fillMaxWidth().padding(vertical = BirdSpotterTheme.space.related),
      horizontalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.separate),
      verticalAlignment = Alignment.Top,
  ) {
    Text(
        text = label,
        style = BirdSpotterTheme.type.label,
        color = BirdSpotterTheme.colors.textFaint,
    )
    Text(
        text = value,
        style = BirdSpotterTheme.type.body,
        color = BirdSpotterTheme.colors.textPrimary,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
    )
  }
}

/**
 * The entry's lead image: the first captured photo, else the primary bird's plate, else a marked
 * tile.
 */
@Composable
private fun PhotoLightboxScope.OutingHero(
    entry: JournalEntry,
    mediaFileStore: MediaFileStore?,
    onOpenPhoto: (OpenPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
  val photo = entry.photos.firstOrNull()
  val plate = entry.primaryBird?.species?.heroPhoto
  when {
    // **A bird's plate is not openable, deliberately** — the branch below stands a catalog
    // illustration in for a photo nobody took, and blowing that up full screen would present
    // somebody else's drawing as this outing's own record.
    photo != null && mediaFileStore != null ->
        OutingPhoto(
            media = photo,
            mediaFileStore = mediaFileStore,
            maxWidthPx = HeroPhotoWidthPx,
            contentDescription = "View this photo",
            modifier =
                modifier.photoTransitionSource(photo.id).clickable {
                  onOpenPhoto(
                      OpenPhoto(
                          id = photo.id,
                          source = PhotoLightboxSource.Media(photo),
                          caption = entry.primaryBird?.species?.species?.commonName,
                      ),
                  )
                },
        )

    plate != null -> CatalogPhoto(media = plate, maxWidthPx = HeroPhotoWidthPx, modifier = modifier)

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
              modifier = Modifier.size(44.dp),
          )
        }
  }
}

/** The page's silhouette while the read runs, so nothing jumps when it lands. */
@Composable
private fun OutingDetailPlaceholder(modifier: Modifier = Modifier) {
  Column(modifier.fillMaxSize()) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(BirdSpotterTheme.colors.rule),
    )
  }
}

/** Shown for an entry the store no longer has. Not an error, so it does not read as one. */
@Composable
private fun OutingNotFound(modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  Column(
      modifier = modifier.fillMaxSize().padding(space.gutter),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(space.related, Alignment.CenterVertically),
  ) {
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.help),
        contentDescription = null,
        tint = BirdSpotterTheme.colors.textSecondary,
        modifier = Modifier.size(48.dp),
    )
    Text(
        text = "Entry Not Found",
        style = BirdSpotterTheme.type.title,
        color = BirdSpotterTheme.colors.textPrimary,
    )
    Text(
        text = "This entry is no longer in your journal.",
        style = BirdSpotterTheme.type.body,
        color = BirdSpotterTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
  }
}

private fun detailRows(entry: JournalEntry): List<Pair<String, String>> {
  val outing = entry.outing
  val primary = entry.primaryBird?.sighting
  return buildList {
    add("Spotted" to JournalFormatting.dateTime(outing.startedAt))
    if (outing.kind == OutingKind.LIVE) {
      outing.durationMs?.let { add("Length" to JournalFormatting.durationLabel(it)) }
    }
    add("Logged" to JournalFormatting.kindLabel(outing.kind))
    // The confirmed moment's context — the primary bird's, since the outing spans many.
    // Read down the evidence links rather than off the sighting: aim belongs to the
    // photo that was aimed, and a confidence only prints when the watcher confirmed the
    // species the detection proposed.
    if (primary != null) {
      val moment = entry.withChildren.momentOf(primary)
      moment.gazeContext?.let { add("Looking" to JournalFormatting.gazeLabel(it)) }
      moment.bearingDeg?.let { add("Facing" to JournalFormatting.bearingLabel(it)) }
      entry.withChildren.confidenceOf(primary)?.let {
        add("Confidence" to JournalFormatting.confidenceLabel(it))
      }
    }
    // The wizard's answers, as provenance for the ID — "you said: robin-sized, black
    // and red, in trees". Trait order, not insertion order, so the page reads like the
    // wizard walked.
    entry.withChildren.events
        .filter { it.type == OutingEventType.WIZARD_ANSWER && it.trait != null && it.value != null }
        .sortedBy { it.trait!!.ordinal }
        .forEach { event ->
          add(
              JournalFormatting.wizardTraitLabel(event.trait!!) to
                  JournalFormatting.wizardAnswerLabel(event.trait, event.value!!),
          )
        }
  }
}

/** The transport, sized as the bird page sizes its clip button. */
private val RecordingPlaySize = 44.dp
private val RecordingPlayGlyphSize = 20.dp

/**
 * Room for `12:00` in the stamp column — the live log's width, so the two logs line up in the hand.
 */
private val TimelineStampWidth = 44.dp

/** A photo in the timeline — the live log's size, for the same reason. */
private val TimelinePhotoSize = 44.dp

/** Decode target for a timeline thumbnail, in pixels — a hair over the largest it draws. */
private const val TimelinePhotoPx = 176

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun OutingDetailWizardPreview() {
  BirdSpotterTheme {
    OutingDetailLoaded(
        entry = PreviewCatalog.journalEntries[0],
        timeline = emptyList(),
        playback = null,
        mediaFileStore = null,
        contentPadding = PaddingValues(0.dp),
        onOpenBird = {},
        onOpenInMaps = {},
        onTogglePlayback = {},
        onScrub = {},
        onSeek = {},
        onSeekRow = {},
        onDelete = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OutingDetailWizardDarkPreview() {
  BirdSpotterTheme(darkTheme = true) {
    OutingDetailLoaded(
        entry = PreviewCatalog.journalEntries[0],
        timeline = emptyList(),
        playback = null,
        mediaFileStore = null,
        contentPadding = PaddingValues(0.dp),
        onOpenBird = {},
        onOpenInMaps = {},
        onTogglePlayback = {},
        onScrub = {},
        onSeek = {},
        onSeekRow = {},
        onDelete = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun OutingDetailMultiBirdPreview() {
  BirdSpotterTheme {
    OutingDetailLoaded(
        entry = PreviewCatalog.journalEntries[1],
        timeline = emptyList(),
        playback = null,
        mediaFileStore = null,
        contentPadding = PaddingValues(0.dp),
        onOpenBird = {},
        onOpenInMaps = {},
        onTogglePlayback = {},
        onScrub = {},
        onSeek = {},
        onSeekRow = {},
        onDelete = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun OutingDetailBirdlessPreview() {
  BirdSpotterTheme {
    OutingDetailLoaded(
        entry = PreviewCatalog.birdlessJournalEntry,
        timeline = emptyList(),
        playback = null,
        mediaFileStore = null,
        contentPadding = PaddingValues(0.dp),
        onOpenBird = {},
        onOpenInMaps = {},
        onTogglePlayback = {},
        onScrub = {},
        onSeek = {},
        onSeekRow = {},
        onDelete = {},
    )
  }
}
