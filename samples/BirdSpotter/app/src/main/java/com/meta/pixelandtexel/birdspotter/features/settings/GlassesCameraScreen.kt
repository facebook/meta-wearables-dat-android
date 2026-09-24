/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.camera.CaptureScratchStore
import com.meta.pixelandtexel.birdspotter.domain.CaptureQuality
import com.meta.pixelandtexel.birdspotter.domain.CaptureResolution
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButton
import com.meta.pixelandtexel.birdspotter.ui.components.CardMetrics
import com.meta.pixelandtexel.birdspotter.ui.components.Chip
import com.meta.pixelandtexel.birdspotter.ui.components.ChipTone
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.components.WorkingDots
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Camera — one photograph at chosen settings, and what it cost.
 *
 * Pushed from the glasses settings screen. The session opens with the screen; the two pickers set
 * what the next shutter asks for; the result arrives with its size, its bytes and its crossing time
 * printed beside it, and the share sheet is how it leaves the phone. See [GlassesCameraViewModel]
 * for why the screen exists at all.
 */
@Composable
fun GlassesCameraScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val container = (context.applicationContext as BirdSpotterApplication).container
  val viewModel: GlassesCameraViewModel = viewModel(
      factory =
          GlassesCameraViewModel.factory(
              container.glassesSessionRepository,
              container.glassesCameraRepository,
              container.captureScratchStore,
          ),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  GlassesCameraContent(
      uiState = uiState,
      onBack = onBack,
      onPickResolution = viewModel::choose,
      onPickQuality = viewModel::choose,
      onCapture = viewModel::capturePhoto,
      onRetry = viewModel::start,
      onShare = { sharePhoto(context, it) },
      modifier = modifier,
  )
}

/** The drawing, taking a reading rather than a repository. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassesCameraContent(
    uiState: GlassesCameraUiState,
    onBack: () -> Unit,
    onPickResolution: (CaptureResolution) -> Unit,
    onPickQuality: (CaptureQuality) -> Unit,
    onCapture: () -> Unit,
    onRetry: () -> Unit,
    onShare: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  Scaffold(
      modifier = modifier,
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Camera") },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(
                    painter = glyph(BirdSpotterTheme.glyphs.back),
                    contentDescription = "Back",
                )
              }
            },
        )
      },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { innerPadding ->
    // Per-child padding and no `verticalArrangement`, deliberately — the rhythm down this
    // page is not uniform, and setting both would add the two together.
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = space.gutter,
                    end = space.gutter,
                    top = space.separate,
                    bottom = space.page,
                ),
    ) {
      SessionSection(uiState = uiState, onRetry = onRetry)

      SettingsSection(
          uiState = uiState,
          onPickResolution = onPickResolution,
          onPickQuality = onPickQuality,
          modifier = Modifier.padding(top = space.section),
      )

      ShutterSection(
          uiState = uiState,
          onCapture = onCapture,
          modifier = Modifier.padding(top = space.section),
      )

      uiState.shot?.let { shot ->
        ResultSection(
            shot = shot,
            onShare = onShare,
            modifier = Modifier.padding(top = space.section),
        )
      }
    }
  }
}

/** Where the session stands, and whether the grant the shutter needs is in hand. */
@Composable
private fun SessionSection(
    uiState: GlassesCameraUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(space.related),
  ) {
    PlateLabel(text = "Session", color = colors.gilt)

    ReadingRow(label = "Glasses", value = sessionValue(uiState))
    ReadingRow(
        label = "Camera access",
        value = accessValue(uiState.cameraAccess),
        support = accessSupport(uiState.cameraAccess),
    )

    uiState.failure?.let {
      // In the page's own ink rather than a red: the palette spends its one red on
      // controls that end something, and a line explaining why the session ended is a
      // statement, not a control.
      Text(text = it, style = BirdSpotterTheme.type.body, color = colors.textPrimary)
    }

    if (!uiState.isRunning) {
      ActionButton(title = "Reconnect", onClick = onRetry)
    }
  }
}

/**
 * The two knobs the SDK sells, and — where they do not reach the capture — the one line that says
 * so before anybody spends an afternoon comparing identical photographs.
 */
