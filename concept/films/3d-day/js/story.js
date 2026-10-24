// The day: clock, camera path, lights, props, screens, captions and notifications, all as functions of film time t.
import * as THREE from 'three';
import { clamp, prog, lerp, eio, eout, ein, smooth, trap, keyed, keyedLin, noise1, eback } from './kit.js';

export const DUR = 120;

// ---------------------------------------------------------------- clock (film seconds -> hour of day)
const CLOCK = [[0, 6.467], [10.8, 6.5], [19.5, 6.6], [22.5, 7.55], [32, 7.66], [35.5, 11.9], [44, 12.0], [46.5, 15.62], [56, 15.78], [58.5, 18.55], [68.5, 18.72], [70.5, 19.2], [80.5, 19.36], [83, 20.5], [99, 20.78], [100.5, 21.0], [106.5, 21.12], [110.5, 22.5], [120, 23.0]];
export const clockAt = (t) => keyed(CLOCK, t, (x) => smooth(x));
export function fmtClock(h) {
  const tot = Math.floor(h * 60 + 1e-6); let hh = Math.floor(tot / 60) % 24; const mm = tot % 60;
  const ap = hh < 12 ? 'AM' : 'PM'; const h12 = hh % 12 === 0 ? 12 : hh % 12;
  return `${h12}:${String(mm).padStart(2, '0')} ${ap}`;
}

