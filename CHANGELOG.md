# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The public API is covered by semantic versioning guarantees, but features labeled experimental may change between minor releases. They can be built and tested against, though apps using them cannot be published yet.

## [1.0.0] - 2026-09-24

### Added

- Muse Code support. Use your Muse Code tools to build with DAT.
- [Experimental] **Inputs.** The new `mwdat-inputs` module adds `Inputs`, reporting glasses input to a session as `InputEvent` values, described by `InputSource`, `NavDirection`, `DragAction`, `ButtonType` and `CapturePressType`, with `InputsConfiguration`, `InputsState` and `InputsError`.
- [Experimental] **Motion.** The new `mwdat-motion` module adds `Motion`, streaming device orientation and movement as `MotionSample` values built from `Vector3` and `Quaternion`, configured with `MotionConfiguration`, `MotionSamplingRate` and `MotionSource`, with `MotionState` and `MotionError`.
- [Experimental] **Speech.** The new `mwdat-speech` module adds `Speech`, delivering on-device transcription as `TranscriptionResult`, configured with `SpeechConfiguration` and reporting `SpeechState` and `SpeechError`. Requires `Permission.MICROPHONE`.
- [Experimental] **Voice invocations.** In `mwdat-core`, `VoiceInvocationsStream` delivers `VoiceInvocation` requests to a session, starting with `LaunchApp`, answered through `ResponseHandle`. `SessionState` and `VoiceInvocationError` report lifecycle and failures.
- [Experimental] **Photo capture.** In `mwdat-camera`, `Camera.photo` captures standalone high-quality photos, delivering `PhotoCaptureData` and reporting `PhotoTransferProgress` while the image transfers. `PhotoConfiguration`, `PhotoResolution` and `PhotoQuality` control capture; `PhotoState` and `PhotoError` report lifecycle and failures.
- [Experimental] **Camera audio streaming.** In `mwdat-camera`, `StreamConfiguration.audioCodec` and `Stream.audioStream` deliver `AudioFrame` data alongside video, described by `AudioCodec` and `AudioSampleRate`.
- [API] **Device state on `Device`.** New accessors `batteryLevel` (`Int`), `chargingState` (`ChargingState`), `donState` (`DonState`), `hingeState` (`HingeState`) and `thermalLevel` (`ThermalLevel`), observable via `Wearables.devicesMetadata[id]`.
- [API] `DeviceSession.deviceInfo: StateFlow<Device>` — the in-session device snapshot with live state updates.
- [API] `Wearables.handleIntent(intent, onRegistrationRequest)`, `RegistrationRequest` and `RegistrationRequestError` for accepting or cancelling registration requests initiated by Meta AI app.
- [API] `DeviceSessionError.INSUFFICIENT_SDK_VERSION`: A terminal error indicating that developers must release a newer version of their app built with the current SDK.
- [API] `DeviceSessionError.DWA_OUT_OF_STU_RANGE`: Adds a nonblocking compatibility warning. Apps can continue normally and may show a rate-limited update suggestion.
- [API] Display buttons support `ActionRole.PRIMARY`; the first primary action receives focus when content first renders.
- [Feature] **Mock display preview.** `GlassesModel.META_RAYBAN_DISPLAY` and `MockDisplayKit` render the Display session locally on the phone, with `createPreviewView(context)` for the preview view and `sendClick(identifier)` for click injection. Pairing uses the existing `pairGlasses` API.
- [Feature] MockDeviceKit gains `MockCameraCaptureKit`, `MockInputKit`, `MockMotionKit`, `MockSpeechKit` and `MockVoiceInvocationKit` so the new capabilities can be exercised without physical glasses, reachable from `MockGlassesServices`. Adds `MockDevice.setBatteryLevel(Int)`, `setChargingState(ChargingState)` and `setThermalLevel(ThermalLevel)` for simulating device state, and `startTestServer(port)` / `stopTestServer()` / `simulateRegistrationOutcome(Boolean)` with `MockDeviceKitError.TestServerUnavailable`.

### Changed

