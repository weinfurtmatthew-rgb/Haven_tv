"""Score + sound design for 'A day with Haven' (120 s). Everything synthesized with numpy/scipy."""
import numpy as np, wave, os, time
from scipy import signal

T0 = time.time()
SR = 44100; DUR = 120.0; n = int(SR * DUR)
rng = np.random.default_rng(21)
f32 = np.float32
def buf(): return np.zeros(n, f32)
PAD = [buf(), buf()]; MUS = [buf(), buf()]; MUS_S = [buf(), buf()]; DRM = [buf(), buf()]
SFX = [buf(), buf()]; SFX_S = [buf(), buf()]; AMB = [buf(), buf()]; MOV = [buf(), buf()]

def mtof(m): return 440.0 * 2 ** ((m - 69) / 12.0)
def tt(sec): return np.arange(int(max(1, sec * SR)), dtype=np.float64) / SR
def adsr(k, a, r):
    e = np.ones(k); ka = min(k, int(a * SR)); kr = min(k, int(r * SR))
    if ka: e[:ka] = np.linspace(0, 1, ka) ** 2
    if kr: e[-kr:] *= np.linspace(1, 0, kr) ** 2
    return e
def place(bus, start, sig, gain=1.0, pan=0.0, send=None, sendgain=0.0):
    i = int(round(start * SR))
    if i >= n or i + len(sig) <= 0: return
    if i < 0: sig = sig[-i:]; i = 0
    j = min(n, i + len(sig)); s = sig[:j - i] * gain
    th = (np.clip(pan, -1, 1) + 1) * np.pi / 4; gl, gr = np.cos(th) * np.sqrt(2), np.sin(th) * np.sqrt(2)
    bus[0][i:j] += (s * gl).astype(f32); bus[1][i:j] += (s * gr).astype(f32)
    if send is not None and sendgain > 0:
        send[0][i:j] += (s * gl * sendgain).astype(f32); send[1][i:j] += (s * gr * sendgain).astype(f32)
def sm(x, a, b):
    q = min(1.0, max(0.0, (x - a) / (b - a))); return q * q * (3 - 2 * q)

# ------------------------------------------------------------------ arrangement
IK = [(0, .1), (10.6, .12), (11.2, .32), (14.4, .4), (20, .46), (22.5, .6), (32.5, .55), (35.5, .48), (44.5, .48), (45.5, .62), (55.8, .62),
      (56.5, .36), (66, .42), (68.6, .55), (70.5, .7), (80.5, .7), (81.6, .3), (86.4, .0), (99.2, .0), (99.6, .16), (110.5, .2), (111.5, .42), (116, .4), (120, .1)]
IX = [a for a, b in IK]; IY = [b for a, b in IK]
def I(t): return float(np.interp(t, IX, IY))
# pad level (separate so the movie can replace the score)
PK = [(0, 1), (85.6, 1), (86.8, 0.12), (98.8, 0.12), (100.2, 0.9), (120, 1)]
def padlvl(t): return float(np.interp(t, [a for a, b in PK], [b for a, b in PK]))

BPM = 92.0; BEAT = 60 / BPM; BAR = 4 * BEAT; CH = 2 * BAR
PADV = [[53, 57, 60, 64, 67], [52, 55, 60, 64, 69], [53, 57, 60, 64, 65], [50, 53, 57, 60, 62],
        [50, 53, 57, 58, 62], [55, 58, 62, 65, 67], [53, 57, 60, 64, 69], [50, 55, 58, 60, 62]]
