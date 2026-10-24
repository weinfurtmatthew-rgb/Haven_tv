from common5 import *

# =============================================================== scenes & routines
SCN = [('Movie night', VIOLET, 'play', '5 actions'), ('Good morning', WARM, 'alarm', '4 actions'),
       ('Dinner', CORAL, 'music', '3 actions'), ('Away', GREEN, 'cam', '6 actions')]
MOVIE_ACTS = [('bulb', 'Lights down to 20%'), ('art', 'Blinds closed'), ('speaker', 'Soundbar to Cinema mode'),
              ('alarm', 'Doorbell to quiet banner'), ('cam', 'Front door locked')]

def ui_scene_grid(t):
    L = new_screen()
    p0, dy0 = fx(t, 0.0)
    topbar(L, 'Scenes', p0, sub='Tap once. The whole house follows.')
    press = trap(t, 3.3, 3.42, 3.5, 3.75)
    for i, (nm, col, g, acts) in enumerate(SCN):
        pp, dd = fx(t, 0.15 + 0.08 * i, 0.8)
        x = 48 + i * 302; y = 136 + dd
        C = layer(278, 420)
        rrect(C, 0, 0, 278, 420, 28, lerpc(SURF, col, .16)); rrect_o(C, 0, 0, 278, 420, 28, 2, lerpc(LINE, col, .3))
        circle(C, 70, 76, 40, col); glyph(C, g, 70, 76, 20, (10, 13, 17))
        circle(C, 250, 30, 90, col, .08)
        text(C, 30, 180, nm, F('D8', 34), TXT)
        text(C, 30, 220, acts, F('B5', 22), MUTED)
        for k in range(3):
            rrect(C, 30, 270 + k * 40, 248, 300 + k * 40, 15, (255, 255, 255), .05)
            rrect(C, 42, 281 + k * 40, 42 + [120, 90, 150][(k + i) % 3], 289 + k * 40, 4, (255, 255, 255), .22)
        if i == 0 and press > 0:
            C = C.resize((int(278 * (1 - .04 * press)), int(420 * (1 - .04 * press))), Image.BILINEAR)
        put(L, scale_alpha(C, pp), int(x + 139 - C.width / 2), int(y + 210 - C.height / 2))
    fsched = [(0, (48, 136, 326, 556)), (1.6, (350, 136, 628, 556)), (2.2, (48, 136, 326, 556))]
    fr = focus_path(fsched, t)
    fa = clamp(prog(t, 1.0, 1.3))
    focus(L, *fr, 28, fa, TXT, 4, 7)
    p5, dy5 = fx(t, 0.6)
    text(L, 48, 640 + dy5, 'Scenes work from any TV, your phone, or your voice.', F('B5', 22), MUTED, p5)
    return L

def _room(L, td, x0, y0, x1, y1):
    dim = e_io(prog(td, 0.7, 1.6))
    blinds = e_io(prog(td, 1.25, 2.1))
    tvon = e_io(prog(td, 1.8, 2.4))
    rrect(L, x0, y0, x1, y1, 24, lerpc((58, 54, 80), (24, 20, 38), dim))
    wx0, wy0, wx1, wy1 = x0 + 40, y0 + 40, x0 + 210, y0 + 220
    rrect(L, wx0, wy0, wx1, wy1, 8, lerpc((110, 140, 190), (40, 50, 80), dim))
    n = 9
    for k in range(n):
        if k / n < blinds:
            yy = wy0 + (wy1 - wy0) * k / n
            rrect(L, wx0, yy, wx1, yy + (wy1 - wy0) / n - 3, 2, (200, 190, 170), .9 * (1 - .4 * dim))
    rrect_o(L, wx0 - 6, wy0 - 6, wx1 + 6, wy1 + 6, 8, 6, (90, 84, 110))
    rrect(L, x0, y1 - 100, x1, y1, 0, lerpc((44, 40, 62), (18, 15, 28), dim), corners=None)
    tvx0, tvy0 = x0 + 250, y0 + 70
    rrect(L, tvx0, tvy0, tvx0 + 230, tvy0 + 130, 8, lerpc((20, 20, 26), (60, 90, 160), tvon))
    if tvon > 0: circle(L, tvx0 + 115, tvy0 + 65, 120, (90, 130, 220), .12 * tvon)
    rrect(L, x0 + 180, y1 - 190, x1 - 120, y1 - 110, 28, lerpc((90, 76, 120), (54, 44, 76), dim))
    rrect(L, x0 + 165, y1 - 220, x1 - 105, y1 - 160, 24, lerpc((104, 88, 136), (62, 50, 86), dim))
    lx = x1 - 50; br = 1 - 0.8 * dim
    line(L, [(lx, y0 + 140), (lx, y1 - 100)], 5, (120, 110, 130))
    poly(L, [(lx - 28, y0 + 140), (lx + 28, y0 + 140), (lx + 18, y0 + 100), (lx - 18, y0 + 100)], lerpc((120, 100, 80), (255, 200, 120), br))
    circle(L, lx, y0 + 150, 110, WARM, .22 * br)
    circle(L, (x0 + x1) / 2, y0 + 8, 90, (255, 220, 160), .18 * br)

def ui_scene_detail(td):
    L = new_screen((20, 16, 34))
    p0, dy0 = fx(td, 0.05)
    text(L, 48, 92 + dy0, 'Movie night', F('D8', 56), TXT, p0)
    rp = e_back(prog(td, 0.3, 0.8))
    if rp > 0:
        pill(L, 48, 130, 'Running', F('B7', 20), VIOLET, (10, 13, 17), clamp(rp), padx=18, h=36)
    _room(L, td, 48, 200, 640, 672)
    for i, (g, lab) in enumerate(MOVIE_ACTS):
        y = 130 + i * 106
        pp, dd = fx(td, 0.2 + 0.07 * i, 0.6)
        ta = 0.8 + i * 0.5
        done = e_back(prog(td, ta, ta + 0.4))
        rrect(L, 690, y + dd, 1232, y + 90 + dd, 22, lerpc(SURF, (36, 30, 60), clamp(done)), pp)
        circle(L, 740, y + 45 + dd, 26, (40, 36, 64), pp); glyph(L, g, 740, y + 45 + dd, 13, lerpc(MUTED, TXT, clamp(done)), pp)
        text(L, 784, y + 45 + dd, lab, F('B7', 24), lerpc(MUTED, TXT, clamp(done)), pp)
        if done > 0: check(L, 1190, y + 45 + dd, 18 * clamp(done) + 2 * max(0, done - 1), pp, GREEN)
        else:
            sp = (td * 4) % 1
            ring(L, 1190, y + 45 + dd, 16, 3, (70, 84, 102), pp)
    return L

