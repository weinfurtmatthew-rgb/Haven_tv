from common5 import *

# =============================================================== phone: remote, keyboard, cast
PW_, PH_ = 340, 690
PCX, PCY = 500, 650
_PSH = None
def phone_body(S):
    global _PSH
    if _PSH is None:
        sh = layer(PW_ + 80, PH_ + 80); rrect(sh, 40, 58, 40 + PW_, 58 + PH_, 52, (0, 0, 0), .6)
        _PSH = sh.filter(ImageFilter.GaussianBlur(18))
    P = _PSH.copy()
    rrect(P, 40, 40, 40 + PW_, 40 + PH_, 52, (24, 28, 36)); rrect_o(P, 40, 40, 40 + PW_, 40 + PH_, 52, 3, (84, 94, 108))
    clip_rr(S, 40)
    put(P, S, 54, 54)
    rrect(P, 40 + PW_ / 2 - 46, 66, 40 + PW_ / 2 + 46, 88, 11, (8, 10, 14))
    return P

def phone_origin():
    return PCX - (PW_ + 80) / 2 + 54, PCY - (PH_ + 80) / 2 + 54

def chevron(L, cx, cy, s, d, col, a=1.0, w=5):
    if d == 'r': pts = [(cx - s * .3, cy - s * .6), (cx + s * .3, cy), (cx - s * .3, cy + s * .6)]
    elif d == 'l': pts = [(cx + s * .3, cy - s * .6), (cx - s * .3, cy), (cx + s * .3, cy + s * .6)]
    elif d == 'u': pts = [(cx - s * .6, cy + s * .3), (cx, cy - s * .3), (cx + s * .6, cy + s * .3)]
    else: pts = [(cx - s * .6, cy - s * .3), (cx, cy + s * .3), (cx + s * .6, cy - s * .3)]
    line(L, pts, w, col, a)

PRESSES = [(1.6, 'r'), (2.3, 'r'), (3.2, 'ok'), (5.3, '+'), (5.75, '+'), (6.2, '+')]
def phone_remote(t):
    S = Image.new('RGBA', (PW_ - 28, PH_ - 28), (16, 19, 24, 255))
    cx = 156
    text(S, cx, 76, 'Living Room TV', F('B7', 24), TXT, al='m')
    f = F('B5', 18); wv = f.getlength('Connected')
    circle(S, cx - wv / 2 - 14, 106, 5, GREEN); text(S, cx + 0, 106, 'Connected', f, MUTED, al='m')
    circle(S, cx, 300, 128, (30, 36, 46)); ring(S, cx, 300, 128, 2, (52, 62, 76))
    pos = {'u': (cx, 208), 'r': (cx + 92, 300), 'd': (cx, 392), 'l': (cx - 92, 300), 'ok': (cx, 300)}
    for tt, d in PRESSES:
        q = trap(t, tt - 0.05, tt, tt + 0.1, tt + 0.45)
        if q > 0 and d in pos:
            circle(S, pos[d][0], pos[d][1], 46 if d == 'ok' else 36, ACC, .35 * q)
    for d in 'urdl': chevron(S, pos[d][0], pos[d][1], 18, d, TXT, 1, 4)
    circle(S, cx, 300, 50, (46, 54, 66)); text(S, cx, 300, 'OK', F('B7', 24), TXT, al='m')
    for i, kind in enumerate(['back', 'home', 'mic']):
        x = 66 + i * 90; y = 494
        circle(S, x, y, 32, ACC if kind == 'mic' else (36, 42, 52))
        if kind == 'back': chevron(S, x, y, 16, 'l', TXT, 1, 4)
        elif kind == 'home': poly(S, [(x - 14, y + 12), (x - 14, y - 2), (x, y - 14), (x + 14, y - 2), (x + 14, y + 12)], TXT)
        else: mic(S, x, y, 22, (10, 13, 17))
    rrect(S, 46, 566, 266, 622, 28, (36, 42, 52))
    text(S, 156, 594, 'Volume', F('B7', 18), MUTED, al='m')
    line(S, [(74, 594), (92, 594)], 4, TXT); line(S, [(220, 594), (238, 594)], 4, TXT); line(S, [(229, 585), (229, 603)], 4, TXT)
    for tt, d in PRESSES:
        if d == '+':
            q = trap(t, tt - 0.05, tt, tt + 0.1, tt + 0.4)
            if q > 0: circle(S, 229, 594, 26, ACC, .35 * q)
    return S

