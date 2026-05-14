---
name: display-access
description: Display capability setup, display-capable device selection, UI DSL, icons, buttons, images, and video playback
---

# Display Access (Android)

Use `mwdat-display` to render content on Meta Ray-Ban Display glasses.

Use this skill with `getting-started` and `permissions-registration` when creating a full app. A Display app still needs SDK initialization, app registration, Android permissions, and DAT manifest metadata before it can create a session.

## Add the Display dependency

In `libs.versions.toml`:

```toml
[libraries]
mwdat-display = { group = "com.meta.wearable", name = "mwdat-display", version.ref = "mwdat" }
```

In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.display)
}
```

## Configure the app for Display

Display apps need the same core DAT setup as other apps plus DAM enabled:

```xml
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
    <meta-data
        android:name="com.meta.wearable.mwdat.DAM_ENABLED"
        android:value="true" />
</application>
```

Set `mwdat_application_id` and `mwdat_client_token` from Gradle manifest placeholders or `local.properties`, as in the DisplayAccess sample. Developer Mode builds can use the developer registration flow, but production builds need real Wearables Developer Center credentials.

Request the runtime permissions before initializing DAT, then call `Wearables.initialize(context)` once and start observing SDK state. Start registration with `Wearables.startRegistration(activity)`, collect `Wearables.registrationState`, collect `Wearables.registrationErrorStream`, and wait for `RegistrationState.REGISTERED` before creating a display session. Use `Wearables.startUnregistration(activity)` when the user disconnects the app.

## Select a display-capable device

Display content only works on connected, compatible devices whose type supports display. Use the public device filter when you want automatic selection:

```kotlin
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector

val selector = AutoDeviceSelector(filter = { device -> device.isDisplayCapable() })
val sessionResult = Wearables.createSession(selector)
```

Use `SpecificDeviceSelector(selectedDeviceId)` instead when your UI lets the user pick a specific `DeviceIdentifier` from `Wearables.devices`.

For device picker UI, mirror the DisplayAccess sample: collect `Wearables.devices`, start a metadata collection for each device ID from `Wearables.devicesMetadata[id]`, remove metadata for devices that disappear, and show device name, `device.deviceType.description`, `device.linkState`, `device.compatibility`, and `device.isDisplayCapable()`. Keep selection disabled unless the device is `LinkState.CONNECTED` and display-capable, and surface `DeviceCompatibility.DEVICE_UPDATE_REQUIRED` with an `openFirmwareUpdate` action.

```kotlin
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.LinkState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private val metadataJobs = mutableMapOf<DeviceIdentifier, Job>()
private val devicesMetadata = MutableStateFlow<Map<DeviceIdentifier, Device>>(emptyMap())

lifecycleScope.launch {
    Wearables.devices.collect { deviceIds ->
        (metadataJobs.keys - deviceIds).forEach { removedId ->
            metadataJobs.remove(removedId)?.cancel()
            devicesMetadata.update { current -> current - removedId }
        }

        deviceIds.forEach { id ->
            metadataJobs.getOrPut(id) {
                launch {
                    Wearables.devicesMetadata[id]?.collect { device ->
                        devicesMetadata.update { current -> current + (id to device) }
                        updateDeviceRow(
                            id = id,
                            name = device.name,
                            type = device.deviceType.description,
                            isConnected = device.linkState == LinkState.CONNECTED,
                            isDisplayCapable = device.isDisplayCapable(),
                            compatibility = device.compatibility,
                        )
                    }
                }
            }
        }
    }
}
```

If `Wearables.createSession(...)` fails with `DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED`, show a DAT glasses app update action that calls `Wearables.openDATGlassesAppUpdate(activity)`.

## Attach Display after the session starts

Add the Display capability only after the `DeviceSession` reaches `DeviceSessionState.STARTED`. Keep the sample app pattern of showing a "preparing" state after session start and enabling user content only once the Display capability reports `DisplayState.STARTED`.

```kotlin
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import kotlinx.coroutines.launch

private var display: Display? = null

fun startDisplaySession(selectedDeviceId: DeviceIdentifier) {
    if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
        showError("Register with Meta AI before starting Display")
        return
    }

    Wearables.createSession(SpecificDeviceSelector(selectedDeviceId))
        .fold(
            onSuccess = { session ->
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
                            attachDisplay(session)
                        }
                    }
                }
                session.start()
            },
            onFailure = { error, _ ->
                if (error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) {
                    showDatAppUpdateAction()
                }
                showError(error.description)
            },
        )
}

private fun attachDisplay(session: DeviceSession) {
    session.addDisplay()
        .fold(
            onSuccess = { newDisplay ->
                display = newDisplay
                lifecycleScope.launch {
                    newDisplay.state.collect { state ->
                        if (state == DisplayState.STARTED) {
                            setTryItEnabled(true)
                        } else {
                            setTryItEnabled(false)
                        }
                    }
                }
            },
            onFailure = { error, _ ->
                showError(error.description)
            },
        )
}

