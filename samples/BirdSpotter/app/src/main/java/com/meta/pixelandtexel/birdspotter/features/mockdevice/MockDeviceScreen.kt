/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.mockdevice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesThermalLevel
import com.meta.pixelandtexel.birdspotter.domain.MockCameraFacing
import com.meta.pixelandtexel.birdspotter.domain.MockCapturePress
import com.meta.pixelandtexel.birdspotter.domain.MockGlassesModel
import com.meta.pixelandtexel.birdspotter.domain.MockMotionPose
import com.meta.pixelandtexel.birdspotter.domain.MockNavDirection
import com.meta.pixelandtexel.birdspotter.domain.MockSpeechSource
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButton
import com.meta.pixelandtexel.birdspotter.ui.components.ActionButtonTone
import com.meta.pixelandtexel.birdspotter.ui.components.CardMetrics
import com.meta.pixelandtexel.birdspotter.ui.components.Chip
import com.meta.pixelandtexel.birdspotter.ui.components.ChipTone
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.components.NotesField
import com.meta.pixelandtexel.birdspotter.ui.components.PlateLabel
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import kotlin.math.roundToInt

/**
 * The Mock Device Kit's controls, one section per capability the simulated pair has.
 *
 * Laid over the running app rather than pushed onto the graph, so the thing being driven — the
 * real-time session, the glasses screen, the display gallery — stays exactly where it was while the
 * controls are up, and is back the moment they close. Every control is one call on the kit and
 * reads back what it was last set to; nothing here has state of its own beyond the two file
 * pickers.
 *
 * The system back gesture closes it, the way it would a sheet.
 */
@Composable
fun MockDeviceScreen(
    viewModel: MockDeviceViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type

  BackHandler(onBack = onClose)

  // The kit opens a picked file later — when a stream starts, when a capture is asked for —
  // so the grant on it has to outlive this composition, not just the result callback.
  val pickVideo =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        viewModel.useCameraFeed(uri)
      }
  val pickPhoto =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        viewModel.useCapturedPhoto(uri)
      }

  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(colors.paper)
              // Swallows touches, so the screen underneath is not driven by taps meant for the
              // panel. No ripple: the panel is a surface, not a button.
              .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = {},
              )
              .safeDrawingPadding(),
  ) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = space.gutter,
                    end = space.snug,
                    top = space.snug,
                    bottom = space.snug,
                ),
        horizontalArrangement = Arrangement.spacedBy(space.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = "Mock Device Kit", style = type.title, color = colors.textPrimary)
      Spacer(modifier = Modifier.weight(1f))
      IconButton(onClick = onClose) {
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.close),
            contentDescription = "Close",
            tint = colors.textSecondary,
        )
      }
    }
    HairlineRule()

    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter, vertical = space.separate),
        verticalArrangement = Arrangement.spacedBy(space.section),
    ) {
      KitSection(uiState, viewModel)
      if (uiState.hasSelection) {
        DeviceSection(uiState, viewModel)
        GrantsSection(uiState, viewModel)
        CameraSection(
            viewModel = viewModel,
            onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
            onPickPhoto = { pickPhoto.launch(arrayOf("image/*")) },
        )
        InputsSection(viewModel)
        SpeechSection(uiState, viewModel)
        MotionSection(uiState, viewModel)
        if (uiState.selectedDevice?.model?.hasDisplay == true) {
          DisplaySection(uiState, viewModel)
        }
        VoiceSection(viewModel)
      }
      uiState.notice?.let { notice ->
        Text(text = notice, style = type.caption, color = colors.textSecondary)
      }
    }
  }
}

