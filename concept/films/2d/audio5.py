"""Five-minute score + sound design for the Haven keynote film (synthesized, no samples)."""
import numpy as np, wave, os, time
from scipy import signal
from tl import START, TOTAL, NAMES, DURS

T0 = time.time()
SR = 44100
DUR = TOTAL
n = int(SR * DUR)
rng = np.random.default_rng(11)
f32 = np.float32

def buf(): return np.zeros(n, f32)
PAD = [buf(), buf()]; MUS = [buf(), buf()]; MUS_S = [buf(), buf()]
DRM = [buf(), buf()]; SFX = [buf(), buf()]; SFX_S = [buf(), buf()]

def A(name, lt): return START[name] + lt
def mtof(m): return 440.0 * 2 ** ((m - 69) / 12.0)
def tt(sec): return np.arange(int(sec * SR), dtype=np.float64) / SR

def place(bus, start, sig, gain=1.0, pan=0.0, send=None, sendgain=0.0):
    i = int(round(start * SR))
    if i >= n or i + len(sig) <= 0: return
    if i < 0: sig = sig[-i:]; i = 0
    j = min(n, i + len(sig)); s = sig[:j - i] * gain
    th = (np.clip(pan, -1, 1) + 1) * np.pi / 4
    gl, gr = np.cos(th) * np.sqrt(2), np.sin(th) * np.sqrt(2)
    bus[0][i:j] += (s * gl).astype(f32); bus[1][i:j] += (s * gr).astype(f32)
    if send is not None and sendgain > 0:
        send[0][i:j] += (s * gl * sendgain).astype(f32); send[1][i:j] += (s * gr * sendgain).astype(f32)

def place2(bus, start, sl, sr_, gain=1.0, send=None, sendgain=0.0):
    i = int(round(start * SR))
    if i >= n: return
    j = min(n, i + len(sl))
    bus[0][i:j] += (sl[:j - i] * gain).astype(f32); bus[1][i:j] += (sr_[:j - i] * gain).astype(f32)
    if send is not None and sendgain > 0:
        send[0][i:j] += (sl[:j - i] * gain * sendgain).astype(f32); send[1][i:j] += (sr_[:j - i] * gain * sendgain).astype(f32)

def adsr(k, a, r):
    e = np.ones(k)
    ka = min(k, int(a * SR)); kr = min(k, int(r * SR))
    if ka: e[:ka] = np.linspace(0, 1, ka) ** 2
    if kr: e[-kr:] *= np.linspace(1, 0, kr) ** 2
    return e

# ------------------------------------------------------------------ intensity map
LEVEL = {'intro': .12, 'title': .3, 'premise': .42, 'quiet': .5, 'profiles': .6, 'search': .66, 'voice': .58, 'scenes': .86,
         'art': .3, 'lights': .62, 'doorbell': .64, 'speakers': .8, 'bedroom': .22, 'phone': .74, 'games': .95, 'rooms': .78, 'end': .4}
xs, ys = [0.0], [LEVEL['intro']]
for k in range(1, len(NAMES)):
    s = START[NAMES[k]]
    xs += [s - 0.5, s + 0.4]; ys += [LEVEL[NAMES[k - 1]], LEVEL[NAMES[k]]]
xs.append(DUR); ys.append(LEVEL['end'])
def I(t): return float(np.interp(t, xs, ys))
def sm(x, a, b):
    q = min(1.0, max(0.0, (x - a) / (b - a))); return q * q * (3 - 2 * q)

# ------------------------------------------------------------------ harmony
BPM = 100.0; BEAT = 60.0 / BPM; BAR = 4 * BEAT; CH = 2 * BAR          # 0.6 s, 2.4 s, 4.8 s
PADV = [[53, 57, 60, 64, 67], [52, 55, 60, 64, 69], [53, 57, 60, 64, 65], [50, 53, 57, 60, 62],
        [50, 53, 57, 58, 62], [55, 58, 62, 65, 67], [53, 57, 60, 64, 69], [50, 55, 58, 60, 62]]
