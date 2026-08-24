#!/bin/bash
# Builds, installs and configures NoScroll Guard on a connected phone.
#
# Requires on the phone (Developer options, both need a Mi account):
#   - USB debugging (Security settings)  — to grant permissions
#   - Install via USB                    — to install over ADB
set -e

PKG=io.github.haku4130.noscrollguard
APK=app/build/outputs/apk/debug/app-debug.apk

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"

if ! adb devices | grep -q "device$"; then
  echo "No device connected (check the cable and pick 'File transfer' mode)"
  exit 1
fi

echo "=== 1. Build ==="
./gradlew assembleDebug -q

echo "=== 2. Install ==="
adb install -r "$APK"

echo "=== 3. Permissions ==="
for P in WRITE_SECURE_SETTINGS PACKAGE_USAGE_STATS DUMP; do
  adb shell pm grant $PKG android.permission.$P 2>/dev/null && echo "  $P granted" || echo "  $P NOT granted"
done

echo "=== 4. Battery whitelist ==="
adb shell dumpsys deviceidle whitelist +$PKG

echo "=== 5. Start ==="
# The service is exported=false, so start it through the activity.
adb shell am start -n $PKG/.ui.MainActivity > /dev/null
sleep 4

echo "=== 6. Verify ==="
adb shell dumpsys package $PKG 2>/dev/null | grep -E "WRITE_SECURE_SETTINGS: granted|PACKAGE_USAGE_STATS: granted"
if adb shell pidof $PKG > /dev/null; then echo "  guard is running"; else echo "  WARNING: process did not come up"; fi

echo
echo "One manual step remains on the phone:"
echo "  Security -> Permissions -> Autostart -> enable NoScroll Guard"
