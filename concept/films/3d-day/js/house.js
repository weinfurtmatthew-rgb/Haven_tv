// Builds the two-floor cutaway house, its furniture, lights and TV screens.
import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';

const SCREEN_VS = `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`;
const SCREEN_FS = `
uniform sampler2D ui; uniform sampler2D pip; uniform vec4 pipRect; uniform float pipA; uniform float bright; uniform float pipR; uniform float aspect;
varying vec2 vUv;
float sdRound(vec2 p, vec2 b, float r){ vec2 q = abs(p)-b+r; return length(max(q,0.))+min(max(q.x,q.y),0.)-r; }
void main(){
  vec3 c = texture2D(ui, vUv).rgb;
  if (pipA > 0.001) {
    vec2 p = (vUv - pipRect.xy) / pipRect.zw;
    if (p.x >= 0. && p.x <= 1. && p.y >= 0. && p.y <= 1.) {
      vec2 sz = vec2(pipRect.z * aspect, pipRect.w);
      float d = sdRound((p - 0.5) * sz, sz * 0.5, pipR);
      float m = 1. - smoothstep(-0.0015, 0.0015, d);
      c = mix(c, texture2D(pip, p).rgb, m * pipA);
    }
  }
  gl_FragColor = vec4(c * bright, 1.0);
  #include <colorspace_fragment>
}`;

