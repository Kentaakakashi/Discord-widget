# Discord login + Firestore sync setup

## Intended behavior
Discord OAuth is the only login. The Android app exchanges the Discord access token with this Worker; the Worker asks Discord `/api/v10/users/@me` to establish the immutable Discord ID and returns a Firebase custom token for UID `discord_<id>`. Android signs in with Firebase Auth, then reads/writes only that UID's Firestore documents.

## Why a bridge is needed
Firebase does not accept a Discord access token directly as Firebase Authentication credentials. Cloud Functions deployment previously requested the paid Blaze plan, so this bridge is designed for Cloudflare Workers' free tier. No separate profile, Google login, email login, or social feature is introduced.

## One-time secure setup (phone-friendly via Cloudflare dashboard)
1. In Google Cloud Console for Firebase project `veltrix-nova-57`, create a dedicated service account for minting Firebase custom tokens. Grant only the permissions needed to create Firebase Auth custom tokens (normally `Service Account Token Creator` on the service account, subject to the project's signing setup). Do not reuse a broad deployment credential if avoidable.
2. Create a key for that dedicated service account and keep the downloaded JSON private. Never commit it, paste it into chat, or put it in the APK.
3. In Cloudflare, create a Worker named `veltrix-nova-auth-bridge`, paste `worker/index.js`, and add the JSON as a Worker secret named `FIREBASE_SERVICE_ACCOUNT_JSON`. Deploy it and copy its HTTPS URL.
4. Test the Worker only after adding that secret. It should return 401 for no bearer token and reject invalid Discord tokens. Never test by sharing a live access token in a public issue.
5. Set the deployed Worker URL as `DISCORD_FIREBASE_BRIDGE_URL` in the Android source before building. Do not use a fake URL in a release build.
6. Deploy Firestore rules. Confirm Firebase Authentication has custom-token sign-in available and Cloud Firestore is enabled.

## Data layout and safety
- `users/<firebaseUid>` contains private settings.
- `users/<firebaseUid>/presets/<presetId>` contains individual presets.
- The client must only sync after Firebase Auth confirms the signed-in UID.
- On a Discord account switch, sign out first and reload that account's data. Never merge separate accounts.
- Do not store Discord access tokens in Firestore or log them.
- Local presets should remain available offline. Cloud merge should preserve local-only presets and cloud-only presets by stable preset ID, then upload the merged set.

## Status
The Worker source is scaffolding until deployed and configured with a secret. Android OAuth must call it and complete Firebase Auth sign-in; Firestore sync must be implemented and tested before cloud sync can be described as active. Do not upgrade Firebase billing merely to deploy Cloud Functions.
