from common5 import *

# =============================================================== intro / title
def sc_intro5(t, d):
    L = layer()
    f = F('D8', 108)
    words(L, W / 2, 470, 'Every room is different.', f, 0.3, t, lerpc(TXT, (86, 94, 106), e_io(prog(t, 2.1, 2.7))))
    words(L, W / 2, 610, 'Your TV should know that.', f, 2.1, t, TXT)
    return L

def sc_title5(t, d):
    L = layer()
    s = 1 + 0.05 * e_in(prog(t, 3.4, 4.6))
    letters(L, W / 2, 470, 'Haven', F('D8', int(380 * s)), 0.3, t, TXT)
    sp = e_out(prog(t, 1.5, 2.2))
    text(L, W / 2, 700, "Your home's companion.", F('B5', 58), MUTED, sp, al='m', blur=(1 - sp) * 10)
    for i in range(3):
        p = e_back(prog(t, 2.2 + i * .12, 2.8 + i * .12))
        circle(L, W / 2 - 40 + i * 40, 800, 7 * p, [ACC, VIOLET, WARM][i], clamp(p))
    return L

# =============================================================== premise
def sc_premise(t, d):
    L = layer()
    words(L, W / 2, 190, 'One base design.', F('D8', 110), 0.2, t, TXT)
    sp = e_out(prog(t, 0.9, 1.6))
    text(L, W / 2, 298, 'Every room adds what it needs.', F('B5', 42), MUTED, sp, al='m', blur=(1 - sp) * 8)
    cards = [('Living Room', ['Art mode', 'Lights', 'Cameras', 'Speakers', 'Doorbell'], ACC, False),
             ('Bedroom', ['Art mode', 'Lights', 'Speakers', 'Alarm', 'Sleep timer'], WARM, False),
             ('Any room', ['Kids', 'Games', 'Sports', 'Scenes', 'Cast'], VIOLET, True)]
    cw, chh, gap = 500, 420, 40
    x0 = (W - (3 * cw + 2 * gap)) / 2
    fb = F('B7', 24)
    for i, (nm, chips, col, dashed) in enumerate(cards):
        ta = 1.3 + 0.18 * i
        p = e_back(prog(t, ta, ta + 0.8), 1.1)
        if p <= 0: continue
        C = layer(cw, chh)
        if dashed: rrect_o(C, 2, 2, cw - 2, chh - 2, 30, 3, (70, 84, 102))
        else:
            rrect(C, 0, 0, cw, chh, 30, SURF); rrect_o(C, 0, 0, cw, chh, 30, 2, LINE)
        rrect(C, 40, 46, 124, 96, 10, lerpc(SURF2, col, .45)); rrect(C, 72, 100, 92, 107, 3, (70, 84, 102))
        text(C, 148, 72, nm, F('D8', 42), TXT)
        x, y = 40, 150
        for j, c in enumerate(chips):
            w = fb.getlength(c) + 44
            if x + w > cw - 40: x = 40; y += 64
            q = e_back(prog(t, ta + 0.55 + 0.12 * j, ta + 0.95 + 0.12 * j))
            if q > 0:
                if dashed: rrect_o(C, x, y, x + w, y + 50, 25, 2, col, clamp(q))
                else: rrect(C, x, y, x + w, y + 50, 25, lerpc(SURF2, col, .25), clamp(q))
                text(C, x + w / 2, y + 25, c, fb, TXT, clamp(q), al='m')
            x += w + 12
        popimg(L, C, x0 + i * (cw + gap) + cw / 2, 410 + chh / 2, p)
    cp = e_out(prog(t, 3.9, 4.6))
    text(L, W / 2, 940, 'Same launcher in every room. Same feel. Different powers.', F('B5', 34), MUTED, cp, al='m', blur=(1 - cp) * 8)
    return L

# =============================================================== quiet
QROWS = [('Continue watching', 1, 1, 0, 0, None), ('Suggested for you', 1, 0, 1, 2, 8.2), ('Family watchlist', 0, 1, 2, 1, 9.6),
         ('Apps', 1, 1, 3, 3, None), ('Trending now', 1, 0, 4, 4, 8.9)]

def _qrow_y(nm, t):
    mv = e_io(prog(t, 10.5, 11.3))
    for n, a0, a1, s0, s1, tt in QROWS:
        if n == nm: return 104 + lerp(s0, s1, mv) * 72
    return 104

