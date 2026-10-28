# MasjidPH Navigator

A native Android mosque finder and prayer companion for the Philippine Muslim community.

## Android build and security policy

The automated workflow is a **verification pipeline**, not an APK distribution pipeline. It runs Android lint, source security analysis, dependency vulnerability scanning, APK manifest/signature checks, and a Firebase configuration gate. It does not publish an installable APK. Debug APKs are not release artifacts and must not be considered safe solely because compilation succeeds.

## Firebase Android configuration

The native app uses Firebase Authentication and Cloud Firestore. The previous hard-coded Android application ID was not a valid Firebase Android app ID. Firebase now fails closed when the ID is missing or malformed.

1. In Firebase Console, register an Android app in project `masjid-109f3` with package name `com.masjidph.navigator`.
2. Copy that app's `mobilesdk_app_id` from its `google-services.json`; the value must look like `1:885467340998:android:0123456789abcdef`.
3. For local builds, pass it as a Gradle property:
   ```bash
   gradle --no-daemon -p android -PfirebaseAndroidAppId=1:885467340998:android:0123456789abcdef :app:assembleDebug
   ```
   For GitHub Actions, set the repository Actions variable `FIREBASE_ANDROID_APP_ID` to the actual value. The sample ID above is illustrative, not a valid project value.
4. Verify the Firestore security rules in Firebase Console before using real user data. Client-side code does not replace server-side rules.

Do not commit private service-account keys or Firebase Admin credentials. Firebase client configuration (including API key and app ID) is not a substitute for Firestore Security Rules.

## Current feature status

The current native UI includes email sign-in/registration/reset-password flows and a Firestore-backed mosque list/profile. Google sign-in still requires the matching OAuth Android client and signing fingerprints. Community chat, the device-sensor Qibla compass, and location-based prayer-time calculations are not complete; their screens are placeholders and should not be described as finished features.

## Safety note

A previous debug APK was flagged by Phone Manager and by four VirusTotal engines. The exact APK hash is being treated as untrusted until those detections are resolved or independently assessed. Do not install that APK or disable security protections to bypass the warning.
