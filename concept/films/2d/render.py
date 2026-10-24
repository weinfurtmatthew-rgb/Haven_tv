import math, os, sys, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
from multiprocessing import Pool

HERE = os.path.dirname(os.path.abspath(__file__))
W, H, FPS = 1920, 1080, 30
DUR = 40.2
N = int(round(DUR * FPS))

BG = (7, 9, 12); TXT = (238, 241, 245); MUTED = (150, 160, 174); ACC = (125, 211, 252)
SURF = (23, 28, 35); SURF2 = (31, 38, 48); UIBG = (13, 16, 20); LINE = (35, 44, 55)
WARM = (245, 183, 75); CORAL = (255, 122, 112); VIOLET = (183, 156, 255); GREEN = (134, 239, 172)
INK = (43, 33, 25)

# ------------------------------------------------------------ helpers
_fc = {}
_FN = {'D8': 'Display800.ttf', 'D7': 'Display700.ttf', 'B5': 'Body500.ttf', 'B7': 'Body700.ttf'}
def F(name, size):
    k = (name, int(size))
    if k not in _fc:
        _fc[k] = ImageFont.truetype(os.path.join(HERE, 'fonts', _FN[name]), int(size))
    return _fc[k]

def clamp(x, a=0.0, b=1.0): return max(a, min(b, x))
def prog(t, a, b): return clamp((t - a) / (b - a)) if b > a else (1.0 if t >= a else 0.0)
def e_out(x): return 1 - (1 - x) ** 3
def e_in(x): return x ** 3
def e_io(x): return x * x * x * (x * (6 * x - 15) + 10)
def e_back(x, s=1.70158):
    x -= 1
    return x * x * ((s + 1) * x + s) + 1
def lerp(a, b, x): return a + (b - a) * x
def lerpc(c1, c2, x): return tuple(int(lerp(c1[i], c2[i], x)) for i in range(3))
def trap(T, a, b, c, d):
    if T <= a or T >= d: return 0.0
    if T < b: return e_io((T - a) / (b - a))
    if T <= c: return 1.0
    return 1 - e_io((T - c) / (d - c))

def layer(w=W, h=H): return Image.new('RGBA', (w, h), (0, 0, 0, 0))

def scale_alpha(img, k):
    if k >= 0.999: return img
    out = img.copy()
    out.putalpha(img.getchannel('A').point([int(i * k) for i in range(256)]))
    return out

def put(L, patch, x, y):
    lw, lh = L.size; pw, ph = patch.size
    x0 = max(0, x); y0 = max(0, y); x1 = min(lw, x + pw); y1 = min(lh, y + ph)
    if x0 >= x1 or y0 >= y1: return
    L.alpha_composite(patch.crop((x0 - x, y0 - y, x1 - x, y1 - y)), (x0, y0))

def shape(L, bbox, fn, col, a=1.0):
    if a <= 0.004: return
    lw, lh = L.size
    x0, y0, x1, y1 = bbox
    xi = max(int(math.floor(x0)) - 2, 0); yi = max(int(math.floor(y0)) - 2, 0)
    xj = min(int(math.ceil(x1)) + 2, lw); yj = min(int(math.ceil(y1)) + 2, lh)
    w = xj - xi; h = yj - yi
    if w <= 0 or h <= 0: return
    ar = w * h
    S = 4 if ar < 120000 else 3 if ar < 500000 else 2
    m = Image.new('L', (w * S, h * S), 0)
    fn(ImageDraw.Draw(m), S, xi, yi)
    m = m.resize((w, h), Image.BOX)
    if a < 1: m = m.point([int(i * a + 0.5) for i in range(256)])
    patch = Image.new('RGBA', (w, h), tuple(int(c) for c in col) + (0,))
    patch.putalpha(m)
    L.alpha_composite(patch, (xi, yi))

def rrect(L, x0, y0, x1, y1, r, col, a=1.0, corners=None):
    if x1 - x0 < 0.5 or y1 - y0 < 0.5: return
    r = max(0, min(r, (x1 - x0) / 2, (y1 - y0) / 2))
    def fn(d, S, ox, oy):
        kw = {} if corners is None else {'corners': corners}
        d.rounded_rectangle(((x0 - ox) * S, (y0 - oy) * S, (x1 - ox) * S - 1, (y1 - oy) * S - 1), radius=r * S, fill=255, **kw)
    shape(L, (x0, y0, x1, y1), fn, col, a)

def rrect_o(L, x0, y0, x1, y1, r, w, col, a=1.0):
    if x1 - x0 < 1 or y1 - y0 < 1: return
    r = max(0, min(r, (x1 - x0) / 2, (y1 - y0) / 2))
    def fn(d, S, ox, oy):
        d.rounded_rectangle(((x0 - ox) * S, (y0 - oy) * S, (x1 - ox) * S - 1, (y1 - oy) * S - 1), radius=r * S, outline=255, width=max(1, int(w * S)))
    shape(L, (x0, y0, x1, y1), fn, col, a)

def circle(L, cx, cy, r, col, a=1.0):
    if r <= 0.3: return
    def fn(d, S, ox, oy):
        d.ellipse(((cx - r - ox) * S, (cy - r - oy) * S, (cx + r - ox) * S, (cy + r - oy) * S), fill=255)
    shape(L, (cx - r, cy - r, cx + r, cy + r), fn, col, a)

def ring(L, cx, cy, r, w, col, a=1.0):
    if r <= 0.5: return
    def fn(d, S, ox, oy):
        d.ellipse(((cx - r - ox) * S, (cy - r - oy) * S, (cx + r - ox) * S, (cy + r - oy) * S), outline=255, width=max(1, int(w * S)))
    shape(L, (cx - r - w, cy - r - w, cx + r + w, cy + r + w), fn, col, a)

