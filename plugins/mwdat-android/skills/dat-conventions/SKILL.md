---
name: dat-conventions
description: Kotlin patterns, DatResult, session and capability conventions for DAT SDK Android development
---

# DAT SDK Conventions (Android)

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

See the `dat-docs-mcp` skill for client setup and troubleshooting. Use `llms.txt` when your tool only supports static reference context.

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