@Composable
private fun KitSection(uiState: MockDeviceUiState, viewModel: MockDeviceViewModel) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Kit", color = colors.gilt)

    SwitchRow(
        title = "Simulate the glasses",
        subtitle = "Off puts the real SDK back. Any running session ends first.",
        checked = uiState.isEnabled,
        enabled = !uiState.isFlipping,
        onCheckedChange = viewModel::setEnabled,
    )

    Text(text = "Model for the next pair", style = type.label, color = colors.textSecondary)
    ChoiceRow(
        choices = MockGlassesModel.entries,
        current = uiState.model,
        label = { it.displayName },
        onPick = viewModel::choose,
    )

    ActionButton(title = "Pair ${uiState.model.displayName}", onClick = viewModel::pair)

    if (uiState.devices.isNotEmpty()) {
      Text(text = "Paired", style = type.label, color = colors.textSecondary)
      ChoiceRow(
          choices = uiState.devices,
          current = uiState.selectedDevice,
          label = { it.model.displayName },
          onPick = { viewModel.select(it.id) },
      )
      ActionButton(
          title = "Unpair selected",
          onClick = viewModel::unpair,
          tone = ActionButtonTone.DESTRUCTIVE,
      )
    }
  }
}

@Composable
private fun DeviceSection(uiState: MockDeviceUiState, viewModel: MockDeviceViewModel) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Device", color = colors.gilt)

    ControlRow {
      ControlButton("Power on", viewModel::powerOn)
      ControlButton("Power off", viewModel::powerOff)
      ControlButton("Unfold", viewModel::unfold)
      ControlButton("Fold", viewModel::fold)
      ControlButton("Put on", viewModel::don)
      ControlButton("Take off", viewModel::doff)
    }

    BatterySlider(level = uiState.batteryLevel, onSet = viewModel::setBatteryLevel)

    SwitchRow(
        title = "Charging",
        checked = uiState.isCharging,
        onCheckedChange = viewModel::setCharging,
    )

    Text(text = "Heat", style = type.label, color = colors.textSecondary)
    ChoiceRow(
        choices = GlassesThermalLevel.entries,
        current = uiState.thermal,
        label = { thermalLabel(it) },
        onPick = viewModel::setThermal,
    )
  }
}

private fun thermalLabel(level: GlassesThermalLevel): String =
    when (level) {
      GlassesThermalLevel.NOMINAL -> "Nominal"
      GlassesThermalLevel.ELEVATED -> "Elevated"
      GlassesThermalLevel.CRITICAL -> "Critical"
    }

@Composable
private fun GrantsSection(uiState: MockDeviceUiState, viewModel: MockDeviceViewModel) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Grants", color = colors.gilt)
    SwitchRow(
        title = "Camera",
        checked = uiState.cameraAccess == GlassesAccess.GRANTED,
        onCheckedChange = { granted ->
          viewModel.setAccess(
              GlassesPermission.CAMERA,
              if (granted) GlassesAccess.GRANTED else GlassesAccess.DENIED,
          )
        },
    )
    SwitchRow(
        title = "Microphone",
        checked = uiState.microphoneAccess == GlassesAccess.GRANTED,
        onCheckedChange = { granted ->
          viewModel.setAccess(
              GlassesPermission.MICROPHONE,
              if (granted) GlassesAccess.GRANTED else GlassesAccess.DENIED,
          )
        },
    )
  }
}

@Composable
private fun CameraSection(
    viewModel: MockDeviceViewModel,
    onPickVideo: () -> Unit,
    onPickPhoto: () -> Unit,
) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Camera", color = colors.gilt)
    Text(
        text = "What the stream shows, and what a capture comes back with. Video must be H.265.",
        style = type.label,
        color = colors.textSecondary,
    )
    ControlRow {
      ControlButton("Phone camera, back") { viewModel.usePhoneCamera(MockCameraFacing.BACK) }
      ControlButton("Phone camera, front") { viewModel.usePhoneCamera(MockCameraFacing.FRONT) }
      ControlButton("Video file…", onPickVideo)
      ControlButton("Captured photo…", onPickPhoto)
      ControlButton("Fail next capture", viewModel::failNextCapture)
    }
  }
}