def ui_quiet(t):
    if t < 6.3:
        bx, ba, st = 1400, 0.0, 0.0
        if t >= 1.4:
            ba = clamp(prog(t, 1.4, 1.6)); bx = (1 - e_out(prog(t, 1.4, 1.95))) * 1400
            st = e_io(prog(t, 2.5, 2.95))
            if t > 3.0:
                q = prog(t, 3.0, 3.5); bx = e_in(q) * 1500; ba = 1 - q
        return ui_home(t - 0.2, (bx, ba, st))
    L = home_static()
    pin = e_out(prog(t, 6.5, 7.2)); pout = e_in(prog(t, 13.0, 13.6))
    k = pin * (1 - pout)
    dimmed(L, 0.64 * k)
    if k <= 0.01: return L
    P = layer(640, 560)
    rrect(P, 0, 0, 640, 560, 28, (23, 28, 35)); rrect_o(P, 0, 0, 640, 560, 28, 2, (58, 70, 86))
    text(P, 40, 56, 'Edit home', F('D8', 36), TXT); text(P, 600, 58, 'Hold OK to move', F('B5', 20), MUTED, al='r')
    lift = trap(t, 9.95, 10.3, 11.2, 11.6)
    order = sorted(QROWS, key=lambda r: 1 if r[0] == 'Family watchlist' else 0)
    for nm, a0, a1, s0, s1, tt in order:
        v = a0 if tt is None else lerp(a0, a1, e_io(prog(t, tt, tt + 0.35)))
        y = _qrow_y(nm, t)
        lf = lift if nm == 'Family watchlist' else 0
        if lf > 0:
            sh = layer(640, 140); rrect(sh, 30, 40, 610, 100, 16, (0, 0, 0), .5 * lf)
            put(P, sh.filter(ImageFilter.GaussianBlur(10)), 0, int(y - 34))
        x0 = 30 - 8 * lf; x1 = 610 + 8 * lf; yy = y - 6 * lf
        rrect(P, x0, yy, x1, yy + 60, 16, lerpc(SURF2, (48, 60, 78), lf))
        for k2 in range(3): rrect(P, x0 + 22, yy + 22 + k2 * 7, x0 + 42, yy + 25 + k2 * 7, 2, MUTED)
        text(P, x0 + 62, yy + 30, nm, F('B7', 24), lerpc(MUTED, TXT, v))
        toggle(P, x1 - 96, yy + 10, v)
    y = 104 + 5 * 72
    rrect(P, 30, y, 610, y + 60, 16, (40, 31, 17))
    lock(P, 62, y + 28, 22, WARM)
    text(P, 92, y + 30, 'Sponsored rows', F('B7', 24), WARM)
    text(P, 586, y + 30, 'Off for good', F('B7', 20), WARM, al='r')
    def rr(nm):
        yy = _qrow_y(nm, t) - (6 * lift if nm == 'Family watchlist' else 0)
        return (30, yy, 610, yy + 60)
    sched = [(0, rr('Suggested for you')), (8.55, rr('Trending now')), (9.25, rr('Family watchlist'))]
    fr = focus_path(sched, t)
    fa = clamp(prog(t, 7.3, 7.6)) * (1 - prog(t, 12.0, 12.4))
    focus(P, *fr, 16, fa, ACC if lift > 0.05 else TXT, 4, 6)
    popimg(L, P, SW / 2, SH / 2 + 10, k, 0.92)
    return L

def sc_quiet(t, d):
    L = layer()
    heads(L, t, [(0.15, 6.2, 'Quiet by design.', None, 118), (6.5, 14.0, 'Rows you choose.', 'Hide it. Reorder it. Nothing sponsored, ever.', 112)])
    entry(L, ui_quiet(t), t)
    ck = 1 - e_io(prog(t, 5.6, 6.1))
    x = 160
    f = F('B7', 30)
    for i, lab in enumerate(['No ads', 'No promoted rows', 'No tracking']):
        pp = e_back(prog(t, 3.2 + i * .3, 3.7 + i * .3))
        if pp > 0 and ck > 0:
            w = f.getlength(lab) + 100
            Cp = layer(int(w) + 20, 80)
            rrect(Cp, 6, 6, 6 + w, 70, 32, SURF2); circle(Cp, 6 + 34, 38, 15, ACC)
            line(Cp, [(6 + 27, 38), (6 + 33, 44), (6 + 43, 32)], 4, (10, 13, 17))
            text(Cp, 6 + 62, 38, lab, f, TXT)
            im = Cp.resize((max(1, int(Cp.width * (.7 + .3 * pp))), max(1, int(Cp.height * (.7 + .3 * pp)))), Image.BILINEAR)
            put(L, scale_alpha(im, clamp(pp) * ck), int(x), 232 + int((80 - im.height) / 2))
        x += f.getlength(lab) + 100 + 24
    return L

# =============================================================== profiles
PROF = [('Matt', (43, 76, 126), 'M', None), ('Sam', (91, 63, 140), 'S', None), ('Lily', (200, 85, 61), 'L', 'Kids'), ('Guest', (70, 80, 94), 'G', None)]
PXS = [SW / 2 + (i - 1.5) * 250 for i in range(4)]
KBG = (30, 23, 48)

