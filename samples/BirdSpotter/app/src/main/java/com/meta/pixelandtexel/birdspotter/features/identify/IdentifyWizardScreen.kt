/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.data.catalog.BirdBehavior
import com.meta.pixelandtexel.birdspotter.data.catalog.PlumageColor
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import java.time.LocalDate

/**
 * The step-by-step wizard: three questions, then the birds that match.
 *
 * One destination, not four. The stations share their answers (goBack must not forget them), the
 * progress header counts them as one flow, and system back walks the stations before it leaves the
 * wizard — all of which is one screen's internal state, not a back stack. `Route.IdentifyWizard`
 * stays a single entry on Identify's own stack.
 *
 * The where-stamp rides along invisibly: a [LaunchedEffect] asks the location provider for one fix
 * as the wizard appears, so the coordinate is usually in hand by the time a bird is claimed.
 */
@Composable
fun IdentifyWizardScreen(
    onClose: () -> Unit,
    onOpenBird: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val application = LocalContext.current.applicationContext as BirdSpotterApplication
  val viewModel: IdentifyWizardViewModel = viewModel(
      factory =
          IdentifyWizardViewModel.factory(
              birdCatalog = application.container.birdCatalogRepository,
              journal = application.container.journalRepository,
              locationProvider = application.container.locationProvider,
          ),
  )
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // The where-stamp: one fix, started as the wizard opens. Location permission is the Identify
  // tab's entry gate, so this is expected to succeed by the time a bird is claimed.
  LaunchedEffect(Unit) { viewModel.captureLocation() }

  IdentifyWizardScreen(
      uiState = uiState,
      onChooseSizeClass = viewModel::chooseSizeClass,
      onToggleColor = viewModel::toggleColor,
      onChooseBehavior = viewModel::chooseBehavior,
      onAdvance = viewModel::advance,
      onBack = { if (!viewModel.goBack()) onClose() },
      onClose = onClose,
      onOpenBird = onOpenBird,
      onSaveSighting = viewModel::saveSighting,
      modifier = modifier,
  )
}

/**
 * The stateless half, so Previews and UI tests can drive every station without a database. The
 * lambda list is long because the wizard asks a lot; bundling them into a listener interface would
 * only move the length somewhere less visible.
 */
@Composable
fun IdentifyWizardScreen(
    uiState: IdentifyWizardUiState,
    onChooseSizeClass: (Int) -> Unit,
    onToggleColor: (PlumageColor) -> Unit,
    onChooseBehavior: (BirdBehavior) -> Unit,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenBird: (String) -> Unit,
    onSaveSighting: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  // The system gesture walks the stations exactly like the bar's chevron; only the first
  // station lets it fall through to navigation (see the stateful wrapper).
  BackHandler(onBack = onBack)

  val space = BirdSpotterTheme.space
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(BirdSpotterTheme.colors.paper)
              .windowInsetsPadding(WindowInsets.statusBars),
  ) {
    WizardBar(step = uiState.step, onBack = onBack, onClose = onClose)

    uiState.step.question?.let { question ->
      Text(
          text = question,
          style = BirdSpotterTheme.type.display,
          color = BirdSpotterTheme.colors.textPrimary,
          modifier =
              Modifier.padding(horizontal = space.gutter)
                  .padding(top = space.related, bottom = space.section),
      )
    }

    Box(Modifier.weight(1f)) {
      when (uiState.step) {
        IdentifyWizardStep.SIZE ->
            WizardSizeStep(
                sizeClass = uiState.sizeClass,
                onChooseSizeClass = onChooseSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )

        IdentifyWizardStep.COLORS ->
            WizardColorsStep(
                colors = uiState.colors,
                onToggleColor = onToggleColor,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )

        IdentifyWizardStep.BEHAVIOR ->
            WizardBehaviorStep(
                behavior = uiState.behavior,
                onChooseBehavior = onChooseBehavior,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )

        IdentifyWizardStep.RESULTS ->
            WizardResultsStep(
                coordinate = uiState.coordinate,
                spottedOn = uiState.spottedOn,
                candidates = uiState.candidates,
                savingSpeciesId = uiState.savingSpeciesId,
                savedSpeciesId = uiState.savedSpeciesId,
                saveError = uiState.saveError,
                onSaveSighting = onSaveSighting,
                onOpenBird = onOpenBird,
            )
      }
    }

    if (uiState.step.hasNextButton) {
      NextButton(
          enabled = uiState.canAdvance,
          onClick = onAdvance,
          modifier =
              Modifier.padding(horizontal = space.gutter)
                  .padding(top = space.separate, bottom = space.separate),
      )
    }
  }
}

