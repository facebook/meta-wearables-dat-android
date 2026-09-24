/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meta.wearable.dat.externalsampleapps.displayaccess.R
import com.meta.wearable.dat.externalsampleapps.displayaccess.SampleApp
import com.meta.wearable.dat.externalsampleapps.displayaccess.wearables.DeveloperPreviewMode
import com.meta.wearable.dat.mockdevice.api.display.MockDisplayKit
import kotlinx.coroutines.delay

private val SampleIconBackgroundColor = Color(0xFF751C19)
private val SampleButtonStartColor = Color(0xFF597FF6)
private val SampleButtonEndColor = Color(0xFF2A50BA)
private val SampleButtonDisabledStartColor = Color(0xFFD4DAE4)
private val SampleButtonDisabledEndColor = Color(0xFFC5CDD8)
private val SampleButtonDisabledTextColor = Color(0xFFF7F8FA)
private val DeveloperPreviewBackground = Color(0xFFF2F2F7)
private val DeveloperPreviewSelectionColor = Color.White
private val DeveloperPreviewActiveColor = Color(0xFF2E7D32)
private const val PREVIEW_DISPLAY_ASPECT_RATIO = 1f

@Composable
fun SamplesListScreen(
    isTryItEnabled: Boolean,
    developerPreviewMode: DeveloperPreviewMode?,
    isDeveloperPreviewBusy: Boolean,
    hasLoadedDeveloperPreview: Boolean,
    chromePreviewCommand: String?,
    chromePreviewUrl: String?,
    developerPreviewErrorMessage: String?,
    onSampleSelected: (SampleApp) -> Unit,
    onStartDeveloperPreview: (DeveloperPreviewMode) -> Unit,
    onStopDeveloperPreview: () -> Unit,
    modifier: Modifier = Modifier,
    previewDisplayKit: MockDisplayKit? = null,
) {
  val sample = SampleApp.CAR_MAINTENANCE
  var isDeveloperPreviewPresented by rememberSaveable { mutableStateOf(false) }
  var selectedDeveloperPreviewMode by rememberSaveable {
    mutableStateOf(DeveloperPreviewMode.CHROME)
  }

  SampleContent(
      sample = sample,
      isTryItEnabled = isTryItEnabled,
      isDeveloperPreviewBusy = isDeveloperPreviewBusy,
      onOpenDeveloperPreview = {
        selectedDeveloperPreviewMode = developerPreviewMode ?: DeveloperPreviewMode.CHROME
        isDeveloperPreviewPresented = true
      },
      onTryIt = { onSampleSelected(sample) },
      modifier = modifier,
  )

  if (isDeveloperPreviewPresented) {
    DeveloperPreviewSheet(
        selectedMode = selectedDeveloperPreviewMode,
        activeMode = developerPreviewMode,
        isBusy = isDeveloperPreviewBusy,
        hasLoadedPreview = hasLoadedDeveloperPreview,
        chromePreviewCommand = chromePreviewCommand,
        chromePreviewUrl = chromePreviewUrl,
        errorMessage = developerPreviewErrorMessage,
        previewDisplayKit = previewDisplayKit,
        onSelectMode = { mode ->
          if (mode != selectedDeveloperPreviewMode) {
            if (developerPreviewMode != null) onStopDeveloperPreview()
            selectedDeveloperPreviewMode = mode
          }
        },
        onStartPreview = { onStartDeveloperPreview(selectedDeveloperPreviewMode) },
        onDismiss = {
          if (developerPreviewMode != null) onStopDeveloperPreview()
          isDeveloperPreviewPresented = false
        },
    )
  }
}

