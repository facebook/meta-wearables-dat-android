/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.journal.GazeContext
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureError
import com.meta.pixelandtexel.birdspotter.domain.CameraControl
import com.meta.pixelandtexel.birdspotter.domain.CameraViewfinder
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import com.meta.pixelandtexel.birdspotter.domain.PreviewFrame
import com.meta.pixelandtexel.birdspotter.domain.RealtimeSession
import com.meta.pixelandtexel.birdspotter.domain.SessionEvent
import com.meta.pixelandtexel.birdspotter.domain.SonogramBins
import com.meta.pixelandtexel.birdspotter.domain.SonogramBuffer
import com.meta.pixelandtexel.birdspotter.domain.SonogramColumn
import com.meta.pixelandtexel.birdspotter.domain.SonogramColumnsPerSecond
import com.meta.pixelandtexel.birdspotter.domain.StripReading
import com.meta.pixelandtexel.birdspotter.features.journal.JournalFormatting
import com.meta.pixelandtexel.birdspotter.ui.components.FrostedGround
import com.meta.pixelandtexel.birdspotter.ui.components.LocationDot
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.components.WorkingDots
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * Real-time identification — **a listening session**, presented full screen.
 *
 * The microphone runs from the moment the cover rises to the moment it falls, and everything else
 * lands on a timeline at the second it happened: a photo the watcher took, a phrase they said, a
 * bird the app thinks it heard. The camera is a **mode inside the session**, not the way into it —
 * opening it does not stop the session listening, and the shutter drops its photo onto the timeline
 * and comes straight back.
 *
 * Why not camera-first: two of the three identification paths in the spec need no camera at all,
 * and a viewfinder has nowhere to put *when* something happened. The reasoning, and the format the
 * strip is drawn from, are.
 *
 * The cover comes up from the bottom over the whole shell, bottom bar included, which is why
 * `BirdSpotterApp` raises it over its Scaffold rather than routing to it. The stop sits at the foot
 * of the strip — on the instrument it stops — and ends the recording onto the review; the review's
 * Save and Discard are what finally call [onClose]. System back follows the mode it is in — camera,
 * session, review — see the handler below.
 *
 * The whole cover runs in the **dark palette**, whatever the phone is set to — a sonogram is a
 * light-on-dark instrument in both, and forcing the theme here lets every control below go on
 * reading `textPrimary` and `gilt` as usual.
 */
@Composable
fun RealtimeScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val container = (LocalContext.current.applicationContext as BirdSpotterApplication).container
  // The session, which is the app's rather than this cover's: built once in the composition
  // root, so a run started by a voice launch before the cover rose is the run the cover shows,
  // and one still going when the cover falls goes on going.
  val viewModel = container.realtimeViewModel
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
  val frame by viewModel.frame.collectAsStateWithLifecycle()
  val viewfinder by viewModel.viewfinder.collectAsStateWithLifecycle()

  // The cover rising is a run starting — unless one is already going, in which case this is
  // a no-op and the cover simply shows it. The run is the view model's own, so neither the
  // stop nor the cover falling is handled here: the stop cancels it, and backgrounding leaves
  // it running — a phone in a pocket is the glasses' ordinary case, and the foreground service
  // MainActivity starts is what lets the microphone stay open there.
  LaunchedEffect(viewModel) { viewModel.start() }

  // The camera's own life, inside the session's. Keyed on the flag, so closing the mode cancels
  // the stream and hands the camera back without touching the microphone.
  LaunchedEffect(viewModel, uiState.isCameraOpen) {
    if (uiState.isCameraOpen) viewModel.observeCamera()
  }

  // The cover falls once the journal has the outing — the "In your journal" beat on the
  // button is all the confirmation a successful save gets.
  LaunchedEffect(uiState.review?.savedOutingId) {
    if (uiState.review?.savedOutingId != null) onClose()
  }

  // How the strip is drawn, and where that survives. Seeded from the store on the way in and
  // written back on the tap: the choice is about how somebody likes to read an instrument, not
  // about this session, so it outlives the cover.
  //
  // **State as well as store, rather than reading the store every recomposition.**
  // SharedPreferences has no way to tell Compose something changed, so a composable that only
  // read it would go on drawing the old reading until something else invalidated it.
  val sessionSettings = container.sessionSettingsStore
  var stripReading by remember { mutableStateOf(sessionSettings.stripReading) }

  RealtimeScreen(
      uiState = uiState,
      sonogram = viewModel.sonogram,
      elapsed = elapsed,
      frame = frame,
      viewfinder = viewfinder,
      onStop = viewModel::stopSession,
      onClose = onClose,
      onOpenCamera = viewModel::openCamera,
      onCloseCamera = viewModel::closeCamera,
      onCapture = viewModel::capturePhoto,
      onToggleSource = viewModel::toggleSource,
      onShowCard = viewModel::showCard,
      onDismissCard = viewModel::dismissCard,
      onToggleFlash = viewModel::toggleFlash,
      // The gesture reports a step and the view model holds the total — see [zoomBy].
      onZoomBy = viewModel::zoomBy,
      onToggleBird = viewModel::toggleBirdKept,
      onNotesChange = viewModel::setReviewNotes,
      onSave = viewModel::saveOuting,
      stripReading = stripReading,
      onStripReadingChange = { reading ->
        stripReading = reading
        sessionSettings.stripReading = reading
      },
      modifier = modifier,
  )
}

