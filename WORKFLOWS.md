# Alakey Development Workflows

This document outlines the standard workflows for maintaining and developing the Alakey Podcast App.

## 🏗️ Building
- **Command**: `/build-apk`
- **Purpose**: Compiles the project and generates `app-debug.apk` in `app/build/outputs/apk/debug/`.
- **Requirements**: Java 17, Gradle.

## 🚀 Releasing
- **Local build**: Copy `keystore.properties.example` to the gitignored `keystore.properties`, point it at the release keystore, then run `./gradlew testDebugUnitTest lintRelease assembleRelease`.
- **CI release**: Store the keystore as base64 in `ALAKEY_RELEASE_KEYSTORE_BASE64`; store its password, alias, and key password in the matching `ALAKEY_RELEASE_*` GitHub Actions secrets. Pushing a `v<versionName>` tag builds, verifies, checksums, and publishes `app-release.apk`.
- **Rule**: Never commit a keystore, `keystore.properties`, or signing password. Preserve and back up the original release key; Android updates must use the same certificate.
- **First signed release**: Earlier Alakey APKs were debug-signed. Installing the first production-signed APK therefore requires uninstalling the old build once; Android will reject an in-place update because the certificates differ. Back up app data first if it matters.

## 📱 Verification
- **Command**: `/test-on-device`
- **Purpose**: Installs the APK to a connected Android device and launches it.
- **Requirements**: ADB in `~/android-sdk/platform-tools/`.

## 🧪 REPL Interaction
Use ADB broadcasts to interact with the app in real-time:
```bash
adb shell am broadcast -a com.example.alakey.REPL --es cmd "play-id <podcast_id>"
```
Available commands: `play-id`, `toggle`, `next`, `prev`, `stop`, `sql <query>`, `assert-fact <json>`.