ROOT = [41, 45, 38, 46, 43, 48, 45, 46]
END_T = START['end']
def chord_at(t):
    if t >= END_T: return 0
    return int(t // CH) % 8

# ------------------------------------------------------------------ pads
def pad_chord(start, length, notes, gain):
    t = tt(length); e = adsr(len(t), 1.3, 2.2)
    lfo = 0.8 + 0.2 * np.sin(2 * np.pi * 0.13 * t + start)
    for m in notes:
        f = mtof(m)
        for side in (0, 1):
            acc = np.zeros(len(t))
            for v, det in enumerate((0.996 if side == 0 else 1.004, 1.0012 if side == 0 else 0.9988)):
                ph = 2 * np.pi * f * det * t + (m * 1.7 + v * 2.1 + side)
                for h in range(1, 7):
                    acc += np.sin(h * ph) / h ** 1.5
            sig = acc * e * lfo * gain
            PAD[side][int(start * SR): int(start * SR) + len(sig)] += sig[:max(0, n - int(start * SR))].astype(f32)

k = 0
while k * CH < END_T:
    st = k * CH
    ln = min(CH + 2.4, END_T - st + 2.4)
    pad_chord(max(0, st - 0.25), ln, PADV[k % 8], 0.018)
    k += 1
pad_chord(END_T - 0.1, DUR - END_T + 0.1, [41, 53, 57, 60, 64, 67, 72], 0.02)
sos = signal.butter(2, 3200, 'low', fs=SR, output='sos')
for s_ in (0, 1):
    PAD[s_][:] = signal.sosfilt(sos, PAD[s_]).astype(f32)
# sub drone under the pads
sub = np.zeros(n, f32)
for k in range(int(END_T // CH) + 1):
    st = k * CH; ln = CH + 1.2
    t = tt(ln); f = mtof(ROOT[k % 8] - 12 if ROOT[k % 8] > 40 else ROOT[k % 8])
    s = (np.sin(2 * np.pi * f * t) * adsr(len(t), 0.6, 1.2) * 0.05).astype(f32)
    i = int(st * SR); j = min(n, i + len(s)); sub[i:j] += s[:j - i]
t = tt(DUR - END_T); s = (np.sin(2 * np.pi * mtof(29) * t) * adsr(len(t), 0.05, 4.0) * 0.07).astype(f32)
sub[int(END_T * SR): int(END_T * SR) + len(s)] += s[:n - int(END_T * SR)]
print('pads', round(time.time() - T0, 1), flush=True)

# ------------------------------------------------------------------ tonal voices
def pluck_sig(f, length=0.9, bright=1.0):
    t = tt(length)
    sig = (np.sin(2 * np.pi * f * t) + 0.5 * bright * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 9)
           + 0.25 * bright * np.sin(2 * np.pi * 3 * f * t) * np.exp(-t * 14) + 0.1 * np.sin(2 * np.pi * 4.01 * f * t) * np.exp(-t * 20)) * np.exp(-t * 4.2)
    a = int(0.003 * SR); sig[:a] *= np.linspace(0, 1, a)
    return sig

def bell_sig(f, length=2.6, idx=2.2, decay=1.4):
    t = tt(length)
    mod = idx * np.exp(-t * 2.5) * np.sin(2 * np.pi * f * 3.5 * t)
    sig = np.sin(2 * np.pi * f * t + mod) * np.exp(-t * decay) + 0.25 * np.sin(2 * np.pi * f * 2.0 * t) * np.exp(-t * decay * 2.2)
    a = int(0.002 * SR); sig[:a] *= np.linspace(0, 1, a)
    return sig

def bass_sig(f, length):
    t = tt(length)
    acc = np.zeros(len(t))
    for h in range(1, 7):
        acc += np.sin(2 * np.pi * h * f * t) / h * np.exp(-t * (2 + 5 * h))
    acc += 0.6 * np.sin(2 * np.pi * f * 0.5 * t) * np.exp(-t * 3)
    e = adsr(len(t), 0.004, 0.06)
    return acc * e

# arps
pat = [0, 2, 4, 1, 3, 2, 4, 3]
step = BEAT / 2
tc = 0.0; idx = 0
while tc < END_T - 0.1:
    lv = I(tc)
    g = sm(lv, 0.36, 0.6) * 0.075
    if g > 0.002:
        c = chord_at(tc); v = PADV[c]
        m = v[pat[idx % 8] % len(v)] + (12 if (idx // 8) % 2 == 0 else 24)
        acc_ = 1.0 if idx % 4 == 0 else 0.72
        place(MUS, tc, pluck_sig(mtof(m), 0.9, 0.7 + 0.5 * lv), g * acc_, -0.35 if idx % 2 == 0 else 0.35, MUS_S, 0.35)
        if lv > 0.84:
            m2 = v[pat[(idx + 3) % 8] % len(v)] + 24
            place(MUS, tc + step / 2, pluck_sig(mtof(m2), 0.5, 1.2), g * 0.45 * sm(lv, .84, .92), 0.5 if idx % 2 == 0 else -0.5, MUS_S, 0.4)
    tc += step; idx += 1

# bass
BPAT = [0, 0, 12, 0, 0, 12, 0, 7]
tc = 0.0; idx = 0
while tc < END_T - 0.05:
    lv = I(tc)
    g = sm(lv, 0.54, 0.74) * 0.16
    if g > 0.003:
        c = chord_at(tc)
        m = ROOT[c] + BPAT[idx % 8]
        place(MUS, tc, bass_sig(mtof(m), step * 0.95), g * (1.0 if idx % 2 == 0 else 0.75), 0.0)
    tc += step; idx += 1
print('arps+bass', round(time.time() - T0, 1), flush=True)

# drums
def kick_sig():
    t = tt(0.45)
    f = 45 + 85 * np.exp(-t * 28)
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph) * np.exp(-t * 7.5)
    s[:int(.003 * SR)] += rng.standard_normal(int(.003 * SR)) * 0.3
    return s
KICK = kick_sig()
hp = signal.butter(2, 7000, 'high', fs=SR, output='sos')
def hat_sig(length=0.08, dec=55):
    t = tt(length); s = signal.sosfilt(hp, rng.standard_normal(len(t))) * np.exp(-t * dec); return s
bp = signal.butter(2, [900, 4200], 'band', fs=SR, output='sos')
def clap_sig():
    t = tt(0.3)
    nz = signal.sosfilt(bp, rng.standard_normal(len(t)))
    e = np.exp(-t * 16)
    for d in (0.012, 0.024):
        k_ = int(d * SR); e[k_:] += 0.6 * np.exp(-t[:len(t) - k_] * 30)
    return nz * e * 0.8 + np.sin(2 * np.pi * 185 * t) * np.exp(-t * 22) * 0.4
CLAP = clap_sig()
duck = np.ones(n, f32)
bt = 0.0; b = 0
while bt < END_T - 0.05:
    lv = I(bt)
    kg = sm(lv, 0.5, 0.72) * 0.55
    four = lv > 0.78
    if kg > 0.01 and (four or b % 2 == 0):
        place(DRM, bt, KICK, kg, 0.0)
        # pump the pads/music under the kick
        dd = tt(0.32); env_ = 1 - 0.32 * sm(lv, .6, .9) * np.exp(-dd * 9)
        i = int(bt * SR); j = min(n, i + len(env_)); duck[i:j] = np.minimum(duck[i:j], env_[:j - i].astype(f32))
    hg = sm(lv, 0.46, 0.7) * 0.05
    if hg > 0.002:
        place(DRM, bt + BEAT / 2, hat_sig(), hg, 0.3)
        if lv > 0.88:
            place(DRM, bt + BEAT / 4, hat_sig(0.05, 80), hg * 0.45, -0.3); place(DRM, bt + 3 * BEAT / 4, hat_sig(0.05, 80), hg * 0.45, -0.3)
    cg = sm(lv, 0.74, 0.86) * 0.22
    if cg > 0.005 and b % 2 == 1:
        place(DRM, bt, CLAP, cg, 0.0, SFX_S, 0.25)
    bt += BEAT; b += 1
print('drums', round(time.time() - T0, 1), flush=True)

# lead motif
MOTIF = [(0.0, 69, 0.9), (0.3, 72, 0.8), (0.6, 77, 1.0), (1.2, 76, 1.0)]
def motif(t0, gain, oct_=0):
    for dt, m, g in MOTIF:
        place(MUS, t0 + dt, bell_sig(mtof(m + oct_), 3.0, 1.6, 1.1), gain * g, 0.1, MUS_S, 0.7)
motif(A('title', 0.45), 0.11)
motif(A('premise', 0.2), 0.06)
motif(A('scenes', 0.05), 0.07, 12)
motif(A('art', 0.3), 0.05)
motif(A('speakers', 0.05), 0.06, 12)
motif(A('games', 0.05), 0.07, 12)
motif(A('end', 0.15), 0.12)

# ------------------------------------------------------------------ sound design
UI = 1.8
lp1 = signal.butter(2, 500, 'low', fs=SR, output='sos')
hp1 = signal.butter(2, 2500, 'high', fs=SR, output='sos')
def whoosh(start, length=1.0, gain=0.07, up=True):
    k_ = int(length * SR); nz = rng.standard_normal(k_)
    lo = signal.sosfilt(lp1, nz); hi = signal.sosfilt(hp1, nz) * 0.6
    x = np.linspace(0, 1, k_)
    mix = x if up else 1 - x
    e = np.sin(np.pi * x) ** 2
    s = (lo * (1 - mix) + hi * mix) * e
    pan = np.linspace(-0.7, 0.7, k_)
    th = (pan + 1) * np.pi / 4
    place2(SFX, start - length * 0.5, s * np.cos(th) * 1.4, s * np.sin(th) * 1.4, gain, SFX_S, 0.5)

def riser(end, length=2.2, gain=0.08):
    t = tt(length); k_ = len(t)
    nz = signal.sosfilt(hp1, rng.standard_normal(k_)) * 0.7 + signal.sosfilt(lp1, rng.standard_normal(k_)) * 0.4
    f = 180 * 2 ** (np.linspace(0, 2.6, k_))
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.35
    e = np.linspace(0, 1, k_) ** 2.5
    s = (nz + tone) * e
    place(SFX, end - length, s, gain, 0.0, SFX_S, 0.6)

def impact(start, gain=0.4):
    t = tt(1.6)
    f = 38 + 60 * np.exp(-t * 14)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 3.2)
    s += signal.sosfilt(lp1, rng.standard_normal(len(t))) * np.exp(-t * 9) * 0.5
    place(SFX, start, s, gain, 0.0, SFX_S, 0.35)

def tick(start, gain=0.05, f=2300, pan=0.0):
    t = tt(0.05); s = np.sin(2 * np.pi * f * t) * np.exp(-t * 110)
    s[:int(0.001 * SR)] += rng.standard_normal(int(0.001 * SR)) * 0.4
    place(SFX, start, s, gain * UI, pan, SFX_S, 0.15)

def click(start, up=True, gain=0.08, pan=0.0):
    t = tt(0.09)
    f0, f1 = (900, 1400) if up else (1300, 800)
    f = np.linspace(f0, f1, len(t))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 60)
    place(SFX, start, s, gain * UI, pan, SFX_S, 0.15)

def pop(start, f=880, gain=0.07, pan=0.0):
    t = tt(0.25)
    ff = f * (0.6 + 0.4 * (1 - np.exp(-t * 90)))
    s = np.sin(2 * np.pi * np.cumsum(ff) / SR) * np.exp(-t * 16)
    place(SFX, start, s, gain * UI, pan, SFX_S, 0.3)

def chime(start, m, gain=0.07, pan=0.0, length=1.6):
    place(SFX, start, bell_sig(mtof(m), length, 1.2, 2.2), gain * UI, pan, SFX_S, 0.55)

def success(start, gain=0.07):
    chime(start, 88, gain, 0.1, 1.2); chime(start + 0.09, 95, gain * 0.9, -0.1, 1.4)

def notify(start, gain=0.07):
    chime(start, 81, gain, 0.2, 1.2); chime(start + 0.14, 88, gain, 0.2, 1.4)

def ding_dong(start, gain=0.16):
    place(SFX, start, bell_sig(mtof(76), 2.4, 0.8, 1.1), gain, 0.25, SFX_S, 0.6)
    place(SFX, start + 0.55, bell_sig(mtof(72), 3.0, 0.8, 0.9), gain, 0.25, SFX_S, 0.6)

def keytick(start, gain=0.035, pan=0.0):
    t = tt(0.035)
    s = signal.sosfilt(hp1, rng.standard_normal(len(t))) * np.exp(-t * 160) + np.sin(2 * np.pi * 320 * t) * np.exp(-t * 120) * 0.5
    place(SFX, start, s, gain * UI, pan, SFX_S, 0.1)

def typing(t0, t1, count, gain=0.035, pan=0.0):
    for k_ in range(count):
        tt_ = t0 + (t1 - t0) * (k_ + 0.5) / count + rng.uniform(-0.02, 0.02)
        keytick(tt_, gain * rng.uniform(0.8, 1.1), pan)

def swish(start, gain=0.06, length=0.35, down=False):
    k_ = int(length * SR); nz = rng.standard_normal(k_)
    s = (signal.sosfilt(hp1, nz) if not down else signal.sosfilt(lp1, nz) * 1.6) * np.sin(np.linspace(0, np.pi, k_)) ** 2
    place(SFX, start, s, gain * UI, 0.0, SFX_S, 0.3)

def sparkle(start, gain=0.04):
    for k_, m in enumerate([84, 88, 91, 96]):
        chime(start + k_ * 0.05, m, gain * (1 - 0.15 * k_), (-0.5 + k_ * 0.33), 0.9)

def listen(start, gain=0.05):
    t = tt(0.22); f = np.linspace(620, 940, len(t))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.linspace(0, np.pi, len(t)))
    place(SFX, start, s, gain * UI, 0.0, SFX_S, 0.4)