def ui_who(t):
    L = home_static(); dimmed(L, 0.82)
    pa = e_out(prog(t, 0.3, 0.9))
    text(L, SW / 2, 150, "Who's watching?", F('D8', 54), TXT, pa, al='m')
    sel = e_io(prog(t, 3.6, 4.3))
    press = trap(t, 5.0, 5.12, 5.22, 5.45)
    out = e_io(prog(t, 5.3, 5.9))
    for i, (nm, col, chr_, tag) in enumerate(PROF):
        p = e_back(prog(t, 0.5 + 0.12 * i, 1.1 + 0.12 * i))
        if p <= 0: continue
        a = clamp(p) * ((1 - out) if i != 2 else 1)
        r = 84 * (0.6 + 0.4 * p) * ((1 - 0.07 * press) if i == 2 else 1)
        avatar(L, PXS[i], 330, r, col, chr_, a)
        text(L, PXS[i], 468, nm, F('B7', 30), TXT, a, al='m')
        if tag: pill(L, PXS[i], 498, tag, F('B7', 20), CORAL, (10, 13, 17), a, padx=16, h=34, al='m')
    ra = clamp(prog(t, 2.6, 3.0)) * (1 - out)
    ring(L, lerp(PXS[0], PXS[2], sel), 330, 100, 6, TXT, ra)
    sa = e_out(prog(t, 1.4, 2.0)) * (1 - out)
    text(L, SW / 2, 610, 'Each profile gets its own rows, watchlist, voice match and room access.', F('B5', 24), MUTED, sa, al='m')
    return L

def ui_kid(t):
    L = new_screen(KBG)
    circle(L, 1180, 20, 300, (90, 50, 140), .35); circle(L, 60, 720, 240, (200, 85, 61), .16)
    p, dy = fx(t, 0.15)
    text(L, 48, 84 + dy, 'Hi, Lily!', F('D8', 64), TXT, p)
    mins = 42
    f = F('B7', 22); s = 'Bedtime in %d min' % mins
    w = f.getlength(s) + 76; xr = SW - 48
    p2, dy2 = fx(t, 0.3)
    rrect(L, xr - w, 50 + dy2, xr, 98 + dy2, 24, (58, 44, 86), p2)
    moon(L, xr - w + 30, 74 + dy2, 11, WARM, (58, 44, 86), p2)
    text(L, xr - w + 52, 74 + dy2, s, f, TXT, p2)
    p3, dy3 = fx(t, 0.4)
    text(L, 48, 168 + dy3, 'Watch', F('D8', 34), TXT, p3)
    cards = [((255, 170, 80), 'Moon Pals'), ((110, 200, 150), 'Tiny Trains'), ((110, 170, 255), 'Ocean Hop'), ((240, 120, 160), 'Doodle Zoo')]
    for i, (col, nm) in enumerate(cards):
        pp, ddy = fx(t, 0.5 + 0.1 * i, 0.8)
        x = 48 + i * 300; y = 200 + ddy
        rrect(L, x, y, x + 280, y + 200, 32, col, pp)
        circle(L, x + 240, y + 30, 70, (255, 255, 255), .16 * pp)
        bob = math.sin(t * 2.4 + i * 1.3) * 6
        face(L, x + 140, y + 100 + bob, 58, lerpc(col, (255, 255, 255), .55), pp)
        text(L, x + 8, y + 232, nm, F('B7', 26), TXT, pp)
    p4, dy4 = fx(t, 0.9)
    text(L, 48, 330 + 160 + dy4, 'Play', F('D8', 34), TXT, p4)
    apps = [('play', 'Shows', False), ('music', 'Songs', False), ('photos', 'Drawings', False), ('games', 'Games', False), ('browser', 'Web', True)]
    for i, (k, lab, locked) in enumerate(apps):
        pp, ddy = fx(t, 1.0 + 0.07 * i)
        x = 48 + i * 196; y = 524 + ddy
        bg = (58, 44, 86) if not locked else (40, 33, 56)
        rrect(L, x, y, x + 176, y + 120, 28, bg, pp)
        glyph(L, k, x + 88, y + 50, 20, TXT if not locked else (110, 100, 130), pp)
        text(L, x + 88, y + 96, lab, F('B7', 20), TXT if not locked else (110, 100, 130), pp, al='m')
        if locked:
            circle(L, x + 152, y + 24, 18, CORAL, pp); lock(L, x + 152, y + 24, 14, (30, 23, 48), pp)
    sp = e_out(prog(t, 1.8, 2.4))
    rrect(L, 1040, 540, 1232, 644, 24, (58, 44, 86), sp)
    text(L, 1136, 572, 'Approved', F('B7', 22), TXT, sp, al='m'); text(L, 1136, 606, 'apps only', F('B7', 22), TXT, sp, al='m')
    return L

