import * as THREE from 'three';
import { makeWorld, applyTimeOfDay } from './world.js';
import { buildHouse } from './house.js';
import { SCREENS } from './ui.js';
import { ICONS } from './icons.js';
import * as S from './story.js';
import { clamp, prog, lerp, eio, eout, trap, eback } from './kit.js';

await Promise.all(['300 40px Inter', '450 40px Inter', '500 40px Inter', '600 40px Inter', '650 40px Inter', '700 40px Inter', '800 40px Inter', '200 40px Inter', '800 40px Bricolage'].map((f) => document.fonts.load(f)));
const Wd = makeWorld(document.getElementById('host'));
const Hs = buildHouse(Wd.scene);
const $ = (id) => document.getElementById(id);
// ---- light slots: only the 5 most relevant lamps get real point lights each frame
const LDEF = {};
for (const k in Hs.lights) { const l = Hs.lights[k]; LDEF[k] = { pos: l.position.clone(), color: l.color.clone(), dist: l.distance }; Wd.scene.remove(l); }
const SLOTS = Array.from({ length: 5 }, () => { const l = new THREE.PointLight(0xffffff, 0, 7, 2); Wd.scene.add(l); l.userData.key = null; return l; });
function assignLights(want) {
  const cp = Wd.camera.position;
  const cand = Object.entries(want).filter(([k, v]) => v > 0.02).map(([k, v]) => [k, v / (1 + LDEF[k].pos.distanceToSquared(cp) / 60)]).sort((a, b) => b[1] - a[1]).slice(0, SLOTS.length).map((x) => x[0]);
  const free = SLOTS.filter((s) => !cand.includes(s.userData.key));
  for (const k of cand) {
    let s = SLOTS.find((x) => x.userData.key === k);
    if (!s) { s = free.shift(); s.userData.key = k; s.position.copy(LDEF[k].pos); s.color.copy(LDEF[k].color); s.distance = LDEF[k].dist; }
    s.intensity = want[k];
  }
  for (const s of free) { s.intensity = 0; s.userData.key = null; }
}
let shadowsOn = true;
function setShadows(on) {
  if (on === shadowsOn) return; shadowsOn = on; Wd.renderer.shadowMap.enabled = on;
  Wd.scene.traverse((o) => { if (o.material) (Array.isArray(o.material) ? o.material : [o.material]).forEach((mm) => (mm.needsUpdate = true)); });
}
const svgIcon = (name, color, sw = 2) => `<svg viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">${(ICONS[name] || []).map((d) => `<path d="${d}"/>`).join('')}</svg>`;

// ------------------------------------------------------------ glow sprites + steam
const spriteMat = (col) => new THREE.SpriteMaterial({ map: Hs.haloTex, color: col, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false });
function glowSprite(p, size, col) { const s = new THREE.Sprite(spriteMat(col)); s.position.set(...p); s.scale.set(size, size, 1); Wd.scene.add(s); return s; }
const G = {
  bed: [glowSprite([-4.75, 4.15, 1.35], 1.2, 0xffb766), glowSprite([-1.85, 4.15, 1.35], 1.2, 0xffb766)],
  kit: Hs.pendPos.map((p) => glowSprite([p[0], p[1] - 0.03, p[2]], 1.0, 0xffc27a)),
  liv: [glowSprite([-1.95, 1.6, 0.2], 1.5, 0xffc27a)],
  livCeil: [glowSprite([0.9, 2.93, -1.0], 1.3, 0xffd6a0)],
  entry: [glowSprite([6.1, 2.93, 0.6], 1.1, 0xffd6a0)],
  porch: [glowSprite([7.33, 2.45, 1.75], 1.2, 0xffc985)],
  landing: [glowSprite([5.9, 6.2, 0.8], 0.9, 0xffd6a0)],
  lily: [glowSprite([2.3, 6.2, -0.5], 1.2, 0xffd0a0)],
  star: [glowSprite([3.07, 3.93, 0.42], 0.8, 0xffc96b)],
};
const steamMat = () => new THREE.SpriteMaterial({ map: Hs.haloTex, color: 0xffffff, transparent: true, opacity: 0, depthWrite: false });
const mkSteam = (n) => Array.from({ length: n }, () => { const s = new THREE.Sprite(steamMat()); Wd.scene.add(s); return s; });
const STEAM_MUG = mkSteam(9), STEAM_POT = mkSteam(12);
function steam(arr, base, t, vis, h = 0.35, w = 0.03, sz = 0.14) {
  arr.forEach((s, i) => { const ph = (t * 0.3 + i / arr.length) % 1; s.visible = vis > 0.01;
    s.position.set(base[0] + Math.sin(ph * 6 + i * 1.7) * w, base[1] + ph * h, base[2] + Math.cos(ph * 5 + i) * w * 0.7);
    const sc = sz * (0.4 + ph); s.scale.set(sc, sc, 1); s.material.opacity = Math.sin(Math.PI * ph) * 0.2 * vis; });
}