def fall(start, gain=0.07):
    t = tt(0.7); f = np.linspace(660, 300, len(t))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 4)
    place(SFX, start, s, gain * UI, 0.0, SFX_S, 0.5)

def rise(start, gain=0.06, f0=400, f1=900, length=0.4):
    t = tt(length); f = np.linspace(f0, f1, len(t))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.linspace(0, np.pi, len(t))) ** 0.5 * np.exp(-t * 3)
    place(SFX, start, s, gain * UI, 0.0, SFX_S, 0.4)

# ---- transitions
BIG = {'scenes', 'speakers', 'games', 'phone'}
for k_ in range(1, len(NAMES)):
    nm = NAMES[k_]; s = START[nm]
    if nm in BIG:
        riser(s, 2.4, 0.075); impact(s + 0.02, 0.32)
    elif nm == 'title':
        riser(s + 0.4, 2.0, 0.05); impact(s + 0.42, 0.42)
    elif nm == 'end':
        riser(s + 0.1, 2.6, 0.06); impact(s + 0.12, 0.45)
    elif nm == 'art':
        whoosh(s + 0.2, 1.6, 0.06, up=False)
    else:
        whoosh(s + 0.15, 1.1, 0.055)

# ---- intro / title / premise
chime(A('intro', 0.35), 69, 0.04, -0.2, 2.4); chime(A('intro', 2.15), 72, 0.045, 0.2, 2.4)
for i_, m in enumerate([84, 88, 91]): pop(A('title', 2.25 + 0.12 * i_), mtof(m), 0.05, -0.3 + 0.3 * i_)
for i_ in range(3):
    pop(A('premise', 1.35 + 0.18 * i_), 520 + 90 * i_, 0.07, -0.5 + 0.5 * i_)
    for j_ in range(5): tick(A('premise', 1.3 + 0.18 * i_ + 0.6 + 0.12 * j_), 0.025, 2600 + 120 * j_, -0.5 + 0.5 * i_)

