# BirdSpotter — Meta Wearables Device Access Toolkit Sample

A companion sample app showcasing the Device Access Toolkit (DAT) capabilities for Meta
smart glasses.

**BirdSpotter** is a Merlin-style bird spotting app: a polished, production-grade mobile app
that a developer has "extended to the glasses." Press the capture button to photograph a
bird, listen for its song with ambient bird-call detection, let the on-glasses IMU tag
whether you're scanning the canopy or the ground, ask about it by voice, and read the result
on the heads-up display (on display models).

It runs end-to-end against a simulated Ray-Ban Meta device via **MockDeviceKit**, so you can
exercise every capability without physical glasses. The same SDK code paths drive the
simulated device and real glasses; only the mock control panel is specific to testing.

> **Bird identification is intentionally mocked** (no LLM, no CV). The purpose is to
> demonstrate sensor access, pipeline handoffs, and UI polish — not ornithology. The demo
> maps a handful of scripted results (a Robin by song, a Green Jay by description) to bundled
> species data — a feature, not a bug.

## DAT capabilities demonstrated

- **Camera** — photo capture and video streaming from the glasses camera
- **Motion (IMU)** — head-orientation "aim" that tags canopy vs. ground scanning
- **Inputs** — touchpad / button / capture events from the glasses
- **Speech (ASR)** — ask about a bird by voice
- **Display** — result cards rendered on the heads-up display (display-capable models)

Some capabilities require an eligible SDK release channel. See the
[developer documentation](https://wearables.developer.meta.com/docs/develop/dat/) for
current availability.

## Prerequisites

- Android Studio Narwhal (2025.1.1) or newer
- JDK 17 or newer
- Android SDK 36 or newer
- Meta Wearables Device Access Toolkit (included as a dependency)
- A Meta AI glasses device is optional — the sample runs fully against a simulated device
  via MockDeviceKit

## Setup

Machine-local values are read from `local.properties` (gitignored, created by Android
Studio). Add your own values there — every key is optional and falls back to a working
default:

```properties
# DAT attestation identifiers from your Wearables Developer Center project. Omitted, they
# default to "0" — the Developer Mode placeholder, which is all BirdSpotter needs, since
# Developer Mode skips attestation.
mwdat_application_id=YOUR_MWDAT_APPLICATION_ID
mwdat_client_token=YOUR_MWDAT_CLIENT_TOKEN

# Google Maps SDK for Android. Without a key the app still builds and runs; only the
# sighting map renders blank.
MAPS_API_KEY=YOUR_ANDROID_MAPS_API_KEY
```

## Building the app

### Using Gradle

```bash
./gradlew installDebug
```

### Using Android Studio

1. Open the project in Android Studio
2. **File** > **Sync Project with Gradle Files**
3. **Run** > **Run...** > **app**

## The bird catalog

The field guide the app ships — `app/src/main/assets/catalog.db` plus the photo and audio
files under `app/src/main/assets/birds/` — is a committed, prebuilt static asset. The app
opens `catalog.db` read-only via Room's `createFromAsset()`; there is no build-time
generation step.

## Architecture

- `app/src/main/java/.../MainActivity` and `.../navigation/`: entry point, composition root, navigation
- `app/src/main/java/.../data/`: repositories and SDK-backed implementations (`dat/`, `mockdevice/`), plus the bundled catalog, journal, audio, camera, display, and location sources
- `app/src/main/java/.../domain/`: models and the mirrored capability surface
- `app/src/main/java/.../features/`: Explore, the Identify wizard, the live session, the Journal, bird detail, settings, and the Demo Director
- `app/src/main/java/.../ui/`: shared Compose components, theme, and previews

## Permissions

- `BLUETOOTH_CONNECT`: communicate with paired wearable devices
- `INTERNET`: required by the DAT stack and related services
- `CAMERA` / `RECORD_AUDIO`: the phone-side fallbacks for capture and audio, and Android
  speech recognition when MockDeviceKit runs live Speech on the phone
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: tag sightings on the map

With real glasses, camera and audio are captured on the glasses; the wearable microphone
and camera are granted at runtime through the in-app DAT permission flow.

## Troubleshooting

For issues related to the Meta Wearables Device Access Toolkit, refer to the
[developer documentation](https://wearables.developer.meta.com/docs/develop/dat/) or visit
our [discussions forum](https://github.com/facebook/meta-wearables-dat-android/discussions).

## License

This source code is licensed under the terms in the [LICENSE](LICENSE) file; third-party content is credited in [NOTICE](NOTICE) and [licenses/](licenses/).
