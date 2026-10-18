# Sally Launcher

Sally Launcher is a personal Android launcher project based on the open-source Zalith Launcher / PojavLauncher codebase. It uses the Android application ID `com.sally.launcher`, a Material 3-inspired teal theme, and a GitHub Actions workflow that builds an Android ARM64 APK.

> **Attribution and license:** This project is derived from Zalith Launcher and PojavLauncher. Original copyright notices and the GPL-3.0 license are retained. See `LICENSE` and upstream notices in source files. Derivative distribution must comply with the applicable licenses.

## Build APK on GitHub

1. Create a GitHub repository and upload/push this project.
2. Open **Actions** and enable workflows if prompted.
3. Run **Sally Launcher Android ARM64** with **Run workflow**, or push a commit.
4. Open the completed workflow run and download the `Sally-Launcher-Android-arm64` artifact.
5. Extract the artifact ZIP to get the debug APK.

The workflow builds the `arm64-v8a` ABI only, suitable for most 64-bit ARM Android devices. It creates a debug-signed APK for testing, not a production release. A CurseForge API key is optional; without it, CurseForge integration may be disabled.

## Local build

Requirements: JDK 17, Android SDK/NDK components specified by Gradle, and a supported host environment.

```bash
chmod +x ./gradlew
./gradlew jre_lwjgl3glfw:build --no-daemon
./gradlew ZalithLauncher:assembleDebug -Darch=arm64 --no-daemon
```

## Notes

- Android package/application ID: `com.sally.launcher` (debug build uses the same ID).
- Android namespace remains `com.movtery.zalithlauncher` to preserve existing generated `R` references in the inherited code. This is valid Android configuration; the installed package ID is still `com.sally.launcher`.
- Minimum Android API level: 26.
- The inherited launcher UI is only partially restyled; Material 3 theme tokens and app icon are updated, but this is not a complete redesign of every legacy screen.