def ui_parent(t):
    L = new_screen()
    p0, dy0 = fx(t, 0.1)
    topbar(L, 'Parental controls', p0, room='Matt', sub='Lily · Kid profile')
    # left card
    pl, dyl = fx(t, 0.2)
    rrect(L, 48, 120 + dyl, 400, 672 + dyl, 24, SURF, pl)
    avatar(L, 224, 220 + dyl, 62, (200, 85, 61), 'L', pl)
    text(L, 224, 322 + dyl, 'Lily', F('D8', 40), TXT, pl, al='m')
    text(L, 224, 360 + dyl, 'Kid profile  ·  Age 7', F('B5', 20), MUTED, pl, al='m')
    for i, (lab, val) in enumerate([('Today', '48 min'), ('This week', '6h 13m'), ('Rooms', 'Living, Bedroom')]):
        yy = 440 + i * 70 + dyl
        rrect(L, 72, yy - 26, 376, yy + 26, 14, SURF2, pl)
        text(L, 90, yy, lab, F('B5', 20), MUTED, pl); text(L, 360, yy, val, F('B7', 20), TXT, pl, al='r')
    # middle: chart
    pm, dym = fx(t, 0.3)
    rrect(L, 424, 120 + dym, 824, 672 + dym, 24, SURF, pm)
    text(L, 452, 162 + dym, 'Screen time', F('B7', 26), TXT, pm); text(L, 796, 164 + dym, 'Last 7 days', F('B5', 18), MUTED, pm, al='r')
    vals = [52, 45, 75, 60, 38, 55, 48]; days = ['T', 'F', 'S', 'S', 'M', 'T', 'W']
    base = 600 + dym; sc = 4.0
    ly = base - 60 * sc
    for xx in range(452, 796, 18): line(L, [(xx, ly), (xx + 9, ly)], 2, WARM, pm * .8)
    text(L, 796, ly - 18, '1h limit', F('B7', 16), WARM, pm, al='r')
    for i, v in enumerate(vals):
        g = e_out(prog(t, 0.9 + 0.07 * i, 1.6 + 0.07 * i))
        x = 470 + i * 46
        col = ACC if i == 6 else (CORAL if v > 60 else (70, 84, 102))
        hh = v * sc * g
        if hh > 1: rrect(L, x, base - hh, x + 28, base, 8, col, pm)
        text(L, x + 14, base + 26, days[i], F('B7', 18), TXT if i == 6 else MUTED, pm, al='m')
    # right: controls
    pr, dyr = fx(t, 0.4)
    rrect(L, 848, 120 + dyr, 1232, 672 + dyr, 24, SURF, pr)
    bon = e_io(prog(t, 3.0, 3.35))
    text(L, 876, 166 + dyr, 'Bedtime', F('B7', 26), TXT, pr); toggle(L, 1132, 146 + dyr, bon, pr)
    text(L, 876, 204 + dyr, '8:30 PM  –  7:00 AM', F('B5', 22), lerpc(MUTED, ACC, bon), pr)
    line(L, [(876, 240 + dyr), (1204, 240 + dyr)], 2, LINE, pr)
    text(L, 876, 278 + dyr, 'Content rating', F('B7', 26), TXT, pr)
    chips = ['G', 'PG', 'PG-13', 'R']; cx = 876; rects = []
    for c in chips:
        w = F('B7', 22).getlength(c) + 40; rects.append((cx, 304 + dyr, cx + w, 350 + dyr)); cx += w + 10
    sel = e_io(prog(t, 4.3, 4.75))
    sr = rect_lerp(rects[2], rects[1], sel)
    rrect(L, sr[0], sr[1], sr[2], sr[3], 23, ACC, pr)
    for i, c in enumerate(chips):
        x0, y0, x1, y1 = rects[i]
        if i != 1 and i != 2: rrect_o(L, x0, y0, x1, y1, 23, 2, (70, 84, 102), pr)
        inside = sr[0] - 4 <= (x0 + x1) / 2 <= sr[2] + 4
        text(L, (x0 + x1) / 2, (y0 + y1) / 2, c, F('B7', 22), (10, 13, 17) if inside else (TXT if i < 3 else (90, 100, 114)), pr, al='m')
    line(L, [(876, 380 + dyr), (1204, 380 + dyr)], 2, LINE, pr)
    won = e_io(prog(t, 5.2, 5.55))
    text(L, 876, 418 + dyr, 'Wind-down', F('B7', 26), TXT, pr); toggle(L, 1132, 398 + dyr, won, pr)
    text(L, 876, 454 + dyr, 'Dims and warns 5 min before', F('B5', 20), MUTED, pr)
    line(L, [(876, 490 + dyr), (1204, 490 + dyr)], 2, LINE, pr)
    text(L, 876, 528 + dyr, 'Approved apps', F('B7', 26), TXT, pr); text(L, 1204, 530 + dyr, '4 of 9', F('B7', 20), MUTED, pr, al='r')
    for i, k in enumerate(['play', 'music', 'photos', 'games']):
        x = 876 + i * 82
        rrect(L, x, 562 + dyr, x + 66, 628 + dyr, 16, SURF2, pr); glyph(L, k, x + 33, 595 + dyr, 13, TXT, pr)
    fsched = [(0, (1132, 146, 1204, 186)), (3.9, rects[2]), (4.3, rects[1]), (5.0, (1132, 398, 1204, 438))]
    fr = focus_path(fsched, t)
    fa = clamp(prog(t, 2.4, 2.7)) * (1 - prog(t, 5.9, 6.2))
    focus(L, fr[0], fr[1] + dyr, fr[2], fr[3] + dyr, 22, fa, TXT, 3, 6)
    return L

