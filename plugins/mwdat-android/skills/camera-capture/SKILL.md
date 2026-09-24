---
name: camera-capture
description: Capture standalone high-quality photos from connected glasses on Android
---

# Camera Capture (Android)

Camera Capture is the experimental standalone high-quality photo path exposed by `camera.photo`. It is different from `stream.capturePhoto()`, which takes a photo while video streaming remains active.

## Prepare Camera and permission

The app needs Camera approval in Wearables Developer Center and `Permission.CAMERA`. Add Camera only after `DeviceSessionState.STARTED`. Stream and standalone Photo compete for camera hardware, so stop an active Stream before accessing Photo.

## Observe Photo

Accessing `camera.photo` lazily creates and starts the standalone Photo child. Collect its flows and wait for `PhotoState.STARTED` before enabling capture.

```kotlin
val photo = camera.photo

photoStateJob = lifecycleScope.launch {
    photo.state.collect { state ->
        photoButtonEnabled = state == PhotoState.STARTED
    }
}
photoDataJob = lifecycleScope.launch {
    photo.photoStream.collect { capture ->
        save(capture.imageData, capture.metadata)
    }
}
progressJob = lifecycleScope.launch {
    photo.transferProgressStream.collect { progress -> updateProgress(progress) }
}
errorJob = lifecycleScope.launch {
    photo.errors.collect { error -> showError(error.description) }
}
```

After Photo reaches `PhotoState.STARTED`, call `photo.capturePhoto(resolution = PhotoResolution.FULL, quality = PhotoQuality.HIGH)` with the desired resolution and quality. Capture is asynchronous; data arrives through `photoStream`. Collect `transferProgressStream` for progress and `errors` for failures.

## Errors and cleanup

Handle not-ready, capture, setup, disconnect, busy, service, permission, and device-health errors from `errors`. After delivery, stop Photo, cancel its collectors, and stop Camera.

Photo is terminal after `stop()`, and accessing `camera.photo` again returns the same stopped instance. To capture again, remove the current Camera from the session, add a new Camera, and access Photo from that new instance. Stopping Camera or the parent session also cascades cleanup.

## Test with MockDeviceKit

Configure the next image with `glasses.services.cameraCapture.setCapturedPhoto(imageUri)` or use `simulateCaptureFailure()` for a one-shot failure.

## Availability

Standalone Camera Capture is experimental. Apps can use it for development and beta testing, but cannot publish it to production release channels yet.