@Composable
private fun InputsSection(viewModel: MockDeviceViewModel) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Inputs", color = colors.gilt)
    ControlRow {
      ControlButton("Tap", viewModel::tap)
      ControlButton("Tap and hold", viewModel::tapAndHold)
      ControlButton("Swipe up") { viewModel.navigate(MockNavDirection.UP) }
      ControlButton("Swipe down") { viewModel.navigate(MockNavDirection.DOWN) }
      ControlButton("Swipe left") { viewModel.navigate(MockNavDirection.LEFT) }
      ControlButton("Swipe right") { viewModel.navigate(MockNavDirection.RIGHT) }
      ControlButton("Select", viewModel::select)
      ControlButton("Back", viewModel::back)
      ControlButton("Capture") { viewModel.pressCapture(MockCapturePress.SHORT_PRESS) }
      ControlButton("Capture, hold") { viewModel.pressCapture(MockCapturePress.HOLD) }
      ControlButton("Capture, double") { viewModel.pressCapture(MockCapturePress.DOUBLE_PRESS) }
      ControlButton("Action button", viewModel::pressActionButton)
    }
  }
}

@Composable
private fun SpeechSection(uiState: MockDeviceUiState, viewModel: MockDeviceViewModel) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Speech", color = colors.gilt)
    ChoiceRow(
        choices = MockSpeechSource.entries,
        current = uiState.speechSource,
        label = { if (it == MockSpeechSource.INJECTED) "Typed here" else "Phone's recogniser" },
        onPick = viewModel::setSpeechSource,
    )
    NotesField(
        text = uiState.speechText,
        onTextChange = viewModel::setSpeechText,
        placeholder = "What the wearer says",
    )
    ControlRow {
      ControlButton("Send partial") { viewModel.sendTranscription(isFinal = false) }
      ControlButton("Send final") { viewModel.sendTranscription(isFinal = true) }
      ControlButton("Recogniser error", viewModel::sendSpeechError)
      ControlButton("Complete", viewModel::completeSpeech)
    }
  }
}

@Composable
private fun MotionSection(uiState: MockDeviceUiState, viewModel: MockDeviceViewModel) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Head", color = colors.gilt)
    ChoiceRow(
        choices = MockMotionPose.entries,
        current = uiState.pose,
        label = { it.displayName },
        onPick = viewModel::setPose,
    )
  }
}

/**
 * The simulated panel, drawn by the kit's own renderer and kept live by it.
 *
 * Keyed on the pair: the view belongs to the pair it was made for, so a new selection has to make a
 * new one rather than keep showing the last pair's panel.
 */
@Composable
private fun DisplaySection(uiState: MockDeviceUiState, viewModel: MockDeviceViewModel) {
  val context = LocalContext.current
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type
  val clipboardManager =
      remember(context) { context.getSystemService(ClipboardManager::class.java) }
  val copyToClipboard: (String) -> Unit = { value ->
    clipboardManager?.setPrimaryClip(
        ClipData.newPlainText("BirdSpotter display preview", value),
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Display", color = colors.gilt)
    key(uiState.selectedDeviceId) {
      AndroidView(
          factory = { context -> viewModel.displayPreview(context) ?: View(context) },
          modifier =
              Modifier.fillMaxWidth()
                  // The panel is square; the kit's view fills whatever it is given.
                  .aspectRatio(1f)
                  .clip(RoundedCornerShape(CardMetrics.CornerRadius))
                  .semantics { contentDescription = "What the app drew on the simulated display" },
      )
    }

    uiState.displayServerPort?.let { port ->
      Text(text = "Chrome preview", style = type.label, color = colors.textSecondary)
      DisplayServerStep(
          number = 1,
          instruction = "Run the ADB command",
          value = "adb forward tcp:$port tcp:$port",
          onCopy = copyToClipboard,
      )
      DisplayServerStep(
          number = 2,
          instruction = "Open the link in Chrome",
          value = "http://127.0.0.1:$port/",
          onCopy = copyToClipboard,
      )
      DisplayServerStep(
          number = 3,
          instruction = "Enable the Meta Ray-Ban Display Simulator extension",
      )
    }
  }
}

@Composable
private fun DisplayServerStep(
    number: Int,
    instruction: String,
    value: String? = null,
    onCopy: (String) -> Unit = {},
) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type

  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space.related),
      verticalAlignment = Alignment.Top,
  ) {
    Text(text = "$number.", style = type.label, color = colors.gilt)
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(space.tight),
    ) {
      Text(text = instruction, style = type.label, color = colors.textPrimary)
      value?.let { text ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = text,
              style = type.caption.copy(fontFamily = FontFamily.Monospace),
              color = colors.textSecondary,
              modifier = Modifier.weight(1f),
          )
          CopyButton { onCopy(text) }
        }
      }
    }
  }
}