def ui_kidplay(t):
    L = new_screen((44, 34, 86))
    for k in range(18):
        sx = (k * 197) % SW; sy = (k * 131) % 420 + 30
        circle(L, sx, sy, 2 + (k % 3), (255, 255, 255), .35 + .3 * abs(math.sin(t * 1.5 + k)))
    circle(L, 980, 170, 90, (250, 230, 170), .9)
    rrect(L, 0, 520, SW, SH, 0, (36, 62, 70))
    for k in range(3):
        bx = 260 + k * 330 + math.sin(t * 1.2 + k) * 30
        face(L, bx, 470 + math.sin(t * 3 + k) * 14, 70, [(255, 170, 80), (110, 200, 150), (240, 120, 160)][k], 1, (30, 23, 48))
    rrect(L, 48, SH - 52, SW - 48, SH - 44, 4, (255, 255, 255), .25); rrect(L, 48, SH - 52, 48 + (SW - 96) * .82, SH - 44, 4, ACC)
    text(L, 48, 60, 'Moon Pals', F('D8', 34), TXT, .9); text(L, 48, 98, 'Episode 12', F('B5', 20), TXT, .7)
    wd = e_io(prog(t, 0.5, 1.6))
    dimmed(L, 0.5 * wd, (40, 20, 6))
    tp = e_back(prog(t, 0.9, 1.6))
    if tp > 0:
        T = layer(640, 210)
        rrect(T, 0, 0, 640, 210, 30, (35, 28, 30)); rrect_o(T, 0, 0, 640, 210, 30, 2, (90, 70, 50))
        circle(T, 86, 105, 46, (70, 52, 30)); moon(T, 86, 105, 24, WARM, (70, 52, 30))
        text(T, 160, 80, 'Bedtime in 5 minutes', F('D8', 36), TXT)
        text(T, 160, 124, 'Time to wrap up, Lily.', F('B5', 24), MUTED)
        slider(T, 160, 580, 168, 1 - prog(t, 1.2, 9.0), 1, WARM, False, 8)
        popimg(L, T, SW / 2, SH / 2 + 40, tp, 0.85)
    return L

def ui_profiles(t):
    if t < 5.55: return ui_who(t)
    if t < 6.6:
        return reveal(ui_who(t), ui_kid(t - 5.55), PXS[2], 330, e_io(prog(t, 5.55, 6.55)) * 1500)
    if t < 15.0: return ui_kid(t - 5.55)
    if t < 15.9: return slide(ui_kid(t - 5.55), ui_parent(t - 15.0), prog(t, 15.0, 15.9))
    if t < 21.0: return ui_parent(t - 15.0)
    if t < 21.6: return xfade(ui_parent(t - 15.0), ui_kidplay(t - 21.0), prog(t, 21.0, 21.6))
    return ui_kidplay(t - 21.0)

def sc_profiles(t, d):
    L = layer()
    heads(L, t, [(0.15, 6.8, 'Everyone gets their own.', 'Profiles, picked in a tap.', 108),
                 (7.2, 14.8, 'Kid mode, built in.', "Big, simple, and only what you've approved.", 112),
                 (15.2, 20.9, 'You stay in charge.', 'Screen time, bedtime, ratings and apps.', 112),
                 (21.2, 24.4, 'Bedtime, handled.', 'A gentle warning. Then lights out.', 112)])
    entry(L, ui_profiles(t), t)
    return L

# =============================================================== search & watchlist
SERV = [('Streams+', (125, 211, 252), 'S', 'Included', True), ('Cinema Plus', (255, 122, 112), 'C', 'Rent  $4.99', False), ('Reelhouse', (183, 156, 255), 'R', 'Buy  $14.99', False)]
_LO = {}
def _orbit_poster(w, h):
    k = (w, h)
    if k not in _LO: _LO[k] = poster(w, h, (138, 90, 43), None, 1)
    return _LO[k]

