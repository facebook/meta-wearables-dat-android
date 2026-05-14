/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.display

import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.display.types.DisplayState

/** Connection state for the DWA service on glasses. */
enum class DwaConnectionState {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
}

data class DisplayUIState(
    // Session
    val isSessionActive: Boolean = false,
    val isDisplayAttached: Boolean = false,
    val selectedDeviceId: DeviceIdentifier? = null,
    val connectionState: DwaConnectionState = DwaConnectionState.DISCONNECTED,
    val isPreparingDisplay: Boolean = false,

    // UI
    val isStartingSession: Boolean = false,
    val isStoppingSession: Boolean = false,
    val isDatAppUpdateRequired: Boolean = false,
    val snackbarMessage: String? = null,

    // Display capability
    val displayState: DisplayState? = null,
)