// ------------------------------------------------------------ porch camera feed
const porchCam = new THREE.PerspectiveCamera(74, 16 / 9, 0.05, 120);
porchCam.position.set(7.42, 1.4, 1.66); porchCam.lookAt(8.45, 0.05, 1.25); porchCam.updateMatrixWorld();
const feed = document.createElement('canvas'); feed.width = 640; feed.height = 360; const fg = feed.getContext('2d');
function renderFeed() {
  const r = Wd.renderer;
  r.setScissorTest(true); r.setViewport(0, 0, 640, 360); r.setScissor(0, 0, 640, 360);
  r.render(Wd.scene, porchCam);
  fg.drawImage(r.domElement, 0, 1080 - 360, 640, 360, 0, 0, 640, 360);
  r.setScissorTest(false); r.setViewport(0, 0, 1920, 1080); r.setScissor(0, 0, 1920, 1080);
  const gr = fg.createRadialGradient(320, 180, 120, 320, 180, 420); gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,0.45)'); fg.fillStyle = gr; fg.fillRect(0, 0, 640, 360);
}

// ------------------------------------------------------------ notifications DOM
const NOT = {};
for (const n of S.NOTIFS) {
  const el = document.createElement('div'); el.className = 'ov nt'; el.style.opacity = 0;
  el.innerHTML = `<div class="ic">${svgIcon('house', '#fff', 2.2)}</div><div class="tx"><div class="row"><span>HAVEN</span><span>now</span></div><div class="tt">${svgIcon(n.icon, n.col, 2.2)}${n.title}</div><div class="bd">${n.body}</div></div>` + (n.thumb ? '<canvas width="300" height="224"></canvas>' : '');
  $('notifs').appendChild(el); NOT[n.id] = { el, cv: el.querySelector('canvas') };
}
$('stampIc').innerHTML = svgIcon('moon', '#C9D6FF', 2);
let stampIcon = 'moon';

// ------------------------------------------------------------ screens
const frustum = new THREE.Frustum(), pmat = new THREE.Matrix4(), sph = new THREE.Sphere();
function visible(mesh) {
  pmat.multiplyMatrices(Wd.camera.projectionMatrix, Wd.camera.matrixWorldInverse); frustum.setFromProjectionMatrix(pmat);
  if (!mesh.geometry.boundingSphere) mesh.geometry.computeBoundingSphere();
  sph.copy(mesh.geometry.boundingSphere).applyMatrix4(mesh.matrixWorld); return frustum.intersectsSphere(sph);
}
function callScreen(g, name, args, st) {
  if (name === 'art') return args ? SCREENS.art(g, ...args) : SCREENS.art(g, st.lt, st.which, st.mix, st.label);
  if (name === 'kitRecipe') return SCREENS.kitRecipe(g, args ? args[0] : st.lt, args ? 0 : st.follow ?? 0);
  if (name === 'livHome') return SCREENS.livHome(g, args ? args[0] : st.lt, args ? null : st.voice);
  if (name === 'movie') return SCREENS.movie(g, args ? args[0] : st.lt, args ? {} : st.mo);
  return SCREENS[name](g, args ? args[0] : st.lt);
}
function offScreen(g) {
  g.fillStyle = '#040506'; g.fillRect(0, 0, 1920, 1080);
  const gr = g.createLinearGradient(0, 0, 1920, 1080); gr.addColorStop(0, 'rgba(255,255,255,0.05)'); gr.addColorStop(0.45, 'rgba(255,255,255,0.0)'); gr.addColorStop(1, 'rgba(255,255,255,0.02)');
  g.fillStyle = gr; g.fillRect(0, 0, 1920, 1080);
}
const _v = new THREE.Vector3();
function projWidth(sc) {
  const a = _v.set(sc.cx - sc.w / 2, sc.cy, sc.z).project(Wd.camera).x, b = _v.set(sc.cx + sc.w / 2, sc.cy, sc.z).project(Wd.camera).x;
  return Math.abs(b - a) * 960;
}
function drawScreen(sc, st) {
  const want = projWidth(sc) > 760 ? sc.hi : sc.lo;
  if (sc.cur !== want) { sc.cur = want; sc.uniforms.ui.value = want.tex; sc.sig = null; }
  let sig = null;
  if (st.s === 'off') sig = 'off';
  else if (st.s === 'bedAmbient') sig = 'amb';
  else if (st.s === 'art' && !(st.x && st.x[1] < 1)) sig = `art|${st.which}|${st.mix.toFixed(3)}|${st.label.toFixed(3)}`;
  sc.uniforms.bright.value = st.s === 'off' ? 1 : st.b;
  if (sig && sc.sig === sig) return;
  sc.sig = sig;
  const C_ = sc.cur, g = C_.ctx; g.setTransform(C_.cw / 1920, 0, 0, C_.ch / 1080, 0, 0); g.globalAlpha = 1;
  if (st.s === 'off') offScreen(g);
  else {
    if (st.x && st.x[1] < 1) { callScreen(g, st.x[0][0], st.x[0].slice(1), st); g.globalAlpha = st.x[1]; }
    callScreen(g, st.s, null, st); g.globalAlpha = 1;
  }
  C_.tex.needsUpdate = true;
}

