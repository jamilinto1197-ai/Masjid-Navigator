# MasjidPH Navigator

MasjidPH Navigator provides a native Android app and a separate static web/PWA for discovering masjids in the Philippines, checking estimated prayer times, finding the Qibla direction, and connecting with local communities. The Android package is fully native Java UI and does not embed the web assets or use a Capacitor bridge.

## Security status

**APK distribution is paused.** A previous debug APK was flagged by Android Phone Manager and four VirusTotal engines. That APK was removed from the default branch. Do not install copies of it or bypass security alerts.

The `security-hardening` branch targets Android 16 (API 36) using AGP 8.10.1 and Gradle 8.11.1, with explicit edge-to-edge system-bar handling. CI resolves the Android dependency lock on every build, checks it against the reviewed SHA-256 baseline in `android/app/gradle.lockfile.sha256`, and runs OSV-Scanner against the actual resolved dependency graph. A changed graph fails CI until reviewed and deliberately re-baselined. GitHub Actions are pinned to reviewed commit SHAs. The security gate also runs MobSF source analysis, Android unit tests, lint/build, compiled APK checks, and Firestore authorization tests. The APK inspection rejects both legacy `assets/www` and Capacitor `assets/public` bundles because the native activity does not use a WebView. The root `www/` folder remains the standalone static web/PWA; unused Capacitor packages and configuration were removed from the Android migration branch. The PWA enforces email verification before showing account data, uses an explicit resend/re-check flow, pins Leaflet 1.9.4 with SRI, and formats its prayer-time dates/reminders in `Asia/Manila`. Its workflow publishes security reports only, never an APK. A green compile alone is not a malware verdict. The current workflow validates the report moderation rules and the reminder scheduling code, but a real Android device test is still required.

## Native feature status on this branch

- **Authentication:** Firebase email/password registration, sign-in, and password-reset flow; unverified password accounts remain on a verification screen with explicit resend, re-check, and switch-account actions. Resends have a client-side cooldown, and Firestore rules independently require verified identities for profile and community access. Native Google sign-in uses Android Credential Manager, pending Firebase OAuth setup.
- **Mosque directory:** Search and filter, optional location sorting, Firestore directory records, and launch directions in Google Maps. Verified administrators can publish new listings through an in-app form; Firestore rules validate the schema and coordinates independently. Starter coordinates are approximate, clearly labelled, and should be locally verified.
- **Prayer times and reminders:** Location-based estimates using the AlAdhan HTTPS API. Optional daily local notifications refresh from the saved location through WorkManager; past or malformed API times cancel stale alarms. Android notification permission is required, background delivery is best-effort and can be delayed by battery policies. Confirm official times with a local masjid.
- **Qibla:** On-device bearing calculation to the Kaaba and a live compass dial when a compatible device sensor is available. The compass rose now rotates with corrected true heading; bearing math has automated unit tests.
- **Profiles:** Member profile read/write with client-side length limits and Firestore ownership rules.
- **Community:** Mosque-specific text chat, realtime reads, own-message deletion, reporting, a 5-second Firestore-enforced per-user posting cooldown, and a custom-claim-gated moderation queue. Atomic-write rules also prevent multiple messages from bypassing the cooldown in one batch. Ordinary members cannot read reports; verified admins can dismiss reports or delete reported messages. Rooms are open to verified signed-in users; membership approval and broader abuse escalation remain future work.
- **Not complete:** Native Google sign-in pending OAuth setup, exact prayer alarms (current alerts are best-effort), in-app map tiles, mosque edit/delete UI, membership approval workflows, advanced abuse escalation, and physical-device / release testing.

Do not describe the unfinished items above as implemented. More security and functionality work remains before the app can be treated as ready for general use.

## Firebase setup required

The native app package is `com.masjidph.navigator` in Firebase project `masjid-109f3`. For password accounts, Firestore now requires the `email_verified` token claim; test clients must explicitly set it.

Moderator access is controlled by the Firebase Auth custom claim `admin: true`, which the client cannot set. A trusted operator must assign it with the Firebase Admin SDK after verifying the person's identity and approval. Preserve existing claims when changing claims. Example for a trusted Node.js environment using Application Default Credentials:

```js
const admin = require("firebase-admin");
admin.initializeApp({ credential: admin.credential.applicationDefault() });

(async () => {
  const user = await admin.auth().getUser("REPLACE_WITH_APPROVED_UID");
  await admin.auth().setCustomUserClaims(user.uid, {
    ...(user.customClaims || {}),
    admin: true
  });
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
```

Never run that snippet in the Android app or expose service-account credentials. Force-refresh the user's ID token or sign in again after claims change. The moderation UI is hidden for non-admin accounts and Firestore rules independently deny unauthorized reads and deletes.

1. Register an Android app with that package name in Firebase Console.
2. Copy its real `mobilesdk_app_id` value (the Android form is `1:885467340998:android:<hex-id>`). Do not use the Web app ID and do not make up an ID.
3. In GitHub, open **Settings → Secrets and variables → Actions → Variables → New repository variable**. Add `FIREBASE_ANDROID_APP_ID` with the real Android `mobilesdk_app_id`. This value is a Firebase client identifier, not a service-account secret.
4. Add a second repository Actions variable named `FIREBASE_WEB_CLIENT_ID` with the project's **Web application** OAuth client ID from Google Cloud credentials (ending in `.apps.googleusercontent.com`). Do not paste an Android OAuth client ID here. The Android app uses Credential Manager and Firebase Auth for Google sign-in; register the package and configure the SHA-1/SHA-256 signing fingerprints in Firebase/Google Cloud.
5. Re-run the **Android security gate** workflow from **Actions → Android security gate → Run workflow** on `security-hardening`. The gate must pass its Firebase configuration check before this branch can be considered release-ready. If the Firebase project has no registered Android app or Web OAuth client, create them first in Firebase Console / Google Cloud Console. Do not substitute placeholder values.
6. After configuration, inspect the run logs and confirm the Firebase config, build, dependency audit, APK inspection, and Firestore rules tests all pass. A passing gate still does not replace physical-device tests or a deeper scan of the previously flagged APK.
5. Review and deploy `firestore.rules` only after the emulator authorization tests pass. The rules have not been deployed to the production Firebase project by this repository workflow.

Never commit Firebase Admin service-account keys or other server credentials. Client SDK configuration is not a substitute for deployed security rules. Consider restricting the Firebase API key to the expected Android package/signing certificates and enabling Firebase App Check before production.

## Build and test

The default branch currently runs a compile-only diagnostic workflow and does not upload or publish an APK. The security branch checks the standalone web/PWA with `npm run check` and builds the native Android package separately. The security branch is being verified independently; it is not a release build. A debug build is not considered safe just because the compiler succeeds.
