# Meta Wearables Device Access Toolkit for Android

[![Maven Central](https://img.shields.io/maven-central/v/com.meta.wearable/mwdat-core?logo=apachemaven&color=brightgreen)](https://central.sonatype.com/namespace/com.meta.wearable)
[![Docs](https://img.shields.io/badge/API_Reference-latest-blue?logo=meta)](https://wearables.developer.meta.com/docs/reference/android/dat/latest)

The Meta Wearables Device Access Toolkit enables developers to utilize Meta's AI glasses to build hands-free wearable experiences into their mobile applications.
By integrating this SDK, developers can reliably connect to Meta's AI glasses and leverage capabilities like video and audio streaming and photo capture.

The Wearables Device Access Toolkit is in developer preview.
Developers can access our SDK and documentation, test on supported AI glasses, and create organizations and release channels to share with test users.

## Documentation & Community

Find our full [developer documentation](https://wearables.developer.meta.com/docs/develop/) on the Wearables Developer Center.

You can find an overview of the Wearables Developer Center [here](https://wearables.developer.meta.com/).
Create an account to stay informed of all updates, report bugs and register your organization.
Set up a project and release channel to share your integration with test users.

For help, discussion about best practices or to suggest feature ideas visit our [discussions forum](https://github.com/facebook/meta-wearables-dat-android/discussions).

See the [changelog](CHANGELOG.md) for the latest updates.

## Including the SDK in your project

The Wearables Device Access Toolkit is published to [Maven Central](https://central.sonatype.com) under the `com.meta.wearable` group, so no access token or extra repository definition is required.
The `mavenCentral()` repository already declared in your `settings.gradle.kts` is all you need.

### 1. Declare the Wearables Device Access Toolkit artifacts in `libs.versions.toml`

Check the available versions on the [artifact page](https://central.sonatype.com/artifact/com.meta.wearable/mwdat-core/versions).

```toml
[versions]
mwdat = "1.0.0"

[libraries]
mwdat-core = { group = "com.meta.wearable", name = "mwdat-core", version.ref = "mwdat" }
mwdat-camera = { group = "com.meta.wearable", name = "mwdat-camera", version.ref = "mwdat" }
mwdat-display = { group = "com.meta.wearable", name = "mwdat-display", version.ref = "mwdat" }
mwdat-inputs = { group = "com.meta.wearable", name = "mwdat-inputs", version.ref = "mwdat" }
mwdat-motion = { group = "com.meta.wearable", name = "mwdat-motion", version.ref = "mwdat" }
mwdat-mockdevice = { group = "com.meta.wearable", name = "mwdat-mockdevice", version.ref = "mwdat" }
mwdat-speech = { group = "com.meta.wearable", name = "mwdat-speech", version.ref = "mwdat" }
```

### 2. Add the required components as dependencies in your app's `build.gradle.kts`

```kotlin
dependencies {
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.camera)
    implementation(libs.mwdat.display)
    implementation(libs.mwdat.inputs)
    implementation(libs.mwdat.motion)
    implementation(libs.mwdat.mockdevice)
    implementation(libs.mwdat.speech)
}
```

## Developer Terms

- By using the Wearables Device Access Toolkit, you agree to our [Meta Wearables Developer Terms](https://wearables.developer.meta.com/terms),
  including our [Acceptable Use Policy](https://wearables.developer.meta.com/acceptable-use-policy).
- By enabling Meta integrations, including through this SDK, Meta may collect information about how users' Meta devices communicate with your app.
  Meta will use this information collected in accordance with our [Privacy Policy](https://www.meta.com/legal/privacy-policy/).
- You may limit Meta's access to data from users' devices by following the instructions below.

### Opting out of data collection

To configure analytics settings in your Meta Wearables DAT Android app, add the following `<meta-data>` element to your
app's `AndroidManifest.xml` file within the `<application>` element:

```xml
<meta-data
    android:name="com.meta.wearable.mwdat.ANALYTICS_OPT_OUT"
    android:value="true"
    />
```

**Default behavior:** If the `ANALYTICS_OPT_OUT` metadata is missing or set to `false`, analytics are enabled
(i.e., you are **not** opting out). Set to `true` to disable data collection.

**Note:** In other words, this setting controls whether or not you're opting out of analytics:

- `true` = Opt out (analytics **disabled**)
- `false` = Opt in (analytics **enabled**)

**Complete example:**

```xml
<application
    android:name=".MyApplication"
    android:label="MyApp"
    android:icon="@mipmap/app_launcher">

    <!-- Required: Your application ID from Wearables Developer Center -->
    <meta-data
        android:name="com.meta.wearable.mwdat.APPLICATION_ID"
        android:value="your_app_id_here"
        />

    <!-- Optional: Disable analytics -->
    <meta-data
        android:name="com.meta.wearable.mwdat.ANALYTICS_OPT_OUT"
        android:value="true"
        />

    <!-- Your activities and other components -->
</application>
```

### Crash reporting

The Wearables Device Access Toolkit can capture crashes originating from SDK code, store them locally,
and chain with any existing uncaught-exception handler your app installs. Crash reporting is **enabled by
default**.

To opt out, add the following `<meta-data>` element to your app's `AndroidManifest.xml` file within the
`<application>` element:

```xml
<meta-data
    android:name="com.meta.wearable.mwdat.CRASH_REPORTING_OPT_OUT"
    android:value="true"
    />
```

**Default behavior:** If the `CRASH_REPORTING_OPT_OUT` metadata is missing or set to `false`, crash
reporting is **enabled**. Set it to `true` to disable SDK crash capture.

## AI-Assisted Development

This repository ships one public DAT knowledge base as a single plugin, plus file-based artifacts for the tools that do not use plugins:

| Tool | Public artifact | Recommended setup |
|------|-----------------|-------------------|
| [Muse Code](https://developer.meta.com/ai/products/muse-code/), [Claude Code](https://docs.anthropic.com/en/docs/claude-code), Codex | `plugins/mwdat-android/` | Add this GitHub repo as a marketplace, then install `mwdat-android` |
| [GitHub Copilot](https://github.com/features/copilot) | `.github/copilot-instructions.md` | Auto-loaded by Copilot in VS Code |
| [Cursor](https://cursor.sh/) | `.cursor/rules/*.mdc` | Auto-loaded with glob-based triggers |
| AGENTS.md-compatible tools | `AGENTS.md` | Portable fallback for agents that read `AGENTS.md` |
| MCP-compatible editors | `https://mcp.developer.meta.com/wearables` | Connect as a remote HTTP MCP server; no authentication required |

Muse Code, Claude Code, and Codex install from the plugin payload under `plugins/`. Copilot, Cursor, and `AGENTS.md` readers use the native file-based artifacts at repo root.

### Muse Code

```bash
muse plugins marketplace add mwdat-android-marketplace https://github.com/facebook/meta-wearables-dat-android
muse plugins install mwdat-android@mwdat-android-marketplace
muse plugins approve mwdat-android
```

Or use the helper script:

```bash
./install-skills.sh muse
```

Muse Code also reads `AGENTS.md` from your project root with no setup at all.

### Claude Code

```bash
claude plugin marketplace add facebook/meta-wearables-dat-android
claude plugin install mwdat-android@mwdat-android-marketplace
```

Or use the helper script:

```bash
./install-skills.sh claude
```

### Codex

```bash
codex plugin marketplace add facebook/meta-wearables-dat-android
codex plugin add mwdat-android@mwdat-android-marketplace
```

Or use the helper script:

```bash
./install-skills.sh codex
```

### Other tool installs

Use the installer when you want the repo-native file surfaces for other tools:

```bash
./install-skills.sh copilot   # .github/copilot-instructions.md
./install-skills.sh cursor    # .cursor/rules/*.mdc
./install-skills.sh agents    # AGENTS.md
./install-skills.sh all       # Muse/Claude/Codex when available, plus Copilot/Cursor/AGENTS.md
```

Or run the helper remotely:

```bash
curl -sL https://raw.githubusercontent.com/facebook/meta-wearables-dat-android/main/install-skills.sh | bash
```

### What's included

- **Getting started** — SDK setup, Gradle integration, manifest configuration
- **Camera streaming** — Session and Stream setup, video frames, and in-stream photo capture
- **Audio streaming** — Receive synchronized PCM audio through Camera Stream (experimental; unavailable for production publishing)
- **Camera capture** — Capture standalone high-quality photos with progress and typed errors (experimental; unavailable for production publishing)
- **Display** — Use Display features on the Meta Ray-Ban Display glasses
- **Inputs** — Receive navigation, button, capture, and drag interactions from glasses
- **Motion** — Stream accelerometer, gyroscope, magnetometer, and orientation samples with explicit lifecycle control
- **Speech** — Recognize on-device speech, handle partial/final results, and test with MockDeviceKit
- **Voice Invocations** — Launch or activate an app from Hey Meta and acknowledge each action
- **MockDevice testing** — Test without physical glasses using MockDeviceKit
- **DAT docs MCP** — Connect the hosted docs MCP server and search live DAT documentation with `search_dat_docs`
- **Live debugging MCP** — Read-only runtime diagnosis from app-visible DAT debug events
- **Session lifecycle** — Session and stream state, pause/resume behavior, and device availability monitoring
- **Permissions & registration** — App registration with Meta AI and device permission flows
- **Debugging** — Common issues, Developer Mode, version compatibility, and session and stream diagnosis
- **Sample app guide** — Building a complete DAT app

For static reference context, point your AI tool at the [llms.txt endpoint](https://wearables.developer.meta.com/llms.txt?full=true). Installing the plugin registers `https://mcp.developer.meta.com/wearables` for live documentation search in Muse Code, Claude Code, and Codex. Other MCP-compatible editors can connect to that endpoint directly and use `search_dat_docs`. The public docs MCP server does not require authentication; do not configure tokens, OAuth, or custom authorization headers for it.

## License

See the [LICENSE](LICENSE) file.