/** The stateless half, so Previews and UI tests can drive every state without a microphone. */
@Composable
fun RealtimeScreen(
    uiState: RealtimeUiState,
    sonogram: SonogramBuffer,
    elapsed: Double,
    frame: PreviewFrame?,
    viewfinder: CameraViewfinder?,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onOpenCamera: () -> Unit,
    onCloseCamera: () -> Unit,
    onCapture: () -> Unit,
    onToggleSource: () -> Unit,
    onShowCard: (String) -> Unit,
    onDismissCard: () -> Unit,
    onToggleFlash: () -> Unit,
    onZoomBy: (Float) -> Unit,
    onToggleBird: (Double) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    stripReading: StripReading,
    onStripReadingChange: (StripReading) -> Unit,
    modifier: Modifier = Modifier,
) {
  // Back answers for the mode you are in: the camera closes, a running session stops onto the
  // review, and from the review — or a session that failed to open — back leaves without
  // saving, which is exactly what Discard is; nothing was written either way.
  BackHandler {
    when {
      uiState.isCameraOpen -> onCloseCamera()
      uiState.isReviewing -> onClose()
      uiState.status == SessionStatus.FAILED -> onClose()
      else -> onStop()
    }
  }

  BirdSpotterTheme(darkTheme = true) {
    // The phone's own copy of the glasses card, for a log tap that had nowhere else to put
    // it — see [BirdCardDialog]. A window of its own rather than a layer in the box below, so
    // it sits over the camera panel and the review alike without either having to know.
    uiState.cardOnPhone?.let { bird ->
      BirdCardDialog(bird = bird, onDismiss = onDismissCard)
    }

    // Where the camera control sits, in the root's own coordinates. The panel's closed
    // geometry — it starts and ends its life as exactly this rectangle, which is what makes
    // the two one object rather than one leaving as another arrives.
    var controlBounds by remember { mutableStateOf(Rect.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier =
            modifier.fillMaxSize().background(BirdSpotterTheme.colors.paper).onGloballyPositioned {
              rootOrigin = it.positionInWindow()
            },
    ) {
      val review = uiState.review
      val reviewRisePx = with(LocalDensity.current) { ReviewRise.roundToPx() }
      // **The stop is two beats, not a cut.** The instrument settles, then what it left
      // behind comes up — the session dissolves where it stands, and the review rises
      // the last inch into its place as that finishes.
      //
      // Both halves at once is a cross-fade of two full screens, which reads as a
      // glitch; straight replacement is what made the stop feel like the app had
      // skipped a frame. The two curves are asymmetric for the reason [CameraDismiss]
      // is: the session is *ending* and has nothing to settle into, while the review is
      // arriving and should land.
      //
      // The session only fades — a recording that slid off the screen would be claiming
      // to have gone somewhere.
      AnimatedContent(
          targetState = review,
          transitionSpec = {
            (fadeIn(animationSpec = ReviewArrival) +
                slideInVertically(animationSpec = ReviewArrivalOffset) {
                  reviewRisePx
                }) togetherWith fadeOut(animationSpec = ReviewDeparture)
          },
          label = "session-to-review",
      ) { target ->
        if (target != null) {
          // The confirmation, in the session's place: same cover, second screen of
          // the flow. The strip and the camera are gone because the session is —
          // what is being looked at now is what it left behind.
          SessionReviewScreen(
              session = uiState.session,
              review = target,
              onToggleBird = onToggleBird,
              onNotesChange = onNotesChange,
              onSave = onSave,
              onDiscard = onClose,
          )
        } else {
          SessionView(
              uiState = uiState,
              sonogram = sonogram,
              elapsed = elapsed,
              onStop = onStop,
              onClose = onClose,
              onOpenCamera = onOpenCamera,
              onCapture = onCapture,
              onToggleSource = onToggleSource,
              onShowCard = onShowCard,
              stripReading = stripReading,
              onStripReadingChange = onStripReadingChange,
              onControlBounds = { controlBounds = it.translate(-rootOrigin) },
          )
        }
      }

      // Outside the swap rather than inside it, and it costs nothing: the stop closes the
      // camera before it sets the review, so by the time this goes there is nothing on
      // screen to see leave.
      if (review == null) {
        // Over the session rather than instead of it: the strip keeps filling behind
        // this, and closing the camera reveals the seconds the session went on recording.
        CameraMode(
            isOpen = uiState.isCameraOpen,
            frame = frame,
            viewfinder = viewfinder,
            closedBounds = controlBounds,
            controls = uiState.cameraControls,
            isFlashOn = uiState.isFlashOn,
            isCapturing = uiState.isCapturing,
            gazeBand = uiState.elevationBand,
            onCancel = onCloseCamera,
            onCapture = onCapture,
            onToggleFlash = onToggleFlash,
            onZoomBy = onZoomBy,
        )
      }
    }
  }
}

/**
 * The session itself: its header, its timeline, and the one control that opens the camera.
 *
 * The shutter is **over** the timeline rather than below it, and the log runs the full height of
 * the screen underneath it — see [SessionLog]. A control that took layout space would cut the log
 * off a clean inch above the bottom of the phone, which is a screen's worth of rows spent saying
 * "the list ends here" on a list that does not end.
 */
@Composable
private fun SessionView(
    uiState: RealtimeUiState,
    sonogram: SonogramBuffer,
    elapsed: Double,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onOpenCamera: () -> Unit,
    onCapture: () -> Unit,
    onToggleSource: () -> Unit,
    onShowCard: (String) -> Unit,
    stripReading: StripReading,
    onStripReadingChange: (StripReading) -> Unit,
    onControlBounds: (Rect) -> Unit,
) {
  Box(Modifier.fillMaxSize()) {
    SessionBody(
        uiState = uiState,
        sonogram = sonogram,
        elapsed = elapsed,
        onStop = onStop,
        onClose = onClose,
        onToggleSource = onToggleSource,
        onShowCard = onShowCard,
        stripReading = stripReading,
        onStripReadingChange = onStripReadingChange,
    )

    SessionControls(
        uiState = uiState,
        onOpenCamera = onOpenCamera,
        onCapture = onCapture,
        onControlBounds = onControlBounds,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

@Composable
private fun SessionBody(
    uiState: RealtimeUiState,
    sonogram: SonogramBuffer,
    elapsed: Double,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onToggleSource: () -> Unit,
    onShowCard: (String) -> Unit,
    stripReading: StripReading,
    onStripReadingChange: (StripReading) -> Unit,
) {
  Column(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
  ) {
    SessionHeader(
        uiState = uiState,
        onClose = onClose,
        onToggleSource = onToggleSource,
    )

    // **The Director says nothing here, deliberately.** A caption naming the armed preset —
    // or saying none was — used to sit under the header, and it is the one line on this
    // screen that is about the demo rather than about the birds. What is playing is read
    // where it is chosen, on the Demo Director page.

    // One line about the source, under the pill that controls it — a missing camera
    // grant, a crossing that failed. Rare, quiet, and cleared the next time the toggle
    // is asked.
    uiState.sourceNotice?.let { notice ->
      Text(
          text = notice,
          style = MaterialTheme.typography.bodySmall,
          color = BirdSpotterTheme.colors.textSecondary,
          textAlign = TextAlign.End,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(horizontal = BirdSpotterTheme.space.gutter)
                  .padding(top = BirdSpotterTheme.space.snug),
      )
    }

    Box(
        modifier =
            Modifier.weight(1f)
                .fillMaxWidth()
                // The air the bearing row used to stand in. It went to the foot of the screen — see
                // [SessionControls] — and the instrument still wants clearing from the chrome above
                // it, so the gap stays and only the row that was in it left.
                .padding(top = BirdSpotterTheme.space.section),
        contentAlignment = Alignment.Center,
    ) {
      when (uiState.status) {
        SessionStatus.FAILED -> FailureNotice(uiState.failureMessage)
        // No gutter: the strip is the screen's one deliberate full-bleed element, so its
        // right edge and the right edge of the phone are the same edge, and that edge is
        // *now*.
        //
        // It sits directly under the header rather than centred in what is left: the strip
        // is the top of the timeline and the log runs down from it, so the session reads top
        // to bottom in one column.
        else ->
            SessionTimeline(
                sonogram = sonogram,
                elapsed = elapsed,
                session = uiState.session,
                emptyLabel = uiState.emptyLogLabel,
                footroom = controlsHeight,
                onStop = onStop,
                reading = stripReading,
                onReadingChange = onStripReadingChange,
                onShowCard = onShowCard,
                modifier = Modifier.fillMaxSize(),
            )
      }
    }
  }
}

/**
 * Whose ears these are — **and nothing else**.
 *
 * **The stop used to sit opposite the source control here, and it went to the strip.** Two
 * unrelated controls sharing a bar made a header that had to be read left to right before either
 * could be used, and pinned the one that ends the session as far as a thumb can get from the
 * session itself. With the stop on the instrument it stops — see [SessionTimeline] — the bar has
 * exactly one thing on it, and a bar with one thing on it puts that thing in the middle.
 *
 * **No clock.** A running total of seconds is a number that changes sixty times a minute and
 * answers nothing a watcher asked — where the time matters is against a particular thing that
 * happened, and that is what the log's stamps are. The session's elapsed seconds are still the
 * column count; nothing now prints them on their own.
 */
@Composable
private fun SessionHeader(
    uiState: RealtimeUiState,
    onClose: () -> Unit,
    onToggleSource: () -> Unit,
) {
  val space = BirdSpotterTheme.space
  Box(
      modifier =
          Modifier.fillMaxWidth()
              // The bar keeps the height the 48-dp control gave it, whether or not that control is
              // there — otherwise a session that fails is a session whose header grows on the way
              // in.
              .heightIn(min = ControlSize)
              .padding(horizontal = space.snug),
      contentAlignment = Alignment.Center,
  ) {
    // The way out of a session that never opened. There is no strip to put a stop on when the
    // microphone was refused, and nothing is running for a stop to be honest about — so this
    // one state, and only this one, keeps a control in the bar, and it is a close mark rather
    // than a stop because putting the screen away is all it does.
    if (uiState.status == SessionStatus.FAILED) {
      IconButton(
          onClick = onClose,
          modifier = Modifier.align(Alignment.CenterStart).size(ControlSize),
      ) {
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.close),
            contentDescription = "Close",
            tint = BirdSpotterTheme.colors.textPrimary,
        )
      }
    }

    // **The switch is measured at the width it wants, not the width the bar has.** Two words,
    // two silhouettes and a set of dots is wider than a narrow phone's bar at a large text
    // size, and a control that is merely *given* the bar's width hands the shortfall to
    // whichever label is laid out last — which is how `LINKING` came to be set as `LINKIN` over
    // a lone `G` the moment the dots appeared beside it. Sized to its content it keeps its
    // shape and leans into the bar's own margins instead, symmetrically, because it is centred.
    SourceControl(
        uiState = uiState,
        onToggleSource = onToggleSource,
        modifier = Modifier.wrapContentWidth(unbounded = true),
    )
  }
}

/**
 * The header's source control, in whichever of its two forms the session has a use for.
 *
 * **A choice should look like one before it is made — and only when there is one.** With no pair
 * registered or none in reach there is exactly one device in the story, so the control is the label
 * it always was: one pill, naming the phone. The moment a pair is reachable it becomes a two-sided
 * chip with both devices on it, because an audience and a presenter can now both see that there is
 * a switch here and which way it is thrown. A single pill that silently became tappable said
 * neither.
 *
 * Which form is [RealtimeUiState.canToggleSource]'s to answer, so the reading and the control stay
 * one decision rather than two that can disagree.
 */
@Composable
private fun SourceControl(
    uiState: RealtimeUiState,
    onToggleSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
  if (uiState.canToggleSource) {
    SourceSwitch(uiState = uiState, onToggleSource = onToggleSource, modifier = modifier)
  } else {
    SourcePill(state = uiState.sourceState, label = uiState.sourceLabel, modifier = modifier)
  }
}

/**
 * The source as a **two-sided chip**: the device in the hand on the left, the glasses on the right,
 * one of them lit.
 *
 * **Both sides are always drawn, and that is the point.** The switch is the one control on this
 * screen a presenter may have to explain from a stage — *watch, I'm moving this to the glasses* —
 * and a control that only shows where it currently is makes them describe an option the audience
 * cannot see. Drawn as a track with a lit side, the throw is legible from the back of a room before
 * anything happens, and afterwards.
 *
 * **The lit side is the ink, not a moving thumb.** A sliding indicator is the usual answer and it
 * is the wrong idiom in this cabinet: everything on this screen is printed — plates, washes, plain
 * inks — and a piece of travelling chrome would be the first thing on it borrowed from a system
 * control. The wash and the ink cross-fade instead, which is the same arithmetic on both platforms
 * and therefore genuinely the same animation.
 *
 * The selection moves on the tap, ahead of the ears: see [RealtimeUiState.isGlassesSelected] for
 * why a switch that waited to move is a switch that gets pressed twice. What stays honest through
 * the crossing is the *ink* — `Linking` runs its dots in the app's gilt, and a paused run drops out
 * of gilt entirely, since a pause is not the session claiming anything.
 *
 * **Each side only takes a tap when it would change something.** Pressing the lit side does nothing
 * at all rather than toggling: with a single pill there was no way to say *put it back on the
 * phone* except by pressing the same thing again, and a control that hangs up a live glasses
 * session on a stray second tap is a control nobody should have to be careful with.
 */
@Composable
private fun SourceSwitch(
    uiState: RealtimeUiState,
    onToggleSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = BirdSpotterTheme.colors
  val onGlasses = uiState.isGlassesSelected

  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          modifier
              .clip(CircleShape)
              // The track the two sides sit in. Without it the unlit side is a word floating beside
              // a
              // chip rather than the other half of one object.
              .background(colors.lacquerRaised)
              .padding(SwitchTrackInset)
              .semantics(mergeDescendants = true) {
                contentDescription =
                    if (onGlasses) {
                      "Session source: ${uiState.glassesSourceLabel}. Tap the phone to bring it back."
                    } else {
                      "Session source: ${uiState.deviceSourceLabel}. Tap the glasses to move it there."
                    }
              },
  ) {
    SourceSegment(
        glyph = BirdSpotterTheme.glyphs.device,
        label = uiState.deviceSourceLabel,
        isSelected = !onGlasses,
        // Unlit but not idle: while the glasses are away this is the device actually
        // recording — see [RealtimeUiState.isDeviceCarrying].
        isCarrying = uiState.isDeviceCarrying,
        isWorking = false,
        isPaused = false,
        // Only live from the glasses side — pressing the lit side is deliberately inert.
        onClick = onToggleSource.takeIf { onGlasses },
    )
    SourceSegment(
        // The frames alone until the pair says it is on a face — see
        // [RealtimeUiState.isGlassesWorn]. A pair that has not reported yet draws as the
        // resting pair, because *not known* and *not worn* look the same from here and the
        // quieter of the two is the honest one to guess.
        glyph =
            if (uiState.isGlassesWorn == true) {
              BirdSpotterTheme.glyphs.glassesWorn
            } else {
              BirdSpotterTheme.glyphs.glasses
            },
        label = uiState.glassesSourceLabel,
        isSelected = onGlasses,
        isCarrying = false,
        isWorking = uiState.sourceState == SourceState.LINKING,
        isPaused = uiState.sourceState == SourceState.GLASSES_PAUSED,
        onClick = onToggleSource.takeIf { !onGlasses },
    )
  }
}

/** One side of the switch: a silhouette, its word, and — while it is reaching — its dots. */
@Composable
private fun SourceSegment(
    @DrawableRes glyph: Int,
    label: String,
    isSelected: Boolean,
    isCarrying: Boolean,
    isWorking: Boolean,
    isPaused: Boolean,
    onClick: (() -> Unit)?,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors

  // Three inks, and the middle one is the whole point of [isCarrying]. Gilt is what the app
  // answers in, so a *live* side wears it. A paused run answers nothing. An unlit side is
  // usually making no claim at all — unless it is the one quietly doing the recording, which is
  // worth more than the faint ink and less than the gilt.
  val ink by animateColorAsState(
      targetValue =
          when {
            isSelected && !isPaused -> colors.gilt
            isSelected || isCarrying -> colors.textSecondary
            else -> colors.textFaint
          },
      animationSpec = tween(SwitchThrowMillis),
      label = "segmentInk",
  )
  val wash by animateColorAsState(
      targetValue =
          when {
            !isSelected -> Color.Transparent
            isPaused -> colors.lacquerHigh
            else -> colors.giltWash
          },
      animationSpec = tween(SwitchThrowMillis),
      label = "segmentWash",
  )

  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(space.snug),
      modifier =
          Modifier.clip(CircleShape)
              .background(wash)
              .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
              .padding(horizontal = space.snug, vertical = space.tight),
  ) {
    Icon(
        painter = glyph(glyph),
        contentDescription = null,
        tint = ink,
        modifier = Modifier.size(SourceGlyphSize),
    )
    // One line, always: a plate on a shared row that breaks a word reads as a rendering fault
    // rather than as a wrapped label.
    PlateLabel(text = label, color = ink, maxLines = 1)

    // Trailing, so the silhouette that opens the side does not shift as the link comes and goes.
    if (isWorking) {
      WorkingDots(color = ink)
    }
  }
}

