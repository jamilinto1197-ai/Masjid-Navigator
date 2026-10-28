# MasjidPH Navigator

MasjidPH Navigator is being migrated into a native Android application for discovering masjids in the Philippines, checking estimated prayer times, finding the Qibla direction, and connecting with local communities.

## Security status

**APK distribution is paused.** A previous debug APK was flagged by Android Phone Manager and four VirusTotal engines. That APK was removed from the default branch. Do not install copies of it or bypass security alerts.

The `security-hardening` branch has a security gate that runs MobSF source analysis, a Gradle dependency lock and OSV audit, Android unit tests, Android lint/build, compiled APK checks, and Firestore authorization tests. The APK inspection rejects both legacy `assets/www` and Capacitor `assets/public` bundles because the native activity does not use a WebView. Its workflow publishes security reports only, never an APK. A green compile alone is not a malware verdict. The current workflow validates the report moderation rules and the reminder scheduling code, but a real Android device test is still required.

## Native feature status on this branch

- **Authentication:** Firebase email/password registration, sign-in, and password-reset flow; unverified password accounts are sent a verification link and signed out, and Firestore rules independently require verified identities for profile and community access. Native Google sign-in uses Android Credential Manager, pending Firebase OAuth setup.
- **Mosque directory:** Search and filter, optional location sorting, Firestore directory records, and launch directions in Google Maps. Starter coordinates are approximate, clearly labelled, and should be locally verified.
- **Prayer times and reminders:** Location-based estimates using the AlAdhan HTTPS API. Optional daily local notifications refresh from the saved location through WorkManager; Android notification permission is required, background delivery is best-effort and can be delayed by battery policies. Confirm official times with a local masjid.
- **Qibla:** On-device bearing calculation to the Kaaba and a live compass dial when a compatible device sensor is available. The compass rose now rotates with corrected true heading; bearing math has automated unit tests.
- **Profiles:** Member profile read/write with client-side length limits and Firestore ownership rules.
- **Community:** Mosque-specific text chat, realtime reads, own-message deletion, reporting, and a custom-claim-gated moderation queue. Ordinary members cannot read reports; verified admins can dismiss reports or delete reported messages. Rooms are open to verified signed-in users; membership approval, server-side spam/rate controls, and abuse escalation remain future work.
- **Not complete:** Native Google sign-in pending OAuth setup, exact prayer alarms (current alerts are best-effort), in-app map tiles, server-side spam/rate controls and membership approval workflows, and physical-device / release testing.

Do not describe the unfinished items above as implemented. More security and functionality work remains before the app can be treated as ready for general use.

## Firebase setup required

The native app package is `com.masjidph.navigator` in Firebase project `masjid-109f3`. For password accounts, Firestore now requires the `email_verified` token claim; test clients must explicitly set it.

Moderator access is controlled by the Firebase Auth custom claim `admin: true`, which the client cannot set. A trusted operator must assign it with the Firebase Admin SDK after verifying the person's identity and approval. Preserve existing claims when changing claims. Example for a trusted Node.js environment using Application Default Credentials:

```js
const admin = require("firebase-admin");
admin.initializeApp({ credential: admin.credential.applicationDefault() });
const user = await admin.auth().getUser("REPLACE_WITH_APPROVED_UID");
await admin.auth().setCustomUserClaims(user.uid, {
  ...(user.customClaims || {}),
  admin: true
});
```

Never run that snippet in the Android app or expose service-account credentials. Force-refresh the user's ID token or sign in again after claims change. The moderation UI is hidden for non-admin accounts and Firestore rules independently deny unauthorized reads and deletes.

1. Register an Android app with that package name in Firebase Console.
2. Copy its real `mobilesdk_app_id` value (the Android form is `1:885467340998:android:<hex-id>`). Do not use the Web app ID and do not make up an ID.
3. Set the repository Actions variable `FIREBASE_ANDROID_APP_ID` to the real value.
4. Set `FIREBASE_WEB_CLIENT_ID` to the project's Web application OAuth client ID from Google Cloud OAuth credentials (ending in `.apps.googleusercontent.com`). The Android app now uses Android Credential Manager and Firebase Auth for Google sign-in; the OAuth client and SHA-1/SHA-256 signing fingerprints must be configured in Firebase/Google Cloud.
5. Review and deploy `firestore.rules` only after the emulator authorization tests pass. The rules have not been deployed to the production Firebase project by this repository workflow.

Never commit Firebase Admin service-account keys or other server credentials. Client SDK configuration is not a substitute for deployed security rules. Consider restricting the Firebase API key to the expected Android package/signing certificates and enabling Firebase App Check before production.

## Build and test

The default branch currently runs a compile-only diagnostic workflow and does not upload or publish an APK. The security branch is being verified independently; it is not a release build. A debug build is not considered safe just because the compiler succeeds.
