# Meta Wearables DAT SDK

> Full API reference: https://wearables.developer.meta.com/llms.txt?full=true
> DAT docs MCP: https://mcp.developer.meta.com/wearables
> Developer docs: https://wearables.developer.meta.com/docs/develop/

## Code style

## Quick Reference

| Task | Command |
|------|---------|
| Build app | `./gradlew assembleDebug` |
| Run tests | `./gradlew test` |
| Install app | `./gradlew installDebug` |
| Lint app | `./gradlew lint` |

## Architecture

The SDK is organized into four public modules:

- **mwdat-core**: Registration, permissions, devices, and session creation
- **mwdat-camera**: Camera capability, video frames, and photo capture
- **mwdat-display**: Display capability, display UI components, icons, images, buttons, and video
- **mwdat-mockdevice**: MockDeviceKit for testing without hardware

### Initialization and session setup

`DeviceSession.start()` is fire-and-forget and returns `Unit`; capabilities can only be attached once the session reports `DeviceSessionState.STARTED`.

```kotlin
Wearables.initialize(context)

Wearables.createSession(AutoDeviceSelector()).fold(
    onSuccess = { session ->
        lifecycleScope.launch {
            session.state.collect { state ->
                if (state == DeviceSessionState.STARTED) {
                    session.addCamera(StreamConfiguration()).fold(
                        onSuccess = { camera ->
                            camera.stream.start().onFailure { error, _ ->
                                showError(error.description)
                            }
                        },
                        onFailure = { error, _ -> showError(error.description) },
                    )
                }
            }
        }
        lifecycleScope.launch {
            session.errors.collect { error -> showError(error.description) }
        }
        session.start()
    },
    onFailure = { error, _ -> showError(error.description) },
)
```

## Kotlin patterns

- Use `DatResult<T, E>` for typed success and failure handling
- Prefer `fold`, `onSuccess`, and the two-parameter `onFailure { error, cause -> }` overload, which hands you the typed `DatError` with a `description`
- `getOrElse { }` receives a raw `Throwable`, not a typed `DatError`, so it cannot read `error.description`
- Observe state with `StateFlow` and `Flow`
- Create a `DeviceSession` first, then attach capabilities such as `Camera` or `Display` after it reaches `STARTED`
- Keep frame handling off the main thread when doing heavier processing

## Error handling

```kotlin
Wearables.checkPermissionStatus(Permission.CAMERA)
    .onSuccess { status -> /* handle status */ }
    .onFailure { error, _ -> /* handle error */ }
```

Avoid `getOrThrow()` in user-facing samples. Surface typed errors from `DatResult` instead.

## Naming conventions

| Type | Purpose | Example |
|------|---------|---------|
| `DeviceSession` | Device connection lifecycle | `Wearables.createSession(...)` |
| `Camera` | Camera capability on a session | `session.addCamera(...)` |
| `Stream` | Video stream from a camera | `camera.stream` |
| `Display` | Display capability on a session | `session.addDisplay(...)` |
| `*Selector` | Device targeting | `AutoDeviceSelector`, `SpecificDeviceSelector` |
| `*Error` | Typed failure surface | `DeviceSessionError`, `StreamError`, `CaptureError` |

## Key types

- `Wearables` — SDK entry point
- `DeviceSession` — lifecycle for an interaction with a linked device
- `Camera` — camera capability attached to a session
- `Stream` — video stream accessed through `camera.stream`
- `Display` — display capability attached to a session
- `StreamConfiguration` — video quality, frame rate, and compression configuration
- `MockDeviceKit` — simulated device environment for testing

## Live docs search

If your editor supports remote MCP servers, connect `https://mcp.developer.meta.com/wearables` and use `search_dat_docs` for current DAT setup, session lifecycle, camera streaming, MockDeviceKit, permissions, and exact API symbols. This public docs server does not require authentication; do not configure tokens, OAuth, or custom authorization headers for it.

Setup:

- Muse Code, Claude Code, and Codex: install the `mwdat-android` plugin; it registers the server automatically and requires no separate MCP configuration.
- Cursor: add an HTTP MCP server named `wearables-dat` pointing at `https://mcp.developer.meta.com/wearables`.
- MCP Inspector: run `npx @modelcontextprotocol/inspector`, choose Streamable HTTP with a Direct connection, enter the same URL, initialize, and confirm `search_dat_docs` is listed.