/**
 * The source when there is nothing to switch to: **one device, said plainly.**
 *
 * **In gilt, and in a pill.** It is the one line of chrome on this screen that a presenter may have
 * to point at from the stage — *these are the phone's ears* — and a grey plate among grey plates is
 * not something an audience finds. Gilt on the gilt wash is the app's own chip, the same one a
 * named bird wears in the log below; the source is the session's answer to a question too, so it is
 * set in the ink the app answers in.
 *
 * **A glyph before the word, and it is the whole point of the redraw.** A pill that read `PHONE`
 * from the third row of a room said nothing; a phone and a pair of glasses are two silhouettes
 * anybody can tell apart at that distance, and the word is then confirmation rather than the only
 * evidence. The two drawings come off `BirdSpotterGlyphs` like every other icon in the app.
 *
 * It takes no tap, and it is not drawn as though it might: with no pair in reach there is nothing
 * on the other side of a switch, and a control that quietly became live the moment a pair walked
 * into the room would be a control nobody knew they had. That is [SourceSwitch]'s job, and the
 * header changes shape when it applies.
 *
 * The glasses' states are still reachable here — a run that was live when the pair went out of
 * reach keeps `canToggleSource` true, so in practice this draws the phone or a simulated feed — but
 * the inks are the switch's, so the two forms never disagree about what a state looks like.
 */