ROOT = [41, 45, 38, 46, 43, 48, 45, 46]
END_T = 111.6
def chord_at(t): return 0 if t >= END_T else int(t // CH) % 8

def pad_chord(start, length, notes, gain):
    t = tt(length); e = adsr(len(t), 1.4, 2.4); lfo = 0.82 + 0.18 * np.sin(2 * np.pi * 0.11 * t + start)
    i0 = int(start * SR)
    for m in notes:
        f = mtof(m)
        for side in (0, 1):
            acc = np.zeros(len(t))
            for v, det in enumerate((0.996 if side == 0 else 1.004, 1.0012 if side == 0 else 0.9988)):
                ph = 2 * np.pi * f * det * t + (m * 1.7 + v * 2.1 + side)
                for h in range(1, 6): acc += np.sin(h * ph) / h ** 1.6
            sig = acc * e * lfo * gain; j = min(n, i0 + len(sig))
            PAD[side][i0:j] += sig[:j - i0].astype(f32)
k = 0
while k * CH < END_T:
    st = k * CH; pad_chord(max(0, st - 0.25), min(CH + 2.6, END_T - st + 2.6), PADV[k % 8], 0.017); k += 1
pad_chord(END_T - 0.1, DUR - END_T + 0.1, [41, 53, 57, 60, 64, 67, 72], 0.02)
sos = signal.butter(2, 3000, 'low', fs=SR, output='sos')
for s_ in (0, 1): PAD[s_][:] = signal.sosfilt(sos, PAD[s_]).astype(f32)
print('pads', round(time.time() - T0, 1), flush=True)

def pluck_sig(f, length=0.9, bright=1.0):
    t = tt(length)
    sig = (np.sin(2 * np.pi * f * t) + 0.5 * bright * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 9) + 0.22 * bright * np.sin(2 * np.pi * 3 * f * t) * np.exp(-t * 14)) * np.exp(-t * 4.0)
    a = int(0.003 * SR); sig[:a] *= np.linspace(0, 1, a); return sig
def bell_sig(f, length=2.6, idx=2.0, decay=1.4):
    t = tt(length); mod = idx * np.exp(-t * 2.5) * np.sin(2 * np.pi * f * 3.5 * t)
    sig = np.sin(2 * np.pi * f * t + mod) * np.exp(-t * decay) + 0.22 * np.sin(2 * np.pi * f * 2.0 * t) * np.exp(-t * decay * 2.2)
    a = int(0.002 * SR); sig[:a] *= np.linspace(0, 1, a); return sig
def mbox_sig(f, length=1.8):   # music box: bright, glassy, quick decay
    t = tt(length)
    sig = (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 4.02 * t) * np.exp(-t * 7) + 0.18 * np.sin(2 * np.pi * f * 6.9 * t) * np.exp(-t * 11)) * np.exp(-t * 2.6)
    a = int(0.001 * SR); sig[:a] *= np.linspace(0, 1, a); return sig
def keys_sig(f, length=2.2):   # soft electric-piano-ish
    t = tt(length)
    sig = (np.sin(2 * np.pi * f * t + 0.8 * np.exp(-t * 3) * np.sin(2 * np.pi * f * t)) + 0.3 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 4)) * np.exp(-t * 1.7)
    a = int(0.004 * SR); sig[:a] *= np.linspace(0, 1, a); return sig
def bass_sig(f, length):
    t = tt(length); acc = np.zeros(len(t))
    for h in range(1, 6): acc += np.sin(2 * np.pi * h * f * t) / h * np.exp(-t * (2 + 5 * h))
    acc += 0.6 * np.sin(2 * np.pi * f * 0.5 * t) * np.exp(-t * 3)
    return acc * adsr(len(t), 0.004, 0.07)