@Composable
private fun SettingsSection(
    uiState: GlassesCameraUiState,
    onPickResolution: (CaptureResolution) -> Unit,
    onPickQuality: (CaptureQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  Column(
      // The pickers stay tappable when they are inert: what they set is still what the
      // request carries, and the note below already says where it stops.
      modifier = modifier.alpha(if (uiState.honoursCaptureSettings) 1f else 0.6f),
      verticalArrangement = Arrangement.spacedBy(space.related),
  ) {
    PlateLabel(text = "Settings", color = colors.gilt)

    if (!uiState.honoursCaptureSettings) {
      Text(
          text =
              "This build's captures travel a channel that takes neither setting, " +
                  "so both pickers are inert: every photograph below comes back at " +
                  "whatever the transport sends.",
          style = BirdSpotterTheme.type.label,
          color = colors.textPrimary,
      )
    }

    PickerRow(
        title = "Resolution",
        support = "How much of the sensor is kept. The expensive knob.",
    ) {
      CaptureResolution.entries.forEach { resolution ->
        SettingChip(
            title = resolution.displayLabel,
            isOn = uiState.resolution == resolution,
        ) {
          onPickResolution(resolution)
        }
      }
    }

    PickerRow(
        title = "Quality",
        support = "How hard it is compressed. Same pixels, fewer bytes.",
    ) {
      CaptureQuality.entries.forEach { quality ->
        SettingChip(title = quality.displayLabel, isOn = uiState.quality == quality) {
          onPickQuality(quality)
        }
      }
    }
  }
}

/**
 * The shutter, and the crossing.
 *
 * **The wait is a row, not a dimmed button.** The Bluetooth crossing is about a second and
 * sometimes several, and greying out the control somebody just pressed reads as broken rather than
 * as working.
 */
@Composable
private fun ShutterSection(
    uiState: GlassesCameraUiState,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val isUp = uiState.sessionState == GlassesSessionState.STARTED
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(space.related),
  ) {
    ActionButton(
        title = "Take photo",
        onClick = { if (isUp) onCapture() },
        modifier = Modifier.alpha(if (isUp) 1f else 0.5f),
    )

    if (uiState.isCapturing) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(space.snug),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        // The watcher's own photograph still arriving, so not gilt: gilt dots are the
        // app composing an answer.
        WorkingDots(
            color = colors.textSecondary,
            modifier = Modifier.size(width = 28.dp, height = 8.dp),
        )
        Text(
            text = "Receiving from the glasses…",
            style = BirdSpotterTheme.type.body,
            color = colors.textSecondary,
        )
      }
    }

    uiState.captureFailure?.let {
      Text(text = it, style = BirdSpotterTheme.type.body, color = colors.textPrimary)
    }
  }
}

/** The photograph, and the three numbers that are the reason to have taken it. */
@Composable
private fun ResultSection(
    shot: GlassesCameraShot,
    onShare: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  val context = LocalContext.current
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(space.related),
  ) {
    PlateLabel(text = "Last photograph", color = colors.gilt)

    ShotImage(
        file = shot.file,
        modifier =
            Modifier.fillMaxWidth()
                .height(ShotHeight)
                .clip(RoundedCornerShape(CardMetrics.CornerRadius))
                .border(1.dp, colors.rule, RoundedCornerShape(CardMetrics.CornerRadius)),
    )

    // Wrapping rather than scrolling: five short chips on a 390dp phone are two rows, and
    // a row that scrolls sideways hides the number at the end of it.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space.snug),
        verticalArrangement = Arrangement.spacedBy(space.snug),
    ) {
      Chip(text = shot.resolution.displayLabel, tone = ChipTone.INPUT)
      Chip(text = shot.quality.displayLabel, tone = ChipTone.INPUT)
      pixelLabel(shot)?.let { Chip(text = it) }
      Chip(text = byteLabel(context, shot.byteCount))
      Chip(text = crossingLabel(shot.crossingMillis), tone = ChipTone.ANSWER)
    }

    // The one way a photograph leaves the phone. The chooser's gallery entry is what puts
    // it in the camera roll; everything else in it is the same file going somewhere else.
    ActionButton(
        title = "Save or share",
        onClick = { onShare(shot.file) },
        tone = com.meta.pixelandtexel.birdspotter.ui.components.ActionButtonTone.SECONDARY,
    )
  }
}

/**
 * The captured file, decoded off the main thread at roughly the size it will be drawn.
 *
 * The same downsampled decode [com.meta.pixelandtexel.birdspotter.features.journal.OutingPhoto]
 * uses, and for the same reason: a full-size still is several megapixels, and a screen that decodes
 * all of them to draw a 280dp frame stutters on the one beat it most wants to feel smooth.
 */
