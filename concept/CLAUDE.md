# CLAUDE.md — Haven TV project

Context for Claude Code. Read README.md for setup and the render commands.

## What this is
Concept + marketing material for **Haven** (working name), a per-room Android TV / Google TV launcher. One base
launcher design; each room adds features (art mode, lights, cameras, speakers, doorbell, alarm, sleep timer,
kid mode, game/sports modes, phone remote/cast, universal search + family watchlist, scenes & routines, voice).
Nothing here is a real Android app yet; it is mockups, a web ad and three films.

Family used in all material (placeholders): Matt and Sam (parents), Lily (kid, age 7). Date in the story:
Wednesday, October 21. Keep invented names; never use real brands, shows or services.

## Layout
- `mockups/*.html` — static 1920x1080 screens (converted from a design canvas; accent color `#7DD3FC`).
- `showcase-ad/index.html` — self-contained animated ad (JS timeline, container-query responsive).
- `films/2d/` — Pillow renderer.
  - `render.py` = the 40 s showcase **and** the shared drawing library (primitives `rrect`, `circle`, `text`,
    `words`, `glyph`, `ui_home`, ...). `render5.py`, `common5.py`, `scenes_*.py` import it — do not rename it.
  - 5-minute film: timeline in `tl.py` (chapter names + durations), scenes in `scenes_a/b/c.py`, frame
    assembly + background glows in `render5.py`, score in `audio5.py` (cue times are absolute seconds derived
    from `tl.py` via `A(name, local_t)`).
- `films/3d-day/` — three.js film rendered frame by frame in headless Chromium.
  - `index.html` — page + DOM overlays (clock stamp, captions, notifications, title/end cards) and their CSS.
  - `js/main.js` — exposes `window.renderAt(t)`; wires lamps, props, light slots, screen redraws, porch-camera
    feed, overlays. **Every frame is a pure function of `t`** (seconds, 0-120). Keep it that way: no
    `Math.random`, no wall-clock time, no state that depends on frame order beyond caches.
  - `js/story.js` — the whole day as data: `CLOCK` (film time -> hour), `CAM` keys `[t, pos, target, fov, via]`
    (centripetal Catmull-Rom, eased per segment; `via` keys do not stop), `LAMPS` schedules, `blindsAt`,
    `doorAt`, `screenStates(t)` (which UI each TV shows, local time, brightness, crossfades), `CAPTIONS`, `NOTIFS`.
  - `js/house.js` — builds the cutaway house (`buildHouse(scene)`): walls with section-colored cut faces,
    furniture, TVs (canvas textures via a small shader that also composites a picture-in-picture), lamps, blinds,
    props used for "implied people" (duvet fold, mug steam, backpack, package, pizza box).
  - `js/world.js` — renderer (MSAA, PCF soft shadows, Neutral tone mapping), gradient sky dome with stars, and
    the time-of-day table `K` (hour -> sky colors, sun direction/color/intensity, hemisphere light).
  - `js/ui.js` — the refined TV UI kit (`T`, `glass`, `icon`, `pill`, `arcRing`, `aurora`, ...) and every
    screen in `SCREENS` (bedAlarm, bedBrief, kitBoard, kitRecipe, art, kid, nowPlaying, livHome, sceneCard,
    movie, lilyWind). Screens draw on a 1920x1080 logical canvas; main.js picks a full or half-size canvas
    depending on how big the TV is on screen.
  - `js/icons.js` — Lucide icons as Path2D data; regenerate with `mkicons.py` after `npm install`.
  - `film.py` (full render, resumable 15 s chunks), `shoot.py` (stills/contact sheets), `audio_day.py` (score),
    `tools/uitest.py` (screens without 3D), `tools/avg.py` (per-frame timing).

## Working on the 3D film
- Iterate with stills: `python films/3d-day/shoot.py out.png 47 54 63` and look at the image before rendering video.
- Changing timing in `story.js` means updating the matching cue times in `audio_day.py` (they are absolute seconds).
- Captions sit bottom-left and must not overlap TV close-ups; close-ups keep the TV at ~57-60% of frame width
  with its bottom edge above ~800 px.
- Performance (software GL, 2 cores): ~1-3 s/frame. Costs, biggest first: sun shadows (only enabled while the
  sun is up), per-pixel lights (main.js keeps 5 point-light slots and assigns the most relevant lamps each frame),
  MSAA, hi-res screen redraws. Matte materials are Lambert on purpose; keep Standard for the few glossy parts.
- Running two renders in parallel does not help: SwiftShader already uses every core.

## Working on the 2D films
- `python render.py test 12.5` / `python render5.py test chapter:local_t` writes PNGs to `test*/`.
- Fonts come from `films/2d/fonts/` (Bricolage Grotesque = Display*, DM Sans = Body*).

## Audio
Synthesized with numpy/scipy (pads, plucks, FM bells, drums, UI cues, ambience) plus a reverb by convolution.
Levels were checked by measurement only; nobody has listened to it. There is no natural text-to-speech here;
voiceover would need a recorded track mixed in.

## If you build the real launcher
Not started. A sensible path is a native Android TV app (Kotlin + Jetpack Compose for TV) declared as a home
launcher, with smart-home control through an existing hub API (for example Home Assistant) rather than
per-vendor integrations. Check on the target device how a third-party launcher can be made the default;
Google TV devices restrict this.