# arps
pat = [0, 2, 4, 1, 3, 2, 4, 3]; step = BEAT / 2; tc = 0.0; idx = 0
while tc < END_T - 0.1:
    lv = I(tc); g = sm(lv, 0.28, 0.55) * 0.07
    if g > 0.002:
        v = PADV[chord_at(tc)]; m = v[pat[idx % 8] % len(v)] + (12 if (idx // 8) % 2 == 0 else 24)
        place(MUS, tc, pluck_sig(mtof(m), 0.9, 0.7 + 0.5 * lv), g * (1.0 if idx % 4 == 0 else 0.7), -0.35 if idx % 2 == 0 else 0.35, MUS_S, 0.35)
    tc += step; idx += 1
# bass
BPAT = [0, 0, 12, 0, 0, 12, 0, 7]; tc = 0.0; idx = 0
while tc < END_T - 0.05:
    lv = I(tc); g = sm(lv, 0.5, 0.7) * 0.15
    if g > 0.003: place(MUS, tc, bass_sig(mtof(ROOT[chord_at(tc)] + BPAT[idx % 8]), step * 0.95), g * (1.0 if idx % 2 == 0 else 0.72))
    tc += step; idx += 1
# drums
def kick_sig():
    t = tt(0.42); f = 46 + 80 * np.exp(-t * 28); s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7.5)
    s[:int(.003 * SR)] += rng.standard_normal(int(.003 * SR)) * 0.25; return s
KICK = kick_sig()
hp = signal.butter(2, 6500, 'high', fs=SR, output='sos')
def hat_sig(length=0.07, dec=60): t = tt(length); return signal.sosfilt(hp, rng.standard_normal(len(t))) * np.exp(-t * dec)
bp = signal.butter(2, [1500, 6000], 'band', fs=SR, output='sos')
def snap_sig():
    t = tt(0.18); nz = signal.sosfilt(bp, rng.standard_normal(len(t))); return nz * np.exp(-t * 30) + np.sin(2 * np.pi * 1100 * t) * np.exp(-t * 60) * 0.3
SNAP = snap_sig()
duck = np.ones(n, f32); bt = 0.0; b = 0
while bt < END_T - 0.05:
    lv = I(bt); kg = sm(lv, 0.5, 0.68) * 0.5
    if kg > 0.01 and (lv > 0.66 or b % 2 == 0):
        place(DRM, bt, KICK, kg)
        dd = tt(0.3); env_ = 1 - 0.28 * sm(lv, .6, .85) * np.exp(-dd * 9)
        i = int(bt * SR); j = min(n, i + len(env_)); duck[i:j] = np.minimum(duck[i:j], env_[:j - i].astype(f32))
    hg = sm(lv, 0.44, 0.66) * 0.045
    if hg > 0.002:
        place(DRM, bt + BEAT / 2, hat_sig(), hg, 0.3)
        if lv > 0.55:
            for q in (0.25, 0.75): place(DRM, bt + BEAT * q, hat_sig(0.04, 90), hg * 0.4, -0.35)
    sg = sm(lv, 0.62, 0.72) * 0.16
    if sg > 0.004 and b % 2 == 1: place(DRM, bt, SNAP, sg, 0.15, SFX_S, 0.3)
    bt += BEAT; b += 1
print('rhythm', round(time.time() - T0, 1), flush=True)

# melodies
MOTIF = [(0.0, 69, 0.9), (0.33, 72, 0.8), (0.66, 77, 1.0), (1.3, 76, 1.0)]
def motif(t0, gain, oct_=0):
    for dt, m, g in MOTIF: place(MUS, t0 + dt, bell_sig(mtof(m + oct_), 3.2, 1.5, 1.05), gain * g, 0.1, MUS_S, 0.75)
motif(1.25, 0.08); motif(47.5, 0.05, 12); motif(112.2, 0.11)
# golden hour keys melody (56.6 - 68.4)
mel = [(0, 72, 1.2), (1.3, 74, 0.6), (1.95, 77, 1.9), (4.0, 76, 1.2), (5.3, 72, 0.6), (5.95, 69, 1.9), (8.0, 70, 1.2), (9.3, 72, 0.6), (9.95, 74, 1.4), (11.4, 72, 0.6)]
for dt, m, ln in mel: place(MUS, 56.6 + dt, keys_sig(mtof(m), ln + 1.2), 0.07, -0.1, MUS_S, 0.6)
for dt, m in [(0, 57), (4.0, 53), (8.0, 58)]: place(MUS, 56.6 + dt, keys_sig(mtof(m), 4.4), 0.045, 0.1, MUS_S, 0.5)
# lullaby music box (99.6 - 110.6)
lul = [72, 69, 65, 69, 70, 67, 64, 67, 69, 72, 77, 76, 74, 72, 70, 69, 67, 69, 72, 65]
for i_, m in enumerate(lul): place(MUS, 99.7 + i_ * 0.5, mbox_sig(mtof(m + 12), 1.8), 0.055 * (1 - 0.3 * (i_ > 15)), 0.25 if i_ % 2 else -0.15, MUS_S, 0.8)
print('melody', round(time.time() - T0, 1), flush=True)

# ------------------------------------------------------------------ movie audio (heard from the TV)
mv0, mv1 = 86.7, 107.2
t = tt(mv1 - mv0); lt_ = t
drone = np.zeros(len(t))
for f, g_ in ((mtof(29), 1.0), (mtof(36), 0.6), (mtof(41), 0.35), (mtof(48), 0.18)):
    for det in (0.998, 1.002):
        ph = 2 * np.pi * f * det * t
        for h in range(1, 5): drone += np.sin(h * ph + h) / h ** 1.3 * g_
drone *= 0.55 + 0.45 * np.sin(2 * np.pi * 0.07 * t) ** 2
shim = np.sin(2 * np.pi * mtof(84) * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.2 * t)) * 0.08
mov = drone * 0.5 + shim
tg = lt_ + mv0
gate = np.ones(len(t)); gate *= np.clip((tg - mv0) / 0.8, 0, 1); gate *= np.clip((mv1 - tg) / 0.6, 0, 1)
pz = np.interp(tg, [92.2, 92.4, 96.6, 97.1], [1, 0, 0, 1]); gate *= pz
mov *= gate
movl = signal.sosfilt(signal.butter(2, 900, 'low', fs=SR, output='sos'), mov)
place(MOV, mv0, movl, 0.05, 0.05)
for tb in (86.9, 89.6, 98.4, 103.0):   # distant booms from the film
    tb_ = tt(2.2); bm = np.sin(2 * np.pi * np.cumsum(38 + 30 * np.exp(-tb_ * 6)) / SR) * np.exp(-tb_ * 2.2)
    place(MOV, tb, bm, 0.12 if tb < 99 else 0.06, 0.0)

