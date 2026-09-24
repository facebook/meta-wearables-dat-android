---
name: camera-streaming
description: Session and Stream capability setup, video frames, photo capture, resolution and frame rate configuration
---

# Camera Streaming (Android)

Use a `DeviceSession` and an attached `Camera` to receive frames and capture photos through `camera.stream`.

For PCM audio delivered with this stream, use the `audio-streaming` skill.

## Key concepts

- **DeviceSession**: Device connection lifecycle created through `Wearables.createSession(...)`
- **Camera**: Camera capability attached to a session with `session.addCamera(...)`
- **Stream**: Video stream accessed through `camera.stream`
- **StreamConfiguration**: Video quality, frame rate, and compression configuration for the stream
- **PhotoData**: Still image captured from glasses while streaming

## Create a session and attach a stream

`DeviceSession.start()` is fire-and-forget: it returns `Unit` and the connection completes in the background. A capability can only be added once the session reports `DeviceSessionState.STARTED` — calling `addCamera(...)` right after `start()` fails with `DeviceSessionError.SESSION_IDLE`. Add the camera from the session-state collector.

```kotlin
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSessionState

var camera: Camera? = null

Wearables.createSession(AutoDeviceSelector()).fold(
    onSuccess = { session ->
        lifecycleScope.launch {
            session.errors.collect { error -> showError(error.description) }
        }
        lifecycleScope.launch {
            session.state.collect { state ->
                if (state == DeviceSessionState.STARTED && camera == null) {
                    session.addCamera(
                        StreamConfiguration(
                            videoQuality = VideoQuality.MEDIUM,
                            frameRate = 24,
                        ),
                    ).fold(
                        onSuccess = { addedCamera ->
                            camera = addedCamera
                            addedCamera.stream.start().onFailure { error, _ ->
                                showError(error.description)
                            }
                        },
                        onFailure = { error, _ -> showError(error.description) },
                    )
                }
            }
        }
        // Subscribe before start() so no initial transition is missed.
        session.start()
    },
    onFailure = { error, _ -> showError(error.description) },
)
```

Check `Wearables.checkPermissionStatus(Permission.CAMERA)` before starting the stream; see the `permissions-registration` skill.

### Resolution options

| Quality | Size |
|---------|------|
| `VideoQuality.HIGH` | 720 x 1280 |
| `VideoQuality.MEDIUM` | 504 x 896 |
| `VideoQuality.LOW` | 360 x 640 |

### Frame rate options

Valid values: `2`, `7`, `15`, `24`, `30` FPS.

Lower resolution and frame rate usually produce better visual quality per frame over Bluetooth.

## Observe stream state

`StreamState` transitions: `STOPPED` -> `STARTING` -> `STARTED` -> `STREAMING` -> `STOPPING` -> `STOPPED`, and `CLOSED` once the stream is terminal. `PAUSED` is reported when the device pauses the stream, for example on a single cap-touch tap; the stream can resume on its own from `PAUSED`.

```kotlin
lifecycleScope.launch {
    camera.stream.state.collect { state ->
        when (state) {
            StreamState.STREAMING -> {
                // Frames are flowing
            }
            StreamState.PAUSED -> {
                // Paused by the device; wait for it to resume
            }
            StreamState.STOPPED -> {
                // Streaming ended
            }
            StreamState.CLOSED -> {
                // Stream fully closed
            }
            else -> Unit
        }
    }
}
```

Observe `camera.stream.errorStream` alongside the state. `StreamError.STREAM_ERROR` is informational and does not stop the stream, while `StreamError.CRITICAL_STREAM_ERROR` means the stream should be torn down.

```kotlin
lifecycleScope.launch {
    camera.stream.errorStream.collect { error ->
        showStreamError(error.description)
    }
}
```

## Receive frames

```kotlin
lifecycleScope.launch {
    camera.stream.videoStream.collect { frame ->
        updatePreview(frame)
    }
}
```

By default the SDK decodes on the phone and `VideoFrame.buffer` holds YUV pixel data. Set `StreamConfiguration(compressVideo = true)` to receive compressed HEVC buffers instead; then check `frame.isCompressed` and `frame.isCodecConfig` and feed the frames to your own decoder. Keep frame handling off the main thread for anything heavier than a buffer copy.

## In-stream photo capture

This path captures while video streaming remains active. For standalone high-quality capture with resolution, quality, transfer progress, and its own lifecycle, use the `camera-capture` skill.

`capturePhoto()` only succeeds while the stream is active, and returns a `PhotoData` sealed type — branch on the variant instead of reading a single `data` property.

```kotlin
import com.meta.wearable.dat.camera.types.PhotoData

lifecycleScope.launch {
    camera.stream.capturePhoto()
        .onSuccess { photoData ->
            when (photoData) {
                is PhotoData.Bitmap -> savePhoto(photoData.bitmap)
                is PhotoData.HEIC -> saveHeic(photoData.data) // ByteBuffer of HEIC bytes
            }
        }
        .onFailure { error, _ ->
            showCaptureError(error.description)
        }
}
```

Only one capture can be in flight at a time; a second concurrent call fails with `CaptureError.CaptureInProgress`.

## Clean up

Stop the camera when you no longer need camera data, then stop the parent session if the device interaction is finished. Stopping the camera cascades to its stream, and stopping the session cascades to every attached capability.

```kotlin
camera.stop()
session.stop()
```

`Camera.stop()` and `Stream.stop()` invalidate the instance — they cannot be restarted. Call `session.removeCamera()` to detach the capability so a later `session.addCamera(...)` on the same session can succeed.

## Links

- [Android API reference](https://wearables.developer.meta.com/docs/reference/android/dat/latest)
- [Integration guide](https://wearables.developer.meta.com/docs/build-integration-android)