- [API] `Device` gained `thermalLevel`, `batteryLevel`, `chargingState`, `donState` and `hingeState` properties.

### Removed

- [API] `Wearables.getDeviceState(deviceIdentifier): StateFlow<DeviceState>` and the `DeviceState` type — device state now lives on `Device`. Observe `Wearables.devicesMetadata[id]` and read `device.thermalLevel` / `device.batteryLevel` / etc.

### Fixed

- MockDeviceKit stream-start rejections now report why they failed instead of failing without detail. A `setCameraFeed(CameraFacing)` feed blocked by a missing runtime `android.permission.CAMERA` grant now names that permission.

## [0.9.0] - 2026-08-03

### Added

- [API] **Consolidated `Camera` capability.** `DeviceSession.addCamera(streamConfiguration)` returns a `Camera` that owns the camera hardware resource and exposes its child features — `Camera.stream` — with `Camera.stop()` to detach and `CameraState` (`STARTING`, `STARTED`, `STOPPING`, `STOPPED`) reporting the camera lifecycle. Use `DeviceSession.removeCamera()` to detach.
- [API] **Display `buttonGroup` component.** New `FlexBoxScope.buttonGroup { }` (backed by `ButtonGroupScope.button(...)`) and `ButtonGroupAlignment` enum for laying out button groups within display content.
- [API] `FlexBoxScope.image(bitmap = ...)` for rendering local `Bitmap` images in display content, in addition to remote URL images.
- [API] Display tap/click handling: taps on display components are routed back to the app.
- [API] Crash-reporting opt-out via an `AndroidManifest.xml` `<meta-data>` entry — `<meta-data android:name="com.meta.wearable.mwdat.CRASH_REPORTING_OPT_OUT" android:value="true" />` disables DAT SDK crash reporting.
- Camera Access sample: record video with optional sound-in-video, continuing while the app is backgrounded.

### Changed

- [API] `DatResult` is now emitted as a Java-visible reference type instead of a Kotlin inline value class, so public DAT APIs such as `Wearables.initialize(...)`, `Wearables.createSession(...)`, `Stream.start()`, `Stream.capturePhoto(...)`, `Display.sendContent(...)`, and `MockDeviceKitInterface.pairGlasses(...)` can be found by Java callers under their source names.
- [API] `FlexBoxScope.image(...)` gained a `Bitmap` parameter for local images. This is a binary-breaking change to the `image(...)` signature.
- [API] `ContentScope.flexBox(...)` and `ContentScope.video(...)` no longer return `DisplayComponent`. Compose display trees purely through the builder blocks; the returned reference is no longer useful and has been dropped.
- Camera Access sample rebuilt around the explicit camera lifecycle (start session, start preview, capture or record, stop preview, end session) on a single full-bleed camera screen, consolidating the previous camera samples into one.

### Removed

- [API] `DeviceSession.addStream(...)` and `DeviceSession.removeStream()` — streaming is now reached through the consolidated `Camera`: use `DeviceSession.addCamera()` and access the stream via `Camera.stream` (and `removeCamera()` to detach). `Stream` is now a child of `Camera` and can no longer be added to a session directly.
- [API] Support for opting out of the Device Access Toolkit App Model (DAM). DAM is now always enabled, so the `com.meta.wearable.mwdat.DAM_ENABLED` manifest metadata key is ignored. No action is required; you can delete the `<meta-data .../>` entry from your `AndroidManifest.xml` at your convenience.
- [API] `DisplayComponent` interface. Display trees are now composed purely through builder blocks; the interface is no longer needed. (Ties to the `flexBox`/`video` return-type change under Changed.)
- [API] `VideoScope` and the `ContentScope.video(VideoPlayer) -> VideoScope` builder. Use `ContentScope.video(player) { ... }` (the `Unit`-returning builder form).
- [API] `StreamError.THERMAL_EMERGENCY`, `RegistrationError.INCOMPATIBLE_SDK_LEVEL`, `DisplayError.CAPABILITY_DENIED`, `DeviceSessionError.DEVICE_POWERED_OFF`, and `DeviceSessionError.NOT_INITIALIZED` enum cases.

