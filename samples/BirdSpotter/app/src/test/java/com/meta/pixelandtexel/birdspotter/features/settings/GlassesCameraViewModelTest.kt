/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import com.meta.pixelandtexel.birdspotter.domain.CaptureQuality
import com.meta.pixelandtexel.birdspotter.domain.CaptureResolution
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesError
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The camera screen's three pieces of policy: **when the shutter may be pressed**, **what a
 * photograph is called on disk**, and **which of the two partings one failure gets.**
 *
 * The crossing itself is the SDK's and is not under test here — what is, is the seam this screen
 * adds around it: a press that cannot succeed is not offered, a file lands under a name that still
 * means something an hour later, and a dropped photograph does not send somebody off to fix a
 * connection that is fine.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class GlassesCameraViewModelTest {

  @Test
  fun theShutterIsOfferedOnlyOnceTheSessionHasStarted() {
    // Connecting is not connected: the capability refuses a capture it has not started,
    // and the refusal comes back so fast it reads on the log as an empty photograph.
    listOf(
        GlassesSessionState.STARTING,
        GlassesSessionState.PAUSED,
        GlassesSessionState.STOPPING,
        GlassesSessionState.STOPPED,
    )
        .forEach { state ->
          assertFalse(GlassesCameraUiState(isRunning = true, sessionState = state).canCapture)
        }

    val started = GlassesCameraUiState(
        isRunning = true,
        sessionState = GlassesSessionState.STARTED,
    )
    assertTrue(started.canCapture)
  }

  @Test
  fun theShutterIsNotOfferedWhileAPhotographIsStillCrossing() {
    // One at a time. A queue of captures is a queue of photographs nobody asked for by
    // the time they land.
    val crossing = GlassesCameraUiState(
        isRunning = true,
        sessionState = GlassesSessionState.STARTED,
        isCapturing = true,
    )

    assertFalse(crossing.canCapture)
  }

  @Test
  fun aDeniedGrantStillLetsTheShutterBePressed() {
    // The grant is read off a live link and can be answered in another app between one
    // press and the next. Refusing on a stale reading leaves a dead button; the row above
    // says what is wrong and the press is allowed to fail honestly.
    val denied = GlassesCameraUiState(
        isRunning = true,
        sessionState = GlassesSessionState.STARTED,
        cameraAccess = GlassesAccess.DENIED,
    )

    assertTrue(denied.canCapture)
  }

  @Test
  fun aPhotographIsNamedForTheSettingsItWasTakenAt() {
    // The name is what the share sheet shows and what lands in the gallery. Three shots
    // called photo-1, photo-2, photo-3 are three shots nobody can tell apart.
    assertEquals(
        "glasses-large-high-3.jpg",
        GlassesCameraViewModel.fileName(CaptureResolution.LARGE, CaptureQuality.HIGH, 3),
    )
    assertEquals(
        "glasses-small-low-1.jpg",
        GlassesCameraViewModel.fileName(CaptureResolution.SMALL, CaptureQuality.LOW, 1),
    )
  }

  @Test
  fun aPhotographIsNamedWithAnImageExtension() {
    // The extension is the whole reason the file has a name: it is what the share sheet
    // shows and what decides whether the gallery takes it.
    val name = GlassesCameraViewModel.fileName(CaptureResolution.FULL, CaptureQuality.MEDIUM, 12)

    assertTrue(name.endsWith(".jpg"))
  }

  @Test
  fun aDroppedCrossingDoesNotBlameTheConnection() {
    // The link is usually still up and the next press usually works. Telling somebody to
    // check their glasses over one dropped photograph sends them to fix nothing.
    val parting = GlassesCameraViewModel.captureParting(GlassesError.TransferFailed)

    assertTrue(parting.contains("Try again"))
    assertFalse(parting.contains("connected"))
  }

  @Test
  fun aShutterWithNoCameraUpSaysSo() {
    // The other of the two the app can tell apart: nothing to photograph through yet.
    val parting = GlassesCameraViewModel.captureParting(GlassesError.NotConnected)

    assertTrue(parting.contains("not up"))
  }

  @Test
  fun aSessionThatEndedOnAStaleFirmwarePointsAtTheMetaAiApp() {
    // The failure that lies: a pair in this state reports battery, wear and heat over the
    // link the whole time, so "check they are connected" is the one answer that helps
    // least.
    val parting = GlassesCameraViewModel.parting(GlassesError.GlassesUpdateRequired)

    assertTrue(parting.contains("firmware"))
  }
}
