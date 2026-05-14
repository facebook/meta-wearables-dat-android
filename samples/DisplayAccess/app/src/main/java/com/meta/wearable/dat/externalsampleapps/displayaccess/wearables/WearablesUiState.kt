/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.wearables

import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState

data class WearablesUiState(
    val registrationState: RegistrationState = RegistrationState.UNAVAILABLE,
    val devices: Set<DeviceIdentifier> = emptySet(),
    val devicesMetadata: Map<DeviceIdentifier, Device> = emptyMap(),
    val isFirmwareUpdateRequired: Boolean = false,
) {
  val isRegistered: Boolean
    get() = registrationState == RegistrationState.REGISTERED

  val hasConnectedDevice: Boolean
    get() =
        devicesMetadata.values.any {
          it.linkState == com.meta.wearable.dat.core.types.LinkState.CONNECTED
        }
}
