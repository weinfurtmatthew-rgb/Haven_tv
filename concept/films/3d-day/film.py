import sys, os, time, base64, subprocess
from playwright.sync_api import sync_playwright
FPS = 30; DUR = 120.0; N = int(DUR * FPS); CH = 450   # 15 s chunks
# Software GL (SwiftShader) gives identical frames on any machine. Set HAVEN_GPU=1 to try your GPU instead (much faster when it works).
ARGS = ['--ignore-gpu-blocklist', '--allow-file-access-from-files', '--disable-web-security'] + ([] if os.environ.get('HAVEN_GPU') else ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'])
"""Full render: python3 film.py  (resumable; renders 15 s chunks into chunks/, then muxes day_score.wav if present).
Re-render only some chunks: python3 film.py 3 4  (delete those chunk files first)."""
HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
os.makedirs('chunks', exist_ok=True)
only = [int(x) for x in sys.argv[1:]] if len(sys.argv) > 1 else None
with sync_playwright() as p:
    b = p.chromium.launch(args=ARGS)
    pg = b.new_page(viewport={'width': 1920, 'height': 1080})
    pg.on('pageerror', lambda e: print('pageerror:', e, flush=True))
    pg.goto('file://' + os.path.join(HERE, 'index.html')); pg.wait_for_function('window.ready === true', timeout=300000)
    cdp = pg.context.new_cdp_session(pg)
    T0 = time.time(); done = 0
    for c in range(0, (N + CH - 1) // CH):
        if only is not None and c not in only: continue
        outp = 'chunks/c%02d.mp4' % c
        if os.path.exists(outp) and os.path.getsize(outp) > 1000: print('skip', outp, flush=True); continue
        a0, a1 = c * CH, min(N, (c + 1) * CH)
        if a0 > 0: pg.evaluate('window.renderAt(%f)' % ((a0 - 1) / FPS))   # warm state with the previous frame
        tmp = outp + '.part.mp4'
        ff = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', str(FPS), '-c:v', 'png', '-i', '-',
                               '-vf', 'noise=alls=2:allf=t', '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-pix_fmt', 'yuv420p', '-r', str(FPS), tmp], stdin=subprocess.PIPE)
        for i in range(a0, a1):
            pg.evaluate('window.renderAt(%f)' % (i / FPS))
            r = cdp.send('Page.captureScreenshot', {'format': 'png', 'optimizeForSpeed': True})
            ff.stdin.write(base64.b64decode(r['data'])); done += 1
            if i % 30 == 0: print('frame %d/%d  %.0fs elapsed  %.2f s/frame' % (i, N, time.time() - T0, (time.time() - T0) / max(1, done)), flush=True)
        ff.stdin.close(); ff.wait(); os.replace(tmp, outp); print('chunk done', outp, flush=True)
    b.close()
print('ALL DONE', flush=True)
chunks = ['chunks/c%02d.mp4' % c for c in range((N + CH - 1) // CH)]
if all(os.path.exists(c) for c in chunks):
    open('chunks/list.txt', 'w').write(''.join("file '%s'\n" % os.path.basename(c) for c in chunks))
    cmd = ['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', 'chunks/list.txt']
    if os.path.exists('day_score.wav'): cmd += ['-i', 'day_score.wav', '-map', '0:v', '-map', '1:a', '-c:a', 'aac', '-b:a', '192k', '-shortest']
    cmd += ['-c:v', 'copy', '-movflags', '+faststart', 'haven_day_in_the_life.mp4']
    subprocess.run(cmd, check=True); print('wrote haven_day_in_the_life.mp4', flush=True)
