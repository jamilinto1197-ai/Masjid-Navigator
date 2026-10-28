# MasjidPH Navigator

A mobile-friendly Philippine mosque finder and prayer companion. The Android project is being migrated to a fully native experience.

## Security notice — Android APK distribution paused

A previously published debug APK was flagged by Android Phone Manager and by four VirusTotal engines. The detected file has been removed from the repository. Do not install copies of that APK or disable device security to bypass the warning.

The default branch currently runs a **compile-only diagnostic** workflow. It does not upload or publish APKs. Security scanning and hardening are being developed separately; a successful compile is not proof of app safety.

## Current feature status

The existing web app includes a starter mosque directory, location-based distances and maps, Qibla bearing calculation, estimated prayer times, and Firebase email/Google authentication and profile/settings data. The native Android migration is incomplete: the current native Qibla screen, prayer times, community chat, and some Firebase workflows are still under development. Mosque starter listings are approximate and must be verified before travel. Prayer times should be confirmed against a local masjid timetable.

## Firebase

The native Android app must be registered as package `com.masjidph.navigator` in Firebase project `masjid-109f3`. The valid Android `mobilesdk_app_id` is distinct from the web app ID. Do not substitute the web app ID or invent an Android app ID. Firestore Security Rules must be configured and deployed before using real user data.

## Build

The GitHub Actions workflow currently compiles the debug APK only for diagnostics. It does not produce a downloadable artifact while the security review is open. Do not rely on a compile-only build as a release.
