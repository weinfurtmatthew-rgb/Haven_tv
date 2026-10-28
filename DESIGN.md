# Haven design language

**The light in the window.** Haven lives on the biggest screen in the house, and most of the time nobody is
looking at it. It should behave like a good lamp: warm, quiet, easy to find in the dark, and never the loudest
thing in the room. The idea comes from *calm technology* (Weiser and Brown; Amber Case): ask for as little
attention as possible, inform without demanding focus, live in the periphery.

Code: tokens and type in `app/src/main/java/app/haven/launcher/ui/Theme.kt` (`C`, `T`, `L`, `Txt`), components in
`ui/Components.kt` (`Focusable`, `Chip`, `IconButton`, `Toggle`, `LevelBar`, `KeyHint`, `ScreenHeader`,
`SectionTitle`, `Panel`), dialogs in `ui/Dialogs.kt`, backgrounds in `ui/Background.kt`, the mark in `ui/Brand.kt`.

## Principles

1. **Furniture first.** At rest, Haven is dark, still and warm. Colour has to earn its place.
2. **Light means on.** The amber *lamp* colour is reserved for things that are on, active or chosen: a lit
   lamp, an armed alarm, the selected scene, a running timer. Never decoration.
3. **One focus.** The same focus treatment everywhere: the focused thing lifts and turns *linen*, or gets a linen
   frame if it is a picture. You can find it from the couch at a glance. Nothing else looks like focus.
4. **Read from the couch.** Nothing smaller than 24 px on the 1920×1080 canvas. A serif for moments (time,
   greetings, titles), a sans built for legibility for everything you read or act on.
5. **Real things, not effects.** No glassmorphism, decorative glows, gradient blobs, sparkle icons or fake
   artwork. When materials appear (art mode, frames), they are real ones: walnut, plaster, paper.
6. **Say less.** Sentence case, plain words, numerals, no exclamation marks. Haven talks like a calm housemate.

## Yours to change

Haven is someone's own home screen, so nearly everything below is a choice each profile can make (Settings ›
Home screen, or the phone page). This document describes the defaults; the choices are
listed with each part. What never changes, whatever is chosen:

- **Focus is always unmistakable.** A light fill, a light frame or a frame in the accent colour, plus a lift
  (unless zoom is off), and nothing else on screen looks like it.
- **The accent still means "on".** Whatever colour it is, it marks things that are on, active or chosen.
- **Every scheme is contrast-checked.** Text pairs meet WCAG AA at their size in all eight schemes and with all
  ten accents (the accent comes in a bright strength for dark schemes and a deep one for the light scheme and for
  sitting on a focus fill).
- **Pictures sit behind text darkened** (45% by default), never at full strength.
- **Sizes scale together.** Text size, tile size and spacing are factors on the design pixels below, so the
  layout keeps its proportions.
- **The words stay calm.** Fonts, colours and greetings change; the voice doesn't.

The choices live in one registry (`data/Options.kt`) that drives both the TV's Settings and the phone page, in six
groups (Profiles, Home screen, Smart home, Sports, Art & bedroom, This TV). Each group starts with the few choices
most people change; More options opens the rest in the same list, under their headings. On the TV, left and right
change a choice right on its row: the value sits on the right with arrows while the row has focus, and every step
shows and saves at once, the way a TV's picture settings do. OK steps on to the next value, or opens the whole list
when it's long (more than six choices, or choices that need a line each): there, looks preview while the list is
open, Back puts back what was there and OK keeps it. Back from the rows goes to the groups.

## Layouts

A layout is a recipe of parts, not a separate design: how you get around (one page of rows, a menu down the side,
or tabs along the top), what fills the top of the screen (the clock bar, a Spotlight photo, a scoreboard, a
featured title, now playing, the house's status, a big hello), which rows show, and a look (scheme, fonts, sizes,
corners, focus, solid or glass, background). Picking a layout sets every part, previewed live; each part stays
its own setting, so layouts mix. The tokens, type scale and focus rules above hold in every layout.

- **Spotlight's clock** sits in the photo's sky. When scenery rises into it (mountains, buildings, trees), the
  clock goes a little behind: the scenery covers about a fifth of the digits and never more than about three
  fifths of any one, so it always reads as a time. Flat horizons, photos without a sky and things floating in
  the sky (the sun, clouds) leave the clock in front. The photo is whole or gone: while focus is on it (the top
  bar, the dock of apps along its bottom) the page sits at the top; moving below it slides it away and the rows
  take the screen, as on Apple TV.
- **Where focus starts**: on the hero's main button when the hero is the point of the page (the game on now, the
  song playing), else on the first thing in the first row. Featured movies and shows keep changing, so focus
  starts below them. A new layout always starts there; coming back to the same one returns to where you were.
- **Glass** is a surface that lets the background through: a blurred copy of the picture behind it, darkened as
  the background is, under a light tint (11% white on dark schemes, 55% on the light one) and a hairline edge.
  It's for tiles, cards, chips and the side menu, over pictures; dialogs stay solid.