@Composable
private fun SampleContent(
    sample: SampleApp,
    isTryItEnabled: Boolean,
    isDeveloperPreviewBusy: Boolean,
    onOpenDeveloperPreview: () -> Unit,
    onTryIt: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier.fillMaxSize().padding(start = 24.dp, top = 48.dp, end = 24.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    SampleIcon(sample = sample, size = 72.dp, iconSize = 32.dp)

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(sample.titleRes),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(sample.descriptionRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.weight(1f))

    DeveloperPreviewEntry(
        isBusy = isDeveloperPreviewBusy,
        onClick = onOpenDeveloperPreview,
    )

    Spacer(modifier = Modifier.height(16.dp))

    TryItButton(isEnabled = isTryItEnabled, onClick = onTryIt)
  }
}

@Composable
private fun DeveloperPreviewEntry(
    isBusy: Boolean,
    onClick: () -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(DeveloperPreviewBackground)
              .clickable(enabled = !isBusy, onClick = onClick)
              .padding(18.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = Icons.Filled.Build,
        contentDescription = null,
        tint = SampleButtonStartColor,
        modifier = Modifier.size(28.dp),
    )

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = stringResource(R.string.developer_preview_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
      Text(
          text = stringResource(R.string.developer_preview_description),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 3.dp),
      )
    }

    Spacer(modifier = Modifier.width(12.dp))

    if (isBusy) {
      CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          strokeWidth = 2.dp,
      )
    } else {
      Icon(
          imageVector = Icons.Filled.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperPreviewSheet(
    selectedMode: DeveloperPreviewMode,
    activeMode: DeveloperPreviewMode?,
    isBusy: Boolean,
    hasLoadedPreview: Boolean,
    chromePreviewCommand: String?,
    chromePreviewUrl: String?,
    errorMessage: String?,
    previewDisplayKit: MockDisplayKit?,
    onSelectMode: (DeveloperPreviewMode) -> Unit,
    onStartPreview: () -> Unit,
    onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val clipboardManager =
      remember(context) { context.getSystemService(ClipboardManager::class.java) }
  val clipboardLabel = stringResource(R.string.developer_preview_clipboard_label)
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var copiedValue by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(copiedValue) {
    if (copiedValue != null) {
      delay(2_000)
      copiedValue = null
    }
  }

  ModalBottomSheet(
      onDismissRequest = { if (!isBusy) onDismiss() },
      sheetState = sheetState,
      containerColor = DeveloperPreviewBackground,
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = stringResource(R.string.developer_preview_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, enabled = !isBusy) {
          Text(stringResource(R.string.done_button))
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      DeveloperPreviewModeSelector(
          selectedMode = selectedMode,
          enabled = !isBusy,
          onSelectMode = onSelectMode,
      )

      Spacer(modifier = Modifier.height(20.dp))

      DeveloperPreviewStage(
          selectedMode = selectedMode,
          chromePreviewCommand = chromePreviewCommand,
          chromePreviewUrl = chromePreviewUrl,
          previewDisplayKit = previewDisplayKit,
          copiedValue = copiedValue,
          onCopy = { value ->
            clipboardManager?.setPrimaryClip(
                ClipData.newPlainText(clipboardLabel, value),
            )
            copiedValue = value
          },
      )

      Spacer(modifier = Modifier.height(20.dp))

      DeveloperPreviewAction(
          selectedMode = selectedMode,
          activeMode = activeMode,
          isBusy = isBusy,
          hasLoadedPreview = hasLoadedPreview,
          errorMessage = errorMessage,
          onStartPreview = onStartPreview,
      )
    }
  }
}

@Composable
private fun DeveloperPreviewModeSelector(
    selectedMode: DeveloperPreviewMode,
    enabled: Boolean,
    onSelectMode: (DeveloperPreviewMode) -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFE4E4E9))
              .padding(4.dp),
  ) {
    DeveloperPreviewModeButton(
        text = stringResource(R.string.chrome_preview_mode),
        selected = selectedMode == DeveloperPreviewMode.CHROME,
        enabled = enabled,
        onClick = { onSelectMode(DeveloperPreviewMode.CHROME) },
        modifier = Modifier.weight(1f),
    )
    DeveloperPreviewModeButton(
        text = stringResource(R.string.in_app_preview_mode),
        selected = selectedMode == DeveloperPreviewMode.IN_APP,
        enabled = enabled,
        onClick = { onSelectMode(DeveloperPreviewMode.IN_APP) },
        modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun DeveloperPreviewModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .clip(RoundedCornerShape(9.dp))
              .background(
                  if (selected) DeveloperPreviewSelectionColor else Color.Transparent,
              )
              .clickable(enabled = enabled, onClick = onClick)
              .padding(vertical = 10.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color =
            if (enabled) {
              MaterialTheme.colorScheme.onSurface
            } else {
              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
    )
  }
}

@Composable
private fun DeveloperPreviewStage(
    selectedMode: DeveloperPreviewMode,
    chromePreviewCommand: String?,
    chromePreviewUrl: String?,
    previewDisplayKit: MockDisplayKit?,
    copiedValue: String?,
    onCopy: (String) -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .aspectRatio(PREVIEW_DISPLAY_ASPECT_RATIO)
              .clip(RoundedCornerShape(24.dp))
              .background(Color.White)
              .padding(20.dp),
  ) {
    when (selectedMode) {
      DeveloperPreviewMode.CHROME ->
          ChromePreviewStage(
              command = chromePreviewCommand,
              url = chromePreviewUrl,
              copiedValue = copiedValue,
              onCopy = onCopy,
          )
      DeveloperPreviewMode.IN_APP -> InAppPreviewStage(previewDisplayKit)
    }
  }
}

