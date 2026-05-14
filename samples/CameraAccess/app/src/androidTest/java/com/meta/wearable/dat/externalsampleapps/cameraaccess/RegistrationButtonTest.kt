/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that tapping the "Connect my glasses" registration button does not crash the app.
 *
 * The mwdat-core SDK uses FragmentActivity internally for its registration flow
 * (RegistrationManagerImpl.launchIntentForResult), but does not bundle it in its AAR. If
 * androidx.fragment is missing from the app's dependencies, the app crashes at runtime with
 * NoClassDefFoundError when the user taps the register button.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RegistrationButtonTest {

  companion object {
    private const val TAG = "RegistrationButtonTest"
  }

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun setup() {
    grantPermissions()
  }

  @Test
  fun clickingConnectMyGlassesDoesNotCrash() {
    val buttonText = composeTestRule.activity.getString(R.string.register_button_title)
    composeTestRule.onNodeWithText(buttonText).performClick()

    // Verify the app is still alive after the registration coroutine executes.
    // If FragmentActivity is missing, the app crashes and this assertion fails.
    composeTestRule.onNodeWithText(buttonText).assertExists()
  }

  private fun grantPermissions() {
    grantPermission("android.permission.BLUETOOTH")
    grantPermission("android.permission.BLUETOOTH_CONNECT")
    grantPermission("android.permission.CAMERA")
    grantPermission("android.permission.INTERNET")
  }

  private fun grantPermission(permission: String) {
    val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    try {
      InstrumentationRegistry.getInstrumentation()
          .uiAutomation
          .executeShellCommand("pm grant $packageName $permission")
      Log.d(TAG, "Granted permission: $permission")
    } catch (e: IOException) {
      Log.e(TAG, "Failed to grant permission", e)
    }
  }
}
