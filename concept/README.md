# Haven TV — per-room TV launcher concept

Haven (working name) is a concept for an Android TV / Google TV launcher where every TV in the house shares one
base design but each room gets its own features: art mode, lights, cameras, speakers, an alarm in the bedroom,
kid mode, and so on. This repo holds everything made for the concept so far: screen mockups, an animated web ad,
and three marketing films with the code that renders them.

| Folder | What it is | Built with |
|---|---|---|
| `mockups/` | 7 launcher screens (base home, living room, bedroom, art mode ×2, cameras, lights & speakers) | Plain HTML/CSS |
| `showcase-ad/` | Animated 8-scene web ad with a seekable timeline | One HTML page |
| `films/2d/` | 40-second showcase and the 5-minute keynote film | Python + Pillow + numpy, ffmpeg |
| `films/3d-day/` | 2-minute 3D "day in the life" film (dollhouse cutaway, sun moves through the day) | three.js in headless Chromium, Python driver, ffmpeg |

Rendered videos are not included (they are large). Re-render them with the commands below.

## Setup

You need Python 3.10+, Node 18+, and ffmpeg on your PATH.

```bash
pip install -r requirements.txt
python -m playwright install chromium     # browser used to render the 3D film
cd films/3d-day && npm install && cd ../..
```

## Quick looks (no rendering)

- Mockups: open `mockups/index.html` in a browser (uses Google Fonts, so be online).
- Web ad: open `showcase-ad/index.html`.

## Rendering the films

**3D day-in-the-life (2:00, 1080p30)** — run from anywhere:

```bash
python films/3d-day/shoot.py preview.png 12.5            # one still at t = 12.5 s
python films/3d-day/shoot.py sheet.png 10 30 60 90       # contact sheet of several times
python films/3d-day/tools/uitest.py '[["bedBrief",2.5],["kid",5.5]]' ui.png   # TV screens drawn flat
python films/3d-day/audio_day.py                         # writes films/3d-day/day_score.wav
python films/3d-day/film.py                              # full render: 15 s chunks in chunks/, then the final MP4
```

`film.py` is resumable: finished chunks are skipped, so you can stop and restart it. With software rendering
it takes about 2 s per frame (about 2 hours for the full film on 2 CPU cores). Setting `HAVEN_GPU=1` tries
your graphics card instead, which can be much faster; check a still first because GPU output can differ slightly.

**2D films** — run inside `films/2d/`:

```bash
python audio.py && python render.py full soundtrack.wav haven_showcase.mp4      # 40 s showcase
python render5.py test premise:3.5 doorbell:4.5                                  # stills from the 5-minute film
python render5.py sheet sheet.png intro:2 voice:12 games:16                      # contact sheet
python audio5.py && python render5.py full score5.wav haven_keynote_5min.mp4     # 5-minute keynote
```

## Notes

- All music and sound effects are synthesized in code (`audio*.py`). They were checked by measurement only, so
  listen before you show them to anyone.
- Names are placeholders: "Haven", the family (Matt, Sam, Lily), shows, services and brands are invented.
- Third-party assets: three.js (MIT), Lucide icons (ISC), Inter, DM Sans and Bricolage Grotesque fonts (SIL OFL).
- The original design canvas and web ad also live as artifacts on claude.ai:
  mockups https://claude.ai/artifact/J7jhogzZCBiKBEN4eo9DjS and ad https://claude.ai/artifact/FVzUXFW2Pfno1Dked1xS5Q