ROUT = [(6.5, 'Good morning', WARM, 'alarm', 'Weekdays at 6:30 AM', ['Bedroom lights ramp up', 'Blinds open', 'Weather on the TV']),
        (8.75, 'Away', GREEN, 'cam', 'When everyone has left', ['Cameras armed', 'Lights off', 'Thermostat to eco']),
        (12.75, 'Lunch break', ACC, 'music', 'Weekdays at 12:45 PM', ['Kitchen speaker on', 'Midday playlist']),
        (17.5, 'Welcome home', ACC, 'bulb', 'When Matt arrives', ['Porch light on', 'Hallway to 60%', 'Music: Evening mix']),
        (19.0, 'Dinner', CORAL, 'music', 'At sunset', ['Kitchen pendants 70%', 'TVs to art mode', 'Speakers: dinner jazz']),
        (21.0, 'Movie night', VIOLET, 'play', 'Fridays at 9:00 PM', ['Lights to 20%', 'Soundbar: Cinema', 'Doorbell quiet']),
        (22.5, 'Good night', (150, 160, 230), 'alarm', 'Every night at 10:30 PM', ['All lights off', 'Doors locked', 'TVs to sleep'])]

def _hfmt(h):
    hh = int(h) % 24; mm = int(round((h - int(h)) * 60 / 5.0) * 5) % 60
    ap = 'AM' if hh < 12 else 'PM'
    h12 = hh % 12 or 12
    return '%d:%02d %s' % (h12, mm, ap)

def ui_routines(tr):
    L = new_screen()
    p0, dy0 = fx(tr, 0.0)
    topbar(L, 'Routines', p0, sub='They run on their own.')
    X0, X1, Y = 90, 1190, 250
    def hx(h): return X0 + (X1 - X0) * (h - 6) / 18.0
    pl, dyl = fx(tr, 0.2)
    rrect(L, X0, Y - 3, X1, Y + 3, 3, (58, 69, 83), pl)
    for h, lab in ((6, '6 AM'), (9, '9'), (12, '12 PM'), (15, '3'), (18, '6 PM'), (21, '9'), (24, '12 AM')):
        line(L, [(hx(h), Y + 12), (hx(h), Y + 20)], 2, (70, 84, 102), pl)
        text(L, hx(h), Y + 42, lab, F('B7', 17), MUTED, pl, al='m')
    hour = lerp(6, 23.6, prog(tr, 0.8, 9.2))
    ph = hx(hour)
    rrect(L, X0, Y - 3, ph, Y + 3, 3, ACC, pl)
    latest = -1
    for i, (h, nm, col, g, trig, acts) in enumerate(ROUT):
        x = hx(h)
        hit = hour >= h
        if hit: latest = i
        tt = 0.8 + (h - 6) / 17.6 * 8.4
        pop = e_back(prog(tr, tt, tt + 0.4)) if hit else 0
        up = (i % 2 == 0)
        ly = Y - 66 if up else Y + 70 + 26
        f = F('B7', 17); w = f.getlength(nm) + 30
        pa = pl * (0.55 + 0.45 * clamp(pop))
        rrect(L, x - w / 2, ly - 18, x + w / 2, ly + 18, 18, col if hit else SURF2, pa)
        text(L, x, ly, nm, f, (10, 13, 17) if hit else TXT, pa, al='m')
        line(L, [(x, ly + (18 if up else -18)), (x, Y + (-10 if up else 10))], 2, col if hit else (70, 84, 102), pa * .7)
        circle(L, x, Y, 9 + 4 * clamp(pop) - 4 * max(0, pop - 1), col if hit else (90, 100, 114), pl)
        if hit:
            q = prog(tr, tt, tt + 0.9)
            ring(L, x, Y, 12 + q * 30, 3, col, (1 - q) * .7)
    circle(L, ph, Y, 13, TXT, pl * clamp(prog(tr, 0.8, 1.0)))
    cw = F('B7', 20).getlength(_hfmt(hour)) + 28
    if tr > 0.8:
        rrect(L, ph - cw / 2, Y - 130, ph + cw / 2, Y - 98, 16, TXT, pl * clamp(prog(tr, 0.8, 1.0)) * (1 if tr < 9.2 else 1))
        text(L, ph, Y - 114, _hfmt(hour), F('B7', 20), (10, 13, 17), pl * clamp(prog(tr, 0.8, 1.0)), al='m')
    # now card
    if latest >= 0:
        h, nm, col, g, trig, acts = ROUT[latest]
        tt = 0.8 + (h - 6) / 17.6 * 8.4
        ca = e_out(prog(tr, tt, tt + 0.35))
        C = layer(1100, 270)
        rrect(C, 0, 0, 1100, 270, 28, lerpc(SURF, col, .12)); rrect_o(C, 0, 0, 1100, 270, 28, 2, lerpc(LINE, col, .4))
        circle(C, 80, 90, 44, col); glyph(C, g, 80, 90, 22, (10, 13, 17))
        text(C, 150, 74, nm, F('D8', 44), TXT)
        text(C, 150, 118, trig, F('B5', 24), MUTED)
        pill(C, 1070, 52, 'Ran just now', F('B7', 18), col, (10, 13, 17), 1, padx=14, h=34, al='r')
        x = 40
        for j, a in enumerate(acts):
            q = e_back(prog(tr, tt + 0.15 + 0.1 * j, tt + 0.5 + 0.1 * j))
            w = F('B7', 22).getlength(a) + 70
            if q > 0:
                rrect(C, x, 180, x + w, 236, 28, (255, 255, 255), .07 * clamp(q))
                check(C, x + 28, 208, 12, clamp(q), col)
                text(C, x + 50, 208, a, F('B7', 22), TXT, clamp(q))
            x += w + 12
        put(L, scale_alpha(C, ca * pl), 90, int(390 + (1 - ca) * 30))
    return L

def ui_builder(tb):
    L = new_screen()
    p0, dy0 = fx(tb, 0.0)
    topbar(L, 'New routine', p0, sub='Good night', room=None)
    def chip(x, y, s, col, t0, dark=True):
        q = e_back(prog(tb, t0, t0 + 0.45))
        f = F('B7', 28); w = f.getlength(s) + 56
        if q > 0:
            yy = y - (1 - clamp(q)) * 40
            rrect(L, x, yy, x + w, yy + 64, 32, col, clamp(q))
            text(L, x + w / 2, yy + 32, s, f, (10, 13, 17) if dark else TXT, clamp(q), al='m')
        return w
    pl, dyl = fx(tb, 0.15)
    for lab, y in (('When', 150), ('And', 250), ('Then', 350), ('Only', 556)):
        text(L, 48, y + 32 + dyl, lab, F('D8', 40), MUTED, pl)
    x = 200; x += chip(x, 150, "It's 10:30 PM", WARM, 0.5) + 14
    x = 200; x += chip(x, 250, 'Everyone is home', GREEN, 1.0) + 14
    x = 200
    acts = [('Turn off every light', 1.6), ('Lock the doors', 1.9), ('TVs to sleep', 2.2)]
    for s, t0 in acts: x += chip(x, 350, s, SURF2, t0, False) + 14
    x = 200; chip(x, 450, 'Bedroom: sleep timer, 30 min', SURF2, 2.5, False)
    x = 200; x += chip(x, 556, 'Weeknights', VIOLET, 3.0) + 14
    press = trap(tb, 4.6, 4.7, 4.75, 4.95)
    sp = e_out(prog(tb, 3.4, 3.9))
    bx0, by0, bx1, by1 = SW - 48 - 260, 610, SW - 48, 672
    rrect(L, bx0 + 3 * press, by0 + 3 * press, bx1 - 3 * press, by1 - 3 * press, 31, ACC, sp)
    text(L, (bx0 + bx1) / 2, (by0 + by1) / 2, 'Save routine', F('B7', 26), (10, 13, 17), sp, al='m')
    focus(L, bx0, by0, bx1, by1, 31, clamp(prog(tb, 4.0, 4.3)) * (1 - prog(tb, 5.0, 5.2)), TXT, 4, 6)
    tp = e_out(prog(tb, 5.0, 5.5))
    if tp > 0.01:
        T = layer(520, 84)
        rrect(T, 0, 0, 520, 84, 42, (35, 44, 56)); rrect_o(T, 0, 0, 520, 84, 42, 2, (70, 84, 102))
        check(T, 44, 42, 18, 1, GREEN); text(T, 80, 42, 'Saved. Runs tonight at 10:30.', F('B7', 24), TXT)
        put(L, scale_alpha(T, tp), int(SW - 48 - 520), int(28 + (1 - tp) * -30))
    return L

