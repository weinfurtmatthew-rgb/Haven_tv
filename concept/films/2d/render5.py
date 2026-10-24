import sys, os, subprocess, time
from common5 import *
from tl import NAMES, START, DURS, TOTAL, span
import scenes_a as SA
try:
    import scenes_b as SB
except ImportError:
    SB = None
try:
    import scenes_c as SCc
except ImportError:
    SCc = None

N5 = int(round(TOTAL * FPS))

def _blank(t, d): return layer()
def _f(mod, name):
    return getattr(mod, name, _blank) if mod is not None else _blank

FUNCS = {'intro': SA.sc_intro5, 'title': SA.sc_title5, 'premise': SA.sc_premise, 'quiet': SA.sc_quiet, 'profiles': SA.sc_profiles,
         'search': SA.sc_search, 'voice': SA.sc_voice5,
         'scenes': _f(SB, 'sc_scenes'), 'art': _f(SB, 'sc_art5'), 'lights': _f(SB, 'sc_lights'), 'doorbell': _f(SB, 'sc_doorbell'),
         'speakers': _f(SB, 'sc_speakers'), 'bedroom': _f(SB, 'sc_bedroom'),
         'phone': _f(SCc, 'sc_phone'), 'games': _f(SCc, 'sc_games'), 'rooms': _f(SCc, 'sc_rooms'), 'end': _f(SCc, 'sc_end5')}
SC5 = [(span(n)[0], span(n)[1], FUNCS[n], n) for n in NAMES]

GLOW = {'intro': (.35, 0, 0, 0), 'title': (1.1, .2, 0, 0), 'premise': (.7, .25, .12, 0), 'quiet': (.55, 0, 0, 0),
        'profiles': (.4, .55, 0, .15), 'search': (.6, .2, 0, .1), 'voice': (.55, .35, 0, 0), 'scenes': (.3, .6, .3, 0),
        'art': (.4, 0, .3, 0), 'lights': (.2, 0, .45, 0), 'doorbell': (.45, 0, 0, .3), 'speakers': (.3, .85, 0, 0),
        'bedroom': (.1, 0, .25, 0), 'phone': (.6, .3, 0, 0), 'games': (.2, .7, 0, .4), 'rooms': (.6, .2, 0, 0), 'end': (1.0, .2, .08, 0)}

def glow_vec(name, lt):
    c, v, w, r = GLOW[name]
    if name == 'lights':
        w = w + 0.55 * e_io(prog(lt, 6.2, 9.6)) * (1 - e_io(prog(lt, 10.4, 11.4)))
    if name == 'bedroom':
        w = 0.15 + 0.85 * e_io(prog(lt, 1.7, 7.0)) * (1 - e_io(prog(lt, 13.0, 14.5))) + 0.2 * (1 - e_io(prog(lt, 14.5, 19.0)))
        c = 0.1 * (1 - prog(lt, 15, 19))
    if name == 'doorbell':
        r = r + 0.5 * trap(lt, 2.9, 3.3, 4.5, 6.0)
    if name == 'games':
        r = r * (1 - e_io(prog(lt, 9.5, 11.0))); c = c + 0.4 * e_io(prog(lt, 9.5, 11.0))
    return (c, v, w, r)

_noise5 = None
def bg5(T):
    global _noise5
    if _noise5 is None:
        _noise5 = Image.fromarray(np.random.default_rng(3).integers(0, 3, (H, W, 3), dtype=np.uint8))
    i = 0
    for k, n in enumerate(NAMES):
        if START[n] <= T + 0.4: i = k
    n = NAMES[i]
    vec = glow_vec(n, T - START[n])
    if i > 0:
        q = e_io(prog(T, START[n] - 0.4, START[n] + 0.8))
        if q < 1:
            pv = glow_vec(NAMES[i - 1], T - START[NAMES[i - 1]])
            vec = tuple(lerp(pv[j], vec[j], q) for j in range(4))
    fin = e_io(prog(T, 0, 1.5)) * (1 - e_io(prog(T, TOTAL - 1.2, TOTAL)))
    c, v, w, r = [x * fin for x in vec]
    g = glow_img([(0.5, 0.52, 0.75, (30, 80, 115), c), (0.75, 0.65, 0.8, (70, 48, 130), v),
                  (0.5, 1.05, 1.0, (255, 140, 50), w), (0.22, 0.75, 0.7, (150, 52, 48), r)])
    return ImageChops.add(Image.new('RGB', (W, H), BG), g)

def render5(i):
    T = i / FPS
    im = bg5(T).convert('RGBA')
    for s, e, fn, n in SC5:
        if s <= T < e:
            t = T - s; dd = e - s
            Lr = fn(t, dd)
            k = min(e_io(prog(t, 0, 0.45)), 1 - e_io(prog(t, dd - 0.5, dd)))
            if k < 0.999: Lr = scale_alpha(Lr, k)
            im.alpha_composite(Lr)
    rgb = ImageChops.add(im.convert('RGB'), _noise5, 1.0, -1)
    return rgb.tobytes()

if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'test':
        out = os.path.join(HERE, 'test5'); os.makedirs(out, exist_ok=True)
        for a in sys.argv[2:]:
            if ':' in a:
                nm, lt = a.split(':'); T = START[nm] + float(lt)
            else: T = float(a)
            t0 = time.time(); b = render5(int(round(T * FPS)))
            Image.frombytes('RGB', (W, H), b).save(os.path.join(out, '%s.png' % a.replace(':', '_')))
            print(a, round(T, 2), round(time.time() - t0, 2), 's', flush=True)
    elif mode == 'sheet':
        # contact sheet of given specs
        specs = sys.argv[3:]; outp = sys.argv[2]
        ims = []
        for a in specs:
            nm, lt = a.split(':'); T = START[nm] + float(lt)
            ims.append(Image.frombytes('RGB', (W, H), render5(int(round(T * FPS)))).resize((640, 360), Image.BILINEAR))
        cols = 3; rows = (len(ims) + cols - 1) // cols
        S = Image.new('RGB', (cols * 640 + (cols + 1) * 8, rows * 360 + (rows + 1) * 8), (60, 60, 60))
        for k, im in enumerate(ims): S.paste(im, (8 + (k % cols) * 648, 8 + (k // cols) * 368))
        S.save(outp); print('sheet', outp)
    elif mode == 'full':
        audio = sys.argv[2]; outp = sys.argv[3]
        a0 = int(sys.argv[4]) if len(sys.argv) > 4 else 0; a1 = int(sys.argv[5]) if len(sys.argv) > 5 else N5
        cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', '%dx%d' % (W, H), '-r', str(FPS), '-i', '-']
        if audio != 'none': cmd += ['-i', audio, '-c:a', 'aac', '-b:a', '192k', '-shortest']
        cmd += ['-c:v', 'libx264', '-preset', 'fast', '-crf', '17', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', outp]
        pr = subprocess.Popen(cmd, stdin=subprocess.PIPE)
        t0 = time.time()
        with Pool(2) as pool:
            for n, b in enumerate(pool.imap(render5, range(a0, a1), chunksize=4)):
                pr.stdin.write(b)
                if n % 150 == 0: print('frame', a0 + n, '/', a1, round(time.time() - t0), 's', flush=True)
        pr.stdin.close(); pr.wait(); print('done', round(time.time() - t0), 's', flush=True)
