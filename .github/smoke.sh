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
rep() { for _ in $(seq "$2"); do adb shell input keyevent "KEYCODE_$1"; sleep 0.4; done; sleep 0.4; }
screen() { adb shell am start -W -n "$PKG/.MainActivity" --es "$PKG.SCREEN" "$1" > /dev/null; sleep "${3:-6}"; shot "$2"; }
demo() { adb shell am broadcast -n "$PKG/.demo.DemoReceiver" -a "$PKG.demo.$1" "${@:2}" > /dev/null; sleep 1; }
# Quoted again for the device's shell, which would otherwise split "a=1;b=2" at the semicolon.
opt() { demo OPT --es set "'$1'"; }
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

# Settings: six groups. Each starts with what most people change; More options opens the rest. Left and right
# change a value in place (saved at once); OK steps on, or opens the whole list when it's long. Back: to the groups.
screen Settings 30-settings-profiles
key DPAD_DOWN
shot 27-settings-home-screen
key DPAD_CENTER
sleep 1
key DPAD_CENTER
sleep 1
key DPAD_DOWN DPAD_DOWN
sleep 2
shot 28-layout-dialog-preview
key BACK
sleep 1
key DPAD_DOWN DPAD_CENTER
sleep 1
shot 33-look-scheme-dialog
key DPAD_DOWN DPAD_DOWN
sleep 1
shot 34-look-scheme-preview
key BACK
sleep 1
key DPAD_RIGHT
sleep 2
shot 32-settings-scheme-right
key DPAD_LEFT
sleep 1
key DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_RIGHT
sleep 1
shot 37b-tile-size-right
key DPAD_LEFT DPAD_DOWN DPAD_CENTER
sleep 1
shot 37-home-rows-dialog
key BACK
sleep 1
key DPAD_DOWN DPAD_CENTER
sleep 1
key DPAD_DOWN DPAD_DOWN
sleep 1
shot 36-settings-more-options
rep DPAD_DOWN 4
key DPAD_CENTER
sleep 1
shot 38-settings-phone-hint
key BACK
sleep 1
key DPAD_DOWN
shot 39-settings-smart-home
key DPAD_DOWN
shot 41-settings-sports
key DPAD_DOWN
shot 40-settings-art-bedroom
key DPAD_DOWN
shot 42-settings-this-tv
rep DPAD_UP 6
key DPAD_CENTER DPAD_CENTER
sleep 2
shot 31-settings-name-dialog
key BACK BACK BACK

screen Alarm 43-alarm-editor
key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_DOWN DPAD_CENTER
sleep 1
shot 44-alarm-sound-dialog
key BACK
screen Sleep 45-sleep-timer
key DPAD_CENTER
sleep 2
shot 46-sleep-timer-running
key DPAD_DOWN DPAD_DOWN DPAD_CENTER
screen Briefing 47-briefing 15
screen Sports 48-sports 12
screen Profiles 49-profiles 5

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

# ---- 3. Everything is an option ---------------------------------------------------------------
# Looks: each one a whole different home screen.
opt "look.theme=Daylight;look.accent=Coral;look.headingFont=Fredoka;look.textFont=Nunito;look.corners=Soft;look.focus=Frame"
home 6
shot 53-home-daylight
key DPAD_DOWN
shot 54-home-daylight-focus
opt "look.theme=Plum;look.accent=Gold;look.headingFont=Bebas;look.textFont=Lexend;look.corners=Square;look.focus=ColorFrame;look.favSize=Small;look.appSize=Small;look.spacing=Compact;home.rows=Recent,Favorites,Apps,Room;look.clock=Small;look.appNames=Always"
home 6
shot 55-home-plum-compact
opt "look.theme=Black;look.accent=Mint;look.headingFont=Playfair;look.textFont=Literata;look.textScale=Larger;look.favSize=Huge;look.corners=Round;look.focus=Fill;look.appArt=Icon;look.appNames=OnFocus;look.clock=Large;home.rows=Favorites,Apps,Room"
home 6
shot 56-home-black-large
key DPAD_DOWN
shot 57-home-black-icons-focus
demo RESET

# Backgrounds.
opt "bg.kind=Gradient;bg.gradient=Aurora"
home 5
shot 60-bg-gradient
demo BGPHOTO
opt "bg.kind=Photo;bg.blur=Soft;bg.dim=30"
home 8
shot 61-bg-photo-soft
opt "bg.kind=Daily;bg.blur=None;bg.dim=45"
home 20
shot 62-bg-photo-of-the-day
opt "bg.kind=Art"
home 25
shot 63-bg-painting
opt "bg.kind=Color;bg.color=4279179818"
home 5
shot 64-bg-colour
# The background from the home screen: the picture button in the top bar opens a strip along the bottom. Moving
# along it tries each background behind it; OK keeps one, Back closes.
key DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_RIGHT DPAD_CENTER
sleep 2
shot 64b-background-strip
key DPAD_LEFT DPAD_LEFT DPAD_LEFT
sleep 4
shot 64c-background-strip-trying
key BACK
sleep 1
screen Settings 65-settings-over-colour
demo BGPHOTO
opt "bg.kind=Photo;bg.blur=Strong;bg.dim=45"
screen Settings 66-settings-over-photo 8
demo RESET

