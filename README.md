# Meta Wearables Device Access Toolkit for Android

[![Maven Central](https://img.shields.io/maven-central/v/com.meta.wearable/mwdat-core?logo=apachemaven&color=brightgreen)](https://central.sonatype.com/namespace/com.meta.wearable)
[![Docs](https://img.shields.io/badge/API_Reference-0.9-blue?logo=meta)](https://wearables.developer.meta.com/docs/reference/android/dat/0.9)

The Meta Wearables Device Access Toolkit enables developers to utilize Meta's AI glasses to build hands-free wearable experiences into their mobile applications.
By integrating this SDK, developers can reliably connect to Meta's AI glasses and leverage capabilities like video streaming and photo capture.

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

The Wearables Device Access Toolkit is published to Maven Central under the `com.meta.wearable` group, so no access token or extra repository definition is required.
The `mavenCentral()` repository already declared in your `settings.gradle.kts` is all you need.
See [SDK for Android setup](https://wearables.developer.meta.com/docs/getting-started-toolkit/#sdk-for-android-setup) for more details.

### 1. Declare the Wearables Device Access Toolkit artifacts in `libs.versions.toml`

Check the available versions on [Maven Central](https://central.sonatype.com/artifact/com.meta.wearable/mwdat-core/versions).

```toml
[versions]
mwdat = "0.9.0"

[libraries]
mwdat-core = { group = "com.meta.wearable", name = "mwdat-core", version.ref = "mwdat" }
mwdat-camera = { group = "com.meta.wearable", name = "mwdat-camera", version.ref = "mwdat" }
mwdat-display = { group = "com.meta.wearable", name = "mwdat-display", version.ref = "mwdat" }
mwdat-mockdevice = { group = "com.meta.wearable", name = "mwdat-mockdevice", version.ref = "mwdat" }
```

### 2. Add the required components as dependencies in your app's `build.gradle.kts`

```kotlin
dependencies {
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.camera)
    implementation(libs.mwdat.display)
    implementation(libs.mwdat.mockdevice)
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

This repository ships one public DAT knowledge base in two first-class formats:

| Tool | Public artifact | Recommended setup |
|------|-----------------|-------------------|
| [Claude Code](https://docs.anthropic.com/en/docs/claude-code) | `.claude-plugin/marketplace.json` + `plugins/mwdat-android/.claude-plugin/plugin.json` | Add this GitHub repo as a marketplace, then install `mwdat-android` |
| Codex | `plugins/mwdat-android/.codex-plugin/plugin.json` | Install the plugin from a cloned checkout of this repo |
| [GitHub Copilot](https://github.com/features/copilot) | `.github/copilot-instructions.md` | Auto-loaded by Copilot in VS Code |
| [Cursor](https://cursor.sh/) | `.cursor/rules/*.mdc` | Auto-loaded with glob-based triggers |
| AGENTS.md-compatible tools | `AGENTS.md` | Portable fallback for agents that read `AGENTS.md` |
| MCP-compatible editors | `https://mcp.developer.meta.com/wearables` | Connect as a remote HTTP MCP server; no authentication required |

Claude and Codex install from the plugin payload under `plugins/`. Copilot, Cursor, and `AGENTS.md` readers use the native file-based artifacts at repo root.

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
git clone https://github.com/facebook/meta-wearables-dat-android.git
cd meta-wearables-dat-android
codex plugin install ./plugins/mwdat-android
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
./install-skills.sh all       # Claude/Codex when available, plus Copilot/Cursor/AGENTS.md
```

Or run the helper remotely:

```bash
curl -sL https://raw.githubusercontent.com/facebook/meta-wearables-dat-android/main/install-skills.sh | bash
```

### What's included

- **Getting started** — SDK setup, Gradle integration, manifest configuration
- **Camera streaming** — Session and Stream capability setup, video frames, resolution/frame rate, photo capture
- **Display** — Use Display features on the Meta Ray-Ban Display glasses
- **MockDevice testing** — Test without physical glasses using MockDeviceKit
- **Session lifecycle** — Session and stream state, pause/resume behavior, and device availability monitoring
- **Permissions & registration** — App registration with Meta AI and device permission flows
- **Debugging** — Common issues, Developer Mode, version compatibility, and session and stream diagnosis
- **Sample app guide** — Building a complete DAT app

For static reference context, point your AI tool at the [llms.txt endpoint](https://wearables.developer.meta.com/llms.txt?full=true). For live documentation search in MCP-compatible editors, connect `https://mcp.developer.meta.com/wearables` and use `search_dat_docs`. The public docs MCP server does not require authentication; do not configure tokens, OAuth, or custom authorization headers for it.

## License

See the [LICENSE](LICENSE) file.
