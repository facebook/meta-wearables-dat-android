# Display Access App

A DAT SDK external sample app demonstrating how to connect to a display-capable Meta wearable, start a session for a selected device, attach the display capability, and render guided content on the glasses.

## Features

- Register and connect to Meta wearable devices
- Select a specific display-capable device from the device list
- Automatically move to the samples list when a device session starts
- Browse the sample entry screen even before the display capability is ready
- Keep `Try it` disabled until the display capability is ready
- Send a guided car maintenance experience to the glasses
- Open firmware and DAT glasses app update flows when required

## Prerequisites

- Android Studio Arctic Fox (2021.3.1) or newer
- JDK 11 or newer
- Android SDK 31+ (Android 12.0+)
- Meta Wearables Device Access Toolkit dependencies available in the workspace
- A display-capable Meta wearable device for end-to-end testing

## Setup

1. Open the project in Android Studio or use the exported Gradle wrapper.
2. Add your app credentials to `local.properties` or export them in your environment.
3. Build and run the sample.

Example `local.properties` values:

```properties
github_token=YOUR_GITHUB_TOKEN
mwdat_application_id=YOUR_APPLICATION_ID
mwdat_client_token=YOUR_CLIENT_TOKEN
```

## Building the app

### Using the exported Gradle project

```bash
./gradlew assembleDebug
```

## Running the app

1. Launch the app on your Android device.
1. Complete app registration when prompted.
1. Tap a connected display-capable device in the list.
1. The app opens the samples list automatically after the session starts.
1. `Try it` stays disabled while the display capability is preparing.
1. Once the display is ready, tap `Try it` to send the tutorial flow to the glasses.
1. If a firmware update is required, tap `Update firmware` on the connection screen.
1. If session start reports that the app on the glasses is outdated, tap `Update app on glasses` on the connection screen.

## Architecture

- `app/src/main/java/.../MainActivity.kt`: App entry point and runtime permission handling
- `app/src/main/java/.../wearables/WearablesViewModel.kt`: Registration and device observation state
- `app/src/main/java/.../display/DisplayViewModel.kt`: Session lifecycle, capability attachment, and display content
- `app/src/main/java/.../ui/AppScaffold.kt`: Navigation between settings and samples, including automatic handoff after session start

## Permissions

- `BLUETOOTH_CONNECT`: Required to communicate with paired wearable devices
- `BLUETOOTH`: Required for Bluetooth-based device discovery and connectivity on supported Android versions
- `INTERNET`: Required by the DAT stack and related services used during wearable communication

## Distribution Notes

If `mwdat-display` is not yet available in the external Maven channel you target, the checked-in Gradle project still serves as the correct 3P app structure, but public distribution depends on that artifact being published for your release channel.

## Troubleshooting

For DAT SDK issues, refer to the developer documentation:

- https://wearables.developer.meta.com/docs/develop/

## License

This source code is licensed under the license found in the LICENSE file in the root directory of this source tree.