# ------------------------------------------------------------------ sound design
UI = 1.0
lp1 = signal.butter(2, 500, 'low', fs=SR, output='sos'); hp1 = signal.butter(2, 2500, 'high', fs=SR, output='sos')
def whoosh(start, length=1.0, gain=0.05, up=True):
    k_ = int(length * SR); nz = rng.standard_normal(k_); lo = signal.sosfilt(lp1, nz); hi = signal.sosfilt(hp1, nz) * 0.55
    x = np.linspace(0, 1, k_); mix = x if up else 1 - x; e = np.sin(np.pi * x) ** 2; s = (lo * (1 - mix) + hi * mix) * e
    th = (np.linspace(-0.6, 0.6, k_) + 1) * np.pi / 4
    i = int((start - length * 0.5) * SR); j = min(n, i + k_)
    for side, gg in ((0, np.cos(th) * 1.4), (1, np.sin(th) * 1.4)):
        SFX[side][i:j] += (s * gg * gain)[:j - i].astype(f32); SFX_S[side][i:j] += (s * gg * gain * 0.5)[:j - i].astype(f32)
def tick(start, gain=0.04, f=2300, pan=0.0):
    t = tt(0.05); s = np.sin(2 * np.pi * f * t) * np.exp(-t * 110); place(SFX, start, s, gain * UI, pan, SFX_S, 0.15)
def click(start, up=True, gain=0.07, pan=0.0):
    t = tt(0.09); f = np.linspace(900, 1400, len(t)) if up else np.linspace(1300, 800, len(t))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 60); place(SFX, start, s, gain * UI, pan, SFX_S, 0.15)