@Composable
private fun ShotImage(file: File, modifier: Modifier = Modifier) {
  val isPreview = LocalInspectionMode.current
  val bitmap: ImageBitmap? by
      produceState<ImageBitmap?>(null, file.path, isPreview) {
        value =
            if (isPreview) {
              null
            } else {
              withContext(Dispatchers.IO) { decodeSampled(file)?.asImageBitmap() }
            }
      }

  Box(modifier.background(BirdSpotterTheme.colors.rule)) {
    bitmap?.let {
      Image(
          bitmap = it,
          contentDescription = "The photograph the glasses sent",
          // Fitted, not filled: the framing is what a capture test is looking at, and a
          // crop would take the edges away without saying so.
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Composable
private fun PickerRow(
    title: String,
    support: String,
    chips: @Composable () -> Unit,
) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  Column(verticalArrangement = Arrangement.spacedBy(space.snug)) {
    Text(text = title, style = BirdSpotterTheme.type.headline, color = colors.textPrimary)
    Text(text = support, style = BirdSpotterTheme.type.label, color = colors.textSecondary)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space.snug),
        verticalArrangement = Arrangement.spacedBy(space.snug),
    ) {
      chips()
    }
  }
}

/**
 * A [Chip] wearing a tap. The tone carries the state — `ANSWER` is the gilt the app names things
 * in, which is what a chosen setting is.
 */
@Composable
private fun SettingChip(title: String, isOn: Boolean, onClick: () -> Unit) {
  TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
    Chip(text = title, tone = if (isOn) ChipTone.ANSWER else ChipTone.NEUTRAL)
  }
}

@Composable
private fun ReadingRow(label: String, value: String, support: String? = null) {
  val space = BirdSpotterTheme.space
  val colors = BirdSpotterTheme.colors
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space.related),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = BirdSpotterTheme.type.headline, color = colors.textPrimary)
      support?.let {
        Text(text = it, style = BirdSpotterTheme.type.label, color = colors.textSecondary)
      }
    }
    Text(text = value, style = BirdSpotterTheme.type.body, color = colors.textSecondary)
  }
}

/**
 * What the session is doing, in the words the pill on the realtime screen uses — one vocabulary for
 * the link across the app, so a reading here is comparable with a reading there.
 */
private fun sessionValue(uiState: GlassesCameraUiState): String =
    when (uiState.sessionState) {
      GlassesSessionState.STARTING -> "Connecting"
      GlassesSessionState.STARTED -> "Connected"
      GlassesSessionState.PAUSED -> "Paused"
      GlassesSessionState.STOPPING -> "Stopping"
      GlassesSessionState.STOPPED -> "Stopped"
      null -> if (uiState.isRunning) "Connecting" else "Not started"
    }

private fun accessValue(access: GlassesAccess?): String =
    when (access) {
      GlassesAccess.GRANTED -> "Granted"
      GlassesAccess.DENIED -> "Denied"
      GlassesAccess.UNKNOWN -> "Unknown"
      null -> "—"
    }

/**
 * Only the readings that change what the shutter will do explain themselves. The grant is given in
 * the Meta AI app, which is where this points rather than at a button that cannot be offered from
 * here.
 */
private fun accessSupport(access: GlassesAccess?): String? =
    when (access) {
      GlassesAccess.DENIED ->
          "Meta AI has not been given the camera — grant it on the Meta AI Glasses screen"
      GlassesAccess.UNKNOWN ->
          "Meta AI can only answer this over a live link — connect your glasses"
      GlassesAccess.GRANTED,
      null -> null
    }

/**
 * The image's own size, when the file could be read. `null` is not an error worth a line of its own
 * — the photograph is on screen above it, which is the better evidence.
 */
private fun pixelLabel(shot: GlassesCameraShot): String? {
  val width = shot.pixelWidth ?: return null
  val height = shot.pixelHeight ?: return null
  return "$width × $height"
}

/**
 * Bytes in the phone's own units, which is what the wearer will compare against everything else on
 * their phone.
 */
private fun byteLabel(context: Context, byteCount: Int): String =
    Formatter.formatShortFileSize(context, byteCount.toLong())

