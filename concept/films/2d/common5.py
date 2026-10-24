import math, random
from render import *
from render import F, SW, SH

def heads(L, t, items, col=None, subcol=None):
    col = col or TXT
    subcol = subcol or MUTED
    for a, b, title, sub, size in items:
        if t < a or t >= b + 0.01: continue
        H1 = layer(W, 340)
        words(H1, 160, 160, title, F('D8', size), a + 0.1, t, col, al='l')
        if sub:
            sp = e_out(prog(t, a + 0.75, a + 1.45))
            text(H1, 160, 266, sub, F('B5', 36), subcol, sp, blur=(1 - sp) * 8)
        k = 1 - e_io(prog(t, b - 0.4, b))
        put(L, scale_alpha(H1, k), 0, 0)

def capc(L, t, items, y=210, size=72, col=None):
    col = col or TXT
    for a, b, title in items:
        if t < a or t >= b + 0.01: continue
        H1 = layer(W, 200)
        words(H1, W / 2, 100, title, F('D8', size), a + 0.1, t, col)
        k = 1 - e_io(prog(t, b - 0.4, b))
        put(L, scale_alpha(H1, k), 0, y - 100)

def pill(L, x, y, s, f, fill, tc, a=1.0, padx=22, h=44, al='l'):
    w = f.getlength(s) + 2 * padx
    x0 = x if al == 'l' else (x - w / 2 if al == 'm' else x - w)
    rrect(L, x0, y, x0 + w, y + h, h / 2, fill, a)
    text(L, x0 + padx, y + h / 2, s, f, tc, a)
    return w

def toggle(L, x, y, v, a=1.0, w=72, h=40):
    rrect(L, x, y, x + w, y + h, h / 2, lerpc((58, 69, 83), ACC, v), a)
    circle(L, x + h / 2 + (w - h) * v, y + h / 2, h / 2 - 4, lerpc(TXT, (10, 13, 17), v), a)

def avatar(L, cx, cy, r, col, ch, a=1.0):
    circle(L, cx, cy, r, col, a)
    text(L, cx, cy, ch, F('B7', int(r * 0.95)), TXT, a, al='m')

def check(L, cx, cy, r, a=1.0, col=ACC):
    circle(L, cx, cy, r, col, a)
    line(L, [(cx - r * .42, cy + r * .02), (cx - r * .1, cy + r * .36), (cx + r * .45, cy - r * .32)], max(2, r * .2), (10, 13, 17), a)

def blit_at(L, scr, cx, cy, scale=1.0, a=1.0, dy=0):
    rrect_o(scr, 2, 2, SW - 2, SH - 2, 24, 4, LINE)
    clip_rr(scr, 26)
    sh = get_shadow()
    if scale != 1: sh = sh.resize((int(sh.width * scale), int(sh.height * scale)), Image.BILINEAR)
    put(L, scale_alpha(sh, a), int(cx - sh.width / 2), int(cy - sh.height / 2 + 36 * scale + dy))
    im = scr if scale == 1 else scr.resize((int(SW * scale), int(SH * scale)), Image.BILINEAR)
    put(L, scale_alpha(im, a), int(cx - im.width / 2), int(cy - im.height / 2 + dy))

def new_screen(col=UIBG): return Image.new('RGBA', (SW, SH), tuple(col) + (255,))