# ---- quiet
swish(A('quiet', 1.4), 0.05, 0.4); click(A('quiet', 2.5), False, 0.06); fall(A('quiet', 2.55), 0.05)
whoosh(A('quiet', 3.2), 0.6, 0.04)
for i_ in range(3): pop(A('quiet', 3.25 + 0.3 * i_), 700 + 120 * i_, 0.06, -0.4 + 0.4 * i_)
pop(A('quiet', 6.55), 600, 0.06)
tick(A('quiet', 7.35)); click(A('quiet', 8.2), False); tick(A('quiet', 8.55)); click(A('quiet', 8.9), False); tick(A('quiet', 9.25)); click(A('quiet', 9.6), True)
rise(A('quiet', 9.95), 0.04, 500, 700, 0.3); swish(A('quiet', 10.5), 0.035, 0.6, True); tick(A('quiet', 11.2), 0.06, 900)
swish(A('quiet', 13.0), 0.04, 0.4, True)

# ---- profiles
for i_ in range(4): pop(A('profiles', 0.52 + 0.12 * i_), 500 + 100 * i_, 0.065, -0.45 + 0.3 * i_)
tick(A('profiles', 2.6)); tick(A('profiles', 3.6)); click(A('profiles', 5.0), True, 0.09)
sparkle(A('profiles', 5.55), 0.045); whoosh(A('profiles', 5.9), 0.9, 0.04)
for i_ in range(4): pop(A('profiles', 6.1 + 0.1 * i_), 700 + 80 * i_, 0.045, -0.4 + 0.27 * i_)
whoosh(A('profiles', 15.4), 0.9, 0.05)
for i_ in range(7): tick(A('profiles', 15.9 + 0.07 * i_), 0.022, 1800 + 140 * i_)
tick(A('profiles', 17.4)); click(A('profiles', 18.0), True); tick(A('profiles', 18.9)); tick(A('profiles', 19.3)); tick(A('profiles', 20.0)); click(A('profiles', 20.2), True)
chime(A('profiles', 21.9), 65, 0.06, 0, 2.5); chime(A('profiles', 22.05), 72, 0.05, 0, 2.5)

