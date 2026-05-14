# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