def ui_search_main(t):
    L = new_screen()
    fa = e_out(prog(t, 0.2, 0.7))
    rrect(L, 48, 40, SW - 48, 104, 32, SURF2, fa)
    magnifier(L, 90, 72, 24, MUTED, fa)
    q = typed('the long orbit', t, 0.8, 2.5)
    f = F('B5', 30)
    text(L, 128, 72, q if q else 'Search every app at once…', f, TXT if q else MUTED, fa)
    if 0.5 < t < 3.4 and int(t * 2.6) % 2 == 0:
        cx = 128 + (f.getlength(q) if q else 0) + 4
        rrect(L, cx, 52, cx + 3, 92, 1, ACC, fa)
    sp = e_out(prog(t, 2.7, 3.2))
    text(L, SW - 84, 72, '6 apps searched', F('B7', 20), MUTED, sp, al='r')
    cx = 48
    for i, c in enumerate(['All', 'Movies', 'Shows', 'Included with yours']):
        w = F('B7', 20).getlength(c) + 36
        pc, dyc = fx(t, 2.9 + 0.05 * i, 0.5)
        rrect(L, cx, 122 + dyc, cx + w, 160 + dyc, 19, ACC if i == 0 else SURF, pc)
        text(L, cx + w / 2, 141 + dyc, c, F('B7', 20), (10, 13, 17) if i == 0 else TXT, pc, al='m')
        cx += w + 10
    # top result
    pr, dyr = fx(t, 3.1, 0.8)
    put(L, scale_alpha(_orbit_poster(210, 300), pr), 48, int(186 + dyr))
    text(L, 286, 212 + dyr, 'The Long Orbit', F('D8', 46), TXT, pr)
    text(L, 286, 258 + dyr, '2024  ·  Sci-fi  ·  2h 8m', F('B5', 22), MUTED, pr)
    rrect(L, 286, 282 + dyr, 346, 314 + dyr, 10, (40, 50, 30), pr); text(L, 316, 298 + dyr, '8.2', F('B7', 18), GREEN, pr, al='m')
    text(L, 360, 298 + dyr, 'Top result', F('B7', 18), MUTED, pr)
    text(L, 286, 346 + dyr, 'A salvage pilot finds a signal that', F('B5', 22), TXT, pr * .85)
    text(L, 286, 376 + dyr, "shouldn't exist, forty years from home.", F('B5', 22), TXT, pr * .85)
    added = t >= 9.55
    press = trap(t, 9.45, 9.55, 9.6, 9.8)
    rrect(L, 286, 420 + dyr, 436, 476 + dyr, 28, ACC, pr); play_tri(L, 320, 448 + dyr, 18, (10, 13, 17), pr)
    text(L, 344, 448 + dyr, 'Play', F('B7', 22), (10, 13, 17), pr)
    bx0 = 452; bw = 214 if not added else 236
    sh = 3 * press
    rrect(L, bx0 + sh, 420 + dyr + sh, bx0 + bw - sh, 476 + dyr - sh, 28, SURF2 if not added else (30, 60, 45), pr)
    if not added: text(L, bx0 + bw / 2, 448 + dyr, '+  Watchlist', F('B7', 22), TXT, pr, al='m')
    else:
        check(L, bx0 + 34, 448 + dyr, 13, pr, GREEN); text(L, bx0 + 58, 448 + dyr, 'In watchlist', F('B7', 22), TXT, pr)
    # where to watch
    pw, dyw = fx(t, 3.3, 0.8)
    text(L, 760, 212 + dyw, 'Where to watch', F('B7', 24), TXT, pw)
    rows = []
    for i, (nm, col, chr_, price, inc) in enumerate(SERV):
        pp, ddy = fx(t, 3.45 + 0.12 * i, 0.7)
        y = 244 + i * 82 + ddy
        rows.append((760, 244 + i * 82, 1232, 244 + i * 82 + 68))
        rrect(L, 760, y, 1232, y + 68, 18, SURF, pp)
        circle(L, 800, y + 34, 20, col, pp); text(L, 800, y + 34, chr_, F('B7', 20), (10, 13, 17), pp, al='m')
        text(L, 834, y + 34, nm, F('B7', 24), TXT, pp)
        text(L, 1208, y + 34, price, F('B7', 22), ACC if inc else TXT, pp, al='r')
    # more like this
    pm, dym = fx(t, 3.8, 0.8)
    text(L, 48, 528 + dym, 'More like this', F('B7', 24), TXT, pm)
    for i, (col, kd) in enumerate([((60, 98, 120), 3), ((107, 47, 60), 2), ((47, 107, 87), 0), ((91, 63, 140), 1), ((40, 90, 110), 3)]):
        pp, ddy = fx(t, 3.9 + 0.07 * i, 0.7)
        put(L, scale_alpha(poster(222, 125, col, None, kd), pp), 48 + i * 238, int(552 + ddy + dym * 0))
    fsched = [(0, rows[0]), (8.5, (bx0, 420, bx0 + 214, 476))]
    fr = focus_path(fsched, t)
    fa = clamp(prog(t, 5.0, 5.3)) * (1 - prog(t, 10.0, 10.3))
    focus(L, *fr, 22, fa, TXT, 4, 6)
    if fa > 0 and t < 8.5:
        bp = e_back(prog(t, 5.3, 5.8))
        if bp > 0: pill(L, 1080, 228, 'Best option', F('B7', 16), ACC, (10, 13, 17), clamp(bp), padx=12, h=28, al='m')
    # toast
    tp = e_out(prog(t, 10.0, 10.5)) * (1 - e_in(prog(t, 13.4, 13.9)))
    if tp > 0.01:
        ty = SH - 116 + (1 - e_out(prog(t, 10.0, 10.5))) * 60 + e_in(prog(t, 13.4, 13.9)) * 60
        T = layer(560, 88)
        rrect(T, 0, 0, 560, 88, 44, (35, 44, 56)); rrect_o(T, 0, 0, 560, 88, 44, 2, (70, 84, 102))
        avatar(T, 50, 44, 24, (43, 76, 126), 'M')
        text(T, 92, 32, 'Added to Family watchlist', F('B7', 24), TXT); text(T, 92, 62, 'Sam and Lily can see it too', F('B5', 18), MUTED)
        check(T, 516, 44, 16, 1, GREEN)
        put(L, scale_alpha(T, tp), int(SW / 2 - 280), int(ty))
    return L