def line(L, pts, w, col, a=1.0):
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    bb = (min(xs) - w, min(ys) - w, max(xs) + w, max(ys) + w)
    def fn(d, S, ox, oy):
        q = [((x - ox) * S, (y - oy) * S) for x, y in pts]
        d.line(q, fill=255, width=max(1, int(w * S)), joint='curve')
        r = w * S / 2
        for x, y in (q[0], q[-1]):
            d.ellipse((x - r, y - r, x + r, y + r), fill=255)
    shape(L, bb, fn, col, a)

def poly(L, pts, col, a=1.0):
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    def fn(d, S, ox, oy):
        d.polygon([((x - ox) * S, (y - oy) * S) for x, y in pts], fill=255)
    shape(L, (min(xs), min(ys), max(xs), max(ys)), fn, col, a)

def arc_pts(cx, cy, r, a0, a1, n=18):
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]

# text ---------------------------------------------------------------
def tw(s, f, sp=0):
    if sp == 0: return f.getlength(s)
    return sum(f.getlength(c) for c in s) + sp * (len(s) - 1)

def text(L, x, y, s, f, col, a=1.0, al='l', sp=0, blur=0, ay='c'):
    if a <= 0.004 or not s: return
    w = tw(s, f, sp); asc, desc = f.getmetrics(); h = asc + desc
    pad = int(blur * 3) + 4
    mw = int(math.ceil(w)) + 2 * pad + 2; mh = h + 2 * pad
    ox = x if al == 'l' else (x - w / 2 if al == 'm' else x - w)
    oy = y - h / 2 if ay == 'c' else (y if ay == 't' else y - asc)
    ix = int(math.floor(ox)); fx = ox - ix
    iy = int(math.floor(oy)); fy = oy - iy
    m = Image.new('L', (mw, mh), 0); d = ImageDraw.Draw(m)
    if sp == 0:
        d.text((pad + fx, pad + fy), s, font=f, fill=255)
    else:
        cx = pad + fx
        for c in s:
            d.text((cx, pad + fy), c, font=f, fill=255); cx += f.getlength(c) + sp
    if blur > 0: m = m.filter(ImageFilter.GaussianBlur(blur))
    if a < 1: m = m.point([int(i * a + 0.5) for i in range(256)])
    patch = Image.new('RGBA', (mw, mh), tuple(col) + (0,)); patch.putalpha(m)
    put(L, patch, ix - pad, iy - pad)

def words(L, x, cy, s, f, t0, t, col, stagger=0.09, dur=0.8, rise=46, blur=16, al='m'):
    ws = s.split(' '); sp = f.getlength(' ')
    widths = [f.getlength(w) for w in ws]; total = sum(widths) + sp * (len(ws) - 1)
    cx = x - total / 2 if al == 'm' else x
    for i, w in enumerate(ws):
        p = e_out(prog(t, t0 + i * stagger, t0 + i * stagger + dur))
        text(L, cx, cy + (1 - p) * rise, w, f, col, a=p, blur=(1 - p) * blur)
        cx += widths[i] + sp

def letters(L, cx, cy, s, f, t0, t, col, stagger=0.07, rise=150, sp=-4):
    widths = [f.getlength(c) for c in s]; total = sum(widths) + sp * (len(s) - 1); x = cx - total / 2
    for i, c in enumerate(s):
        p = prog(t, t0 + i * stagger, t0 + i * stagger + 0.85)
        text(L, x, cy + (1 - e_back(p, 1.3)) * rise, c, f, col, a=clamp(p * 3), blur=(1 - clamp(p * 2)) * 12)
        x += widths[i] + sp

# clipped rounded masks ---------------------------------------------------
_mc = {}
def rr_mask(w, h, r):
    k = (w, h, r)
    if k not in _mc:
        S = 4 if w * h < 400000 else 2
        m = Image.new('L', (w * S, h * S), 0)
        ImageDraw.Draw(m).rounded_rectangle((0, 0, w * S - 1, h * S - 1), radius=r * S, fill=255)
        _mc[k] = m.resize((w, h), Image.BOX)
    return _mc[k]
def clip_rr(img, r):
    img.putalpha(ImageChops.multiply(img.getchannel('A'), rr_mask(img.width, img.height, r)))
    return img