# ---- search
typing(A('search', 0.8), A('search', 2.5), 14, 0.03)
whoosh(A('search', 3.2), 0.7, 0.035)
for i_ in range(3): tick(A('search', 3.45 + 0.12 * i_), 0.03, 2000 + 200 * i_)
tick(A('search', 5.0)); pop(A('search', 5.3), 990, 0.05); tick(A('search', 8.5)); click(A('search', 9.5), True); success(A('search', 10.0), 0.065)
whoosh(A('search', 15.45), 0.9, 0.05)
pop(A('search', 16.1), 660, 0.07); sparkle(A('search', 16.3), 0.04); tick(A('search', 19.6)); notify(A('search', 20.2), 0.05)

# ---- voice
for lt in (0.4, 5.6, 10.4, 18.0): listen(A('voice', lt), 0.05)
fall(A('voice', 3.3), 0.06); rise(A('voice', 7.6), 0.06, 900, 1500, 0.35); chime(A('voice', 7.7), 91, 0.04, 0.6, 1.0)
pop(A('voice', 13.3), 760, 0.06); chime(A('voice', 13.4), 84, 0.04); tick(A('voice', 15.4)); click(A('voice', 16.2), True)
success(A('voice', 20.5), 0.06)

# ---- scenes & routines
for i_ in range(4): pop(A('scenes', 0.2 + 0.08 * i_), 480 + 90 * i_, 0.05, -0.45 + 0.3 * i_)
for lt in (1.0, 1.6, 2.2): tick(A('scenes', lt))
click(A('scenes', 3.3), True, 0.09); whoosh(A('scenes', 3.95), 0.9, 0.05)
for i_, m in enumerate([77, 81, 84, 88, 89]): chime(A('scenes', 3.75 + 0.8 + 0.5 * i_), m, 0.05, -0.4 + 0.2 * i_, 1.2); tick(A('scenes', 3.75 + 0.8 + 0.5 * i_), 0.03, 1500)
fall(A('scenes', 4.5), 0.035)
whoosh(A('scenes', 11.3), 0.9, 0.05)
for i_, h in enumerate([6.5, 8.75, 12.75, 17.5, 19.0, 21.0, 22.5]):
    lt = 10.9 + 0.8 + (h - 6) / 17.6 * 8.4
    chime(A('scenes', lt), [76, 79, 81, 84, 86, 88, 91][i_], 0.045, -0.6 + 0.2 * i_, 1.2)