### Fixed

- `MockDevice` phone-camera stream no longer stops after a few seconds.
- `mwdat-mockdevice` now ships `-dontwarn` ProGuard consumer rules so external Gradle apps that use `mockdevice` without `camera` build successfully under R8 (previously failed with missing-class errors on internal camera/streaming types).

## [0.8.0] - 2026-06-25

### Added

- Meta Glasses support.
- [Feature] **MockDeviceKit multi-glasses support.** `MockDeviceKit.pairGlasses(model)` is a single factory for all supported glasses models, returning `DatResult<MockGlasses, MockDeviceKitError>`, replacing `pairRaybanMeta()`.
  - `GlassesModel` enum (`RAYBAN_META`, `OAKLEY_META_HSTN`, `OAKLEY_META_VANGUARD`, `RAYBAN_META_OPTICS`, `META_GLASSES`).
  - `MockDeviceKitError.NotEnabled` for explicit error handling when MockDeviceKit is not enabled.
- [API] `DeviceType.META_GLASSES` value.
- [API] `StreamState.PAUSED` value for observing a paused camera stream.
- [API] `AutoDeviceSelector.activeDevice()` — returns the currently active `DeviceIdentifier` (parity with `SpecificDeviceSelector.activeDevice()`).
- [API] `Display.clearDisplay()` to clear rendered display content.

### Changed

- [API] Capabilities (`Stream`, `Display`) are now `Closeable` and expose a `stop()` method rather than implementing a `Capability` interface. The `Capability` and `BaseCapability` types are removed; manage capabilities directly through their `DeviceSession`.
- [API] Renamed `MockDisplaylessGlasses` to `MockGlasses` and `MockDisplaylessGlassesServices` to `MockGlassesServices`.

### Fixed

- D8 warnings about "companion object could not be found".
- Play Store warning about unknown language `fb` caused by pseudo-locale resources.

### Removed

- [API] `MockRaybanMeta` interface (use `MockGlasses`).
- [API] `pairRaybanMeta()` (use `pairGlasses(GlassesModel.RAYBAN_META)`).

## [0.7.0] - 2026-05-14

### Added

- [Feature] **Display capability** — `mwdat-display` brings visual experiences to Meta Ray-Ban Display glasses, with content rendering (FlexBox, Text, Button, Image, Icon) and MP4 video playback.
  - `Display` interface conforming to `Capability`, accessed via `DeviceSession.addDisplay(config)` / `DeviceSession.removeDisplay()`.
  - `DisplayConfiguration` for configuring a display session, plus typed `DisplayState` and `DisplayError` enums for observing display lifecycle and error handling.
  - `Display.sendContent { ... }` for building UI declaratively. Each call replaces the entire display; content is presented one view at a time with vertical scrolling only.
  - `FlexBoxScope` view builders: `flexBox`, `text`, `icon`, `image`, `button` with `Direction`, `Alignment`, `ButtonStyle`, `CornerRadius`, `IconName`, `IconStyle`, `ImageSize`, `TextColor`, `TextStyle`, `FlexBoxBackground` styling primitives.
  - `VideoPlayer` with `VideoSource.Url`, `VideoCodec.MP4`, plus typed `VideoPlayerState` and `VideoPlayerError` for observation.
- [Feature] **Device Access Toolkit App Model (DAM)** — a new architecture model for the SDK. Apps opt in by declaring `<meta-data android:name="com.meta.wearable.mwdat.DAM_ENABLED" android:value="true" />` in `AndroidManifest.xml`. DAM is required for the new Display capability; both the App Model flow and the older flow continue to be supported for camera functionality on Meta AI glasses.
  - `Wearables.openDATGlassesAppUpdate(activity)`: opens the Meta AI DAT app update destination for the configured app.
