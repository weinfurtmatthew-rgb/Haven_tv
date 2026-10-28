# CLAUDE.md — Haven TV launcher

Context for Claude Code. README.md covers features, the DIRECTV install steps and building.

## What this is
A real Android TV launcher (Kotlin + Jetpack Compose, no XML layouts) built from the Haven concept in `concept/`
(mockups, ad, films; see `concept/CLAUDE.md` for working on those). Main target: the DIRECTV STREAM box, which
does not let users change the default home app, so `system/HomeButtonService.kt` (an accessibility service)
catches the Home key. Double Home opens the stock home screen. On that box the stock "launcher" is the DIRECTV
app itself (live TV, guide, DVR), so the service must never redirect away from a provider's home app: automatic
redirects only apply to Google's plain launchers (`GENERIC_LAUNCHERS`). The DIRECTV home app is listed in Haven's
apps (HOME-category query in `data/Apps.kt`) and is the default first favorite. Never suggest disabling it.

## Build and verify
- CI (`.github/workflows/build.yml`) is the reference build: `testDebugUnitTest` then `assembleRelease`, then a
  GitHub release `v1.0.<run_number>` with `haven.apk`. The in-app updater (`update/Updater.kt`) parses that tag,
  so keep the `v1.0.<n>` format and keep versionCode = run number.
- Local: JDK 17 + Android SDK, `./gradlew testDebugUnitTest assembleRelease`.
- Versions: AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, compileSdk 35, targetSdk 34, minSdk 26.
- Unit tests (`app/src/test`) cover pure logic only (alarm times, the options registry, layouts, feed and media
  parsers, app kinds, the depth clock, alarm tones); they get the real org.json library (testImplementation), not
  Android's stub.

## Layout rules
- `DESIGN.md` is the design language (colour tokens, type scale, focus, voice) and lists what profiles can change.
  Follow it for any UI change.
- Everything is drawn on a 1920x1080 canvas: `ui/Theme.kt` `DesignCanvas` sets density so 1.dp is one design
  pixel. Colours are tokens in `C` (no raw hex in screens); text goes through `Txt` with a style from `T`; corners,
  gaps and tile sizes come from `L`. All three follow the active profile's look live (snapshot state in `Look`), so
  read them during composition and never cache them in `remember` or top-level vals. Nothing under 24 px.
- Every interactive element is `ui/Components.kt` `Focusable` (D-pad focus: controls turn linen or get a frame,
  pictures get a frame; OK = click, hold OK = long click, `onKey` for left/right or up/down adjustments). Don't use
  `clickable`. Content picks colours with `ink(focused)` / `inkMuted(focused)` / `inkIcon(focused)`, or
  `lit(focused)` when it needs to know the control is filled: with a frame focus style, a focused control keeps
  its own colours. The accent (`C.Lamp`, amber by default) means on/selected.
- Screens sit on the profile's background (`ui/Background.kt`, drawn by `HavenRoot`): don't paint opaque
  full-screen backgrounds or hearth-coloured boxes behind content, and fade lists with `Modifier.fadeBottom`.
- A composable that shows Home Assistant data must read `Haven.ha.entities` (collectAsState) itself; otherwise
  Compose skips it when only the entities change and toggles never show.
- Dialogs use `HavenDialog` (a real window, so focus can't escape to the screen behind).
- Icons are Lucide paths in `ui/Icons.kt`, generated from `lucide-static`; add new ones the same way.
- Screenshots: CI's emulator tour (`.github/smoke.sh`, demo household from `demo/Demo.kt`) pushes every screen
  to the `screens-api34` / `screens-api30` branches; check them after UI changes. The tour sets any option with
  `opt "id=value;id=value"` (a DUMP-protected demo receiver, so only adb can do it).

## Data and integrations
- Settings are per profile (`data/Settings.kt` `SettingsStore`): one JSON blob each, `Haven.settings.state` /
  `value` / `update` are the profile on the TV. Work for a particular profile (its alarm, the phone page editing
  someone else) uses `settingsOf(id)` / `updateProfile(id)`. Side effects of a change (rescheduling alarms, score
  alerts, Home Assistant) belong in `Haven.init`'s `settings.onChange`, which sees every profile's changes.
- Layouts (`data/Layouts.kt`) are recipes over parts kept in `LookConfig` (nav, hero, rows, sections, grid,
  surface, Spotlight's photo and clock). `Layouts.apply` sets a recipe's parts (and its look and background when
  `layoutLook` is on); never special-case a layout by name in the UI: render the parts. The home screen is
  `HomeScreen.kt` (pages, focus, data loads) with `HomeRows.kt` (one composable per `HomeRow`; focus keys are
  "Row:index"), `Heroes.kt`, `HomeFrames.kt` (side menu, tabs) and `HomeDialogs.kt`. Rows that need the internet
  declare it in `needsOf` so the page loads and refreshes it. A page starts on its hero's main button (`heroStart`)
  or its first row; Spotlight pages scroll by their own rule (`spotlightScroll`, the pure `ui/PageScroll.kt`).
- Glass is `Focusable(glass = Look.glass)`: it draws the blurred backdrop at its own place (`ui/Glass.kt`); the
  background layer and Spotlight publish backdrops. The grown-up PIN is checked at the screen level in
  `HavenRoot` (Settings, Profiles) and with `HomeCtx.guard` for app menus.
- A new choice for people goes in the options registry (`data/Options.kt`): a field in the data class with
  `toJson`/`fromJson`, then one `Opt` entry. The TV's Settings and the phone page (`/api/options`) both pick it up;
  `Preview.Look` / `Preview.Background` make it preview live. Only things that aren't a pick from a list (Home
  Assistant, teams, uploads) need their own UI in `SettingsScreen.kt`, `setup/SetupServer.kt` and `assets/setup.html`.
- Home Assistant: REST with a long-lived token (`ha/HomeAssistant.kt`). Areas come from `POST /api/template`
  (admin token needed); without areas Haven shows every entity. The room's entities = area entities minus
  `hiddenEntities`.
- Art: `art/ArtRepository.kt` (AIC public-domain search API + IIIF images, uploaded photos in `filesDir/photos`,
  generated fallback). `ArtDreamService` reuses `ArtScreen` as the system screensaver.
- Bedroom: `bedroom/Bedroom.kt` schedules everything with AlarmManager (every profile's alarm clock, each with
  its profile id in the intent; sunrise via HA `transition`; sleep timer). `BootReceiver` restores alarms after
  reboot/update. Alarm sounds are synthesized in `bedroom/AlarmTone.kt` (no sound files).
- Backgrounds: `bg/Backgrounds.kt` (uploaded photos in `filesDir/backgrounds`, Bing's photo of the day, AIC
  paintings, TMDB with the user's own key, Home Assistant cover art). App pictures are uploaded from the phone into
  `filesDir/appimages`. Spotlight's depth clock: `bg/DepthMask.kt` (pure, unit tested; test it on real photos with
  kotlinc before changing thresholds).
- Movies and TV: `media/Media.kt` (TVmaze, keyless; TMDB with the profile's key, cached per list) and the pure
  parsers in `media/MediaParse.kt`. The demo household (`demo/Demo.kt`) supplies invented titles with painted
  posters, so CI screenshots need no key and no network for them.
- Names in the concept material (Matt, Sam, Lily, shows, brands) are placeholders; the app uses real device data.