whoosh(A('scenes', 21.3), 0.9, 0.05)
for lt in (21.4, 21.9, 22.5, 22.8, 23.1, 23.4, 23.9): pop(A('scenes', lt + 0.1), 560 + 30 * (lt - 21.4) * 4, 0.05)
tick(A('scenes', 24.9)); click(A('scenes', 25.5), True, 0.09); success(A('scenes', 25.9), 0.065)

# ---- art
swish(A('art', 2.4), 0.03, 0.9); chime(A('art', 2.45), 84, 0.03, 0, 2.0)
whoosh(A('art', 5.8), 1.0, 0.035, up=False)
for k_ in range(6): tick(A('art', 6.1 + 0.06 * k_), 0.02, 2400)
for lt in (7.4, 8.6, 9.8, 11.0, 12.2): tick(A('art', lt), 0.045, 1900); swish(A('art', lt), 0.02, 0.6)
for lt in (14.4, 15.6, 16.8, 18.0): tick(A('art', lt), 0.05, 1200); pop(A('art', lt + 0.02), 300, 0.04)
chime(A('art', 20.5), 65, 0.05, 0, 2.5)

# ---- lights & cameras
click(A('lights', 1.6), True); swish(A('lights', 1.7), 0.03, 1.0, True)
tick(A('lights', 3.5))
for k_ in range(10): tick(A('lights', 4.1 + 0.12 * k_), 0.018, 1500 + 60 * k_)
tick(A('lights', 6.0))
for k_ in range(14): tick(A('lights', 6.2 + 0.24 * k_), 0.016, 2500 - 70 * k_)
tick(A('lights', 9.6)); click(A('lights', 9.8), True)
whoosh(A('lights', 11.35), 0.9, 0.05)
for i_ in range(4): pop(A('lights', 11.0 + 0.08 * i_), 600 + 70 * i_, 0.04)
tick(A('lights', 12.3)); click(A('lights', 13.8), True); whoosh(A('lights', 14.2), 0.8, 0.04)
swish(A('lights', 15.5), 0.03)
swish(A('lights', 17.7), 0.06, 0.8, True); rise(A('lights', 18.5), 0.05, 1200, 1400, 0.3)
swish(A('lights', 21.1), 0.05, 0.6, False); pop(A('lights', 22.2), 990, 0.05); chime(A('lights', 22.3), 88, 0.03)

# ---- doorbell
ding_dong(A('doorbell', 3.0), 0.17)
fall(A('doorbell', 3.35), 0.04)
tick(A('doorbell', 4.6)); click(A('doorbell', 6.1), True); listen(A('doorbell', 6.2), 0.05)
# muffled "voice" through the doorbell speaker
vs = tt(3.2); vlp = signal.butter(2, [300, 1800], 'band', fs=SR, output='sos')
voice_ = signal.sosfilt(vlp, rng.standard_normal(len(vs))) * (0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 3.1 * vs) * np.sin(2 * np.pi * 0.9 * vs)))
voice_ *= adsr(len(vs), 0.2, 0.4)
place(SFX, A('doorbell', 6.4), voice_, 0.03, 0.3, SFX_S, 0.2)
tick(A('doorbell', 9.3)); click(A('doorbell', 9.8), False); rise(A('doorbell', 10.2), 0.05, 500, 800, 0.35)
notify(A('doorbell', 11.85), 0.06)
pop(A('doorbell', 15.45), 600, 0.06)
for lt in (16.8, 17.3, 18.4): tick(A('doorbell', lt))
click(A('doorbell', 17.6), True)

# ---- speakers
rise(A('speakers', 1.2), 0.05, 400, 600, 0.3)
for i_, (lt, m) in enumerate(((1.9, 79), (2.6, 83), (3.3, 86), (4.0, 91))): chime(A('speakers', lt), m, 0.06, [0.1, 0.6, 0.6, -0.6][i_], 1.2); pop(A('speakers', lt), 700, 0.03)
pop(A('speakers', 4.85), 820, 0.05)
whoosh(A('speakers', 10.8), 1.2, 0.05)
pop(A('speakers', 11.3), 520, 0.05)
tick(A('speakers', 13.4), 0.04, 1600, 0.6); tick(A('speakers', 18.2), 0.04, 1600, -0.6)

# ---- bedroom: alarm
al0 = A('bedroom', 1.7); al1 = A('bedroom', 12.0)
tc = al0; k_ = 0
while tc < al1 - 0.1:
    g = 0.05 + 0.05 * min(1.0, (tc - al0) / 4.0)
    for j_, m in enumerate((84, 88, 91)):
        place(SFX, tc + j_ * 0.13, bell_sig(mtof(m), 1.0, 0.6, 3.2), g * (1 if j_ < 2 else 1.1), 0.0, SFX_S, 0.5)
    tc += 1.2; k_ += 1
tick(A('bedroom', 9.7)); tick(A('bedroom', 10.8)); click(A('bedroom', 11.8), False, 0.09)
chime(A('bedroom', 12.25), 77, 0.06, 0, 2.5); chime(A('bedroom', 12.4), 81, 0.05, 0, 2.5); chime(A('bedroom', 12.55), 84, 0.05, 0, 2.5)
whoosh(A('bedroom', 14.4), 1.4, 0.035, up=False)
for i_ in range(4): pop(A('bedroom', 15.35 + 0.2 * i_), 420 + 40 * i_, 0.04)
chime(A('bedroom', 18.7), 65, 0.045, 0, 3.0)

