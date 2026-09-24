/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import com.meta.pixelandtexel.birdspotter.data.journal.OutingMedia
import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.domain.PhotoZoom
import com.meta.pixelandtexel.birdspotter.features.journal.OutingPhoto
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * Where a full-screen photograph gets its pixels: a picture the session is still holding, or one
 * the journal has on disk.
 *
 * **Two cases rather than one, because the two are genuinely different pictures.** A session's
 * capture is a live bitmap at capture resolution that has not been encoded yet — asking it for a
 * file path would mean writing one, and the whole point of encoding at Save is that a discarded
 * session never pays for compression. A journal photo is bytes on disk, decoded on demand at
 * whatever size is being drawn. The lightbox does not care which it is given; the call sites cannot
 * honestly offer the other.
 */
sealed interface PhotoLightboxSource {

  /** A picture already in memory — a capture on a running or just-stopped session. */
  data class Image(val bitmap: ImageBitmap) : PhotoLightboxSource

  /** A picture in the captured-media directory, decoded when the lightbox opens. */
  data class Media(val media: OutingMedia) : PhotoLightboxSource
}

/**
 * The photograph a lightbox is open on, if one is.
 *
 * The [id] is doing two jobs at once, and they have to be the same value: it identifies the
 * presentation, and it is what the shared element matches the thumbnail against. A screen stamps it
 * with whatever identifies the row the picture came from — a session event's stamp, a media row's
 * id — and puts the same value on that row's [PhotoLightboxScope.photoTransitionSource].
 */
data class OpenPhoto(
    val id: String,
    val source: PhotoLightboxSource,
    val caption: String? = null,
)

/**
 * A screen that can open one of its own photographs full screen.
 *
 * **The transition is the reason this exists rather than a plain overlay.** Growing a thumbnail
 * into the picture and dropping it back needs the two to be one shared element, which needs a
 * layout wrapping both and a content swap between them — plumbing that would otherwise be copied
 * into every screen that shows a photo, three times, slightly differently. Here the caller writes
 * its list as it always did and marks its thumbnails with
 * [PhotoLightboxScope.photoTransitionSource].
 *
 * The swap also settles a question an overlay leaves open: while the picture is up, the list is
 * gone rather than sitting live underneath it. A row cannot be tapped through a photograph, and two
 * composables cannot claim the same shared key at once.
 *
 * Hoist any list state the content owns *outside* this call — the content is unmounted while a
 * photograph is open, and a scroll position remembered inside it would not survive the trip.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoLightboxHost(
    openPhoto: OpenPhoto?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    mediaFileStore: MediaFileStore? = null,
    content: @Composable PhotoLightboxScope.() -> Unit,
) {
  SharedTransitionLayout(modifier) {
    AnimatedContent(
        targetState = openPhoto,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "photoLightbox",
    ) { photo ->
      val scope = PhotoLightboxScope(this@SharedTransitionLayout, this@AnimatedContent)
      if (photo == null) {
        scope.content()
      } else {
        PhotoLightbox(
            source = photo.source,
            onClose = onClose,
            modifier =
                with(this@SharedTransitionLayout) {
                  Modifier.sharedElement(
                      rememberSharedContentState(photo.id),
                      this@AnimatedContent,
                  )
                },
            caption = photo.caption,
            mediaFileStore = mediaFileStore,
        )
      }
    }
  }
}

/**
 * What a [PhotoLightboxHost]'s content can do that an ordinary composable cannot: name a thumbnail
 * as the place a photograph grows out of and falls back into.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class PhotoLightboxScope(
    private val sharedScope: SharedTransitionScope,
    private val animatedScope: AnimatedVisibilityScope,
) {

  /**
   * Marks this thumbnail as the origin of the photograph identified by [id] — the same [id] the
   * screen puts on the [OpenPhoto] it opens.
   *
   * A row that has scrolled away or been removed while the picture is up simply is not there to
   * return to, and the shared element fades instead of flying to a stale position. That is the
   * right answer and it needs no handling: the live session's log scrolls itself to the newest row
   * on every arrival, so a photograph opened from it is routinely returning to somewhere other than
   * where it left.
   */
  @Composable
  fun Modifier.photoTransitionSource(id: String): Modifier =
      with(sharedScope) {
        this@photoTransitionSource.sharedElement(
            rememberSharedContentState(id),
            animatedScope,
        )
      }
}

