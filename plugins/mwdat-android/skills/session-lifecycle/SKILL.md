---
name: session-lifecycle
description: Session state, stream state, pause and resume behavior, and device availability monitoring
---

# Session Lifecycle (Android)

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