def ui_scenes(t):
    if t < 3.75: return ui_scene_grid(t)
    if t < 4.7: return reveal(ui_scene_grid(t), ui_scene_detail(t - 3.75), 187, 346, e_io(prog(t, 3.75, 4.65)) * 1500)
    if t < 10.9: return ui_scene_detail(t - 3.75)
    if t < 11.8: return slide(ui_scene_detail(t - 3.75), ui_routines(t - 10.9), prog(t, 10.9, 11.8))
    if t < 20.9: return ui_routines(t - 10.9)
    if t < 21.8: return slide(ui_routines(t - 10.9), ui_builder(t - 20.9), prog(t, 20.9, 21.8))
    return ui_builder(t - 20.9)

def sc_scenes(t, d):
    L = layer()
    heads(L, t, [(0.15, 10.8, 'One tap. Whole house.', 'Scenes set lights, blinds, sound and locks at once.', 112),
                 (11.2, 20.8, 'Or let the day run itself.', 'Routines by time, by sunset, or by who is home.', 104),
                 (21.2, 28.4, 'Build your own.', 'When this happens, do that. In plain words.', 112)])
    entry(L, ui_scenes(t), t)
    return L

# =============================================================== art mode
FRAMES = [('Black', INK), ('Oak', (150, 104, 62)), ('White', (236, 232, 224)), ('Navy', (32, 46, 80))]
_TH = None
def thumbs():
    global _TH
    if _TH is None:
        _TH = []
        for a in arts():
            im = a.resize((150, 73), Image.LANCZOS).copy(); clip_rr(im, 8); _TH.append(im)
    return _TH

