import sys
from PIL import Image, ImageDraw
import render5 as R
from render import F
def sheet(times, outp, cols=4, tw=480, th=270):
    rows = (len(times) + cols - 1) // cols
    S = Image.new('RGB', (cols * tw + (cols + 1) * 6, rows * (th + 22) + (rows + 1) * 6), (50, 50, 50))
    d = ImageDraw.Draw(S)
    for k, T in enumerate(times):
        im = Image.frombytes('RGB', (1920, 1080), R.render5(int(round(T * 30)))).resize((tw, th), Image.BILINEAR)
        x = 6 + (k % cols) * (tw + 6); y = 6 + (k // cols) * (th + 28)
        S.paste(im, (x, y)); d.text((x + 4, y + th + 2), '%.1f s' % T, fill=(230, 230, 230), font=F('B7', 16))
    S.save(outp)
a, b, step, outp = float(sys.argv[1]), float(sys.argv[2]), float(sys.argv[3]), sys.argv[4]
ts = []; t = a
while t < b - 1e-6: ts.append(t); t += step
sheet(ts, outp)
print(len(ts), 'frames ->', outp)
