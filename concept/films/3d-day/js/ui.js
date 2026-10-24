// Haven UI kit + every TV screen. All screens are designed on a 1920x1080 logical canvas.
import { ICONS } from './icons.js';
import { clamp, prog, lerp, eout, eio, eback, trap, hash } from './kit.js';

export const C = {
  ink: '#0B0E14', txt: '#F4F6F9', acc: '#7DD3FC', warm: '#F7B955', coral: '#FF8A7A', violet: '#B9A2FF', green: '#86EFAC', rose: '#F9A8D4',
  matt: '#5B8DEF', sam: '#A78BFA', lily: '#FF8A7A',
};
const F = (w, s) => `${w} ${s}px Inter`;
const P2 = {};
const path2d = (n) => (P2[n] ??= (ICONS[n] || []).map((d) => new Path2D(d)));

export function T(g, s, x, y, o = {}) {
  const a = o.a ?? 1; if (a <= 0.003) return;
  g.save(); g.globalAlpha *= a;
  g.font = o.fam ? `${o.w ?? 500} ${o.s ?? 32}px ${o.fam}` : F(o.w ?? 500, o.s ?? 32);
  g.fillStyle = o.c ?? C.txt; g.textAlign = o.al ?? 'left'; g.textBaseline = o.bl ?? 'alphabetic';
  g.letterSpacing = (o.ls ?? 0) + 'px';
  if (o.blur) g.filter = `blur(${o.blur}px)`;
  if (o.glow) { g.shadowColor = o.glow; g.shadowBlur = o.glowB ?? 30; }
  g.fillText(s, x, y); g.restore();
}
export function TW(g, s, w, size, ls = 0) { g.save(); g.font = F(w, size); g.letterSpacing = ls + 'px'; const m = g.measureText(s).width; g.restore(); return m; }
export function rr(g, x, y, w, h, r) { g.beginPath(); g.roundRect(x, y, w, h, r); }
export function glass(g, x, y, w, h, r, o = {}) {
  const a = o.a ?? 1; if (a <= 0.003) return;
  g.save(); g.globalAlpha *= a;
  if (o.shadow === true) { g.shadowColor = `rgba(0,0,0,${o.sh ?? 0.25})`; g.shadowBlur = o.shb ?? 46; g.shadowOffsetY = 14; }
  rr(g, x, y, w, h, r); g.fillStyle = o.fill ?? 'rgba(255,255,255,0.085)'; g.fill();
  g.shadowColor = 'transparent';
  const gr = g.createLinearGradient(0, y, 0, y + h); gr.addColorStop(0, `rgba(255,255,255,${o.edge ?? 0.24})`); gr.addColorStop(0.5, 'rgba(255,255,255,0.06)'); gr.addColorStop(1, 'rgba(255,255,255,0.03)');
  g.strokeStyle = gr; g.lineWidth = 1.6; rr(g, x + 0.8, y + 0.8, w - 1.6, h - 1.6, r); g.stroke();
  g.restore();
}
export function fillRR(g, x, y, w, h, r, fill, a = 1) { if (a <= 0.003) return; g.save(); g.globalAlpha *= a; rr(g, x, y, w, h, r); g.fillStyle = fill; g.fill(); g.restore(); }
export function icon(g, n, cx, cy, size, color, lw = 1.9, a = 1) {
  if (a <= 0.003) return;
  g.save(); g.globalAlpha *= a; g.translate(cx - size / 2, cy - size / 2); g.scale(size / 24, size / 24);
  g.strokeStyle = color; g.lineWidth = lw; g.lineCap = 'round'; g.lineJoin = 'round';
  for (const p of path2d(n)) g.stroke(p); g.restore();
}
export function circ(g, cx, cy, r, fill, a = 1) { if (a <= 0.003 || r <= 0) return; g.save(); g.globalAlpha *= a; g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.fillStyle = fill; g.fill(); g.restore(); }
export function arcRing(g, cx, cy, r, lw, frac, color, track = 'rgba(255,255,255,0.12)', a = 1) {
  g.save(); g.globalAlpha *= a; g.lineCap = 'round'; g.lineWidth = lw;
  g.strokeStyle = track; g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.stroke();
  if (frac > 0.002) { g.strokeStyle = color; g.beginPath(); g.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * clamp(frac)); g.stroke(); }
  g.restore();
}
export function avatar(g, cx, cy, r, color, ch, a = 1) {
  circ(g, cx, cy, r, color, a);
  const gr = g.createLinearGradient(cx, cy - r, cx, cy + r); gr.addColorStop(0, 'rgba(255,255,255,0.28)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
  circ(g, cx, cy, r, gr, a);
  T(g, ch, cx, cy + r * 0.36, { s: r * 1.0, w: 650, al: 'center', a });
}
export function check(g, cx, cy, r, color = C.green, a = 1, ink = '#0B0E14') {
  circ(g, cx, cy, r, color, a);
  g.save(); g.globalAlpha *= a; g.strokeStyle = ink; g.lineWidth = r * 0.22; g.lineCap = 'round'; g.lineJoin = 'round';
  g.beginPath(); g.moveTo(cx - r * 0.42, cy + r * 0.02); g.lineTo(cx - r * 0.12, cy + r * 0.32); g.lineTo(cx + r * 0.44, cy - r * 0.3); g.stroke(); g.restore();
}
export function pill(g, x, y, h, label, o = {}) {
  const s = o.s ?? 28, w = TW(g, label, o.w ?? 600, s) + (o.icon ? h * 0.9 : 0) + h * 0.9;
  const x0 = o.al === 'right' ? x - w : o.al === 'center' ? x - w / 2 : x;
  if (o.glass) glass(g, x0, y, w, h, h / 2, { a: o.a ?? 1, shadow: false }); else fillRR(g, x0, y, w, h, h / 2, o.fill ?? 'rgba(255,255,255,0.12)', o.a ?? 1);
  let tx = x0 + h * 0.45;
  if (o.icon) { icon(g, o.icon, tx + h * 0.3, y + h / 2, h * 0.5, o.ic ?? (o.c ?? C.txt), 2, o.a ?? 1); tx += h * 0.82; }
  T(g, label, tx, y + h / 2 + s * 0.36, { s, w: o.w ?? 600, c: o.c ?? C.txt, a: o.a ?? 1 });
  return w;
}
export function focusRing(g, x, y, w, h, r, a = 1, pad = 10) {
  if (a <= 0.003) return;
  g.save(); g.globalAlpha *= a; g.shadowColor = 'rgba(255,255,255,0.55)'; g.shadowBlur = 22;
  g.strokeStyle = 'rgba(255,255,255,0.95)'; g.lineWidth = 4; rr(g, x - pad, y - pad, w + pad * 2, h + pad * 2, r + pad); g.stroke(); g.restore();
}
const hexA = (h, a) => { const n = parseInt(h.slice(1), 16); return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`; };
export function blob(g, x, y, r, col, a) { const gr = g.createRadialGradient(x, y, 0, x, y, r); gr.addColorStop(0, hexA(col, a)); gr.addColorStop(0.55, hexA(col, a * 0.45)); gr.addColorStop(1, hexA(col, 0)); g.fillStyle = gr; g.fillRect(x - r, y - r, r * 2, r * 2); }
let NOISE = null;
function noisePattern(g) {
  if (!NOISE) {
    const c = document.createElement('canvas'); c.width = c.height = 256; const x = c.getContext('2d'); const im = x.createImageData(256, 256);
    for (let i = 0; i < 256 * 256; i++) { const v = Math.floor(hash(i * 1.37) * 255); im.data[i * 4] = im.data[i * 4 + 1] = im.data[i * 4 + 2] = v; im.data[i * 4 + 3] = 255; }
    x.putImageData(im, 0, 0); NOISE = c;
  }
  return g.createPattern(NOISE, 'repeat');
}
export function grain(g, a = 0.035) { g.save(); g.globalAlpha *= a; g.globalCompositeOperation = 'overlay'; g.fillStyle = noisePattern(g); g.fillRect(0, 0, 1920, 1080); g.restore(); }
const AUR = new Map();
export function aurora(g, base, blobs, t = 0) {
  const key = base + JSON.stringify(blobs);
  let c = AUR.get(key);
  if (!c) {
    c = document.createElement('canvas'); c.width = 1920; c.height = 1080; const x = c.getContext('2d');
    x.fillStyle = base; x.fillRect(0, 0, 1920, 1080);
    for (const [bx, by, r, col, a] of blobs) blob(x, bx, by, r, col, a);
    grain(x, 0.03); AUR.set(key, c);
  }
  g.drawImage(c, 0, 0, 1920, 1080);
}
const ap = (lt, t0, d = 0.8) => { const p = eout(prog(lt, t0, t0 + d)); return { a: clamp(p * 1.3), y: (1 - p) * 36, p }; };
function digitsW(g, s, x, y, size, w, slot, o = {}) {
  // fixed-width digits so ticking numbers do not jitter
  let tw = 0; for (const ch of s) tw += /[0-9]/.test(ch) ? slot : TW(g, ch, w, size) * 0.9;
  let cx = o.al === 'center' ? x - tw / 2 : x;
  for (const ch of s) { const cw = /[0-9]/.test(ch) ? slot : TW(g, ch, w, size) * 0.9; T(g, ch, cx + cw / 2, y, { s: size, w, al: 'center', a: o.a, c: o.c }); cx += cw; }
}

// ============================================================== artworks
export function paintDusk(g, x, y, w, h) {
  g.save(); rr(g, x, y, w, h, 2); g.clip();
  let gr = g.createLinearGradient(0, y, 0, y + h); gr.addColorStop(0, '#16455A'); gr.addColorStop(0.62, '#2C6C78'); gr.addColorStop(0.621, '#1D4E5B'); gr.addColorStop(1, '#173F4A');
  g.fillStyle = gr; g.fillRect(x, y, w, h);
  const sx = x + w * 0.74, sy = y + h * 0.3; blob(g, sx, sy, w * 0.22, '#F6C66B', 0.35);
  circ(g, sx, sy, w * 0.085, '#E9A43C');
  const dome = (cx, bw, bh, col, col2) => { const b = y + h * 0.78; const gg = g.createLinearGradient(cx - bw / 2, 0, cx + bw / 2, 0); gg.addColorStop(0, col); gg.addColorStop(1, col2); g.fillStyle = gg; g.beginPath();
    if (bh <= bw / 2) { g.ellipse(cx, b, bw / 2, bh, 0, Math.PI, 0); g.closePath(); }
    else { g.moveTo(cx - bw / 2, b); g.lineTo(cx - bw / 2, b - bh + bw / 2); g.arc(cx, b - bh + bw / 2, bw / 2, Math.PI, 0); g.lineTo(cx + bw / 2, b); g.closePath(); }
    g.fill(); };
  dome(x + w * 0.25, w * 0.36, h * 0.42, '#D46A4C', '#B9533A');
  dome(x + w * 0.52, w * 0.4, h * 0.24, '#F3E6CF', '#DCCBAE');
  dome(x + w * 0.72, w * 0.2, h * 0.5, '#9BC2B0', '#7FA996');
  g.fillStyle = '#F3EEE6'; g.fillRect(x, y + h * 0.78, w, h * 0.008);
  grain(g, 0.06); g.restore();
}
export function paintLake(g, x, y, w, h) {
  g.save(); rr(g, x, y, w, h, 2); g.clip();
  let gr = g.createLinearGradient(0, y, 0, y + h * 0.62); gr.addColorStop(0, '#8EBFE6'); gr.addColorStop(1, '#F4D9B8'); g.fillStyle = gr; g.fillRect(x, y, w, h);
  blob(g, x + w * 0.78, y + h * 0.26, w * 0.2, '#FFF1C9', 0.6); circ(g, x + w * 0.78, y + h * 0.26, w * 0.045, '#FFF4D6');
  const hill = (pts, col) => { g.fillStyle = col; g.beginPath(); g.moveTo(x, y + h * 0.66); for (const [u, v] of pts) g.lineTo(x + w * u, y + h * v); g.lineTo(x + w, y + h * 0.66); g.closePath(); g.fill(); };
  hill([[0, 0.42], [0.12, 0.34], [0.3, 0.44], [0.46, 0.4], [0.6, 0.5], [1, 0.48]], '#7E9FB3');
  hill([[0, 0.52], [0.2, 0.45], [0.42, 0.56], [0.7, 0.5], [0.9, 0.58], [1, 0.56]], '#5E8A6E');
  gr = g.createLinearGradient(0, y + h * 0.66, 0, y + h); gr.addColorStop(0, '#6D9DBA'); gr.addColorStop(1, '#3F6F8E'); g.fillStyle = gr; g.fillRect(x, y + h * 0.66, w, h * 0.34);
  for (let i = 0; i < 9; i++) fillRR(g, x + w * (0.12 + 0.09 * i), y + h * (0.74 + 0.025 * (i % 3)), w * 0.07, h * 0.006, 2, 'rgba(255,255,255,0.35)');
  fillRR(g, x + w * 0.55, y + h * 0.68, w * 0.09, h * 0.022, 6, '#F6EFE6'); fillRR(g, x + w * 0.585, y + h * 0.62, w * 0.006, h * 0.06, 2, '#F6EFE6');
  g.fillStyle = 'rgba(246,239,230,0.95)'; g.beginPath(); g.moveTo(x + w * 0.591, y + h * 0.62); g.lineTo(x + w * 0.635, y + h * 0.672); g.lineTo(x + w * 0.591, y + h * 0.672); g.fill();
  grain(g, 0.05); g.restore();
}
function space(g, x, y, w, h, mt) {
  g.save(); rr(g, x, y, w, h, 0); g.clip();
  g.fillStyle = '#05070F'; g.fillRect(x, y, w, h);
  blob(g, x + w * 0.25, y + h * 0.3, w * 0.45, '#3B2A6E', 0.45); blob(g, x + w * 0.8, y + h * 0.75, w * 0.4, '#16467A', 0.4);
  for (let i = 0; i < 160; i++) { const sx = x + ((hash(i) * w + mt * 6 * (0.3 + hash(i + 9))) % w), sy = y + hash(i + 3) * h; circ(g, sx, sy, 0.6 + hash(i + 5) * 1.6, '#ffffff', 0.35 + 0.5 * hash(i + 7)); }
  const px = x + w * 0.58, py = y + h * 0.62, pr = h * 0.42;
  blob(g, px, py, pr * 1.35, '#F2A65A', 0.22);
  let gr = g.createRadialGradient(px - pr * 0.35, py - pr * 0.4, pr * 0.1, px, py, pr); gr.addColorStop(0, '#F6C48A'); gr.addColorStop(0.6, '#B8643E'); gr.addColorStop(1, '#3A1E1A');
  g.fillStyle = gr; g.beginPath(); g.arc(px, py, pr, 0, Math.PI * 2); g.fill();
  g.save(); g.strokeStyle = 'rgba(255,225,190,0.45)'; g.lineWidth = h * 0.008; g.beginPath(); g.ellipse(px, py, pr * 1.6, pr * 0.32, -0.18, 0, Math.PI * 2); g.stroke(); g.restore();
  const a = mt * 0.12, shx = px + Math.cos(a) * pr * 1.6, shy = py + Math.sin(a) * pr * 0.32 - Math.cos(a) * 0 - pr * 0.05;
  circ(g, shx, shy - pr * 0.28 * Math.sin(-0.18), h * 0.008, '#ffffff', 0.95); blob(g, shx, shy, h * 0.05, '#BFE3FF', 0.5);
  g.restore();
}
function showArt(g, x, y, w, h, col, kind, t) {
  g.save(); rr(g, x, y, w, h, 30); g.clip();
  const gr = g.createLinearGradient(x, y, x + w, y + h); gr.addColorStop(0, col[0]); gr.addColorStop(1, col[1]); g.fillStyle = gr; g.fillRect(x, y, w, h);
  blob(g, x + w * 0.85, y + h * 0.1, w * 0.5, '#ffffff', 0.25);
  const cx = x + w * 0.5, cy = y + h * 0.52 + Math.sin(t * 2.2 + kind) * 6, r = h * 0.28;
  circ(g, cx, cy, r, 'rgba(255,255,255,0.92)');
  circ(g, cx - r * 0.34, cy - r * 0.12, r * 0.1, '#2B2340'); circ(g, cx + r * 0.34, cy - r * 0.12, r * 0.1, '#2B2340');
  g.strokeStyle = '#2B2340'; g.lineWidth = r * 0.09; g.lineCap = 'round'; g.beginPath(); g.arc(cx, cy + r * 0.05, r * 0.4, 0.35, Math.PI - 0.35); g.stroke();
  if (kind === 1) { circ(g, cx - r * 0.75, cy - r * 0.75, r * 0.32, 'rgba(255,255,255,0.92)'); circ(g, cx + r * 0.75, cy - r * 0.75, r * 0.32, 'rgba(255,255,255,0.92)'); }
  if (kind === 2) { g.fillStyle = 'rgba(255,255,255,0.5)'; for (let i = 0; i < 4; i++) { g.beginPath(); g.arc(x + w * (0.15 + i * 0.24), y + h * 0.92, w * 0.09, Math.PI, 0); g.fill(); } }
  if (kind === 3) { icon(g, 'star', x + w * 0.18, y + h * 0.22, h * 0.18, 'rgba(255,255,255,0.9)', 2.4); icon(g, 'sparkles', x + w * 0.82, y + h * 0.72, h * 0.16, 'rgba(255,255,255,0.9)', 2.2); }
  g.restore();
}

// ============================================================== screens
export const SCREENS = {
  // ---------------- bedroom
  bedAmbient(g, lt) {
    g.fillStyle = '#040508'; g.fillRect(0, 0, 1920, 1080);
    digitsW(g, '6:29', 960, 600, 240, 200, 150, { al: 'center', a: 0.16 });
    icon(g, 'alarm-clock', 868, 705, 34, 'rgba(244,246,249,0.25)', 1.8);
    T(g, '6:30 AM', 898, 717, { s: 32, w: 500, a: 0.25 });
  },
  bedAlarm(g, lt) {
    const rise = eout(prog(lt, 0, 3.4));
    const gr = g.createLinearGradient(0, 0, 0, 1080);
    gr.addColorStop(0, '#120f2c'); gr.addColorStop(0.55, `rgba(${Math.round(lerp(40, 140, rise))},${Math.round(lerp(28, 62, rise))},${Math.round(lerp(70, 110, rise))},1)`); gr.addColorStop(1, `rgba(${Math.round(lerp(60, 255, rise))},${Math.round(lerp(40, 148, rise))},${Math.round(lerp(60, 92, rise))},1)`);
    g.fillStyle = gr; g.fillRect(0, 0, 1920, 1080);
    blob(g, 960, 1320 - rise * 380, 900, '#FFD7A0', 0.75 * rise); blob(g, 960, 1250 - rise * 380, 420, '#FFF0D6', 0.65 * rise);
    const pin = ap(lt, 0.05, 0.9);
    T(g, 'Wednesday, October 21', 960, 300 + pin.y, { s: 36, w: 500, al: 'center', a: pin.a * 0.85, ls: 0.5 });
    g.save(); const sc = 1 + 0.05 * (1 - eout(prog(lt, 0, 1.0))); g.translate(960, 560); g.scale(sc, sc);
    digitsW(g, '6:30', 0, 70, 310, 230, 190, { al: 'center', a: pin.a }); g.restore();
    const gm = ap(lt, 0.7, 0.9);
    T(g, 'Good morning, Matt', 960, 735 + gm.y, { s: 50, w: 560, al: 'center', a: gm.a });
    const pp = ap(lt, 1.1, 0.8);
    const pct = Math.round(lerp(0, 60, eio(prog(lt, 1.2, 3.6))));
    glass(g, 610, 800 + pp.y, 700, 92, 46, { a: pp.a, fill: 'rgba(40,22,48,0.32)' });
    icon(g, 'sunrise', 665, 846 + pp.y, 40, '#FFE2B0', 1.9, pp.a);
    T(g, 'Sunrise lights', 705, 858 + pp.y, { s: 32, w: 600, a: pp.a });
    fillRR(g, 960, 840 + pp.y, 240, 12, 6, 'rgba(255,255,255,0.2)', pp.a); fillRR(g, 960, 840 + pp.y, 240 * pct / 60 * 0.6 + 0.01, 12, 6, '#FFE2B0', pp.a);
    T(g, pct + '%', 1270, 858 + pp.y, { s: 30, w: 600, al: 'right', a: pp.a });
    const bt = ap(lt, 1.5, 0.8);
    const press = trap(lt, 3.45, 3.55, 3.62, 3.85);
    glass(g, 700, 935 + bt.y, 240, 80, 40, { a: bt.a, shadow: false, fill: 'rgba(40,22,48,0.34)' });
    T(g, 'Snooze', 820, 986 + bt.y, { s: 32, w: 600, al: 'center', a: bt.a });
    const sx = 980 + 4 * press, sw = 240 - 8 * press;
    fillRR(g, sx, 935 + bt.y + 4 * press, sw, 80 - 8 * press, 40, '#FFFFFF', bt.a);
    T(g, 'Stop', 1100, 986 + bt.y, { s: 32, w: 650, al: 'center', a: bt.a, c: '#1A1420' });
    focusRing(g, 980, 935 + bt.y, 240, 80, 40, clamp(prog(lt, 2.9, 3.2)) * bt.a, 9);
  },
  bedBrief(g, lt) {
    aurora(g, '#121829', [[1650, 120, 950, '#FF9F6B', 0.36], [260, 960, 900, '#4F8FE8', 0.28], [980, 1100, 700, '#FF7EA8', 0.18]], lt);
    const h = ap(lt, 0.0);
    T(g, 'Good morning, Matt.', 140, 250 + h.y, { s: 78, w: 650, a: h.a, ls: -1 });
    T(g, "Here's your Wednesday.", 140, 318 + h.y, { s: 40, w: 450, a: h.a * 0.75 });
    const wv = ap(lt, 0.25);
    g.save(); g.shadowColor = 'rgba(255,200,110,0.8)'; g.shadowBlur = 40; icon(g, 'sun', 220, 512 + wv.y, 130, '#FFD27A', 1.7, wv.a); g.restore();
    T(g, '64°', 320, 590 + wv.y, { s: 210, w: 250, a: wv.a, ls: -6 });
    T(g, 'Clear  ·  High 71°  Low 52°', 148, 680 + wv.y, { s: 36, w: 500, a: wv.a * 0.82 });
    const hrs = [['7 AM', '58°', 'sun'], ['9 AM', '62°', 'sun'], ['11 AM', '67°', 'cloud-sun'], ['1 PM', '70°', 'sun'], ['3 PM', '71°', 'sun']];
    hrs.forEach(([lab, deg, ic], i) => { const q = ap(lt, 0.45 + 0.07 * i); const x = 150 + i * 150;
      T(g, lab, x + 50, 785 + q.y, { s: 26, w: 500, al: 'center', a: q.a * 0.7 }); icon(g, ic, x + 50, 840 + q.y, 44, '#FFE0A6', 1.8, q.a); T(g, deg, x + 50, 915 + q.y, { s: 32, w: 600, al: 'center', a: q.a }); });
    // right column
    const c1 = ap(lt, 0.35);
    glass(g, 1040, 140 + c1.y, 740, 350, 36, { a: c1.a });
    icon(g, 'calendar', 1100, 205 + c1.y, 38, C.acc, 1.9, c1.a); T(g, 'Today', 1140, 217 + c1.y, { s: 34, w: 650, a: c1.a }); T(g, '3 events', 1730, 217 + c1.y, { s: 28, w: 500, al: 'right', a: c1.a * 0.6 });
    [['9:30', 'Design review'], ['12:30', 'Lunch with Sam'], ['6:00', "Lily's swim class"]].forEach(([tm, ti], i) => {
      const y = 300 + i * 66 + c1.y; T(g, tm, 1100, y, { s: 32, w: 650, c: C.acc, a: c1.a }); T(g, ti, 1230, y, { s: 34, w: 500, a: c1.a });
      if (i < 2) fillRR(g, 1100, y + 24, 620, 1.5, 1, 'rgba(255,255,255,0.08)', c1.a); });
    const c2 = ap(lt, 0.5);
    glass(g, 1040, 515 + c2.y, 740, 175, 36, { a: c2.a });
    icon(g, 'car', 1100, 578 + c2.y, 38, C.warm, 1.9, c2.a); T(g, '18 min to work', 1140, 592 + c2.y, { s: 40, w: 650, a: c2.a });
    T(g, 'Light traffic  ·  Leave by 8:10', 1100, 650 + c2.y, { s: 30, w: 500, a: c2.a * 0.7 });
    const c3 = ap(lt, 0.65);
    glass(g, 1040, 715 + c3.y, 740, 225, 36, { a: c3.a });
    icon(g, 'house', 1100, 778 + c3.y, 38, C.green, 1.9, c3.a); T(g, 'Home', 1140, 790 + c3.y, { s: 34, w: 650, a: c3.a });
    icon(g, 'lock', 1105, 852 + c3.y, 30, 'rgba(244,246,249,0.85)', 2, c3.a); T(g, 'Front door locked', 1140, 863 + c3.y, { s: 32, w: 500, a: c3.a }); check(g, 1725, 852 + c3.y, 17, C.green, c3.a);
    icon(g, 'bus', 1105, 908 + c3.y, 30, 'rgba(244,246,249,0.85)', 2, c3.a); T(g, "Lily's bus  ·  7:42 AM", 1140, 919 + c3.y, { s: 32, w: 500, a: c3.a }); T(g, 'on time', 1735, 919 + c3.y, { s: 28, w: 600, al: 'right', c: C.green, a: c3.a });
  },
  // ---------------- kitchen
  kitBoard(g, lt) {
    aurora(g, '#12151C', [[1750, 40, 900, '#FFA978', 0.3], [150, 1050, 800, '#8FD1B0', 0.18], [1000, 600, 700, '#6A8BFF', 0.1]], lt);
    const h = ap(lt, 0);
    T(g, 'Good morning', 110, 175 + h.y, { s: 78, w: 650, a: h.a, ls: -1 });
    T(g, 'Wednesday, October 21', 110, 238 + h.y, { s: 36, w: 500, a: h.a * 0.7 });
    const mp = ap(lt, 0.15);
    glass(g, 1180, 92 + mp.y, 630, 128, 34, { a: mp.a });
    const ag = g.createLinearGradient(1210, 112, 1298, 200); ag.addColorStop(0, '#FFB27A'); ag.addColorStop(1, '#E2577E');
    fillRR(g, 1210, 112 + mp.y, 88, 88, 18, ag, mp.a);
    icon(g, 'music', 1254, 156 + mp.y, 40, '#fff', 2, mp.a);
    T(g, 'Morning Mix', 1325, 150 + mp.y, { s: 34, w: 650, a: mp.a }); T(g, 'Playing in the kitchen', 1325, 192 + mp.y, { s: 26, w: 500, a: mp.a * 0.65 });
    for (let i = 0; i < 4; i++) { const hh = 14 + 26 * Math.abs(Math.sin(lt * 6 + i * 1.3)); fillRR(g, 1735 + i * 14, 156 - hh / 2 + mp.y, 8, hh, 4, C.acc, mp.a); }
    const people = [
      ['Lily', C.lily, 'L', 'bus', 'Bus in 12 min', 'Maple & 3rd  ·  7:42', ['Swim bag', 'Lunch packed']],
      ['Sam', C.sam, 'S', 'dumbbell', 'Gym at 6 PM', 'Matt is making dinner', ['Car charged']],
      ['Matt', C.matt, 'M', 'briefcase', 'Review at 9:30', 'Leave by 8:10  ·  18 min', ['Laptop charged']],
    ];
    people.forEach(([nm, col, ch, ic, title, sub, chips], i) => {
      const q = ap(lt, 0.3 + 0.12 * i); const x = 110 + i * 580, y = 300 + q.y;
      glass(g, x, y, 555, 640, 40, { a: q.a });
      avatar(g, x + 82, y + 88, 44, col, ch, q.a); T(g, nm, x + 145, y + 104, { s: 44, w: 650, a: q.a });
      circ(g, x + 92, y + 238, 54, col + '33', q.a);
      if (i === 0) arcRing(g, x + 92, y + 238, 62, 6, 1 - lt / 120 - 0.2, col, 'rgba(255,255,255,0.1)', q.a);
      icon(g, ic, x + 92, y + 238, 54, col, 1.9, q.a);
      T(g, title, x + 46, y + 372, { s: 50, w: 650, a: q.a, ls: -0.5 });
      T(g, sub, x + 46, y + 424, { s: 30, w: 500, a: q.a * 0.68 });
      chips.forEach((c, k) => { const cq = ap(lt, 0.9 + 0.15 * k + 0.1 * i, 0.5);
        const w = TW(g, c, 600, 28) + 92; fillRR(g, x + 46, y + 478 + k * 70, w, 56, 28, 'rgba(255,255,255,0.1)', q.a * cq.a);
        check(g, x + 76, y + 506 + k * 70, 15, C.green, q.a * cq.a); T(g, c, x + 102, y + 516 + k * 70, { s: 28, w: 600, a: q.a * cq.a }); });
    });
  },
  kitRecipe(g, lt, follow = 0) {
    aurora(g, '#17120F', [[1500, 200, 900, '#FFAE5E', 0.3], [260, 980, 800, '#D9774F', 0.2]], lt);
    const h = ap(lt, 0);
    pill(g, 110, 108 + h.y, 62, 'Step 3 of 6', { s: 28, a: h.a, icon: 'chef-hat', ic: C.warm });
    T(g, 'Lemon garlic pasta', 110, 262 + h.y, { s: 68, w: 650, a: h.a, ls: -1 });
    T(g, 'Toss the pasta with lemon butter', 110, 400 + h.y, { s: 56, w: 500, a: h.a });
    T(g, 'and a handful of parmesan.', 110, 470 + h.y, { s: 56, w: 500, a: h.a });
    for (let i = 0; i < 6; i++) fillRR(g, 110 + i * 120, 560 + h.y, 108, 10, 5, i < 3 ? C.warm : 'rgba(255,255,255,0.16)', h.a);
    const tr = ap(lt, 0.2);
    const secs = Math.max(0, 252 - Math.floor(lt));
    arcRing(g, 1480, 400, 210, 22, secs / 300, C.warm, 'rgba(255,255,255,0.12)', tr.a);
    digitsW(g, `${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, '0')}`, 1480, 430, 130, 280, 80, { al: 'center', a: tr.a });
    T(g, 'Simmer', 1480, 495, { s: 34, w: 500, al: 'center', a: tr.a * 0.7 });
    const mb = ap(lt, 0.35);
    glass(g, 110, 820 + mb.y, 1700, 170, 40, { a: mb.a });
    const ag = g.createLinearGradient(140, 850, 250, 960); ag.addColorStop(0, '#F7B955'); ag.addColorStop(1, '#C2410C');
    fillRR(g, 145, 852 + mb.y, 106, 106, 22, ag, mb.a); icon(g, 'music', 198, 905 + mb.y, 46, '#fff', 2, mb.a);
    T(g, 'Dinner Jazz', 285, 895 + mb.y, { s: 42, w: 650, a: mb.a }); T(g, 'Haven Radio', 285, 945 + mb.y, { s: 30, w: 500, a: mb.a * 0.65 });
    // rooms: kitchen -> living room as music follows
    const k1 = 1 - follow * 0.75, k2 = follow;
    fillRR(g, 1170, 868 + mb.y, 290, 74, 37, `rgba(125,211,252,${0.22 * k1})`, mb.a); icon(g, 'speaker', 1212, 905 + mb.y, 32, C.acc, 2, mb.a * (0.4 + 0.6 * k1)); T(g, 'Kitchen', 1240, 916 + mb.y, { s: 30, w: 600, a: mb.a * (0.45 + 0.55 * k1) });
    fillRR(g, 1480, 868 + mb.y, 300, 74, 37, `rgba(125,211,252,${0.22 * k2})`, mb.a); icon(g, 'speaker', 1522, 905 + mb.y, 32, C.acc, 2, mb.a * (0.4 + 0.6 * k2)); T(g, 'Living Room', 1550, 916 + mb.y, { s: 30, w: 600, a: mb.a * (0.45 + 0.55 * k2) });
    if (follow > 0) T(g, 'Following you', 1480, 845 + mb.y, { s: 24, w: 650, a: mb.a * follow, c: C.acc, ls: 1 });
  },
  // ---------------- living room
  art(g, lt, which = 0, mix = 0, label = 1) {
    g.fillStyle = '#ECE6DB'; g.fillRect(0, 0, 1920, 1080);
    const gr = g.createRadialGradient(960, 420, 100, 960, 540, 1300); gr.addColorStop(0, 'rgba(255,255,255,0.35)'); gr.addColorStop(1, 'rgba(0,0,0,0.06)'); g.fillStyle = gr; g.fillRect(0, 0, 1920, 1080);
    const x = 300, y = 170, w = 1320, h = 740;
    g.save(); g.shadowColor = 'rgba(40,30,20,0.35)'; g.shadowBlur = 30; g.shadowOffsetY = 10; g.fillStyle = '#fff'; g.fillRect(x, y, w, h); g.restore();
    if (which === 0 || mix < 1) paintDusk(g, x, y, w, h);
    if (mix > 0) { g.save(); g.globalAlpha *= eio(mix); paintLake(g, x, y, w, h); g.restore(); }
    const lab = mix > 0.5 ? ['Lake trip', 'Summer 2025  ·  from Sam\'s phone'] : ['Dusk Domes', 'Haven Collection'];
    T(g, lab[0], 1620, 990, { s: 30, w: 650, al: 'right', c: '#3A3029', a: label * 0.8 });
    T(g, lab[1], 1620, 1030, { s: 24, w: 500, al: 'right', c: '#3A3029', a: label * 0.55 });
    grain(g, 0.045);
  },
  kid(g, lt) {
    aurora(g, '#24173F', [[1600, 160, 900, '#FF8A7A', 0.42], [300, 900, 900, '#B9A2FF', 0.45], [1000, 1100, 700, '#F7B955', 0.25]], lt * 1.5);
    const wl = 1 - eio(prog(lt, 2.4, 3.0));
    if (wl > 0.01) {
      const q = ap(lt, 0.1, 0.9);
      g.save(); g.globalAlpha *= wl;
      T(g, 'Hi, Lily!', 960 - 40, 520 + q.y, { s: 170, w: 800, al: 'center', a: q.a, ls: -3 });
      g.save(); g.translate(1390, 455 + q.y); g.rotate(Math.sin(lt * 8) * 0.25 * (1 - prog(lt, 1.6, 2.2))); icon(g, 'hand', 0, 0, 120, '#FFD27A', 1.8, q.a); g.restore();
      const q2 = ap(lt, 0.6);
      T(g, 'Welcome home.', 960, 620 + q2.y, { s: 54, w: 500, al: 'center', a: q2.a * 0.9 });
      const q3 = ap(lt, 1.0);
      pill(g, 960, 690 + q3.y, 76, 'Matt and Sam know you’re home', { s: 30, al: 'center', glass: true, a: q3.a, icon: 'circle-check', ic: C.green });
      g.restore();
    }
    const hm = eio(prog(lt, 2.5, 3.2)); if (hm <= 0.01) { return; }
    g.save(); g.globalAlpha *= hm;
    const lt2 = lt - 2.6;
    const h = ap(lt2, 0);
    T(g, 'Hi, Lily!', 110, 165 + h.y, { s: 80, w: 800, a: h.a, ls: -1.5 });
    pill(g, 1810, 104 + h.y, 70, 'Screen time left  1h 20m', { s: 28, al: 'right', glass: true, icon: 'hourglass', ic: C.warm, a: h.a });
    const c = ap(lt2, 0.15);
    glass(g, 110, 250 + c.y, 820, 560, 44, { a: c.a, fill: 'rgba(255,255,255,0.12)' });
    icon(g, 'book-open', 190, 340 + c.y, 64, '#FFE0A6', 1.9, c.a);
    T(g, 'Homework first', 160, 470 + c.y, { s: 64, w: 750, a: c.a, ls: -1 });
    T(g, 'Games unlock after 30 minutes.', 160, 530 + c.y, { s: 34, w: 500, a: c.a * 0.8 });
    arcRing(g, 770, 380 + c.y, 92, 14, 0.02 + 0.98 * eio(prog(lt2, 0.3, 1.4)) * 0, C.warm, 'rgba(255,255,255,0.18)', c.a);
    digitsW(g, '30:00', 770, 398 + c.y, 52, 650, 30, { al: 'center', a: c.a });
    const press = trap(lt2, 3.4, 3.5, 3.6, 3.8);
    fillRR(g, 160 + 4 * press, 640 + c.y + 4 * press, 330 - 8 * press, 92 - 8 * press, 46, '#FFFFFF', c.a);
    T(g, 'Start homework', 325, 698 + c.y, { s: 32, w: 650, al: 'center', c: '#2A1F45', a: c.a });
    focusRing(g, 160, 640 + c.y, 330, 92, 46, clamp(prog(lt2, 1.6, 1.9)) * c.a, 9);
    const shows = [[['#FFB36B', '#FF7E6B'], 'Moon Pals', 0], [['#8EE3C0', '#4FB3A0'], 'Tiny Trains', 1], [['#9CC7FF', '#6A8BFF'], 'Ocean Hop', 2], [['#F9A8D4', '#C084FC'], 'Doodle Zoo', 3]];
    shows.forEach(([col, nm, k], i) => { const q = ap(lt2, 0.25 + 0.08 * i); const x = 980 + (i % 2) * 425, y = 250 + Math.floor(i / 2) * 285 + q.y;
      g.save(); g.globalAlpha *= q.a; showArt(g, x, y, 400, 225, col, k, lt); g.restore(); T(g, nm, x + 6, y + 266, { s: 32, w: 650, a: q.a }); });
    const apps = [['square-play', 'Shows'], ['music', 'Songs'], ['palette', 'Drawing'], ['book-open', 'Reading'], ['gamepad-2', 'Games']];
    apps.forEach(([ic, nm], i) => { const q = ap(lt2, 0.5 + 0.06 * i); const x = 110 + i * 210, y = 860 + q.y; const locked = ic === 'gamepad-2';
      glass(g, x, y, 190, 150, 34, { a: q.a * (locked ? 0.6 : 1), shadow: false });
      icon(g, ic, x + 95, y + 62, 52, locked ? 'rgba(244,246,249,0.5)' : '#fff', 1.9, q.a); T(g, nm, x + 95, y + 128, { s: 26, w: 600, al: 'center', a: q.a * (locked ? 0.5 : 1) });
      if (locked) { circ(g, x + 165, y + 25, 24, C.coral, q.a); icon(g, 'lock', x + 165, y + 25, 26, '#2A1F45', 2.4, q.a); } });
    g.restore();
  },
  nowPlaying(g, lt) {
    aurora(g, '#1A120C', [[500, 500, 1000, '#F7B955', 0.38], [1500, 300, 900, '#C2410C', 0.3], [1200, 1000, 700, '#F9A8D4', 0.18]], lt);
    const q = ap(lt, 0.0, 1.0);
    g.save(); g.shadowColor = 'rgba(0,0,0,0.45)'; g.shadowBlur = 60; g.shadowOffsetY = 20;
    const ag = g.createLinearGradient(170, 220, 770, 820); ag.addColorStop(0, '#F7B955'); ag.addColorStop(1, '#C2410C'); fillRR(g, 170, 220 + q.y, 600, 600, 40, ag, q.a); g.restore();
    icon(g, 'music', 470, 520 + q.y, 220, 'rgba(255,255,255,0.9)', 1.4, q.a);
    const t2 = ap(lt, 0.2);
    pill(g, 880, 250 + t2.y, 64, 'Picked up from the kitchen', { s: 28, glass: true, icon: 'sparkles', ic: C.acc, a: t2.a });
    T(g, 'Dinner Jazz', 880, 450 + t2.y, { s: 110, w: 700, a: t2.a, ls: -2 });
    T(g, 'Haven Radio  ·  Living Room', 880, 525 + t2.y, { s: 42, w: 500, a: t2.a * 0.72 });
    const pr = 0.34 + lt * 0.004;
    fillRR(g, 880, 640, 880, 10, 5, 'rgba(255,255,255,0.18)', t2.a); fillRR(g, 880, 640, 880 * pr, 10, 5, '#fff', t2.a);
    T(g, '1:42', 880, 700, { s: 28, w: 500, a: t2.a * 0.6 }); T(g, '-3:05', 1760, 700, { s: 28, w: 500, al: 'right', a: t2.a * 0.6 });
    ['skip-forward', 'pause'].forEach((ic, i) => { circ(g, 980 + i * 140, 790, 52, i ? '#fff' : 'rgba(255,255,255,0.14)', t2.a); icon(g, ic, 980 + i * 140, 790, 46, i ? '#1A120C' : '#fff', 2, t2.a); });
  },
  livHome(g, lt, voice = null) {
    aurora(g, '#0C0F18', [[1500, 200, 900, '#4F46E5', 0.22], [300, 900, 800, '#0EA5E9', 0.14]], lt);
    // hero
    g.save(); rr(g, 0, 0, 1920, 640, 0); g.clip(); space(g, 0, 0, 1920, 640, lt + 30);
    const sg = g.createLinearGradient(0, 300, 0, 640); sg.addColorStop(0, 'rgba(12,15,24,0)'); sg.addColorStop(1, 'rgba(12,15,24,1)'); g.fillStyle = sg; g.fillRect(0, 300, 1920, 340);
    const lg = g.createLinearGradient(0, 0, 900, 0); lg.addColorStop(0, 'rgba(12,15,24,0.75)'); lg.addColorStop(1, 'rgba(12,15,24,0)'); g.fillStyle = lg; g.fillRect(0, 0, 900, 640); g.restore();
    T(g, '8:30 PM', 110, 110, { s: 40, w: 600 });
    T(g, 'Continue watching', 110, 300, { s: 32, w: 600, a: 0.7, ls: 0.5 });
    T(g, 'The Long Orbit', 110, 400, { s: 96, w: 750, ls: -2 });
    fillRR(g, 110, 450, 260, 80, 40, '#fff'); icon(g, 'play', 160, 490, 34, '#0C0F18', 2.4); T(g, 'Resume', 200, 502, { s: 32, w: 650, c: '#0C0F18' });
    T(g, '1h 12m left', 400, 502, { s: 30, w: 500, a: 0.7 });
    const cards = [['#2B4C7E', '#1B2A4A'], ['#5B3F8C', '#2A1F45'], ['#2F6B57', '#1A3A30'], ['#8A5A2B', '#3A2614'], ['#6B2F3C', '#2E141A']];
    cards.forEach((c, i) => { const x = 110 + i * 350; const gg = g.createLinearGradient(x, 700, x + 330, 885); gg.addColorStop(0, c[0]); gg.addColorStop(1, c[1]); fillRR(g, x, 700, 330, 186, 24, gg); blob(g, x + 280, 730, 160, '#ffffff', 0.12); });
    if (voice) {
      const { a = 1, text = '', listening = 1 } = voice;
      g.save(); g.globalAlpha *= a * 0.55; g.fillStyle = '#05070D'; g.fillRect(0, 0, 1920, 1080); g.restore();
      glass(g, 460, 860, 1000, 140, 70, { a, fill: 'rgba(28,32,44,0.85)' });
      const cols = [C.acc, C.violet, C.coral, C.warm, C.green];
      for (let i = 0; i < 5; i++) { const hh = (22 + 46 * Math.abs(Math.sin(lt * 9 + i * 1.4))) * (0.3 + 0.7 * listening); fillRR(g, 540 + i * 26 - 6, 930 - hh / 2, 12, hh, 6, cols[i], a); }
      T(g, text || 'Listening…', 700, 948, { s: 48, w: 550, a: a * (text ? 1 : 0.6) });
    }
  },
  sceneCard(g, lt) {
    SCREENS.livHome(g, lt + 5, null);
    g.save(); g.globalAlpha *= 0.6; g.fillStyle = '#05070D'; g.fillRect(0, 0, 1920, 1080); g.restore();
    const q = ap(lt, 0, 0.7);
    glass(g, 360, 120 + q.y, 1200, 840, 56, { a: q.a, fill: 'rgba(30,26,44,0.88)' });
    circ(g, 480, 250 + q.y, 70, 'rgba(185,162,255,0.25)', q.a); icon(g, 'popcorn', 480, 250 + q.y, 72, C.violet, 1.9, q.a);
    T(g, 'Movie night', 590, 280 + q.y, { s: 96, w: 750, a: q.a, ls: -2 });
    const rows = [['lamp-floor', 'Lights down to 15%'], ['blinds', 'Blinds closed'], ['speaker', 'Soundbar in cinema mode'], ['bell', 'Doorbell will pause the movie']];
    rows.forEach(([ic, tx], i) => { const y = 410 + i * 130 + q.y; const done = eback(prog(lt, 0.8 + i * 0.45, 1.15 + i * 0.45));
      fillRR(g, 430, y, 1060, 104, 30, 'rgba(255,255,255,0.07)', q.a);
      icon(g, ic, 500, y + 52, 50, '#fff', 1.9, q.a); T(g, tx, 560, y + 68, { s: 46, w: 550, a: q.a * (0.55 + 0.45 * clamp(done)) });
      if (done > 0.01) check(g, 1420, y + 52, 26 * clamp(done) + 3 * Math.max(0, done - 1), C.green, q.a);
      else { g.save(); g.globalAlpha *= q.a; g.strokeStyle = 'rgba(255,255,255,0.25)'; g.lineWidth = 3; g.beginPath(); g.arc(1420, y + 52, 24, 0, Math.PI * 2); g.stroke(); g.restore(); } });
  },
  movie(g, mt, o = {}) {
    space(g, 0, 0, 1920, 1080, mt);
    g.fillStyle = '#000'; g.fillRect(0, 0, 1920, 130); g.fillRect(0, 950, 1920, 130);
    const p = o.paused ?? 0;
    if (p > 0.01) {
      g.save(); g.globalAlpha *= 0.5 * p; g.fillStyle = '#04050A'; g.fillRect(0, 0, 1920, 1080); g.restore();
      circ(g, 960, 540, 90, 'rgba(255,255,255,0.18)', p); icon(g, 'pause', 960, 540, 80, '#fff', 2.2, p);
      T(g, 'Paused for the doorbell', 960, 690, { s: 40, w: 600, al: 'center', a: p });
      fillRR(g, 110, 980, 1700, 10, 5, 'rgba(255,255,255,0.25)', p); fillRR(g, 110, 980, 1700 * 0.57, 10, 5, '#fff', p);
      T(g, 'The Long Orbit', 110, 950, { s: 36, w: 650, a: p }); T(g, '1:12:45 / 2:08:00', 1810, 950, { s: 30, w: 500, al: 'right', a: p * 0.7 });
    }
    const pc = o.pip ?? 0;
    if (pc > 0.01) {
      const x = 1810 - 720 + (1 - eout(pc)) * 200, y = 90;
      glass(g, x, y, 720, 560, 40, { a: pc, fill: 'rgba(22,26,36,0.9)' });
      circ(g, x + 62, y + 62, 28, C.warm, pc); icon(g, 'bell-ring', x + 62, y + 62, 32, '#1A1420', 2.2, pc);
      T(g, 'Front door', x + 108, y + 75, { s: 36, w: 650, a: pc }); T(g, 'now', x + 680, y + 75, { s: 28, w: 500, al: 'right', a: pc * 0.6 });
      o.pipRect = [x + 30, y + 120, 660, 330];
      if (o.feed) { g.save(); g.globalAlpha *= pc; rr(g, x + 30, y + 120, 660, 330, 22); g.clip(); g.drawImage(o.feed, x + 30, y + 120, 660, 330); g.restore(); }
      fillRR(g, x + 48, y + 138, 92, 40, 20, 'rgba(0,0,0,0.55)', pc); circ(g, x + 70, y + 158, 7, C.coral, pc * (0.6 + 0.4 * Math.abs(Math.sin(mt * 5)))); T(g, 'Live', x + 84, y + 167, { s: 22, w: 650, a: pc });
      T(g, o.feedText ?? 'Delivery at the door', x + 34, y + 505, { s: 34, w: 600, a: pc });
      const b = o.btn ?? 'Talk';
      fillRR(g, x + 470, y + 470, 220, 62, 31, '#fff', pc); T(g, b, x + 580, y + 512, { s: 28, w: 650, al: 'center', c: '#1A1420', a: pc });
    }
    const rb = o.resume ?? 0;
    if (rb > 0.01) pill(g, 960, 860, 84, 'Welcome back. Resuming.', { s: 34, al: 'center', glass: true, icon: 'play', ic: C.acc, a: rb });
  },
  // ---------------- Lily's room
  lilyWind(g, lt) {
    aurora(g, '#0E1230', [[1500, 200, 800, '#6D5BD0', 0.4], [300, 900, 900, '#2C3E91', 0.4], [960, 1100, 600, '#F7B955', 0.12]], lt);
    for (let i = 0; i < 70; i++) circ(g, hash(i) * 1920, hash(i + 40) * 720, 1.2 + 2 * hash(i + 80), '#ffffff', 0.25 + 0.6 * Math.abs(Math.sin(lt * 1.3 + i)));
    const gn = eio(prog(lt, 4.0, 4.8));
    const q = ap(lt, 0.0, 0.9);
    g.save(); g.shadowColor = 'rgba(255,214,140,0.9)'; g.shadowBlur = 60;
    circ(g, 960, 330 + q.y, 110, '#FFE2A8', q.a); g.restore(); circ(g, 1010, 295 + q.y, 100, '#121636', q.a);
    g.save(); g.globalAlpha *= 1 - gn;
    T(g, 'Bedtime in 5 minutes', 960, 620 + q.y, { s: 96, w: 700, al: 'center', a: q.a, ls: -2 });
    const q2 = ap(lt, 0.4);
    T(g, 'Time to wrap up, Lily.', 960, 700 + q2.y, { s: 46, w: 500, al: 'center', a: q2.a * 0.85 });
    fillRR(g, 610, 780, 700, 14, 7, 'rgba(255,255,255,0.16)', q2.a); fillRR(g, 610, 780, 700 * (1 - prog(lt, 0.6, 12)), 14, 7, '#FFE2A8', q2.a);
    g.restore();
    if (gn > 0.01) { const q3 = ap(lt, 4.1, 1.0);
      T(g, 'Good night, Lily', 960, 640 + q3.y, { s: 110, w: 700, al: 'center', a: q3.a * gn, ls: -2 });
      icon(g, 'sparkles', 1450, 520 + q3.y, 80, '#FFE2A8', 1.8, q3.a * gn); icon(g, 'star', 470, 700 + q3.y, 56, '#FFE2A8', 1.8, q3.a * gn); }
  },
};