// ---------------------------------------------------------------- camera
// [t, pos, target, fov, via]
const CAM = [
  [0.0, [-8.0, 9.4, 44.0], [0.8, 6.6, -1], 30],
  [7.0, [-3.6, 8.8, 34.0], [-1.4, 5.0, -1], 30],
  [10.0, [-3.3, 5.75, 7.2], [-3.3, 4.72, -3.5], 38],
  [14.2, [-3.22, 5.55, 5.5], [-3.3, 4.8, -3.6], 38],
  [17.2, [-3.3, 4.9, -1.75], [-3.3, 4.74, -3.98], 38],
  [19.6, [-3.3, 4.88, -1.6], [-3.3, 4.74, -3.98], 38],
  [22.0, [-2.6, 4.3, 13.5], [-3.1, 2.5, -1.5], 34, true],
  [24.4, [-4.4, 2.0, 4.6], [-4.1, 1.45, -3.0], 40],
  [26.6, [-4.25, 1.95, 2.9], [-4.0, 1.6, -3.5], 40],
  [29.0, [-3.95, 1.88, -2.25], [-3.95, 1.75, -3.98], 36],
  [32.4, [-3.95, 1.88, -2.1], [-3.95, 1.75, -3.98], 36],
  [35.4, [2.0, 8.0, 32.0], [1.5, 3.2, -1], 30],
  [43.4, [8.6, 6.6, 27.0], [4.0, 2.7, -0.5], 30],
  [45.4, [9.6, 4.6, 16.5], [3.9, 1.55, -0.9], 34],
  [47.6, [9.0, 4.3, 15.2], [3.6, 1.45, -1.0], 34],
  [50.0, [2.8, 2.2, 6.8], [1.0, 1.4, -3.5], 40],
  [52.8, [0.9, 1.62, -1.05], [0.9, 1.4, -3.98], 38],
  [55.8, [0.9, 1.6, -1.2], [0.9, 1.4, -3.98], 38],
  [58.4, [-1.9, 2.2, 5.6], [1.4, 1.2, -3.2], 40],
  [63.4, [-1.2, 1.95, 4.2], [1.2, 1.35, -3.5], 40],
  [66.6, [0.9, 1.58, -0.95], [0.9, 1.4, -3.98], 38],
  [68.4, [0.9, 1.58, -1.07], [0.9, 1.4, -3.98], 38],
  [71.0, [-4.6, 2.4, 5.4], [-4.3, 1.3, -2.2], 40],
  [74.2, [-4.2, 2.1, 3.0], [-4.0, 1.55, -3.0], 38],
  [77.6, [0.2, 2.0, 5.2], [0.9, 1.3, -3.2], 40],
  [80.6, [0.9, 1.75, 2.4], [0.9, 1.4, -3.5], 38],
  [82.8, [0.9, 1.58, -0.6], [0.9, 1.38, -3.98], 38],
  [84.4, [0.9, 1.58, -0.75], [0.9, 1.38, -3.98], 38],
  [86.4, [1.3, 1.95, 3.5], [0.9, 1.25, -3.4], 40],
  [91.2, [0.9, 1.72, 2.3], [0.9, 1.35, -3.6], 38],
  [93.0, [0.9, 1.56, -0.5], [0.9, 1.38, -3.98], 38],
  [96.4, [0.9, 1.56, -0.65], [0.9, 1.38, -3.98], 38],
  [98.8, [0.9, 1.78, 2.5], [0.9, 1.3, -3.4], 40],
  [101.0, [2.0, 4.95, 4.6], [2.0, 4.35, -3.2], 40],
  [103.2, [1.6, 4.68, -2.05], [1.6, 4.47, -3.98], 38],
  [105.4, [1.6, 4.68, -1.9], [1.6, 4.47, -3.98], 38],
  [107.6, [2.3, 5.2, 4.8], [2.4, 4.3, -2.8], 40],
  [111.2, [0.5, 7.2, 31.0], [0.6, 4.6, -1], 30],
  [120.0, [0.5, 9.0, 40.0], [0.6, 6.4, -1], 30],
];
const posCurve = new THREE.CatmullRomCurve3(CAM.map((k) => new THREE.Vector3(...k[1])), false, 'centripetal');
const tgtCurve = new THREE.CatmullRomCurve3(CAM.map((k) => new THREE.Vector3(...k[2])), false, 'centripetal');
const easeSeg = (x, a, b) => (a && b ? eio(x) : a ? 1 - Math.cos((x * Math.PI) / 2) : b ? Math.sin((x * Math.PI) / 2) : x);
export function cameraAt(t, cam) {
  let i = 0; while (i < CAM.length - 2 && t > CAM[i + 1][0]) i++;
  const [t0, , , f0, v0] = CAM[i], [t1, , , f1, v1] = CAM[i + 1];
  const x = clamp((t - t0) / (t1 - t0));
  const e = easeSeg(x, !v0, !v1);
  const u = (i + e) / (CAM.length - 1);
  const p = posCurve.getPoint(u), q = tgtCurve.getPoint(u);
  const hand = 0.012;
  p.x += noise1(t * 0.35 + 3) * hand; p.y += noise1(t * 0.31 + 11) * hand; q.x += noise1(t * 0.27 + 5) * hand * 0.6;
  cam.position.copy(p); cam.fov = lerp(f0, f1, e); cam.updateProjectionMatrix(); cam.lookAt(q);
}