def feed_img(w, h, kind, t, pp=0.0, r=18, pkg=0.0):
    bgc = {'door': (32, 42, 54), 'driveway': (38, 44, 52), 'backyard': (31, 48, 41), 'garage': (43, 40, 48), 'porch': (32, 42, 54)}[kind]
    gr = {'door': (44, 58, 74), 'driveway': (52, 58, 66), 'backyard': (42, 68, 55), 'garage': (56, 52, 62), 'porch': (44, 58, 74)}[kind]
    C = Image.new('RGBA', (int(w), int(h)), bgc + (255,))
    rrect(C, 0, h * .66, w, h, 0, gr)
    if kind in ('door', 'porch'):
        rrect(C, w * .2, h * .14, w * .4, h * .72, max(4, w * .02), (58, 74, 94), corners=(True, True, False, False))
        circle(C, w * .38, h * .5, max(3, w * .012), (163, 173, 186))
        circle(C, w * .62, h * .3, w * .16, (255, 255, 255), .06)
    if kind == 'door':
        if pkg > 0:
            bx = w * .58; by = h * .82; bw = w * .2; bh = h * .2
            rrect(C, bx, by - bh, bx + bw, by, 4, (176, 128, 82), pkg); rrect(C, bx + bw * .45, by - bh, bx + bw * .55, by, 0, (214, 176, 128), pkg)
        px = lerp(w * 1.02, w * .7, pp); py = h * .9; s = h / 248.0 * 1.45
        circle(C, px, py - 62 * s, 15 * s, (140, 150, 165))
        rrect(C, px - 20 * s, py - 44 * s, px + 20 * s, py, 14 * s, (140, 150, 165), corners=(True, True, False, False))
    if kind == 'porch':
        bx = w * .58; by = h * .82; bw = w * .2; bh = h * .2
        rrect(C, bx, by - bh, bx + bw, by, 4, (176, 128, 82)); rrect(C, bx + bw * .45, by - bh, bx + bw * .55, by, 0, (214, 176, 128))
    if kind == 'driveway':
        rrect(C, 0, h * .5, w, h * .68, 0, (58, 66, 76))
        cx = ((t * 0.12) % 1.5 - 0.25) * w
        rrect(C, cx, h * .52, cx + w * .28, h * .64, h * .04, (200, 85, 61)); rrect(C, cx + w * .06, h * .46, cx + w * .2, h * .55, h * .03, (150, 60, 45))
        circle(C, cx + w * .06, h * .65, h * .035, (20, 24, 30)); circle(C, cx + w * .22, h * .65, h * .035, (20, 24, 30))
    if kind == 'backyard':
        for k, (tx, ts) in enumerate(((.2, .14), (.5, .18), (.8, .12))):
            rrect(C, w * tx - 4, h * .45, w * tx + 4, h * .7, 2, (80, 60, 40)); sway = math.sin(t * 1.4 + k) * h * .01
            circle(C, w * tx + sway, h * .38, h * ts, (58, 95, 74))
    if kind == 'garage':
        for k in range(4): rrect(C, w * .25, h * (.2 + .13 * k), w * .75, h * (.31 + .13 * k), 3, (62, 58, 70))
    clip_rr(C, r)
    return C

def feed_badges(C, t, label, live=True):
    w, h = C.size
    s = max(0.8, w / 440.0)
    if live:
        rrect(C, 14 * s, 14 * s, 92 * s, 46 * s, 16 * s, (10, 13, 17), .8); circle(C, 34 * s, 30 * s, 6 * s, CORAL, .5 + .5 * abs(math.sin(t * 5)))
        text(C, 48 * s, 30 * s, 'Live', F('B7', int(17 * s)), TXT)
    lw = F('B7', int(17 * s)).getlength(label) + 36 * s
    rrect(C, 14 * s, h - 46 * s, 14 * s + lw, h - 14 * s, 16 * s, (10, 13, 17), .8); text(C, 32 * s, h - 30 * s, label, F('B7', int(17 * s)), TXT)

# ---- art helpers ----
AW, AH = 936, 456
FW, FH, FM = 1100, 620, 90
_ARTS = None
_FSH = None
def dome(P, x, bot, w, h, col):
    r = min(w / 2, h); rrect(P, x, bot - h, x + w, bot, r, col, corners=(True, True, False, False))

def gen_art(seed):
    pals = [((24, 60, 70), [(232, 163, 61), (200, 85, 61), (242, 227, 198), (143, 185, 168)]),
            ((46, 36, 66), [(255, 170, 110), (240, 110, 110), (150, 120, 200), (250, 230, 200)]),
            ((232, 224, 208), [(40, 70, 110), (210, 90, 70), (240, 180, 70), (90, 120, 90)]),
            ((20, 30, 40), [(80, 160, 170), (240, 230, 210), (220, 120, 80), (120, 90, 150)])]
    r = random.Random(seed); bg, cols = pals[seed % len(pals)]
    P = Image.new('RGBA', (AW, AH), bg + (255,))
    rrect(P, 0, AH * .62, AW, AH, 0, tuple(int(c * 0.82) for c in bg))
    circle(P, AW * r.uniform(.2, .8), AH * r.uniform(.22, .4), AW * r.uniform(.07, .12), cols[0])
    for k in range(3):
        dome(P, AW * r.uniform(.05, .6), AH * .78, AW * r.uniform(.2, .4), AH * r.uniform(.2, .5), cols[1 + k % 3])
    rrect(P, 0, AH * .78, AW, AH * .78 + 4, 0, (243, 239, 232))
    return P

def arts():
    global _ARTS
    if _ARTS is None:
        _ARTS = [paint_img(), photo_img()] + [gen_art(s) for s in (1, 2, 3, 4)]
    return _ARTS

def frame_shadow():
    global _FSH
    if _FSH is None:
        g = layer(FW + 2 * FM, FH + 2 * FM)
        rrect(g, FM, FM + 24, FM + FW, FM + FH + 24, 6, (40, 30, 20), .45)
        _FSH = g.filter(ImageFilter.GaussianBlur(26))
    return _FSH