/**
 * One photograph, full screen, magnifiable — what a tapped thumbnail opens into.
 *
 * **The two drags never negotiate, and that is the design.** Swipe-to-dismiss and pan want the same
 * gesture, so exactly one of them is live at a time: at rest the drag dismisses, and the moment the
 * picture is magnified it pans. [PhotoZoom.isZoomed] is the switch, and because it is one reading
 * of one value there is no state in which both are half-enabled — which is the failure mode every
 * hand-rolled viewer of this kind eventually grows.
 *
 * The close control is not a fallback for that. A magnified picture has no dismissing drag left, so
 * without a button the only way out would be to zoom back out first, and a viewer that traps
 * somebody who pinched too far is a viewer with a bug in it.
 *
 * Raised inside a `SharedTransitionLayout` by its caller, which is what makes the thumbnail grow
 * into the picture and the picture fall back into the thumbnail; a row that has scrolled away under
 * it — the live session's log does, on every arrival — simply has no bounds to return to and the
 * shared element fades instead.
 */
@Composable
fun PhotoLightbox(
    source: PhotoLightboxSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * What the app made of this picture, printed under it — a bird's name, or nothing where the app
     * never named one.
     */
    caption: String? = null,
    mediaFileStore: MediaFileStore? = null,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors

  /** The magnification, and the whole of the state this screen holds. See [PhotoZoom]. */
  var zoom by remember { mutableStateOf(PhotoZoom.IDLE) }

  /**
   * The viewport, measured. Nothing can be clamped until this has landed, which is why
   * [PhotoZoom.settled] is required to survive a zero.
   */
  var viewport by remember { mutableStateOf(Size.Zero) }

  /** How far a drag has carried the picture while it is at rest — what dismisses it. */
  var dismissTravel by remember { mutableStateOf(0f) }

  val aspect = source.aspectRatio()
  val fitted = fittedSize(viewport, aspect)

  // The picture eases to a double-tapped magnification rather than snapping to it, and tracks a
  // pinch directly — `animateFloatAsState` on a value the gesture is already writing every frame
  // settles within a frame of it, so one path covers both.
  val scale by animateFloatAsState(zoom.scale, label = "photoScale")

  Box(
      modifier =
          modifier
              .fillMaxSize()
              // The cabinet's own black rather than a system scrim: this opens over the session's
              // dark cover as often as over the journal's paper, and a photograph is looked at
              // against one ground or the other, never against whichever it happened to come from.
              .background(colors.ink)
              .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) },
  ) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                  detectTapGestures(
                      onDoubleTap = { zoom = zoom.toggledZoom(viewport, fitted) },
                  )
                }
                .pointerInput(fitted) {
                  detectTransformGestures { _, panChange, zoomChange, _ ->
                    val magnified = zoom.scaledBy(zoomChange, viewport, fitted)
                    zoom =
                        if (magnified.isZoomed) {
                          // Magnified: the drag belongs to the picture.
                          dismissTravel = 0f
                          magnified.pannedBy(panChange.x, panChange.y, viewport, fitted)
                        } else {
                          // At rest: the drag belongs to the dismissal, and a far enough one
                          // ends the viewer rather than moving anything.
                          dismissTravel += panChange.y
                          if (kotlin.math.abs(dismissTravel) > DismissTravelPx) onClose()
                          magnified
                        }
                  }
                },
        contentAlignment = Alignment.Center,
    ) {
      Box(
          modifier =
              Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = zoom.offsetX
                translationY = zoom.offsetY
              },
          contentAlignment = Alignment.Center,
      ) {
        when (source) {
          is PhotoLightboxSource.Image ->
              Image(
                  bitmap = source.bitmap,
                  contentDescription = caption,
                  contentScale = ContentScale.Fit,
                  modifier = Modifier.fillMaxSize(),
              )

          is PhotoLightboxSource.Media ->
              if (mediaFileStore != null) {
                // A decode target in the screen's own pixels rather than the
                // thumbnail's: this is the one place the whole photograph is being
                // looked at, and the row's 176 blown up to fill a phone is a row's
                // thumbnail with its pixels showing. Asked for at the ceiling the
                // magnification can reach, so pinching in finds detail rather than
                // finding the decode.
                OutingPhoto(
                    media = source.media,
                    mediaFileStore = mediaFileStore,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = caption,
                    maxWidthPx = LightboxDecodeWidthPx,
                    fillsFrame = false,
                )
              }
        }
      }
    }

    // The close mark and the caption — the only two things laid over the picture. Both ride
    // the safe area rather than the picture: a photograph is fitted, so what is behind them at
    // any moment is the black above and below it as often as the image itself, and chrome that
    // tracked the picture would move every time the magnification did.
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(space.gutter),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
      ) {
        IconButton(
            onClick = onClose,
            modifier =
                Modifier.background(
                    colors.ink.copy(alpha = LightboxChromeAlpha),
                    CircleShape,
                ),
        ) {
          Icon(
              painter = glyph(BirdSpotterTheme.glyphs.close),
              contentDescription = "Close this photo",
              tint = colors.textPrimary,
          )
        }
      }

      if (!caption.isNullOrEmpty()) {
        PlateLabel(
            text = caption,
            color = colors.gilt,
            modifier =
                Modifier.background(
                        colors.ink.copy(alpha = LightboxChromeAlpha),
                        RoundedCornerShape(percent = 50),
                    )
                    .padding(horizontal = space.related, vertical = space.snug),
        )
      }
    }
  }
}

