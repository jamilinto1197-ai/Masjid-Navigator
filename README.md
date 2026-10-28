# MasjidPH Navigator

A mobile-friendly Philippine mosque finder and prayer companion.

## Features
- Responsive mosque-finder interface
- Device-location support for distance calculations (permission required)
- Qibla direction calculator
- Estimated prayer times; verify them against a local masjid timetable
- Progressive web app assets and Android WebView wrapper

## Build an Android debug APK
1. Open the **Actions** tab.
2. Select **Build MasjidPH Android Debug APK**.
3. Choose **Run workflow**.
4. Download the `MasjidPH-Navigator-debug-APK` artifact when the run succeeds.

This is a debug/test APK, not a Play Store release. Test on a real Android device before relying on GPS, maps, prayer times, or other device features. Mosque entries are starter data and should be verified locally.

## Privacy
Location should only be requested after the user activates a location-dependent feature. The Qibla and distance calculations are intended to run on-device.