def pop(start, f=880, gain=0.06, pan=0.0):
    t = tt(0.25); ff = f * (0.6 + 0.4 * (1 - np.exp(-t * 90))); s = np.sin(2 * np.pi * np.cumsum(ff) / SR) * np.exp(-t * 16)
    place(SFX, start, s, gain * UI, pan, SFX_S, 0.3)
def chime(start, m, gain=0.06, pan=0.0, length=1.6): place(SFX, start, bell_sig(mtof(m), length, 1.2, 2.2), gain * UI, pan, SFX_S, 0.55)
def notify(start, gain=0.07): chime(start, 88, gain, 0.4, 1.2); chime(start + 0.12, 93, gain * 0.9, 0.4, 1.4)
def tvon(start, gain=0.05, base=76): chime(start, base, gain, 0, 1.6); chime(start + 0.09, base + 7, gain * 0.8, 0, 1.6)
def sparkle(start, gain=0.035):
    for k_, m in enumerate([84, 88, 91, 96]): chime(start + k_ * 0.05, m, gain * (1 - 0.15 * k_), -0.5 + k_ * 0.33, 0.9)
def thud(start, gain=0.12, f=85, pan=0.0, length=0.35):
    t = tt(length); s = np.sin(2 * np.pi * np.cumsum(f * (1 + 0.6 * np.exp(-t * 30))) / SR) * np.exp(-t * 14) + signal.sosfilt(lp1, rng.standard_normal(len(t))) * np.exp(-t * 25) * 0.6
    place(SFX, start, s, gain, pan, SFX_S, 0.2)
def latch(start, gain=0.06, pan=0.4):
    for d_, f in ((0, 2400), (0.035, 1600)):
        t = tt(0.03); s = signal.sosfilt(hp1, rng.standard_normal(len(t))) * np.exp(-t * 200) + np.sin(2 * np.pi * f * t) * np.exp(-t * 150) * 0.5
        place(SFX, start + d_, s, gain, pan, SFX_S, 0.1)
def door_open(start, pan=0.5): latch(start, 0.06, pan); whoosh(start + 0.35, 0.7, 0.025)
def door_close(start, pan=0.5): thud(start, 0.1, 70, pan, 0.4); latch(start + 0.06, 0.05, pan)
def step(start, gain=0.05, pan=0.4): thud(start, gain, 60, pan, 0.18)
def motor(start, length, gain=0.02):
    t = tt(length); hum = np.sin(2 * np.pi * np.cumsum(118 + 10 * np.sin(2 * np.pi * 0.7 * t)) / SR) * 0.6 + signal.sosfilt(signal.butter(2, [300, 1500], 'band', fs=SR, output='sos'), rng.standard_normal(len(t))) * 0.5
    s = hum * adsr(len(t), 0.15, 0.25); place(SFX, start, s, gain, -0.2, SFX_S, 0.1)
def rustle(start, length=0.9, gain=0.025):
    t = tt(length); s = signal.sosfilt(signal.butter(2, [800, 5000], 'band', fs=SR, output='sos'), rng.standard_normal(len(t))) * np.sin(np.pi * np.linspace(0, 1, len(t))) ** 1.5 * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 7 * t)))
    place(SFX, start, s, gain, 0.1, SFX_S, 0.2)
def ding_dong(start, gain=0.15):
    place(SFX, start, bell_sig(mtof(76), 2.4, 0.8, 1.1), gain, 0.3, SFX_S, 0.6); place(SFX, start + 0.55, bell_sig(mtof(72), 3.0, 0.8, 0.9), gain, 0.3, SFX_S, 0.6)
def listen(start, gain=0.045):
    t = tt(0.22); f = np.linspace(620, 940, len(t)); s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.linspace(0, np.pi, len(t)))
    place(SFX, start, s, gain * UI, 0.0, SFX_S, 0.4)
