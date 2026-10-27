#!/usr/bin/env bash
# Smoke test run inside the Android TV emulator by .github/workflows/build.yml.
set -u
PKG=app.haven.launcher
OUT=smoke
TAG="${1:-}"
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$TAG-$1.png"; }
key() { for k in "$@"; do adb shell input keyevent "KEYCODE_$k"; sleep 1; done; }
screen() { adb shell am start -W -n "$PKG/.MainActivity" --es "$PKG.SCREEN" "$1" > /dev/null; sleep "${3:-6}"; shot "$2"; }

adb shell getprop ro.build.version.release
adb install -r -g haven.apk
adb logcat -c

adb shell am start -W -n "$PKG/.MainActivity"
sleep 15
shot 1-home
key DPAD_DOWN DPAD_RIGHT DPAD_RIGHT DPAD_DOWN DPAD_DOWN
shot 2-home-moved
screen Lights 3-lights
screen Settings 4-settings
screen Alarm 5-alarm
screen Sleep 6-sleep
screen Briefing 7a-briefing 12
screen Sports 7b-sports 12
screen Art 7-art 25
key DPAD_CENTER
sleep 3
shot 8-back-home

# Turn on the Home button service the way the Settings switch does, then press Home from another app.
echo "----- Home button service -----"
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.system.HomeButtonService"
adb shell settings put secure accessibility_enabled 1
sleep 6
echo "enabled_accessibility_services = $(adb shell settings get secure enabled_accessibility_services)"
adb shell dumpsys accessibility | grep -iE "haven|bound services|enabled services|crashed" | head -20
adb shell am start -W -a android.settings.SETTINGS > /dev/null
sleep 4
key HOME
sleep 4
shot 9-after-home-key
echo "Top activity after Home: $(adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity')"
echo "enabled_accessibility_services after Home = $(adb shell settings get secure enabled_accessibility_services)"

if adb shell pidof "$PKG" > /dev/null; then echo "Haven is running"; else echo "Haven is NOT running"; fi
adb logcat -d -b crash > "$OUT/crash.txt"
adb logcat -d > "$OUT/logcat.txt"
cp "$OUT/crash.txt" "$OUT/$TAG-crash.txt"; cp "$OUT/logcat.txt" "$OUT/$TAG-logcat.txt"
echo "----- crash buffer -----"
cat "$OUT/crash.txt"
echo "----- Haven log lines -----"
grep -E "AndroidRuntime|$PKG|FATAL" "$OUT/logcat.txt" | tail -120

if grep -q "$PKG" "$OUT/crash.txt"; then
  echo "::error::Haven crashed on the emulator; see the crash buffer above"
  exit 1
fi
