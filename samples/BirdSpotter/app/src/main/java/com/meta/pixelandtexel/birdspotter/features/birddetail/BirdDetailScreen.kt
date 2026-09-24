/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.birddetail

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMedia
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesMediaType
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.data.catalog.SystemAudioClipPlayer
import com.meta.pixelandtexel.birdspotter.ui.components.CardSurface
import com.meta.pixelandtexel.birdspotter.ui.components.CatalogPhoto
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.components.Sonogram
import com.meta.pixelandtexel.birdspotter.ui.previews.PreviewCatalog
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One species' page in the field guide — the plates, the name, and what the guide says.
 *
 * Reached by `Route.BirdDetail`, which every tab graph registers, so the same page opens from
 * Explore's card, from an Identify result, and from a Journal entry — each landing on its own tab's
 * back stack.
 *
 * Spacing comes from `BirdSpotterTheme.space` throughout.
 */
@Composable
fun BirdDetailScreen(
    speciesId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val player =
      remember(speciesId) {
        SystemAudioClipPlayer(application.container.catalogAssetStore)
      }
  val viewModel: BirdDetailViewModel = viewModel(
      key = speciesId,
      factory =
          BirdDetailViewModel.factory(
              speciesId = speciesId,
              birdCatalog = application.container.birdCatalogRepository,
              player = player,
          ),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // Collected without reading `.value` here, so this composable does not recompose on every
  // playhead tick; the value is read inside the vocalization card via the lambda below,
  // which is where the recomposition is wanted.
  val playback = viewModel.playback.collectAsStateWithLifecycle()

  BirdDetailScreen(
      uiState = uiState,
      playback = { playback.value },
      onTogglePlayback = viewModel::togglePlayback,
      onSeek = viewModel::seek,
      onBack = onBack,
      modifier = modifier,
  )
}

/**
 * The stateless half, so Previews and UI tests can drive every state without a database.
 *
 * [playback] is a lambda, not a value: it changes ~30 times a second while a clip plays, and
 * passing it as a plain parameter would recompose this whole screen — plates and all — on every
 * tick. Read as a lambda, the subscription lands in the one composable that invokes it (the
 * vocalization card), so only that card ticks.
 */
@Composable
fun BirdDetailScreen(
    uiState: BirdDetailUiState,
    playback: () -> Playback,
    onTogglePlayback: () -> Unit,
    onSeek: (Double) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val density = LocalDensity.current
  val barProgress by
      remember(density) {
        derivedStateOf {
          val travelled = with(density) { scrollState.value.toDp() }
          (travelled / BarFadeDistance).coerceIn(0f, 1f)
        }
      }

  Box(
      modifier.fillMaxSize().background(BirdSpotterTheme.colors.paper),
  ) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState),
    ) {
      // Inside the scroll, so the plate travels *under* the clock rather than
      // stopping short of it. The bar's own background is what covers it on the way
      // up — which is the whole effect.
      Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

      when (uiState) {
        is BirdDetailUiState.Loading -> BirdPagePlaceholder()
        is BirdDetailUiState.Ready ->
            BirdPage(
                uiState.bird,
                playback = playback,
                onTogglePlayback = onTogglePlayback,
                onSeek = onSeek,
            )
        is BirdDetailUiState.NotFound -> NotInTheGuide()
      }
    }

    CollapsingBar(
        title = (uiState as? BirdDetailUiState.Ready)?.bird?.species?.commonName.orEmpty(),
        progress = barProgress,
        onBack = onBack,
        modifier = Modifier.align(Alignment.TopCenter),
    )
  }
}

// ── The bar ────────────────────────────────────────────────────────────────

/** A standard bar row, so the title lands where a Material title would. */
private val BarHeight = 56.dp
private val ControlSize = 48.dp
private val DiscSize = 36.dp

/**
 * The bar reaches full strength well before the plate is gone, the way a scrolled photo header
 * settles.
 */
private val BarFadeDistance = 120.dp

/**
 * Transparent over the plate, solid once you have scrolled past it.
 *
 * Hand-rolled rather than a `TopAppBar` with `enterAlwaysScrollBehavior`: Material's collapsing
 * bars move the bar, where this one keeps it still and changes how solid it is.
 */
