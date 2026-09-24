/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.domain.Permission
import com.meta.pixelandtexel.birdspotter.domain.PermissionStatus
import com.meta.pixelandtexel.birdspotter.domain.PermissionsController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Identify landing renders: the three permission statuses, and the single gate they open.
 * The page is all-or-nothing — until every permission is granted it is the access gate and nothing
 * else; once they all are, the gate gives way to the two ways in.
 */
data class IdentifyUiState(
    val cameraStatus: PermissionStatus = PermissionStatus.NOT_DETERMINED,
    val microphoneStatus: PermissionStatus = PermissionStatus.NOT_DETERMINED,
    val locationStatus: PermissionStatus = PermissionStatus.NOT_DETERMINED,
) {
  /**
   * True only when camera, mic, and location are all granted. The wizard needs location for its
   * where-stamp and the live flow needs camera and mic; the tab opens once it has all three, and
   * shows the access gate until then.
   */
  val allGranted: Boolean
    get() =
        cameraStatus == PermissionStatus.GRANTED &&
            microphoneStatus == PermissionStatus.GRANTED &&
            locationStatus == PermissionStatus.GRANTED

  fun status(permission: Permission): PermissionStatus =
      when (permission) {
        Permission.CAMERA -> cameraStatus
        Permission.MICROPHONE -> microphoneStatus
        Permission.LOCATION -> locationStatus
      }
}

/**
 * Drives the Identify landing: it reads permission status and re-reads it whenever the screen might
 * have gone stale (first appearance, and every return to the foreground — a trip to Settings is
 * exactly how a status flips under us).
 *
 * Requesting the system prompt is not here — that is the part that does not mirror, so the screen
 * owns it, through a Compose result contract. The view model only reads and routes to Settings.
 */
class IdentifyViewModel(private val permissions: PermissionsController) : ViewModel() {

  private val _uiState = MutableStateFlow(readStatuses())
  val uiState: StateFlow<IdentifyUiState> = _uiState.asStateFlow()

  /** Re-reads all three statuses from the OS. */
  fun refresh() {
    _uiState.value = readStatuses()
  }

  /** Sends the user to this app's page in Settings — the way back from a standing denial. */
  fun openSettings() = permissions.openAppSettings()

  private fun readStatuses() = IdentifyUiState(
      cameraStatus = permissions.status(Permission.CAMERA),
      microphoneStatus = permissions.status(Permission.MICROPHONE),
      locationStatus = permissions.status(Permission.LOCATION),
  )

  companion object {
    fun factory(permissions: PermissionsController): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(
              modelClass: Class<T>,
              extras: CreationExtras,
          ): T {
            @Suppress("UNCHECKED_CAST")
            return IdentifyViewModel(permissions) as T
          }
        }
  }
}