def blip(start, f0, f1, gain=0.05, length=0.3):
    t = tt(length); f = np.linspace(f0, f1, len(t)); s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 6)
    place(SFX, start, s, gain * UI, 0.0, SFX_S, 0.4)
def sweep_down(start, length=1.6, gain=0.04):
    k_ = int(length * SR); nz = rng.standard_normal(k_); x = np.linspace(0, 1, k_)
    s = (signal.sosfilt(hp1, nz) * (1 - x) * 0.5 + signal.sosfilt(lp1, nz) * x) * np.sin(np.pi * x) ** 2
    place(SFX, start, s, gain, 0.0, SFX_S, 0.5)

# camera transitions
for tw in (7.6, 19.9, 32.7, 43.6, 47.9, 56.4, 68.7, 74.7, 98.9, 105.6, 108.7): whoosh(tw, 1.3, 0.035)
# morning
for k_ in range(3):                                         # alarm marimba, rising
    pass
tcur = 10.8; g = 0.05
while tcur < 14.2:
    for j_, m in enumerate((77, 81, 84)): place(SFX, tcur + j_ * 0.14, pluck_sig(mtof(m), 0.8, 1.2), g, 0.0, SFX_S, 0.5)
    tcur += 1.15; g += 0.012
click(14.3, False, 0.08)
motor(11.4, 2.5, 0.022); rustle(15.0, 1.0, 0.03); blip(14.45, 500, 760, 0.035)
tvon(21.45, 0.045, 74)
cm = tt(2.6); coffee = signal.sosfilt(lp1, rng.standard_normal(len(cm))) * (0.4 + 0.6 * np.abs(np.sin(2 * np.pi * 3.3 * cm)) ** 3) * adsr(len(cm), 0.3, 0.6)
place(SFX, 20.7, coffee, 0.05, -0.4, SFX_S, 0.2)
# away
door_open(33.0); door_close(34.35)
notify(35.7, 0.065); thud(38.86, 0.13, 95, 0.5); notify(39.3, 0.065)
# Lily home
door_open(45.25); step(45.75); step(46.05, 0.04); thud(46.18, 0.08, 75, 0.45); door_close(46.95); notify(46.0, 0.06)
tvon(47.45, 0.06, 79); sparkle(47.7, 0.04); pop(53.45, 700, 0.05)
# golden hour / dinner
sparkle(62.5, 0.025); click(66.4, True, 0.02, 0.6); click(68.6, True, 0.03, -0.4); tvon(69.05, 0.065, 72)
sim = tt(12.0); bub = signal.sosfilt(signal.butter(2, [200, 900], 'band', fs=SR, output='sos'), rng.standard_normal(len(sim)))
bub *= (np.abs(np.sin(2 * np.pi * 2.3 * sim + np.sin(2 * np.pi * 0.7 * sim))) ** 6) * adsr(len(sim), 0.6, 1.5)
place(SFX, 69.0, bub, 0.05, -0.45, SFX_S, 0.2)
sparkle(75.45, 0.07)
# movie night
listen(81.75, 0.05); blip(82.8, 700, 1000, 0.04)
for k_, m in enumerate((79, 83, 86, 91)): chime(83.0 + 0.8 + 0.45 * k_, m, 0.04, -0.3 + 0.2 * k_, 1.1)
sweep_down(84.1, 1.8, 0.04); motor(84.4, 2.0, 0.018)
ding_dong(92.0, 0.15); blip(92.3, 760, 420, 0.045)
door_open(94.6); step(94.85, 0.035); rustle(95.15, 0.6, 0.025); door_close(96.05); blip(96.6, 420, 760, 0.045)
# bedtime + good night
motor(99.2, 1.6, 0.012); tvon(99.45, 0.035, 72); chime(104.0, 96, 0.03, 0.4, 1.4); click(105.5, False, 0.03); click(107.1, False, 0.03)
for tl, pn in ((106.5, -0.6), (107.0, -0.1), (107.6, 0.5), (108.0, 0.4), (108.5, 0.7), (109.3, -0.5)): thud(tl, 0.035, 120, pn, 0.15); click(tl, False, 0.02, pn)
latch(107.3, 0.07, 0.5); latch(107.45, 0.06, 0.5); notify(107.4, 0.055)
print('sfx', round(time.time() - T0, 1), flush=True)