@Composable
private fun SourcePill(state: SourceState, label: String, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors

  // A paused run is the one state the pill does not answer in gilt: nothing is arriving, so
  // there is nothing for the app's answering ink to be claiming.
  val isPaused = state == SourceState.GLASSES_PAUSED
  val ink = if (isPaused) colors.textSecondary else colors.gilt
  val wash = if (isPaused) colors.lacquerHigh else colors.giltWash

  // Which device the pill is about, which is not always the device it is *on*: the linking beat
  // wears the glasses it is reaching for.
  val isAboutGlasses = state != SourceState.ON_DEVICE && state != SourceState.SIMULATED

  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(space.snug),
      modifier =
          modifier
              .clip(CircleShape)
              .background(wash)
              .padding(horizontal = space.snug, vertical = space.tight)
              // One node, not a silhouette and a word read separately — the pill is one reading.
              .semantics(mergeDescendants = true) { contentDescription = "Session source: $label" },
  ) {
    Icon(
        painter =
            glyph(
                if (isAboutGlasses) BirdSpotterTheme.glyphs.glasses
                else BirdSpotterTheme.glyphs.device,
            ),
        contentDescription = null,
        tint = ink,
        modifier = Modifier.size(SourceGlyphSize),
    )
    PlateLabel(text = label, color = ink, maxLines = 1)

    // The one state that is *doing* something. Trailing rather than leading, so the row's
    // left edge — the silhouette — does not shift as the link comes and goes.
    if (state == SourceState.LINKING) {
      WorkingDots(color = ink)
    }
  }
}

/**
 * Where the watcher is standing — the pin, and the dot that is the fix landing.
 *
 * It goes quiet rather than guessing: a pin still in the faint ink is a session that has not been
 * answered yet, not an error worth a sentence.
 *
 * The dot belongs *here*, next to the thing that means where. Beside the source plate it was a
 * second, vaguer claim about the session at large, which is why that copy of it went and this one
 * did not.
 */
@Composable
private fun LocationReading(uiState: RealtimeUiState, modifier: Modifier = Modifier) {
  val colors = BirdSpotterTheme.colors
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.snug),
      modifier =
          modifier.semantics {
            contentDescription = if (uiState.isLocated) "Location found" else "Finding location"
          },
  ) {
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.location),
        contentDescription = null,
        tint = if (uiState.isLocated) colors.textSecondary else colors.textFaint,
    )
    LocationDot(isLocated = uiState.isLocated)
  }
}

/**
 * Which way the watcher is facing, as one of eight points.
 *
 * Quiet in the same way the pin is: a compass with no letters is a phone that cannot answer, and
 * saying so in faint ink is the whole message.
 */
@Composable
private fun BearingReading(uiState: RealtimeUiState, modifier: Modifier = Modifier) {
  val colors = BirdSpotterTheme.colors
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.snug),
      modifier =
          modifier.semantics {
            contentDescription = uiState.headingPoint?.let { "Facing $it" } ?: "No compass"
          },
  ) {
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.compass),
        contentDescription = null,
        tint = if (uiState.headingPoint == null) colors.textFaint else colors.textSecondary,
    )
    // Reserved whether or not there is a bearing, so nothing jumps the first time the compass
    // speaks.
    PlateLabel(
        text = uiState.headingPoint.orEmpty(),
        color = colors.textSecondary,
        modifier = Modifier.widthIn(min = BearingPlateWidth),
    )
  }
}

/**
 * The standing spot: where the watcher is, and which way they are turned.
 *
 * **One sentence, so one group.** *Here, facing north-east* is a single fact about where the
 * watcher is planted, and the two halves of it read as one only when they are side by side. Split
 * across the shutter they were two unrelated instruments that happened to share a row.
 */
@Composable
private fun StandingReading(uiState: RealtimeUiState, modifier: Modifier = Modifier) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.related),
      modifier = modifier,
  ) {
    LocationReading(uiState = uiState)
    BearingReading(uiState = uiState)
  }
}

/**
 * How high the watcher is looking, as one of five strata.
 *
 * **The other question the foot answers, and the reason it gets its own end of the row.** The pin
 * and the compass are about a standing spot and are true while the watcher stands still; this
 * changes the moment they raise their head, and it is the one reading on the screen a presenter can
 * demonstrate by looking up.
 *
 * A word, not an angle: `Canopy` is where a bird would be, where `+37°` is a fact about a sensor.
 * See [com.meta.pixelandtexel.birdspotter.domain.gazeBand].
 *
 * Quiet in the same way the compass is — a mark in faint ink and no word — rather than absent the
 * way the chip over the viewfinder is. It has a neighbour across the row to stay level with now,
 * and a foot that loses a whole side while the sensors settle is a foot that moves.
 */