// ------------------------------------------------------------ overlays
function ov(el, a, dy = 0, blur = 0) { el.style.opacity = a.toFixed(3); el.style.transform = `translate3d(0,${dy.toFixed(1)}px,0)`; el.style.filter = blur > 0.05 ? `blur(${blur.toFixed(2)}px)` : 'none'; }
const fade = document.createElement('div'); fade.className = 'ov'; fade.style.cssText = 'inset:0;background:#000'; document.body.appendChild(fade);
function overlays(t, hour) {
  // title + end
  const ta = eout(prog(t, 0.7, 1.8)) * (1 - eio(prog(t, 5.3, 6.5))), tb = eout(prog(t, 1.0, 2.2)) * (1 - eio(prog(t, 5.2, 6.4)));
  ov($('titleA'), ta, (1 - eout(prog(t, 0.7, 1.8))) * 20); ov($('titleB'), tb, (1 - eout(prog(t, 1.0, 2.2))) * 30, (1 - eout(prog(t, 1.0, 2.0))) * 14);
  ov($('title'), 1);
  const e1 = eout(prog(t, 112.0, 113.4)), e2 = eout(prog(t, 113.0, 114.2)), e3 = eout(prog(t, 114.2, 115.4));
  ov($('endB'), e1, (1 - e1) * 30, (1 - e1) * 14); ov($('endS'), e2, (1 - e2) * 20); ov($('endF'), e3 * 0.55);
  // stamp
  const sa = eout(prog(t, 7.0, 7.8)) * (1 - eio(prog(t, 110.6, 111.4)));
  ov($('stamp'), sa, (1 - eout(prog(t, 7.0, 7.8))) * -16); ov($('topscrim'), sa);
  $('stampTime').textContent = S.fmtClock(hour);
  const want = hour >= 6.62 && hour < 19.2 ? 'sun' : 'moon';
  if (want !== stampIcon) { $('stampIc').innerHTML = want === 'sun' ? svgIcon('sun', '#FFD27A', 2) : svgIcon('moon', '#C9D6FF', 2); stampIcon = want; }
  // captions
  let ca = 0, cb = 0, cap = null;
  for (const c of S.CAPTIONS) if (t >= c[0] - 0.01 && t < c[1]) cap = c;
  if (cap) {
    const [t0, t1, h1, h2] = cap;
    if ($('capH').textContent !== h1) { $('capH').textContent = h1; $('capS').textContent = h2; }
    ca = eout(prog(t, t0, t0 + 0.8)) * (1 - eio(prog(t, t1 - 0.55, t1))); cb = eout(prog(t, t0 + 0.3, t0 + 1.1)) * (1 - eio(prog(t, t1 - 0.6, t1 - 0.05)));
    ov($('capH'), ca, (1 - eout(prog(t, t0, t0 + 0.8))) * 26, (1 - eout(prog(t, t0, t0 + 0.6))) * 10); ov($('capS'), cb, (1 - eout(prog(t, t0 + 0.3, t0 + 1.1))) * 20);
  } else { ov($('capH'), 0); ov($('capS'), 0); }
  ov($('cap'), 1); ov($('scrim'), Math.max(ca, cb) * 0.95);
  // notifications
  for (const n of S.NOTIFS) {
    const el = NOT[n.id].el; const a = eout(prog(t, n.t0, n.t0 + 0.6)) * (1 - eio(prog(t, n.t1 - 0.5, n.t1)));
    if (a <= 0.002) { if (el.style.opacity !== '0') el.style.opacity = 0; continue; }
    const x = (1 - eout(prog(t, n.t0, n.t0 + 0.7))) * 680; const y = 70 - eio(prog(t, n.t1 - 0.5, n.t1)) * 40;
    el.style.opacity = a.toFixed(3); el.style.top = '0px'; el.style.transform = `translate3d(${x.toFixed(1)}px,${y.toFixed(1)}px,0) scale(${(0.96 + 0.04 * eout(prog(t, n.t0, n.t0 + 0.6))).toFixed(4)})`;
  }
  fade.style.opacity = (Math.max(1 - eout(prog(t, 0, 1.2)), eio(prog(t, 119.0, 120.0)))).toFixed(3);
}

