import sys, os, time, base64, io
from playwright.sync_api import sync_playwright
from PIL import Image
"""Render stills of the 3D film: python3 shoot.py out.png 12.5 [30 60 ...]  (several times -> contact sheet)."""
HERE = os.path.dirname(os.path.abspath(__file__))
times = [float(x) for x in sys.argv[2:]]; out = sys.argv[1]
# Software GL (SwiftShader) gives identical frames on any machine. Set HAVEN_GPU=1 to try your GPU instead (much faster when it works).
ARGS = ['--ignore-gpu-blocklist', '--allow-file-access-from-files', '--disable-web-security'] + ([] if os.environ.get('HAVEN_GPU') else ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'])
with sync_playwright() as p:
    b = p.chromium.launch(args=ARGS)
    pg = b.new_page(viewport={'width': 1920, 'height': 1080})
    pg.on('console', lambda m: print('console:', m.text)); pg.on('pageerror', lambda e: print('pageerror:', e))
    pg.goto('file://' + os.path.join(HERE, 'index.html') + os.environ.get('Q', ''))
    pg.wait_for_function('window.ready === true', timeout=180000)
    cdp = pg.context.new_cdp_session(pg)
    ims = []
    for t in times:
        t0 = time.time()
        pg.evaluate('window.renderAt(%f)' % t)
        r = cdp.send('Page.captureScreenshot', {'format': 'png', 'optimizeForSpeed': True})
        im = Image.open(io.BytesIO(base64.b64decode(r['data']))).convert('RGB')
        ims.append((t, im)); print('t=%.2f %.2fs' % (t, time.time() - t0), flush=True)
    b.close()
if len(ims) == 1:
    ims[0][1].save(out)
else:
    from PIL import ImageDraw
    cols = 3; rows = (len(ims) + cols - 1) // cols; tw, th = 640, 360
    S = Image.new('RGB', (cols * tw + (cols + 1) * 6, rows * (th + 26) + 6), (40, 40, 40)); d = ImageDraw.Draw(S)
    for k, (t, im) in enumerate(ims):
        x = 6 + (k % cols) * (tw + 6); y = 6 + (k // cols) * (th + 26); S.paste(im.resize((tw, th), Image.LANCZOS), (x, y)); d.text((x + 4, y + th + 4), 't=%.1f' % t, fill=(230, 230, 230))
    S.save(out)
print('saved', out)
