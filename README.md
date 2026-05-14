# Meta Wearables Device Access Toolkit for Android

[![Maven](https://img.shields.io/badge/Maven-0.7.0-brightgreen?logo=apachemaven)](https://github.com/orgs/facebook/packages?repo_name=meta-wearables-dat-android)
[![Docs](https://img.shields.io/badge/API_Reference-0.7-blue?logo=meta)](https://wearables.developer.meta.com/docs/reference/android/dat/0.7)

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

You can add the Wearables Device Access Toolkit to your Gradle project by following the steps below.
You will need to provide a personal access token (classic) with at least **read:packages** scope as an environment variable named `GITHUB_TOKEN` or
by adding it as a property named `github_token` in your `local.properties` file.
See [SDK for Android setup](https://wearables.developer.meta.com/docs/getting-started-toolkit/#sdk-for-android-setup) for more details.

### 1. Add the repository definition to `settings.gradle.kts`

```kotlin
val localProperties =
    Properties().apply {
        val localPropertiesPath = rootDir.toPath() / "local.properties"
        if (localPropertiesPath.exists()) {
            load(localPropertiesPath.inputStream())
        }
    }

dependencyResolutionManagement {
    ...
    repositories {
        ...
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials {
                username = "" // not needed
                password = System.getenv("GITHUB_TOKEN") ?: localProperties.getProperty("github_token")
            }
        }
    }
}
```

### 2. Declare the Wearables Device Access Toolkit artifacts in `libs.versions.toml`

Check the available versions in [GitHub Packages](https://github.com/orgs/facebook/packages?repo_name=meta-wearables-dat-android).

```toml
[versions]
mwdat = "0.7.0"

[libraries]
mwdat-core = { group = "com.meta.wearable", name = "mwdat-core", version.ref = "mwdat" }
mwdat-camera = { group = "com.meta.wearable", name = "mwdat-camera", version.ref = "mwdat" }
mwdat-mockdevice = { group = "com.meta.wearable", name = "mwdat-mockdevice", version.ref = "mwdat" }
```

### 3. Add the required components as dependencies in your app's `build.gradle.kts`

```kotlin
dependencies {
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.camera)
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

## AI-Assisted Development

This repository ships one public DAT knowledge base in two first-class formats:

| Tool | Public artifact | Recommended setup |
|------|-----------------|-------------------|
| [Claude Code](https://docs.anthropic.com/en/docs/claude-code) | `.claude-plugin/marketplace.json` + `plugins/mwdat-android/.claude-plugin/plugin.json` | Add this GitHub repo as a marketplace, then install `mwdat-android` |
| Codex | `plugins/mwdat-android/.codex-plugin/plugin.json` | Install the plugin from a cloned checkout of this repo |
| [GitHub Copilot](https://github.com/features/copilot) | `.github/copilot-instructions.md` | Auto-loaded by Copilot in VS Code |
| [Cursor](https://cursor.sh/) | `.cursor/rules/*.mdc` | Auto-loaded with glob-based triggers |
| AGENTS.md-compatible tools | `AGENTS.md` | Portable fallback for agents that read `AGENTS.md` |
| MCP-compatible editors | `https://mcp.facebook.com/wearables_dat` | Connect as a remote HTTP MCP server |

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
- **MockDevice testing** — Test without physical glasses using MockDeviceKit
- **Session lifecycle** — Session and stream state, pause/resume behavior, and device availability monitoring
- **Permissions & registration** — App registration with Meta AI and device permission flows
- **Debugging** — Common issues, Developer Mode, version compatibility, and session and stream diagnosis
- **Sample app guide** — Building a complete DAT app

For static reference context, point your AI tool at the [llms.txt endpoint](https://wearables.developer.meta.com/llms.txt?full=true). For live documentation search in MCP-compatible editors, connect `https://mcp.facebook.com/wearables_dat` and use `search_dat_docs`.

## License

See the [LICENSE](LICENSE) file.
