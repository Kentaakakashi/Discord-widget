# Discord identity and Firebase data isolation

## Identity contract

- Discord OAuth is the only sign-in provider. Do not add Google, email/password, or a separate Veltrix Nova account.
- The Android client must never submit a Discord user ID as proof of identity.
- The trusted function obtains the identity from Discord's `/api/v10/users/@me` endpoint using the OAuth access token.
- Firebase UID is deterministically derived from the verified immutable Discord snowflake (`discord_<snowflake>`). Usernames and display names are labels only and must never be used as keys.
- The backend-owned `discordIdentityMap/<discordId>` collection is not readable or writable by clients.
- A Firebase custom token is minted only after Discord validates the access token. The Android app must then sign in with Firebase Auth using that custom token.
- On account switching, clear the prior in-memory account state and reload all user-scoped data only after Firebase confirms the new UID. Never merge two accounts automatically.

## Firestore layout

- `users/<firebaseUid>`: private account settings.
- `users/<firebaseUid>/presets/<presetId>`: private cloud presets.
- Additional private collections must remain nested under the authenticated UID.
- `publicPresets/<presetId>`: public community presets. Client writes remain disabled until server-side validation, ownership, rate limits, and moderation are implemented.
- `discordIdentityMap/<discordId>`: backend-only identity mapping.

Firestore rules are deny-by-default. They are not a substitute for validating identity in the trusted function.

## Local setup and deployment

1. Create a Firebase project and enable **Authentication → Custom authentication** and **Cloud Firestore**.
2. Install the Firebase CLI and authenticate on a computer or a supported cloud shell.
3. From the repository root, run `firebase use --add` to associate the project.
4. Install backend dependencies with `cd functions && npm install`.
5. Deploy with `firebase deploy --only functions:exchangeDiscordIdentity,firestore:rules`.
6. The Android client must POST to the deployed `exchangeDiscordIdentity` endpoint with `Authorization: Bearer <Discord OAuth access token>`, then sign in using Firebase Auth's `signInWithCustomToken`.
7. Keep Discord client secrets, Firebase Admin credentials, and service-account keys out of the Android app and Git repository.

Cloud Functions deployment may require the Firebase project's billing plan to be upgraded. Check the project's current pricing before deploying.

## Current implementation boundary

The trusted function and Firestore rules are scaffolding in this branch. The Android app currently performs Discord OAuth directly and shows a profile label; it has not yet called this function or signed into Firebase. Do not claim cloud sync or secure Firebase-backed account isolation is active until client integration, deployment, rules tests, and two-account isolation tests pass.