@Composable
private fun ChromePreviewStage(
    command: String?,
    url: String?,
    copiedValue: String?,
    onCopy: (String) -> Unit,
) {
  PreviewStageTitle(
      text = stringResource(R.string.preview_in_chrome_title),
      isChrome = true,
  )

  Spacer(modifier = Modifier.height(16.dp))

  Column(
      modifier = Modifier.padding(start = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    InstructionRow(
        number = 1,
        text = stringResource(R.string.chrome_preview_step_start_server),
    )
    InstructionRow(
        number = 2,
        text = stringResource(R.string.chrome_preview_step_adb_forward),
    ) {
      PreviewValue(
          value = command,
          copiedValue = copiedValue,
          onCopy = onCopy,
      )
    }
    InstructionRow(
        number = 3,
        text = stringResource(R.string.chrome_preview_step_open_link),
    ) {
      PreviewValue(
          value = url,
          copiedValue = copiedValue,
          onCopy = onCopy,
      )
    }
    InstructionRow(
        number = 4,
        text = stringResource(R.string.chrome_preview_step_enable_extension),
    )
  }
}

@Composable
private fun InAppPreviewStage(previewDisplayKit: MockDisplayKit?) {
  Column(modifier = Modifier.fillMaxSize()) {
    PreviewStageTitle(
        text = stringResource(R.string.in_app_simulator_title),
        isChrome = false,
    )

    Spacer(modifier = Modifier.height(16.dp))

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
    ) {
      if (previewDisplayKit != null) {
        val side = minOf(maxWidth, maxHeight)
        DisplayPreviewPane(
            displayKit = previewDisplayKit,
            modifier = Modifier.size(side).clip(RoundedCornerShape(18.dp)),
        )
      } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
              imageVector = Icons.Filled.PhoneAndroid,
              contentDescription = null,
              tint = SampleButtonStartColor,
              modifier = Modifier.size(42.dp),
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
              text = stringResource(R.string.in_app_preview_empty_state),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

@Composable
private fun PreviewStageTitle(
    text: String,
    isChrome: Boolean,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
        imageVector = if (isChrome) Icons.Filled.DesktopWindows else Icons.Filled.PhoneAndroid,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun InstructionRow(
    number: Int,
    text: String,
    detail: @Composable () -> Unit = {},
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
  ) {
    Text(
        text = "$number.",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(24.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = text,
          style = MaterialTheme.typography.bodySmall,
      )
      detail()
    }
  }
}

@Composable
private fun PreviewValue(
    value: String?,
    copiedValue: String?,
    onCopy: (String) -> Unit,
) {
  if (value == null) {
    Text(
        text = stringResource(R.string.preview_value_unavailable),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    return
  }

  val isCopied = copiedValue == value
  Row(
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    TextButton(onClick = { onCopy(value) }) {
      Icon(
          imageVector = if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
          text =
              stringResource(
                  if (isCopied) R.string.copied_button else R.string.copy_button,
              ),
      )
    }
  }
}

@Composable
private fun DeveloperPreviewAction(
    selectedMode: DeveloperPreviewMode,
    activeMode: DeveloperPreviewMode?,
    isBusy: Boolean,
    hasLoadedPreview: Boolean,
    errorMessage: String?,
    onStartPreview: () -> Unit,
) {
  if (hasLoadedPreview && activeMode == selectedMode) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DeveloperPreviewActiveColor.copy(alpha = 0.12f))
                .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = DeveloperPreviewActiveColor,
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
          text =
              stringResource(
                  if (selectedMode == DeveloperPreviewMode.CHROME) {
                    R.string.chrome_preview_ready
                  } else {
                    R.string.in_app_preview_ready
                  },
              ),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = DeveloperPreviewActiveColor,
      )
    }
    return
  }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (errorMessage != null) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Button(
        onClick = onStartPreview,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SampleButtonStartColor),
        shape = RoundedCornerShape(12.dp),
    ) {
      if (isBusy) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = Color.White,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
      }
      Text(
          text =
              stringResource(
                  when {
                    isBusy && selectedMode == DeveloperPreviewMode.CHROME ->
                        R.string.starting_preview_server
                    isBusy -> R.string.starting_in_app_preview
                    errorMessage != null -> R.string.try_again_button
                    selectedMode == DeveloperPreviewMode.CHROME ->
                        R.string.start_preview_server_button
                    else -> R.string.preview_in_app_button
                  },
              ),
          modifier = Modifier.padding(vertical = 6.dp),
      )
    }
  }
}