@Composable
private fun ElevationReading(uiState: RealtimeUiState, modifier: Modifier = Modifier) {
  val colors = BirdSpotterTheme.colors
  // The journal's formatter, not a local one: the foot and the saved entry name a stratum the same
  // way or they are two vocabularies for one reading.
  val label = uiState.elevationBand?.let(JournalFormatting::gazeLabel)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(BirdSpotterTheme.space.snug, Alignment.End),
      modifier =
          modifier.semantics {
            contentDescription = label?.let { "Aimed at the $it" } ?: "No tilt reading"
          },
  ) {
    // The mark trails the word here, where the pin and the compass lead theirs. Both readings put
    // their mark on the row's outer edge, which is the edge that holds still: this half is
    // end-aligned, so a mark on the inside would slide by the width of the word every time the
    // stratum changed — `Sky` and `Understory` would draw the eye in two different places.
    PlateLabel(text = label.orEmpty(), color = colors.textSecondary, maxLines = 1)
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.gaze),
        contentDescription = null,
        tint = if (label == null) colors.textFaint else colors.textSecondary,
    )
  }
}

/**
 * The session's foot: the shutter, centred, with **where the watcher is standing** on one side of
 * it and **where they are looking** on the other.
 *
 * The shutter is centred and unlabelled, at the size it will be once the panel has grown — it is
 * the thing a thumb goes looking for without aiming, and a word beside it only says what the shape
 * already does. Drawn here at exactly the panel's own shutter size, so the control the watcher
 * presses and the control they press next are the same drawing at the same scale.
 *
 * **The readings used to be a row under the header, and this is a better place for them.** They are
 * chrome about the standing spot, not part of the instrument, and up there they pushed the strip a
 * section's worth down the screen to say so. Down here they flank the one control the screen has,
 * in the band the log already leaves clear — at no cost in timeline. The gap they vacated stays
 * where it was, because the strip still wants clearing from the header.
 *
 * **The split is by question, not by count.** The pin and the compass are one thought — *I am here,
 * facing that way* — and they were never two readings that wanted opposite ends of a row; putting
 * the bearing across the shutter from the pin made a watcher read the foot twice to assemble one
 * sentence. Together on the leading edge they are the standing spot, and the trailing edge is left
 * to the other question entirely: how high.
 *
 * **Weighted halves rather than a centred overlay**, so the two groups can never run under the
 * shutter. Both sides take the same flexible width, which centres the control exactly while giving
 * each reading a hard bound to lay out in — a stratum spelled *Understory* at a large font scale
 * truncates inside its own half instead of colliding with the thing a thumb is aiming for.
 *
 * They fade with the shutter rather than staying put, and not only because the panel covers them:
 * the foot is one object, and half of it hanging on while the other half becomes a viewfinder would
 * read as two.
 *
 * `LIVE` used to sit opposite the shutter. It was a plate that never changed, on a screen whose
 * strip is already moving because the microphone is open.
 */
@Composable
private fun SessionControls(
    uiState: RealtimeUiState,
    onOpenCamera: () -> Unit,
    onCapture: () -> Unit,
    onControlBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  // This control *becomes* the panel, so it goes rather than sitting underneath it — what leaves
  // and what arrives are meant to read as one object, which means they cannot be on two clocks.
  val presence by animateFloatAsState(
      targetValue = if (uiState.isCameraOpen) 0f else 1f,
      animationSpec = if (uiState.isCameraOpen) CameraMorph else CameraDismiss,
      label = "controls",
  )
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .windowInsetsPadding(WindowInsets.navigationBars)
              .padding(space.gutter)
              .graphicsLayer { alpha = presence },
      verticalAlignment = Alignment.CenterVertically,
  ) {
    StandingReading(uiState = uiState, modifier = Modifier.weight(1f))

    // On the phone the shutter opens the viewfinder panel; on the glasses it *is* the
    // shutter — the wearer's eyes already framed the shot, so the press asks the glasses
    // for the photograph and the panel never enters into it. While one is mid-crossing
    // the control dims and holds still; what the crossing itself looks like is a row on
    // the log, where the photograph is going to land.
    //
    // **Routed on the session, exactly as `capturePhoto` routes.** This read the *pill* —
    // `sourceKind`, which follows the audio that arrived — and so disagreed with the view
    // model it was calling: in the beat where the camera stream is live but the glasses'
    // audio has not crossed yet, the button opened the phone's panel while `capturePhoto`
    // would have photographed through the glasses, and in the beat after a pause it called
    // a capture that fell through to a phone path with no frame and did nothing at all. The
    // eyes and the ears are allowed to differ; the shutter follows the eyes.
    val isGlassesSource = uiState.isGlassesSessionLive
    IconButton(
        onClick = { if (isGlassesSource) onCapture() else onOpenCamera() },
        enabled = uiState.status == SessionStatus.LISTENING && !uiState.isCapturing,
        // What the panel grows out of, measured rather than guessed. Reported whatever the
        // presence alpha is doing — a transparent control still has a rectangle, and that
        // rectangle is what the panel shrinks back into.
        modifier =
            Modifier.size(ShutterTargetSize).onGloballyPositioned {
              onControlBounds(it.boundsInWindow())
            },
    ) {
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.shutter),
          contentDescription =
              if (isGlassesSource) {
                "Photograph through the glasses"
              } else {
                "Open the camera"
              },
          tint =
              if (uiState.isCapturing) {
                BirdSpotterTheme.colors.textFaint
              } else {
                BirdSpotterTheme.colors.textPrimary
              },
          modifier = Modifier.size(ShutterSize),
      )
    }

    ElevationReading(uiState = uiState, modifier = Modifier.weight(1f))
  }
}

/**
 * The camera, over the session — **a panel that is the button, grown**.
 *
 * It starts life as exactly [closedBounds], the camera control's rectangle, and grows from there
 * into a rounded card over the lower part of the session, then shrinks back into it. Not an
 * enter/exit transition: the panel is always in the tree and its rectangle is animated between the
 * two, so there is one object on screen throughout rather than a small one leaving and a large one
 * arriving. A `scaleIn` can only be anchored to a *corner of the thing being scaled*, which is near
 * the button but never it — this is measured.
 *
 * A card rather than a screen, and that is the point: the camera is a mode *inside* a session that
 * never stopped listening, and a viewfinder that swallowed the phone would say the opposite. The
 * whole strip and the top of the log stay in view behind it.
 *
 * The shutter is real here — it takes the frame on screen, puts it on the timeline at this second,
 * and closes the mode. The X leaves without taking one.
 */
