import numpy as np, wave, os, sys
SR = 44100
DUR = 40.2
n = int(SR * DUR)
t = np.arange(n) / SR
rng = np.random.default_rng(7)
L = np.zeros(n); R = np.zeros(n)

def mtof(m): return 440.0 * 2 ** ((m - 69) / 12)
def env_adsr(length, a, r, sr=SR):
    k = int(length * sr)
    e = np.ones(k)
    ka = min(k, int(a * sr)); kr = min(k, int(r * sr))
    if ka: e[:ka] = np.linspace(0, 1, ka) ** 2
    if kr: e[-kr:] *= np.linspace(1, 0, kr) ** 2
    return e
def add(buf, start, sig, gain=1.0):
    i = int(start * SR)
    if i >= n: return
    j = min(n, i + len(sig))
    buf[i:j] += sig[:j - i] * gain

# --- pad chords (every 6.7 s) -------------------------------------------------
chords = [[53, 57, 60, 64], [45, 52, 55, 60, 64], [50, 57, 60, 64, 65], [46, 53, 57, 62], [48, 55, 62, 64], [41, 48, 57, 64]]
cl = 6.7
for ci, ch in enumerate(chords):
    st = ci * cl - (0.0 if ci == 0 else 0.9)
    length = cl + (0.9 if ci else 0) + 1.8
    tt = np.arange(int(length * SR)) / SR
    e = env_adsr(length, 1.6, 2.2)
    for m in ch:
        f = mtof(m)
        for side, det in ((0, 0.9985), (1, 1.0015)):
            sig = (np.sin(2 * np.pi * f * det * tt) + 0.35 * np.sin(2 * np.pi * 2 * f * det * tt + 0.7) + 0.12 * np.sin(2 * np.pi * 3 * f * det * tt)) * e * 0.05
            sig *= 0.75 + 0.25 * np.sin(2 * np.pi * (0.11 + 0.03 * side) * tt + m)
            add(L if side == 0 else R, st, sig)
    root = mtof(ch[0] - 12)
    sub = np.sin(2 * np.pi * root * tt) * e * 0.09
    add(L, st, sub); add(R, st, sub)

# --- plucks -------------------------------------------------------------------
def pluck(start, f, gain, pan):
    length = 1.6
    tt = np.arange(int(length * SR)) / SR
    sig = (np.sin(2 * np.pi * f * tt) + 0.4 * np.sin(2 * np.pi * 2 * f * tt) * np.exp(-tt * 6) + 0.15 * np.sin(2 * np.pi * 3 * f * tt)) * np.exp(-tt * 3.2)
    sig[:int(0.004 * SR)] *= np.linspace(0, 1, int(0.004 * SR))
    add(L, start, sig, gain * (1 - pan)); add(R, start, sig, gain * pan)
step = 0.4
k = 0
tcur = 6.2
while tcur < 38.0:
    ci = min(len(chords) - 1, int(tcur / cl))
    ch = chords[ci]
    pat = [0, 2, 1, 3, 2, 1, 3, 2]
    m = ch[pat[k % 8] % len(ch)] + 24
    g = 0.09 * min(1, (tcur - 6.2) / 3.0) * (1 - min(1, max(0, (tcur - 36.0) / 2.0)))
    pluck(tcur, mtof(m), g, 0.3 if k % 2 == 0 else 0.7)
    tcur += step; k += 1

# --- whooshes at scene changes ----------------------------------------------
def whoosh(start, length=0.9, gain=0.05):
    kk = int(length * SR)
    nz = rng.standard_normal(kk)
    # moving-average low-pass whose cutoff rises through the swell
    out = np.zeros(kk)
    w = 80
    for seg in range(4):
        a = seg * kk // 4; b = (seg + 1) * kk // 4
        ww = max(4, int(w / (1 + seg * 1.6)))
        ker = np.ones(ww) / ww
        out[a:b] = np.convolve(nz, ker, mode='same')[a:b]
    e = np.sin(np.linspace(0, np.pi, kk)) ** 2
    sig = out * e * gain * 6
    add(L, start, sig); add(R, start, sig[::-1])
for s in [2.8, 6.0, 10.0, 18.8, 23.6, 28.6, 32.8, 36.6]:
    whoosh(s)

# --- event blips / chimes ------------------------------------------------------
def blip(start, f, gain=0.12, length=0.5, glide=1.0):
    tt = np.arange(int(length * SR)) / SR
    ff = f * (1 + (glide - 1) * np.minimum(1, tt / length))
    ph = 2 * np.pi * np.cumsum(ff) / SR
    sig = np.sin(ph) * np.exp(-tt * 7)
    add(L, start, sig, gain); add(R, start, sig, gain)
def bell(start, f, gain=0.1):
    length = 2.2
    tt = np.arange(int(length * SR)) / SR
    sig = (np.sin(2 * np.pi * f * tt) + 0.5 * np.sin(2 * np.pi * f * 2.76 * tt) * np.exp(-tt * 2) + 0.3 * np.sin(2 * np.pi * f * 5.4 * tt) * np.exp(-tt * 4)) * np.exp(-tt * 1.6)
    add(L, start, sig, gain); add(R, start, sig, gain)

blip(6.2 + 2.95, 300, 0.10, 0.35, 0.4)        # banner swept
blip(10.2 + 0.6, 660, 0.06, 0.25)            # listening
blip(10.2 + 3.3, 440, 0.08, 0.7, 0.5)        # lights dim (falling)
blip(10.2 + 5.8, 660, 0.06, 0.25)            # listening again
blip(10.2 + 7.7, 990, 0.09, 0.5)             # camera ping
for ta, f in ((1.9, 784), (2.6, 988), (3.3, 1175)):
    blip(23.8 + ta, f, 0.10, 0.45)           # speakers join
bell(28.8 + 1.7, 1319, 0.12); bell(28.8 + 2.4, 1319, 0.08)   # alarm
for i, off in enumerate([0.55, 0.68, 0.81, 0.94, 1.07, 1.2]):
    blip(33.0 + off, 880 + 110 * i, 0.04, 0.3)  # grid pops
bell(36.8 + 0.6, 659, 0.07)

# --- master --------------------------------------------------------------------
fin = np.minimum(1, t / 1.2) ** 2
fout = np.minimum(1, (DUR - t) / 2.0) ** 2
L *= fin * fout; R *= fin * fout
# gentle reverb-ish smear
def smear(x):
    out = x.copy()
    for d, g in ((0.043, 0.28), (0.071, 0.2), (0.113, 0.14)):
        k_ = int(d * SR); out[k_:] += x[:-k_] * g
    return out
L = smear(L); R = smear(R)
peak = max(np.abs(L).max(), np.abs(R).max())
L = L / peak * 0.72; R = R / peak * 0.72
pcm = np.stack([L, R], axis=1)
pcm = (np.clip(pcm, -1, 1) * 32767).astype('<i2')
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'soundtrack.wav')
with wave.open(out, 'wb') as wf:
    wf.setnchannels(2); wf.setsampwidth(2); wf.setframerate(SR); wf.writeframes(pcm.tobytes())
print('wrote', out, round(len(pcm) / SR, 1), 's')