// ---------------------------------------------------------------- lights, props
const lvl = (t, segs) => { let v = 0; for (const [a, b, c, d, m = 1] of segs) v = Math.max(v, trap(t, a, b, c, d) * m); return v; };
export const LAMPS = {
  bed: (t) => lvl(t, [[10.8, 13.6, 20.6, 22.4, 0.85], [98.8, 100.0, 108.6, 109.6, 0.45]]),
  kit: (t) => lvl(t, [[21.2, 22.2, 32.2, 33.4, 0.55], [68.6, 69.6, 80.0, 81.0, 1.0], [81.0, 81.5, 106.4, 107.0, 0.35]]),
  liv: (t) => lvl(t, [[56.5, 58.0, 69.5, 70.5, 0.35], [70.5, 71.2, 84.1, 85.7, 1.0], [85.7, 86.0, 106.8, 107.4, 0.12]]),
  livCeil: (t) => lvl(t, [[70.5, 71.2, 84.0, 85.2, 0.8]]),
  entry: (t) => lvl(t, [[45.1, 45.5, 48.6, 49.6, 0.9], [70.4, 71.0, 107.4, 108.0, 0.7]]),
  porch: (t) => lvl(t, [[-1, -0.5, 8.0, 9.6, 0.9], [66.2, 67.4, 108.2, 108.9, 1.0]]),
  landing: (t) => lvl(t, [[70.4, 71.0, 107.8, 108.4, 0.55]]),
  lily: (t) => lvl(t, [[98.8, 99.6, 102.6, 104.4, 0.85]]),
  star: (t) => lvl(t, [[-1, -0.5, 10.8, 12.4, 0.8], [103.6, 104.6, 130, 131, 1.0]]),
};
export function blindsAt(t) {
  return { bed: keyed([[0, 0], [11.4, 0], [13.9, 1], [98.5, 1], [100.2, 0]], t), liv: keyed([[0, 0.15], [20, 0.15], [24, 1], [84.4, 1], [86.4, 0]], t), lily: keyed([[0, 0], [21, 0], [23, 1], [99.2, 1], [100.8, 0]], t) };
}
export function doorAt(t) { // 0 closed .. 1 open
  return Math.max(trap(t, 33.0, 33.5, 33.9, 34.4), trap(t, 45.25, 45.75, 46.55, 47.05), trap(t, 94.6, 95.1, 95.6, 96.1));
}

// ---------------------------------------------------------------- screens
// returns { state, lt, bright, glow:[hex, k] }
export function screenStates(t) {
  const S = {};
  // bedroom
  if (t < 10.8) S.bed = { s: 'bedAmbient', lt: t, b: 1, glow: ['#334', 0.15] };
  else if (t < 14.45) S.bed = { s: 'bedAlarm', lt: t - 10.8, b: 1, glow: ['#ff9a5c', 1.0] };
  else if (t < 23.6) S.bed = { s: 'bedBrief', lt: t - 14.45, b: 1 - prog(t, 22.4, 23.6), glow: ['#ffb08a', 0.7], x: [['bedAlarm', t - 10.8], prog(t, 14.45, 15.05)] };
  else S.bed = { s: 'off' };
  // kitchen
  if (t >= 21.4 && t < 34.6) S.kit = { s: 'kitBoard', lt: t - 21.4, b: prog(t, 21.4, 22.0) * (1 - prog(t, 33.9, 34.6)), glow: ['#ffb27a', 0.6] };
  else if (t >= 68.4 && t < 81.4) S.kit = { s: 'kitRecipe', lt: t - 68.4, b: prog(t, 68.4, 69.0) * (1 - prog(t, 80.6, 81.4)), glow: ['#f7b955', 0.7], follow: eio(prog(t, 75.2, 77.0)) };
  else S.kit = { s: 'off' };
  // living room
  if (t < 33.2) S.liv = { s: 'off' };
  else if (t < 47.4) S.liv = { s: 'art', lt: t - 33.2, b: 0.62 * prog(t, 33.2, 34.6), glow: ['#ffe6c4', 0.25], which: 0, mix: 0, label: 0 };
  else if (t < 56.3) S.liv = { s: 'kid', lt: t - 47.4, b: 1, glow: ['#c38bff', 0.8], x: [['art', 0, 0, 0, 0], prog(t, 47.4, 47.95)] };
  else if (t < 75.4) S.liv = { s: 'art', lt: t - 56.3, b: 0.92, glow: ['#ffd9a8', 0.4], which: 0, mix: eio(prog(t, 62.5, 63.8)), label: trap(t, 57.2, 58.0, 61.8, 62.4) + trap(t, 64.0, 64.8, 70.0, 71.0), x: [['kid', t - 47.4], prog(t, 56.3, 57.0)] };
  else if (t < 81.6) S.liv = { s: 'nowPlaying', lt: t - 75.4, b: 1, glow: ['#f7a54a', 0.8], x: [['art', 20, 0, 1, 0], prog(t, 75.4, 76.1)] };
  else if (t < 83.0) S.liv = { s: 'livHome', lt: t - 81.6, b: 1, glow: ['#5b6cff', 0.6], voice: { a: eout(prog(t, 81.75, 82.15)), text: t > 82.1 ? 'Movie night.'.slice(0, Math.floor(prog(t, 82.1, 82.7) * 12)) : '', listening: t < 82.8 ? 1 : 0.3 }, x: [['nowPlaying', t - 75.4], prog(t, 81.6, 82.1)] };
  else if (t < 86.7) S.liv = { s: 'sceneCard', lt: t - 83.0, b: 1, glow: ['#9b7bff', 0.7] };
  else if (t < 107.2) {
    const mt = t < 92.25 ? t - 86.7 : t < 96.6 ? 92.25 - 86.7 : t - 86.7 - (96.6 - 92.25);
    S.liv = { s: 'movie', lt: mt, b: 1 - prog(t, 106.6, 107.2), glow: ['#4f7cff', 0.9], x: [['sceneCard', t - 83.0], prog(t, 86.7, 87.3)],
      paused: eout(prog(t, 92.25, 92.7)) * (1 - eio(prog(t, 96.6, 97.0))), pip: eout(prog(t, 92.05, 92.7)) * (1 - eio(prog(t, 96.2, 96.7))),
      resume: trap(t, 96.6, 96.9, 98.0, 98.5), feedText: t < 95.2 ? 'Pizza delivery at the door' : 'Picked up. Enjoy!', btn: t < 95.2 ? 'Talk' : 'Resume' };
  } else S.liv = { s: 'off' };
  // Lily
  if (t >= 99.4 && t < 105.6) S.lily = { s: 'lilyWind', lt: t - 99.4, b: prog(t, 99.4, 100.0) * (1 - prog(t, 104.9, 105.6)), glow: ['#8f7bff', 0.8] };
  else S.lily = { s: 'off' };
  return S;
}

