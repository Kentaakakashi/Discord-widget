# Veltrix Nova

An Android-first, Kotlin + Jetpack Compose app for creating and organizing custom Discord Rich Presence preset ideas.

## Current status

- Native Android app with a dark liquid-glass-inspired surfaces with violet and cyan accents.
- Local preset storage.
- Create, edit, select, and delete presets.
- Activity types: Playing, Watching, Listening.
- Details, state, two button labels/URLs, and Discord artwork asset key fields.
- Select a local image and retain its URI in the preset.
- Optional start/end timestamps stored as Unix milliseconds.
- Import/export presets as JSON files through Android's document picker.
- GitHub Actions workflow builds a debug APK on pushes to `main` or manual workflow dispatch.

## Important publishing limitation

**This app currently does not publish activity to Discord.** Selecting a preset changes the local preview only. The artwork picker stores a local image URI; it does not upload the image to Discord or make it visible on a profile. Timestamps and button fields are saved in the preset, but are not sent to Discord.

Discord's current documentation describes Android Rich Presence through the Discord Social SDK (including RPC without authentication when the Discord Android app is installed and signed in). That is the legitimate route to investigate next. The implementation still needs to be integrated and tested against Discord's supported SDK and application requirements before this app can claim to publish activities. Standard OAuth login alone is not sufficient.

Veltrix Nova will not extract Discord WebView session tokens, ask users to paste account tokens, or automate a normal user account through self-bot techniques.

## JSON import/export

Use **Import JSON** or **Export JSON** on the main screen. Export writes the current preset list to a JSON file. Import replaces the current local preset list with the presets in the selected JSON file. Keep exported files private if they contain personal activity descriptions or links.

## Build the APK on a phone

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Tap **Run workflow** to start a build manually, or push a commit to `main`.
4. Open the completed run and download the `VeltrixNova-debug-apk` artifact.

The workflow uses Java 17, Android SDK 35, and Gradle 8.13. The APK is only considered build-verified after the relevant Actions run completes successfully and the artifact is present.

## Security

Never store a Discord bot token, user token, or other secret in a preset file or commit it to this repository.


## Discord account login setup

The app now includes a real browser-based Discord OAuth2 sign-in flow using the `identify` scope and PKCE. It does not collect your Discord password and does not ask for your account token. The OAuth access token is used only in memory to request the basic profile and is not saved by the app.

Before the first login:

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and create or select an application you control.
2. Copy its **Application ID** (Client ID) and enter it in Veltrix Nova.
3. In the application's OAuth2 settings, add this exact redirect URL: `presencestudio://oauth/callback`.
4. Ensure the application is configured as a public client for the PKCE authorization-code flow, if the portal exposes that option.
5. Tap **Connect Discord**, approve the requested `identify` permission in the browser, and return to the app.

If Discord rejects the redirect URI or token exchange, verify that the URI matches exactly and that the application supports public-client PKCE. Never add a client secret to the Android app.

**Login is not Rich Presence publishing.** The app currently displays the signed-in Discord username, but does not yet send activities to Discord. The next integration should use Discord's supported Android Social SDK/RPC route and must be tested independently.
