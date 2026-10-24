import sys, os, time, base64
from playwright.sync_api import sync_playwright
# Software GL (SwiftShader) gives identical frames on any machine. Set HAVEN_GPU=1 to try your GPU instead (much faster when it works).
ARGS = ['--ignore-gpu-blocklist', '--allow-file-access-from-files', '--disable-web-security'] + ([] if os.environ.get('HAVEN_GPU') else ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'])
"""Time full frames: python3 tools/avg.py 12 30 60 90"""
ts = [float(x) for x in sys.argv[1:]]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
with sync_playwright() as p:
    b = p.chromium.launch(args=ARGS)
    pg = b.new_page(viewport={'width': 1920, 'height': 1080})
    pg.on('pageerror', lambda e: print('pageerror:', e))
    pg.goto('file://' + os.path.join(ROOT, 'index.html') + os.environ.get('Q', '')); pg.wait_for_function('window.ready === true', timeout=180000)
    cdp = pg.context.new_cdp_session(pg)
    tot = 0; n = 0
    for t in ts:
        pg.evaluate('window.renderAt(%f)' % (t - 1 / 30))
        t0 = time.time(); pg.evaluate('window.renderAt(%f)' % t); r = cdp.send('Page.captureScreenshot', {'format': 'png', 'optimizeForSpeed': True}); dt = time.time() - t0
        tot += dt; n += 1; print('t=%.1f %.2fs' % (t, dt), flush=True)
    print('AVG %.2f s/frame' % (tot / n))
    b.close()