export function buildHouse(scene) {
  const H = { lights: {}, glow: {}, screens: {}, props: {}, mats: {}, halos: {}, blinds: {} };
  const gloss = (color, roughness = 0.4, metalness = 0, extra = {}) => new THREE.MeshStandardMaterial({ color, roughness, metalness, ...extra });
  const std = (color, roughness = 0.85, metalness = 0, extra = {}) => (roughness < 0.5 ? gloss(color, roughness, metalness, extra) : new THREE.MeshLambertMaterial({ color, ...extra }));
  const M = {
    wall: std(0xF1ECE5, 0.95), section: std(0xFFFCF7, 0.9), ext: std(0xE8E1D6, 0.95), extDark: std(0xD9D0C3, 0.95),
    oak: std(0xC59D72, 0.62), oakLight: std(0xD9B994, 0.6), oakDark: std(0x8B6744, 0.65), tile: std(0xDCD8D1, 0.75),
    carpet: std(0xE4DCD0, 1.0), kidCarpet: std(0xEED9CE, 1.0),
    sage: std(0x93A995, 0.8), navy: std(0x39465E, 0.82), navyFab: std(0x3B4760, 0.95), terracotta: std(0xC9785A, 0.9),
    white: std(0xF7F4EF, 0.85), quartz: std(0xF3F1EC, 0.35), steel: std(0xC7CBD0, 0.35, 0.6), ink: std(0x1B1E23, 0.55),
    black: std(0x0C0E11, 0.28, 0.25), fabric: std(0x2A2E35, 0.95), linen: std(0xF4F0EA, 0.98), duvet: std(0xA9BACB, 0.97),
    coral: std(0xEE8B76, 0.95), sun: std(0xF4C35A, 0.8), mint: std(0x9FD4C0, 0.9), lilac: std(0xB9A7E0, 0.9), sky: std(0x8EC1E8, 0.9),
    roof: std(0x3A3F47, 0.92), roofEdge: std(0xFFFCF7, 0.9), grass: std(0x9DB888, 1.0), soil: std(0xD7C7AD, 1.0), stone: std(0xDCD6CC, 0.95),
    leaf: std(0x6E9A69, 0.9), leaf2: std(0x86AE78, 0.9), trunk: std(0x8B6A4E, 0.9), pot: std(0xC77E5E, 0.85), rug: std(0xD3C3AA, 1.0),
    cardboard: std(0xC89F72, 0.95), tape: std(0xE9DCC4, 0.8), pizza: std(0xF4EFE6, 0.9), red: std(0xC94B3E, 0.8),
    glass: new THREE.MeshBasicMaterial({ color: 0xd8e8f2, transparent: true, opacity: 0.08, depthWrite: false }),
    frame: std(0xF7F4EF, 0.6), shade: std(0xFFF6E8, 0.9, 0, { emissive: 0xffc47a, emissiveIntensity: 0 }),
  };
  H.mats = M;
  const add = (m) => { scene.add(m); return m; };
  const shadow = (m, c = true, r = true) => { m.castShadow = c; m.receiveShadow = r; return m; };
  // extents box; sec: put section material on +z face
  function bx(x0, x1, y0, y1, z0, z1, mat, { sec = false, r = 0, cast = true, recv = true, seg = 3 } = {}) {
    const w = x1 - x0, h = y1 - y0, d = z1 - z0;
    const g = r > 0 ? new RoundedBoxGeometry(w, h, d, seg, Math.min(r, w / 2 - 1e-3, h / 2 - 1e-3, d / 2 - 1e-3)) : new THREE.BoxGeometry(w, h, d);
    const m = new THREE.Mesh(g, sec ? [mat, mat, mat, mat, M.section, mat] : mat);
    m.position.set((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
    return add(shadow(m, cast, recv));
  }
  function cyl(x, y0, y1, z, r, mat, { seg = 32, r2 = null, cast = true } = {}) {
    const m = new THREE.Mesh(new THREE.CylinderGeometry(r2 === null ? r : r2, r, y1 - y0, seg), mat);
    m.position.set(x, (y0 + y1) / 2, z); return add(shadow(m, cast, true));
  }
  function sph(x, y, z, r, mat, sx = 1, sy = 1, sz = 1) {
    const m = new THREE.Mesh(new THREE.SphereGeometry(r, 32, 20), mat); m.position.set(x, y, z); m.scale.set(sx, sy, sz); return add(shadow(m));
  }

  // ------------------------------------------------------------ structure
  const XL = -7, XR = 7, ZB = -4, ZF = 2, F2 = 3.25, C2 = 6.25;
  // plinth + garden
  const plinth = new THREE.Mesh(new THREE.CylinderGeometry(12.2, 12.0, 0.9, 96), M.soil); plinth.position.set(0.6, -0.78, -0.6); add(shadow(plinth, false, true));
  const lawn = new THREE.Mesh(new THREE.CylinderGeometry(12.2, 12.2, 0.12, 96), M.grass); lawn.position.set(0.6, -0.33, -0.6); add(shadow(lawn, false, true));
  // stepping stones to the porch
  for (let i = 0; i < 6; i++) {
    const s = new THREE.Mesh(new RoundedBoxGeometry(0.62, 0.06, 0.48, 2, 0.05), M.stone);
    s.position.set(9.9 + Math.sin(i * 0.7) * 0.5, -0.26, 2.0 + i * 0.85); s.rotation.y = i * 0.35; add(shadow(s, false, true));
  }
  // floor slabs
  bx(-7.2, 7.2, -0.3, 0, -4.2, ZF, M.ext, { sec: true });
  bx(-7.0, -2.6, 0, 0.02, -4.0, ZF, M.tile, { cast: false });
  bx(-2.6, 4.4, 0, 0.02, -4.0, ZF, M.oak, { cast: false });
  bx(4.4, 7.0, 0, 0.02, -4.0, ZF, M.tile, { cast: false });
  bx(-7.2, 4.6, 3.0, F2, -4.2, ZF, M.ext, { sec: true });
  bx(4.6, 7.2, 3.0, F2, -0.6, ZF, M.ext, { sec: true });
  bx(4.6, 7.2, 3.0, F2, -4.2, -3.5, M.ext, { sec: true });
  bx(-7.0, -0.1, F2, F2 + 0.02, -4.0, ZF, M.carpet, { cast: false });
  bx(0.1, 4.4, F2, F2 + 0.02, -4.0, ZF, M.kidCarpet, { cast: false });
  bx(4.6, 7.0, F2, F2 + 0.02, -0.6, ZF, M.oakLight, { cast: false });
  bx(-7.2, 7.2, C2, C2 + 0.2, -4.2, ZF, M.ext, { sec: true });
  // back wall with window openings (extruded shape)
  const wallShape = new THREE.Shape();
  wallShape.moveTo(-7.2, -0.3); wallShape.lineTo(7.2, -0.3); wallShape.lineTo(7.2, C2 + 0.2); wallShape.lineTo(-7.2, C2 + 0.2); wallShape.lineTo(-7.2, -0.3);
  const WIN = { kit: [-6.15, -4.95, 1.2, 2.35], liv: [2.85, 4.15, 0.55, 2.55], bed: [-6.5, -5.1, 4.15, 5.65], lily: [2.95, 4.05, 4.3, 5.5], land: [5.45, 6.35, 4.35, 5.45] };
  for (const k in WIN) {
    const [a, b, c, d] = WIN[k]; const hp = new THREE.Path();
    hp.moveTo(a, c); hp.lineTo(a, d); hp.lineTo(b, d); hp.lineTo(b, c); hp.lineTo(a, c); wallShape.holes.push(hp);
  }
  const wg = new THREE.ExtrudeGeometry(wallShape, { depth: 0.2, bevelEnabled: false, curveSegments: 1 });
  const backWall = new THREE.Mesh(wg, M.wall); backWall.position.z = -4.2; add(shadow(backWall));
  for (const k in WIN) { // frames, sills, glass
    const [a, b, c, d] = WIN[k];
    bx(a - 0.06, b + 0.06, c - 0.06, c, -4.2, -3.9, M.frame); bx(a - 0.06, b + 0.06, d, d + 0.06, -4.2, -3.98, M.frame);
    bx(a - 0.06, a, c, d, -4.2, -3.98, M.frame); bx(b, b + 0.06, c, d, -4.2, -3.98, M.frame);
    bx((a + b) / 2 - 0.02, (a + b) / 2 + 0.02, c, d, -4.12, -4.08, M.frame);
    const gl = new THREE.Mesh(new THREE.PlaneGeometry(b - a, d - c), M.glass); gl.position.set((a + b) / 2, (c + d) / 2, -4.1); add(gl);
  }
  // side walls
  bx(-7.2, -7.0, -0.3, C2 + 0.2, -4.2, ZF, M.wall, { sec: true });
  bx(7.0, 7.2, -0.3, C2 + 0.2, -4.2, 0.4, M.wall, { sec: false });
  bx(7.0, 7.2, -0.3, C2 + 0.2, 1.45, ZF, M.wall, { sec: true });
  bx(7.0, 7.2, 2.3, C2 + 0.2, 0.4, 1.45, M.wall);
  // partitions
  bx(-2.7, -2.5, 0, 3.0, -4.0, -2.3, M.wall, { sec: true });
  bx(4.4, 4.6, 0, 3.0, -4.0, -1.3, M.wall); bx(4.4, 4.6, 0, 3.0, 0.1, ZF, M.wall, { sec: true }); bx(4.4, 4.6, 2.25, 3.0, -1.3, 0.1, M.wall);
  bx(-0.1, 0.1, F2, C2, -4.0, 0.3, M.wall); bx(-0.1, 0.1, F2, C2, 1.35, ZF, M.wall, { sec: true }); bx(-0.1, 0.1, F2 + 2.2, C2, 0.3, 1.35, M.wall);
  bx(4.4, 4.6, F2, C2, -4.0, 0.5, M.wall); bx(4.4, 4.6, F2, C2, 1.55, ZF, M.wall, { sec: true }); bx(4.4, 4.6, F2 + 2.2, C2, 0.5, 1.55, M.wall);
  // baseboards
  for (const [x0, x1, y] of [[-7, -2.7, 0], [-2.5, 4.4, 0], [4.6, 7, 0], [-7, -0.1, F2], [0.1, 4.4, F2], [4.6, 7, F2]]) bx(x0, x1, y, y + 0.09, -4.0, -3.96, M.white, { cast: false });
  // roof (gable along x)
  const ridgeZ = -1.0, ridgeY = 8.35, eaveY = C2 + 0.15;
  const slope = (z0, y0, z1, y1) => {
    const len = Math.hypot(z1 - z0, y1 - y0), ang = Math.atan2(y1 - y0, z1 - z0);
    const g = new THREE.BoxGeometry(15.4, 0.18, len + 0.25);
    const m = new THREE.Mesh(g, [M.roof, M.roof, M.roof, M.wall, M.roofEdge, M.roofEdge]);
    m.position.set(0, (y0 + y1) / 2 + 0.09, (z0 + z1) / 2); m.rotation.x = -ang; return add(shadow(m));
  };
  slope(ZF + 0.45, eaveY - 0.2, ridgeZ, ridgeY);
  slope(-4.65, eaveY - 0.2, ridgeZ, ridgeY);
  for (const xs of [-7.2, 7.0]) {
    const sh = new THREE.Shape(); sh.moveTo(ZB - 0.2, eaveY - 0.05); sh.lineTo(ZF, eaveY - 0.05); sh.lineTo(ridgeZ, ridgeY - 0.05); sh.lineTo(ZB - 0.2, eaveY - 0.05);
    const g = new THREE.ExtrudeGeometry(sh, { depth: 0.2, bevelEnabled: false });
    const m = new THREE.Mesh(g, M.wall); m.rotation.y = -Math.PI / 2; m.position.set(xs + 0.2, 0, 0); add(shadow(m));
  }

  // ------------------------------------------------------------ lamps & lights helpers
  function shadeMat(col = 0xffc47a) { return std(0xFFF4E4, 0.9, 0, { emissive: col, emissiveIntensity: 0 }); }
  function point(x, y, z, color, dist = 7) {
    const l = new THREE.PointLight(color, 0, dist, 2); l.position.set(x, y, z); add(l); return l;
  }
  const haloTex = (() => {
    const c = document.createElement('canvas'); c.width = c.height = 128; const g = c.getContext('2d');
    const gr = g.createRadialGradient(64, 64, 0, 64, 64, 64); gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.35, 'rgba(255,255,255,0.35)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 128, 128); const t = new THREE.CanvasTexture(c); return t;
  })();
  function halo(x, y, z, size, color, rotY = 0, facing = 'z') {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(size * 1.8, size), new THREE.MeshBasicMaterial({ map: haloTex, color, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false }));
    m.position.set(x, y, z); m.rotation.y = rotY; add(m); return m;
  }
  H.haloTex = haloTex;

  // ------------------------------------------------------------ TV screens
  function tv(name, cx, cy, z, w, cw, ch) {
    const h = w * 9 / 16;
    bx(cx - w / 2 - 0.018, cx + w / 2 + 0.018, cy - h / 2 - 0.018, cy + h / 2 + 0.018, z - 0.035, z, M.black, { r: 0.012, seg: 2 });
    const mk = (w_, h_) => { const c = document.createElement('canvas'); c.width = w_; c.height = h_; const x = c.getContext('2d'); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8; return { canvas: c, ctx: x, tex: t, cw: w_, ch: h_ }; };
    const hi = mk(cw, ch), lo = mk(Math.round(cw / 2), Math.round(ch / 2));
    const canvas = hi.canvas, ctx = hi.ctx, tex = hi.tex;
    const uniforms = { ui: { value: tex }, pip: { value: null }, pipRect: { value: new THREE.Vector4(0, 0, 0, 0) }, pipA: { value: 0 }, bright: { value: 1 }, pipR: { value: 0.03 }, aspect: { value: 16 / 9 } };
    const mat = new THREE.ShaderMaterial({ uniforms, vertexShader: SCREEN_VS, fragmentShader: SCREEN_FS, toneMapped: false });
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat); mesh.position.set(cx, cy, z + 0.002); add(mesh);
    const glow = halo(cx, cy, z - 0.04, w * 1.9, 0x88aaff);
    H.screens[name] = { mesh, canvas, ctx, tex, uniforms, w, h, cw, ch, cx, cy, z, glow, hi, lo, cur: hi };
    return H.screens[name];
  }

  // ------------------------------------------------------------ KITCHEN (x -7..-2.6)
  bx(-7.0, -6.25, 0, 2.15, -4.0, -3.3, M.steel, { r: 0.03 });                       // fridge
  bx(-6.95, -6.3, 1.2, 1.24, -3.32, -3.28, M.ink);
  bx(-6.25, -2.75, 0, 0.88, -4.0, -3.4, M.sage, { r: 0.02 });                         // base cabinets
  for (let x = -6.25; x < -2.8; x += 0.58) bx(x + 0.02, x + 0.56, 0.08, 0.84, -3.405, -3.38, M.sage, { r: 0.01, cast: false });
  bx(-6.27, -2.72, 0.88, 0.93, -4.0, -3.33, M.quartz);                                 // counter
  bx(-6.25, -2.75, 0.93, 1.42, -4.0, -3.97, std(0xE3DED3, 0.9), { cast: false });            // backsplash
  bx(-3.35, -2.95, 0.93, 0.935, -3.85, -3.45, M.black, { cast: false });              // cooktop
  bx(-2.75, -2.55, 0, 3.0, -4.0, -3.3, M.wall);
  bx(-6.2, -5.75, 0.93, 1.3, -3.9, -3.6, M.ink, { r: 0.03 });                          // coffee machine
  cyl(-5.97, 0.93, 1.02, -3.5, 0.04, M.white);
  bx(-3.25, -2.8, 1.6, 2.35, -4.0, -3.62, M.sage, { r: 0.02 });                        // upper cabinet right
  bx(-4.75, -4.55, 1.55, 1.58, -4.0, -3.75, M.oak);                                   // open shelf
  for (let i = 0; i < 3; i++) cyl(-4.72 + i * 0.07, 1.58, 1.7 + i * 0.02, -3.85, 0.025, [M.white, M.sage, M.terracotta][i]);
  cyl(-3.05, 0.93, 1.13, -3.62, 0.07, M.fabric);                                      // kitchen speaker
  const kitRing = new THREE.Mesh(new THREE.TorusGeometry(0.06, 0.008, 8, 32), std(0x111111, 0.5, 0, { emissive: 0x7dd3fc, emissiveIntensity: 0 }));
  kitRing.rotation.x = Math.PI / 2; kitRing.position.set(-3.05, 1.135, -3.62); add(kitRing); H.glow.kitSpk = kitRing.material;
  // island + stools
  bx(-5.65, -3.55, 0, 0.88, -1.85, -0.95, M.navy, { r: 0.03 });
  bx(-5.72, -3.48, 0.88, 0.94, -1.92, -0.88, M.quartz, { r: 0.015 });
  for (const sx of [-5.05, -4.15]) { cyl(sx, 0, 0.62, -0.45, 0.025, M.ink); cyl(sx, 0.62, 0.68, -0.45, 0.19, M.oak); }
  // pendants
  H.mats.pend = [];
  for (const px of [-5.25, -4.45]) {
    cyl(px, 2.05, 3.0, -1.4, 0.006, M.ink, { seg: 6, cast: false });
    const sm = shadeMat(0xffbe72); H.mats.pend.push(sm);
    const sh = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.2, 0.22, 32, 1, true), [M.ink]); sh.position.set(px, 1.96, -1.4); add(shadow(sh, true, false));
    const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.06, 16, 12), sm); bulb.position.set(px, 1.86, -1.4); add(bulb);
  }
  H.lights.kit = point(-4.85, 1.75, -1.2, 0xffcf96, 7);
  H.pendPos = [[-5.25, 1.86, -1.4], [-4.45, 1.86, -1.4]];
  // mug + plates
  H.props.mug = cyl(-4.75, 0.94, 1.05, -1.25, 0.045, M.white);
  H.props.mug2 = cyl(-4.35, 0.94, 1.04, -1.5, 0.04, M.terracotta);
  H.props.lunch = bx(-6.0 + 1.2, -5.65 + 1.2, 0.93, 1.06, -3.75, -3.5, M.mint, { r: 0.03 });
  H.props.plates = [];
  for (const [px, pz] of [[-5.15, -1.2], [-4.1, -1.2]]) { const p = cyl(px, 0.94, 0.955, pz, 0.15, M.white); p.visible = false; H.props.plates.push(p); }
  H.props.pot = cyl(-3.15, 0.935, 1.12, -3.65, 0.13, M.steel); H.props.pot.visible = false;
  tv('kit', -3.95, 1.86, -3.98, 1.12, 1280, 720);

  // ------------------------------------------------------------ LIVING (x -2.6..4.4)
  bx(-0.55, 2.35, 0, 0.48, -4.0, -3.52, M.oak, { r: 0.03 });                         // media console
  bx(-0.5, 2.3, 0.05, 0.43, -3.53, -3.515, M.oakDark, { cast: false });
  bx(0.35, 1.45, 0.48, 0.56, -3.85, -3.73, M.fabric, { r: 0.03 });                   // soundbar
  const sbStrip = new THREE.Mesh(new THREE.BoxGeometry(0.9, 0.006, 0.004), std(0x111111, 0.5, 0, { emissive: 0x7dd3fc, emissiveIntensity: 0 }));
  sbStrip.position.set(0.9, 0.52, -3.727); add(sbStrip); H.glow.soundbar = sbStrip.material;
  H.glow.livSpk = [];
  for (const sx of [-1.0, 2.8]) {
    bx(sx - 0.17, sx + 0.17, 0, 1.08, -3.85, -3.5, M.fabric, { r: 0.05 });
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.1, 0.008, 8, 32), std(0x111111, 0.5, 0, { emissive: 0x7dd3fc, emissiveIntensity: 0 }));
    ring.rotation.x = Math.PI / 2; ring.position.set(sx, 1.085, -3.675); add(ring); H.glow.livSpk.push(ring.material);
  }
  tv('liv', 0.9, 1.52, -3.98, 2.1, 1920, 1080);
  // sofa (back to camera)
  bx(-0.55, 2.55, 0.12, 0.46, 0.25, 1.35, M.navyFab, { r: 0.08 });
  bx(-0.55, 2.55, 0.42, 0.98, 1.05, 1.4, M.navyFab, { r: 0.1 });
  bx(-0.75, -0.45, 0.12, 0.72, 0.2, 1.4, M.navyFab, { r: 0.1 }); bx(2.45, 2.75, 0.12, 0.72, 0.2, 1.4, M.navyFab, { r: 0.1 });
  bx(-0.42, 0.98, 0.46, 0.6, 0.3, 1.05, M.navyFab, { r: 0.07 }); bx(1.02, 2.42, 0.46, 0.6, 0.3, 1.05, M.navyFab, { r: 0.07 });
  for (const [cx, cz, m] of [[-0.1, 0.92, M.terracotta], [2.1, 0.92, M.sun]]) { const c = bx(cx - 0.22, cx + 0.22, 0.58, 0.98, cz - 0.07, cz + 0.07, m, { r: 0.07 }); c.rotation.z = 0.1; }
  H.props.blanket = bx(2.0, 2.62, 0.6, 0.66, 0.2, 1.0, M.coral, { r: 0.03 });
  for (const lx of [-0.4, 2.4]) for (const lz of [0.3, 1.3]) cyl(lx, 0, 0.12, lz, 0.025, M.oakDark);
  bx(-0.9, 2.9, 0.02, 0.035, -2.5, 1.25, M.rug, { cast: false });                    // rug
  cyl(0.9, 0.36, 0.42, -0.85, 0.55, M.oak); cyl(0.9, 0, 0.36, -0.85, 0.05, M.oakDark);    // coffee table
  H.props.book = bx(0.55, 0.85, 0.42, 0.46, -0.95, -0.72, M.sky);
  H.props.popcorn = sph(1.25, 0.47, -0.8, 0.13, M.white, 1, 0.55, 1); H.props.popcorn.visible = false;
  H.props.pizzaTable = bx(0.62, 1.12, 0.42, 0.47, -1.1, -0.6, M.pizza, { r: 0.01 }); H.props.pizzaTable.visible = false;
  // floor lamp (left)
  cyl(-1.95, 0.02, 0.05, 0.2, 0.18, M.ink); cyl(-1.95, 0.05, 1.55, 0.2, 0.015, M.ink, { seg: 8 });
  H.mats.livLamp = shadeMat(0xffc27a);
  const ls = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.24, 0.32, 32, 1, true), H.mats.livLamp); ls.material.side = THREE.DoubleSide; ls.position.set(-1.95, 1.62, 0.2); add(ls);
  H.lights.liv = point(-1.6, 1.7, -0.2, 0xffd2a0, 8);
  H.lights.livTv = point(0.9, 1.3, -2.6, 0x8fb4ff, 6);
  // plant
  cyl(4.0, 0, 0.42, -3.35, 0.2, M.pot, { r2: 0.24 }); sph(4.0, 0.95, -3.35, 0.45, M.leaf, 0.9, 1.15, 0.9); sph(3.85, 1.3, -3.25, 0.3, M.leaf2);
  // side table + lamp (right of sofa)
  cyl(3.1, 0, 0.55, 0.9, 0.2, M.oakLight);

  // ------------------------------------------------------------ ENTRY + stairs + door + porch
  const steps = 12, rise = F2 / steps, run = 0.29;
  for (let i = 0; i < steps; i++) bx(4.6, 5.75, 0, (i + 1) * rise, 0.0 - (i + 1) * run, 0.0 - i * run + 0.001, M.oakLight, { cast: true });
  bx(5.75, 5.8, 0, F2 + 0.9, -3.48, 0.0, M.white);                                      // stair stringer/rail panel
  bx(6.55, 6.98, 0, 0.45, -1.7, -0.35, M.oak, { r: 0.03 });                             // bench
  for (const hz of [-1.5, -1.0, -0.55]) bx(6.93, 6.98, 1.65, 1.7, hz - 0.03, hz + 0.03, M.ink);
  H.props.backpack = bx(6.6, 6.92, 0.45, 0.85, -1.25, -0.8, M.coral, { r: 0.08 }); H.props.backpack.visible = false;
  const door = new THREE.Group(); door.position.set(7.1, 0, 0.42); add(door);
  const dp = new THREE.Mesh(new RoundedBoxGeometry(0.06, 2.24, 1.02, 2, 0.015), M.sage); dp.position.set(0, 1.12, 0.51); dp.castShadow = dp.receiveShadow = true; door.add(dp);
  const knob = new THREE.Mesh(new THREE.SphereGeometry(0.035, 12, 8), M.steel); knob.position.set(-0.06, 1.05, 0.92); door.add(knob);
  H.props.door = door;
  H.mats.entry = shadeMat(0xffd59a);
  const el = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.16, 0.05, 32), H.mats.entry); el.position.set(6.1, 2.97, 0.6); add(el);
  H.lights.entry = point(6.1, 2.6, 0.6, 0xffd6a6, 6);
  // porch
  bx(7.2, 9.1, -0.3, 0.0, -0.6, 2.4, M.oak, { r: 0.02 });
  bx(9.1, 9.5, -0.3, -0.15, -0.3, 2.1, M.oak, { r: 0.02 });
  bx(7.25, 7.33, 1.15, 1.35, 1.6, 1.72, M.ink, { r: 0.02 });                              // doorbell cam
  const dbLed = new THREE.Mesh(new THREE.TorusGeometry(0.03, 0.006, 8, 24), std(0x111111, 0.5, 0, { emissive: 0x7dd3fc, emissiveIntensity: 0 }));
  dbLed.rotation.y = Math.PI / 2; dbLed.position.set(7.335, 1.27, 1.66); add(dbLed); H.glow.doorbell = dbLed.material;
  H.mats.porch = shadeMat(0xffc985);
  const pl = new THREE.Mesh(new RoundedBoxGeometry(0.1, 0.28, 0.16, 2, 0.03), H.mats.porch); pl.position.set(7.28, 2.45, 1.75); add(pl);
  H.lights.porch = point(7.7, 2.3, 1.6, 0xffcf96, 6);
  H.props.package = new THREE.Group(); H.props.package.position.set(8.25, 0.0, 1.55); add(H.props.package);
  { const b = new THREE.Mesh(new RoundedBoxGeometry(0.5, 0.34, 0.4, 2, 0.015), M.cardboard); b.position.y = 0.17; b.castShadow = b.receiveShadow = true; H.props.package.add(b);
    const tp = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.005, 0.08), M.tape); tp.position.y = 0.343; H.props.package.add(tp); }
  H.props.package.visible = false;
  H.props.pizzaPorch = new THREE.Group(); H.props.pizzaPorch.position.set(8.0, 0, 0.95); add(H.props.pizzaPorch);
  { const b = new THREE.Mesh(new RoundedBoxGeometry(0.46, 0.07, 0.46, 2, 0.01), M.pizza); b.position.y = 0.035; b.castShadow = true; H.props.pizzaPorch.add(b);
    const s = new THREE.Mesh(new THREE.BoxGeometry(0.46, 0.004, 0.08), M.red); s.position.y = 0.072; H.props.pizzaPorch.add(s); }
  H.props.pizzaPorch.visible = false;
  // landing upstairs
  cyl(6.4, F2, F2 + 0.35, -1.6 + 1.6, 0.17, M.pot, { r2: 0.2 }); sph(6.4, F2 + 0.75, 0.0, 0.32, M.leaf, 1, 1.2, 1);
  bx(5.75, 7.0, F2 + 0.9, F2 + 0.94, -0.6, -0.56, M.white);
  H.mats.landing = shadeMat(0xffd59a);
  const ll = new THREE.Mesh(new THREE.CylinderGeometry(0.14, 0.14, 0.05, 32), H.mats.landing); ll.position.set(5.9, C2 - 0.03, 0.8); add(ll);
  H.lights.landing = point(5.9, C2 - 0.4, 0.8, 0xffd6a6, 5);

  // ------------------------------------------------------------ BEDROOM (upper, x -7..-0.1)
  const bedX = -3.3, bedHalf = 1.05;
  bx(bedX - 1.15, bedX + 1.15, F2, F2 + 0.86, 1.55, 1.7, M.linen, { r: 0.06 });           // headboard (toward camera)
  bx(bedX - bedHalf, bedX + bedHalf, F2, F2 + 0.34, -0.75, 1.56, M.oak, { r: 0.03 });     // frame
  bx(bedX - bedHalf + 0.04, bedX + bedHalf - 0.04, F2 + 0.34, F2 + 0.58, -0.7, 1.52, M.linen, { r: 0.06 });
  for (const px of [bedX - 0.5, bedX + 0.5]) bx(px - 0.36, px + 0.36, F2 + 0.58, F2 + 0.74, 1.05, 1.45, M.white, { r: 0.07 });
  // duvet: main part + fold (animated)
  H.props.duvet = bx(bedX - bedHalf + 0.01, bedX + bedHalf - 0.01, F2 + 0.56, F2 + 0.68, -0.76, 1.0, M.duvet, { r: 0.05 });
  H.props.duvetFold = cyl(0, 0, 1, 0, 0.09, M.duvet, { seg: 24 }); H.props.duvetFold.rotation.z = Math.PI / 2; H.props.duvetFold.scale.set(1, 2.05, 1); H.props.duvetFold.visible = false;
  H.props.bedX = bedX;
  H.mats.bedLamp = [];
  for (const nx of [bedX - 1.45, bedX + 1.45]) {
    bx(nx - 0.27, nx + 0.27, F2, F2 + 0.55, 1.08, 1.62, M.oak, { r: 0.03 });
    cyl(nx, F2 + 0.55, F2 + 0.82, 1.35, 0.04, M.ink, { seg: 12 });
    const sm = shadeMat(0xffbb70); H.mats.bedLamp.push(sm);
    const sh = new THREE.Mesh(new THREE.CylinderGeometry(0.13, 0.16, 0.22, 32, 1, true), sm); sh.material.side = THREE.DoubleSide; sh.position.set(nx, F2 + 0.9, 1.35); add(sh);
  }
  H.mats.phone = new THREE.MeshLambertMaterial({ color: 0x0c0e11, emissive: 0xffc890, emissiveIntensity: 0 });
  H.props.phone = bx(bedX + 1.3, bedX + 1.5, F2 + 0.55, F2 + 0.565, 1.15, 1.25, H.mats.phone, { cast: false });
  H.lights.bed = point(bedX, F2 + 1.3, 1.1, 0xffc890, 8);
  bx(bedX - 1.1, bedX + 1.1, F2, F2 + 0.75, -4.0, -3.52, M.oak, { r: 0.03 });             // dresser
  for (let i = 0; i < 3; i++) bx(bedX - 1.05 + i * 0.71, bedX - 0.39 + i * 0.71, F2 + 0.08, F2 + 0.7, -3.525, -3.51, M.oakLight, { cast: false });
  cyl(bedX + 0.88, F2 + 0.75, F2 + 0.88, -3.75, 0.06, M.white); sph(bedX + 0.88, F2 + 0.97, -3.75, 0.1, M.leaf2, 1, 1.1, 1);
  tv('bed', bedX, F2 + 1.62, -3.98, 1.6, 1920, 1080);
  bx(-6.9, -1.0, F2 + 0.02, F2 + 0.035, -2.6, 1.4, M.rug, { cast: false });
  // armchair corner
  bx(-6.6, -5.6, F2, F2 + 0.42, -1.6, -0.6, M.terracotta, { r: 0.1 }); bx(-6.6, -6.35, F2 + 0.3, F2 + 1.0, -1.6, -0.6, M.terracotta, { r: 0.1 });
  // blinds helper (roller shade)
  function blind(key, [a, b, c, d]) {
    const roll = cyl(0, 0, 1, 0, 0.045, M.white, { seg: 16 }); roll.rotation.z = Math.PI / 2; roll.scale.set(1, b - a + 0.16, 1); roll.position.set((a + b) / 2, d + 0.05, -3.9);
    const sheet = new THREE.Mesh(new THREE.BoxGeometry(b - a + 0.1, 1, 0.012), std(0xF6EFE4, 0.95)); sheet.castShadow = true; sheet.receiveShadow = true; add(sheet);
    H.blinds[key] = (f) => { const hh = Math.max(0.001, (d - c + 0.04) * (1 - f)); sheet.scale.y = hh; sheet.position.set((a + b) / 2, d + 0.02 - hh / 2, -3.89); sheet.visible = hh > 0.01; };
    H.blinds[key](1);
  }
  blind('bed', WIN.bed); blind('liv', WIN.liv); blind('lily', WIN.lily);

  // ------------------------------------------------------------ LILY'S ROOM (upper, x 0.1..4.4)
  bx(3.35, 4.38, F2, F2 + 0.32, -2.6, 0.05, M.white, { r: 0.04 });
  bx(3.38, 4.35, F2 + 0.32, F2 + 0.5, -2.56, 0.0, M.linen, { r: 0.05 });
  bx(3.37, 4.36, F2 + 0.48, F2 + 0.58, -1.9, 0.02, M.coral, { r: 0.04 });
  bx(3.5, 4.22, F2 + 0.5, F2 + 0.64, -2.5, -2.05, M.white, { r: 0.06 });
  sph(3.85, F2 + 0.7, -1.55, 0.13, M.oakLight); sph(3.85, F2 + 0.92, -1.55, 0.1, M.oakLight); sph(3.77, F2 + 1.0, -1.55, 0.035, M.oakLight); sph(3.93, F2 + 1.0, -1.55, 0.035, M.oakLight);
  bx(0.9, 2.3, F2, F2 + 0.55, -4.0, -3.62, M.white, { r: 0.03 });
  for (let i = 0; i < 4; i++) bx(1.0 + i * 0.32, 1.22 + i * 0.32, F2 + 0.55, F2 + 0.75, -3.85, -3.7, [M.coral, M.sun, M.mint, M.lilac][i], { r: 0.02 });
  tv('lily', 1.6, F2 + 1.32, -3.98, 1.15, 1280, 720);
  bx(0.2, 0.55, F2, F2 + 1.4, -3.4, -2.3, M.white, { r: 0.02 });                          // bookshelf
  for (let r = 0; r < 3; r++) for (let i = 0; i < 4; i++) bx(0.28, 0.5, F2 + 0.12 + r * 0.45, F2 + 0.4 + r * 0.45 - (i % 2) * 0.05, -3.35 + i * 0.25, -3.2 + i * 0.25, [M.coral, M.sky, M.sun, M.mint, M.lilac][(r + i) % 5]);
  const rugK = new THREE.Mesh(new THREE.CylinderGeometry(1.0, 1.0, 0.015, 48), M.lilac); rugK.position.set(1.9, F2 + 0.03, -0.6); add(shadow(rugK, false, true));
  const blocks = [[1.6, -0.5, M.coral], [1.85, -0.35, M.sun], [1.72, -0.42, M.mint], [2.3, -0.9, M.sky]];
  blocks.forEach(([x, z, m], i) => { const b = bx(x - 0.09, x + 0.09, F2 + 0.03 + (i === 2 ? 0.18 : 0), F2 + 0.21 + (i === 2 ? 0.18 : 0), z - 0.09, z + 0.09, m, { r: 0.02 }); b.rotation.y = i * 0.5; });
  // nightstand + star nightlight + lamp
  bx(2.85, 3.3, F2, F2 + 0.45, 0.2, 0.62, M.white, { r: 0.03 });
  const star = new THREE.Shape(); for (let i = 0; i < 10; i++) { const r = i % 2 ? 0.06 : 0.14, a = Math.PI / 2 + i * Math.PI / 5; const x = Math.cos(a) * r, y = Math.sin(a) * r; i ? star.lineTo(x, y) : star.moveTo(x, y); }
  H.mats.star = std(0xFFE7A8, 0.6, 0, { emissive: 0xffc96b, emissiveIntensity: 0 });
  const st = new THREE.Mesh(new THREE.ExtrudeGeometry(star, { depth: 0.05, bevelEnabled: true, bevelSize: 0.01, bevelThickness: 0.01, bevelSegments: 2 }), H.mats.star);
  st.position.set(3.07, F2 + 0.62, 0.38); add(st);
  H.lights.lily = point(2.3, F2 + 2.0, -0.5, 0xffd0a0, 7);
  H.lights.star = point(3.07, F2 + 0.75, 0.6, 0xffc06a, 3);
  H.mats.lilyLamp = shadeMat(0xffc27a);
  const ceilLily = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.22, 0.05, 32), H.mats.lilyLamp); ceilLily.position.set(2.3, C2 - 0.03, -0.5); add(ceilLily);
  H.mats.bedCeil = shadeMat(0xffc890);
  const ceilBed = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.25, 0.05, 32), H.mats.bedCeil); ceilBed.position.set(-3.3, C2 - 0.03, -1.0); add(ceilBed);
  H.mats.livCeil = shadeMat(0xffc890);
  const ceilLiv = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.25, 0.05, 32), H.mats.livCeil); ceilLiv.position.set(0.9, 2.97, -1.0); add(ceilLiv);

  // ------------------------------------------------------------ garden
  const tree = (x, z, s, m = M.leaf) => { cyl(x, -0.3, 1.2 * s, z, 0.09 * s, M.trunk, { seg: 10 }); sph(x, 1.8 * s, z, 0.95 * s, m, 1, 1.15, 1); sph(x + 0.45 * s, 1.4 * s, z + 0.2 * s, 0.6 * s, M.leaf2); };
  tree(-9.4, -2.4, 1.25); tree(-8.8, 3.2, 0.9, M.leaf2); tree(10.0, -3.4, 1.15); tree(-4.5, -6.6, 1.0, M.leaf2); tree(4.8, -6.9, 1.3);
  for (const [x, z, r] of [[-8.3, 0.4, 0.5], [-9.6, 0.9, 0.42], [8.4, -2.1, 0.45], [9.6, 4.6, 0.5], [-1.2, -5.3, 0.55], [1.6, -5.4, 0.48], [-7.2, 5.8, 0.4]]) sph(x, -0.25 + r * 0.6, z, r, M.leaf2, 1, 0.8, 1);
  return H;
}
