# Android Firebase configuration

The native Android app reads Firebase and Google OAuth configuration from Gradle properties or environment variables. Do not put credentials/configuration values directly in Java source.

## GitHub Actions configuration

Add these repository settings under **Settings → Secrets and variables → Actions**:

### Repository secret
- `FIREBASE_API_KEY` — Firebase Android API key from the Firebase app configuration.

### Repository variables
- `FIREBASE_APP_ID` — Firebase Android App ID (currently `1:885467340998:android:913114c4da94798283b7c7`).
- `FIREBASE_PROJECT_ID` — Firebase project ID (currently `masjid-109f3`).
- `FIREBASE_MESSAGING_SENDER_ID` — Firebase project sender number (currently `885467340998`).
- `FIREBASE_STORAGE_BUCKET` — Firebase storage bucket from the Firebase app configuration.
- `GOOGLE_WEB_CLIENT_ID` — Web OAuth client ID used for Google ID-token sign-in.

The workflow passes these settings to Gradle at build time. The GitHub integration used for repository edits cannot create or read repository secrets/variables, so an owner with repository Settings access must add the names above. Until values are configured, the app can compile but Firebase initialization / Google sign-in will not work.

## Local builds

Supply the same names as environment variables, or as Gradle project properties (for example, `-PFIREBASE_PROJECT_ID=...`). Avoid committing local values to source control. Keep any private values in your local environment or an ignored `~/.gradle/gradle.properties` file.

Note: Firebase API keys and OAuth client IDs are app configuration identifiers, not substitutes for server-side authorization. Restrict the API key appropriately in Google Cloud, and configure the exact Android signing certificate SHA-1/SHA-256 fingerprints in Firebase/Google Cloud for the APK you distribute.