KEYS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
def phone_keys(t, q, active):
    S = Image.new('RGBA', (PW_ - 28, PH_ - 28), (16, 19, 24, 255))
    text(S, 156, 76, 'Living Room TV', F('B7', 24), TXT, al='m')
    text(S, 156, 106, 'Keyboard', F('B5', 18), MUTED, al='m')
    rrect(S, 18, 140, 294, 196, 20, (36, 42, 52))
    text(S, 38, 168, q if q else 'Search', F('B5', 24), TXT if q else MUTED)
    if active and int(t * 2.6) % 2 == 0:
        x = 38 + F('B5', 24).getlength(q) + 3; rrect(S, x, 152, x + 2, 184, 1, ACC)
    last = q[-1] if (q and active) else None
    for r, row in enumerate(KEYS):
        n = len(row); kw, gap = 26, 4; x0 = (312 - (n * kw + (n - 1) * gap)) / 2; y = 404 + r * 52
        for k, c in enumerate(row):
            x = x0 + k * (kw + gap)
            hl = (c == last)
            rrect(S, x, y, x + kw, y + 44, 6, ACC if hl else (46, 54, 66))
            text(S, x + kw / 2, y + 22, c, F('B7', 18), (10, 13, 17) if hl else TXT, al='m')
    rrect(S, 60, 560, 210, 604, 8, ACC if (last == ' ') else (46, 54, 66)); text(S, 135, 582, 'space', F('B7', 16), TXT, al='m')
    rrect(S, 218, 560, 296, 604, 8, ACC); text(S, 257, 582, 'Go', F('B7', 18), (10, 13, 17), al='m')
    return S

PHOTO_COLS = [((169, 204, 230), (94, 140, 106)), ((240, 190, 140), (200, 110, 80)), ((120, 150, 200), (60, 80, 120)), ((200, 220, 200), (110, 150, 120)),
              ((250, 210, 160), (160, 110, 90)), ((150, 180, 210), (90, 120, 150)), ((230, 170, 180), (150, 90, 110)), ((180, 200, 170), (100, 130, 90)),
              ((210, 200, 240), (120, 110, 170)), ((240, 220, 180), (180, 140, 90)), ((160, 210, 220), (70, 130, 140)), ((220, 190, 200), (140, 100, 120))]