@Composable
private fun BoxScope.CameraMode(
    isOpen: Boolean,
    frame: PreviewFrame?,
    viewfinder: CameraViewfinder?,
    closedBounds: Rect,
    controls: Set<CameraControl>,
    isFlashOn: Boolean,
    isCapturing: Boolean,
    gazeBand: GazeContext?,
    onCancel: () -> Unit,
    onCapture: () -> Unit,
    onToggleFlash: () -> Unit,
    onZoomBy: (Float) -> Unit,
) {
  val space = BirdSpotterTheme.space
  val density = LocalDensity.current
  val navigationBar = WindowInsets.navigationBars.getBottom(density)

  // **One number drives the whole mode, and which curve it rides depends on the direction.**
  //
  // Leaving is not arriving played backwards. A spring is right for arriving, because the panel is
  // a thing *becoming* — it should overshoot slightly and settle. Leaving has nothing to settle
  // into, and a spring's tail keeps a live camera frame being resized and recomposited long after
  // the panel stopped being interesting. A short ease covers the same distance and stops dead.
  val morph by animateFloatAsState(
      targetValue = if (isOpen) 1f else 0f,
      animationSpec = if (isOpen) CameraMorph else CameraDismiss,
      label = "camera",
  )

  // A scrim, so the panel reads as raised over a session that is still running.
  Box(
      Modifier.fillMaxSize()
          .background(BirdSpotterTheme.colors.ink.copy(alpha = ScrimOpacity * morph)),
  )

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val inset = with(density) { space.related.toPx() }
    val open = Rect(
        left = inset,
        top = constraints.maxHeight * (1f - CameraPanelHeight) - inset - navigationBar,
        right = constraints.maxWidth - inset,
        bottom = constraints.maxHeight - inset - navigationBar,
    )
    // The panel starts and ends its life as the camera control's rectangle, with its foot
    // dropped onto the open one's.
    //
    // **The two share a bottom edge, and that is the whole point.** The panel lays its controls
    // out from that edge, so if it travels during the morph the shutter travels with it —
    // pressed, the button lifts an inset and settles back, which is the one thing on the screen
    // that must not move while a thumb is on it. Matching the *ends* was not enough; this
    // matches every frame in between. Growing the closed rectangle down by that inset costs
    // nothing visible: the contents are still fading in, and what is on screen is a rounded
    // shape the size of the button, twelve dp taller than the button.
    //
    // Before the control has been measured there is nowhere to grow from, so the panel simply
    // is its open rectangle — one frame at most, and only ever on the very first composition.
    val closed =
        if (closedBounds.isEmpty) {
          open
        } else {
          closedBounds.copy(bottom = open.bottom)
        }
    val rect = lerp(closed, open, morph)

    // Gone rather than transparent once the morph has finished closing: a zero-alpha box over
    // the session's own controls would still take the taps meant for them.
    if (morph > 0f) {
      CameraPanel(
          frame = frame,
          viewfinder = viewfinder,
          controls = controls,
          isFlashOn = isFlashOn,
          isCapturing = isCapturing,
          gazeBand = gazeBand,
          onCancel = onCancel,
          onCapture = onCapture,
          onToggleFlash = onToggleFlash,
          onZoomBy = onZoomBy,
          modifier =
              Modifier.absoluteOffset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                  .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                  // The contents fade in over the growth rather than being scaled up from
                  // nothing: a viewfinder squeezed into 48 dp is a smear, and the button it is
                  // standing in for is a shape, not a picture.
                  .graphicsLayer { alpha = morph },
      )
    }
  }
}

/**
 * The panel itself: the picture, the band it is aimed at across the head of it, and the controls
 * laid over the foot.
 *
 * **The picture is the platform's own surface, and that is the whole viewfinder story.** The camera
 * draws into it on the hardware path — no frame of the live picture ever crosses app code — which
 * is what makes a pinch here feel like the camera app's: the preview is not waiting on any copy
 * this process makes. Three earlier rounds of zoom machinery (request pacing, frame stamps, a
 * lead-scale correction) were all compensation for drawing the viewfinder out of [frame]s, and all
 * three went when the surface came in. The frames are still collected — they are what the shutter
 * *takes*, and the picture a source with no surface falls back to.
 *
 * `EMBEDDED` rather than the default `EXTERNAL`, deliberately: an `EXTERNAL` surface is its own
 * window layer, which ignores the alpha and the rounded clip the panel's morph is made of. An
 * embedded surface composites in-tree — still zero app-side copies, just drawn by the GPU where the
 * panel actually is.
 */
@Composable
private fun CameraPanel(
    frame: PreviewFrame?,
    viewfinder: CameraViewfinder?,
    controls: Set<CameraControl>,
    isFlashOn: Boolean,
    isCapturing: Boolean,
    gazeBand: GazeContext?,
    onCancel: () -> Unit,
    onCapture: () -> Unit,
    onToggleFlash: () -> Unit,
    onZoomBy: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Box(
      modifier.fillMaxWidth().clip(RoundedCornerShape(space.gutter)),
  ) {
    // What the panel is made of while it is still becoming one. A flat fill made the growing
    // rectangle read as a hole cut in the session; a screened surface reads as something laid
    // over it — see [FrostedGround].
    FrostedGround()

    // Pinch on the picture, not on a slider beside it — the thing being magnified is the
    // thing under the fingers. Only offered by a camera that can actually do it; a gesture
    // that silently does nothing is worse than no gesture.
    //
    // `zoomChange` is the step since the last event rather than since the fingers landed,
    // which is why the view model multiplies rather than sets. Forwarded per event, the way
    // the platform's own camera recipe does — the surface shows the result at sensor rate,
    // so there is nothing left to pace or correct.
    val pinch =
        if (CameraControl.ZOOM in controls) {
          Modifier.pointerInput(Unit) {
            detectTransformGestures { _, _, zoomChange, _ -> onZoomBy(zoomChange) }
          }
        } else {
          Modifier
        }

    val image = remember(frame) { frame?.image?.asImageBitmap() }
    when {
      viewfinder != null ->
          CameraXViewfinder(
              surfaceRequest = viewfinder.request,
              implementationMode = ImplementationMode.EMBEDDED,
              modifier =
                  Modifier.fillMaxSize()
                      .semantics { contentDescription = "Live camera" }
                      .then(pinch),
          )

      // No surface, but frames — a Compose preview, a simulated feed. The picture costs a
      // copy per frame here and that is fine: nothing without a real camera produces enough
      // of them to matter.
      image != null ->
          Image(
              bitmap = image,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier =
                  Modifier.fillMaxSize()
                      .semantics { contentDescription = "Live camera" }
                      .then(pinch),
          )

      else ->
          PlateLabel(
              text = "Opening the camera",
              color = BirdSpotterTheme.colors.textSecondary,
              modifier = Modifier.align(Alignment.Center),
          )
    }

    // What the viewfinder is aimed at, across the head of the picture — see [GazeChip].
    if (gazeBand != null) {
      GazeChip(
          band = gazeBand,
          modifier = Modifier.align(Alignment.TopCenter).padding(top = space.related),
      )
    }

    // Leave without a photo on the left, take one in the middle — the panel's own foot. Over
    // the picture rather than under it, so the panel stays one shape however tall the frame
    // turns out to be. The gradient is what keeps a pale glyph legible over a bright sky.
    Box(
        Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        BirdSpotterTheme.colors.ink.copy(alpha = 0f),
                        BirdSpotterTheme.colors.ink.copy(alpha = 0.6f),
                    ),
                ),
            )
            .padding(horizontal = space.related)
            // One step tighter than the control it grew out of, and that is what keeps the
            // shutter still: the panel's own bottom edge already sits an inset above where the
            // control's did, so the same gutter here would lift the shutter by exactly that
            // inset — the one thing under the thumb, jumping, in the middle of an animation
            // whose whole claim is that the button *became* this.
            .padding(bottom = space.related, top = space.gutter),
    ) {
      IconButton(
          onClick = onCancel,
          modifier = Modifier.align(Alignment.CenterStart).size(ControlSize),
      ) {
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.close),
            contentDescription = "Close the camera",
            tint = BirdSpotterTheme.colors.textPrimary,
        )
      }

      IconButton(
          onClick = onCapture,
          // Held through an armed flash's settle as well as through a missing frame: the
          // panel deliberately stays open across [FlashSettleMillis] so the light is seen
          // coming on, and a shutter left live across those milliseconds is a second
          // capture waiting to happen — see [RealtimeUiState.isCapturing].
          enabled = image != null && !isCapturing,
          modifier = Modifier.align(Alignment.Center).size(ShutterTargetSize),
      ) {
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.shutter),
            contentDescription = "Take a photo",
            tint = BirdSpotterTheme.colors.textPrimary,
            modifier = Modifier.size(ShutterSize),
        )
      }

      // **The flash is there only if this camera has one.** Built from the source's
      // capabilities rather than from its kind, so the day the frames come off a pair of
      // glasses the foot quietly loses a flash the glasses do not have — with no line here
      // mentioning glasses. Absent, not disabled: a greyed-out control invites someone to
      // wonder what they broke.
      if (CameraControl.FLASH in controls) {
        IconButton(
            onClick = onToggleFlash,
            modifier = Modifier.align(Alignment.CenterEnd).size(ControlSize),
        ) {
          Icon(
              painter =
                  glyph(
                      if (isFlashOn) {
                        BirdSpotterTheme.glyphs.flash
                      } else {
                        BirdSpotterTheme.glyphs.flashOff
                      },
                  ),
              contentDescription = if (isFlashOn) "Flash on" else "Flash off",
              // Armed in gilt: the app's one warm ink, doing the job a yellow flash badge
              // does everywhere else. Off is the struck-through bolt in the ordinary ink —
              // two drawings, so the state is legible without comparing brightnesses.
              tint =
                  if (isFlashOn) {
                    BirdSpotterTheme.colors.gilt
                  } else {
                    BirdSpotterTheme.colors.textPrimary
                  },
          )
        }
      }
    }
  }
}

