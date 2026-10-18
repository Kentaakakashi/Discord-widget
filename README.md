# Presence Studio

An Android-first manager for custom Discord Rich Presence presets.

## Current status: 0.1.0 starter

- Native Android app built with Kotlin and Jetpack Compose.
- Local preset storage.
- Create, edit, select, and delete activity presets.
- Configure activity type, details, state, artwork asset key, and two button labels/URLs.
- Starter presets for Theo, gaming, movies, and YouTube.
- GitHub Actions workflow builds a debug APK on pushes to `main` or manual workflow dispatch.

## Important limitation

This first milestone is a **local preset manager and preview only**. It does not yet publish Rich Presence to Discord. The next milestone is to implement and validate a supported Android Discord connection, then add artwork handling and JSON preset import/export.

## Build the APK

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Tap **Run workflow** to start a build manually, or push a commit to `main).
4. Open the completed workflow run and download the `PresenceStudio-debug-apk` artifact.

The workflow uses Java 17, Android SDK, Gradle 8.13, and runs `gradle assembleDebug`.

## Security

Never store a Discord bot token, user token, or other secret in a preset file or commit it to this repository.