# ---- phone
for lt, d in [(1.6, 'r'), (2.3, 'r'), (3.2, 'ok')]:
    keytick(A('phone', lt), 0.05, -0.5); tick(A('phone', lt + 0.03), 0.045, 2100, 0.4)
click(A('phone', 3.25), True, 0.08, 0.4); whoosh(A('phone', 3.6), 0.6, 0.03)
for i_, lt in enumerate((5.3, 5.75, 6.2)): keytick(A('phone', lt), 0.045, -0.5); pop(A('phone', lt + 0.02), 700 + 80 * i_, 0.045, 0.4)
typing(A('phone', 8.3), A('phone', 10.6), 12, 0.035, -0.5)
for i_ in range(4): pop(A('phone', 10.5 + 0.08 * i_), 600 + 60 * i_, 0.04, 0.4)
tick(A('phone', 11.6), 0.04, 2300, 0.4)
keytick(A('phone', 15.5), 0.05, -0.5); swish(A('phone', 15.8), 0.04, 0.4, True)
keytick(A('phone', 16.35), 0.05, -0.5); whoosh(A('phone', 17.0), 0.9, 0.07); impact(A('phone', 17.4), 0.12); chime(A('phone', 17.42), 89, 0.05, 0.4, 1.4)
keytick(A('phone', 19.05), 0.05, -0.5); swish(A('phone', 19.4), 0.05, 0.8, True)
for i_, lt in enumerate((20.2, 20.4)): pop(A('phone', lt), 760 + 100 * i_, 0.06, [-0.2, 0.6][i_]); chime(A('phone', lt + 0.05), [84, 88][i_], 0.035, [-0.2, 0.6][i_], 1.0)

# ---- games & sports
chime(A('games', 0.95), 79, 0.05); chime(A('games', 1.1), 86, 0.05)
pop(A('games', 3.65), 640, 0.05)
for k_ in range(18):
    lt = 4.3 + 1.7 * (k_ / 18.0) ** 0.8
    tick(A('games', lt), 0.025, 2600 - 50 * k_, 0.5)
success(A('games', 6.05), 0.05)
swish(A('games', 8.8), 0.03, 0.5, True)
pop(A('games', 10.75), 900, 0.05)
pop(A('games', 12.35), 560, 0.06, 0.5)
# crowd bed during sports
cs, ce = A('games', 9.9), A('games', 22.4)
ct = tt(ce - cs)
cbp = signal.butter(2, [350, 2600], 'band', fs=SR, output='sos')
crowd = signal.sosfilt(cbp, rng.standard_normal(len(ct))) * (0.75 + 0.25 * np.sin(2 * np.pi * 0.23 * ct) * np.sin(2 * np.pi * 0.11 * ct + 1))
lt_ = ct + 9.9
lvl = np.interp(lt_, [9.9, 10.8, 14.8, 15.2, 16.4, 17.6, 19.8, 20.7, 22.0, 22.4], [0, .35, .35, 1.0, .9, .4, .4, .8, .8, 0])
crowd *= lvl
place(SFX, cs, crowd, 0.05, 0.0, SFX_S, 0.3)
chime(A('games', 14.9), 88, 0.06, 0.5, 1.2); chime(A('games', 15.02), 93, 0.055, 0.5, 1.3)
pop(A('games', 17.95), 640, 0.05, 0.4)
for k_ in range(8): tick(A('games', 18.7 + 0.11 * k_), 0.02, 2000 - 60 * k_, 0.4)
for k_ in range(8): tick(A('games', 19.8 + 0.11 * k_), 0.02, 1500 + 60 * k_, 0.4)

# ---- rooms
for j_ in range(9): pop(A('rooms', 0.2 + 0.05 * j_), 500 + 40 * j_, 0.03, -0.3 + 0.08 * j_)
for lt in (3.0, 5.6, 8.0):
    tick(A('rooms', lt), 0.05, 2000)
    for j_ in range(9): tick(A('rooms', lt + 0.05 * j_ + 0.15), 0.018, 1400 + 90 * j_, -0.4 + 0.1 * j_)

# ---- end
for i_, m in enumerate([84, 88, 91]): pop(A('end', 1.65 + 0.12 * i_), mtof(m), 0.045, -0.3 + 0.3 * i_)
print('sfx', round(time.time() - T0, 1), flush=True)

# ------------------------------------------------------------------ automation on the music bus
tg = np.arange(n, dtype=np.float64) / SR
mg = np.ones(n)
def dip(a, b, c, d, lvl):
    global mg
    x = np.interp(tg[int(a * SR):int(d * SR)], [a, b, c, d], [1, lvl, lvl, 1])
    mg[int(a * SR):int(d * SR)] = np.minimum(mg[int(a * SR):int(d * SR)], x)
