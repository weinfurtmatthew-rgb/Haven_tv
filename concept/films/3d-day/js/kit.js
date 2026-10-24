// Shared math, easing and keyframe helpers. Everything is a pure function of time.
export const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
export const prog = (t, a, b) => (b > a ? clamp((t - a) / (b - a)) : (t >= a ? 1 : 0));
export const lerp = (a, b, x) => a + (b - a) * x;
export const smooth = (x) => { x = clamp(x); return x * x * (3 - 2 * x); };
export const eio = (x) => { x = clamp(x); return x * x * x * (x * (6 * x - 15) + 10); };
export const eout = (x) => { x = clamp(x); return 1 - Math.pow(1 - x, 3); };
export const eout5 = (x) => { x = clamp(x); return 1 - Math.pow(1 - x, 5); };
export const ein = (x) => { x = clamp(x); return x * x * x; };
export const eback = (x, s = 1.4) => { x = clamp(x) - 1; return x * x * ((s + 1) * x + s) + 1; };
export const spring = (x, k = 7, d = 0.45) => { x = clamp(x); if (x >= 1) return 1; return 1 - Math.exp(-k * x * 1.2) * Math.cos(k * x * 2.2 * (1 - d * 0.5)) * (1 - x * 0.2); };
export const trap = (t, a, b, c, d) => (t <= a || t >= d ? 0 : t < b ? eio((t - a) / (b - a)) : t <= c ? 1 : 1 - eio((t - c) / (d - c)));
export const win = (t, a, d) => t >= a && t < d;

// colors as [r,g,b] 0..1 or hex
export const hex = (h) => [((h >> 16) & 255) / 255, ((h >> 8) & 255) / 255, (h & 255) / 255];
export const mixc = (a, b, x) => [lerp(a[0], b[0], x), lerp(a[1], b[1], x), lerp(a[2], b[2], x)];
export const css = (c, a = 1) => `rgba(${Math.round(c[0] * 255)},${Math.round(c[1] * 255)},${Math.round(c[2] * 255)},${a})`;

// piecewise keyframes: keys = [[t, value], ...]; value: number or array; eased with smoothstep per segment
export function keyed(keys, t, ease = smooth) {
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    if (t <= keys[i][0]) {
      const [t0, v0] = keys[i - 1], [t1, v1] = keys[i];
      const x = ease((t - t0) / (t1 - t0));
      if (Array.isArray(v0)) return v0.map((v, k) => lerp(v, v1[k], x));
      return lerp(v0, v1, x);
    }
  }
  return keys[keys.length - 1][1];
}
// linear piecewise
export const keyedLin = (keys, t) => keyed(keys, t, (x) => clamp(x));

// deterministic hash noise
export function hash(n) { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); }
export function noise1(x) { const i = Math.floor(x), f = x - i; const u = f * f * (3 - 2 * f); return lerp(hash(i), hash(i + 1), u) * 2 - 1; }
