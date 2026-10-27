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
- Unit tests (`app/src/test`) cover pure logic only (alarm times, URL normalizing); org.json is stubbed there.

## Layout rules
- `DESIGN.md` is the design language (colour tokens, type scale, focus, voice). Follow it for any UI change.
- Everything is drawn on a 1920x1080 canvas: `ui/Theme.kt` `DesignCanvas` sets density so 1.dp is one design
  pixel. Colours are tokens in `C` (no raw hex in screens); text goes through `Txt` with a style from `T`
  (Fraunces for titles and clocks, Atkinson Hyperlegible Next for the rest, nothing under 24 px).
- Every interactive element is `ui/Components.kt` `Focusable` (D-pad focus: controls turn linen, pictures get a
  linen frame; OK = click, hold OK = long click, `onKey` for left/right or up/down adjustments). Don't use
  `clickable`. Content picks colours with `ink(focused)` / `inkMuted(focused)`. Lamp (amber) means on/selected.
- A composable that shows Home Assistant data must read `Haven.ha.entities` (collectAsState) itself; otherwise
  Compose skips it when only the entities change and toggles never show.
- Dialogs use `HavenDialog` (a real window, so focus can't escape to the screen behind).
- Icons are Lucide paths in `ui/Icons.kt`, generated from `lucide-static`; add new ones the same way.
- Screenshots: CI's emulator tour (`.github/smoke.sh`, demo household from `demo/Demo.kt`) pushes every screen
  to the `screens-api34` / `screens-api30` branches; check them after UI changes.

## Data and integrations
- Settings: one JSON blob (`data/Settings.kt`), exposed as `Haven.settings.state`. Add a field in the data
  class, `toJson`, `fromJson`, and (if the phone page edits it) `setup/SetupServer.kt` + `assets/setup.html`.
- Home Assistant: REST with a long-lived token (`ha/HomeAssistant.kt`). Areas come from `POST /api/template`
  (admin token needed); without areas Haven shows every entity. The room's entities = area entities minus
  `hiddenEntities`.
- Art: `art/ArtRepository.kt` (AIC public-domain search API + IIIF images, uploaded photos in `filesDir/photos`,
  generated fallback). `ArtDreamService` reuses `ArtScreen` as the system screensaver.
- Bedroom: `bedroom/Bedroom.kt` schedules everything with AlarmManager (alarm clock, sunrise via HA
  `transition`, sleep timer). `BootReceiver` restores alarms after reboot/update.
- Names in the concept material (Matt, Sam, Lily, shows, brands) are placeholders; the app uses real device data.
