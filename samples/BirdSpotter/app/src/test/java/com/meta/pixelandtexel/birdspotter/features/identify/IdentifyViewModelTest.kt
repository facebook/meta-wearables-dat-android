/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import com.meta.pixelandtexel.birdspotter.domain.Permission
import com.meta.pixelandtexel.birdspotter.domain.PermissionStatus
import com.meta.pixelandtexel.birdspotter.domain.PermissionsController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Identify tab's access gate: when the page is the permission wall and when it opens, and that
 * the view model reads status fresh — including the change made in Settings and brought back on the
 * next foreground.
 *
 * The gate rule is pure `IdentifyUiState`, so it needs no view model and this stays a plain JUnit
 * test — no Robolectric runner, no main-dispatcher rule; the read path drives the real
 * `IdentifyViewModel` over a `FakePermissions`. Requesting the system prompt is not tested here: it
 * is the platform plumbing the surface deliberately keeps off `PermissionsController`.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class IdentifyViewModelTest {

  // ── The gate ─────────────────────────────────────────────────────────────

  @Test
  fun allGranted_whenEveryPermissionGranted_isTrue() {
    val state = IdentifyUiState(
        cameraStatus = PermissionStatus.GRANTED,
        microphoneStatus = PermissionStatus.GRANTED,
        locationStatus = PermissionStatus.GRANTED,
    )

    assertTrue(state.allGranted)
  }

  @Test
  fun allGranted_whenLocationMissing_isFalse() {
    val state = IdentifyUiState(
        cameraStatus = PermissionStatus.GRANTED,
        microphoneStatus = PermissionStatus.GRANTED,
        locationStatus = PermissionStatus.NOT_DETERMINED,
    )

    assertFalse(state.allGranted)
  }

  @Test
  fun allGranted_whenCameraMissing_isFalse() {
    val state = IdentifyUiState(
        cameraStatus = PermissionStatus.DENIED,
        microphoneStatus = PermissionStatus.GRANTED,
        locationStatus = PermissionStatus.GRANTED,
    )

    assertFalse(state.allGranted)
  }

  @Test
  fun allGranted_whenMicrophoneMissing_isFalse() {
    val state = IdentifyUiState(
        cameraStatus = PermissionStatus.GRANTED,
        microphoneStatus = PermissionStatus.NOT_DETERMINED,
        locationStatus = PermissionStatus.GRANTED,
    )

    assertFalse(state.allGranted)
  }

  // ── Reading status ───────────────────────────────────────────────────────

  @Test
  fun refresh_readsEachPermissionsStatus() {
    val permissions = FakePermissions(
        camera = PermissionStatus.GRANTED,
        microphone = PermissionStatus.DENIED,
        location = PermissionStatus.NOT_DETERMINED,
    )
    val model = IdentifyViewModel(permissions)

    assertEquals(PermissionStatus.GRANTED, model.uiState.value.cameraStatus)
    assertEquals(PermissionStatus.DENIED, model.uiState.value.microphoneStatus)
    assertEquals(PermissionStatus.NOT_DETERMINED, model.uiState.value.locationStatus)
  }

  @Test
  fun refresh_picksUpAStatusThatChangedWhileAway() {
    val permissions = FakePermissions(
        camera = PermissionStatus.GRANTED,
        microphone = PermissionStatus.GRANTED,
        location = PermissionStatus.NOT_DETERMINED,
    )
    val model = IdentifyViewModel(permissions)
    assertFalse(model.uiState.value.allGranted)

    // The user grants location in Settings and returns; the screen calls refresh on resume.
    permissions.location = PermissionStatus.GRANTED
    model.refresh()

    assertTrue(model.uiState.value.allGranted)
  }

  @Test
  fun openSettings_isRoutedToTheController() {
    val permissions = FakePermissions()
    val model = IdentifyViewModel(permissions)

    model.openSettings()

    assertTrue(permissions.openedSettings)
  }
}

/**
 * The first hand-written fake in the suite: a `PermissionsController` whose answers are set by the
 * test and can change between reads.
 */
private class FakePermissions(
    var camera: PermissionStatus = PermissionStatus.NOT_DETERMINED,
    var microphone: PermissionStatus = PermissionStatus.NOT_DETERMINED,
    var location: PermissionStatus = PermissionStatus.NOT_DETERMINED,
) : PermissionsController {
  var openedSettings = false
    private set

  override fun status(permission: Permission): PermissionStatus =
      when (permission) {
        Permission.CAMERA -> camera
        Permission.MICROPHONE -> microphone
        Permission.LOCATION -> location
      }

  override fun openAppSettings() {
    openedSettings = true
  }
}
