/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.mockdevice

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.meta.pixelandtexel.birdspotter.domain.MockGlassesModel

/**
 * Where the floating mock button was left on the screen, as fractions of the screen's width and
 * height — so the pin survives a rotation and a phone with a different screen.
 *
 * @property x 0 at the left edge, 1 at the right.
 * @property y 0 at the top edge, 1 at the bottom.
 */
data class MockDeviceButtonPosition(val x: Double, val y: Double) {
  companion object {
    /**
     * Where the button starts: low on the right, where a thumb finds it and where it is over
     * nothing a screen puts its own controls on.
     */
    val Initial = MockDeviceButtonPosition(x = 0.9, y = 0.78)
  }
}

/**
 * How the mock is set: whether it stands in for the real SDK, which model it fakes, and where its
 * button was pinned.
 *
 * SharedPreferences, for the same reason
 * [com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsSettingsStore] is — this is how
 * somebody has their instrument set, not something the app made. The kit itself remembers nothing
 * between launches, so this store is what lets a relaunch come back up simulated.
 */
class MockDeviceSettingsStore(private val prefs: SharedPreferences) {

  /**
   * Whether the kit should be standing in for the real SDK. Off by default: the real glasses are
   * the app's whole point, and the mock is the thing you reach for without them.
   */
  var isEnabled: Boolean
    get() = prefs.getBoolean(KeyEnabled, false)
    set(value) = prefs.edit { putBoolean(KeyEnabled, value) }

  /** The model paired last, and the one a relaunch pairs again. */
  var model: MockGlassesModel
    get() =
        prefs.getString(KeyModel, null)?.let { stored ->
          MockGlassesModel.entries.firstOrNull { it.name == stored }
        } ?: MockGlassesModel.RAY_BAN_META
    set(value) = prefs.edit { putString(KeyModel, value.name) }

  /** Where the floating button was pinned. */
  var buttonPosition: MockDeviceButtonPosition
    get() {
      // Presence first, because a missing float reads as its default, and zero here
      // would pin the button in the corner.
      if (!prefs.contains(KeyButtonX) || !prefs.contains(KeyButtonY)) {
        return MockDeviceButtonPosition.Initial
      }
      return MockDeviceButtonPosition(
          x = prefs.getFloat(KeyButtonX, 0f).toDouble(),
          y = prefs.getFloat(KeyButtonY, 0f).toDouble(),
      )
    }
    set(value) = prefs.edit {
      putFloat(KeyButtonX, value.x.toFloat())
      putFloat(KeyButtonY, value.y.toFloat())
    }

  companion object {
    private const val KeyEnabled = "mockDevice.isEnabled"
    private const val KeyModel = "mockDevice.model"
    private const val KeyButtonX = "mockDevice.buttonX"
    private const val KeyButtonY = "mockDevice.buttonY"

    /** Opens the store over the app's preferences. */
    fun open(context: Context) = MockDeviceSettingsStore(
        context.getSharedPreferences("mock_device_settings", Context.MODE_PRIVATE),
    )
  }
}
