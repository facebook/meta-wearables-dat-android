# Display Access App

A sample app demonstrating how to connect to display-capable Meta AI glasses, automatically select a device, attach the display capability, and render guided content on the glasses.

## Features

- Register and connect to Meta wearable devices
- Automatically select a connected display-capable device when a sample starts
- Browse and start a sample before creating a device session
- Send a guided car maintenance experience to the glasses
- Preview display content on the phone with MockDeviceKit's bundled React renderer
- Preview the same mock display in Chrome with browser click events routed back to the app
- Open firmware and DAT glasses app update flows when required

## Prerequisites

- Android Studio Narwhal (2025.1.1) or newer
- JDK 17 or newer
- Android SDK 36 or newer
- Meta Wearables Device Access Toolkit (included as a dependency)
- Display-capable Meta AI glasses, or the built-in phone preview for local testing
- Chrome with the DAT display preview extension installed for browser preview

## Setup

1. Open the project in Android Studio or use the Gradle wrapper.
2. Add your credentials to `local.properties`.
3. Build and run the sample.

Example `local.properties` values:

```properties
mwdat_application_id=YOUR_APPLICATION_ID
mwdat_client_token=YOUR_CLIENT_TOKEN
```

## Building the app

### Using Android Studio

1. Clone this repository
1. Open the project in Android Studio
1. Click **File** > **Sync Project with Gradle Files**
1. Click **Run** > **Run...** > **app**

### Using Gradle

```bash
./gradlew installDebug
```

## Running the app

1. Launch the app on your Android device.
1. Complete app registration when prompted.
1. Open the Samples tab and tap "Try it." The app automatically selects a connected display-capable device, starts a session, and sends the tutorial when the display is ready.
1. For local testing, open "Developer preview" on the sample screen and choose Chrome or In app.
1. For Chrome preview, start the preview server, run the displayed `adb forward` command, open the displayed URL in Chrome, and enable the Meta Ray-Ban Display Simulator extension in Chrome.
1. If a firmware update is required, tap "Update firmware" on the connection screen.
1. If session start reports that the app on the glasses is outdated, tap "Update app on glasses" on the connection screen.

## Architecture

- `app/src/main/java/.../MainActivity.kt`: App entry point and runtime permission handling
- `app/src/main/java/.../wearables/WearablesViewModel.kt`: Registration, device observation, and developer preview state
- `app/src/main/java/.../display/DisplayViewModel.kt`: Automatic device selection, session lifecycle, capability attachment, and display content
- `app/src/main/java/.../ui/AppScaffold.kt`: Navigation between settings and samples, including developer preview coordination

## Permissions

- `BLUETOOTH_CONNECT`: Required to communicate with paired wearable devices
- `BLUETOOTH`: Required for Bluetooth-based device discovery and connectivity on supported Android versions
- `INTERNET`: Required by the DAT stack and related services used during wearable communication

## Troubleshooting

For issues related to the Meta Wearables Device Access Toolkit, please refer to the [developer documentation](https://wearables.developer.meta.com/docs/develop/dat/) or visit our [discussions forum](https://github.com/facebook/meta-wearables-dat-android/discussions)

## License

This source code is licensed under the license found in the LICENSE file in the root directory of this source tree.
