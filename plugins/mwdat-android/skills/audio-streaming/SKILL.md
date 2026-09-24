---
name: audio-streaming
description: Receive synchronized PCM audio frames through a Camera stream on Android
---

# Audio Streaming (Android)

Audio Streaming is an experimental extension of Camera Stream, not a separate capability. Follow the `camera-streaming` skill for session and Camera setup, then add audio configuration and collection.

## Permissions

The app needs Camera and Audio Streaming approval in Wearables Developer Center plus both `Permission.CAMERA` and `Permission.MICROPHONE`. Check first and launch permission requests only after a user action.

## Configure PCM audio

Set a non-null codec in `StreamConfiguration`:

```kotlin
val configuration = StreamConfiguration(
    audioCodec = AudioCodec.PCM(
        sampleRate = AudioSampleRate.RATE_16000,
        numberOfChannels = 1,
    ),
    videoQuality = VideoQuality.MEDIUM,
    frameRate = 24,
)
```

Pass this configuration to `addCamera` using the Camera Streaming flow. Then collect audio before starting its Stream:

```kotlin
audioJob = lifecycleScope.launch(Dispatchers.IO) {
    camera.stream.audioStream.collect { frame ->
        consumePCM(frame.buffer, frame.presentationTimeUs)
    }
}
camera.stream.start().onFailure { error, _ -> showError(error.description) }
```

Supported sample rates are 16,000, 44,100, and 48,000 Hz. Configure downstream processing with the same rate and channel count. Use `presentationTimeUs` to preserve order or align audio with video. Process PCM off the main thread because slow collectors can drop frames.

Audio shares Stream state and errors. A microphone denial is surfaced as a stream permission failure; do not model a separate audio lifecycle.

## Cleanup and testing limits

Cancel audio and any other Stream collectors before calling `camera.stop()`. Stopping Camera cascades to its Stream, and the instances cannot be restarted.

MockDeviceKit can negotiate an audio-enabled Camera stream, but it does not provide a public deterministic audio-frame injection API. Do not assume mock PCM frames will arrive.

## Availability

Audio Streaming is experimental. Apps can use it for development and beta testing, but cannot publish it to production release channels yet.
