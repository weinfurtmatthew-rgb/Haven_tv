#!/usr/bin/env bash
# Smoke test and UI tour, run inside the Android TV emulator by .github/workflows/build.yml.
# Fails if Haven crashes. Screenshots of every screen land in smoke/ (pushed to the screens-apiNN branch).
set -u
PKG=app.haven.launcher
OUT=smoke
TAG="${1:-}"
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$TAG-$1.png"; }
key() { for k in "$@"; do adb shell input keyevent "KEYCODE_$k"; sleep 0.7; done; }
screen() { adb shell am start -W -n "$PKG/.MainActivity" --es "$PKG.SCREEN" "$1" > /dev/null; sleep "${3:-6}"; shot "$2"; }
demo() { adb shell am broadcast -n "$PKG/.demo.DemoReceiver" -a "$PKG.demo.$1" "${@:2}" > /dev/null; sleep 1; }
home() { adb shell am start -W -n "$PKG/.MainActivity" --es "$PKG.SCREEN" Home > /dev/null; sleep "${1:-4}"; }

adb shell getprop ro.build.version.release
adb shell wm size
adb shell wm density
adb install -r -g haven.apk
adb logcat -c

# ---- 1. First run: nothing configured -------------------------------------------------------
adb shell am start -W -n "$PKG/.MainActivity"
sleep 15
shot 01-first-run-home
screen Lights 02-lights-not-connected
screen Sports 03-sports-no-teams
screen Briefing 04-briefing-no-ha 10
screen Settings 05-settings-first-run
screen Art 06-art-tv-type-dialog 6
key BACK
sleep 22
shot 07-art-thin-frame
key DPAD_CENTER

# ---- 2. Demo household ----------------------------------------------------------------------
demo ENABLE
sleep 3
home 10
shot 10-home
key DPAD_DOWN
shot 11-home-apps-focus
key DPAD_DOWN
shot 12-home-room-focus
key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT
shot 13-home-room-focus-right
key DPAD_UP DPAD_UP DPAD_UP DPAD_UP
shot 14-home-topbar-focus
key DPAD_DOWN DPAD_RIGHT
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER
sleep 2
shot 15-home-app-menu
key BACK

screen Lights 20-lights 6
key DPAD_DOWN
shot 21-lights-row-focus
key DPAD_RIGHT DPAD_RIGHT
shot 22-lights-brightness-up
key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT
shot 23-lights-speakers-focus
key DPAD_DOWN DPAD_CENTER
sleep 1
shot 24-lights-speaker-toggled

screen Settings 30-settings-top
key DPAD_CENTER
shot 31-settings-first-row
key DPAD_LEFT DPAD_DOWN DPAD_DOWN DPAD_CENTER DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_CENTER
sleep 1
shot 32-settings-art-phone-hint
key DPAD_LEFT DPAD_DOWN DPAD_DOWN DPAD_DOWN
shot 33-settings-home-button
key DPAD_DOWN DPAD_DOWN
shot 34-settings-about
key DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_CENTER DPAD_CENTER
sleep 2
shot 36-settings-text-dialog
key BACK BACK

screen Alarm 40-alarm-editor
screen Sleep 41-sleep-timer
key DPAD_CENTER
sleep 2
shot 41-sleep-timer-running
key DPAD_DOWN DPAD_DOWN DPAD_CENTER
screen Briefing 42-briefing 15
screen Sports 43-sports 12

demo PATCH --es style FullScreen
screen Art 50-art-full-screen 25
key DPAD_CENTER
demo PATCH --es style Gallery
screen Art 51-art-gallery 25
key DPAD_CENTER
demo PATCH --es style ThinFrame --es source Generated
screen Art 52-art-generated 8
key DPAD_CENTER
demo PATCH --es source Paintings

demo ALARM
sleep 5
shot 60-alarm-ringing
key DPAD_CENTER
sleep 12
shot 61-briefing-after-alarm
key DPAD_CENTER

demo CRASH
sleep 3
shot 70-crash-report
key BACK

# Score alerts draw over other apps (Android hides them over its own Settings, so show one over Haven).
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
home 4
demo POPUP
sleep 2
shot 80-score-alert

home 4
shot 90-home-end

# ---- results --------------------------------------------------------------------------------
if adb shell pidof "$PKG" > /dev/null; then echo "Haven is running"; else echo "Haven is NOT running"; fi
adb logcat -d -b crash > "$OUT/crash.txt"
adb logcat -d > "$OUT/logcat.txt"
cp "$OUT/crash.txt" "$OUT/$TAG-crash.txt"
echo "----- crash buffer -----"
cat "$OUT/crash.txt"
echo "----- Haven log lines -----"
grep -E "AndroidRuntime|$PKG|FATAL" "$OUT/logcat.txt" | tail -80

# The demo CRASH step records a report without crashing, so any FATAL here is real.
if grep -q "$PKG" "$OUT/crash.txt"; then
  echo "::error::Haven crashed on the emulator; see the crash buffer above"
  exit 1
fi