def frame_dyn(col, matte=(243, 239, 232)):
    g = layer(FW + 2 * FM, FH + 2 * FM)
    g.alpha_composite(frame_shadow())
    rrect(g, FM, FM, FM + FW, FM + FH, 4, col); rrect(g, FM + 18, FM + 18, FM + FW - 18, FM + FH - 18, 0, matte)
    return g

def sched_at(sched, t, dur=0.8):
    cur = 0
    for i, (tt, v) in enumerate(sched):
        if t >= tt: cur = i
    prev = max(0, cur - 1)
    tt = sched[cur][0]
    mix = e_io(prog(t, tt, tt + dur)) if cur > 0 else 1.0
    return sched[prev][1], sched[cur][1], mix, cur

# ---- more shared helpers ----
def entry(L, scr, t, t0=0.2):
    p = e_out(prog(t, t0, t0 + 1.1))
    blit_screen(L, scr, 0.93 + 0.07 * p, (1 - p) * 170, clamp(prog(t, t0, t0 + 0.6)))

def dimmed(img, k, col=(0, 0, 0)):
    if k > 0.005: img.alpha_composite(Image.new('RGBA', img.size, tuple(col) + (int(255 * clamp(k)),)))
    return img

def topbar(L, title, a=1.0, room='Living Room', sub=None):
    f = F('D8', 40)
    text(L, 48, 66, title, f, TXT, a)
    if sub: text(L, 48 + f.getlength(title) + 20, 70, sub, F('B5', 22), MUTED, a)
    if room:
        w = F('B7', 22).getlength(room) + 64; xr = SW - 48
        rrect(L, xr - w, 44, xr, 88, 22, SURF2, a); circle(L, xr - w + 26, 66, 6, ACC, a)
        text(L, xr - w + 42, 66, room, F('B7', 22), TXT, a)

def focus(L, x0, y0, x1, y1, r, a=1.0, col=TXT, w=4, pad=7):
    rrect_o(L, x0 - pad, y0 - pad, x1 + pad, y1 + pad, r + pad, w, col, a)

def popimg(L, img, cx, cy, p, smin=0.8, a=None):
    if p <= 0.001: return
    s = smin + (1 - smin) * p
    im = img if abs(s - 1) < 1e-3 else img.resize((max(1, int(img.width * s)), max(1, int(img.height * s))), Image.BILINEAR)
    put(L, scale_alpha(im, clamp(p) if a is None else a), int(cx - im.width / 2), int(cy - im.height / 2))

def moon(L, cx, cy, r, col, bg, a=1.0):
    circle(L, cx, cy, r, col, a); circle(L, cx + r * .45, cy - r * .32, r * .82, bg, a)

def slider(L, x0, x1, y, v, a=1.0, col=ACC, knob=True, h=10, track=(58, 69, 83)):
    v = clamp(v)
    rrect(L, x0, y - h / 2, x1, y + h / 2, h / 2, track, a)
    rrect(L, x0, y - h / 2, x0 + (x1 - x0) * v, y + h / 2, h / 2, col, a)
    if knob: circle(L, x0 + (x1 - x0) * v, y, h * 1.25, TXT, a)