# ------------------------------------------------------------------ ambience
def crickets(t0, t1, gain):
    for k_ in range(int((t1 - t0) * 3.2)):
        st = t0 + rng.uniform(0, t1 - t0); t = tt(0.18); f = rng.uniform(4200, 5200)
        s = np.sin(2 * np.pi * f * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 32 * t)) * np.sin(np.pi * np.linspace(0, 1, len(t)))
        place(AMB, st, s, gain * rng.uniform(0.5, 1.0), rng.uniform(-0.8, 0.8))
def birds(t0, t1, gain, rate=1.6):
    for k_ in range(int((t1 - t0) * rate)):
        st = t0 + rng.uniform(0, t1 - t0); nn = rng.integers(1, 4); f0 = rng.uniform(2400, 4200)
        for q in range(nn):
            t = tt(rng.uniform(0.06, 0.14)); f = f0 * (1 + rng.uniform(-0.25, 0.35) * np.linspace(0, 1, len(t)))
            s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * np.linspace(0, 1, len(t))) ** 2
            place(AMB, st + q * 0.11, s, gain * rng.uniform(0.4, 1.0), rng.uniform(-0.9, 0.9))
crickets(0.0, 10.5, 0.01); birds(10.8, 24.0, 0.012, 1.8); birds(33.0, 45.0, 0.008, 0.7); birds(56.5, 66.0, 0.006, 0.6); crickets(70.0, 120.0, 0.008)
wind = signal.sosfilt(lp1, rng.standard_normal(n)).astype(f32) * f32(0.06)
wenv = np.interp(np.arange(n) / SR, [0, 32, 35, 44, 46, 120], [0, 0, 1, 1, 0, 0]).astype(f32)
AMB[0] += wind * wenv; AMB[1] += np.roll(wind, 3000) * wenv
print('ambience', round(time.time() - T0, 1), flush=True)

# ------------------------------------------------------------------ automation + mix
tg = np.arange(n, dtype=np.float64) / SR
pl = np.array([padlvl(x) for x in np.linspace(0, DUR, 1201)]); plv = np.interp(tg, np.linspace(0, DUR, 1201), pl).astype(f32)
pan = np.interp(tg, [0, 74.5, 77.5, 80.0, 120], [0, -0.35, 0.15, 0.0, 0.0]); th = (pan + 1) * np.pi / 4
gl = (np.cos(th) * np.sqrt(2)).astype(f32); gr = (np.sin(th) * np.sqrt(2)).astype(f32)
mus_gate = np.interp(tg, [0, 85.8, 86.8, 99.0, 99.6, 120], [1, 1, 0.0, 0.0, 1, 1]).astype(f32)   # rhythm/arps out during the movie
mixL = (PAD[0] * plv + MUS[0] * mus_gate) * duck * gl
mixR = (PAD[1] * plv + MUS[1] * mus_gate) * duck * gr
sendL = (PAD[0] * plv * 0.5 + MUS_S[0] * mus_gate) * gl + SFX_S[0]
sendR = (PAD[1] * plv * 0.5 + MUS_S[1] * mus_gate) * gr + SFX_S[1]
drL = DRM[0] * mus_gate; drR = DRM[1] * mus_gate
irn = int(2.6 * SR); it_ = np.arange(irn) / SR; irs = []
for s_ in range(2):
    ir = rng.standard_normal(irn) * np.exp(-it_ * 2.4); ir = signal.sosfilt(signal.butter(1, 5200, 'low', fs=SR, output='sos'), ir); ir[:int(0.012 * SR)] = 0
    irs.append((ir / np.sqrt(np.sum(ir ** 2))).astype(f32))