def sc_art5(t, d):
    L = layer()
    dusk = e_io(prog(t, 19.6, 21.6))
    wall = lerpc((216, 210, 198), (84, 78, 74), dusk)
    L.paste(wall + (255,), (0, 0, W, H))
    # artwork selection
    asched = [(0, 0), (2.4, 1), (7.4, 2), (8.6, 3), (9.8, 4), (11.0, 5), (12.2, 0)]
    a_prev, a_cur, amix, _ = sched_at(asched, t, 0.9 if t < 5 else 0.6)
    A = arts()
    art = A[a_prev].copy()
    if amix > 0 and a_cur != a_prev: art.alpha_composite(scale_alpha(A[a_cur], amix))
    elif a_cur == a_prev: art = A[a_cur].copy()
    # frame color
    fsched = [(0, 0), (14.4, 1), (15.6, 2), (16.8, 3), (18.0, 1)]
    f_prev, f_cur, fmix, fidx = sched_at(fsched, t, 0.5)
    fcol = lerpc(FRAMES[f_prev][1], FRAMES[f_cur][1], fmix)
    g = frame_dyn(fcol)
    g.alpha_composite(art, (FM + 18 + 64, FM + 18 + 64))
    if dusk > 0: g = dim_rgb(g, 0.3 * dusk)
    shrink = e_io(prog(t, 5.3, 6.4))
    sc = lerp(1.07 - 0.07 * e_out(prog(t, 0.2, 4.8)), 0.62, shrink)
    cy = lerp(640, 590, shrink)
    p = e_out(prog(t, 0.5, 1.5))
    im = g.resize((int(g.width * sc), int(g.height * sc)), Image.BILINEAR)
    put(L, scale_alpha(im, clamp(p * 1.2)), int(W / 2 - im.width / 2), int(cy - im.height / 2 + (1 - p) * 40))
    # phase A chip
    cw = [F('B7', 26).getlength('Painting') + 64, F('B7', 26).getlength('Your photo') + 64]
    ca = clamp(prog(t, 1.4, 1.9)) * (1 - prog(t, 5.0, 5.4)); sw = prog(t, 2.5, 3.1)
    wd = lerp(cw[0], cw[1], sw)
    if ca > 0:
        rrect(L, W / 2 - wd / 2, 960, W / 2 + wd / 2, 1016, 28, (13, 16, 20), .9 * ca)
        text(L, W / 2, 988, 'Painting', F('B7', 26), TXT, ca * (1 - sw), al='m'); text(L, W / 2, 988, 'Your photo', F('B7', 26), TXT, ca * sw, al='m')
    # phase B: thumbnails
    ta = e_out(prog(t, 6.0, 6.7)) * (1 - e_in(prog(t, 12.9, 13.4)))
    if ta > 0.01:
        TH = thumbs(); n = len(TH); gap = 22; tw_ = 150 * n + gap * (n - 1); x0 = W / 2 - tw_ / 2; y0 = 900
        rrect(L, x0 - 24, y0 - 22, x0 + tw_ + 24, y0 + 73 + 22, 30, (13, 16, 20), .85 * ta)
        tsched = [(0, 1), (7.4, 2), (8.6, 3), (9.8, 4), (11.0, 5), (12.2, 0)]
        rects = [(x0 + k * (150 + gap), y0, x0 + k * (150 + gap) + 150, y0 + 73) for k in range(n)]
        for k in range(n):
            q = e_out(prog(t, 6.1 + 0.06 * k, 6.6 + 0.06 * k))
            put(L, scale_alpha(TH[k], ta * q), int(rects[k][0]), int(rects[k][1] + (1 - q) * 20))
        fr = focus_path([(tt, rects[v]) for tt, v in tsched], t, 0.35)
        focus(L, *fr, 8, ta, (255, 255, 255), 4, 6)
        lab = ['Dusk Domes', 'Lake Day (your photo)', 'Ember Hills', 'Violet Hour', 'Harbor Study', 'Night Garden']
        cur = 0
        for tt, v in tsched:
            if t >= tt: cur = v
        text(L, W / 2, y0 - 50, lab[cur], F('B7', 26), INK, ta * .9, al='m')
    # phase C: frame chips
    fa = e_out(prog(t, 13.4, 14.0)) * (1 - e_in(prog(t, 19.2, 19.7)))
    if fa > 0.01:
        f = F('B7', 26); ws = [f.getlength(nm) + 92 for nm, c in FRAMES]; gap = 16
        tot = sum(ws) + gap * 3; x = W / 2 - tot / 2; y0 = 930
        rects = []
        for k, (nm, c) in enumerate(FRAMES):
            rects.append((x, y0, x + ws[k], y0 + 60)); x += ws[k] + gap
        fr = focus_path([(tt, rects[v]) for tt, v in fsched], t, 0.35)
        rrect(L, fr[0], fr[1], fr[2], fr[3], 30, (13, 16, 20), .92 * fa)
        for k, (nm, c) in enumerate(FRAMES):
            x0, y0_, x1, y1 = rects[k]
            inside = fr[0] - 4 <= (x0 + x1) / 2 <= fr[2] + 4
            if not inside: rrect_o(L, x0, y0_, x1, y1, 30, 2, (120, 108, 96), fa)
            circle(L, x0 + 34, y0_ + 30, 13, c, fa); ring(L, x0 + 34, y0_ + 30, 13, 2, (120, 108, 96), fa)
            text(L, x0 + 58, y0_ + 30, nm, f, TXT if inside else INK, fa)
    # phase D: dusk pill
    da = e_out(prog(t, 20.4, 21.0))
    if da > 0.01:
        f = F('B7', 26); s = 'Room is dim  ·  Art eased to 40%'
        w = f.getlength(s) + 96
        rrect(L, W / 2 - w / 2, 930, W / 2 + w / 2, 990, 30, (13, 16, 20), .9 * da)
        moon(L, W / 2 - w / 2 + 40, 960, 13, WARM, (13, 16, 20), da)
        text(L, W / 2 - w / 2 + 70, 960, s, f, TXT, da)
    hc = lerpc(INK, TXT, dusk); sc_ = lerpc((110, 96, 84), (200, 192, 180), dusk)
    heads(L, t, [(0.7, 5.3, 'A frame, not a screen.', None, 96),
                 (5.7, 12.9, 'Hang anything.', 'Curated collections, or your own photos.', 96),
                 (13.3, 19.4, 'Dress the frame.', 'Frame styles, drawn to scale.', 96),
                 (19.8, 24.4, 'It dims with the room.', 'An ambient sensor keeps it gallery-right.', 96)], hc, sc_)
    r = e_io(prog(t, 0, 1.1)) * 1250
    if r < 1249:
        m = Image.new('L', (W // 4, H // 4), 0); ImageDraw.Draw(m).ellipse((W / 8 - r / 4, H / 8 - r / 4, W / 8 + r / 4, H / 8 + r / 4), fill=255)
        m = m.filter(ImageFilter.GaussianBlur(2)).resize((W, H), Image.BILINEAR)
        L.putalpha(ImageChops.multiply(L.getchannel('A'), m))
    return L

# =============================================================== lights & cameras
LROOMS = [('Living Room', 5), ('Kitchen', 3), ('Bedroom', 2), ('Patio', 2), ('Hallway', 1)]
LIGHTS = [('Floor lamp', 100, 45), ('Ceiling', 80, 30), ('Backlight', 100, 60), ('Reading lamp', 60, 50), ('Shelf strip', 40, 20)]

def ui_lights(t):
    L = new_screen()
    p0, dy0 = fx(t, 0.0)
    warm = lerp(0.3, 0.88, e_io(prog(t, 6.2, 9.6)))
    topbar(L, 'Lights', p0, sub='5 on in Living Room', room=None)
    ps, dys = fx(t, 0.1)
    rrect(L, 48, 120 + dys, 330, 672 + dys, 24, SURF, ps)
    for i, (nm, n) in enumerate(LROOMS):
        y = 140 + i * 70 + dys
        if i == 0: rrect(L, 62, y, 316, y + 56, 16, SURF2, ps)
        text(L, 84, y + 28, nm, F('B7', 22), TXT if i == 0 else MUTED, ps)
        circle(L, 290, y + 28, 15, (40, 48, 60), ps); text(L, 290, y + 28, str(n), F('B7', 16), TXT, ps, al='m')
    text(L, 84, 560 + dys, 'All lights', F('B7', 22), MUTED, ps); toggle(L, 236, 540 + dys, 1, ps)
    text(L, 84, 620 + dys, 'Adaptive', F('B7', 22), MUTED, ps); toggle(L, 236, 600 + dys, e_io(prog(t, 9.8, 10.2)), ps)
    pc, dyc = fx(t, 0.2)
    chips = ['Bright', 'Relax', 'Movie', 'Night', 'Focus']; x = 360; crect = []
    for c in chips:
        w = F('B7', 22).getlength(c) + 44; crect.append((x, 122, x + w, 166)); x += w + 12
    sel = sched_at([(0, 0), (1.6, 1)], t, 0.35)
    sr = rect_lerp(crect[sel[0]], crect[sel[1]], sel[2])
    rrect(L, sr[0], sr[1] + dyc, sr[2], sr[3] + dyc, 22, WARM, pc)
    for k, c in enumerate(chips):
        x0, y0, x1, y1 = crect[k]
        inside = sr[0] - 4 <= (x0 + x1) / 2 <= sr[2] + 4
        if not inside: rrect(L, x0, y0 + dyc, x1, y1 + dyc, 22, SURF, pc)
        text(L, (x0 + x1) / 2, (y0 + y1) / 2 + dyc, c, F('B7', 22), (10, 13, 17) if inside else TXT, pc, al='m')
    scene = e_io(prog(t, 1.7, 2.7))
    drag = e_io(prog(t, 4.1, 5.3))
    for i, (nm, v0, v1) in enumerate(LIGHTS):
        pp, dd = fx(t, 0.3 + 0.07 * i, 0.7)
        y = 196 + i * 78 + dd
        v = lerp(v0, v1, scene)
        if i == 0: v = lerp(v, 75, drag)
        bc = lerpc((225, 236, 255), (255, 172, 84), warm)
        rrect(L, 360, y, 1232, y + 66, 18, SURF, pp)
        circle(L, 400, y + 33, 20, lerpc((40, 48, 60), bc, v / 100), pp)
        if v > 1: circle(L, 400, y + 33, 30, bc, .15 * v / 100 * pp)
        text(L, 436, y + 33, nm, F('B7', 22), TXT, pp)
        slider(L, 640, 1130, y + 33, v / 100, pp, bc, True, 10)
        text(L, 1206, y + 33, '%d%%' % round(v), F('B7', 20), MUTED, pp, al='r')
    pw, dyw = fx(t, 0.7)
    y = 600 + dyw
    rrect(L, 360, y, 1232, y + 72, 18, SURF, pw)
    text(L, 386, y + 36, 'Warmth', F('B7', 22), TXT, pw)
    gx0, gx1 = 640, 1130
    nseg = 28
    for k in range(nseg):
        c = lerpc((215, 230, 255), (255, 160, 70), k / (nseg - 1))
        xa = gx0 + (gx1 - gx0) * k / nseg; xb = gx0 + (gx1 - gx0) * (k + 1) / nseg
        rrect(L, xa, y + 30, xb + 1, y + 42, 0 if 0 < k < nseg - 1 else 6, c, pw)
    circle(L, lerp(gx0, gx1, warm), y + 36, 15, TXT, pw); circle(L, lerp(gx0, gx1, warm), y + 36, 9, lerpc((215, 230, 255), (255, 160, 70), warm), pw)
    text(L, 1206, y + 36, '%dK' % int(round(lerp(6500, 2200, warm) / 100) * 100), F('B7', 20), MUTED, pw, al='r')
    fsched = [(0, (360, 196, 1232, 262)), (3.6, (360, 196, 1232, 262)), (6.0, (360, 600, 1232, 672)), (9.6, (62, 520, 316, 640))]
    fr = focus_path(fsched, t, 0.35)
    fa = clamp(prog(t, 3.5, 3.8)) * (1 - prog(t, 10.2, 10.5))
    focus(L, *fr, 18, fa, TXT, 3, 5)
    return L

CAMS = [('Front Door', 'door'), ('Driveway', 'driveway'), ('Backyard', 'backyard'), ('Garage', 'garage')]
def ui_cams(tc):
    L = new_screen()
    p0, dy0 = fx(tc, 0.0)
    topbar(L, 'Cameras', p0, sub='4 live  ·  2 events today', room=None)
    rects = [(48, 118, 632, 404), (648, 118, 1232, 404), (48, 420, 632, 706), (648, 420, 1232, 706)]
    ex = e_io(prog(tc, 3.0, 3.9))
    for i, (nm, kd) in enumerate(CAMS):
        x0, y0, x1, y1 = rects[i]
        pp, dd = fx(tc, 0.1 + 0.08 * i, 0.7)
        a = pp * (1 - ex if i else 1)
        if i == 0 and ex > 0: continue
        if a <= 0.01: continue
        C = feed_img(x1 - x0, y1 - y0, kd, tc + 10 * i, pp=e_out(prog(tc, 0.5, 4)), r=18)
        feed_badges(C, tc + i, nm)
        put(L, scale_alpha(C, a), int(x0), int(y0 + dd))
    fa = clamp(prog(tc, 1.4, 1.7)) * (1 - ex)
    focus(L, *rects[0], 18, fa, TXT, 4, 6)
    if ex > 0:
        r0 = rects[0]; r1 = (0, 0, SW, SH)
        rr = rect_lerp(r0, r1, ex)
        w = int(rr[2] - rr[0]); h = int(rr[3] - rr[1])
        rec = tc >= 7.6 and tc < 10.6
        pkg = e_io(prog(tc, 7.6, 8.0)) * (1 - e_io(prog(tc, 10.6, 11.0)))
        C = feed_img(w, h, 'door', tc, pp=e_out(prog(tc, 4.0, 7.0)) * (1 - e_io(prog(tc, 10.8, 12.6))), r=int(lerp(18, 0, ex)), pkg=e_io(prog(tc, 10.6, 11.0)))
        if pkg > 0:
            C2 = feed_img(w, h, 'porch', tc, r=int(lerp(18, 0, ex)))
            C.alpha_composite(scale_alpha(C2, pkg))
        if ex > 0.98:
            s = 1.0
            if not rec:
                rrect(C, 24, 24, 112, 62, 19, (10, 13, 17), .8); circle(C, 48, 43, 7, CORAL, .5 + .5 * abs(math.sin(tc * 5))); text(C, 64, 43, 'Live', F('B7', 20), TXT)
            else:
                rrect(C, 24, 24, 250, 62, 19, (10, 13, 17), .8); circle(C, 48, 43, 7, WARM); text(C, 64, 43, 'Recorded 1:40 PM', F('B7', 20), TXT)
            text(C, 24, 104, 'Front Door', F('D8', 40), TXT)
            # event timeline
            ta = e_out(prog(tc, 4.6, 5.2))
            rrect(C, 24, h - 120, w - 24, h - 24, 24, (10, 13, 17), .78 * ta)
            bx0, bx1, by = 64, w - 64, h - 60
            rrect(C, bx0, by - 3, bx1, by + 3, 3, (90, 100, 114), ta)
            evs = [(0.18, 'Car', VIOLET), (0.46, 'Package', WARM), (0.83, 'Person', ACC)]
            for u, lab, col in evs:
                ex_ = lerp(bx0, bx1, u)
                circle(C, ex_, by, 9, col, ta)
                text(C, ex_, by - 30, lab, F('B7', 18), col, ta, al='m')
            text(C, bx1, by - 30, 'Now', F('B7', 18), TXT, ta, al='r')
            hu = lerp(1.0, 0.46, e_io(prog(tc, 6.8, 7.6)))
            hu = lerp(hu, 1.0, e_io(prog(tc, 10.2, 10.8)))
            hx = lerp(bx0, bx1, hu)
            rrect(C, hx - 3, by - 20, hx + 3, by + 20, 3, TXT, ta)
            za = e_out(prog(tc, 11.3, 11.9))
            if za > 0:
                zx0, zy0, zx1, zy1 = w * .54, h * .58, w * .84, h * .825
                for k in range(0, int((zx1 - zx0) // 28) + 1):
                    xa = zx0 + k * 28
                    line(C, [(xa, zy0), (min(xa + 14, zx1), zy0)], 4, ACC, za); line(C, [(xa, zy1), (min(xa + 14, zx1), zy1)], 4, ACC, za)
                for k in range(0, int((zy1 - zy0) // 28) + 1):
                    ya = zy0 + k * 28
                    line(C, [(zx0, ya), (zx0, min(ya + 14, zy1))], 4, ACC, za); line(C, [(zx1, ya), (zx1, min(ya + 14, zy1))], 4, ACC, za)
                rrect(C, zx0, zy0 - 52, zx0 + 230, zy0 - 12, 20, ACC, za)
                text(C, zx0 + 115, zy0 - 32, 'Package zone', F('B7', 20), (10, 13, 17), za, al='m')
        put(L, C, int(rr[0]), int(rr[1]))
    return L

def ui_lightcam(t):
    if t < 10.9: return ui_lights(t)
    if t < 11.8: return slide(ui_lights(t), ui_cams(t - 10.9), prog(t, 10.9, 11.8))
    return ui_cams(t - 10.9)

def sc_lights(t, d):
    L = layer()
    heads(L, t, [(0.15, 10.8, 'Every light. Every lamp.', 'Scenes, brightness and warmth, room by room.', 108),
                 (11.2, 24.4, 'See it all.', 'Live cameras, event history and zones.', 118)])
    entry(L, ui_lightcam(t), t)
    return L

# =============================================================== doorbell & packages
_STARS = [((k * 389) % 1280, (k * 211) % 720, 1 + (k % 3)) for k in range(70)]
def ui_movie(mt):
    L = new_screen((8, 12, 24))
    for x, y, s in _STARS:
        xx = (x - mt * 6 * s) % SW
        circle(L, xx, y, s * .9, (255, 255, 255), .35 + .15 * s)
    circle(L, 820, 420, 300, (60, 90, 140), .25)
    circle(L, 820, 420, 210, (150, 110, 80))
    circle(L, 760, 360, 210, (8, 12, 24), .35)
    a = mt * 0.25
    ring(L, 820, 420, 330, 3, (255, 230, 200), .25)
    sx = 820 + math.cos(a) * 330; sy = 420 + math.sin(a) * 330 * .35
    circle(L, sx, sy, 10, TXT)
    return L

def ui_doorbell(t):
    paused = e_io(prog(t, 3.3, 3.8)) * (1 - e_io(prog(t, 10.2, 10.8)))
    mt = t if t < 3.3 else (3.3 if t < 10.2 else 3.3 + (t - 10.2))
    L = ui_movie(mt)
    if paused > 0.01:
        dimmed(L, 0.45 * paused)
        circle(L, SW / 2, SH / 2 - 20, 56, (255, 255, 255), .16 * paused); pause_g(L, SW / 2, SH / 2 - 20, 34, TXT, paused)
        text(L, SW / 2, SH / 2 + 66, 'Paused for the doorbell', F('B7', 22), TXT, paused, al='m')
        rrect(L, 48, SH - 70, SW - 48, SH - 62, 4, (255, 255, 255), .25 * paused); rrect(L, 48, SH - 70, 48 + (SW - 96) * .57, SH - 62, 4, ACC, paused)
        text(L, 48, SH - 104, 'The Long Orbit', F('B7', 24), TXT, paused); text(L, SW - 48, SH - 104, '1:12:45 / 2:08:00', F('B7', 20), MUTED, paused, al='r')
    # doorbell card
    cin = e_out(prog(t, 3.0, 3.5)); cout = e_in(prog(t, 10.0, 10.5))
    if cin > 0 and cout < 1:
        C = layer(470, 420)
        rrect(C, 0, 0, 470, 420, 28, (23, 28, 35)); rrect_o(C, 0, 0, 470, 420, 28, 2, (58, 70, 86))
        circle(C, 46, 44, 20, WARM); bell_g(C, 46, 42, 14, (10, 13, 17))
        text(C, 78, 44, 'Front Door', F('B7', 24), TXT); text(C, 444, 46, 'now', F('B5', 18), MUTED, al='r')
        fd = feed_img(422, 214, 'door', t, pp=e_out(prog(t, 3.0, 6.5)), r=16)
        feed_badges(fd, t, 'Doorbell')
        put(C, fd, 24, 80)
        talking = t >= 6.1 and t < 9.6
        if not talking:
            text(C, 24, 324, 'Someone is at the door', F('B7', 22), TXT)
        else:
            mic(C, 40, 324, 22, ACC)
            for k in range(14):
                amp = 0.3 + 0.7 * abs(math.sin(t * 8 + k * 0.9)) * abs(math.sin(t * 3 + k))
                rrect(C, 72 + k * 12, 324 - 16 * amp, 78 + k * 12, 324 + 16 * amp, 3, ACC)
            text(C, 260, 324, 'Talking…', F('B7', 22), TXT)
        b1 = (24, 352, 236, 400); b2 = (248, 352, 446, 400)
        rrect(C, *b1, 24, ACC if not talking else CORAL); text(C, (b1[0] + b1[2]) / 2, 376, 'Talk' if not talking else 'End talk', F('B7', 22), (10, 13, 17), al='m')
        rrect(C, *b2, 24, SURF2); text(C, (b2[0] + b2[2]) / 2, 376, 'Dismiss', F('B7', 22), TXT, al='m')
        fr = focus_path([(0, b1), (9.3, b2)], t, 0.3)
        focus(C, *fr, 24, clamp(prog(t, 4.6, 4.9)), TXT, 3, 4)
        put(L, scale_alpha(C, cin * (1 - cout)), int(SW - 48 - 470 + (1 - cin) * 300 + cout * 520), 48)
    # package toast
    pin = e_out(prog(t, 11.8, 12.3)); pout = e_in(prog(t, 14.6, 15.1))
    if pin > 0 and pout < 1:
        C = layer(560, 130)
        rrect(C, 0, 0, 560, 130, 26, (23, 28, 35)); rrect_o(C, 0, 0, 560, 130, 26, 2, (58, 70, 86))
        put(C, feed_img(160, 98, 'porch', t, r=14), 16, 16)
        text(C, 196, 40, 'Package delivered', F('B7', 24), TXT)
        text(C, 196, 72, 'Front porch  ·  2:14 PM', F('B5', 20), MUTED)
        box_g(C, 214, 104, 12, WARM); text(C, 236, 104, 'Movie kept playing', F('B7', 18), WARM)
        put(L, scale_alpha(C, pin * (1 - pout)), int(SW - 48 - 560 + (1 - pin) * 300 + pout * 600), 48)
    # settings
    sp = e_out(prog(t, 15.4, 16.0))
    if sp > 0.01:
        dimmed(L, 0.6 * sp)
        P = layer(760, 470)
        rrect(P, 0, 0, 760, 470, 28, (23, 28, 35)); rrect_o(P, 0, 0, 760, 470, 28, 2, (58, 70, 86))
        text(P, 40, 58, 'While you watch', F('D8', 38), TXT); text(P, 720, 60, 'Living Room', F('B7', 20), MUTED, al='r')
        rows = [('bell', 'Doorbell', 'Pause and show'), ('box', 'Packages', 'Quiet banner'), ('cam', 'Motion', None), ('moon', 'Quiet hours', '10 PM – 7 AM')]
        mot = e_io(prog(t, 17.6, 18.0))
        for i, (ic, nm, val) in enumerate(rows):
            y = 110 + i * 86
            rrect(P, 30, y, 730, y + 72, 18, SURF2)
            cx, cy = 74, y + 36
            if ic == 'bell': bell_g(P, cx, cy - 2, 14, TXT)
            elif ic == 'box': box_g(P, cx, cy, 13, TXT)
            elif ic == 'cam': glyph(P, 'cam', cx, cy, 14, TXT)
            else: moon(P, cx, cy, 13, TXT, SURF2)
            text(P, 110, cy, nm, F('B7', 24), TXT)
            if val is None:
                v0 = 'Off'; v1 = 'People only'
                text(P, 700, cy, v0, F('B7', 22), MUTED, 1 - mot, al='r'); text(P, 700, cy, v1, F('B7', 22), ACC, mot, al='r')
            else:
                text(P, 700, cy, val, F('B7', 22), ACC, al='r')
        fr = focus_path([(0, (30, 110, 730, 182)), (16.8, (30, 196, 730, 268)), (17.3, (30, 282, 730, 354)), (18.4, (30, 368, 730, 440))], t, 0.3)
        focus(P, *fr, 18, clamp(prog(t, 16.2, 16.5)), TXT, 3, 5)
        popimg(L, P, SW / 2, SH / 2, sp, 0.92)
    return L

def sc_doorbell(t, d):
    L = layer()
    heads(L, t, [(0.15, 15.0, 'Nothing slips by.', 'The doorbell pauses your movie. Packages just say hi.', 118),
                 (15.3, 20.4, 'On your terms.', 'Pick what interrupts you, and when.', 118)])
    entry(L, ui_doorbell(t), t)
    return L

# =============================================================== speakers
SROOMS = [('Patio', (160, 360, 744, 900)), ('Living Room', (760, 360, 1290, 900)), ('Kitchen', (1306, 360, 1760, 620)), ('Bedroom', (1306, 636, 1760, 900))]
SNODES = [((1010, 800), 'Soundbar', 1.9, 1), ((1560, 500), 'Kitchen', 2.6, 2), ((1560, 770), 'Bedroom', 3.3, 3), ((452, 650), 'Patio', 4.0, 0)]
PATH = [(0.0, (1040, 610)), (12.0, (1040, 610)), (14.0, (1500, 450)), (15.4, (1500, 450)), (17.0, (1040, 560)), (18.6, (500, 520))]

def _person(t):
    pts = PATH
    for k in range(1, len(pts)):
        if t < pts[k][0]:
            q = e_io(prog(t, pts[k - 1][0], pts[k][0]))
            return lerp(pts[k - 1][1][0], pts[k][1][0], q), lerp(pts[k - 1][1][1], pts[k][1][1], q)
    return pts[-1][1]

def _room_of(x, y):
    for i, (nm, (x0, y0, x1, y1)) in enumerate(SROOMS):
        if x0 <= x <= x1 and y0 <= y <= y1: return i
    return 1

def sc_speakers(t, d):
    L = layer()
    heads(L, t, [(0.15, 10.6, 'Sound follows you.', "Group speakers. Play what's on screen.", 118),
                 (10.9, 22.4, 'Music that moves with you.', 'Walk room to room. It follows.', 104)])
    follow = e_io(prog(t, 10.6, 11.6))
    px, py = _person(t)
    pr = _room_of(px, py)
    for i, (nm, (x0, y0, x1, y1)) in enumerate(SROOMS):
        p = e_out(prog(t, 0.3 + .12 * i, 1.0 + .12 * i)); dy = (1 - p) * 30
        hl = follow * (1.0 if i == pr else 0.0)
        rrect(L, x0, y0 + dy, x1, y1 + dy, 22, lerpc((16, 21, 28), (24, 34, 48), hl), p)
        rrect_o(L, x0, y0 + dy, x1, y1 + dy, 22, 3, lerpc((50, 62, 78), ACC, hl * .7), p)
        text(L, x0 + 28, y0 + 38 + dy, nm, F('B7', 26), lerpc(MUTED, TXT, hl), p)
        if i == 0:
            for k in range(5):
                line(L, [(x0 + 40 + k * 110, y1 - 40 + dy), (x0 + 90 + k * 110, y1 - 40 + dy)], 3, (40, 52, 66), p)
    tvp = e_out(prog(t, 0.8, 1.3)); on = e_io(prog(t, 1.2, 1.6)) * (1 - follow * .6)
    rrect(L, 800, 560, 824, 700, 6, lerpc(SURF2, ACC, on), tvp); text(L, 812, 530, 'TV', F('B7', 24), TXT, tvp, al='m')
    if t > 1.3 and follow < 1:
        for k in range(3):
            ph = ((t - 1.3) * 0.5 + k / 3.0) % 1.0
            ring(L, 824, 630, 40 + ph * 210, 4, ACC, (1 - ph) * .5 * (1 - follow))
    for (nx, ny), nm, ta, ri in SNODES:
        lp = prog(t, ta - 0.6, ta)
        la = 1 - follow
        if lp > 0 and la > 0.01:
            x0, y0 = 824, 630; x1, y1 = nx, ny
            ln = math.hypot(x1 - x0, y1 - y0); end = ln * e_io(lp); pos = -(t * 90) % 26
            s = pos - 26
            while s < end:
                a0 = max(s, 0); a1 = min(s + 14, end)
                if a1 > a0:
                    u0 = a0 / ln; u1 = a1 / ln
                    line(L, [(lerp(x0, x1, u0), lerp(y0, y1, u0)), (lerp(x0, x1, u1), lerp(y0, y1, u1))], 4, ACC, .8 * la)
                s += 26
        act = e_out(prog(t, ta, ta + 0.4)); base = e_out(prog(t, 0.9, 1.4))
        here = 1.0 if ri == pr else 0.0
        if follow > 0:
            # smooth switching driven by distance
            dist = math.hypot(px - nx, py - ny)
            here = clamp(1.3 - dist / 300)
            act = lerp(act, here, follow)
        circle(L, nx, ny, 34, SURF2, base); ring(L, nx, ny, 34, 3, (58, 70, 86), base)
        if act > 0.02:
            circle(L, nx, ny, 34 * clamp(act), ACC, clamp(act))
            glyph(L, 'speaker', nx, ny, 15, (10, 13, 17), clamp(act))
            if follow > 0 and act > 0.3:
                for k in range(2):
                    ph = ((t * 0.8) + k / 2.0) % 1.0
                    ring(L, nx, ny, 40 + ph * 90, 3, ACC, (1 - ph) * .5 * act)
            elif follow <= 0:
                q = prog(t, ta, ta + 0.9); ring(L, nx, ny, 34 + q * 44, 3, ACC, (1 - q) * .6)
        else:
            glyph(L, 'speaker', nx, ny, 15, MUTED, base)
        vol = int(round((40 + 15 * (ri % 3)) * (act if follow > 0 else 1)))
        text(L, nx, ny + 66, nm, F('B7', 22), TXT if act > .5 else MUTED, base, al='m')
        if base > 0: text(L, nx, ny + 94, '%d%%' % vol, F('B7', 18), ACC if act > .5 else (90, 100, 114), base * clamp(prog(t, ta, ta + .5)), al='m')
    # person
    pa = e_out(prog(t, 11.2, 11.8))
    if pa > 0:
        ring(L, px, py, 30, 3, TXT, pa * .5)
        avatar(L, px, py, 24, (43, 76, 126), 'M', pa)
    # now playing pill
    npa = e_out(prog(t, 4.8, 5.4))
    if npa > 0:
        mus = follow
        f = F('B7', 24)
        s0 = "Playing from TV  ·  The Long Orbit"; s1 = 'Evening mix  ·  following Matt'
        w = lerp(f.getlength(s0), f.getlength(s1), mus) + 110
        x0 = W - 160 - w; y0 = 228
        rrect(L, x0, y0, x0 + w, y0 + 60, 30, SURF2, npa)
        for k in range(4):
            amp = 0.35 + 0.65 * abs(math.sin(t * 7 + k * 1.3))
            rrect(L, x0 + 28 + k * 11, y0 + 30 - 14 * amp, x0 + 34 + k * 11, y0 + 30 + 14 * amp, 3, ACC, npa)
        text(L, x0 + 86, y0 + 30, s0, f, TXT, npa * (1 - mus)); text(L, x0 + 86, y0 + 30, s1, f, TXT, npa * mus)
    return L

# =============================================================== bedroom
def sc_bedroom(t, d):
    L = layer()
    stop = t >= 12.0
    if 1.7 < t < 12.2:
        for k in range(3):
            ph = ((t - 1.7) / 2.2 + k / 3.0) % 1.0
            ring(L, W / 2, 470, 260 + ph * 700, 5, WARM, (1 - ph) * .4 * (1 - e_io(prog(t, 11.9, 12.2))))
    for a, b, s in ((0.3, 8.6, 'Bedroom adds an alarm.'), (8.9, 13.8, 'Snooze, or start the day.'), (14.2, 20.4, 'And it eases you to sleep.')):
        if a - 0.01 <= t < b + 0.01:
            p = e_out(prog(t, a, a + 0.7)) * (1 - e_io(prog(t, b - 0.4, b)))
            text(L, W / 2, 210, s, F('D8', 72), TXT, p, al='m', blur=(1 - e_out(prog(t, a, a + 0.7))) * 10)
    ck = 1 - e_io(prog(t, 13.6, 14.2))
    if ck > 0:
        sw = t >= 1.7
        size = 400 * (1 + 0.12 * (1 - e_out(prog(t, 1.7, 2.3)))) if sw else 400
        f = F('D8', size); s = '6:30' if sw else '6:29'
        if t >= 12.4: s = '6:34'
        am = F('B7', 96); tot = f.getlength(s) + 30 + am.getlength('AM')
        ap = e_out(prog(t, 0.2, 0.9)) * ck
        x0 = W / 2 - tot / 2
        text(L, x0, 600, s, f, TXT, ap, ay='b', sp=-6)
        text(L, x0 + f.getlength(s) + 30, 600, 'AM', am, MUTED, ap, ay='b')
    # phase A pills
    pa = 1 - e_io(prog(t, 8.4, 8.9))
    p1 = e_back(prog(t, 0.9, 1.5)); p2 = e_back(prog(t, 1.1, 1.7))
    w1 = F('B7', 34).getlength('Alarm on') + 64; w2 = F('B7', 34).getlength('Weekdays') + 64; gap = 20
    xs = W / 2 - (w1 + w2 + gap) / 2
    if p1 > 0 and pa > 0:
        rrect(L, xs, 700, xs + w1, 768, 34, WARM, clamp(p1) * pa); text(L, xs + w1 / 2, 734, 'Alarm on', F('B7', 34), (10, 13, 17), clamp(p1) * pa, al='m')
    if p2 > 0 and pa > 0:
        rrect(L, xs + w1 + gap, 700, xs + w1 + gap + w2, 768, 34, SURF2, clamp(p2) * pa); text(L, xs + w1 + gap + w2 / 2, 734, 'Weekdays', F('B7', 34), TXT, clamp(p2) * pa, al='m')
    # sunrise bar
    sa = e_out(prog(t, 2.8, 3.4)) * (1 - e_io(prog(t, 13.4, 14.0)))
    if sa > 0:
        v = e_io(prog(t, 3.0, 9.0))
        text(L, W / 2 - 340, 850, 'Sunrise lights', F('B7', 28), TXT, sa)
        slider(L, W / 2 - 100, W / 2 + 260, 850, v, sa, WARM, False, 12)
        text(L, W / 2 + 340, 850, '%d%%' % round(v * 100), F('B7', 28), WARM, sa, al='r')
    # phase B buttons
    pb = e_out(prog(t, 8.9, 9.5)) * (1 - e_io(prog(t, 12.0, 12.4)))
    if pb > 0:
        f = F('B7', 34)
        b1 = (W / 2 - 360, 700, W / 2 - 20, 768); b2 = (W / 2 + 20, 700, W / 2 + 360, 768)
        rrect(L, *b1, 34, SURF2, pb); text(L, (b1[0] + b1[2]) / 2, 734, 'Snooze 9 min', f, TXT, pb, al='m')
        press = trap(t, 11.7, 11.8, 11.85, 12.05)
        rrect(L, b2[0] + 4 * press, b2[1] + 4 * press, b2[2] - 4 * press, b2[3] - 4 * press, 34, WARM, pb); text(L, (b2[0] + b2[2]) / 2, 734, 'Stop', f, (10, 13, 17), pb, al='m')
        fr = focus_path([(0, b1), (10.8, b2)], t, 0.3)
        focus(L, *fr, 34, pb * clamp(prog(t, 9.7, 10.0)), TXT, 4, 7)
    gm = e_out(prog(t, 12.2, 12.8)) * (1 - e_io(prog(t, 13.4, 14.0)))
    if gm > 0:
        Hm = layer(W, 200)
        words(Hm, W / 2, 100, 'Good morning, Matt.', F('D8', 56), 12.2, t, WARM)
        put(L, scale_alpha(Hm, 1 - e_io(prog(t, 13.4, 14.0))), 0, 634)
        text(L, W / 2, 800, '64° and clear  ·  First meeting at 9:30  ·  Coffee is on', F('B5', 30), MUTED, gm, al='m')
    # phase C sleep timer
    pc = e_out(prog(t, 14.2, 15.0))
    if pc > 0:
        cx, cy, R = W / 2, 560, 230
        fade = 1 - e_io(prog(t, 18.6, 19.8)) * .85
        rem = 1 - e_in(prog(t, 15.0, 19.4))
        ring(L, cx, cy, R, 14, (40, 48, 60), pc * fade)
        if rem > 0.002:
            line(L, arc_pts(cx, cy, R, -90, -90 + 360 * rem, max(4, int(80 * rem))), 14, VIOLET, pc * fade)
        secs = int(round(1800 * rem)); s = '%d:%02d' % (secs // 60, secs % 60)
        text(L, cx, cy - 10, s, F('D8', 130), TXT, pc * fade, al='m')
        text(L, cx, cy + 92, 'Sleep timer', F('B7', 30), MUTED, pc * fade, al='m')
        chips = ['Screen dims', 'Lights fade', 'Speakers fade', 'Doors locked']
        f = F('B7', 26); ws = [f.getlength(c) + 50 for c in chips]; tot = sum(ws) + 14 * 3; x = W / 2 - tot / 2
        for k, c in enumerate(chips):
            q = e_back(prog(t, 15.3 + .2 * k, 15.8 + .2 * k))
            if q > 0: rrect(L, x, 860, x + ws[k], 914, 27, SURF2, clamp(q) * fade); text(L, x + ws[k] / 2, 887, c, f, TXT, clamp(q) * fade, al='m')
            x += ws[k] + 14
        za = e_out(prog(t, 18.6, 19.4))
        if za > 0:
            for k in range(3):
                zz = (t * 0.6 + k / 3) % 1
                text(L, cx + 240 + k * 30 + zz * 20, cy - 220 - zz * 80 - k * 40, 'z', F('D8', 40 + k * 10), VIOLET, za * (1 - zz) * .8, al='m')
    return L