_THM = {}
def thumb(k, s=96):
    key = (k, s)
    if key not in _THM:
        if k == 4:
            ph = photo_img().crop((240, 0, 240 + 456, 456)).resize((s, s), Image.LANCZOS).copy()
        else:
            sky, land = PHOTO_COLS[k]
            ph = Image.new('RGBA', (s, s), sky + (255,))
            circle(ph, s * (.2 + .6 * ((k * 37) % 10) / 10), s * .3, s * .1, (255, 245, 220), .9)
            rrect(ph, -s * .2, s * .55, s * .7, s * 1.2, s * .4, land)
            rrect(ph, s * .4, s * .62, s * 1.3, s * 1.2, s * .4, lerpc(land, (0, 0, 0), .2))
        _THM[key] = clip_rr(ph, max(6, s // 10))
    return _THM[key]

def phone_photos(t):
    S = Image.new('RGBA', (PW_ - 28, PH_ - 28), (16, 19, 24, 255))
    text(S, 22, 84, 'Photos', F('D8', 34), TXT)
    text(S, 22, 118, 'Lake trip  ·  48 photos', F('B5', 18), MUTED)
    for k in range(12):
        r, c = divmod(k, 3); x = 6 + c * 102; y = 150 + r * 102
        put(S, thumb(k), int(x), int(y))
    sel = clamp(prog(t, 15.5, 15.8))
    if sel > 0:
        x = 6 + 1 * 102; y = 150 + 1 * 102
        rrect_o(S, x - 4, y - 4, x + 100, y + 100, 14, 4, ACC, sel)
        check(S, x + 80, y + 16, 12, sel, ACC)
    sh = e_out(prog(t, 15.8, 16.3))
    if sh > 0:
        yy = 662 - 220 * sh
        rrect(S, 0, yy, 312, 662 + 40, 30, (30, 36, 46))
        text(S, 22, yy + 40, 'Cast to', F('B7', 20), MUTED)
        b1 = (18, yy + 64, 294, yy + 116); b2 = (18, yy + 128, 294, yy + 180)
        p1 = trap(t, 16.3, 16.4, 16.45, 16.7); p2 = trap(t, 19.0, 19.1, 19.15, 19.4)
        rrect(S, b1[0] + 3 * p1, b1[1] + 3 * p1, b1[2] - 3 * p1, b1[3] - 3 * p1, 26, ACC)
        text(S, 156, b1[1] + 26, 'Living Room TV', F('B7', 22), (10, 13, 17), al='m')
        rrect(S, b2[0] + 3 * p2, b2[1] + 3 * p2, b2[2] - 3 * p2, b2[3] - 3 * p2, 26, VIOLET if t >= 19.1 else (46, 54, 66))
        text(S, 156, b2[1] + 26, 'Every room', F('B7', 22), (10, 13, 17) if t >= 19.1 else TXT, al='m')
    return S

HOME_CARDS = [(48, 186, 394, 381), (410, 186, 650, 321), (666, 186, 906, 321)]
def tv_remote(t):
    L = home_static()
    k = 0
    for tt, d in PRESSES:
        if d == 'r' and t >= tt: k += 1
    fr = focus_path([(0, HOME_CARDS[0]), (1.6, HOME_CARDS[1]), (2.3, HOME_CARDS[2])], t, 0.25)
    fa = clamp(prog(t, 0.6, 0.9))
    focus(L, *fr, 16, fa, TXT, 4, 6)
    pl = e_io(prog(t, 3.3, 3.9))
    if pl > 0:
        P = poster(SW, SH, (47, 107, 87), None, 0, r=0)
        text(P, 48, 70, 'Trail Notes', F('D8', 44), TXT); text(P, 48, 112, 'Episode 2  ·  Into the pines', F('B5', 24), TXT, .8)
        rrect(P, 48, SH - 60, SW - 48, SH - 52, 4, (255, 255, 255), .3); rrect(P, 48, SH - 60, 48 + (SW - 96) * (.12 + .002 * max(0, t - 3.9)), SH - 52, 4, ACC)
        L.alpha_composite(scale_alpha(P, pl))
    va = trap(t, 5.2, 5.4, 6.8, 7.2)
    if va > 0:
        lv = 40
        for tt, d in PRESSES:
            if d == '+' and t >= tt: lv += 4
        rrect(L, SW - 110, 170, SW - 62, 550, 24, (10, 13, 17), .7 * va)
        hh = 340 * lv / 100
        rrect(L, SW - 98, 540 - hh, SW - 74, 540, 12, TXT, va)
        text(L, SW - 86, 140, str(lv), F('B7', 26), TXT, va, al='m')
        glyph(L, 'speaker', SW - 86, 590, 14, TXT, va)
    return L

def tv_search(t):
    L = new_screen()
    q = typed('paper cities', t, 8.3, 10.6)
    rrect(L, 48, 40, SW - 48, 104, 32, SURF2)
    magnifier(L, 90, 72, 24, MUTED)
    text(L, 128, 72, q if q else 'Search…', F('B5', 30), TXT if q else MUTED)
    if t < 10.9 and int(t * 2.6) % 2 == 0:
        x = 128 + F('B5', 30).getlength(q) + 4; rrect(L, x, 52, x + 3, 92, 1, ACC)
    pill(L, SW - 80, 50, "Typing from Matt's phone", F('B7', 18), (46, 54, 66), TXT, 1, padx=16, h=44, al='r')
    res = [('Paper Cities', (107, 47, 60), 2), ('Paper Moon Diaries', (60, 98, 120), 0), ('City of Paper', (138, 90, 43), 2), ('The Paper Trail', (47, 107, 87), 3)]
    text(L, 48, 150, 'Top results', F('B7', 26), TXT, clamp(prog(t, 10.4, 10.8)))
    for i, (nm, col, kd) in enumerate(res):
        pp, dd = fx(t, 10.5 + 0.08 * i, 0.7)
        x = 48 + i * 302
        put(L, scale_alpha(poster(278, 156, col, None, kd), pp), x, int(184 + dd))
        text(L, x + 2, 368 + dd, nm, F('B7', 22), TXT, pp)
        text(L, x + 2, 396 + dd, ['Drama · 2 seasons', 'Comedy · Movie', 'Thriller · Movie', 'Documentary · 4 eps'][i], F('B5', 18), MUTED, pp)
    fa = clamp(prog(t, 11.6, 11.9))
    focus(L, 48, 184, 326, 340, 16, fa, TXT, 4, 6)
    pp, dd = fx(t, 11.8, 0.7)
    rrect(L, 48, 460 + dd, 1232, 660 + dd, 24, SURF, pp)
    put(L, scale_alpha(poster(260, 146, (107, 47, 60), None, 2), pp), 72, int(487 + dd))
    text(L, 360, 520 + dd, 'Paper Cities', F('D8', 38), TXT, pp)
    text(L, 360, 562 + dd, 'Season 1, Episode 1  ·  Included with Streams+', F('B5', 22), MUTED, pp)
    rrect(L, 360, 596 + dd, 500, 640 + dd, 22, ACC, pp); play_tri(L, 390, 618 + dd, 14, (10, 13, 17), pp); text(L, 410, 618 + dd, 'Play', F('B7', 20), (10, 13, 17), pp)
    return L

def _cast_photo():
    return photo_img().crop((62, 0, 873, 456)).resize((SW, SH), Image.LANCZOS)
_CP = None
def cast_screen(t):
    global _CP
    if _CP is None: _CP = _cast_photo()
    L = _CP.copy()
    pa = e_out(prog(t, 17.5, 18.0))
    pill(L, 40, 36, "Casting from Matt's phone", F('B7', 22), (10, 13, 17), TXT, .8 * pa, padx=20, h=48)
    return L

BIG = (1290, 640, 0.74)
MINIS = [((1290, 470), 'Living Room'), ((1040, 800), 'Bedroom'), ((1540, 800), 'Kitchen')]
MS = 0.33

def sc_phone(t, d):
    L = layer()
    heads(L, t, [(0.15, 7.4, 'Your phone is the remote.', 'Touchpad, volume and voice. No app to hunt for.', 104),
                 (7.7, 14.3, 'And the keyboard.', 'Type on your phone. It shows up on the TV.', 112),
                 (14.6, 24.4, 'Cast anything, anywhere.', 'One photo to one room. Or to every room at once.', 104)])
    # TV content
    if t < 7.4: scr = tv_remote(t)
    elif t < 8.0: scr = xfade(tv_remote(t), tv_search(t), prog(t, 7.4, 8.0))
    elif t < 14.4: scr = tv_search(t)
    elif t < 15.0: scr = xfade(tv_search(t), dimmed(home_static(), .25), prog(t, 14.4, 15.0))
    elif t < 17.2: scr = dimmed(home_static(), .25)
    elif t < 17.5: scr = xfade(dimmed(home_static(), .25), cast_screen(t), prog(t, 17.2, 17.5))
    else: scr = cast_screen(t)
    p = e_out(prog(t, 0.2, 1.3))
    tva = clamp(prog(t, 0.2, 0.8))
    allr = e_io(prog(t, 19.3, 20.3))
    if allr <= 0:
        blit_at(L, scr, BIG[0], BIG[1] + (1 - p) * 120, BIG[2], tva)
    else:
        cx = lerp(BIG[0], MINIS[0][0][0], allr); cy = lerp(BIG[1], MINIS[0][0][1], allr); s = lerp(BIG[2], MS, allr)
        # dashed beams from phone
        for k, ((mx, my), nm) in enumerate(MINIS):
            q = e_out(prog(t, 19.6 + .15 * k, 20.4 + .15 * k))
            if q <= 0: continue
            x0, y0 = PCX + 120, PCY - 200; x1, y1 = mx - 220 if mx > PCX else mx, my
            ln = math.hypot(x1 - x0, y1 - y0); end = ln * q; pos = -(t * 120) % 30; ss = pos - 30
            while ss < end:
                a0 = max(ss, 0); a1 = min(ss + 14, end)
                if a1 > a0:
                    line(L, [(lerp(x0, x1, a0 / ln), lerp(y0, y1, a0 / ln)), (lerp(x0, x1, a1 / ln), lerp(y0, y1, a1 / ln))], 4, VIOLET, .7 * q * (1 - e_io(prog(t, 22.6, 23.4))))
                ss += 30
        for k in (1, 2):
            (mx, my), nm = MINIS[k]
            q = e_back(prog(t, 20.0 + .2 * k, 20.7 + .2 * k))
            if q > 0:
                blit_at(L, cast_screen(t).copy(), mx, my, MS * (0.8 + 0.2 * q), clamp(q))
        blit_at(L, scr, cx, cy, s, 1)
        for k, ((mx, my), nm) in enumerate(MINIS):
            q = e_out(prog(t, 20.4 + .15 * k, 20.9 + .15 * k))
            if q > 0:
                text(L, mx, my + SH * MS / 2 + 34, nm, F('B7', 26), TXT, q, al='m')
    # phone
    if t < 7.6: S = phone_remote(t)
    elif t < 14.6:
        q = typed('paper cities', t, 8.3, 10.6)
        S = phone_keys(t, q, t < 10.8)
        if t < 7.9: S = xfade(phone_remote(t), S, prog(t, 7.6, 7.9))
    else:
        S = phone_photos(t)
        if t < 14.9:
            S = xfade(phone_keys(t, 'paper cities', False), S, prog(t, 14.6, 14.9))
    P = phone_body(S)
    pp = e_out(prog(t, 0.3, 1.2))
    bob = math.sin(t * 1.1) * 6
    put(L, scale_alpha(P, clamp(pp)), int(PCX - P.width / 2), int(PCY - P.height / 2 + (1 - pp) * 160 + bob))
    # flying photo
    fp = prog(t, 16.6, 17.4)
    if 0 < fp < 1:
        ox, oy = phone_origin()
        sx = ox + 6 + 102 + 48; sy = oy + 150 + 102 + 48 + bob
        ex, ey = BIG[0], BIG[1]
        q = e_io(fp)
        mx = lerp(sx, ex, q); my = lerp(sy, ey, q) - math.sin(math.pi * q) * 220
        w = lerp(96, SW * BIG[2], q); h = lerp(96, SH * BIG[2], q)
        ph = _CP if _CP is not None else _cast_photo()
        im = ph.resize((max(2, int(w)), max(2, int(h))), Image.BILINEAR).copy(); clip_rr(im, max(6, int(lerp(10, 18, q))))
        put(L, scale_alpha(im, 1 - e_in(prog(fp, 0.85, 1.0)) * 0.6), int(mx - w / 2), int(my - h / 2))
    return L

# =============================================================== games & sports
def _sky(y):
    k = clamp(y / 330.0)
    return lerpc((24, 10, 44), (110, 34, 96), k)

def ui_game(t):
    L = new_screen((24, 10, 44))
    for k in range(11):
        rrect(L, 0, k * 30, SW, (k + 1) * 30, 0, _sky(k * 30 + 15))
    circle(L, 640, 300, 150, (255, 150, 90))
    circle(L, 640, 300, 150, (255, 90, 120), .35)
    for k in range(6):
        y = 250 + k * 16; hgt = 3 + k * 1.6
        rrect(L, 460, y, 820, y + hgt, 0, _sky(y))
    for k in range(9):
        mx = 120 + k * 140; mh = 30 + (k * 53) % 60
        poly(L, [(mx - 90, 330), (mx, 330 - mh), (mx + 90, 330)], (40, 16, 64))
    rrect(L, 0, 330, SW, SH, 0, (14, 6, 28))
    for i in range(-10, 11):
        line(L, [(640 + i * 30, 330), (640 + i * 190, SH)], 2, (220, 60, 200), .5)
    for k in range(12):
        u = ((k + t * 2.2) % 12) / 12.0
        y = 330 + 390 * u ** 2.2
        line(L, [(0, y), (SW, y)], 2, (90, 220, 255), .25 + .5 * u)
    poly(L, [(610, 330), (670, 330), (980, SH), (300, SH)], (26, 12, 46))
    for k in range(7):
        u0 = ((k / 7.0) + t * 0.9) % 1.0; u1 = min(1.0, u0 + 0.05)
        y0 = 330 + 390 * u0 ** 2.2; y1 = 330 + 390 * u1 ** 2.2
        line(L, [(640, y0), (640, y1)], 2 + 10 * u0, (255, 220, 120), .9)
    sway = math.sin(t * 1.8) * 40
    cx = 640 + sway; cy = 620
    poly(L, [(cx - 150, cy + 50), (cx - 120, cy - 10), (cx - 60, cy - 50), (cx + 60, cy - 50), (cx + 120, cy - 10), (cx + 150, cy + 50)], (30, 34, 60))
    rrect(L, cx - 150, cy + 10, cx + 150, cy + 60, 14, (50, 56, 90))
    rrect(L, cx - 130, cy + 22, cx - 60, cy + 40, 6, (255, 60, 90)); rrect(L, cx + 60, cy + 22, cx + 130, cy + 40, 6, (255, 60, 90))
    circle(L, cx - 95, cy + 31, 50, (255, 60, 90), .18); circle(L, cx + 95, cy + 31, 50, (255, 60, 90), .18)
    text(L, 48, 60, 'LAP 2/3', F('D8', 34), TXT); text(L, 48, 100, 'P1  ·  +0.84s', F('B7', 22), (255, 220, 120))
    spd = 200 + int(12 * math.sin(t * 1.3))
    text(L, SW - 140, 74, str(spd), F('D8', 64), TXT, al='r'); text(L, SW - 48, 84, 'km/h', F('B7', 24), MUTED, al='r')
    # toast
    ta = e_out(prog(t, 0.9, 1.4)) * (1 - e_in(prog(t, 3.4, 3.9)))
    if ta > 0.01:
        T = layer(560, 92)
        rrect(T, 0, 0, 560, 92, 46, (16, 20, 28), .92); rrect_o(T, 0, 0, 560, 92, 46, 2, (70, 84, 102))
        circle(T, 48, 46, 26, GREEN); glyph(T, 'games', 48, 46, 15, (10, 13, 17))
        text(T, 92, 34, 'Console detected on HDMI 2', F('B7', 22), TXT); text(T, 92, 64, 'Switched to Game mode', F('B5', 20), MUTED)
        put(L, scale_alpha(T, ta), int(SW / 2 - 280), int(32 + (1 - ta) * -30))
    # panel
    pa = e_out(prog(t, 3.6, 4.2)) * (1 - e_in(prog(t, 8.8, 9.4)))
    if pa > 0.01:
        P = layer(380, 470)
        rrect(P, 0, 0, 380, 470, 26, (16, 20, 28), .92); rrect_o(P, 0, 0, 380, 470, 26, 2, (70, 84, 102))
        text(P, 28, 46, 'Game mode', F('D8', 32), TXT); pill(P, 352, 28, 'On', F('B7', 18), GREEN, (10, 13, 17), 1, padx=14, h=34, al='r')
        ms = int(round(lerp(38, 4, e_io(prog(t, 4.3, 6.0)))))
        rows = [('Input lag', '%d ms' % ms), ('Refresh rate', '120 Hz'), ('VRR', 'On'), ('HDR', 'Game HDR'), ('Notifications', 'Held')]
        for i, (k, v) in enumerate(rows):
            q = e_out(prog(t, 3.9 + .12 * i, 4.4 + .12 * i))
            y = 100 + i * 70
            rrect(P, 20, y, 360, y + 58, 16, (30, 36, 48), q)
            text(P, 40, y + 29, k, F('B7', 22), TXT, q); text(P, 340, y + 29, v, F('B7', 22), GREEN if i == 0 else ACC, q, al='r')
        put(L, scale_alpha(P, pa), int(SW - 48 - 380 + (1 - pa) * 60), 140)
    return L

_PL = [(k, ((k * 97) % 13) / 13.0, ((k * 61) % 11) / 11.0) for k in range(22)]
def field(w, h, t, base=(40, 110, 60), alt=(46, 122, 68), seed=0, pan=40):
    L = Image.new('RGBA', (w, h), base + (255,))
    sw = w / 11.0
    off = (t * pan) % (2 * sw)
    for k in range(-2, 14):
        x = k * sw - off
        if k % 2 == 0: rrect(L, x, 0, x + sw, h, 0, alt)
        line(L, [(x, h * .08), (x, h * .95)], max(1, w / 420), (255, 255, 255), .45)
    for k, u, v in _PL:
        team = k % 2
        x = w * (.12 + .76 * u) + math.sin(t * 1.3 + k + seed) * w * .03 - (t * pan * 0.3) % 1
        y = h * (.18 + .7 * v) + math.cos(t * 1.1 + k * 1.7 + seed) * h * .03
        r = max(3, w / 110)
        circle(L, x, y + r * .4, r, (0, 0, 0), .3)
        circle(L, x, y, r, (245, 245, 245) if team == 0 else (230, 80, 70))
    bx = w * (.5 + .2 * math.sin(t * .9 + seed)); by = h * (.5 + .2 * math.cos(t * .7 + seed))
    circle(L, bx, by, max(2, w / 220), (150, 90, 40))
    return L

def scorebug(L, x, y, a_, sa, b_, sb, q, clock, s=1.0, a=1.0):
    w = 430 * s; h = 56 * s
    rrect(L, x, y, x + w, y + h, 14 * s, (10, 13, 17), .86 * a)
    f = F('B7', int(24 * s)); fd = F('D8', int(28 * s))
    text(L, x + 22 * s, y + h / 2, a_, f, TXT, a); text(L, x + 100 * s, y + h / 2, str(sa), fd, TXT, a, al='m')
    text(L, x + 150 * s, y + h / 2, b_, f, TXT, a); text(L, x + 228 * s, y + h / 2, str(sb), fd, TXT, a, al='m')
    rrect(L, x + 268 * s, y + 10 * s, x + 270 * s, y + h - 10 * s, 1, (70, 84, 102), a)
    text(L, x + 290 * s, y + h / 2, q, f, MUTED, a); text(L, x + w - 22 * s, y + h / 2, clock, f, TXT, a, al='r')

def ui_sports(ts):
    L = field(SW, SH, ts)
    secs = int(252 - ts * 1.0)
    scorebug(L, 40, 30, 'HAW', 21, 'BAY', 17, 'Q3', '%d:%02d' % (secs // 60, secs % 60))
    rrect(L, 40, 96, 140, 128, 16, CORAL); text(L, 90, 112, 'LIVE', F('B7', 18), (10, 13, 17), al='m')
    ta = e_out(prog(ts, 0.8, 1.3)) * (1 - e_in(prog(ts, 3.6, 4.1)))
    if ta > 0.01:
        T = layer(560, 92)
        rrect(T, 0, 0, 560, 92, 46, (16, 20, 28), .92); rrect_o(T, 0, 0, 560, 92, 46, 2, (70, 84, 102))
        circle(T, 48, 46, 26, ACC); glyph(T, 'play', 48, 46, 14, (10, 13, 17))
        text(T, 92, 34, 'Sports mode on', F('B7', 22), TXT); text(T, 92, 64, 'Sharper motion, true greens, live scores', F('B5', 20), MUTED)
        put(L, scale_alpha(T, ta), int(SW / 2 - 280 + 130), int(SH - 130 + (1 - ta) * 30))
    pp = e_back(prog(ts, 2.4, 3.0))
    if pp > 0:
        pw, ph = 384, 216
        F2 = field(pw, ph, ts + 3, (30, 90, 110), (36, 100, 122), 4, 25)
        sb = 14 if ts < 5.2 else 20
        scorebug(F2, 10, 10, 'NOR', 10, 'SEA', sb, 'Q2', '1:08', 0.62)
        clip_rr(F2, 16)
        G = layer(pw + 16, ph + 16)
        alert = trap(ts, 5.0, 5.3, 7.4, 8.0)
        rrect_o(G, 0, 0, pw + 16, ph + 16, 22, 4, lerpc(TXT, CORAL, alert), .9)
        put(G, F2, 8, 8)
        popimg(L, G, SW - 48 - pw / 2 - 8, 48 + ph / 2 + 8, pp, 0.6)
        if alert > 0:
            f = F('B7', 22); s = 'Touchdown!  SEA 20 – 10'
            w = f.getlength(s) + 48
            rrect(L, SW - 48 - w, 300, SW - 48, 346, 23, CORAL, alert)
            text(L, SW - 48 - w / 2, 323, s, f, (10, 13, 17), alert, al='m')
    ma = e_out(prog(ts, 8.0, 8.6))
    if ma > 0.01:
        P = layer(440, 200)
        rrect(P, 0, 0, 440, 200, 26, (16, 20, 28), .92); rrect_o(P, 0, 0, 440, 200, 26, 2, (70, 84, 102))
        text(P, 28, 42, 'Audio mix', F('D8', 30), TXT)
        cv = lerp(.72, .25, e_io(prog(ts, 8.8, 9.7))); cr = lerp(.4, .9, e_io(prog(ts, 9.9, 10.8)))
        text(P, 28, 100, 'Commentary', F('B7', 20), TXT); slider(P, 176, 400, 100, cv, 1, ACC, True, 8)
        text(P, 28, 154, 'Crowd', F('B7', 20), TXT); slider(P, 176, 400, 154, cr, 1, WARM, True, 8)
        put(L, scale_alpha(P, ma), int(SW - 48 - 440), int(SH - 48 - 200 + (1 - ma) * 40))
    return L

def sc_games(t, d):
    L = layer()
    heads(L, t, [(0.15, 9.8, 'Game mode, automatically.', 'Lowest lag, 120 Hz and VRR. Notifications wait.', 104),
                 (10.2, 22.4, 'Never miss the moment.', 'Sports mode, picture-in-picture, live alerts.', 108)])
    if t < 9.9: scr = ui_game(t)
    elif t < 10.6: scr = xfade(ui_game(t), ui_sports(t - 9.9), prog(t, 9.9, 10.6))
    else: scr = ui_sports(t - 9.9)
    entry(L, scr, t)
    return L

# =============================================================== rooms (per-room config)
FEATS = [('art', 'Art mode'), ('bulb', 'Lights'), ('cam', 'Cameras'), ('speaker', 'Speakers'), ('alarm', 'Alarm'),
         ('moon', 'Sleep timer'), ('face', 'Kid mode'), ('games', 'Game mode'), ('bell', 'Doorbell')]
RSETS = [('Living Room', {'Art mode', 'Lights', 'Cameras', 'Speakers', 'Game mode', 'Doorbell'}),
         ('Bedroom', {'Art mode', 'Lights', 'Speakers', 'Alarm', 'Sleep timer', 'Doorbell'}),
         ('Kitchen', {'Lights', 'Cameras', 'Speakers', 'Doorbell'}),
         ("Lily's room", {'Lights', 'Speakers', 'Sleep timer', 'Kid mode'})]
RT = [0.0, 3.0, 5.6, 8.0]

def ui_rooms(t):
    L = new_screen()
    p0, dy0 = fx(t, 0.0)
    topbar(L, 'Rooms', p0, sub='Same Haven. Different features.', room=None)
    cur = 0
    for i, tt in enumerate(RT):
        if t >= tt: cur = i
    ps, dys = fx(t, 0.1)
    rrect(L, 48, 120 + dys, 360, 672 + dys, 24, SURF, ps)
    rects = [(62, 140 + i * 74, 346, 202 + i * 74) for i in range(len(RSETS))]
    fr = focus_path([(RT[i], rects[i]) for i in range(len(RSETS))], t, 0.3)
    rrect(L, fr[0], fr[1] + dys, fr[2], fr[3] + dys, 16, SURF2, ps)
    for i, (nm, st) in enumerate(RSETS):
        x0, y0, x1, y1 = rects[i]
        rrect(L, x0 + 16, y0 + 17 + dys, x0 + 58, y0 + 45 + dys, 6, [ACC, WARM, GREEN, CORAL][i], ps * .8)
        text(L, x0 + 76, (y0 + y1) / 2 + dys, nm, F('B7', 22), TXT if i == cur else MUTED, ps)
    text(L, 76, 520 + dys, '+  Add a TV', F('B7', 22), ACC, ps)
    text(L, 76, 600 + dys, 'All run the same', F('B5', 18), MUTED, ps); text(L, 76, 626 + dys, 'launcher underneath.', F('B5', 18), MUTED, ps)
    for j, (g, nm) in enumerate(FEATS):
        r, c = divmod(j, 3)
        x = 384 + c * 288; y = 120 + r * 186
        pp, dd = fx(t, 0.2 + 0.05 * j, 0.7)
        on_prev = nm in RSETS[max(0, cur - 1)][1]; on_cur = nm in RSETS[cur][1]
        q = e_io(prog(t, RT[cur] + 0.05 * j, RT[cur] + 0.05 * j + 0.35)) if cur > 0 else 1.0
        v = lerp(1.0 if on_prev else 0.0, 1.0 if on_cur else 0.0, q)
        rrect(L, x, y + dd, x + 272, y + 170 + dd, 22, lerpc(SURF, (24, 40, 54), v), pp)
        rrect_o(L, x, y + dd, x + 272, y + 170 + dd, 22, 2, lerpc(LINE, (60, 110, 140), v), pp)
        cx, cy = x + 52, y + 52 + dd
        circle(L, cx, cy, 30, lerpc((40, 48, 60), (30, 58, 78), v), pp)
        gc = lerpc(MUTED, ACC, v)
        if g == 'moon': moon(L, cx, cy, 14, gc, lerpc((40, 48, 60), (30, 58, 78), v), pp)
        elif g == 'face': face(L, cx, cy, 16, gc, pp, lerpc((40, 48, 60), (30, 58, 78), v))
        elif g == 'bell': bell_g(L, cx, cy - 2, 15, gc, pp)
        else: glyph(L, g, cx, cy, 15, gc, pp)
        text(L, x + 24, y + 118 + dd, nm, F('B7', 24), lerpc(MUTED, TXT, v), pp)
        toggle(L, x + 176, y + 98 + dd, v, pp, 72, 40)
    return L

def sc_rooms(t, d):
    L = layer()
    heads(L, t, [(0.15, 11.4, 'Pick what each room gets.', 'Every TV runs Haven. Each one is yours to shape.', 104)])
    entry(L, ui_rooms(t), t)
    return L

# =============================================================== end
def sc_end5(t, d):
    L = layer()
    letters(L, W / 2, 420, 'Haven', F('D8', 340), 0.1, t, TXT, stagger=0.06)
    words(L, W / 2, 650, 'Your house. Your TV.', F('B5', 64), 1.0, t, MUTED, blur=10)
    for i in range(3):
        p = e_back(prog(t, 1.6 + i * .12, 2.2 + i * .12))
        circle(L, W / 2 - 40 + i * 40, 760, 7 * p, [ACC, VIOLET, WARM][i], clamp(p))
    fl = 'Profiles  ·  Kid mode  ·  Search  ·  Voice  ·  Scenes  ·  Routines  ·  Art  ·  Lights  ·  Cameras  ·  Doorbell  ·  Speakers  ·  Alarm  ·  Cast  ·  Games'
    fa = e_out(prog(t, 2.2, 3.0))
    text(L, W / 2, 850, fl, F('B7', 26), (120, 130, 144), fa, al='m', blur=(1 - fa) * 6)
    text(L, W / 2, 1010, 'CONCEPT AD  ·  WORKING NAME', F('B7', 22), (96, 106, 120), e_out(prog(t, 2.6, 3.3)), al='m', sp=4)
    return L