- [API] `Wearables.openFirmwareUpdate(activity)`: opens the Meta AI firmware update screen for the connected device.
- [API] `Wearables.getDeviceState(deviceIdentifier)`: returns a `StateFlow<DeviceState>` exposing live device state, including current `ThermalLevel`.
- [API] `Wearables.registrationErrorStream`: returns a `Flow<RegistrationError>` for observing registration errors out-of-band from `RegistrationState`.
- [API] `Wearables.isDevMode`: returns whether the SDK is configured for developer mode.
- [API] `DeviceSession.errors`: `SharedFlow<DeviceSessionError>` for observing session-scoped errors.
- [API] `DeviceState` data class with a `thermalLevel` property exposing per-device thermal status.
- [API] `ThermalLevel` enum exposing per-device thermal state.
- [API] `NavigationError` enum: typed error for the new `Wearables.openFirmwareUpdate` / `openDATGlassesAppUpdate` APIs.
- [API] `DeviceSessionError` (replaces `SessionError`) adds typed cases for thermal, battery, and peak-power conditions: `BATTERY_CRITICAL`, `PEAK_POWER_SHUTDOWN`, `THERMAL_CRITICAL`, `THERMAL_EMERGENCY`. Also adds `DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED` for surfacing required app updates.
- [API] `StreamError` cases: `BATTERY_LOW`, `CRITICAL_STREAM_ERROR`, `PEAK_POWER_LIMIT`, `THERMAL_EMERGENCY`, `THERMAL_HOT`, `TIMEOUT` for typed handling of streaming failures.
- [API] `DeviceType.isDisplayCapable` and `Device.isDisplayCapable()`: helpers to check whether a device or device type supports a display capability.
- [API] `SpecificDeviceSelector.activeDevice()`: returns the currently active `DeviceIdentifier` for the selector.
- [API] `Stream.errorStream` and `Stream.start()`: `Stream` now exposes a typed error stream and an explicit `start()` method.
- [API] `VideoFrame.isCodecConfig`: indicates whether a frame contains codec configuration data instead of payload video frames. Constructor signature changed to include the new flag.
- [Feature] **Captouch simulation:** `MockDeviceKit` now allows simulating `tap` and `tapAndHold` captouch gestures via the new `MockCaptouchKit` interface, accessible from `MockDisplaylessGlasses.services.captouch`.

### Changed

- [API] **Overhauled session management to make device sessions explicit.** The previously-implicit CoreUX session is now surfaced as `DeviceSession`, which serves as the entry point to interact with a glasses device — capabilities like `Stream` and `Display` are attached to a session via `DeviceSession.addStream(...)` / `addDisplay(...)`, and session state and errors are observed directly on the `DeviceSession` instance rather than via global `Wearables` APIs. This consolidates several renames:
  - `Session` class is now `DeviceSession`. Existing extension functions `addStream(...)` / `removeStream(...)` now take a `DeviceSession` receiver.
  - `SessionError` is now `DeviceSessionError` (with additional cases — see *Added*).
  - `SessionState` is now `DeviceSessionState`. State values are `IDLE`, `STARTING`, `STARTED`, `PAUSED`, `STOPPING`, `STOPPED` for finer lifecycle tracking.
  - `StreamSession` is now `Stream` (interface, conforming to `Capability`).
  - `StreamSessionState` is now `StreamState`.
  - The `Wearables.startStreamSession(...)` factory is removed; create a `DeviceSession` via `Wearables.createSession(...)`, then call `DeviceSession.addStream(config)` (and `removeStream()` to detach).
  - `Wearables.getDeviceSessionState(deviceIdentifier)` removed; observe state directly on the `DeviceSession` instance via `DeviceSession.state`.
- [API] `RegistrationState` reshaped from a sealed class hierarchy (`Available`, `Registered`, `Registering`, `Unavailable`, `Unregistering`) to a plain enum (`AVAILABLE`, `REGISTERED`, `REGISTERING`, `UNAVAILABLE`, `UNREGISTERING`). The previous registration-error payload has been moved to `Wearables.registrationErrorStream`.

### Fixed

