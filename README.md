<div align="center">

<img src="assets/veltrix-nova-logo.svg" alt="Veltrix Nova liquid-glass logo" width="720" />

# Veltrix Nova

### Make your Discord presence feel like yours.

A phone-first Android app for designing, saving, and publishing custom Discord Rich Presence activities — with a dark liquid-glass interface, electric violet highlights, and cyan glow.

[![Android CI](https://github.com/Kentaakakashi/Discord-widget/actions/workflows/android-build.yml/badge.svg?branch=feature%2Fsocial-sdk-rich-presence)](https://github.com/Kentaakakashi/Discord-widget/actions/workflows/android-build.yml)
[![Platform](https://img.shields.io/badge/platform-Android-65E6F2?style=flat-square)](#)
[![Built with](https://img.shields.io/badge/built%20with-Kotlin%20%2B%20Compose-9B87F5?style=flat-square)](#)

</div>

---

## ✨ What is Veltrix Nova?

Veltrix Nova is an Android-first workspace for shaping the activity people see on your Discord profile. Create reusable presence presets, tune the details and state, choose artwork keys, configure button labels and URLs, and keep a library of different activities for gaming, music, watching, development, and more.

The app is designed for a phone-first workflow: build and test through GitHub Actions, download the APK from your Android device, and manage your presets without needing a desktop development environment.

## 🚀 Features

- **Rich Presence presets** — create, edit, select, and delete your own activities.
- **Activity types** — Playing, Watching, and Listening.
- **Activity details** — customize the activity name, details, and state.
- **Buttons** — configure up to two button labels and URLs in a preset.
- **Artwork fields** — choose a local image URI and store a Discord artwork asset key.
- **Timestamps** — optional start and end timestamps stored with each preset.
- **JSON portability** — import and export your preset library using Android's document picker.
- **Discord sign-in** — browser-based OAuth 2.0 with PKCE and the `identify` scope; no Discord password or account token is requested by the app.
- **Native Android SDK work** — includes the Discord Social SDK AAR and an Android RPC bridge, with publishing dependent on the supported SDK flow and configuration.
- **Liquid-glass visual direction** — near-black surfaces, glassy edges, violet gradients, and cyan light.

## 📱 Screenshots and branding

The logo is available as a scalable SVG at [`assets/veltrix-nova-logo.svg`](assets/veltrix-nova-logo.svg). The Android launcher uses a compact vector version of the same violet/cyan glass emblem, designed to remain legible at small icon sizes.

## ⚠️ Current status — read before using

The app's capabilities are still being validated. A configured preset and a local preview do not, by themselves, prove that Discord has received or displayed the activity. Publishing depends on a compatible Discord Android installation/session, a valid application configuration, and successful execution of the supported Social SDK/RPC path.

- OAuth sign-in identifies the Discord account; **login is not the same thing as publishing Rich Presence**.
- Local artwork URIs are stored locally. They do not upload images to Discord or automatically create Discord asset keys.
- Button labels, URLs, and timestamps are editable preset data; support in the actual published activity must be verified on-device.
- Treat a feature as working only after a successful build and a real Discord profile test.

The project deliberately does **not** extract Discord WebView session tokens, request users' account tokens, or automate a normal Discord user account through self-bot techniques.

## 🛠️ Build an APK on your phone

No local Android Studio installation is required for the repository's GitHub Actions workflow.

1. Open the [Actions workflow](https://github.com/Kentaakakashi/Discord-widget/actions/workflows/android-build.yml).
2. Choose **Build Android APK** and tap **Run workflow** to build the current branch, or push a commit to `feature/social-sdk-rich-presence`.
3. Open the completed run and wait for the build job to finish successfully.
4. Download the `VeltrixNova-debug-apk` artifact.
5. Extract the ZIP and install the debug APK on your Android device. If Android blocks installation, allow installs for the app you used to open the APK.

**Build note:** the workflow's success status and the presence of its APK artifact are the source of truth. A commit alone does not mean the APK has built successfully.

## 🔐 Discord OAuth setup

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and create or select an application you control.
2. Copy its **Application ID / Client ID** into Veltrix Nova.
3. Add this exact redirect URI in the application's OAuth2 settings: `presencestudio://oauth/callback`.
4. Use the browser-based PKCE flow and grant only the requested `identify` scope.
5. Tap **Connect Discord**, complete the browser prompt, and return to the app.

The redirect scheme remains `presencestudio` intentionally to preserve the existing OAuth configuration. Do not add a client secret to the APK; mobile apps cannot keep embedded secrets confidential.

## 🧭 Project direction

- Validate the Android Social SDK/RPC publishing path against Discord's current supported documentation.
- Make connection and publish status explicit, including actionable error feedback.
- Test actual activity visibility on a Discord profile rather than relying only on local state.
- Continue refining the violet/cyan liquid-glass UI while keeping the app usable on small screens.

## 🧱 Tech stack

- Kotlin
- Jetpack Compose + Material 3
- Android SDK 35 / Java 17
- Discord Social SDK AAR and native bridge
- GitHub Actions for debug APK builds

## 🔒 Security

- Never commit Discord bot tokens, user tokens, client secrets, or API credentials.
- Keep exported preset JSON files private if they contain personal descriptions or links.
- Use only official Discord authorization and supported SDK interfaces.
- Review permissions and OAuth scopes before granting access.

## 📄 License and status

This repository is a work in progress. Check the repository's license and Discord's current developer documentation before redistributing or publishing builds.