- **Lift** is the fourth focus look: no fill and no frame; the focused thing rises (with Big zoom), casts a soft
  shadow and catches a sheen of light. Spotlight and Music use it; it needs zoom to read across the room.
- **Kids** keeps the same rules at a bigger size: Daylight, coral, Fredoka and Nunito, extra-large tiles and text,
  extra-round corners, names always shown.

## Colour

The table is the default scheme, **Hearth**, with the default **Lamp** accent. The other schemes (Midnight,
Forest, Plum, Graphite, Black for OLED, High contrast, and the light Daylight) fill the same roles with their own
colours, and the accent can be lamp amber, coral, rose, lilac, sky blue, mint, sage green, gold, red or white.
All colours are sRGB. Contrast ratios are against `hearth` unless noted; text pairs meet WCAG AA at their size.

| Token | Hex | Use | Contrast |
|---|---|---|---|
| `Hearth` | `#14120F` | Background everywhere | — |
| `Surface` | `#28241F` | Resting tiles, rows and cards | 1.21:1 (visible edge) |
| `Raised` | `#35302A` | Chips and controls | 1.43:1 |
| `Line` | `#4A433B` | Dividers, disabled tracks | — |
| `Track` | `#7E756B` | Empty part of level bars, off switches | 3.0:1 on raised |
| `Linen` | `#F2EDE4` | Primary text; the focus fill | 16.0:1 |
| `Stone` | `#B9B0A3` | Secondary text | 8.7:1 (7.2 on surface) |
| `Dust` | `#8F877B` | Hints and notes, 24 px and up | 5.3:1 (4.4 on surface) |
| `Lamp` | `#F2B35E` | On, active, selected; the light in the mark | 10.1:1 |
| `LampSoft` | `#3A2D1C` | Surface of a selected chip | lamp on it 7.2:1 |
| `Sage` | `#A8C49A` | Fine: locked, closed | 9.8:1 (8.1 on surface) |
| `Clay` | `#E8795E` | Needs attention: unlocked, live, errors | 6.5:1 (5.4 on surface) |

On a focused (linen) surface the content switches to `Ink` (= hearth, 16:1), `InkMuted #5E564C` (6.2:1),
`Ember #8A4F0B` (the lamp, 5.6:1) and `TrackOnLinen #A39A8C`. Helpers: `ink(focused)`, `inkMuted(focused)`.

Art-mode materials: `Plaster #D9D2C5` (gallery wall), `Walnut #3B2A1E` (frames), `Mat #F4F0E8`,
`LabelInk #3A332B` (museum-label ink, 10.9:1 on mat).

Rules: dark backgrounds by default (TVs glare and burn power on light ones); Daylight and the gallery wall in art
mode are there for people who choose them. Haven itself adds no gradients except two functional ones: the
alarm's sunrise (dithered) and the fade at the bottom of a scrolling list, which fades the list itself so it
works over any background. A background can be a gradient or a picture when the profile picks one. Colour never
carries meaning alone: every state also has a word, a position (switch knob) or a shape.

## Type

| Style | Font | Size / weight | Use |
|---|---|---|---|
| `T.Display` | Fraunces 72pt Soft | 240 / 600 | Alarm clock, big numbers in art and weather |
| `T.Clock` | Fraunces 72pt Soft | 88 / 600 | Home screen time |
| `T.Headline` | Fraunces 36pt Soft | 56 / 600 | Screen titles, greetings |
| `T.Title` | Fraunces 36pt Soft | 36 / 600 | Section and dialog titles, art captions |
| `T.CardTitle` | Atkinson Hyperlegible Next | 28 / 700 | Tile, row and card titles |
| `T.Body` | Atkinson Hyperlegible Next | 26 / 500 | Everything you read |
| `T.Label` | Atkinson Hyperlegible Next | 24 / 600 | Values, chips, metadata, hints (the floor) |
| `T.Numeric` | Atkinson Hyperlegible Next | 26 / 700, `tnum` | Timers, scores, times in a column |

Each profile can pick other fonts. Headings (clocks, greetings, titles): Fraunces (default), Literata, Playfair
Display, Bebas Neue, Fredoka, Outfit, or the text font. Text: Atkinson Hyperlegible Next (default), Lexend,
Nunito, Inter, Literata, or the TV's own font. Each is nudged in size so they look alike at the same setting, and
text size (standard, large, extra large) scales all of them, so nothing gets under 24 px.

Fraunces (SIL OFL) is a soft, warm serif. Haven ships two static cuts from its variable axes, made for TV:
36pt optical size for titles (sturdy strokes that survive a TV panel at 3 m) and 72pt for display sizes, both
at SemiBold with the Soft axis at 50 and Wonk off. Fraunces has no tabular figures, so anything that ticks every
second, or lines up in a column, is set in Atkinson with `tnum`; the minute-only clocks stay in Fraunces.
Atkinson Hyperlegible Next (SIL OFL) was designed by the Braille Institute for low-vision readers: open shapes
and unambiguous letters, which is exactly what 3 m viewing needs. Text that can run long wraps to two lines
before it ellipsizes.

