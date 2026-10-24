"""Regenerate js/icons.js from lucide-static (npm install first). Add icon names to the list below."""
import re, json, os
os.chdir(os.path.dirname(os.path.abspath(__file__)))
names = ['sun','moon','cloud-sun','bell','bell-ring','shield-check','package','lock','play','pause','mic','music','lamp','lightbulb','tv','cast','calendar','car','bus','coffee','utensils','timer','clock','camera','video','door-open','house','blinds','speaker','volume-2','star','heart','gamepad-2','book-open','backpack','check','sparkles','sunrise','sunset','bed','users','user','chevron-right','pizza','popcorn','thermometer','alarm-clock','hand','smile','film','clapperboard','circle-check','list-checks','plus','wind','droplets','cloud','x','pencil','palette','image','images','sofa','chef-hat','flame','dumbbell','briefcase','school','graduation-cap','baby','layout-grid','sliders-horizontal','wifi','map-pin','navigation','zap','leaf','bike','waves','tent','rocket','dog','cat','ghost','apple','sandwich','lamp-desk','lamp-floor','house-wifi','radio','headphones','skip-forward','tv-minimal','monitor-play','hourglass','moon-star','cloud-moon','door-closed','key-round','scan-face','user-round','users-round','shield','tablet-smartphone','smartphone']
base = 'node_modules/lucide-static/icons/'
out = {}
def num(s): return float(s)
for nm in names:
    p = base + nm + '.svg'
    if not os.path.exists(p): print('missing', nm); continue
    svg = open(p).read()
    ds = []
    for tag, attrs in re.findall(r'<(path|circle|rect|line|polyline|polygon|ellipse)\s([^>]*?)/?>', svg):
        a = dict(re.findall(r'([a-zA-Z0-9-]+)="([^"]*)"', attrs))
        if tag == 'path': ds.append(a['d'])
        elif tag == 'circle':
            cx, cy, r = num(a['cx']), num(a['cy']), num(a['r'])
            ds.append('M%g %gA%g %g 0 1 0 %g %gA%g %g 0 1 0 %g %gZ' % (cx - r, cy, r, r, cx + r, cy, r, r, cx - r, cy))
        elif tag == 'ellipse':
            cx, cy, rx, ry = num(a['cx']), num(a['cy']), num(a['rx']), num(a['ry'])
            ds.append('M%g %gA%g %g 0 1 0 %g %gA%g %g 0 1 0 %g %gZ' % (cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy))
        elif tag == 'rect':
            x, y, w, h = num(a.get('x', 0)), num(a.get('y', 0)), num(a['width']), num(a['height'])
            rx = num(a.get('rx', a.get('ry', 0))); ry = num(a.get('ry', rx))
            if rx > 0:
                ds.append('M%g %gH%gA%g %g 0 0 1 %g %gV%gA%g %g 0 0 1 %g %gH%gA%g %g 0 0 1 %g %gV%gA%g %g 0 0 1 %g %gZ' % (
                    x + rx, y, x + w - rx, rx, ry, x + w, y + ry, y + h - ry, rx, ry, x + w - rx, y + h, x + rx, rx, ry, x, y + h - ry, y + ry, rx, ry, x + rx, y))
            else:
                ds.append('M%g %gH%gV%gH%gZ' % (x, y, x + w, y + h, x))
        elif tag == 'line':
            ds.append('M%s %sL%s %s' % (a['x1'], a['y1'], a['x2'], a['y2']))
        elif tag in ('polyline', 'polygon'):
            pts = re.findall(r'-?[\d.]+', a['points'])
            pp = [(pts[i], pts[i + 1]) for i in range(0, len(pts), 2)]
            d = 'M' + ' L'.join('%s %s' % q for q in pp)
            if tag == 'polygon': d += 'Z'
            ds.append(d)
    out[nm] = ds
open('js/icons.js', 'w').write('// Lucide icons (ISC license), converted to Path2D data\nexport const ICONS = ' + json.dumps(out) + ';\n')
print(len(out), 'icons')