dip(A('doorbell', 2.9), A('doorbell', 3.5), A('doorbell', 10.0), A('doorbell', 10.8), 0.42)
dip(A('bedroom', 18.4), A('bedroom', 19.85), A('phone', -0.05), A('phone', 0.1), 0.3)
dip(A('games', 9.7), A('games', 10.2), A('games', 10.4), A('games', 11.0), 0.6)
# pan the music while it "follows" Matt between rooms
pan = np.zeros(n)
pts_t = [A('speakers', v) for v in (0, 11.6, 12.0, 14.0, 15.4, 17.0, 18.6, 21.6, 22.4)]
pts_p = [0, 0, 0.05, 0.55, 0.55, 0.0, -0.55, -0.55, 0]
i0, i1 = int(pts_t[0] * SR), int(pts_t[-1] * SR)
pan[i0:i1] = np.interp(tg[i0:i1], pts_t, pts_p)
th = (pan + 1) * np.pi / 4
gl = (np.cos(th) * np.sqrt(2)).astype(f32); gr = (np.sin(th) * np.sqrt(2)).astype(f32)
mgf = mg.astype(f32)
mixL = (PAD[0] + MUS[0]) * duck * mgf * gl + sub * duck * mgf
mixR = (PAD[1] + MUS[1]) * duck * mgf * gr + sub * duck * mgf
sendL = (PAD[0] * 0.5 + MUS_S[0]) * mgf * gl + SFX_S[0]
sendR = (PAD[1] * 0.5 + MUS_S[1]) * mgf * gr + SFX_S[1]
drL = DRM[0] * mgf; drR = DRM[1] * mgf
del PAD, MUS, MUS_S

# ------------------------------------------------------------------ reverb (synthetic stereo hall)
irn = int(2.8 * SR); it_ = np.arange(irn) / SR
irs = []
for s_ in range(2):
    ir = rng.standard_normal(irn) * np.exp(-it_ * 2.3)
    ir = signal.sosfilt(signal.butter(1, 5500, 'low', fs=SR, output='sos'), ir)
    ir[:int(0.012 * SR)] = 0
    irs.append((ir / np.sqrt(np.sum(ir ** 2))).astype(f32))
wetL = signal.oaconvolve(sendL, irs[0])[:n].astype(f32)
wetR = signal.oaconvolve(sendR, irs[1])[:n].astype(f32)
print('reverb', round(time.time() - T0, 1), flush=True)

if os.environ.get('STEMS'):
    for nm in NAMES:
        a_, b_ = int(START[nm] * SR), int(min(DUR, START[nm] + DURS[NAMES.index(nm)]) * SR)
        mu = np.sqrt(np.mean((mixL[a_:b_] + drL[a_:b_]) ** 2)); sf = np.sqrt(np.mean(SFX[0][a_:b_] ** 2)); sfp = np.abs(SFX[0][a_:b_]).max()
        wt = np.sqrt(np.mean((wetL[a_:b_] * 0.55) ** 2))
        print('%-9s music %.1f  sfx rms %.1f  sfx peak %.1f  wet %.1f dB' % (nm, 20*np.log10(mu+1e-9), 20*np.log10(sf+1e-9), 20*np.log10(sfp+1e-9), 20*np.log10(wt+1e-9)))
L = mixL + drL + SFX[0] + wetL * 0.8
R = mixR + drR + SFX[1] + wetR * 0.8
fin = np.minimum(1, tg / 1.0) ** 2
fout = np.clip((DUR - tg) / 3.0, 0, 1) ** 1.5
L = L * (fin * fout).astype(f32); R = R * (fin * fout).astype(f32)
hpm = signal.butter(2, 28, 'high', fs=SR, output='sos')
L = signal.sosfilt(hpm, L); R = signal.sosfilt(hpm, R)
peak = max(np.abs(L).max(), np.abs(R).max())
L = L / peak * 1.25; R = R / peak * 1.25
L = np.tanh(L) / np.tanh(1.25) * 0.89; R = np.tanh(R) / np.tanh(1.25) * 0.89
# report loudness per chapter (rms dBFS)
for nm in NAMES:
    a_, b_ = int(START[nm] * SR), int(min(DUR, START[nm] + DURS[NAMES.index(nm)]) * SR)
    r = np.sqrt(np.mean((L[a_:b_] ** 2 + R[a_:b_] ** 2) / 2))
    print('%-9s rms %.1f dBFS' % (nm, 20 * np.log10(r + 1e-9)))
pcm = np.stack([L, R], axis=1)
pcm = (np.clip(pcm, -1, 1) * 32767).astype('<i2')
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'score5.wav')
with wave.open(out, 'wb') as wf:
    wf.setnchannels(2); wf.setsampwidth(2); wf.setframerate(SR); wf.writeframes(pcm.tobytes())
print('wrote', out, round(len(pcm) / SR, 2), 's in', round(time.time() - T0, 1), 's')