@Composable
private fun CollapsingBar(
    title: String,
    progress: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier
          .fillMaxWidth()
          // Carries the paper up behind the clock, so the bar and the status bar become
          // one surface rather than two.
          .background(BirdSpotterTheme.colors.paper.copy(alpha = progress)),
  ) {
    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

    Row(
        Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = BirdSpotterTheme.space.snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      BackControl(disc = 1f - progress, onClick = onBack)

      Text(
          text = title,
          style = BirdSpotterTheme.type.headline,
          color = BirdSpotterTheme.colors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f).alpha(progress),
      )

      // Balances the back control so the title sits on the screen's centre line.
      Spacer(Modifier.width(ControlSize))
    }

    HairlineRule(Modifier.alpha(progress))
  }
}

/** A disc on the plate, gone by the time the bar is solid behind it. */
@Composable
private fun BackControl(disc: Float, onClick: () -> Unit) {
  IconButton(onClick = onClick, modifier = Modifier.size(ControlSize)) {
    Box(
        Modifier.size(DiscSize)
            .background(
                color = BirdSpotterTheme.colors.paper.copy(alpha = 0.94f * disc),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.back),
          contentDescription = "Back",
          tint = BirdSpotterTheme.colors.textPrimary,
      )
    }
  }
}

// ── The page ───────────────────────────────────────────────────────────────

@Composable
private fun BirdPage(
    bird: SpeciesWithMedia,
    playback: () -> Playback,
    onTogglePlayback: () -> Unit,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space

  // No arrangement: the carousel and its caption are flush by design, and everything
  // below is one block with its own rhythm.
  Column(modifier) {
    if (bird.photos.isNotEmpty()) {
      val pager = rememberPagerState(pageCount = { bird.photos.size })

      PlateCarousel(
          photos = bird.photos,
          commonName = bird.species.commonName,
          state = pager,
      )
      PlateCaption(
          credit = bird.photos.getOrNull(pager.currentPage)?.credit,
          plate = pager.currentPage,
          count = bird.photos.size,
      )
    }

    Column(
        modifier =
            Modifier.padding(horizontal = space.gutter)
                .padding(top = space.section, bottom = space.page),
        verticalArrangement = Arrangement.spacedBy(space.section),
    ) {
      Nameplate(bird)

      GuideSection(title = "About") {
        Prose(bird.species.aboutText)
      }

      GuideSection(title = "Habitat") {
        Prose(bird.species.habitatText)
      }

      bird.referenceAudio?.let { audio ->
        GuideSection(title = audio.type.sectionTitle) {
          VocalizationCard(
              media = audio,
              sonogram = bird.sonogram,
              playback = playback,
              onTogglePlayback = onTogglePlayback,
              onSeek = onSeek,
          )
        }
      }
    }
  }
}

/**
 * Family in plate caps, then the name and the binomial — the head of a guide entry.
 *
 * `Spacer`s rather than an arrangement, because these two gaps are deliberately different sizes; a
 * uniform column rhythm would flatten the hierarchy.
 */
@Composable
private fun Nameplate(bird: SpeciesWithMedia, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  Column(modifier) {
    PlateLabel(
        text = bird.species.familyName,
        color = BirdSpotterTheme.colors.verdigris,
    )
    Spacer(Modifier.height(space.related))
    Text(
        text = bird.species.commonName,
        style = BirdSpotterTheme.type.display,
        color = BirdSpotterTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(space.tight))
    Text(
        text = bird.species.scientificName,
        style = BirdSpotterTheme.type.scientific,
        color = BirdSpotterTheme.colors.textSecondary,
    )
  }
}

// ── Plates ─────────────────────────────────────────────────────────────────

/**
 * Every bundled photograph, swiped through in place.
 *
 * Full bleed on purpose — the plate is the page's opening, and insetting it to the gutter would
 * make it a card like Explore's rather than a plate.
 */
@Composable
private fun PlateCarousel(
    photos: List<SpeciesMedia>,
    commonName: String,
    state: PagerState,
    modifier: Modifier = Modifier,
) {
  HorizontalPager(
      state = state,
      modifier = modifier.fillMaxWidth().aspectRatio(4f / 3f),
  ) { page ->
    CatalogPhoto(
        media = photos[page],
        contentDescription = "$commonName, plate ${page + 1}",
        modifier = Modifier.fillMaxSize(),
    )
  }
}

