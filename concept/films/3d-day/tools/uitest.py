import sys, json, os, base64
from playwright.sync_api import sync_playwright
"""Draw TV screens flat (no 3D), e.g. python3 tools/uitest.py '[["bedBrief",2.5],["kid",5.5]]' ui.png"""
items = json.loads(sys.argv[1]); out = sys.argv[2]
HERE = os.path.dirname(os.path.abspath(__file__))
from PIL import Image
import io
with sync_playwright() as p:
    b = p.chromium.launch(args=['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--allow-file-access-from-files'])
    pg = b.new_page(viewport={'width': 1920, 'height': 1080})
    pg.on('console', lambda m: print('console:', m.text)); pg.on('pageerror', lambda e: print('pageerror:', e))
    pg.goto('file://' + os.path.join(HERE, 'uitest.html')); pg.wait_for_function('window.ready === true', timeout=60000)
    ims = []
    for it in items:
        name, lt = it[0], it[1]; extra = json.dumps(it[2]) if len(it) > 2 else None
        pg.evaluate('([n,l,e]) => window.draw(n,l,e)', [name, lt, extra])
        ims.append(Image.open(io.BytesIO(pg.screenshot())).convert('RGB').resize((960, 540)))
    b.close()
cols = 2; rows = (len(ims) + 1) // 2
S = Image.new('RGB', (cols * 960 + 30, rows * 540 + (rows + 1) * 10), (60, 60, 60))
for k, im in enumerate(ims): S.paste(im, (10 + (k % 2) * 970, 10 + (k // 2) * 550))
S.save(out); print('ok', out)
