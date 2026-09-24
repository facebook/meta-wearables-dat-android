---
name: motion
description: Stream accelerometer, gyroscope, magnetometer, and orientation samples from connected wearables on Android
---

# Motion (Android)

Motion is an experimental capability for streaming inertial sensor samples. Enable it for the app in Wearables Developer Center. It has no runtime `Permission` value.

## Attach, collect, and start

Call `addMotion` only after `DeviceSessionState.STARTED`. The returned capability starts in `STOPPED`; launch collectors before calling `start()`.

```kotlin
session.addMotion(
    MotionConfiguration(samplingRate = MotionSamplingRate.HZ_30),
).fold(
    onSuccess = { motion ->
        motionSampleJob = lifecycleScope.launch {
            motion.samples.collect { sample -> render(sample) }
        }
        motion.start()
    },
    onFailure = { error, _ -> showError(error.description) },
)
```

Retain the sample job while streaming. Collect `state` for lifecycle updates and `errors` for sensor, connection, and closure failures.

Sampling rates are 5, 10, 15, 24, 30, and 60 Hz; 10 Hz is the default.

## Read samples defensively

`timestampNs` uses the device monotonic clock. Accelerometer values are m/s², gyroscope values are rad/s, and magnetometer values are µT. Each sensor value and orientation can be `null`; `source` identifies glasses, Neural Band, or an unknown source.

Motion normally transitions `STOPPED -> STARTING -> STARTED`. When its parent session pauses, it reports `MotionState.PAUSED` and automatically restarts when the session resumes.

## Stop and remove

`motion.stop()` is reusable. Cancel the sample job before calling `session.removeMotion()` and handle its `DatResult`; removal is terminal.

## Test with MockDeviceKit

Replay an in-memory recording with `glasses.services.motion.setMotionFeed(samples, loop = true)` or provide a CSV file URI. App logic should observe the SDK Motion state rather than `isStreaming`, which is a test assertion helper.

## Availability

Motion is experimental. Apps can use it for development and beta testing, but cannot publish it to production release channels yet.
