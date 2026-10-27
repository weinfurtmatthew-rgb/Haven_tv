# Haven TV launcher

Haven is a home screen (launcher) for Android TV boxes, built for the DIRECTV STREAM box but usable on any
Android TV / Google TV device running Android 8 or newer. It follows the Haven mockups in `concept/`: one shared
design, with features that depend on the room the TV is in.

**On every TV**
- Clock, date and weather (from Home Assistant), your favorite apps as big cards, and every installed app.
- Hold OK on an app to pin it, move it, hide it or open its app info.
- **Lights and speakers** for the room, through Home Assistant: scenes, on/off, brightness and volume with the remote.
- **Art mode**: public-domain paintings (Art Institute of Chicago), your own photos, or generated art, in a frame on a
  warm wall. It can start after the home screen sits idle, and it can be the system screensaver.
- **Phone setup**: scan a QR code in Settings to connect Home Assistant, pick the room's lights, upload photos and
  set the alarm from your phone, with no typing on the remote.
- **Updates in the app**: Settings › Updates downloads the newest build from this repo's releases.

- **Art mode styles and screen care**: full screen, thin frame or gallery. It protects against burn-in with pixel shift,
  captions that fade, stronger protection on OLED, optional dimming, quiet hours (dim or black), an optional
  motion sensor (black when nobody's there) and a time limit.
- **Sports**: follow NFL, MLB, NHL, NBA, Premier League, La Liga and Champions League teams (ESPN's public feed,
  which is unofficial and may change). You get live scores and next games on the home screen and the Sports screen.
  Selecting a game opens the streaming app that carries it, or DIRECTV with the channel to look for. Score alerts
  appear over any app once "Display over other apps" is allowed; otherwise they show as small messages.

**Bedroom features** (turn on in Settings › Bedroom features)
- **Alarm** with snooze, chosen days, and *sunrise lights* that fade the room's lights up before it rings.
  Stopping it opens the **morning briefing**: weather with an hourly strip, today's calendar, commute time, home
  status (locks, garage), headlines from any RSS feed, and your teams' games. Pick the sources on the phone page.
- **Sleep timer**: when time is up it pauses playback, turns off the room's lights and puts the box (and TV) to sleep.

## Install on the DIRECTV STREAM box

You need the remote and about ten minutes. Some DIRECTV boxes are locked down by DIRECTV's device policy.
If step 2's **Unknown sources** switch is missing or blocked, the box won't accept apps from outside Google Play,
and Haven can't be installed this way. Stop there rather than trying developer-mode workarounds: owners report that
some Gemini boxes disable themselves when developer mode is turned on. Menu names below are the standard Android TV ones; your box may word
them slightly differently.

1. **Install Downloader.** On the box, open the Google Play Store, search for **Downloader** (by AFTVnews) and install it.
2. **Allow it to install apps.** Settings › Device Preferences › Security & restrictions › Unknown sources ›
   turn on **Downloader**. (If you don't see this, open Downloader once and try the install; Android will offer
   the switch.)
3. **Download Haven.** Open Downloader and type this address into the URL box:

   ```
   https://github.com/weinfurtmatthew-rgb/haven_tv/releases/latest/download/haven.apk
   ```

   When it finishes, choose **Install**. You can delete the APK afterwards when Downloader asks.
4. **Open Haven** from the apps row of the stock home screen.
5. **Make the Home button open Haven.** The DIRECTV box doesn't let you choose a different home screen, so Haven
   includes a small accessibility service for this:
   Settings › Device Preferences › Accessibility › **Haven Home button** › turn it on, and confirm.
   Now Home opens Haven. **Press Home twice quickly** to get the original home screen (on this box, the DIRECTV
   app) whenever you want it.
   - If the switch is greyed out ("restricted setting"), go to Settings › Apps › Haven, open the menu or
     the app info screen, choose **Allow restricted settings**, then try again.
   - The service only listens for the Home button and notices when the stock home screen opens. It does not read
     what's on screen.
6. **Set it up from your phone.** In Haven, select the round avatar button (top right) to open Settings. Scan the
   QR code with your phone (same Wi-Fi) and fill in Home Assistant, the room and your photos.
7. Optional: Settings › Device Preferences › **Screen saver** › choose **Haven art** to get art mode when the
   box sits idle in any app.

### Updating

Haven › Settings › **Updates** checks this repo for a newer build, downloads it and opens the installer. The first
time, Android asks you to allow Haven to install apps. Haven also shows an **Update ready** chip on the home screen
when there is one.

### Connecting Home Assistant

1. In Home Assistant, open your profile (bottom left) › **Security** › **Long-lived access tokens** › **Create token**.
   Name it Haven and copy it. Use an **admin** account, so Haven can read your areas.
2. On the phone setup page, enter the address (for example `http://192.168.1.10:8123`; using the IP address is
   more reliable than `homeassistant.local`) and paste the token. Tap **Test**, then **Save**.
3. Under **This room**, pick the Home Assistant **area** this TV is in. Haven then shows that area's lights,
   speakers (media players) and scenes. Untick anything you don't want on the TV.
4. Weather comes from your Home Assistant weather entity (the default "Home" forecast works).

## DIRECTV on its own box

On the DIRECTV box, the built-in "launcher" **is** the DIRECTV app: live TV, the guide and the DVR all live in it.
That's why other launchers tend to break DIRECTV, especially ones you install by disabling the stock launcher
over ADB. Haven works around this instead of replacing the DIRECTV app:

- Haven never disables or hides the DIRECTV app. It sits on top, and only takes over when you press Home.
- **DIRECTV is the first, big card** on Haven's home screen. Selecting it opens the DIRECTV app as usual, with live
  TV, guide and DVR.
- Inside DIRECTV, Haven stays out of the way: it doesn't pull you back when DIRECTV's own screens open. Home
  goes to Haven, and Home twice goes to DIRECTV's home.
- Installing the DIRECTV app from Google Play isn't needed (and is reportedly not offered on DIRECTV's own boxes).
  **Don't disable the built-in DIRECTV app** with ADB to "make room" for Haven; that removes live TV.