/**
 * **Milliseconds under a second, seconds above it.** A crossing is the number this screen exists to
 * produce, and `0.8 s` throws away the digit that distinguishes a fast link from a very fast one
 * while `1847 ms` makes a slow one hard to feel.
 */
private fun crossingLabel(millis: Long): String {
  if (millis < 1_000) return "$millis ms"
  val seconds = millis / 100 / 10.0
  return "$seconds s"
}

/**
 * Hands the photograph out through the app's captures [FileProvider] — see the provider declaration
 * in `AndroidManifest.xml`. A `file://` URI would throw `FileUriExposedException`, and the
 * receiving app could not read this app's private storage in any case.
 */
private fun sharePhoto(context: Context, file: File) {
  if (!file.exists()) return
  val uri = FileProvider.getUriForFile(context, CaptureScratchStore.authority(context), file)
  val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
  context.startActivity(Intent.createChooser(send, "Share photo"))
}

/**
 * Decodes [file] downsampled to roughly [ShotDecodeWidthPx]: read the bounds first, halve
 * `inSampleSize` until the next halving would drop below the target, then decode once at that scale
 * so the full-size bitmap is never materialised.
 */
private fun decodeSampled(file: File): Bitmap? {
  if (!file.exists()) return null

  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(file.absolutePath, bounds)
  if (bounds.outWidth <= 0) return null

  var sample = 1
  while (bounds.outWidth / (sample * 2) >= ShotDecodeWidthPx) sample *= 2

  val options = BitmapFactory.Options().apply { inSampleSize = sample }
  return BitmapFactory.decodeFile(file.absolutePath, options)
}

/**
 * How tall the photograph is drawn. Big enough that feather detail is arguable at arm's length,
 * which is the whole reason somebody is comparing two of these.
 */
private val ShotHeight = 280.dp

/**
 * What the file is decoded to. Generous against the tallest phone at 3× rather than sized to
 * [ShotHeight], so the picture is not the soft thing in a comparison about sharpness.
 */
private const val ShotDecodeWidthPx = 1_200

/** Mid-experiment: connected, the grant in hand, and a large photograph back with its numbers. */
@Preview(showBackground = true)
@Composable
private fun GlassesCameraScreenPreview() {
  BirdSpotterTheme {
    GlassesCameraContent(
        uiState =
            GlassesCameraUiState(
                isRunning = true,
                sessionState = GlassesSessionState.STARTED,
                cameraAccess = GlassesAccess.GRANTED,
                resolution = CaptureResolution.LARGE,
                quality = CaptureQuality.HIGH,
                shot =
                    GlassesCameraShot(
                        file = File("/dev/null/glasses-large-high-3.jpg"),
                        resolution = CaptureResolution.LARGE,
                        quality = CaptureQuality.HIGH,
                        byteCount = 1_284_331,
                        pixelWidth = 2_592,
                        pixelHeight = 1_944,
                        crossingMillis = 4_120,
                    ),
            ),
        onBack = {},
        onPickResolution = {},
        onPickQuality = {},
        onCapture = {},
        onRetry = {},
        onShare = {},
    )
  }
}

/**
 * The beat the feature doc is about: the shutter fired, the picture still crossing, and the control
 * that was pressed still looking pressable.
 */
@Preview(showBackground = true)
@Composable
private fun GlassesCameraScreenCrossingPreview() {
  BirdSpotterTheme {
    GlassesCameraContent(
        uiState =
            GlassesCameraUiState(
                isRunning = true,
                sessionState = GlassesSessionState.STARTED,
                cameraAccess = GlassesAccess.GRANTED,
                isCapturing = true,
            ),
        onBack = {},
        onPickResolution = {},
        onPickQuality = {},
        onCapture = {},
        onRetry = {},
        onShare = {},
    )
  }
}

/**
 * The reading that matters: two pickers that cannot reach the capture, saying so before anybody
 * compares two identical photographs and concludes the glasses are broken.
 */
@Preview(showBackground = true)
@Composable
private fun GlassesCameraScreenInertSettingsPreview() {
  BirdSpotterTheme {
    GlassesCameraContent(
        uiState =
            GlassesCameraUiState(
                isRunning = true,
                sessionState = GlassesSessionState.STARTED,
                cameraAccess = GlassesAccess.GRANTED,
                honoursCaptureSettings = false,
            ),
        onBack = {},
        onPickResolution = {},
        onPickQuality = {},
        onCapture = {},
        onRetry = {},
        onShare = {},
    )
  }
}