- `Wearables.checkPermission()` (via `PermissionsSession`): fixed double-resume crash that could occur when a permission check is completed twice.
- `PermissionsSession`: fixed stale state after Bluetooth reconnection so subsequent permission checks reflect current device state.
- `Stream` (photo capture): added a timeout to photo capture to prevent permanent locking when the capture never completes.
- `MockDeviceKit`: `don()` and `fold()` now maintain consistent device state across cycles.
- `VideoFrame`: fixed buffer lifecycle when surfacing compressed video codec config frames.
- `ACDCRegistrationService`: fixed a leaked `ServiceConnection` during registration.

## [0.6.0] - 2026-04-15

### Added

- Ray-Ban Meta Optics glasses support.
- [Feature] `MockCameraKit` can use the phone camera (front and back) to simulate streaming with `MockCameraKit.setCameraFeed(CameraFacing)`.
- [Feature] `StreamConfiguration.compressVideo` property to enable compressed HEVC video streaming, bypassing decoding. The `VideoFrame.isCompressed` property indicates whether the frame data is compressed.
- [Feature] `MockDeviceKit` now supports configuration to simulate device registration and permissions.
  - `MockDeviceKitConfig` data class to configure `MockDeviceKit` initialization with `initiallyRegistered` and `initialPermissionsGranted` options.
  - `MockPermissions` interface with `set` and `setRequestResult` to simulate permission states in tests.
  - `MockDeviceKitInterface.enable(config)`, `disable`, `isEnabled`, and `permissions` for controlling MockDeviceKit lifecycle and permissions.
- [API] Session-based device management. Device interactions are now scoped to a `Session` with explicit lifecycle control.
  - `Wearables.createSession(deviceSelector)`: Creates a `Session` for a given `DeviceSelector`.
  - `Session` class with `start`, `stop`, state observation via `getState`, and error observation via `getErrors`.
  - `DeviceSessionState` enum with values `IDLE`, `STARTING`, `PAUSED`, `STOPPING`.
  - `SessionError` enum with typed error cases.
  - `Capability` interface for extending sessions with additional features such as camera streaming.
  - Camera streaming exposed as a `Capability`, as new `Stream` interface. Access to it with `Session.addStream(config)` and `Session.removeStream`.
- [API] `MockDisplaylessGlassesServices` interface grouping mock services, accessible via `MockDisplaylessGlasses.services`.

### Changed

- [API] Renamed `DeviceMetadata` data class to `Device`, making it consistent with iOS.
- [API] `DeviceSelector.activeDevice` returns `DeviceIdentifier` directly. `activeDeviceFlow` for the flow-based approach.
- [API] `MockDeviceKitInterface.reset` replaced with `enable` / `disable` for explicit lifecycle control.
- Improved the Camera Access App MockDevice UI.

### Fixed

- `MockDevice` better simulates state when a device is powered off or doffed.
- Runtime crashes when building with R8/minify enabled.
- `ClassCastException` when `com.meta.wearable.mwdat.APPLICATION_ID` manifest metadata is not parsed as a `String`.

### Removed

- [API] Removed old session model API, including `DeviceSession` class, in favor of the new `Session`.
- [API] `MockDisplaylessGlasses.getCameraKit` has been removed. The functionality is accessible through `MockDisplaylessGlasses.services`.
- Third-party library entries from `AndroidManifest.xml`.

## [0.5.0] - 2026-03-11


### Added

- [API] Sealed interface `CaptureError` for photo capture error handling with typed error cases: `DeviceDisconnected`, `NotStreaming`, `CaptureInProgress`, and `CaptureFailed`.
- [API] Enum `LinkState` representing device connectivity state with values `CONNECTING`, `CONNECTED`, and `DISCONNECTED`. Brings parity with iOS SDK.

### Changed

- [API] `StreamSession.capturePhoto()` now returns `DatResult<PhotoData, CaptureError>` instead of `Result<PhotoData>`.
- [API] `Device.linkState`: Replaces boolean `available` property with `LinkState` enum for richer connectivity state information.
- Improved Android video decoding and playback performance.
- [CameraAccess] Removed timer functionality.

