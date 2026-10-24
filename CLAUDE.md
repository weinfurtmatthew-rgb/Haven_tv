# CLAUDE.md — Haven TV launcher

Context for Claude Code. README.md covers features, the DIRECTV install steps and building.

## What this is
A real Android TV launcher (Kotlin + Jetpack Compose, no XML layouts) built from the Haven concept in `concept/`
(mockups, ad, films; see `concept/CLAUDE.md` for working on those). Main target: the DIRECTV STREAM box, which
does not let users change the default home app, so `system/HomeButtonService.kt` (an accessibility service)
catches the Home key and redirects the stock home screen to Haven. Double Home opens the stock home screen.

## Build and verify
- CI (`.github/workflows/build.yml`) is the reference build: `testDebugUnitTest` then `assembleRelease`, then a
  GitHub release `v1.0.<run_number>` with `haven.apk`. The in-app updater (`update/Updater.kt`) parses that tag,
  so keep the `v1.0.<n>` format and keep versionCode = run number.
- Local: JDK 17 + Android SDK, `./gradlew testDebugUnitTest assembleRelease`.
- Versions: AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, compileSdk 35, targetSdk 34, minSdk 26.
- Unit tests (`app/src/test`) cover pure logic only (alarm times, URL normalizing); org.json is stubbed there.

## Layout rules
- Everything is drawn on the mockups' 1920x1080 canvas: `ui/Theme.kt` `DesignCanvas` sets density so 1.dp is
  one design pixel. Use mockup pixel values directly. Colors are in `C`; text goes through `Txt` (Manrope).
- Every interactive element is `ui/Components.kt` `Focusable` (D-pad focus, scale + accent outline, OK = click,
  hold OK = long click, `onKey` for left/right or up/down adjustments). Don't use `clickable`.
- Dialogs use `HavenDialog` (a real window, so focus can't escape to the screen behind).
- Icons are Lucide paths in `ui/Icons.kt`, generated from `lucide-static`; add new ones the same way.

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