/**
 * The strip under the plates: who took this one, and where you are in the set.
 *
 * The credit rides with its photograph rather than collecting in a footer — everything bundled is
 * openly licensed on the condition that the photographer is named, and naming them beside the
 * picture is both better manners and better design. See licenses/ATTRIBUTION.md.
 */
@Composable
private fun PlateCaption(
    credit: String?,
    plate: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Column(modifier) {
    HairlineRule()
    Row(
        Modifier.fillMaxWidth()
            .background(BirdSpotterTheme.colors.paperRaised)
            .padding(horizontal = space.gutter, vertical = space.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = credit.orEmpty(),
          style = BirdSpotterTheme.type.caption,
          color = BirdSpotterTheme.colors.textFaint,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          // Shrink the line to fit before dropping characters from it. Credits run
          // long and it is the tail that goes — which is where the licence is, the
          // one part of the credit we are actually obliged to print. The max is the
          // caption's own 12sp, so a credit that fits is set at full size.
          autoSize =
              TextAutoSize.StepBased(
                  minFontSize = 10.sp,
                  maxFontSize = 12.sp,
              ),
          modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(space.separate))
      Text(
          text = "${plate + 1} of $count",
          style = BirdSpotterTheme.type.data,
          color = BirdSpotterTheme.colors.textSecondary,
          modifier =
              Modifier.semantics {
                contentDescription = "Plate ${plate + 1} of $count"
              },
      )
    }
    HairlineRule()
  }
}

// ── Sections ───────────────────────────────────────────────────────────────

/** A plate-capped heading over its content, at the page's one section rhythm. */
@Composable
private fun GuideSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.related),
  ) {
    PlateLabel(text = title, color = BirdSpotterTheme.colors.gilt)
    content()
  }
}

/** Running guide text. */
@Composable
private fun Prose(text: String, modifier: Modifier = Modifier) {
  Text(
      text = text,
      style = BirdSpotterTheme.type.body,
      color = BirdSpotterTheme.colors.textSecondary,
      modifier = modifier.fillMaxWidth(),
  )
}

/**
 * The guide's entry for this bird's voice, now a working player: play or pause the clip, a sonogram
 * whose playhead tracks it, the sex and stage of the bird recorded, and who recorded it.
 *
 * Invokes [playback] here — the one composable on the page that does — so the ~30 Hz playhead ticks
 * recompose this card and not the plates and prose above it.
 */
@Composable
private fun VocalizationCard(
    media: SpeciesMedia,
    sonogram: SpeciesMedia?,
    playback: () -> Playback,
    onTogglePlayback: () -> Unit,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val state = playback()
  CardSurface(modifier = modifier) {
    Column(Modifier.padding(space.cardInset)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        PlayButton(isPlaying = state.isPlaying, onClick = onTogglePlayback)
        Spacer(Modifier.width(space.related))
        // The section eyebrow already names this "Song"/"Call"; the card leads instead
        // with who is singing — the sex and stage of the recorded bird.
        Column {
          Text(
              text = media.voiceDescription ?: "Reference recording",
              style = BirdSpotterTheme.type.headline,
              color = BirdSpotterTheme.colors.textPrimary,
          )
          media.formattedDuration?.let { duration ->
            Spacer(Modifier.height(space.tight))
            Text(
                text = duration,
                style = BirdSpotterTheme.type.data,
                color = BirdSpotterTheme.colors.textSecondary,
            )
          }
        }
      }

      sonogram?.let {
        Spacer(Modifier.height(space.separate))
        Sonogram(media = it, progress = state.progress, onScrub = onSeek)
      }

      media.credit?.let { credit ->
        Spacer(Modifier.height(space.separate))
        HairlineRule()
        Spacer(Modifier.height(space.separate))
        Text(
            text = "Recording: $credit",
            style = BirdSpotterTheme.type.caption,
            color = BirdSpotterTheme.colors.textFaint,
        )
      }
    }
  }
}

/** A standard touch target, with the glyph inside it — control sizes, not the spacing scale. */
private val PlayButtonSize = 44.dp
private val PlayGlyphSize = 20.dp

/**
 * The transport control. A filled verdigris disc so it reads as the one thing to tap on the card;
 * the glyph swaps between play and pause without changing weight.
 */
