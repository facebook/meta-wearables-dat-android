/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.ui.graphics.vector.ImageVector

/** Defines the available display sample apps. */
enum class SampleApp(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val route: String,
) {
  CAR_MAINTENANCE(
      titleRes = R.string.sample_car_maintenance,
      descriptionRes = R.string.sample_car_maintenance_desc,
      icon = Icons.Outlined.DirectionsCar,
      route = "car_maintenance",
  ),
}