fun stopDisplaySession(session: DeviceSession) {
    session.removeDisplay().onFailure { error, _ -> showError(error.description) }
    session.stop()
    display = null
}
```

## Send display UI

Build exactly one root view per `sendContent` call. Each call replaces the previous content on the glasses. Use a root `flexBox { ... }` for UI, or a root `video(player = player)` for video; do not put `video(...)` inside a `flexBox`.

Button and clickable `flexBox` callbacks are routed back to the phone app. Keep callbacks fast and delegate to your ViewModel to send the next content screen, as the DisplayAccess sample does for list items, Back, Start, Previous, Next, Done, and Watch video buttons.

```kotlin
import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle

private suspend fun sendStatusCard(display: Display) {
    display.sendContent {
        flexBox(
            gap = 12,
            padding = 24,
            background = FlexBoxBackground.CARD,
            onClick = { showDetailState() },
        ) {
            text("Bike ride", style = TextStyle.HEADING)
            text("Turn right in 200 ft", style = TextStyle.BODY, color = TextColor.SECONDARY)
            button(
                label = "Done",
                style = ButtonStyle.PRIMARY,
                iconName = IconName.CHECKMARK,
                onClick = { showDoneState() },
            )
        }
    }.onFailure { error, _ ->
        showError(error.description)
    }
}
```

## Use images and built-in icons

Use HTTPS image URLs for image content, and use `IconName` enum values for built-in icons. Do not invent string icon names.

```kotlin
import com.meta.wearable.dat.display.views.CornerRadius
import com.meta.wearable.dat.display.views.IconStyle
import com.meta.wearable.dat.display.views.ImageSize

display.sendContent {
    flexBox(gap = 8, padding = 24) {
        image(
            uri = "https://example.com/thumbnail.png",
            sizePreset = ImageSize.FILL,
            cornerRadius = CornerRadius.MEDIUM,
        )
        icon(name = IconName.GEAR, style = IconStyle.FILLED)
        text("Device settings", style = TextStyle.BODY)
    }
}
```

## Send video

For URL-based video, create a `VideoPlayer`, send it as the root content, then call `play()` after the send succeeds. Collect both `player.state` and `player.error`; on `VideoPlayerState.ENDED`, cancel the video observer and send the next display screen. Use an HTTP or HTTPS `VideoSource.Url`, `VideoCodec.MP4` for MP4 assets, and a video sized within the public player limits.

```kotlin
import com.meta.wearable.dat.display.types.VideoCodec
import com.meta.wearable.dat.display.types.VideoPlayerState
import com.meta.wearable.dat.display.types.VideoSource
import com.meta.wearable.dat.display.views.VideoPlayer
import kotlinx.coroutines.Job

private var videoStateJob: Job? = null
private var videoErrorJob: Job? = null

lifecycleScope.launch {
    val currentDisplay = display ?: return@launch
    val player = VideoPlayer(
        source = VideoSource.Url("https://example.com/tutorial.mp4"),
        codec = VideoCodec.MP4,
    )

    videoStateJob?.cancel()
    videoErrorJob?.cancel()

    videoStateJob = launch {
        player.state.collect { state ->
            if (state == VideoPlayerState.ENDED) {
                videoStateJob?.cancel()
                videoStateJob = null
                sendStatusCard(currentDisplay)
            }
        }
    }
    videoErrorJob = launch {
        player.error.collect { error ->
            if (error != null) {
                showError(error.description)
            }
        }
    }

    currentDisplay.sendContent { video(player = player) }
        .onSuccess { player.play() }
        .onFailure { error, _ -> showError(error.description) }
}
```

## Display rules

- Initialize DAT and complete registration before creating the session.
- Observe `Wearables.registrationErrorStream`, `session.errors`, `Display.sendContent` failures, and `VideoPlayer.error`; `DeviceSession.start()` returns `Unit` and reports async failures through `session.errors`.
- Use `SpecificDeviceSelector` for a user-picked device and `AutoDeviceSelector(filter = { it.isDisplayCapable() })` only when automatic selection is acceptable.
- Wait for `DeviceSessionState.STARTED` before calling `session.addDisplay()`.
- Wait for `DisplayState.STARTED` before enabling or sending user-triggered content.
- If device metadata reports `DeviceCompatibility.DEVICE_UPDATE_REQUIRED`, offer `Wearables.openFirmwareUpdate(activity)`.
- If session creation or `session.errors` reports `DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED`, offer `Wearables.openDATGlassesAppUpdate(activity)`.
- Keep display callbacks fast; move app state changes back onto your UI layer as needed.
- Cancel state/error collection jobs, close or replace active `VideoPlayer` instances, detach with `session.removeDisplay()`, and then stop the session when the display experience ends.

## Sample app

Use the Display Access sample app for a complete flow: registration, device selection, display attachment, interactive content, and video.

## Links

- [Android API reference](https://wearables.developer.meta.com/docs/reference/android/dat/0.7)
- [Developer documentation](https://wearables.developer.meta.com/docs/develop/)
