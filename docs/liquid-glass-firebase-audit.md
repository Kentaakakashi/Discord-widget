# Veltrix Nova integration audit — 2026-10-19

## Firebase: current state

The existing Firebase backend scaffold was inspected before changes:

- `firebase.json` deploys `firestore.rules` and Node 20 Functions from `functions/`.
- `functions/index.js` exposes `exchangeDiscordIdentity`: it verifies the supplied Discord OAuth access token against Discord's `/users/@me`, derives a stable `discord_<snowflake>` Firebase UID, and mints a Firebase custom token.
- `firestore.rules` isolates private documents under `users/{uid}`; identity mappings are server-only and public preset writes are disabled.
- `docs/discord-firebase-identity.md` already documents the intended identity and data-isolation contract.
- No `google-services.json`, Firebase project ID, deployed Function URL, or project-specific client configuration exists in this branch. Therefore, the Android client cannot safely initialize Firebase or call the identity-exchange function yet. No project was invented, no existing cloud data was changed, and no existing Discord OAuth redirect or flow was replaced.

The Firebase Auth and Firestore Android SDKs have been added using the Firebase BoM. This is dependency setup only; it does **not** mean sign-in or cloud sync is live.

### Client integration sequence

1. Register Android package `com.presencestudio` in the existing Firebase project (or confirm the intended project).
2. Add its generated `google-services.json` to `app/` without committing service-account private keys. The client config file contains project identifiers, not an Admin credential.
3. Deploy/confirm `exchangeDiscordIdentity` and provide the real HTTPS function URL through non-secret app configuration.
4. After the existing Discord OAuth flow successfully fetches `/users/@me`, POST the OAuth access token to the function, then call Firebase Auth `signInWithCustomToken`.
5. Only after Firebase confirms the new UID, load/save `users/{uid}/presets/{presetId}` and user settings. Keep current local presets intact and implement explicit merge/import rules; never silently overwrite cloud data or merge across accounts.
6. Test account switching and Firestore rules against two separate Discord identities before enabling cloud sync by default.

## BitChord Liquid Glass source audit

Inspected upstream `kushagrasinghx/BitChord` source:

- `app/src/main/java/com/music/bitchord/ui/components/LiquidGlass.kt`
- `app/src/main/java/com/music/bitchord/ui/components/GlassNavBar.kt`
- `app/src/main/java/com/music/bitchord/ui/components/backdrop/`

The real effect is not a gradient: it records a backdrop, applies vibrancy/color controls and blur, then a lens/refraction pass (including chromatic aberration on Android 13+), plus edge highlights and shadow. The nav bar samples the app backdrop and uses transparent bar fills. The implementation explicitly gates full glass on Android 12/API 31+.

Important license boundary: BitChord's repository is GPL-3.0. Its `LiquidGlass.kt` identifies its integration glue as adapted from Echo Music (GPL-3.0), while the vendored `backdrop` renderer is attributed to Kyant0/backdrop under Apache-2.0. Copying BitChord's GPL integration into Veltrix Nova would bring GPL obligations for the derivative work when distributed. Do not copy the whole component blindly or remove its notices. The reusable Apache-2.0 renderer is the more compatible starting point, subject to preserving its license/notice and checking its source/dependency compatibility with this app's current Compose/Kotlin toolchain.

A separate current Compose liquid-glass library was also checked, but its stated requirements (Compose BOM 2026.02.01+ and Kotlin 2.2+) exceed this app's current BOM 2025.05.00 and Kotlin 2.1.20. Blindly adding it risks destabilizing the Android build. The prior gradient panels are not equivalent to BitChord's refracting glass; do not describe them as the real effect.

## Build verification

GitHub Actions workflow: `.github/workflows/android-build.yml`. The dependency change is intended to trigger its existing debug APK build. The build status must be read from Actions before claiming success.
