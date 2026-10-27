#!/usr/bin/env bash
# Smoke test run inside the Android TV emulator by .github/workflows/build.yml.
set -u
PKG=app.haven.launcher
OUT=smoke
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
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
screen Art 7-art 25
key DPAD_CENTER
sleep 3
shot 8-back-home

if adb shell pidof "$PKG" > /dev/null; then echo "Haven is running"; else echo "Haven is NOT running"; fi
adb logcat -d -b crash > "$OUT/crash.txt"
adb logcat -d > "$OUT/logcat.txt"
echo "----- crash buffer -----"
cat "$OUT/crash.txt"
echo "----- Haven log lines -----"
grep -E "AndroidRuntime|$PKG|FATAL" "$OUT/logcat.txt" | tail -120

if grep -q "$PKG" "$OUT/crash.txt"; then
  echo "::error::Haven crashed on the emulator; see the crash buffer above"
  exit 1
fi