- If something misbehaves, Settings › Home button › **Recent screens** lists which apps and screens opened.
  That tells us exactly how DIRECTV's screens are named on your box.

## What to know

- **Continue watching isn't possible** for a third-party launcher: Android only lets the built-in home screen read
  other apps' watch progress. The top row is your favorite apps instead, with when you last opened them.
- **HDMI inputs and cameras** from the mockups are left out: the box has no HDMI inputs, and cameras weren't in
  scope for this version.
- **The alarm** needs the box to be able to wake the TV. Leave the box plugged in, keep HDMI-CEC on, and try the
  alarm once (Alarm › Try the alarm now) before relying on it.
- **Sleep timer** needs the Home button service on to put the box to sleep (Android 9+). Without it, Haven still
  pauses playback and turns off the lights.
- **The setup page** runs only while Haven's Settings screen is open, and only accepts requests carrying the code
  shown on the TV. Your Home Assistant token is stored only on the box.

## Building

GitHub Actions builds every push to `main` (`.github/workflows/build.yml`), runs the unit tests and publishes the
APK as release `v1.0.<build number>`. The build number is also the Android version code, so each build installs
over the previous one.

To build locally you need JDK 17 and the Android SDK (Android Studio or the command-line tools):

```bash
./gradlew testDebugUnitTest assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

**Signing:** `app/haven-release.jks` is a dedicated key committed on purpose, so CI and local builds produce APKs
that update each other. It only signs this sideloaded app. Anyone with this repo could sign an APK as "Haven",
so only install Haven builds from this repo's releases.

## Code map

| Path | What it is |
|---|---|
| `app/src/main/java/app/haven/launcher/MainActivity.kt` | The launcher activity and screen switching |
| `…/ui/` | Compose screens: home, lights and speakers, art mode, alarm, sleep timer, settings; theme, icons, D-pad components |
| `…/data/` | Settings (one JSON blob in SharedPreferences) and the installed-apps list |
| `…/ha/HomeAssistant.kt` | Home Assistant REST client: states, areas (template API), services |
| `…/art/` | Art sources (paintings, photos, generated) and the screensaver service |
| `…/bedroom/` | Alarm scheduling, the ringing screen and chime, sunrise lights, sleep timer |
| `…/system/HomeButtonService.kt` | Home button override (accessibility service) and the boot receiver |
| `…/setup/SetupServer.kt`, `app/src/main/assets/setup.html` | The phone setup page and its tiny HTTP server |
| `…/update/Updater.kt` | In-app updates from GitHub releases |
| `concept/` | The original Haven concept: mockups, web ad and film renderers (see `concept/README.md`) |

The UI is laid out on the mockups' 1920×1080 canvas (`DesignCanvas`): one `dp` is one design pixel, whatever the
box's resolution. Colors and type (Manrope) match the mockups.

Third-party assets: Manrope font (SIL OFL 1.1), Lucide icons (ISC), artwork images from the Art Institute of
Chicago API (public domain works, CC0 data).