@Composable
private fun CopyButton(onClick: () -> Unit) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type
  val shape = RoundedCornerShape(CardMetrics.CornerRadius)

  Row(
      modifier =
          Modifier.clip(shape)
              .background(colors.paperRaised)
              .border(1.dp, colors.rule, shape)
              .clickable(onClick = onClick)
              .padding(horizontal = space.related, vertical = space.snug),
      horizontalArrangement = Arrangement.spacedBy(space.tight),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = Icons.Filled.ContentCopy,
        contentDescription = null,
        tint = colors.textPrimary,
        modifier = Modifier.size(14.dp),
    )
    Text(text = "Copy", style = type.label, color = colors.textPrimary)
  }
}

@Composable
private fun VoiceSection(viewModel: MockDeviceViewModel) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space

  Column(verticalArrangement = Arrangement.spacedBy(space.related)) {
    PlateLabel(text = "Voice", color = colors.gilt)
    ControlButton("“Hey Meta, open BirdSpotter”", viewModel::simulateVoiceLaunch)
  }
}

/** A title, an optional line under it, and the switch — the row every on/off here uses. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type

  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space.related),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = type.headline, color = colors.textPrimary)
      subtitle?.let { Text(text = it, style = type.label, color = colors.textSecondary) }
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = colors.paper,
                checkedTrackColor = colors.verdigris,
            ),
    )
  }
}

/** One-of-many, as chips: the chosen one in the answer tone, the rest quiet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <Choice> ChoiceRow(
    choices: List<Choice>,
    current: Choice?,
    label: (Choice) -> String,
    onPick: (Choice) -> Unit,
) {
  val space = BirdSpotterTheme.space
  FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space.snug),
      verticalArrangement = Arrangement.spacedBy(space.snug),
  ) {
    choices.forEach { choice ->
      Chip(
          text = label(choice),
          tone = if (choice == current) ChipTone.ANSWER else ChipTone.NEUTRAL,
          modifier = Modifier.clickable { onPick(choice) },
      )
    }
  }
}

/** The kit's verbs, wrapping — twelve to a section is the norm here, not one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlRow(content: @Composable () -> Unit) {
  val space = BirdSpotterTheme.space
  FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space.snug),
      verticalArrangement = Arrangement.spacedBy(space.snug),
  ) {
    content()
  }
}

/**
 * A small, squared control — set the way [ActionButton] is set but sized to sit twelve to a row
 * rather than one.
 */
@Composable
private fun ControlButton(title: String, onClick: () -> Unit) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type
  val shape = RoundedCornerShape(CardMetrics.CornerRadius)

  Text(
      text = title,
      style = type.label,
      color = colors.textPrimary,
      modifier =
          Modifier.clip(shape)
              .background(colors.paperRaised)
              .border(1.dp, colors.rule, shape)
              .clickable(onClick = onClick)
              .padding(horizontal = space.related, vertical = space.snug),
  )
}

/**
 * The battery, as a slider that reports when the thumb is let go — not on every tick, so the kit is
 * told once per gesture rather than a hundred times.
 */
@Composable
private fun BatterySlider(level: Int, onSet: (Int) -> Unit) {
  val colors = BirdSpotterTheme.colors
  val space = BirdSpotterTheme.space
  val type = BirdSpotterTheme.type
  var value by remember(level) { mutableFloatStateOf(level.toFloat()) }

  Column(verticalArrangement = Arrangement.spacedBy(space.tight)) {
    Row(modifier = Modifier.fillMaxWidth()) {
      Text(text = "Battery", style = type.headline, color = colors.textPrimary)
      Spacer(modifier = Modifier.weight(1f))
      Text(text = "${value.roundToInt()}%", style = type.data, color = colors.textSecondary)
    }
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onSet(value.roundToInt()) },
        valueRange = 0f..100f,
        colors =
            SliderDefaults.colors(
                thumbColor = colors.verdigris,
                activeTrackColor = colors.verdigris,
            ),
        modifier = Modifier.semantics { contentDescription = "Battery level" },
    )
  }
}
