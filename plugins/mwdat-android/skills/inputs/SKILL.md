---
name: inputs
description: Receive navigation, button, capture, and drag input events from connected glasses on Android
---

# Inputs (Android)

Use the experimental Inputs capability to receive semantic interactions from glasses. The app must have Inputs enabled for it in Wearables Developer Center. Inputs does not have a `Permission` value to request at runtime.

## Attach after the session starts

Call `addInputs` only after `DeviceSessionState.STARTED`. Adding the capability starts it automatically; there is no separate public `start` call.

```kotlin
session.addInputs(
    InputsConfiguration(consumeBack = true),
).fold(
    onSuccess = { inputs ->
        inputEventsJob = lifecycleScope.launch {
            inputs.events.collect { event ->
                when (event) {
                    is InputEvent.Nav -> moveFocus(event.direction)
                    is InputEvent.Select -> activateFocusedItem()
                    is InputEvent.Back -> dismissCurrentView()
                    is InputEvent.Button -> handleButton(event.button)
                    is InputEvent.Capture -> handleCaptureButton(event.pressType)
                    is InputEvent.Drag -> updateDrag(
                        event.action,
                        event.x,
                        event.y,
                        event.dx,
                        event.dy,
                    )
                }
            }
        }
    },
    onFailure = { error, _ -> showError(error.description) },
)
```

Retain the event job while Inputs is attached. Collect `state` when the UI needs activation state and `errors` for activation, connection, and communication failures.

The default configuration enables every known source except `UNKNOWN` and consumes Back. Set `consumeBack` to `false` when the system should handle Back.

Each event includes its source and device timestamp in milliseconds. Keep the `when` exhaustive so new behavior is intentional.

## Errors and cleanup

`InputsError.PERMISSION_DENIED` means the capability was not approved for the app; it is not fixed with `requestPermission`. Other errors cover activation, connection, and communication failures.

Cancel the collection jobs, then call `session.removeInputs()` and handle its `DatResult` before adding another Inputs capability.

## Test with MockDeviceKit

After the app has attached Inputs, inject events through the paired mock glasses with calls such as `glasses.services.input.navDown()`. The service also exposes Select, Back, Button, Capture, and Drag injection. Calls made before Inputs is active, or from a source excluded by the configuration, are dropped.

## Availability

Inputs is experimental. Apps can use it for development and beta testing, but cannot publish it to production release channels yet.