/**
 * Which stratum the viewfinder is pointed at, over the top of the picture.
 *
 * **The same reading [ElevationReading] carries, drawn where the foot cannot follow.** The foot
 * fades out as the panel grows, so the two are never on screen together — this is that reading
 * continuing across the one moment it matters most, when the watcher is actually aiming. Not a
 * second opinion: both read [RealtimeUiState.elevationBand], and both name it through the journal's
 * formatter.
 *
 * It used to be the only place this reading appeared, on the argument that a stratum is about *aim*
 * and so belongs on the thing being aimed. That held while the aim came from the phone's own
 * attitude. It no longer does: the reading now follows the wearer's head whenever the glasses have
 * the session, which makes it a fact about the watcher — true whether or not a viewfinder is open,
 * and so owed a place in the foot beside the pin and the compass.
 *
 * A word, not an angle: `Canopy` is where a bird would be, where `+37°` is a fact about a sensor.
 * See [gazeBand].
 *
 * Its own dark capsule rather than the gradient the foot uses. A band across the top of a
 * viewfinder would darken the sky, which is the half of the frame a watcher is usually reading; a
 * capsule carries the same legibility over a bright ground in the width of the word itself.
 *
 * Absent rather than quiet, unlike its twin in the foot: over a picture there is no row for a gap
 * to unbalance, so a stratum that cannot be read is simply not drawn.
 */
@Composable
private fun GazeChip(band: GazeContext, modifier: Modifier = Modifier) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  // The journal's formatter, not a local one: the chip and the saved entry name a stratum the
  // same way or they are two vocabularies for one reading.
  val label = JournalFormatting.gazeLabel(band)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(space.snug),
      modifier =
          modifier
              .clip(CircleShape)
              .background(colors.ink.copy(alpha = GazeChipOpacity))
              .padding(horizontal = space.snug, vertical = space.tight)
              .semantics { contentDescription = "Aimed at the $label" },
  ) {
    Icon(
        painter = glyph(BirdSpotterTheme.glyphs.gaze),
        contentDescription = null,
        tint = colors.textPrimary,
    )
    PlateLabel(text = label, color = colors.textPrimary)
  }
}

/** Why the session has no sound, and what to do about it. */
@Composable
private fun FailureNotice(message: String?) {
  val space = BirdSpotterTheme.space
  Column(
      modifier = Modifier.padding(horizontal = space.gutter),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(space.related),
  ) {
    PlateLabel(text = "Session", color = BirdSpotterTheme.colors.gilt)
    Text(
        text = message.orEmpty(),
        style = BirdSpotterTheme.type.body,
        color = BirdSpotterTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
    )
  }
}

/** The chrome controls' tap target — the wizard's size, so the feature's controls all match. */
private val ControlSize = 48.dp

/**
 * The device silhouette in the source pill. Set to the plate's own cap height rather than the glyph
 * set's 24, so the icon and the word read as one line of type instead of a picture with a caption
 * beside it.
 */
private val SourceGlyphSize = 16.dp

/**
 * The air between the switch's track and the side sitting in it. One step under the scale's
 * smallest gap on purpose — this is a hairline of ground showing around a chip, not a gap between
 * two things, and at `tight` the track reads as a second, larger pill around the first.
 */
private val SwitchTrackInset = 2.dp

/**
 * How long the switch takes to throw. Short: the selection moves on the press, and this is only the
 * ink catching up with a decision the watcher has already made.
 */
private const val SwitchThrowMillis = 180

/**
 * The shutter is drawn larger than the other controls on purpose: it is the one thing on the screen
 * a thumb goes looking for without aiming.
 */
private val ShutterSize = 64.dp

/**
 * Its tap target, a little wider than the drawing, so the ring never sits flush against its edge.
 */
private val ShutterTargetSize = 72.dp

/**
 * The band the shutter occupies at the foot of the screen. The log leaves this much room below its
 * last row and fades out across it — see [SessionLog].
 */
private val controlsHeight: Dp
  @Composable get() = ShutterTargetSize + BirdSpotterTheme.space.gutter * 2

/**
 * Room for the widest of the eight points, so a turn from `N` to `NW` moves nothing but letters.
 */
private val BearingPlateWidth = 28.dp

/**
 * How dark the gaze chip's capsule is. The same value the foot's gradient ends on, so the two
 * pieces of chrome laid over the picture are laid over it to the same depth.
 */
private const val GazeChipOpacity = 0.6f