## Layout

- Canvas 1920×1080, 1 dp = 1 px (`DesignCanvas`). Safe area: 116 px left and right (Google TV's 58 dp margin),
  48 px at the top for the top bar, 64 px at the bottom.
- Spacing steps: 8, 16, 24, 32, 48, 64, 96.
- Rows of things (favourites, apps, room tiles) bleed off the right edge to say "there's more"; everything else
  stays inside the safe area. Lists scroll only as far as needed to keep the focused item 116 px clear of the
  edges (`rememberKeepVisible`, provided for the whole app in `HavenRoot`), not the TV default that pins focus
  a third of the way in.
- Screens don't put their content in boxes. Columns of rows sit on the hearth with a section title; a `Panel`
  is for dialogs and the one card that needs grouping (phone setup).
- Home: favourites 400×225 (all the same size), apps 256×144, room tiles 400×112. A card's name appears under
  it only while it has focus. Profiles can make favourite and app tiles small, large or extra large (0.8×, 1.2×,
  1.4×), spacing compact or airy (0.67×, 1.4×), names always or never shown, and put the rows (favourites,
  recently used, all apps, the room) in any order. The home screen scrolls when the rows don't fit.

## Shape

- Tiles, rows, cards and app banners: 12 px radius. Panels and dialogs: 20 px. Chips and buttons: fully round.
  Art: square corners, like the real thing. Profiles can choose square, slightly rounded (6), rounded (12), very
  rounded (20) or extra round (32) corners; panels follow at five thirds, and chips stay round unless corners are
  square or slight.
- **The arch** is Haven's one signature shape: the mark (a doorway with the light on), the alarm's rising sun,
  and empty states. It is not used as decoration anywhere else.

## Focus and state

- **Controls** (buttons, chips, rows, text tiles): the surface turns linen and the content turns ink, with a
  1.03–1.08 lift (smaller for wide rows, larger for small chips). `Focusable(style = FocusStyle.Control)`. With
  the profile's focus set to a frame (light or accent), controls keep their colours and get the picture frame
  instead; content uses `ink(focused)` / `lit(focused)`, which know the difference. Zoom scales the lift (none,
  subtle, standard, big).
- **Pictures** (app banners, posters, art): 1.06 lift and a 4 px linen frame drawn 4 px outside the picture.
  `FocusStyle.Picture`. No shadows, except with the Lift focus look (see Layouts), where the shadow and a bigger
  zoom take the frame's place.
- **Selected** (not focus): lamp. A selected chip is `LampSoft` with a lamp dot and lamp text; when it also has
  focus it turns linen and keeps an ember dot, so selection and focus never look alike.
- **On**: lamp on the indicator only: switch track, level fill, status word, room-tile icon.
- **Pressed:** a quick dip to 0.97. **Unavailable:** dust text and a dimmed switch, with the reason in words
  ("Needs the Home button service. Select to turn it on").
- Settings that can only be changed on the phone show a phone icon, and selecting them points at the QR code
  instead of doing nothing.

## Motion

Focus 160 ms ease-out (lift, linen fill, frame). Screens fade in over 240 ms. Ambient changes (art, briefing,
backgrounds) crossfade over 1.5 s; the alarm's sunrise grows over a minute. No springs, bounces or parallax.
Reduced motion (a profile choice) makes every one of these instant, which also helps slow boxes.

## Icons

Lucide outlines (ISC), 2 px stroke on the 24 px grid, shown at 28–40 px (`ui/Icons.kt`, generated from
`lucide-static`). Only where an icon is faster than a word (weather, lights, speakers, alarm, room tiles). Icons
are stone at rest, ink on focus, lamp only when the thing is on.

## Voice

- Sentence case everywhere; no title case, no exclamation marks, no emoji.
- Short and specific: "Lights", "3 of 5 on", "Locked", "On. Rings tomorrow at 6:30 AM, in 7 hr 12 min".
- Greet by the time of day ("Good morning" / "Good afternoon" / "Good evening"), on the alarm and the briefing
  only. A profile can drop its name from the greeting, write its own words, or have no greeting.
- Errors say what happened and what to do: "Can't reach Home Assistant. Check the address in Settings, or try
  again."
- Football (soccer) is written home team first ("ARS 2 – 1 LIV", "ARS v LIV"); North American leagues away
  first ("BUF 17 – 21 KC", "BOS at NYY").

## Brand mark

An arched doorway in linen with the light on (lamp) inside, next to the wordmark *Haven* in Fraunces 72pt Soft
SemiBold, set on the doorway's threshold line. Files: `res/drawable/banner.xml` (launcher banner, wordmark
outlined), `res/drawable/ic_launcher_foreground.xml` (mark alone, inside the adaptive-icon safe zone),
`ui/Brand.kt` (`HavenMark`, for empty states and the phone page header).