# ------------------------------------------------------------ icons
def glyph(L, kind, cx, cy, s, col, a=1.0):
    w = max(2.0, s * 0.12)
    if kind == 'play':
        rrect_o(L, cx - s, cy - s * .8, cx + s, cy + s * .8, s * .3, w, col, a)
        poly(L, [(cx - s * .25, cy - s * .4), (cx + s * .45, cy), (cx - s * .25, cy + s * .4)], col, a)
    elif kind == 'music':
        ring(L, cx - s * .45, cy + s * .5, s * .28, w, col, a); ring(L, cx + s * .45, cy + s * .3, s * .28, w, col, a)
        line(L, [(cx - s * .17, cy + s * .5), (cx - s * .17, cy - s * .6), (cx + s * .73, cy - s * .8), (cx + s * .73, cy + s * .3)], w, col, a)
    elif kind == 'photos':
        rrect_o(L, cx - s, cy - s * .8, cx + s, cy + s * .8, s * .2, w, col, a); circle(L, cx - s * .4, cy - s * .25, s * .15, col, a)
        line(L, [(cx - s, cy + s * .5), (cx - s * .3, cy), (cx + s * .2, cy + s * .4), (cx + s * .5, cy + s * .1), (cx + s, cy + s * .6)], w, col, a)
    elif kind == 'games':
        rrect_o(L, cx - s, cy - s * .5, cx + s, cy + s * .5, s * .5, w, col, a)
        line(L, [(cx - s * .6, cy), (cx - s * .2, cy)], w, col, a); line(L, [(cx - s * .4, cy - s * .2), (cx - s * .4, cy + s * .2)], w, col, a)
        circle(L, cx + s * .3, cy - s * .1, s * .1, col, a); circle(L, cx + s * .55, cy + s * .12, s * .1, col, a)
    elif kind == 'files':
        rrect_o(L, cx - s, cy - s * .55, cx + s, cy + s * .7, s * .2, w, col, a)
        line(L, [(cx - s, cy - s * .2), (cx + s, cy - s * .2)], w, col, a)
    elif kind == 'browser':
        ring(L, cx, cy, s * .85, w, col, a); line(L, [(cx - s * .85, cy), (cx + s * .85, cy)], w, col, a)
        line(L, [(cx, cy - s * .85), (cx, cy + s * .85)], w, col, a)
    elif kind == 'cast':
        line(L, arc_pts(cx - s * .8, cy + s * .8, s * .6, -90, 0), w, col, a); line(L, arc_pts(cx - s * .8, cy + s * .8, s * 1.2, -90, 0), w, col, a)
        circle(L, cx - s * .75, cy + s * .75, s * .1, col, a)
        line(L, [(cx - s * .8, cy - s * .6), (cx + s * .8, cy - s * .6), (cx + s * .8, cy + s * .6), (cx + s * .2, cy + s * .6)], w, col, a)
    elif kind == 'art':
        rrect_o(L, cx - s, cy - s, cx + s, cy + s, s * .1, w, col, a); rrect_o(L, cx - s * .6, cy - s * .6, cx + s * .6, cy + s * .6, 0, w * .8, col, a)
        line(L, [(cx - s * .6, cy + s * .3), (cx - s * .15, cy - s * .1), (cx + s * .1, cy + s * .15), (cx + s * .35, cy - s * .05), (cx + s * .6, cy + s * .3)], w * .8, col, a)
    elif kind == 'bulb':
        ring(L, cx, cy - s * .2, s * .62, w, col, a)
        line(L, [(cx - s * .3, cy + s * .7), (cx + s * .3, cy + s * .7)], w, col, a); line(L, [(cx - s * .2, cy + s * .98), (cx + s * .2, cy + s * .98)], w, col, a)
    elif kind == 'cam':
        rrect_o(L, cx - s, cy - s * .55, cx + s * .3, cy + s * .55, s * .2, w, col, a)
        line(L, [(cx + s * .3, cy - s * .15), (cx + s, cy - s * .5), (cx + s, cy + s * .5), (cx + s * .3, cy + s * .15)], w, col, a)
    elif kind == 'speaker':
        rrect_o(L, cx - s * .55, cy - s, cx + s * .55, cy + s, s * .2, w, col, a); ring(L, cx, cy + s * .3, s * .3, w, col, a); circle(L, cx, cy - s * .5, s * .1, col, a)
    elif kind == 'alarm':
        ring(L, cx, cy + s * .1, s * .75, w, col, a)
        line(L, [(cx, cy - s * .35), (cx, cy + s * .1), (cx + s * .3, cy + s * .35)], w, col, a)
        line(L, [(cx - s * .85, cy - s * .6), (cx - s * .45, cy - s * .95)], w, col, a); line(L, [(cx + s * .85, cy - s * .6), (cx + s * .45, cy - s * .95)], w, col, a)
    elif kind == 'hdmi':
        rrect_o(L, cx - s, cy - s * .5, cx + s, cy + s * .5, s * .2, w, col, a)
        for k in range(4): line(L, [(cx - s * .55 + k * s * .37, cy - s * .2), (cx - s * .55 + k * s * .37, cy + s * .2)], w, col, a)