If a client asks for credentials for this server, remove any stale auth headers and reconnect. Use `llms.txt` when your tool only supports static reference context.

## Testing with MockDeviceKit

```kotlin
val mockDeviceKit = MockDeviceKit.getInstance(context)
mockDeviceKit.enable()
mockDeviceKit.pairGlasses(GlassesModel.RAYBAN_META).fold(
    onSuccess = { device -> onMockDevicePaired(device) },
    onFailure = { error, _ -> showError(error.description) },
)
```

Use MockDeviceKit to drive registration, device availability, streaming media, and permission scenarios without physical hardware.

## Common pitfalls

- Do not call SDK APIs before `Wearables.initialize(context)`
- Do not call `addCamera(...)` or `addDisplay()` immediately after `session.start()`; wait for `DeviceSessionState.STARTED`
- Do not assume a session implies streaming or display access; capabilities are attached separately
- Do not ignore `DatResult` failures from `createSession`, `start`, `addCamera`, `addDisplay`, or `capturePhoto`
- Do not reuse terminally stopped sessions, cameras, or streams

## Links

- [Android API reference](https://wearables.developer.meta.com/docs/reference/android/dat/latest)
- [Developer documentation](https://wearables.developer.meta.com/docs/develop/)
- [GitHub repository](https://github.com/facebook/meta-wearables-dat-android)

## Dev environment tips

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

Camera access also needs the DAT camera permission; see the permissions and registration section.

## Next steps

- Camera streaming — Stream capability, video frames, photo capture
- MockDevice testing — Test without hardware
- Session lifecycle — Handle session and stream state changes
- Permissions and registration — Registration and permission flows
- Display access — Render content on Meta Ray-Ban Display glasses
- [Full Android API reference](https://wearables.developer.meta.com/docs/reference/android/dat/latest)

## Testing instructions

Use MockDeviceKit to test DAT SDK integrations without physical Meta glasses.

MockDeviceKit simulates Meta glasses behavior for development and testing. It provides:
- `MockDeviceKit` — Entry point for creating simulated devices
- `MockGlasses` — Simulated Ray-Ban Meta glasses
- `MockCameraKit` — Simulated camera with configurable video feed and photo capture

## Setup

Add `mwdat-mockdevice` to your Gradle dependencies:

```kotlin
dependencies {
    implementation(libs.mwdat.mockdevice)
}
```

## Creating a mock device

```kotlin
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitConfig

val mockDeviceKit = MockDeviceKit.getInstance(context)

// Attach fake registration and connectivity (auto-initializes Wearables if needed).
// By default, Wearables.registrationState transitions to Registered.
mockDeviceKit.enable()

// Or start in unregistered state to test registration flows:
// mockDeviceKit.enable(MockDeviceKitConfig(initiallyRegistered = false))

// pairGlasses returns DatResult<MockGlasses, MockDeviceKitError>.
val device = mockDeviceKit.pairGlasses(GlassesModel.RAYBAN_META).getOrThrow()
```

You can check `mockDeviceKit.isEnabled` to query whether the mock environment is active.

## Simulating device states

```kotlin
// Simulate glasses lifecycle
device.powerOn()
device.unfold()
device.don()    // Simulate wearing the glasses

// Later...
device.doff()   // Simulate removing
device.fold()
device.powerOff()
```

## Configuring permissions

MockDeviceKit provides `permissions` to control permission behavior without the Meta AI app.

By default, `RequestPermissionContract` returns `Granted`. Use `set()` to control `checkPermissionStatus()` and `setRequestResult()` to control request outcomes.

```kotlin
val mockDeviceKit = MockDeviceKit.getInstance(context)

// Simulate denied camera permission status
mockDeviceKit.permissions.set(Permission.CAMERA, PermissionStatus.Denied)

// Simulate denied request result (user tapping "deny")
mockDeviceKit.permissions.setRequestResult(Permission.CAMERA, PermissionStatus.Denied)
```

## Setting up mock camera feeds

### Video streaming

```kotlin
val camera = device.services.camera
camera.setCameraFeed(videoUri)
```

### Photo capture

```kotlin
val camera = device.services.camera
camera.setCapturedImage(imageUri)
```

**Note**: Android doesn't transcode video automatically. Mock video files must be in h.265 format. Use FFmpeg to convert:

```bash
ffmpeg -hwaccel videotoolbox -i input.mp4 -c:v hevc_videotoolbox -c:a aac_at -tag:v hvc1 -vf "scale=540:960" output.mov
```

### Phone camera as the feed

`setCameraFeed(CameraFacing)` sources frames from the handset camera, so the app needs `android.permission.CAMERA` in the manifest and at runtime. That is not the DAT `Permission.CAMERA` covered by `MockDeviceKitConfig(initialPermissionsGranted = true)` — no real-glasses path needs the handset grant, so an existing integration will never have requested it.

Grant it before calling `setCameraFeed`; in instrumentation tests, `pm grant <your.package> android.permission.CAMERA`. Without it the mock rejects the stream start and the failure names the missing permission. `setCameraFeed(Uri)` has no such requirement.

## Writing instrumentation tests

Create a reusable test base class:

```kotlin
import android.content.Context
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import org.junit.After
import org.junit.Before
import org.junit.Rule

open class MockDeviceKitTestCase<T : Any>(
    private val activityClass: Class<T>
) {
    @get:Rule
    val scenarioRule = ActivityScenarioRule(activityClass)

    protected lateinit var mockDeviceKit: MockDeviceKitInterface
    protected lateinit var targetContext: Context

    @Before
    open fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        targetContext = instrumentation.targetContext
        mockDeviceKit = MockDeviceKit.getInstance(targetContext)
        grantRuntimePermissions()
    }

    @After
    open fun tearDown() {
        mockDeviceKit.disable()
    }

    private fun grantRuntimePermissions() {
        val packageName = targetContext.packageName
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("pm grant $packageName android.permission.BLUETOOTH_CONNECT")
        shell.executeShellCommand("pm grant $packageName android.permission.CAMERA")
    }
}
```

## Using MockDeviceKit in the CameraAccess sample

The CameraAccess sample app includes a Debug menu for MockDeviceKit:

1. Tap the **Debug icon** to open the MockDeviceKit menu
2. Tap **Pair RayBan Meta** to create a simulated device
3. Use **PowerOn**, **Unfold**, **Don** to simulate glasses states
4. Select video/image files for mock camera feeds
5. Start streaming to see simulated frames

## Supported media formats

| Type | Formats |
|------|---------|
| Video | h.264 (AVC), h.265 (HEVC) |
| Image | JPEG, PNG |

## Links

- [Mock Device Kit overview](https://wearables.developer.meta.com/docs/mock-device-kit)
- [Android testing guide](https://wearables.developer.meta.com/docs/testing-mdk-android)

## Building and streaming

Use a `DeviceSession` and an attached `Camera` to receive frames and capture photos through `camera.stream`.

## Key concepts

- **DeviceSession**: Device connection lifecycle created through `Wearables.createSession(...)`
- **Camera**: Camera capability attached to a session with `session.addCamera(...)`
- **Stream**: Video stream accessed through `camera.stream`
- **StreamConfiguration**: Video quality, frame rate, and compression configuration for the stream
- **PhotoData**: Still image captured from glasses while streaming

## Create a session and attach a stream

`DeviceSession.start()` is fire-and-forget: it returns `Unit` and the connection completes in the background. A capability can only be added once the session reports `DeviceSessionState.STARTED` — calling `addCamera(...)` right after `start()` fails with `DeviceSessionError.SESSION_IDLE`. Add the camera from the session-state collector.

```kotlin
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSessionState

var camera: Camera? = null

Wearables.createSession(AutoDeviceSelector()).fold(
    onSuccess = { session ->
        lifecycleScope.launch {
            session.errors.collect { error -> showError(error.description) }
        }
        lifecycleScope.launch {
            session.state.collect { state ->
                if (state == DeviceSessionState.STARTED && camera == null) {
                    session.addCamera(
                        StreamConfiguration(
                            videoQuality = VideoQuality.MEDIUM,
                            frameRate = 24,
                        ),
                    ).fold(
                        onSuccess = { addedCamera ->
                            camera = addedCamera
                            addedCamera.stream.start().onFailure { error, _ ->
                                showError(error.description)
                            }
                        },
                        onFailure = { error, _ -> showError(error.description) },
                    )
                }
            }
        }
        // Subscribe before start() so no initial transition is missed.
        session.start()
    },
    onFailure = { error, _ -> showError(error.description) },
)
```

Check `Wearables.checkPermissionStatus(Permission.CAMERA)` before starting the stream.

### Resolution options

| Quality | Size |
|---------|------|
| `VideoQuality.HIGH` | 720 x 1280 |
| `VideoQuality.MEDIUM` | 504 x 896 |
| `VideoQuality.LOW` | 360 x 640 |

### Frame rate options

Valid values: `2`, `7`, `15`, `24`, `30` FPS.

Lower resolution and frame rate usually produce better visual quality per frame over Bluetooth.

## Observe stream state

`StreamState` transitions: `STOPPED` -> `STARTING` -> `STARTED` -> `STREAMING` -> `STOPPING` -> `STOPPED`, and `CLOSED` once the stream is terminal. `PAUSED` is reported when the device pauses the stream, for example on a single cap-touch tap; the stream can resume on its own from `PAUSED`.

```kotlin
lifecycleScope.launch {
    camera.stream.state.collect { state ->
        when (state) {
            StreamState.STREAMING -> {
                // Frames are flowing
            }
            StreamState.PAUSED -> {
                // Paused by the device; wait for it to resume
            }
            StreamState.STOPPED -> {
                // Streaming ended
            }
            StreamState.CLOSED -> {
                // Stream fully closed
            }
            else -> Unit
        }
    }
}
```

Observe `camera.stream.errorStream` alongside the state. `StreamError.STREAM_ERROR` is informational and does not stop the stream, while `StreamError.CRITICAL_STREAM_ERROR` means the stream should be torn down.

```kotlin
lifecycleScope.launch {
    camera.stream.errorStream.collect { error ->
        showStreamError(error.description)
    }
}
```

## Receive frames

```kotlin
lifecycleScope.launch {
    camera.stream.videoStream.collect { frame ->
        updatePreview(frame)
    }
}
```

By default the SDK decodes on the phone and `VideoFrame.buffer` holds YUV pixel data. Set `StreamConfiguration(compressVideo = true)` to receive compressed HEVC buffers instead; then check `frame.isCompressed` and `frame.isCodecConfig` and feed the frames to your own decoder.

## Capture a photo

`capturePhoto()` only succeeds while the stream is active, and returns a `PhotoData` sealed type — branch on the variant instead of reading a single `data` property.

```kotlin
import com.meta.wearable.dat.camera.types.PhotoData

lifecycleScope.launch {
    camera.stream.capturePhoto()
        .onSuccess { photoData ->
            when (photoData) {
                is PhotoData.Bitmap -> savePhoto(photoData.bitmap)
                is PhotoData.HEIC -> saveHeic(photoData.data) // ByteBuffer of HEIC bytes
            }
        }
        .onFailure { error, _ ->
            showCaptureError(error.description)
        }
}
```

Only one capture can be in flight at a time; a second concurrent call fails with `CaptureError.CaptureInProgress`.

## Clean up

Stop the camera when you no longer need camera data, then stop the parent session if the device interaction is finished. Stopping the camera cascades to its stream, and stopping the session cascades to every attached capability.

```kotlin
camera.stop()
session.stop()
```

`Camera.stop()` and `Stream.stop()` invalidate the instance — they cannot be restarted. Call `session.removeCamera()` to detach the capability so a later `session.addCamera(...)` on the same session can succeed.

## Links

- [Android API reference](https://wearables.developer.meta.com/docs/reference/android/dat/latest)
- [Integration guide](https://wearables.developer.meta.com/docs/build-integration-android)

## Session management

Manage session and stream state in DAT SDK integrations.

Create a `DeviceSession` with `Wearables.createSession(...)`, start it, then attach capabilities such as camera streaming once the session is `STARTED`. Session lifecycle and capability lifecycle are related but distinct.

## Session states

| State | Meaning | App action |
|-------|---------|------------|
| `IDLE` | Session created, not started yet | Call `session.start()` |
| `STARTING` | Connecting to the device | Show loading UI |
| `STARTED` | Session active and ready for capabilities | Add or use capabilities |
| `PAUSED` | Session temporarily suspended | Keep state, wait for resume or stop |
| `STOPPING` | Session is shutting down | Stop user work and wait |
| `STOPPED` | Terminal; session ended | Release resources and create a new session if needed |

`STOPPED` is terminal: a stopped session cannot be restarted, so call `Wearables.createSession(...)` again for the next connection.

## Observe session state

`start()` and `stop()` are both sync fire-and-forget and return `Unit`. Subscribe to `state` and `errors` before calling `start()` so no transition is missed, and treat `session.errors` as the failure channel for `start()`.

```kotlin
Wearables.createSession(AutoDeviceSelector()).fold(
    onSuccess = { session ->
        lifecycleScope.launch {
            session.state.collect { state ->
                when (state) {
                    DeviceSessionState.STARTED -> onStarted(session)
                    DeviceSessionState.PAUSED -> onPaused()
                    DeviceSessionState.STOPPED -> onStopped()
                    else -> Unit
                }
            }
        }
        lifecycleScope.launch {
            session.errors.collect { error -> showError(error.description) }
        }
        session.start()
    },
    onFailure = { error, _ -> showError(error.description) },
)
```

Add capabilities from `onStarted(session)`. Calling `session.addCamera(...)` while the session is still `IDLE` fails with `DeviceSessionError.SESSION_IDLE`, and calling it after `STOPPED` fails with `DeviceSessionError.SESSION_ALREADY_STOPPED`.

## Stream state

Camera streaming has its own state flow after you add a camera:

```text
STOPPED -> STARTING -> STARTED -> STREAMING -> STOPPING -> STOPPED
```

`CLOSED` is the terminal stream state, and `PAUSED` is reported when the device pauses streaming (for example a single cap-touch tap) and can resume on its own.

```kotlin
lifecycleScope.launch {
    camera.stream.state.collect { state ->
        // React to stream state changes
    }
}
```

## Common transitions

The SDK may pause or stop a session when:

- Another experience takes over the device
- The user removes or folds the glasses
- Bluetooth connectivity drops
- The user unregisters the app or revokes needed access

## Pause and resume

When a session is paused:

- The device connection may remain active
- Attached capabilities stop doing useful work
- Your app should wait for the next observed session state instead of trying to force a restart

## Device availability

```kotlin
lifecycleScope.launch {
    Wearables.devices.collect { devices ->
        // Update the list of available devices
    }
}
```

Use `Wearables.devices` and device metadata to decide when it is sensible to create a new session after a stop.

## Checklist

- [ ] Handle all `DeviceSessionState` values you care about
- [ ] Observe stream state separately from session state
- [ ] Release resources only after stop or close
- [ ] Recreate sessions after terminal stops instead of reusing dead ones
- [ ] Surface typed `DeviceSessionError` and `StreamError` failures

## Links

- [Session lifecycle documentation](https://wearables.developer.meta.com/docs/lifecycle-events)
- [Android API reference](https://wearables.developer.meta.com/docs/reference/android/dat/latest)

## Permissions

Register your app with Meta AI, then request the device permissions it needs.

The DAT SDK separates two steps:

1. **Registration**: The user connects your app to Meta AI.
2. **Device permissions**: After registration, your app requests capabilities such as camera access.

Both flows depend on the Meta AI app being installed on the phone.

## Start registration

```kotlin
Wearables.startRegistration(activity)
```

Observe registration state:

```kotlin
lifecycleScope.launch {
    Wearables.registrationState.collect { state ->
        // Update your registration UI
    }
}
```

To unregister:

```kotlin
Wearables.startUnregistration(activity)
```

## Check permission status

`checkPermissionStatus(...)` is a suspend API that returns a `DatResult`.

```kotlin
lifecycleScope.launch {
    Wearables.checkPermissionStatus(Permission.CAMERA)
        .onSuccess { status ->
            if (status == PermissionStatus.Granted) {
                startStreaming()
            }
        }
        .onFailure { error, _ ->
            showPermissionError(error.description)
        }
}
```

## Request a permission

Use `Wearables.RequestPermissionContract()` with the Activity Result API:

```kotlin
private val permissionLauncher =
    registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        result.onSuccess { status ->
            if (status == PermissionStatus.Granted) {
                startStreaming()
            }
        }.onFailure { error, _ ->
            showPermissionError(error.description)
        }
    }

fun requestCameraPermission() {
    permissionLauncher.launch(Permission.CAMERA)
}
```

Users can allow once or allow always through the Meta AI flow.

## Developer Mode vs production

| Mode | Registration behavior |
|------|------------------------|
| Developer Mode | Use `mwdat_application_id = 0` and `mwdat_client_token = 0` manifest placeholders for local development |
| Production | Use the application ID and client token assigned in the Wearables Developer Center |

For development builds, enable Developer Mode in the Meta AI app before testing registration and permissions.

## Prerequisites

- Internet connection for registration
- Meta AI app installed on the phone
- Callback URI scheme configured in `AndroidManifest.xml`
- Bluetooth permission granted on Android

## Links

- [Permissions documentation](https://wearables.developer.meta.com/docs/permissions-requests)
- [Manage projects](https://wearables.developer.meta.com/docs/manage-projects)
- [Android integration guide](https://wearables.developer.meta.com/docs/build-integration-android)

## Debugging

Diagnose common setup, session, and stream issues in DAT SDK integrations.

## Quick diagnosis

```text
No eligible device or session won't start?
|
+-- Did you call Wearables.initialize(context)? -> Must happen before SDK usage
|
+-- Did registration complete? -> Observe Wearables.registrationState
|
+-- Is Developer Mode enabled? -> Enable it in the Meta AI app for dev builds
|
+-- Does Wearables.devices contain a linked device? -> Check Bluetooth and range
|
+-- Did createSession() or addCamera() return a DatResult failure? -> Surface the typed error
```

## Developer Mode

Developer Mode must be enabled for local development builds that use `mwdat_application_id = 0` and `mwdat_client_token = 0`.

### Symptoms when Developer Mode is disabled

- Registration flow completes but the device never becomes eligible
- Permission requests do not succeed for development builds
- `Wearables.createSession(...)` fails with no eligible device

### Watch for

- Developer Mode may reset after app or firmware updates
- Developer Mode is configured per linked device
- Production builds use a real `APPLICATION_ID`, `CLIENT_TOKEN`, and release-channel gating instead

## Session and stream issues

### Session never reaches `STARTED`

- Verify `Wearables.registrationState`
- Check that `Wearables.devices` contains a compatible linked device
- Ensure the glasses are powered on, unfolded, and in range

### Stream never reaches `STREAMING`

- Confirm `session.start()` succeeded before calling `session.addCamera(...)`
- Check camera permission status through `Wearables.checkPermissionStatus(...)`
- Make sure `camera.stream.start()` returned success

### Photo capture fails

- `capturePhoto()` only succeeds while the stream is actively streaming
- Surface the returned `CaptureError` instead of discarding the `DatResult`

## Version compatibility

Ensure compatible versions of the SDK, Meta AI app, and glasses firmware. See [version dependencies](https://wearables.developer.meta.com/docs/version-dependencies) for the current compatibility matrix.

## Logging

```kotlin
private const val TAG = "DATWearables"

camera.stream.start()
    .onFailure { error, _ -> Log.e(TAG, "Failed to start stream: ${error.description}") }
```

Prefer logging typed `DatResult` failures and observed state transitions over generic exceptions.

## Checklist

- [ ] `Wearables.initialize(context)` ran before SDK usage
- [ ] Developer Mode enabled for development builds
- [ ] `APPLICATION_ID` and `CLIENT_TOKEN` match the build mode
- [ ] Registration completed before session creation
- [ ] Bluetooth permission granted
- [ ] Camera permission granted through Meta AI
- [ ] Session and stream `DatResult` failures are surfaced in logs or UI

## Links

- [Known issues](https://wearables.developer.meta.com/docs/knownissues)
- [Version dependencies](https://wearables.developer.meta.com/docs/version-dependencies)
- [Troubleshooting discussions](https://github.com/facebook/meta-wearables-dat-android/discussions)

## Sample app

Build an Android DAT app with registration, sessions, camera streaming, and photo capture.

Pair this with the [CameraAccess sample](https://github.com/facebook/meta-wearables-dat-android/tree/main/samples).

## Project setup

1. Create an Android Studio app project.
2. Add the DAT Maven repository and dependencies.
3. Configure `AndroidManifest.xml` for registration callbacks plus `APPLICATION_ID` and `CLIENT_TOKEN`.
4. Initialize `Wearables` in your `Application`.

## Suggested app structure

```text
app/src/main/java/com/example/myapp/
├── MyApplication.kt
├── MainActivity.kt
├── session/
│   └── SessionViewModel.kt
└── ui/
    ├── RegistrationScreen.kt
    └── CameraScreen.kt
```

## Registration and session creation

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            Wearables.registrationState.collect { state ->
                // Update registration UI
            }
        }
    }

    fun register() {
        Wearables.startRegistration(this)
    }
}
```

`DeviceSession.start()` is fire-and-forget. Attach the camera from the session-state collector once the session reports `DeviceSessionState.STARTED`; adding a capability to an `IDLE` session fails with `DeviceSessionError.SESSION_IDLE`.

```kotlin
class SessionViewModel : ViewModel() {
    private var session: DeviceSession? = null
    private var camera: Camera? = null

    fun startCameraSession() {
        Wearables.createSession(AutoDeviceSelector()).fold(
            onSuccess = { createdSession ->
                session = createdSession

                viewModelScope.launch {
                    createdSession.errors.collect { error -> showError(error.description) }
                }
                viewModelScope.launch {
                    createdSession.state.collect { state ->
                        when (state) {
                            DeviceSessionState.STARTED -> attachCamera(createdSession)
                            DeviceSessionState.STOPPED -> clearSession(createdSession)
                            else -> Unit
                        }
                    }
                }

                createdSession.start()
            },
            onFailure = { error, _ -> showError(error.description) },
        )
    }

    private fun attachCamera(activeSession: DeviceSession) {
        if (camera != null) return
        activeSession.addCamera(
            StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 24),
        ).fold(
            onSuccess = { addedCamera ->
                camera = addedCamera
                addedCamera.stream.start().onFailure { error, _ ->
                    showError(error.description)
                }
            },
            onFailure = { error, _ -> showError(error.description) },
        )
    }

    private fun clearSession(stoppedSession: DeviceSession) {
        if (session !== stoppedSession) return
        camera = null
        session = null
    }
}
```

## Observe frames and capture photos

```kotlin
viewModelScope.launch {
    camera?.stream?.videoStream?.collect { frame ->
        // Render preview
    }
}

fun capturePhoto() {
    viewModelScope.launch {
        camera?.stream?.capturePhoto()
            ?.onSuccess { photoData ->
                when (photoData) {
                    is PhotoData.Bitmap -> savePhoto(photoData.bitmap)
                    is PhotoData.HEIC -> saveHeic(photoData.data)
                }
            }
            ?.onFailure { error, _ ->
                showCaptureError(error.description)
            }
    }
}
```

## Shutdown

```kotlin
fun stopCameraSession() {
    camera?.stop()
    session?.stop()
    camera = null
    session = null
}
```

## Testing with MockDeviceKit

Use `MockDeviceKit` to simulate linking glasses, permission state, and camera media without physical hardware. See [MockDevice Testing](mockdevice-testing.md) for setup details.

## Links

- [CameraAccess sample](https://github.com/facebook/meta-wearables-dat-android/tree/main/samples)
- [Android integration guide](https://wearables.developer.meta.com/docs/build-integration-android)
- [Developer documentation](https://wearables.developer.meta.com/docs/develop/)

## Display Access

Add `mwdat-display` when rendering content on Meta Ray-Ban Display glasses. Display apps also need the core DAT setup from getting-started and permissions-registration: initialize DAT once, complete registration, request Bluetooth and Internet permissions, and configure DAT manifest metadata.

```toml
mwdat-display = { group = "com.meta.wearable", name = "mwdat-display", version.ref = "mwdat" }
```

```kotlin
dependencies {
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.display)
}
```

Set `mwdat_application_id` and `mwdat_client_token` from manifest placeholders or `local.properties`, as in the DisplayAccess sample. Request runtime permissions before `Wearables.initialize(context)`. Observe `Wearables.registrationState` and `Wearables.registrationErrorStream`; wait for `RegistrationState.REGISTERED` before creating a display session.

For a picker, collect `Wearables.devices` and per-device `Wearables.devicesMetadata[id]`. Show the device name, `device.deviceType.description`, `device.linkState`, `device.compatibility`, and `device.isDisplayCapable()`. Enable selection only for connected display-capable devices, surface `DeviceCompatibility.DEVICE_UPDATE_REQUIRED` with `Wearables.openFirmwareUpdate(activity)`, and use `SpecificDeviceSelector(selectedDeviceId)` for the selected row. Use `AutoDeviceSelector(filter = { it.isDisplayCapable() })` only when automatic selection is acceptable.

Attach Display only after the `DeviceSession` reaches `STARTED`, and enable user content only once the Display capability reaches `DisplayState.STARTED`. Observe `session.errors`; `DeviceSession.start()` returns `Unit`. If session creation or `session.errors` reports `DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED`, show an update action that calls `Wearables.openDATGlassesAppUpdate(activity)`.

```kotlin
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.TextStyle

fun startDisplaySession(selectedDeviceId: DeviceIdentifier) {
    if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
        showError("Register with Meta AI before starting Display")
        return
    }

    val session =
        Wearables.createSession(SpecificDeviceSelector(selectedDeviceId)).fold(
            onSuccess = { it },
            onFailure = { error, _ ->
                if (error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) {
                    showDatAppUpdateAction()
                }
                showError(error.description)
                return
            },
        )
    var display: Display? = null

    lifecycleScope.launch {
        session.errors.collect { error ->
            if (error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) {
                showDatAppUpdateAction()
            }
            showError(error.description)
        }
    }

    lifecycleScope.launch {
        session.state.collect { state ->
            if (state == DeviceSessionState.STARTED && display == null) {
                session.addDisplay()
                    .onSuccess { newDisplay ->
                        display = newDisplay
                        lifecycleScope.launch {
                            newDisplay.state.collect { displayState ->
                                setTryItEnabled(displayState == DisplayState.STARTED)
                                if (displayState == DisplayState.STARTED) {
                                    newDisplay.sendContent {
                                        flexBox(
                                            gap = 12,
                                            padding = 24,
                                            background = FlexBoxBackground.CARD,
                                        ) {
                                            text("Bike ride", style = TextStyle.HEADING)
                                            button(
                                                label = "Done",
                                                style = ButtonStyle.PRIMARY,
                                                iconName = IconName.CHECKMARK,
                                                onClick = { showDoneState() },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .onFailure { error, _ -> showError(error.description) }
            }
        }
    }
    session.start()
}
```

Build exactly one root view per `sendContent` call: use a root `flexBox { ... }` for UI, or a root `video(player = player)` for video. Do not put `video(...)` inside a `flexBox`. Button and clickable `flexBox` callbacks are routed back to the phone app; keep callbacks fast and delegate to app state or ViewModel methods. Use `IconName` enum values such as `IconName.GEAR`, not raw strings.

For URL video, create `VideoPlayer(source = VideoSource.Url(...), codec = VideoCodec.MP4)`, send it with `display.sendContent { video(player = player) }`, and call `player.play()` after send success. Collect `player.state` and `player.error`; on `VideoPlayerState.ENDED`, cancel the video observer and send the next display screen. On cleanup, cancel state/error collection jobs, close or replace active video players, call `session.removeDisplay()`, then stop the session.

## Inputs

Inputs is experimental and requires capability approval in Wearables Developer Center; there is no runtime Inputs permission to request. After `DeviceSessionState.STARTED`, call `session.addInputs(...).fold(...)`. Adding automatically starts the capability. Collect state, nullable errors, and events in lifecycle-owned jobs and handle every `InputEvent` subtype. Cancel those jobs before `session.removeInputs()`. For tests, inject events through `glasses.services.input` only after Inputs is active.

## Motion

Motion is experimental and has no runtime permission. After `DeviceSessionState.STARTED`, handle `addMotion` as a `DatResult`, launch collectors, then call `motion.start()`. Treat sensor fields as nullable and handle `MotionState.PAUSED`; the capability resumes automatically with the parent session. Stop and cancel collectors before `session.removeMotion()`. Use `glasses.services.motion.setMotionFeed` for deterministic replay.

## Speech

Speech is experimental and requires DAT microphone permission from a user action. Host `RECORD_AUDIO` is only for live-phone MockDevice recognition. After `DeviceSessionState.STARTED`, handle `addSpeech` and `start` results, collect state/locale/errors/transcriptions, distinguish partial and final text, and cancel collectors before `session.removeSpeech()`. Use `glasses.services.speech` for deterministic tests.

## Audio Streaming

Audio Streaming is an experimental Camera Stream extension. Require `Permission.CAMERA` and `Permission.MICROPHONE`, set `AudioCodec.PCM`, collect `audioStream` before starting Stream, and use `presentationTimeUs`. Cancel collectors before stopping Camera. Do not assume MockDeviceKit emits deterministic PCM frames.

## Camera Capture

For standalone high-quality capture use `camera.photo`, not `stream.capturePhoto()`. Require `Permission.CAMERA` and stop an active Stream first. Accessing Photo activates it lazily; collect state/data/progress/errors and wait for `PhotoState.STARTED` before capture. Stop Photo before Camera. Configure mock results through `glasses.services.cameraCapture`.

## Voice Invocations

Voice Invocations is an experimental Wearables-level stream and does not use `DeviceSession` or camera/microphone permission. Validate intents in `onCreate` and `onNewIntent`, retain one app-scoped stream, handle `LaunchApp`, and answer every invocation exactly once. Cancel collectors and call `close()` during cleanup. Configure approval and the spoken app name in Wearables Developer Center.