/**
 * How much of the screen the camera panel takes. A fraction rather than a height, because what has
 * to hold across every phone is what stays *uncovered*: the whole strip, and the top of the log
 * under it.
 */
private const val CameraPanelHeight = 0.62f

/**
 * How far the session dims behind the panel. Enough to say the panel is in front, not so much that
 * the strip stops being readable — it is still recording, and still worth watching.
 */
private const val ScrimOpacity = 0.55f

/**
 * The camera's morph. A spring rather than a curve: the panel is meant to read as the control
 * growing, and something that grows to exactly its final size and stops reads as a cross-fade.
 */
private val CameraMorph = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * How the panel leaves: a short ease, not the spring it arrived on. See the note on the morph in
 * [CameraMode] for why the two directions differ.
 */
private val CameraDismiss = tween<Float>(durationMillis = 200, easing = FastOutLinearInEasing)

/**
 * How far the review travels on its way in. A `separate`'s worth — enough that it reads as arriving
 * rather than appearing, and short enough that nothing on it is legible mid-flight and then moves.
 */
private val ReviewRise = 16.dp

/**
 * How the session leaves when the stop lands: a short fade, no travel. The leaving easing the
 * camera panel uses, for the same reason — it is ending, not settling.
 */
private val ReviewDeparture = tween<Float>(durationMillis = 220, easing = FastOutLinearInEasing)

/**
 * How the review arrives, held back until the session has all but gone — the second beat.
 *
 * The delay is the whole effect. Without it the two screens cross-fade and the eye has two things
 * to read at once; with it there is a moment where the session is over and nothing has replaced it
 * yet, which is exactly what has happened.
 *
 * Two specs for one curve because [fadeIn] animates a float and [slideInVertically] an offset.
 */
private val ReviewArrival =
    tween<Float>(durationMillis = 340, delayMillis = 180, easing = LinearOutSlowInEasing)

private val ReviewArrivalOffset =
    tween<IntOffset>(durationMillis = 340, delayMillis = 180, easing = LinearOutSlowInEasing)

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Listening")
@Composable
private fun RealtimeScreenListeningPreview() {
  RealtimeScreen(
      uiState =
          RealtimeUiState(
              sourceKind = CaptureSourceKind.PHONE,
              status = SessionStatus.LISTENING,
              session = PreviewSession,
          ),
      sonogram = remember { previewSonogram() },
      elapsed = PreviewColumns / SonogramColumnsPerSecond,
      frame = null,
      viewfinder = null,
      onStop = {},
      onClose = {},
      onOpenCamera = {},
      onCloseCamera = {},
      onCapture = {},
      onToggleSource = {},
      onShowCard = {},
      onDismissCard = {},
      onToggleFlash = {},
      onZoomBy = {},
      onToggleBird = {},
      onNotesChange = {},
      onSave = {},
      stripReading = StripReading.SONOGRAM,
      onStripReadingChange = {},
  )
}

/**
 * The same session on the other reading — the live trace, at the moment the preview's data ends.
 */
@Preview(showBackground = true, name = "Listening, waveform")
@Composable
private fun RealtimeScreenWaveformPreview() {
  RealtimeScreen(
      uiState =
          RealtimeUiState(
              sourceKind = CaptureSourceKind.PHONE,
              status = SessionStatus.LISTENING,
              session = PreviewSession,
          ),
      sonogram = remember { previewSonogram() },
      elapsed = PreviewColumns / SonogramColumnsPerSecond,
      frame = null,
      viewfinder = null,
      onStop = {},
      onClose = {},
      onOpenCamera = {},
      onCloseCamera = {},
      onCapture = {},
      onToggleSource = {},
      onShowCard = {},
      onDismissCard = {},
      onToggleFlash = {},
      onZoomBy = {},
      onToggleBird = {},
      onNotesChange = {},
      onSave = {},
      stripReading = StripReading.WAVEFORM,
      onStripReadingChange = {},
  )
}

@Preview(showBackground = true, name = "Microphone denied")
@Composable
private fun RealtimeScreenFailedPreview() {
  RealtimeScreen(
      uiState =
          RealtimeUiState(
              status = SessionStatus.FAILED,
              failure = AudioCaptureError.AccessDenied,
          ),
      sonogram = remember { SonogramBuffer(capacity = 1) },
      elapsed = 0.0,
      frame = null,
      viewfinder = null,
      onStop = {},
      onClose = {},
      onOpenCamera = {},
      onCloseCamera = {},
      onCapture = {},
      onToggleSource = {},
      onShowCard = {},
      onDismissCard = {},
      onToggleFlash = {},
      onZoomBy = {},
      onToggleBird = {},
      onNotesChange = {},
      onSave = {},
      stripReading = StripReading.SONOGRAM,
      onStripReadingChange = {},
  )
}

@Preview(showBackground = true, name = "Review")
@Composable
private fun RealtimeScreenReviewPreview() {
  RealtimeScreen(
      uiState =
          RealtimeUiState(
              status = SessionStatus.LISTENING,
              session = PreviewSession,
              review = SessionReview(durationSeconds = 19.0),
          ),
      sonogram = remember { SonogramBuffer(capacity = 1) },
      elapsed = 19.0,
      frame = null,
      viewfinder = null,
      onStop = {},
      onClose = {},
      onOpenCamera = {},
      onCloseCamera = {},
      onCapture = {},
      onToggleSource = {},
      onShowCard = {},
      onDismissCard = {},
      onToggleFlash = {},
      onZoomBy = {},
      onToggleBird = {},
      onNotesChange = {},
      onSave = {},
      stripReading = StripReading.SONOGRAM,
      onStripReadingChange = {},
  )
}

/** Columns behind the preview's window — twenty seconds of them. */
private val PreviewColumns = (20 * SonogramColumnsPerSecond).toInt()

/**
 * A strip with something on it: a band of energy that drifts, so the preview shows the ramp and the
 * lanes over a picture rather than over black. Not a recording of anything — a preview has no
 * microphone, and inventing a bird here would be inventing a bird.
 *
 * The level swells and falls with the band, so the waveform reading has something to draw too —
 * without it the second reading previews as a flat line, which is a picture of a bug rather than of
 * the control.
 */
private fun previewSonogram(): SonogramBuffer {
  val buffer = SonogramBuffer(capacity = PreviewColumns)
  repeat(PreviewColumns) { column ->
    val centre = 28 + (column / 12) % 40
    buffer.append(
        SonogramColumn(
            magnitudes =
                FloatArray(SonogramBins) { bin ->
                  val distance = kotlin.math.abs(bin - centre)
                  (1f - distance / 14f).coerceIn(0f, 1f) * 0.9f
                },
            level = 0.35f + 0.5f * kotlin.math.abs(kotlin.math.sin(column / 9f)),
        ),
    )
  }
  return buffer
}

/**
 * A session mid-run, at nineteen seconds: a robin heard, a description offered, a jay named. The
 * shipped script, so the preview shows what the demo shows.
 */
private val PreviewSession =
    RealtimeSession(startedAt = 0L)
        .adding(SessionEvent.Bird(14.0, "american-robin", "American Robin", 0.87f))
        .adding(SessionEvent.Speech(16.5, "It's green with a yellow belly"))
