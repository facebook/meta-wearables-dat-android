---
name: speech
description: Recognize speech on connected glasses and consume partial and final transcriptions on Android
---

# Speech (Android)

Speech is an experimental on-device recognition capability. Enable it for the app in Wearables Developer Center and obtain `Permission.MICROPHONE` before starting it.

## Permission flow

Check permission before attaching Speech. Launch `Wearables.RequestPermissionContract` only from a user-initiated action; it opens the Meta AI app. Android `RECORD_AUDIO` is not required for real-glasses recognition. It is needed only when a MockDevice test deliberately uses live phone recognition.

## Attach, collect, and start

Call `addSpeech` after `DeviceSessionState.STARTED`. Launch collectors before `start()`.

```kotlin
session.addSpeech().fold(
    onSuccess = { speech ->
        transcriptionJob = lifecycleScope.launch {
            speech.transcriptions.collect { result ->
                result ?: return@collect
                if (result.isFinal) commitTranscript(result.text)
                else showPartialTranscript(result.text)
            }
        }
        speech.start().onFailure { error, _ -> showError(error.description) }
    },
    onFailure = { error, _ -> showError(error.description) },
)
```

Retain the transcription job while Speech is attached. Collect `state`, `locale`, and `errors` when the app needs lifecycle, locale, or failure updates.

`confidence` is between 0 and 1, or `-1` when unavailable. `errorDetails` is diagnostic text and should not be displayed directly to end users.

## Stop and remove

`speech.stop()` is reusable; removal is terminal. Cancel all collection jobs before `session.removeSpeech()`.

## Test with MockDeviceKit

After Speech is listening, inject results with calls such as `glasses.services.speech.simulateTranscription("turn left", isFinal = true, confidence = 0.9f)`. The service can also inject locale changes, partial text, completion, and terminal errors. The live-device source and host `RECORD_AUDIO` permission are only for manual MockDevice testing.

## Availability

Speech is experimental. Apps can use it for development and beta testing, but cannot publish it to production release channels yet.
