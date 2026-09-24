/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// CameraAccess Sample App - Main Activity
//
// This is the main entry point for the CameraAccess sample application that demonstrates how to use
// the Meta Wearables Device Access Toolkit (DAT) to:
// - Initialize the DAT SDK
// - Handle device permissions (Bluetooth, Internet)
// - Request camera permissions from wearable devices (Ray-Ban Meta glasses)
// - Stream video and capture photos from connected wearable devices

package com.meta.wearable.dat.externalsampleapps.cameraaccess

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.INTERNET
import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.CameraAccessScaffold
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
  companion object {
    private const val TAG = "CameraAccessMain"
    // Intent boolean extra that activates the MockDevice test server for out-of-process MCP
    // control.
    private const val EXTRA_UI_TESTING = "mwdat_ui_testing"
    // Fixed loopback port the test server binds so the MCP host can reach it with a
    // deterministic `adb forward`. Must match _DEFAULT_ANDROID_TEST_SERVER_PORT in the
    // plugin's mcp/target_resolver.py.
    private const val MOCK_TEST_SERVER_PORT = 8237

    // Required Android permissions for the DAT SDK to function properly
    val PERMISSIONS: Array<String> = arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, INTERNET)
  }

  val viewModel: WearablesViewModel by viewModels()

  private val permissionCheckLauncher =
      registerForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
        viewModel.onPermissionsResult(permissionsResult) {
          // Initialize the DAT SDK once the permissions are granted
          // This is REQUIRED before using any Wearables APIs
          Wearables.initialize(this)
        }
      }

  private var permissionContinuation: CancellableContinuation<PermissionStatus>? = null
  private val permissionMutex = Mutex()
  // Requesting wearable device permissions via the Meta AI app
  private val permissionsResultLauncher =
      registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        val permissionStatus = result.getOrDefault(PermissionStatus.Denied)
        permissionContinuation?.resume(permissionStatus)
        permissionContinuation = null
      }

  // Convenience method to make a permission request in a sequential manner
  // Uses a Mutex to ensure requests are processed one at a time, preventing race conditions
  suspend fun requestWearablesPermission(permission: Permission): PermissionStatus {
    return permissionMutex.withLock {
      suspendCancellableCoroutine { continuation ->
        permissionContinuation = continuation
        continuation.invokeOnCancellation { permissionContinuation = null }
        permissionsResultLauncher.launch(permission)
      }
    }
  }

  private var audioPermissionContinuation: CancellableContinuation<Boolean>? = null
  // Phone microphone permission, requested in context when recording with sound-in-video on.
  private val recordAudioPermissionLauncher =
      registerForActivityResult(RequestPermission()) { granted ->
        audioPermissionContinuation?.resume(granted)
        audioPermissionContinuation = null
      }

  // Requests RECORD_AUDIO for sound-in-video. Returns true if granted (already or just now); false
  // if denied, so recording can proceed video-only instead of being blocked.
  suspend fun requestRecordAudioPermission(): Boolean {
    if (
        ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    ) {
      return true
    }
    return permissionMutex.withLock {
      suspendCancellableCoroutine { continuation ->
        audioPermissionContinuation = continuation
        continuation.invokeOnCancellation { audioPermissionContinuation = null }
        recordAudioPermissionLauncher.launch(RECORD_AUDIO)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    maybeStartMockDeviceTestServer()
    enableEdgeToEdge()
    setContent {
      CameraAccessScaffold(
          viewModel = viewModel,
          onRequestWearablesPermission = ::requestWearablesPermission,
          onRequestRecordAudioPermission = ::requestRecordAudioPermission,
      )
    }
  }

  override fun onStart() {
    super.onStart()
    // First, ensure the app has necessary Android permissions
    permissionCheckLauncher.launch(PERMISSIONS)
  }

  // Debug-only: when launched with `--ez mwdat_ui_testing true`, enable MockDeviceKit and start its
  // local HTTP test server so an out-of-process MCP client can drive the app without real glasses.
  private fun maybeStartMockDeviceTestServer() {
    if (!BuildConfig.DEBUG || !intent.getBooleanExtra(EXTRA_UI_TESTING, false)) {
      return
    }
    val mockDeviceKit = MockDeviceKit.getInstance(applicationContext)
    mockDeviceKit.enable(MockDeviceKitConfig(initiallyRegistered = false))
    mockDeviceKit
        .startTestServer(MOCK_TEST_SERVER_PORT)
        .fold(
            onSuccess = { Log.i(TAG, "MockDevice test server started") },
            onFailure = { error, _ ->
              Log.w(TAG, "Failed to start MockDevice test server: ${error.description}")
            },
        )
  }
}