/** The station's question, or null on the results list — which titles the bar instead. */
private val IdentifyWizardStep.question: String?
  get() =
      when (this) {
        IdentifyWizardStep.SIZE -> "What size was the bird?"
        IdentifyWizardStep.COLORS -> "What were the main colors?"
        IdentifyWizardStep.BEHAVIOR -> "Was the bird… ?"
        IdentifyWizardStep.RESULTS -> null
      }

/**
 * Results advances by choosing a bird, not by Next — every other station earns the pinned button.
 */
private val IdentifyWizardStep.hasNextButton: Boolean
  get() =
      when (this) {
        IdentifyWizardStep.RESULTS -> false
        else -> true
      }

// ── Chrome ─────────────────────────────────────────────────────────────────

private val BarHeight = 56.dp
private val ControlSize = 48.dp

/**
 * Chevron, progress, close. The chevron is absent on the first station — there is nothing inside
 * the wizard to go back to, and showing a control that closes the flow while dressed as "back"
 * would teach the wrong lesson.
 */
@Composable
private fun WizardBar(
    step: IdentifyWizardStep,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .height(BarHeight)
              .padding(horizontal = BirdSpotterTheme.space.snug),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (step == IdentifyWizardStep.SIZE) {
      Spacer(Modifier.width(ControlSize))
    } else {
      IconButton(onClick = onBack, modifier = Modifier.size(ControlSize)) {
        Icon(
            painter = glyph(BirdSpotterTheme.glyphs.back),
            contentDescription = "Back",
            tint = BirdSpotterTheme.colors.textPrimary,
        )
      }
    }

    Text(
        text =
            when (step) {
              IdentifyWizardStep.RESULTS -> "Results"
              else -> "${step.questionNumber} of 3"
            },
        style = BirdSpotterTheme.type.headline,
        color = BirdSpotterTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f),
    )

    IconButton(onClick = onClose, modifier = Modifier.size(ControlSize)) {
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.close),
          contentDescription = "Close",
          tint = BirdSpotterTheme.colors.textPrimary,
      )
    }
  }
}

/**
 * The one filled button in the flow. Verdigris — the accent for "the thing the page is about" — and
 * squared to the card radius, because this app's controls are plates, not pills.
 */
@Composable
private fun NextButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(3.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = BirdSpotterTheme.colors.verdigris,
              contentColor = BirdSpotterTheme.colors.paperRaised,
              disabledContainerColor = BirdSpotterTheme.colors.rule,
              disabledContentColor = BirdSpotterTheme.colors.textFaint,
          ),
      modifier = modifier.fillMaxWidth().height(52.dp),
  ) {
    Text(text = "Next", style = BirdSpotterTheme.type.headline)
  }
}

// ── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun IdentifyWizardSizePreview() {
  BirdSpotterTheme {
    IdentifyWizardScreen(
        uiState =
            IdentifyWizardUiState(
                step = IdentifyWizardStep.SIZE,
                spottedOn = LocalDate.of(2026, 7, 24),
            ),
        onChooseSizeClass = {},
        onToggleColor = {},
        onChooseBehavior = {},
        onAdvance = {},
        onBack = {},
        onClose = {},
        onOpenBird = {},
        onSaveSighting = {},
    )
  }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IdentifyWizardColorsPreview() {
  BirdSpotterTheme(darkTheme = true) {
    IdentifyWizardScreen(
        uiState =
            IdentifyWizardUiState(
                step = IdentifyWizardStep.COLORS,
                spottedOn = LocalDate.of(2026, 7, 24),
                sizeClass = 4,
            ),
        onChooseSizeClass = {},
        onToggleColor = {},
        onChooseBehavior = {},
        onAdvance = {},
        onBack = {},
        onClose = {},
        onOpenBird = {},
        onSaveSighting = {},
    )
  }
}