# Apps with their own names and pictures, then the other profile's home screen.
demo APPLOOKS
home 6
shot 67-home-app-looks
demo SWITCH --es name Sam
home 6
shot 68-home-sam
demo SWITCH --es name Matt

# Sports layouts and spoiler-free scores.
opt "sports.layout=Big;sports.noSpoilers=on;sports.teamColors=on"
screen Sports 70-sports-big-no-spoilers 12
opt "sports.layout=Compact;sports.noSpoilers=off;sports.board=off"
screen Sports 71-sports-compact-wide 10
opt "sports.layout=Cards;sports.board=on;sports.teamColors=off"

# Briefing layouts.
opt "brief.layout=Single;brief.greeting=Plain;brief.background=Sunrise"
screen Briefing 72-briefing-single 15
opt "brief.layout=Minimal;brief.background=Art"
screen Briefing 73-briefing-minimal 20
opt "brief.layout=Columns;brief.greeting=WithName;brief.background=Theme"

# Alarm looks and ways to stop it (Back snoozes; the demo then cancels the snooze).
opt "alarm.stopWith=Hold;alarm.background=Art;alarm.greeting=Custom;alarm.message=Rise and shine"
demo ALARM
sleep 12
shot 74-alarm-hold-painting
key BACK
demo UNSNOOZE
opt "alarm.stopWith=Sum;alarm.background=Black;alarm.greeting=WithName"
demo ALARM
sleep 6
shot 75-alarm-sum
key BACK
demo UNSNOOZE
opt "alarm.stopWith=Press;alarm.background=Sunrise"

# ---- 4. Layouts --------------------------------------------------------------------------------
# Every layout, picked the way a person would (its look and background come with it), then a mix of two.
demo BGPHOTO
opt "home.layout=Spotlight;spot.source=Photos"
home 14
shot 100-layout-spotlight
key DPAD_RIGHT
sleep 2
shot 101-spotlight-focus
key DPAD_DOWN
sleep 2
shot 102-spotlight-apps
key DPAD_UP
sleep 2
opt "spot.source=Daily"
home 20
shot 103-spotlight-photo-of-the-day
opt "home.layout=BigTiles"
home 8
shot 104-layout-big-tiles
key DPAD_LEFT
sleep 1
key DPAD_DOWN DPAD_DOWN
sleep 3
shot 105-big-tiles-menu-sports
opt "home.layout=GameDay"
home 10
shot 106-layout-game-day
opt "home.layout=Cinema"
home 14
shot 107-layout-cinema
key DPAD_CENTER
sleep 2
shot 108-movie-details
key BACK
opt "home.layout=Shows"
home 10
shot 109-layout-shows
key DPAD_UP DPAD_UP DPAD_RIGHT DPAD_RIGHT
sleep 3
shot 110-shows-tab-movies
opt "home.layout=SmartHome"
home 10
shot 111-layout-smart-home
key DPAD_DOWN DPAD_DOWN
sleep 1
shot 112-smart-home-climate
key DPAD_CENTER
sleep 2
shot 113-thermostat-dialog
key BACK
opt "home.layout=Music"
home 10
shot 114-layout-music
opt "home.layout=Spotlight;spot.source=Photos;home.rows=YourTeams,Favorites,Apps"
home 14
shot 115-mix-spotlight-teams
demo RESET
demo SWITCH --es name Lily
home 10
shot 116-layout-kids
screen Settings 117-grown-up-pin 4
key BACK
screen Bedtime 118-bedtime 5
key DPAD_CENTER
demo SWITCH --es name Matt
home 6

demo ALARM
sleep 5
shot 80-alarm-ringing
key DPAD_CENTER
sleep 12
shot 81-briefing-after-alarm
key DPAD_CENTER

demo CRASH
sleep 3
shot 85-crash-report
key BACK

# Score alerts draw over other apps (Android hides them over its own Settings, so show one over Haven).
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
home 4
demo POPUP
sleep 2
shot 86-score-alert
sleep 8
opt "sports.alertCorner=BottomLeft;sports.alertSize=Large"
demo POPUP
sleep 2
shot 87-score-alert-large-corner
opt "sports.alertCorner=TopRight;sports.alertSize=Normal"

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