/**
 * The picture's width over its height, or `null` for a journal photo saved before the dimensions
 * were recorded — in which case the fit falls back to the viewport, and the pan is merely more
 * generous than it needed to be.
 */
private fun PhotoLightboxSource.aspectRatio(): Float? =
    when (this) {
      is PhotoLightboxSource.Image ->
          if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else null

      is PhotoLightboxSource.Media -> {
        val width = media.width
        val height = media.height
        if (width != null && height != null && height > 0) {
          width.toFloat() / height.toFloat()
        } else {
          null
        }
      }
    }

/**
 * The photograph as it sits at rest: the whole picture fitted inside the viewport, letterboxed on
 * whichever axis has room to spare.
 *
 * **This, and not the viewport, is what the pan clamps against.** The offset limit is the overhang
 * past the screen, and a letterboxed picture at 2× may genuinely have overhang on one axis and none
 * on the other — clamping both against the viewport would let a photograph be dragged up and down
 * inside its own black bars.
 */
private fun fittedSize(viewport: Size, aspect: Float?): Size {
  if (aspect == null || viewport.width <= 0f || viewport.height <= 0f) return viewport
  val viewportAspect = viewport.width / viewport.height
  return if (aspect > viewportAspect) {
    Size(viewport.width, viewport.width / aspect)
  } else {
    Size(viewport.height * aspect, viewport.height)
  }
}

/**
 * How far an unmagnified picture has to be dragged before the viewer closes. Far enough that a
 * pinch which drifted does not dismiss, short enough that the flick everybody tries first works.
 */
private const val DismissTravelPx = 220f

/**
 * How wide a journal photo is decoded for the lightbox. Generous, because this is the one screen
 * where the whole photograph is the subject and the magnification goes to `MAX_SCALE`.
 */
private const val LightboxDecodeWidthPx = 2048

/**
 * How dark the chrome laid over a picture sits. The same depth the session's controls use over the
 * strip and the viewfinder — one value for everything this app floats over an image.
 */
private const val LightboxChromeAlpha = 0.6f