@Composable
private fun SampleIcon(
    sample: SampleApp,
    size: Dp,
    iconSize: Dp,
) {
  Box(
      modifier =
          Modifier.size(size).clip(RoundedCornerShape(22.dp)).background(SampleIconBackgroundColor),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector = sample.icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(iconSize),
    )
  }
}

@Composable
private fun TryItButton(
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val buttonBrush =
      Brush.horizontalGradient(
          colors =
              if (isEnabled) {
                listOf(SampleButtonStartColor, SampleButtonEndColor)
              } else {
                listOf(SampleButtonDisabledStartColor, SampleButtonDisabledEndColor)
              },
      )

  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(CircleShape)
              .background(brush = buttonBrush)
              .clickable(enabled = isEnabled, onClick = onClick)
              .padding(vertical = 20.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = stringResource(R.string.try_it_button),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (isEnabled) Color.White else SampleButtonDisabledTextColor,
    )
  }
}

@SuppressLint("JetpackComposeAndroidView")
@Composable
private fun DisplayPreviewPane(
    displayKit: MockDisplayKit,
    modifier: Modifier = Modifier,
) {
  key(displayKit) {
    AndroidView(
        factory = { context -> displayKit.createPreviewView(context) },
        modifier = modifier.background(Color.Black),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplePlaceholderScreen(
    sample: SampleApp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
        title = { Text(stringResource(sample.titleRes), fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White,
                titleContentColor = Color(0xFF1A1A1A),
            ),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
          imageVector = sample.icon,
          contentDescription = null,
          tint = Color(0xFF666666),
          modifier = Modifier.size(64.dp),
      )
      Spacer(modifier = Modifier.height(16.dp))
      Text(
          text = stringResource(sample.titleRes),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
          text = "Coming soon",
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFF999999),
      )
    }
  }
}