// ---------------------------------------------------------------- overlays
export const CAPTIONS = [
  [10.3, 19.6, 'It wakes you gently.', 'Sunrise lights first. Then your day at a glance.'],
  [22.9, 32.6, 'Everyone’s morning, at a glance.', 'The kitchen TV knows who leaves when.'],
  [35.4, 43.6, 'It keeps watch while you’re out.', 'Away mode and package alerts, right on your phone.'],
  [45.2, 55.6, 'It knows who just got home.', 'Kid mode, homework first, parents in the loop.'],
  [57.2, 68.2, 'A frame, not a screen.', 'Art and family photos, matched to the light.'],
  [69.6, 80.6, 'Music follows you.', 'From the kitchen to the couch, without a tap.'],
  [82.0, 98.6, 'Movie night, on cue.', 'One phrase sets the room. Even the doorbell waits its turn.'],
  [100.0, 110.6, 'And it winds the day down.', 'Bedtime for Lily. Good night for the house.'],
];
export const NOTIFS = [
  { id: 'n1', t0: 35.7, t1: 39.6, icon: 'shield-check', col: '#86EFAC', title: 'Everyone’s out', body: 'Away mode is on. Lights off, doors locked.', thumb: false },
  { id: 'n2', t0: 39.3, t1: 44.0, icon: 'package', col: '#F7B955', title: 'Package delivered', body: 'Left at the front door · 11:58 AM', thumb: true },
  { id: 'n3', t0: 46.0, t1: 50.4, icon: 'house', col: '#FF8A7A', title: 'Lily is home', body: 'Front door · 3:41 PM. Kid mode is on.', thumb: false },
  { id: 'n4', t0: 107.4, t1: 111.4, icon: 'lock', col: '#B9A2FF', title: 'Good night', body: 'Doors locked. Lights off. Alarm set for 6:30.', thumb: false },
];
