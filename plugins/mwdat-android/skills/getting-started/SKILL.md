---
name: getting-started
description: SDK setup, Gradle integration, AndroidManifest configuration, and first connection to Meta glasses
---

# Getting Started with DAT SDK (Android)

Set up the Meta Wearables Device Access Toolkit in an Android app.

## Prerequisites

- Android Studio Flamingo or newer
- Android 10+ test device with the Meta AI app installed
- Supported Meta glasses or MockDeviceKit for local testing
- Developer Mode enabled in the Meta AI app for development builds

The SDK is published to Maven Central under the `com.meta.wearable` group, so the `mavenCentral()` repository already declared in `settings.gradle.kts` is all that is needed. No access token is required.

## Step 1: Declare dependencies

In `libs.versions.toml`:

```toml
[versions]
mwdat = "1.0.0"

[libraries]
mwdat-core = { group = "com.meta.wearable", name = "mwdat-core", version.ref = "mwdat" }
mwdat-camera = { group = "com.meta.wearable", name = "mwdat-camera", version.ref = "mwdat" }
mwdat-display = { group = "com.meta.wearable", name = "mwdat-display", version.ref = "mwdat" }
mwdat-mockdevice = { group = "com.meta.wearable", name = "mwdat-mockdevice", version.ref = "mwdat" }
```

In `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        manifestPlaceholders["mwdat_application_id"] = "0"
        manifestPlaceholders["mwdat_client_token"] = "0"
    }
}

dependencies {
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.camera)
    implementation(libs.mwdat.display)
    implementation(libs.mwdat.mockdevice)
}
```

## Step 2: Configure `AndroidManifest.xml`

```xml
<manifest ...>
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application ...>
        <meta-data
            android:name="com.meta.wearable.mwdat.APPLICATION_ID"
            android:value="${mwdat_application_id}" />
        <meta-data
            android:name="com.meta.wearable.mwdat.CLIENT_TOKEN"
            android:value="${mwdat_client_token}" />

        <activity android:name=".MainActivity" ...>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="myexampleapp" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`APPLICATION_ID` and `CLIENT_TOKEN` are used for app attestation and can be found in the Wearables Developer Center. In Developer Mode, attestation is not used, so the manifest placeholders can both be `0`. For production, replace both placeholders with the credentials for your Wearables Developer Center app. Replace `myexampleapp` with your app's URL scheme.

## Step 3: Initialize the SDK

```kotlin
import android.util.Log
import com.meta.wearable.dat.core.Wearables

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Wearables.initialize(this)
            .onFailure { error, _ ->
                Log.e("DATWearables", "Failed to initialize DAT: ${error.description}")
            }
    }
}
```

## Step 4: Register and create a session

Registration must complete before a session can start. Observe `Wearables.registrationState` and wait for `RegistrationState.REGISTERED`.

```kotlin
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState

fun connect(activity: Activity) {
    Wearables.startRegistration(activity)
}

fun startSession() {
    Wearables.createSession(AutoDeviceSelector()).fold(
        onSuccess = { session ->
            lifecycleScope.launch {
                session.errors.collect { error -> showError(error.description) }
            }
            lifecycleScope.launch {
                session.state.collect { state ->
                    if (state == DeviceSessionState.STARTED) {
                        addCameraStreaming(session)
                    }
                }
            }
            // Fire-and-forget: returns Unit and reports failures through session.errors.
            session.start()
        },
        onFailure = { error, _ -> showError(error.description) },
    )
}
```

Observe registration and available devices:

```kotlin
lifecycleScope.launch {
    Wearables.registrationState.collect { state ->
        // Update registration UI
    }
}

lifecycleScope.launch {
    Wearables.devices.collect { devices ->
        // Update the device list
    }
}
```

## Step 5: Add camera streaming

Call this only once the session reports `DeviceSessionState.STARTED`; adding a capability earlier fails with `DeviceSessionError.SESSION_IDLE`.

```kotlin
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality

fun addCameraStreaming(session: DeviceSession) {
    session.addCamera(
        StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 24),
    ).fold(
        onSuccess = { camera ->
            camera.stream.start().onFailure { error, _ -> showError(error.description) }
        },
        onFailure = { error, _ -> showError(error.description) },
    )
}
```

Camera access also needs the DAT camera permission; see the `permissions-registration` skill.

## Next steps

- `camera-streaming` — Stream capability, video frames, photo capture
- `mockdevice-testing` — Test without hardware
- `session-lifecycle` — Handle session and stream state changes
- `permissions-registration` — Registration and permission flows
- `display-access` — Render content on Meta Ray-Ban Display glasses
- [Full Android API reference](https://wearables.developer.meta.com/docs/reference/android/dat/latest)
