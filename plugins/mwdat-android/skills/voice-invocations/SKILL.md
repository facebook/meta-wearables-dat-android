---
name: voice-invocations
description: Launch and activate an Android app from Hey Meta voice invocations and acknowledge each action
---

# Voice Invocations (Android)

Voice Invocations lets Meta AI launch or foreground an app when the wearer says “Hey Meta, start {app name}.” It is a Wearables-level stream, not a `DeviceSession` capability, and does not require a running device session.

## Configure the app

Register the package name, request Voice Invocation approval, and configure the spoken app name in Wearables Developer Center. Do not declare invocation phrases in the manifest. Voice Invocations does not require camera or microphone permission.

## Detect cold and warm launches

Use the SDK validator in both entry points. Do not inspect intent extras yourself.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (isVoiceInvocationsIntent(intent)) startYourExperience()
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (isVoiceInvocationsIntent(intent)) startYourExperience()
}
```

## Start and retain the stream

Store one app-scoped stream. The factory starts it automatically; there is no separate public `start` call.

```kotlin
stream = Wearables.startVoiceInvocationsStream(AutoDeviceSelector())

invocationJob = appScope.launch {
    stream.invocations.collect { invocation ->
        when (invocation) {
            is LaunchApp -> {
                navigateToMainScreen()
                invocation.responseHandle.sendSuccess(actionOutput = null)
            }
        }
    }
}
```

Collect `state` and `errors` when the app needs lifecycle or failure updates.

Every delivered invocation must be answered exactly once with `sendSuccess` or `sendFailure`. Both are suspend functions returning whether delivery succeeded.

The stream reports `STARTING`, `STARTED`, and `STOPPED`. Creation and automatic startup do not throw; failures surface through `errors` and state.

## Close and test

`close()` is terminal. Cancel collectors before closing the app-scoped stream when the app no longer listens.

To test without hardware, check `glasses.services.voiceInvocation.hasConnectedApps()` before calling `simulateLaunchAppAction()`. Use `simulateIncompleteAction()` to verify malformed actions surface an error rather than an invocation.

## Availability

Voice Invocations is experimental. Apps can use it for development and beta testing, but cannot publish it to production release channels yet.

See the [Voice Invocations guide](https://wearables.developer.meta.com/docs/develop/dat/voice-invocations/).