wetL = signal.oaconvolve(sendL, irs[0])[:n].astype(f32); wetR = signal.oaconvolve(sendR, irs[1])[:n].astype(f32)
L = mixL + drL + SFX[0] + AMB[0] + MOV[0] + wetL * 0.8
R = mixR + drR + SFX[1] + AMB[1] + MOV[1] + wetR * 0.8
# stem report (pre-master)
for a_, b_, nm in ((10.5, 20, 'alarm'), (20, 33, 'kitchen'), (33, 45, 'away'), (45, 56, 'lily'), (68.5, 81, 'dinner'), (81, 99, 'movie'), (99, 111, 'bedtime')):
    i0, i1 = int(a_ * SR), int(b_ * SR)
    mu = np.sqrt(np.mean((mixL[i0:i1] + drL[i0:i1] + MOV[0][i0:i1]) ** 2)); sp = np.abs(SFX[0][i0:i1]).max()
    print('  stem %-8s music %.1f dB  sfx peak %.1f dB' % (nm, 20 * np.log10(mu + 1e-9), 20 * np.log10(sp + 1e-9)))
# gentle leveling compressor on the full mix (slow, 2.5:1 above threshold)
def mavg(x, k_):
    c = np.cumsum(np.concatenate([np.zeros(1), x])); h = k_ // 2
    idx0 = np.clip(np.arange(len(x)) - h, 0, len(x)); idx1 = np.clip(np.arange(len(x)) + h, 0, len(x))
    return (c[idx1] - c[idx0]) / np.maximum(1, idx1 - idx0)
def level(Lx, Rx, thr_db=-25.0, ratio=1.8, win=0.5):
    p = (Lx.astype(np.float64) ** 2 + Rx.astype(np.float64) ** 2) / 2
    env = np.sqrt(mavg(p, int(win * SR)) + 1e-12)
    edb = 20 * np.log10(env); gdb = -np.maximum(0, edb - thr_db) * (1 - 1 / ratio)
    g_ = mavg(10 ** (gdb / 20), int(0.25 * SR))
    return (Lx * g_).astype(f32), (Rx * g_).astype(f32)
L, R = level(L, R)
fin = np.minimum(1, tg / 1.2) ** 2; fout = np.clip((DUR - tg) / 3.0, 0, 1) ** 1.5
L = L * (fin * fout).astype(f32); R = R * (fin * fout).astype(f32)
hpm = signal.butter(2, 28, 'high', fs=SR, output='sos'); L = signal.sosfilt(hpm, L); R = signal.sosfilt(hpm, R)
peak = max(np.abs(L).max(), np.abs(R).max()); L = L / peak * 1.25; R = R / peak * 1.25
L = np.tanh(L) / np.tanh(1.25) * 0.89; R = np.tanh(R) / np.tanh(1.25) * 0.89
for a_, b_, nm in ((0, 10.5, 'dawn'), (10.5, 20, 'alarm'), (20, 33, 'kitchen'), (33, 45, 'away'), (45, 56, 'lily'), (56, 68.5, 'golden'), (68.5, 81, 'dinner'), (81, 99, 'movie'), (99, 111, 'bedtime'), (111, 120, 'end')):
    i0, i1 = int(a_ * SR), int(b_ * SR); r = np.sqrt(np.mean((L[i0:i1] ** 2 + R[i0:i1] ** 2) / 2)); print('%-8s %.1f dBFS' % (nm, 20 * np.log10(r + 1e-9)))
pcm = (np.clip(np.stack([L, R], axis=1), -1, 1) * 32767).astype('<i2')
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'day_score.wav')
with wave.open(out, 'wb') as wf: wf.setnchannels(2); wf.setsampwidth(2); wf.setframerate(SR); wf.writeframes(pcm.tobytes())
print('wrote', out, round(len(pcm) / SR, 2), 's in', round(time.time() - T0, 1), 's')