WL = [('The Long Orbit', (138, 90, 43), 1, 'M', (43, 76, 126), 'Just added'), ('Paper Cities', (107, 47, 60), 2, 'S', (91, 63, 140), 'New episode'),
      ('Harbor Lights', (43, 76, 126), 3, 'M', (43, 76, 126), None), ('Trail Notes', (47, 107, 87), 0, 'S', (91, 63, 140), None),
      ('Glass Garden', (60, 98, 120), 0, 'S', (91, 63, 140), 'Leaving soon'), ('Moon Pals', (220, 140, 60), 0, 'L', (200, 85, 61), None),
      ('Nightfall Kitchen', (91, 63, 140), 2, 'M', (43, 76, 126), 'New episode'), ('Tideline', (40, 90, 110), 3, 'M', (43, 76, 126), None)]
_WLP = {}
def ui_watch(t):
    L = new_screen()
    p0, dy0 = fx(t, 0.0)
    topbar(L, 'Family watchlist', p0, room=None, sub='8 titles  ·  3 people')
    for i, (c, col) in enumerate([('M', (43, 76, 126)), ('S', (91, 63, 140)), ('L', (200, 85, 61))]):
        avatar(L, SW - 48 - 22 - (2 - i) * 54, 66 + dy0, 22, col, c, p0)
    pr, dyr = fx(t, 0.1)
    rrect(L, 48, 112 + dyr, 760, 178 + dyr, 33, SURF2, pr)
    circle(L, 86, 145 + dyr, 22, ACC, pr); play_tri(L, 88, 145 + dyr, 18, (10, 13, 17), pr)
    text(L, 122, 133 + dyr, 'Resume Harbor Lights', F('B7', 24), TXT, pr)
    text(L, 122, 160 + dyr, 'Season 2  ·  Episode 4  ·  31 min left', F('B5', 18), MUTED, pr)
    rrect(L, 560, 142 + dyr, 728, 149 + dyr, 4, (70, 84, 102), pr); rrect(L, 560, 142 + dyr, 560 + 168 * .62, 149 + dyr, 4, ACC, pr)
    for i, (nm, col, kd, who, wcol, tag) in enumerate(WL):
        cx = 48 + (i % 4) * 302; cy = 214 + (i // 4) * 240
        if i == 0:
            pp = e_back(prog(t, 1.1, 1.8)); ddy = 0
            if pp <= 0: continue
            a = clamp(pp)
        else:
            pp, ddy = fx(t, 0.2 + 0.06 * i, 0.7); a = pp
        if nm not in _WLP: _WLP[nm] = poster(278, 156, col, None, kd)
        img = _WLP[nm]
        if i == 0:
            g = trap(t, 1.4, 1.8, 3.6, 4.4)
            if g > 0: rrect_o(L, cx - 8, cy - 8, cx + 286, cy + 164, 24, 4, ACC, g)
            popimg(L, img, cx + 139, cy + 78, pp, 0.7)
        else:
            put(L, scale_alpha(img, a), cx, int(cy + ddy))
        avatar(L, cx + 248, cy + 30 + ddy, 18, wcol, who, a)
        if tag:
            tc = {'Just added': ACC, 'New episode': GREEN, 'Leaving soon': CORAL}[tag]
            pill(L, cx + 12, cy + 112 + ddy, tag, F('B7', 16), tc, (10, 13, 17), a, padx=12, h=30)
        text(L, cx + 2, cy + 184 + ddy, nm, F('B7', 22), TXT, a)
    fa = clamp(prog(t, 4.6, 4.9))
    if fa > 0: focus(L, 48, 112 + dyr, 760, 178 + dyr, 33, fa, TXT, 4, 6)
    tp = e_out(prog(t, 5.2, 5.7))
    if tp > 0.01:
        T = layer(620, 80)
        rrect(T, 0, 0, 620, 80, 40, (35, 44, 56)); rrect_o(T, 0, 0, 620, 80, 40, 2, (70, 84, 102))
        for k in range(3):
            rrect(T, 30 + k * 26, 26, 50 + k * 26, 52, 4, [ACC, WARM, VIOLET][k])
        text(T, 128, 40, 'Picks up where you left off, in any room.', F('B7', 22), TXT)
        put(L, scale_alpha(T, tp), int(SW - 48 - 620), int(SH - 104 + (1 - tp) * 40))
    return L

def ui_search(t):
    if t < 15.0: return ui_search_main(t)
    if t < 15.9: return slide(ui_search_main(t), ui_watch(t - 15.0), prog(t, 15.0, 15.9))
    return ui_watch(t - 15.0)

def sc_search(t, d):
    L = layer()
    heads(L, t, [(0.15, 9.2, 'Find it once.', 'Every app, one search. Best price first.', 118),
                 (9.5, 14.9, 'Save it for later.', 'One press adds it for the whole family.', 112),
                 (15.3, 24.4, 'One list for everyone.', 'Who added it, what is new, what is leaving.', 112)])
    entry(L, ui_search(t), t)
    return L

# =============================================================== voice
_VFRZ = None
def ui_voice2(t):
    global _VFRZ
    if t < 9.4: return ui_voice(t)
    if _VFRZ is None: _VFRZ = ui_voice(9.4)
    L = _VFRZ.copy()
    nb = home_static(); dimmed(nb, 0.6)
    L.alpha_composite(scale_alpha(nb, e_io(prog(t, 9.5, 10.1))))
    a3 = e_out(prog(t, 10.4, 10.9)) * (1 - e_in(prog(t, 13.0, 13.4)))
    y3 = (1 - e_out(prog(t, 10.4, 10.9))) * 60 + e_in(prog(t, 13.0, 13.4)) * 60
    voice_bar(L, t, y3, a3, typed('Find something Sam and I both like.', t, 10.9, 12.8), t < 12.9)
    pc = e_back(prog(t, 13.3, 14.0)); ca = clamp(prog(t, 13.3, 13.7)) * (1 - e_in(prog(t, 17.4, 17.8)))
    if ca > 0.01:
        Cd = layer(720, 380)
        rrect(Cd, 0, 0, 720, 380, 28, (23, 28, 35)); rrect_o(Cd, 0, 0, 720, 380, 28, 2, (58, 70, 86))
        put(Cd, poster(210, 316, (107, 47, 60), None, 2), 32, 32)
        text(Cd, 272, 72, 'Paper Cities', F('D8', 44), TXT)
        text(Cd, 272, 114, 'Drama  ·  2 seasons', F('B5', 22), MUTED)
        avatar(Cd, 292, 174, 22, (43, 76, 126), 'M'); ring(Cd, 326, 174, 23, 3, (23, 28, 35)); avatar(Cd, 326, 174, 22, (91, 63, 140), 'S')
        text(Cd, 362, 174, '94% match for both of you', F('B7', 22), ACC)
        text(Cd, 272, 224, 'You both loved Harbor Lights.', F('B5', 22), TXT, .85)
        text(Cd, 272, 254, 'Sam is on episode 3. You have not started.', F('B5', 22), TXT, .85)
        press = trap(t, 16.2, 16.3, 16.35, 16.55)
        rrect(Cd, 272 + 3 * press, 300 + 3 * press, 412 - 3 * press, 352 - 3 * press, 26, ACC); play_tri(Cd, 304, 326, 16, (10, 13, 17)); text(Cd, 326, 326, 'Play', F('B7', 22), (10, 13, 17))
        rrect(Cd, 426, 300, 688, 352, 26, SURF2); text(Cd, 557, 326, 'Start together', F('B7', 22), TXT, al='m')
        focus(Cd, 272, 300, 412, 352, 26, clamp(prog(t, 15.4, 15.7)), TXT, 3, 5)
        put(L, scale_alpha(Cd, ca), int(SW / 2 - 360), int(118 + (1 - pc) * 90 + e_in(prog(t, 17.4, 17.8)) * 40))
    a4 = e_out(prog(t, 18.0, 18.5)) * (1 - e_in(prog(t, 20.2, 20.6)))
    y4 = (1 - e_out(prog(t, 18.0, 18.5))) * 60 + e_in(prog(t, 20.2, 20.6)) * 60
    voice_bar(L, t, y4, a4, typed('Is the garage door closed?', t, 18.5, 19.9), t < 20.0)
    pc = e_back(prog(t, 20.5, 21.1)); ca = clamp(prog(t, 20.5, 20.8))
    if ca > 0.01:
        Cd = layer(620, 200)
        rrect(Cd, 0, 0, 620, 200, 28, (23, 28, 35)); rrect_o(Cd, 0, 0, 620, 200, 28, 2, (58, 70, 86))
        put(Cd, feed_img(200, 136, 'garage', t, r=16), 32, 32)
        check(Cd, 290, 72, 22, 1, GREEN)
        text(Cd, 326, 72, 'Closed', F('D8', 40), TXT)
        text(Cd, 268, 128, 'Garage door  ·  closed at 9:42 PM', F('B5', 22), MUTED)
        text(Cd, 268, 160, 'Front door locked too.', F('B5', 22), MUTED)
        put(L, scale_alpha(Cd, ca), int(SW / 2 - 310), int(200 + (1 - pc) * 90))
    return L

def sc_voice5(t, d):
    L = layer()
    heads(L, t, [(0.15, 9.7, 'Just say it.', 'Lights, cameras, speakers. Ask for it.', 118),
                 (10.0, 22.4, 'Ask it anything.', 'It knows your house. And your family.', 118)])
    blit_screen(L, ui_voice2(t), 1, 0, 1)
    return L