### Fixed

- Fixed R8 build errors when minify is enabled.
- Improved `DeviceSession` accuracy.
- Fixed duplicate class errors when building with React Native.
- Improved audio and video packet deserialization.
- High resolution (720x1280) video can be requested.

## [0.4.0] - 2026-02-03

> **Note:** This version requires updated configuration values from Wearables Developer Center for release channel functionality.

### Added

- Meta Ray-Ban Display glasses support.
- [API] `AutoDeviceSelector` includes new `filter` property. Defaults to filter out incompatible devices.
- [API] `presentationTimeUs` property to `VideoFrame`.

### Changed

- The registration dialog now opens in place, instead of jumping to Meta AI app.
- [API] `Wearables.startRegistration` and `Wearables.startUnregistration` accept an Activity
instead of a Context.

### Removed

- Removed timer functionality in Camera Access app.

### Fixed

- The correct state is now reported after unregistering the application.
- Improved stream latency, which was degrading over time.

## [0.3.0] - 2025-12-16

### Added

- [API] Result-like object (`DatResult`) used to return `Error` from some methods.
- [API] `ALREADY_INITIALIZED` error to `WearablesError`.

### Changed

- [API] Permission functions now return `DatResult<PermissionStatus, PermissionError>` instead of `PermissionStatus`.
- [API] In `PermissionError`, `COMPANION_APP_NOT_INSTALLED` has been renamed to `META_AI_NOT_INSTALLED`.
- The Camera Access app streaming UX reflects device availability.
- The Camera Access app shows errors when incompatible glasses are found.

### Fixed

- Fixed orientation of images captured by `MockDevice`.
- Streaming status becomes `stopped` when permission is not granted.
- Fixed invalidation of flow from `Wearables.getDeviceSessionState` after streaming stops.
- Fixed UI issues in the Camera Access app.

### Removed

- [API] `Error` data class from `PermissionStatus`.

## [0.2.1] - 2025-12-04

### Changed

- The Camera Access app now correctly processes orientation metadata in HEIC images.

## [0.2.0] - 2025-11-18

### Added

- [API] Base classes for errors (`DatError`) and exceptions (`DatException`) of the SDK.
- [API] New `WearablesError` and `WearablesException` types.
- [API] Configurable frame rate for the video stream. Valid values include 30, 24, 15, 7 and 2 fps.
- [API] `AutoDeviceSelector` constructor now accepts a device ranking function to influence device selection.
- [API] A description (string) to enum types.
- [API] `DeviceMetadata` includes new fields for compatibility and firmware info versions.

### Changed

- [API] The SDK now splits into components for independent project inclusion.
- [API] Calling any `Wearables` function without initialization throws a WearablesException.
- [API] Permission API updated for better consistency with iOS:
  - `checkPermission` renamed to `checkPermissionStatus`.
  - `AskPermissionContract` renamed to `RequestpermissionContract`.
  - `PermissionGrantState` replaced by `PermissionStatus`, with values `GRANTED` and `DENIED`.
  - Updated the set of values of `PermissionError`.
- [API] Permission requests now return an `Error` instead of throwing exceptions.
- [API] `RegistrationError` now holds different errors, aligning more closely with the iOS SDK.
- [API] `DeviceSelector`'s select method replaced by an active device Flow.
- [API] Renamed `DeviceType` enum values.
- [API] Replaced `MockDevice` `UUID` with `DeviceIdentifier`.
- `AutoDeviceSelector` now selects or drops devices based on connectivity state.
- Adaptive Bit Rate (streaming) updated to use provided resolution and frame rate hints.
- Camera Access app redesigned and updated to the current SDK version.

### Removed

- [API] `PermissionException`.
- [API] `onDeviceName` method on `Permission`.

### Fixed

- Sessions now close properly when the connection with the glasses is lost.
- The requested video quality is now correctly applied to the stream.

## [0.1.0] - 2025-10-30

### Added

- First version of the Wearables Device Access Toolkit for Android.