# ------------------------------------------------------------ background
GW, GH = 240, 135
_yy, _xx = np.mgrid[0:GH, 0:GW]
def glow_img(items):
    acc = np.zeros((GH, GW, 3), np.float32)
    for cx, cy, r, col, s in items:
        if s <= 0.002: continue
        d = np.hypot((_xx / GW - cx) * (16 / 9), (_yy / GH - cy))
        f = np.clip(1 - d / r, 0, 1) ** 2 * s
        acc += f[..., None] * np.array(col, np.float32)
    return Image.fromarray(np.clip(acc, 0, 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)

_noise = None
def bg_image(T):
    global _noise
    if _noise is None:
        _noise = Image.fromarray(np.random.default_rng(3).integers(0, 3, (H, W, 3), dtype=np.uint8))
    cool = 0.35 + 0.8 * trap(T, 2.6, 4.0, 6.0, 7.5) + 0.7 * trap(T, 35.6, 37.0, 40.0, 40.4)
    violet = trap(T, 22.8, 24.8, 28.6, 30.0) * 0.9
    warm = trap(T, 28.6, 31.8, 33.0, 34.6)
    g = glow_img([(0.5, 0.52, 0.75, (30, 80, 115), cool), (0.75, 0.65, 0.8, (70, 48, 130), violet), (0.5, 1.05, 1.0, (255, 140, 50), warm)])
    return ImageChops.add(Image.new('RGB', (W, H), BG), g)

# ------------------------------------------------------------ the TV screen UI (1280x720)
SW, SH = 1280, 720
def fx(t, d, dur=0.7):
    p = e_out(prog(t, d, d + dur))
    return p, (1 - p) * 26

def ui_home(t, ban=None):
    L = Image.new('RGBA', (SW, SH), UIBG + (255,))
    p, dy = fx(t, 0.0)
    f = F('D8', 62); x = 48
    text(L, x, 92 + dy, '8:15', f, TXT, p, ay='b'); x += f.getlength('8:15') + 14
    text(L, x, 92 + dy, 'PM', F('B7', 24), MUTED, p, ay='b'); x += F('B7', 24).getlength('PM') + 26
    text(L, x, 90 + dy, 'Wednesday, October 21', F('B5', 24), MUTED, p, ay='b')
    p2, dy2 = fx(t, 0.1)
    xr = SW - 48
    circle(L, xr - 22, 64 + dy2, 22, (43, 76, 126), p2); text(L, xr - 22, 64 + dy2, 'M', F('B7', 24), TXT, p2, al='m')
    xr -= 58
    w = F('B7', 22).getlength('64° Clear') + 56
    rrect(L, xr - w, 42 + dy2, xr, 86 + dy2, 22, SURF, p2); text(L, xr - w + 28, 64 + dy2, '64° Clear', F('B7', 22), TXT, p2)
    xr -= w + 14
    w = F('B7', 22).getlength('Living Room') + 64
    rrect(L, xr - w, 42 + dy2, xr, 86 + dy2, 22, SURF2, p2); circle(L, xr - w + 26, 64 + dy2, 6, ACC, p2)
    text(L, xr - w + 42, 64 + dy2, 'Living Room', F('B7', 22), TXT, p2)
    p3, dy3 = fx(t, 0.25)
    text(L, 48, 150 + dy3, 'Continue watching', F('B7', 26), TXT, p3)
    cards = [(48, 346, (43, 76, 126), 'Harbor Lights', 'Season 2 · Episode 4', .62),
             (410, 240, (91, 63, 140), 'Nightfall Kitchen', 'Episode 9', .30),
             (666, 240, (47, 107, 87), 'Trail Notes', 'Episode 2', .78),
             (922, 240, (138, 90, 43), 'The Long Orbit', 'Movie', .45),
             (1178, 240, (107, 47, 60), 'Paper Cities', 'Episode 6', .15)]
    for i, (x, w, col, title, sub, pr) in enumerate(cards):
        h = int(w * 9 / 16); y = 186
        p, _ = fx(t, 0.35 + 0.09 * i, 0.8); dx = (1 - p) * 70
        C = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        rrect(C, 0, 0, w, h, 16, col)
        circle(C, w * .88, h * .15, w * .3, (255, 255, 255), .14)
        rrect(C, w * .07, h * .55, w * .07 + h * .3, h * .85, 10, (255, 255, 255), .10)
        rrect(C, 0, h - 6, w, h, 0, (255, 255, 255), .22); rrect(C, 0, h - 6, w * pr, h, 0, ACC)
        clip_rr(C, 16)
        put(L, scale_alpha(C, p), int(x + dx), y)
        text(L, x + dx, y + h + 22, title, F('B7', 24 if i == 0 else 20), TXT, p)
        text(L, x + dx, y + h + 46 if i == 0 else y + h + 44, sub, F('B5', 18 if i == 0 else 16), MUTED, p)
    p4, dy4 = fx(t, 0.7)
    text(L, 48, 470 + dy4, 'Apps', F('B7', 26), TXT, p4)
    kinds = [('play', 'Streams'), ('music', 'Music'), ('photos', 'Photos'), ('games', 'Games'), ('files', 'Files'), ('browser', 'Browser'), ('cast', 'Cast')]
    for i, (k, lab) in enumerate(kinds):
        p, dy = fx(t, 0.8 + 0.06 * i)
        x = 48 + i * 162
        rrect(L, x, 498 + dy, x + 146, 582 + dy, 14, SURF, p)
        glyph(L, k, x + 73, 530 + dy, 15, TXT, p)
        text(L, x + 73, 565 + dy, lab, F('B7', 17), TXT, p, al='m')
    if ban:
        bx, ba, st = ban
        if ba > 0.01:
            rrect(L, 48 + bx, 606, SW - 48 + bx, 678, 16, (58, 42, 16), ba)
            text(L, 76 + bx, 642, 'SPONSORED', F('B7', 22), WARM, ba, sp=3)
            rrect(L, SW - 48 + bx - 330, 628, SW - 48 + bx - 60, 656, 14, WARM, ba * .35)
            if st > 0: line(L, [(48 + bx, 642), (48 + bx + (SW - 96) * st, 642)], 5, CORAL, ba)
    return L

_HOME = None
def home_static():
    global _HOME
    if _HOME is None: _HOME = ui_home(99)
    return _HOME.copy()

def voice_bar(L, t, yoff, a, txt, active):
    if a <= 0.01: return
    cx = SW / 2; w = 720; h = 76; x0 = cx - w / 2; y0 = SH - 48 - h + yoff
    rrect(L, x0, y0, x0 + w, y0 + h, h / 2, (28, 34, 43), a); rrect_o(L, x0, y0, x0 + w, y0 + h, h / 2, 2, (58, 70, 86), a)
    for i, c in enumerate([ACC, VIOLET, CORAL, WARM, GREEN]):
        amp = (0.35 + 0.65 * abs(math.sin(t * 9 + i * 1.4))) * (1 if active else 0.25)
        hh = 10 + 30 * amp; x = x0 + 46 + i * 18
        rrect(L, x - 4, y0 + h / 2 - hh / 2, x + 4, y0 + h / 2 + hh / 2, 4, c, a)
    if txt: text(L, x0 + 140, y0 + h / 2, txt, F('B5', 30), TXT, a)
    else: text(L, x0 + 140, y0 + h / 2, 'Ask Haven…', F('B5', 30), MUTED, a)

def typed(s, t, t0, t1): return s[:int(len(s) * prog(t, t0, t1) + 0.0001)]

def ui_voice(t):
    L = home_static()
    dim = e_io(prog(t, 3.1, 4.5)) * 0.6
    if dim > 0.01: L.alpha_composite(Image.new('RGBA', (SW, SH), (0, 0, 0, int(255 * dim))))
    # bar 1
    a1 = e_out(prog(t, 0.4, 0.9)) * (1 - e_in(prog(t, 3.2, 3.6)))
    y1 = (1 - e_out(prog(t, 0.4, 0.9))) * 60 + e_in(prog(t, 3.2, 3.6)) * 60
    voice_bar(L, t, y1, a1, typed('Dim the lights for the movie.', t, 0.9, 2.7), t < 2.9)
    # lights card
    pc = e_back(prog(t, 3.3, 4.0)); ca = clamp(prog(t, 3.3, 3.7)) * (1 - e_in(prog(t, 5.1, 5.5)))
    if ca > 0.01:
        cx0 = SW / 2 - 310; cy0 = 150 + (1 - pc) * 90 + e_in(prog(t, 5.1, 5.5)) * 40
        rrect(L, cx0, cy0, cx0 + 620, cy0 + 300, 24, (23, 28, 35), ca); rrect_o(L, cx0, cy0, cx0 + 620, cy0 + 300, 24, 2, (58, 70, 86), ca)
        text(L, cx0 + 36, cy0 + 48, 'Movie scene', F('D8', 34), TXT, ca); text(L, cx0 + 584, cy0 + 50, '3 lights', F('B5', 20), MUTED, ca, al='r')
        dp = e_io(prog(t, 3.8, 5.0))
        for i, (nm, tg) in enumerate([('Floor lamp', 20), ('Ceiling', 10), ('Backlight', 60)]):
            ry = cy0 + 108 + i * 62
            text(L, cx0 + 36, ry, nm, F('B7', 22), TXT, ca)
            tx0 = cx0 + 200; tx1 = cx0 + 520
            rrect(L, tx0, ry - 5, tx1, ry + 5, 5, (58, 69, 83), ca)
            v = lerp(100, tg, dp)
            rrect(L, tx0, ry - 5, tx0 + (tx1 - tx0) * v / 100, ry + 5, 5, ACC, ca)
            text(L, cx0 + 584, ry, '%d%%' % round(v), F('B7', 20), MUTED, ca, al='r')
    # bar 2
    a2 = e_out(prog(t, 5.6, 6.1)) * (1 - e_in(prog(t, 7.5, 7.9)))
    y2 = (1 - e_out(prog(t, 5.6, 6.1))) * 60 + e_in(prog(t, 7.5, 7.9)) * 60
    voice_bar(L, t, y2, a2, typed('Show me the front door.', t, 6.1, 7.3), t < 7.4)
    # pip camera
    pp = e_back(prog(t, 7.6, 8.3), 1.2)
    if prog(t, 7.6, 7.8) > 0:
        pw, ph = 440, 248
        pipx = SW - 48 - pw + (1 - pp) * 520; pipy = 48
        C = Image.new('RGBA', (pw, ph), (32, 42, 54, 255))
        rrect(C, 0, ph * .66, pw, ph, 0, (44, 58, 74)); rrect(C, pw * .2, ph * .14, pw * .4, ph * .72, 8, (58, 74, 94), corners=(True, True, False, False))
        circle(C, pw * .62, ph * .3, pw * .16, (255, 255, 255), .06); circle(C, pw * .38, ph * .5, 5, (163, 173, 186))
        wp = e_out(prog(t, 8.1, 9.0)); px = lerp(pw * .92, pw * .66, wp); py = ph * .86
        circle(C, px, py - 62, 15, (140, 150, 165)); rrect(C, px - 20, py - 44, px + 20, py, 14, (140, 150, 165), corners=(True, True, False, False))
        rrect(C, 14, 14, 92, 46, 16, (10, 13, 17), .8); circle(C, 34, 30, 6, CORAL, .5 + .5 * abs(math.sin(t * 5))); text(C, 48, 30, 'Live', F('B7', 17), TXT)
        rrect(C, 14, ph - 46, 150, ph - 14, 16, (10, 13, 17), .8); text(C, 30, ph - 30, 'Front Door', F('B7', 17), TXT)
        clip_rr(C, 20)
        G = Image.new('RGBA', (pw + 12, ph + 12), (0, 0, 0, 0)); rrect_o(G, 0, 0, pw + 12, ph + 12, 24, 3, TXT, .9); put(G, C, 6, 6)
        put(L, scale_alpha(G, clamp(prog(t, 7.6, 7.9))), int(pipx - 6), int(pipy - 6))
    return L

_SHADOW = None
def get_shadow():
    global _SHADOW
    if _SHADOW is None:
        g = Image.new('RGBA', (SW + 320, SH + 320), (0, 0, 0, 0))
        rrect(g, 160, 160, 160 + SW, 160 + SH, 26, (0, 0, 0), .75)
        _SHADOW = g.filter(ImageFilter.GaussianBlur(40))
    return _SHADOW

SCX, SCY = 960, 690
def blit_screen(L, scr, scale, dy, a):
    rrect_o(scr, 2, 2, SW - 2, SH - 2, 24, 4, LINE)
    clip_rr(scr, 26)
    sh = get_shadow()
    put(L, scale_alpha(sh, a), int(SCX - sh.width / 2), int(SCY - sh.height / 2 + 36 + dy))
    im = scr if scale == 1 else scr.resize((int(SW * scale), int(SH * scale)), Image.BILINEAR)
    put(L, scale_alpha(im, a), int(SCX - im.width / 2), int(SCY - im.height / 2 + dy))

# ------------------------------------------------------------ scenes
def sc_intro(t, d):
    L = layer()
    f = F('D8', 108)
    words(L, W / 2, 470, 'Every room is different.', f, 0.3, t, lerpc(TXT, (86, 94, 106), e_io(prog(t, 2.0, 2.6))))
    words(L, W / 2, 610, 'Your TV should know that.', f, 2.0, t, TXT)
    return L

def sc_title(t, d):
    L = layer()
    s = 1 + 0.05 * e_in(prog(t, 2.3, 3.1))
    letters(L, W / 2, 470, 'Haven', F('D8', int(380 * s)), 0.0, t, TXT)
    text(L, W / 2, 700, "Your home's companion.", F('B5', 58), MUTED, e_out(prog(t, 1.2, 1.9)), al='m', blur=(1 - e_out(prog(t, 1.2, 1.9))) * 10)
    for i in range(3):
        p = e_back(prog(t, 1.9 + i * .12, 2.5 + i * .12))
        circle(L, W / 2 - 40 + i * 40, 800, 7 * p, [ACC, VIOLET, WARM][i], clamp(p))
    return L

def sc_quiet(t, d):
    L = layer()
    words(L, 160, 160, 'Quiet by design.', F('D8', 118), 0.15, t, TXT, al='l')
    bx, ba, st = 1400, 0.0, 0.0
    if t >= 1.4:
        ba = clamp(prog(t, 1.4, 1.6)); bx = (1 - e_out(prog(t, 1.4, 1.95))) * 1400
        st = e_io(prog(t, 2.5, 2.95))
        if t > 3.0:
            q = prog(t, 3.0, 3.5); bx = e_in(q) * 1500; ba = 1 - q
    scr = ui_home(t - 0.2, (bx, ba, st))
    p = e_out(prog(t, 0.2, 1.3))
    blit_screen(L, scr, 0.93 + 0.07 * p, (1 - p) * 170, clamp(prog(t, 0.2, 0.8)))
    x = 160
    for i, lab in enumerate(['No ads', 'No promoted rows']):
        pp = e_back(prog(t, 3.2 + i * .3, 3.7 + i * .3))
        if pp > 0:
            f = F('B7', 30); w = f.getlength(lab) + 100
            Cp = layer(int(w) + 20, 80)
            rrect(Cp, 6, 6, 6 + w, 70, 32, SURF2); circle(Cp, 6 + 34, 38, 15, ACC)
            line(Cp, [(6 + 27, 38), (6 + 33, 44), (6 + 43, 32)], 4, (10, 13, 17))
            text(Cp, 6 + 62, 38, lab, f, TXT)
            im = Cp.resize((max(1, int(Cp.width * (.7 + .3 * pp))), max(1, int(Cp.height * (.7 + .3 * pp)))), Image.BILINEAR)
            put(L, scale_alpha(im, clamp(pp)), int(x), 232 + int((80 - im.height) / 2))
        x += F('B7', 30).getlength(lab) + 100 + 24
    return L

def sc_voice(t, d):
    L = layer()
    words(L, 160, 160, 'Just say it.', F('D8', 118), 0.15, t, TXT, al='l')
    sub = e_out(prog(t, 0.7, 1.4))
    text(L, 160, 262, 'Lights, cameras, speakers. Ask for it.', F('B5', 36), MUTED, sub, blur=(1 - sub) * 8)
    blit_screen(L, ui_voice(t), 1, 0, 1)
    return L

_PAINT = None; _PHOTO = None; _FRAMEBASE = None
AW, AH = 936, 456
def paint_img():
    global _PAINT
    if _PAINT is None:
        P = Image.new('RGBA', (AW, AH), (31, 78, 95, 255))
        rrect(P, 0, AH * .64, AW, AH, 0, (22, 59, 73))
        circle(P, AW * (1 - .12 - .11), AH * .10 + AW * .11, AW * .11, (232, 163, 61))
        def dome(x, bot, w, h, col):
            r = min(w / 2, h); rrect(P, x, bot - h, x + w, bot, r, col, corners=(True, True, False, False))
        dome(AW * .06, AH * .78, AW * .38, AH * .40, (200, 85, 61))
        dome(AW * .28, AH * .78, AW * .44, AH * .24, (242, 227, 198))
        dome(AW * .62, AH * .78, AW * .22, AH * .50, (143, 185, 168))
        rrect(P, 0, AH * .78, AW, AH * .78 + 4, 0, (243, 239, 232))
        _PAINT = P
    return _PAINT
def photo_img():
    global _PHOTO
    if _PHOTO is None:
        P = Image.new('RGBA', (AW, AH), (169, 204, 230, 255))
        circle(P, AW * .79, AH * .12 + AW * .05, AW * .05, (251, 231, 176))
        def dome(x, bot, w, h, col):
            r = min(w / 2, h); rrect(P, x, bot - h, x + w, bot, r, col, corners=(True, True, False, False))
        dome(-AW * .08, AH * .68, AW * .56, AH * .38, (94, 140, 106))
        dome(AW * .32, AH * .68, AW * .60, AH * .28, (123, 163, 127))
        rrect(P, 0, AH * .66, AW, AH, 0, (79, 134, 168))
        rrect(P, AW * .58, AH * .62, AW * .68, AH * .68, AW * .02, (243, 239, 232), corners=(False, False, True, True))
        rrect(P, AW * .30, AH * .84, AW * .52, AH * .855, 4, (255, 255, 255), .45); rrect(P, AW * .26, AH * .92, AW * .56, AH * .935, 4, (255, 255, 255), .3)
        _PHOTO = P
    return _PHOTO
FW, FH, FM = 1100, 620, 90
def frame_base():
    global _FRAMEBASE
    if _FRAMEBASE is None:
        g = Image.new('RGBA', (FW + 2 * FM, FH + 2 * FM), (0, 0, 0, 0))
        sh = Image.new('RGBA', g.size, (0, 0, 0, 0)); rrect(sh, FM, FM + 24, FM + FW, FM + FH + 24, 6, (40, 30, 20), .45)
        g.alpha_composite(sh.filter(ImageFilter.GaussianBlur(26)))
        rrect(g, FM, FM, FM + FW, FM + FH, 4, INK); rrect(g, FM + 18, FM + 18, FM + FW - 18, FM + FH - 18, 0, (243, 239, 232))
        _FRAMEBASE = g
    return _FRAMEBASE

def sc_art(t, d):
    L = layer()
    wall = (216, 210, 198)
    L.paste(wall + (255,), (0, 0, W, H))
    words(L, 160, 150, 'A frame, not a screen.', F('D8', 96), 0.7, t, INK, al='l')
    g = frame_base().copy()
    art = paint_img().copy(); mix = e_io(prog(t, 2.4, 3.4))
    if mix > 0: art.alpha_composite(scale_alpha(photo_img(), mix))
    g.alpha_composite(art, (FM + 18 + 64, FM + 18 + 64))
    sc = 1.07 - 0.07 * e_out(prog(t, 0.2, 4.8)); p = e_out(prog(t, 0.5, 1.5))
    im = g.resize((int(g.width * sc), int(g.height * sc)), Image.BILINEAR)
    put(L, scale_alpha(im, clamp(p * 1.2)), int(W / 2 - im.width / 2), int(640 - im.height / 2 + (1 - p) * 40))
    cw = [F('B7', 26).getlength('Painting') + 64, F('B7', 26).getlength('Your photo') + 64]
    ca = clamp(prog(t, 1.4, 1.9)); sw = prog(t, 2.5, 3.1)
    wd = lerp(cw[0], cw[1], sw)
    rrect(L, W / 2 - wd / 2, 960, W / 2 + wd / 2, 1016, 28, (13, 16, 20), .9 * ca)
    text(L, W / 2, 988, 'Painting', F('B7', 26), TXT, ca * (1 - sw), al='m'); text(L, W / 2, 988, 'Your photo', F('B7', 26), TXT, ca * sw, al='m')
    # iris reveal
    r = e_io(prog(t, 0, 1.1)) * 1250
    if r < 1249:
        m = Image.new('L', (W // 4, H // 4), 0); ImageDraw.Draw(m).ellipse((W / 8 - r / 4, H / 8 - r / 4, W / 8 + r / 4, H / 8 + r / 4), fill=255)
        m = m.filter(ImageFilter.GaussianBlur(2)).resize((W, H), Image.BILINEAR)
        L.putalpha(ImageChops.multiply(L.getchannel('A'), m))
    return L

def sc_sound(t, d):
    L = layer()
    words(L, 160, 160, 'Sound follows you.', F('D8', 118), 0.15, t, TXT, al='l')
    sub = e_out(prog(t, 0.7, 1.4))
    text(L, 160, 262, "Group speakers. Play what's on screen.", F('B5', 38), MUTED, sub, blur=(1 - sub) * 8)
    rooms = [('Living Room', (760, 360, 1290, 900)), ('Kitchen', (1306, 360, 1760, 620)), ('Bedroom', (1306, 636, 1760, 900))]
    for i, (nm, (x0, y0, x1, y1)) in enumerate(rooms):
        p = e_out(prog(t, 0.3 + .15 * i, 1.0 + .15 * i)); dy = (1 - p) * 30
        rrect(L, x0, y0 + dy, x1, y1 + dy, 22, (16, 21, 28), p); rrect_o(L, x0, y0 + dy, x1, y1 + dy, 22, 3, (50, 62, 78), p)
        text(L, x0 + 28, y0 + 38 + dy, nm, F('B7', 26), MUTED, p)
    tvp = e_out(prog(t, 0.8, 1.3)); on = e_io(prog(t, 1.2, 1.6))
    rrect(L, 800, 560, 824, 700, 6, lerpc(SURF2, ACC, on), tvp); text(L, 812, 530, 'TV', F('B7', 24), TXT, tvp, al='m')
    if t > 1.3:
        for k in range(3):
            ph = ((t - 1.3) * 0.5 + k / 3.0) % 1.0
            ring(L, 824, 630, 40 + ph * 210, 4, ACC, (1 - ph) * .5)
    nodes = [((1010, 800), 'Soundbar', 1.9), ((1560, 500), 'Kitchen speaker', 2.6), ((1560, 770), 'Bedroom speaker', 3.3)]
    for (nx, ny), nm, ta in nodes:
        lp = prog(t, ta - 0.6, ta)
        if lp > 0:
            x0, y0 = 824, 630; x1, y1 = nx, ny
            ln = math.hypot(x1 - x0, y1 - y0); end = ln * e_io(lp); pos = -(t * 90) % 26
            s = pos - 26
            while s < end:
                a0 = max(s, 0); a1 = min(s + 14, end)
                if a1 > a0:
                    u0 = a0 / ln; u1 = a1 / ln
                    line(L, [(lerp(x0, x1, u0), lerp(y0, y1, u0)), (lerp(x0, x1, u1), lerp(y0, y1, u1))], 4, ACC, .8)
                s += 26
        act = e_out(prog(t, ta, ta + 0.4)); base = e_out(prog(t, 0.9, 1.4))
        circle(L, nx, ny, 34, SURF2, base); ring(L, nx, ny, 34, 3, (58, 70, 86), base)
        if act > 0:
            circle(L, nx, ny, 34 * act, ACC, act)
            line(L, [(nx - 12, ny + 1), (nx - 3, ny + 10), (nx + 13, ny - 9)], 5, (10, 13, 17), act)
            q = prog(t, ta, ta + 0.9); ring(L, nx, ny, 34 + q * 44, 3, ACC, (1 - q) * .6)
        text(L, nx, ny + 66, nm, F('B7', 22), TXT if act > .5 else MUTED, base, al='m')
    return L

def sc_wake(t, d):
    L = layer()
    if t > 1.7:
        for k in range(3):
            ph = ((t - 1.7) / 2.2 + k / 3.0) % 1.0
            ring(L, W / 2, 470, 260 + ph * 700, 5, WARM, (1 - ph) * .4)
    text(L, W / 2, 210, 'Bedroom adds an alarm.', F('D8', 72), TXT, e_out(prog(t, 0.3, 1.0)), al='m', blur=(1 - e_out(prog(t, 0.3, 1.0))) * 10)
    sw = t >= 1.7
    size = 400 * (1 + 0.12 * (1 - e_out(prog(t, 1.7, 2.3)))) if sw else 400
    f = F('D8', size); s = '6:30' if sw else '6:29'
    am = F('B7', 96); tot = f.getlength(s) + 30 + am.getlength('AM')
    ap = e_out(prog(t, 0.2, 0.9))
    x0 = W / 2 - tot / 2
    text(L, x0, 600, s, f, TXT, ap, ay='b', sp=-6)
    text(L, x0 + f.getlength(s) + 30, 600, 'AM', am, MUTED, ap, ay='b')
    p1 = e_back(prog(t, 0.9, 1.5)); p2 = e_back(prog(t, 1.1, 1.7))
    w1 = F('B7', 34).getlength('Alarm on') + 64; w2 = F('B7', 34).getlength('Weekdays') + 64; gap = 20
    xs = W / 2 - (w1 + w2 + gap) / 2
    if p1 > 0:
        rrect(L, xs, 700, xs + w1, 768, 34, WARM, clamp(p1)); text(L, xs + w1 / 2, 734, 'Alarm on', F('B7', 34), (10, 13, 17), clamp(p1), al='m')
    if p2 > 0:
        rrect(L, xs + w1 + gap, 700, xs + w1 + gap + w2, 768, 34, SURF2, clamp(p2)); text(L, xs + w1 + gap + w2 / 2, 734, 'Weekdays', F('B7', 34), TXT, clamp(p2), al='m')
    return L

def sc_grid(t, d):
    L = layer()
    words(L, W / 2, 170, 'Pick what each room gets.', F('D8', 92), 0.1, t, TXT)
    items = [('art', 'Art mode', 'Painting or your photos', None), ('bulb', 'Lights', 'Scenes and every lamp', None), ('cam', 'Cameras', 'Live, without leaving', None),
             ('speaker', 'Speakers', 'Group or go solo', None), ('alarm', 'Alarm', 'Wakes you gently', 'Bedroom'), ('hdmi', 'Inputs', 'HDMI, named by you', None)]
    for i, (k, ti, ds, tag) in enumerate(items):
        x = 240 + (i % 3) * 492; y = 330 + (i // 3) * 270; w, h = 456, 232
        p = e_back(prog(t, 0.55 + .13 * i, 1.25 + .13 * i), 1.2)
        if p <= 0: continue
        C = layer(w, h)
        rrect(C, 0, 0, w, h, 28, SURF); rrect_o(C, 0, 0, w, h, 28, 2, LINE)
        circle(C, 80, 82, 44, (30, 58, 78)); glyph(C, k, 80, 82, 22, ACC)
        text(C, 36, h - 84, ti, F('D8', 42), TXT); text(C, 36, h - 40, ds, F('B5', 24), MUTED)
        if tag:
            tw_ = F('B7', 20).getlength(tag) + 36; rrect(C, w - 36 - tw_, 44, w - 36, 84, 20, WARM); text(C, w - 36 - tw_ / 2, 64, tag, F('B7', 20), (10, 13, 17), al='m')
        sc = 0.8 + 0.2 * p
        im = C.resize((int(w * sc), int(h * sc)), Image.BILINEAR)
        put(L, scale_alpha(im, clamp(p)), int(x + w / 2 - im.width / 2), int(y + h / 2 - im.height / 2))
    return L

def sc_end(t, d):
    L = layer()
    letters(L, W / 2, 450, 'Haven', F('D8', 340), 0.1, t, TXT, stagger=0.06)
    words(L, W / 2, 690, 'Your house. Your TV.', F('B5', 64), 1.0, t, MUTED, blur=10)
    text(L, W / 2, 1010, 'CONCEPT AD  ·  WORKING NAME', F('B7', 22), (96, 106, 120), e_out(prog(t, 1.6, 2.3)), al='m', sp=4)
    return L

SC = [(0.0, 3.4, sc_intro), (3.3, 6.4, sc_title), (6.2, 10.6, sc_quiet), (10.2, 19.4, sc_voice), (19.0, 24.2, sc_art),
      (23.8, 29.2, sc_sound), (28.8, 33.4, sc_wake), (33.0, 37.2, sc_grid), (36.8, 40.2, sc_end)]

def render(i):
    T = i / FPS
    im = bg_image(T).convert('RGBA')
    for s, e, fn in SC:
        if s <= T < e:
            t = T - s; dd = e - s
            Lr = fn(t, dd)
            k = min(e_io(prog(t, 0, 0.45)), 1 - e_io(prog(t, dd - 0.5, dd)))
            if k < 0.999: Lr = scale_alpha(Lr, k)
            im.alpha_composite(Lr)
    rgb = ImageChops.add(im.convert('RGB'), _noise, 1.0, -1)
    return rgb.tobytes()

if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'test':
        out = os.path.join(HERE, 'test'); os.makedirs(out, exist_ok=True)
        import time
        for T in [float(x) for x in sys.argv[2:]]:
            t0 = time.time(); b = render(int(T * FPS))
            Image.frombytes('RGB', (W, H), b).save(os.path.join(out, 'f_%05.1f.png' % T))
            print(T, round(time.time() - t0, 2), 's')
    elif mode == 'full':
        audio = sys.argv[2]; outp = sys.argv[3]
        cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', '%dx%d' % (W, H), '-r', str(FPS), '-i', '-',
               '-i', audio, '-c:v', 'libx264', '-preset', 'fast', '-crf', '17', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', outp]
        pr = subprocess.Popen(cmd, stdin=subprocess.PIPE)
        with Pool(2) as pool:
            for n, b in enumerate(pool.imap(render, range(N), chunksize=3)):
                pr.stdin.write(b)
                if n % 60 == 0: print('frame', n, '/', N, flush=True)
        pr.stdin.close(); pr.wait(); print('done', flush=True)