@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
  Box(
      Modifier.size(PlayButtonSize)
          .clip(CircleShape)
          .background(BirdSpotterTheme.colors.verdigris)
          .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painter =
            glyph(if (isPlaying) BirdSpotterTheme.glyphs.pause else BirdSpotterTheme.glyphs.play),
        contentDescription = if (isPlaying) "Pause recording" else "Play recording",
        tint = BirdSpotterTheme.colors.paper,
        modifier = Modifier.size(PlayGlyphSize),
    )
  }
}

// ── Other states ───────────────────────────────────────────────────────────

/** The page's silhouette while the query runs, so nothing jumps when the row lands. */
@Composable
private fun BirdPagePlaceholder(modifier: Modifier = Modifier) {
  Column(modifier) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(BirdSpotterTheme.colors.rule),
    )
    Spacer(Modifier.height(260.dp))
  }
}

/**
 * Shown for an id the catalog does not have — a species dropped by a later seed, or an address that
 * was never good. Not an error, so it does not read as one.
 */
@Composable
private fun NotInTheGuide(modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .padding(horizontal = space.gutter)
              // Clears the bar, which is an overlay and reserves no room of its own.
              .padding(top = BarHeight + space.page),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.help),
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = BirdSpotterTheme.colors.textFaint,
    )
    Spacer(Modifier.height(space.separate))
    Text(
        text = "Not in the Guide",
        style = BirdSpotterTheme.type.title,
        color = BirdSpotterTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(space.snug))
    Text(
        text = "This bird isn't part of the bundled field guide.",
        style = BirdSpotterTheme.type.body,
        color = BirdSpotterTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
  }
}

// ── Formatting ─────────────────────────────────────────────────────────────

/**
 * Heads the vocalization section. A song and a call are both audio and both play the same way; the
 * distinction is a field-guide one, so the guide prints it.
 */
internal val SpeciesMediaType.sectionTitle: String
  get() =
      when (this) {
        SpeciesMediaType.PHOTO -> "Photograph"
        SpeciesMediaType.SONG -> "Song"
        SpeciesMediaType.CALL -> "Call"
        // Never heads a section of its own — the sonogram is drawn inside the
        // vocalization section, under the clip it depicts. Titled for completeness.
        SpeciesMediaType.SONOGRAM -> "Sonogram"
      }

/** `m:ss`, or null for the photos that have no duration to print. */
internal val SpeciesMedia.formattedDuration: String?
  get() = durationMs?.let { ms ->
    val seconds = (ms / 1000.0).roundToInt()
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
  }

/**
 * "Male · Adult" from the recording's sex and stage — the field-guide facts about the bird on the
 * clip, which the card shows in place of restating the section's own "Song"/"Call". Either half may
 * be missing (both usually are); null when both are, and the card falls back to a plain "Reference
 * recording".
 *
 * Only the first letter is raised, so a Xeno-canto value like `male, female` reads "Male, female"
 * rather than the word-by-word "Male, Female" a title-case helper would give.
 */
internal val SpeciesMedia.voiceDescription: String?
  get() {
    val parts =
        listOfNotNull(sex, stage)
            .filter { it.isNotEmpty() }
            .map { it.replaceFirstChar(Char::uppercaseChar) }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
  }

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun BirdDetailScreenPreview() {
  BirdSpotterTheme {
    BirdDetailScreen(
        uiState = BirdDetailUiState.Ready(PreviewCatalog.bird),
        playback = { Playback(isPlaying = true, progress = 0.35) },
        onTogglePlayback = {},
        onSeek = {},
        onBack = {},
    )
  }
}

@Preview(showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BirdDetailScreenDarkPreview() {
  BirdSpotterTheme(darkTheme = true) {
    BirdDetailScreen(
        uiState = BirdDetailUiState.Ready(PreviewCatalog.bird),
        playback = { Playback(isPlaying = true, progress = 0.35) },
        onTogglePlayback = {},
        onSeek = {},
        onBack = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun BirdDetailScreenNotFoundPreview() {
  BirdSpotterTheme {
    BirdDetailScreen(
        uiState = BirdDetailUiState.NotFound,
        playback = { Playback() },
        onTogglePlayback = {},
        onSeek = {},
        onBack = {},
    )
  }
}
