/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.meta.pixelandtexel.birdspotter.domain.Permission
import com.meta.pixelandtexel.birdspotter.domain.PermissionStatus
import com.meta.pixelandtexel.birdspotter.domain.PermissionsController

/**
 * The [PermissionsController] the app ships: `checkSelfPermission` for status, and the app's own
 * details page in Settings for the way back.
 *
 * The mirror is [PermissionsController] — status and the Settings escape hatch — not this class.
 *
 * Requesting the system prompt is **not** on the interface (see its doc); on Android it is a
 * Compose result launcher in the Identify screen, which reuses [androidPermissions] to know what to
 * ask for. This divergence is one the architecture rules allow by name: the shape is shared, the
 * way each platform raises the prompt is not.
 */
class SystemPermissionsController(context: Context) : PermissionsController {

  private val appContext = context.applicationContext

  override fun status(permission: Permission): PermissionStatus {
    val granted =
        androidPermissions(permission).any {
          ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    // Android cannot tell "never asked" from "denied" without an Activity, and the Identify
    // tab does not need the distinction (see PermissionStatus). Not-granted reads as
    // NOT_DETERMINED; the screen's launcher escalates to Settings when a prompt changes nothing.
    return if (granted) PermissionStatus.GRANTED else PermissionStatus.NOT_DETERMINED
  }

  override fun openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", appContext.packageName, null),
    )
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
  }

  companion object {
    /** The manifest permissions each [Permission] maps to — location accepts fine or coarse. */
    fun androidPermissions(permission: Permission): List<String> =
        when (permission) {
          Permission.CAMERA -> listOf(Manifest.permission.CAMERA)
          Permission.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
          Permission.LOCATION ->
              listOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION,
              )
        }
  }
}
