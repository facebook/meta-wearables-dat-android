---
name: debugging
description: Common issues, Developer Mode, version compatibility, and session and stream diagnosis
---

# Debugging (Android)

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
+-- Did createSession() or addStream() return a DatResult failure? -> Surface the typed error
```

## Developer Mode

Developer Mode must be enabled for local development builds that use `APPLICATION_ID = 0`.

### Symptoms when Developer Mode is disabled

- Registration flow completes but the device never becomes eligible
- Permission requests do not succeed for development builds
- `Wearables.createSession(...)` fails with no eligible device

### Watch for

- Developer Mode may reset after app or firmware updates
- Developer Mode is configured per linked device
- Production builds use a real `APPLICATION_ID` and release-channel gating instead

## Session and stream issues

### Session never reaches `STARTED`

- Verify `Wearables.registrationState`
- Check that `Wearables.devices` contains a compatible linked device
- Ensure the glasses are powered on, unfolded, and in range

### Stream never reaches `STREAMING`

- Confirm `session.start()` succeeded before calling `session.addStream(...)`
- Check camera permission status through `Wearables.checkPermissionStatus(...)`
- Make sure `stream.start()` returned success

### Photo capture fails

- `capturePhoto()` only succeeds while the stream is actively streaming
- Surface the returned `CaptureError` instead of discarding the `DatResult`

## Version compatibility

Ensure compatible versions of the SDK, Meta AI app, and glasses firmware. See [version dependencies](https://wearables.developer.meta.com/docs/version-dependencies) for the current compatibility matrix.

## Logging

```kotlin
private const val TAG = "DATWearables"

stream.start()
    .onFailure { error, _ -> Log.e(TAG, "Failed to start stream: ${error.description}") }
```

Prefer logging typed `DatResult` failures and observed state transitions over generic exceptions.

## Checklist

- [ ] `Wearables.initialize(context)` ran before SDK usage
- [ ] Developer Mode enabled for development builds
- [ ] `APPLICATION_ID` matches the build mode
- [ ] Registration completed before session creation
- [ ] Bluetooth permission granted
- [ ] Camera permission granted through Meta AI
- [ ] Session and stream `DatResult` failures are surfaced in logs or UI

## Links

- [Known issues](https://wearables.developer.meta.com/docs/knownissues)
- [Version dependencies](https://wearables.developer.meta.com/docs/version-dependencies)
- [Troubleshooting discussions](https://github.com/facebook/meta-wearables-dat-android/discussions)
