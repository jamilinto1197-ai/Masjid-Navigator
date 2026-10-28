#!/usr/bin/env bash
set -euo pipefail

APK="${1:-android/app/build/outputs/apk/debug/app-debug.apk}"
REPORT_DIR="${2:-android/app/build/reports/security}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
BUILD_TOOLS_VERSION="35.0.0"

mkdir -p "$REPORT_DIR"
if [[ ! -f "$APK" ]]; then
  echo "::error::APK not found at $APK"
  exit 1
fi
if [[ -z "$ANDROID_SDK_ROOT" ]]; then
  echo "::error::ANDROID_HOME or ANDROID_SDK_ROOT is not set"
  exit 1
fi

AAPT="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/aapt"
APKSIGNER="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/apksigner"
for tool in "$AAPT" "$APKSIGNER"; do
  if [[ ! -x "$tool" ]]; then
    echo "::error::Required Android SDK tool not found: $tool"
    exit 1
  fi
done

BADGING="$REPORT_DIR/apk-badging.txt"
PERMISSIONS="$REPORT_DIR/apk-permissions.txt"
MANIFEST_TREE="$REPORT_DIR/merged-manifest-tree.txt"
SIGNATURE="$REPORT_DIR/apk-signature.txt"
CONTENTS="$REPORT_DIR/apk-contents.txt"
HASH="$REPORT_DIR/apk-sha256.txt"

"$AAPT" dump badging "$APK" > "$BADGING"
if ! grep -Fq "package: name='com.masjidph.navigator'" "$BADGING"; then
  echo "::error::Unexpected application ID in compiled APK"
  cat "$BADGING"
  exit 1
fi

"$AAPT" dump permissions "$APK" > "$PERMISSIONS"
"$AAPT" dump xmltree "$APK" AndroidManifest.xml > "$MANIFEST_TREE"

TARGET_SDK="$(sed -n "s/.*targetSdkVersion:'\\([0-9][0-9]*\\)'.*/\\1/p" "$BADGING" | head -n1)"
if [[ -z "$TARGET_SDK" || "$TARGET_SDK" -lt 29 ]]; then
  echo "::error::Compiled APK target SDK is missing or below 29"
  cat "$BADGING"
  exit 1
fi
if grep -q 'android:launchMode.*singleTask' "$MANIFEST_TREE"; then
  echo "::error::Launcher uses singleTask launch mode; investigate task-hijacking exposure"
  exit 1
fi
if ! grep -q 'android:taskAffinity.*=""' "$MANIFEST_TREE"; then
  echo "::error::Compiled launcher task affinity is not explicitly empty"
  exit 1
fi

# These capabilities have no legitimate use in the current mosque finder.
# A dependency adding one of them must be reviewed explicitly before approval.
# WorkManager legitimately uses RECEIVE_BOOT_COMPLETED to restore user-enabled daily reminder work after reboot.
DENIED_PERMISSION_PATTERN='android\.permission\.(READ_SMS|RECEIVE_SMS|SEND_SMS|RECEIVE_MMS|READ_CONTACTS|WRITE_CONTACTS|CALL_PHONE|READ_CALL_LOG|WRITE_CALL_LOG|RECORD_AUDIO|CAMERA|ACCESS_BACKGROUND_LOCATION|MANAGE_EXTERNAL_STORAGE|SYSTEM_ALERT_WINDOW|REQUEST_INSTALL_PACKAGES|QUERY_ALL_PACKAGES|BIND_ACCESSIBILITY_SERVICE|BIND_DEVICE_ADMIN)'
if grep -E "$DENIED_PERMISSION_PATTERN" "$PERMISSIONS"; then
  echo "::error::APK requests a high-risk permission that is not approved for this app"
  exit 1
fi

# WorkManager may request RECEIVE_BOOT_COMPLETED to restore the user's opt-in
# daily reminder work. Accept it only when its non-exported reschedule receiver
# is present in the merged manifest.
if grep -q 'android.permission.RECEIVE_BOOT_COMPLETED' "$PERMISSIONS"; then
  if ! grep -q 'androidx.work.impl.background.systemalarm.RescheduleReceiver' "$MANIFEST_TREE"; then
    echo "::error::RECEIVE_BOOT_COMPLETED is present without WorkManager's expected reschedule receiver"
    exit 1
  fi
fi

# The app's reminder receiver is private to this package and must stay non-exported.
if ! grep -A12 'com.masjidph.navigator.PrayerReminderReceiver' "$MANIFEST_TREE" | grep -qi 'android:exported.*false'; then
  echo "::error::Prayer reminder receiver is missing or exported"
  exit 1
fi

# Native migration must not accidentally package a legacy WebView/PWA bundle.
# Reject both the old assets/www path and Capacitor's newer assets/public path.
unzip -Z1 "$APK" > "$CONTENTS"
if grep -Eq '^assets/(www|public)/' "$CONTENTS"; then
  echo "::error::Legacy web assets are still packaged in the native APK"
  grep -E '^assets/(www|public)/' "$CONTENTS"
  exit 1
fi

# Do not allow unsafe dynamic-code or WebView trust overrides in first-party Java.
if grep -RInE 'DexClassLoader|PathClassLoader|Runtime\.getRuntime\(\)\.exec|ProcessBuilder|addJavascriptInterface|onReceivedSslError|\.proceed\(\)' android/app/src/main/java; then
  echo "::error::Review unsafe dynamic code, command execution, or WebView APIs in first-party source"
  exit 1
fi

"$APKSIGNER" verify --verbose --print-certs "$APK" > "$SIGNATURE"
sha256sum "$APK" > "$HASH"

{
  echo "APK security checks completed."
  echo "App ID: com.masjidph.navigator"
  echo "Legacy Android web bundle (assets/www and assets/public): absent"
  echo "High-risk permission denylist: passed"
  echo "First-party unsafe API pattern check: passed"
  echo "APK signature verification: passed"
  cat "$HASH"
} | tee "$REPORT_DIR/summary.txt"