// ------------------------------------------------------------ frame
let lastFeed = -1;
window.PROF = {};
window.renderAt = (t) => {
  const T0 = performance.now();
  const hour = S.clockAt(t);
  const L = applyTimeOfDay(Wd, hour);
  const dark = clamp(1 - L.hemiInt / 1.8);
  const gk = 0.25 + 0.75 * dark;
  // lamps
  const lv = {}; for (const k in S.LAMPS) lv[k] = S.LAMPS[k](t);
  const want = {};
  want.bed = 7 * lv.bed; Hs.mats.bedLamp.forEach((m) => (m.emissiveIntensity = 2.4 * lv.bed)); G.bed.forEach((s) => (s.material.opacity = 0.6 * lv.bed * gk));
  want.kit = 7 * lv.kit; Hs.mats.pend.forEach((m) => (m.emissiveIntensity = 3 * lv.kit)); G.kit.forEach((s) => (s.material.opacity = 0.55 * lv.kit * gk));
  want.liv = 7 * Math.max(lv.liv, 0.85 * lv.livCeil); Hs.mats.livLamp.emissiveIntensity = 2.4 * lv.liv; G.liv[0].material.opacity = 0.6 * lv.liv * gk;
  Hs.mats.livCeil.emissiveIntensity = 2.6 * lv.livCeil; G.livCeil[0].material.opacity = 0.45 * lv.livCeil * gk;
  want.entry = 5 * lv.entry; Hs.mats.entry.emissiveIntensity = 2.6 * lv.entry; G.entry[0].material.opacity = 0.5 * lv.entry * gk;
  want.porch = 6 * lv.porch; Hs.mats.porch.emissiveIntensity = 2.8 * lv.porch; G.porch[0].material.opacity = 0.65 * lv.porch * gk;
  want.landing = 4 * lv.landing; Hs.mats.landing.emissiveIntensity = 2.4 * lv.landing; G.landing[0].material.opacity = 0.45 * lv.landing * gk;
  want.lily = 6 * lv.lily; Hs.mats.lilyLamp.emissiveIntensity = 2.6 * lv.lily; G.lily[0].material.opacity = 0.45 * lv.lily * gk;
  want.star = 2.2 * lv.star; Hs.mats.star.emissiveIntensity = 2.6 * lv.star; G.star[0].material.opacity = 0.75 * lv.star * gk;
  Hs.mats.bedCeil.emissiveIntensity = 0;
  // props
  const fold = eio(prog(t, 15.0, 15.9)); const zEnd = lerp(1.0, 0.12, fold);
  Hs.props.duvet.scale.z = (zEnd + 0.76) / 1.76; Hs.props.duvet.position.z = (-0.76 + zEnd) / 2;
  const fr = Hs.props.duvetFold; fr.visible = fold > 0.02; fr.position.set(Hs.props.bedX, 3.25 + 0.66 + 0.04 * fold, zEnd + 0.05); fr.scale.set(0.4 + 0.6 * fold, 2.05, 0.4 + 0.6 * fold);
  Hs.mats.phone.emissiveIntensity = 1.4 * trap(t, 10.75, 10.95, 14.3, 14.6);
  Hs.props.mug.visible = Hs.props.mug2.visible = t >= 19 && t < 35;
  Hs.props.lunch.visible = t < 31.9;
  Hs.props.plates.forEach((p) => (p.visible = t >= 68.4 && t < 107)); Hs.props.pot.visible = t >= 68.4 && t < 107;
  const pk = Hs.props.package; pk.visible = (t >= 38.8 && t < 45.45) || t >= 46.3;
  if (t < 45.45) { pk.position.set(8.25, (1 - eback(prog(t, 38.8, 39.35), 1.6)) * 0.6, 1.55); pk.rotation.y = 0.25; } else { pk.position.set(6.35, 0.02, 1.05); pk.rotation.y = 0.1; }
  const bp = Hs.props.backpack; bp.visible = t >= 46.1; bp.position.y = 0.65 + (1 - eback(prog(t, 46.1, 46.6), 1.4)) * 0.35;
  const door = S.doorAt(t); Hs.props.door.rotation.y = -1.35 * door;
  const pz = Hs.props.pizzaPorch; pz.visible = t >= 91.6 && t < 95.3; pz.position.y = (1 - eback(prog(t, 91.6, 92.0), 1.6)) * 0.5;
  Hs.props.pizzaTable.visible = t >= 96.3; Hs.props.popcorn.visible = t >= 81;
  const pulse = 0.75 + 0.25 * Math.sin(t * 7);
  Hs.glow.doorbell.emissiveIntensity = 3 * Math.max(trap(t, 91.9, 92.0, 94.8, 95.2) * (0.6 + 0.4 * Math.sin(t * 12)), trap(t, 38.7, 38.8, 39.6, 40.0), trap(t, 45.1, 45.2, 45.9, 46.3));
  Hs.glow.kitSpk.emissiveIntensity = 2.6 * Math.max(trap(t, 21.6, 22.2, 32.6, 33.4), trap(t, 68.6, 69.4, 76.0, 77.6)) * pulse;
  const lspk = Math.max(trap(t, 75.8, 77.2, 81.5, 82.2), 0.65 * trap(t, 86.8, 87.4, 106.8, 107.4)) * pulse;
  Hs.glow.livSpk.forEach((m) => (m.emissiveIntensity = 2.6 * lspk)); Hs.glow.soundbar.emissiveIntensity = 2.6 * lspk;
  const B = S.blindsAt(t); Hs.blinds.bed(B.bed); Hs.blinds.liv(B.liv); Hs.blinds.lily(B.lily);
  steam(STEAM_MUG, [-4.75, 1.07, -1.25], t, trap(t, 19.5, 20.5, 33.5, 34.5), 0.3, 0.025, 0.12);
  steam(STEAM_POT, [-3.15, 1.14, -3.65], t, trap(t, 68.4, 69.4, 80.5, 82.0), 0.5, 0.05, 0.22);
  // camera
  S.cameraAt(t, Wd.camera); Wd.scene.updateMatrixWorld(); Wd.camera.updateMatrixWorld();
  // screens
  const st = S.screenStates(t);
  const needFeed = (t >= 39.2 && t < 44.1) || (st.liv.s === 'movie' && (st.liv.pip ?? 0) > 0.01);
  if (needFeed) renderFeed();
  if (st.liv.s === 'movie') st.liv.mo = { paused: st.liv.paused, pip: st.liv.pip, resume: st.liv.resume, feedText: st.liv.feedText, btn: st.liv.btn, feed: needFeed ? feed : null };
  let tvLight = 0; const T1 = performance.now();
  for (const k of ['bed', 'kit', 'liv', 'lily']) {
    const sc = Hs.screens[k], s = st[k];
    sc.glow.material.opacity = s.s === 'off' ? 0 : (s.b ?? 1) * (s.glow ? s.glow[1] : 0.4) * (0.08 + 0.6 * dark);
    if (s.glow) sc.glow.material.color.set(s.glow[0]);
    if (visible(sc.mesh) && !window.NOSCR) drawScreen(sc, s);
    else sc.uniforms.bright.value = s.s === 'off' ? 1 : s.b;
  }
  if (st.liv.s === 'movie') tvLight = 2.2 * st.liv.b * (1 - 0.55 * (st.liv.paused ?? 0)) * (0.85 + 0.15 * Math.sin(t * 3.1) * Math.sin(t * 1.7));
  else if (st.liv.s !== 'off' && st.liv.s !== 'art') tvLight = 0.9 * dark;
  want.livTv = tvLight; assignLights(want);
  setShadows(hour >= 6.55 && hour < 19.3);
  // notification thumbnail
  const n2 = NOT.n2; if (t >= 39.2 && t < 44.1 && n2.cv) { const c = n2.cv.getContext('2d'); c.drawImage(feed, 80, 0, 480, 360, 0, 0, 300, 224); }
  const T2 = performance.now();
  overlays(t, hour);
  const T3 = performance.now();
  Wd.render();
  const gl = Wd.renderer.getContext(); const px = new Uint8Array(4); gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
  const T4 = performance.now();
  window.PROF = { pre: T1 - T0, screens: T2 - T1, ov: T3 - T2, gl: T4 - T3 };
  return true;
};
Wd.renderer.compile(Wd.scene, Wd.camera);
window.renderAt(0);
window.Wd = Wd; window.Hs = Hs;
window.ready = true;