def circ_mask(w, h, cx, cy, r):
    m = Image.new('L', (w // 2, h // 2), 0)
    if r > 0: ImageDraw.Draw(m).ellipse((cx / 2 - r / 2, cy / 2 - r / 2, cx / 2 + r / 2, cy / 2 + r / 2), fill=255)
    return m.resize((w, h), Image.BILINEAR)

def reveal(base, top, cx, cy, r):
    top = top.copy(); top.putalpha(ImageChops.multiply(top.getchannel('A'), circ_mask(top.width, top.height, cx, cy, r)))
    base.alpha_composite(top); return base

def slide(a_img, b_img, q, w=SW, h=SH, col=UIBG):
    L = Image.new('RGBA', (w, h), tuple(col) + (255,))
    q = e_io(q)
    put(L, a_img, int(-w * q), 0); put(L, b_img, int(w * (1 - q)), 0)
    return L

def xfade(a_img, b_img, q):
    out = a_img.copy(); out.alpha_composite(scale_alpha(b_img, e_io(q))); return out

def face(L, cx, cy, r, col, a=1.0, ink=(30, 23, 48)):
    circle(L, cx, cy, r, col, a)
    circle(L, cx - r * .34, cy - r * .14, r * .11, ink, a); circle(L, cx + r * .34, cy - r * .14, r * .11, ink, a)
    line(L, arc_pts(cx, cy + r * .02, r * .42, 35, 145, 10), max(2, r * .09), ink, a)

def poster(w, h, col, title=None, kind=0, r=16):
    w = int(w); h = int(h)
    C = Image.new('RGBA', (w, h), tuple(col) + (255,))
    light = (255, 255, 255)
    if kind == 0:
        circle(C, w * .82, h * .16, max(w, h) * .32, light, .13)
        rrect(C, w * .08, h * .52, w * .08 + min(w, h) * .3, h * .52 + min(w, h) * .3, 10, light, .10)
    elif kind == 1:   # orbit
        ring(C, w * .5, h * .4, min(w, h) * .32, max(2, w * .012), (255, 230, 200), .55)
        circle(C, w * .5, h * .4, min(w, h) * .15, (255, 200, 140), .9)
        circle(C, w * .5 + min(w, h) * .32, h * .4, min(w, h) * .035, light, .9)
    elif kind == 2:   # skyline
        for k in range(7):
            bw = w * .1; bx = w * (.04 + k * .135); bh = h * (.25 + .45 * ((k * 37) % 10) / 10)
            rrect(C, bx, h * .8 - bh, bx + bw, h * .8, 3, light, .12 + .03 * (k % 3))
    elif kind == 3:   # waves
        for k in range(4):
            line(C, [(w * x / 12, h * (.45 + .1 * k) + math.sin(x * 1.3 + k) * h * .03) for x in range(13)], max(2, h * .02), light, .18)
    rrect(C, 0, h * .7, w, h, 0, (0, 0, 0), .22)
    if title: text(C, w * .07, h * .86, title, F('D8', max(12, int(min(w * .1, h * .14)))), TXT)
    clip_rr(C, r)
    return C

def magnifier(L, cx, cy, s, col, a=1.0):
    ring(L, cx - s * .15, cy - s * .15, s * .55, max(2, s * .16), col, a)
    line(L, [(cx + s * .25, cy + s * .25), (cx + s * .7, cy + s * .7)], max(2, s * .18), col, a)

def lock(L, cx, cy, s, col, a=1.0):
    line(L, arc_pts(cx, cy - s * .1, s * .42, 180, 360, 12), max(2, s * .15), col, a)
    rrect(L, cx - s * .6, cy - s * .1, cx + s * .6, cy + s * .75, s * .15, col, a)

def mic(L, cx, cy, s, col, a=1.0):
    rrect(L, cx - s * .28, cy - s * .75, cx + s * .28, cy + s * .2, s * .28, col, a)
    line(L, arc_pts(cx, cy - s * .1, s * .5, 0, 180, 12), max(2, s * .12), col, a)
    line(L, [(cx, cy + s * .4), (cx, cy + s * .7)], max(2, s * .12), col, a)

def bell_g(L, cx, cy, s, col, a=1.0):
    rrect(L, cx - s * .55, cy - s * .55, cx + s * .55, cy + s * .45, s * .5, col, a, corners=(True, True, False, False))
    rrect(L, cx - s * .8, cy + s * .35, cx + s * .8, cy + s * .55, s * .1, col, a)
    circle(L, cx, cy + s * .72, s * .16, col, a)

def box_g(L, cx, cy, s, col, a=1.0):
    rrect(L, cx - s * .8, cy - s * .55, cx + s * .8, cy + s * .7, s * .1, col, a)
    rrect(L, cx - s * .12, cy - s * .55, cx + s * .12, cy + s * .7, 0, (10, 13, 17), a * .35)

def play_tri(L, cx, cy, s, col, a=1.0):
    poly(L, [(cx - s * .4, cy - s * .55), (cx + s * .6, cy), (cx - s * .4, cy + s * .55)], col, a)

def pause_g(L, cx, cy, s, col, a=1.0):
    rrect(L, cx - s * .5, cy - s * .6, cx - s * .14, cy + s * .6, s * .08, col, a); rrect(L, cx + s * .14, cy - s * .6, cx + s * .5, cy + s * .6, s * .08, col, a)

def rect_lerp(r0, r1, q):
    return tuple(lerp(r0[i], r1[i], q) for i in range(4))

def focus_path(sched, t, dur=0.32):
    # sched: list of (time, rect); returns rect at time t (smoothly moving)
    cur = 0
    for i, (tt, r) in enumerate(sched):
        if t >= tt: cur = i
    if cur == 0: return sched[0][1]
    q = e_io(prog(t, sched[cur][0], sched[cur][0] + dur))
    return rect_lerp(sched[cur - 1][1], sched[cur][1], q)

from PIL import ImageEnhance
def dim_rgb(img, k):
    if k <= 0.005: return img
    a = img.getchannel('A')
    rgb = ImageEnhance.Brightness(img.convert('RGB')).enhance(1 - k).convert('RGBA')
    rgb.putalpha(a)
    return rgb
