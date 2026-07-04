# Alakey Podcast App

Alakey is a premium, modern podcast application for Android featuring a rich "glassmorphic" UI, smart sleep timers, and automated downloads.

![Alakey UI Concept](https://img.shields.io/badge/UI-Jetpack_Compose-blue?style=for-the-badge)
![Android](https://img.shields.io/badge/Platform-Android_11%2B-green?style=for-the-badge)
![Architecture](https://img.shields.io/badge/Architecture-Decomplected-purple?style=for-the-badge)

## ✨ Key Features

*   **Premium Visuals**: Vibrant dark-mode design with glassmorphism, subtle gradients, and de-cluttered layouts.
*   **Hero Action Center**: Quick-access controls on the library hero—Play, Queue, Navigation, and Sleep Timer status directly on the card.
*   **Smart Playback Continuity**: Automatic progression through your queue with a seamless handoff between tracks.
*   **Smart Sleep Timer**: Includes a unique motion-reset feature—simply move your phone to extend your listening time.
*   **Offline First**: Automatic and manual episode downloads for seamless offline support.
*   **Robust Player Visibility**: Universal player state management ensures the player appears instantly even for external search results.
*   **Marketplace Discovery**: Built-in integration with the iTunes Search API for finding new content.
*   **Power-User REPL**: Debug and control the app via ADB broadcasts (SQL queries, Fact injection, Playback control).

## 🏗️ Architecture

The app follows modern Android development best practices:
- **View**: Jetpack Compose with **Spec-Driven Components** (Pure Functional UI).
- **Architecture**: **De-complected** design separating logic from IO/Android specifics.
- **Model**: Room Database with **Fact Registries** (EAV Information Model).
- **Playback**: Jetpack Media3 (ExoPlayer) wrapper service.
- **DI**: Hilt for robust dependency management.
- **Deps**: Gradle Version Catalogs (`libs.versions.toml`) for type-safe dependency management.

For more details, see [ARCHITECTURE.md](ARCHITECTURE.md).

## 🚀 Getting Started

1.  **Clone the Repo**: `git clone https://github.com/criticalinsight/Alakey_Android11.git`
2.  **Install JDK 17**: Gradle and Android Gradle Plugin require a Java 17 runtime.
3.  **Open in Android Studio**: Use a recent Android Studio release with Android API 36 support.
4.  **Build & Run**: Use a device or emulator running Android 11+ (API 30+).

For Homebrew-based command-line builds, use the environment variables in `.envrc.example` and set `sdk.dir=/opt/homebrew/share/android-commandlinetools` in the gitignored `local.properties` file.

## 🛠️ Development

-   **PRD**: Detailed requirements can be found in [PRD.md](PRD.md).
-   **Roadmap**: Current and future tasks are tracked in [TASKS.md](TASKS.md).
-   **Automation**: This project uses `sly` for autonomous learning and documentation management.

## 📜 License

[MIT](LICENSE)
