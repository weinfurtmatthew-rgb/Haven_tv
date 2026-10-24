// Renderer, post-processing, sky, sun and the time-of-day lighting model.
import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { keyed, hex, mixc, smooth, clamp, lerp } from './kit.js';

export const W = 1920, H = 1080;

export function makeWorld(canvasHost) {
  const Q = new URLSearchParams(location.search);
  const renderer = new THREE.WebGLRenderer({ antialias: Q.get('aa') !== '0', preserveDrawingBuffer: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(1); renderer.setSize(W, H);
  renderer.shadowMap.enabled = Q.get('shadow') !== 'off'; renderer.shadowMap.type = Q.get('shadow') === 'pcf' ? THREE.PCFShadowMap : Q.get('shadow') === 'basic' ? THREE.BasicShadowMap : Q.get('shadow') === 'vsm' ? THREE.VSMShadowMap : THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.NeutralToneMapping; renderer.toneMappingExposure = 1.0;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  canvasHost.appendChild(renderer.domElement);
  const scene = new THREE.Scene();
  const pm = new THREE.PMREMGenerator(renderer);
  scene.environment = pm.fromScene(new RoomEnvironment(), 0.04).texture;
  scene.environmentIntensity = 0.5;
  const camera = new THREE.PerspectiveCamera(30, W / H, 0.05, 600);

  // sky dome with vertical gradient + stars
  const skyU = { top: { value: new THREE.Color(0x0b1630) }, hor: { value: new THREE.Color(0x1e2a4a) }, low: { value: new THREE.Color(0x10182c) }, stars: { value: 0 } };
  const sky = new THREE.Mesh(new THREE.SphereGeometry(300, 48, 24), new THREE.ShaderMaterial({
    uniforms: skyU, side: THREE.BackSide, depthWrite: false, toneMapped: false,
    vertexShader: `varying vec3 vP; void main(){ vP = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: `uniform vec3 top; uniform vec3 hor; uniform vec3 low; uniform float stars; varying vec3 vP;
      float h(vec3 p){ return fract(sin(dot(p, vec3(12.9898,78.233,45.164)))*43758.5453); }
      void main(){ float y = vP.y; vec3 c = y > 0.0 ? mix(hor, top, pow(clamp(y*1.6,0.,1.), 0.7)) : mix(hor, low, clamp(-y*4.0,0.,1.));
        if (stars > 0.0 && y > 0.05) { vec3 q = floor(vP * 260.0); float s = step(0.9965, h(q)); c += vec3(s * stars * (0.6 + 0.4*h(q+1.0))); }
        gl_FragColor = vec4(c, 1.0);
        #include <colorspace_fragment>
      }`
  }));
  scene.add(sky);

  const sun = new THREE.DirectionalLight(0xffffff, 2.5);
  sun.castShadow = true; sun.shadow.mapSize.set(+(Q.get('sm') || 2048), +(Q.get('sm') || 2048));
  const sc = sun.shadow.camera; sc.left = -13; sc.right = 13; sc.top = 11; sc.bottom = -9; sc.near = 1; sc.far = 80;
  sun.shadow.bias = -0.0004; sun.shadow.normalBias = 0.02;
  scene.add(sun); scene.add(sun.target); sun.target.position.set(0.5, 2.5, -1);
  const hemi = new THREE.HemisphereLight(0xbcd2ff, 0xd9c7a8, 0.6); scene.add(hemi);

  const render = () => renderer.render(scene, camera);
  return { renderer, scene, camera, sky, skyU, sun, hemi, render };
}

// ---------------------------------------------------------------- time-of-day model (clock hours)
// each key: [hour, {top, hor, low, sunCol, sunInt, sunDir:[x,y,z], hemiSky, hemiGnd, hemiInt, env, stars, exp}]
const K = [
  [5.5, { top: 0x060b1a, hor: 0x121c36, low: 0x0b1222, sunCol: 0x8fa8ff, sunInt: 0.35, sunDir: [-0.35, 0.7, 0.62], hemiSky: 0x5a6a9a, hemiGnd: 0x1a1c24, hemiInt: 0.45, env: 0.08, stars: 1.0, exp: 1.0 }],
  [6.45, { top: 0x18245a, hor: 0x7a5274, low: 0x1a1a2c, sunCol: 0xff8a5c, sunInt: 0.35, sunDir: [0.85, 0.1, -0.55], hemiSky: 0x7a7aa8, hemiGnd: 0x3a3030, hemiInt: 0.7, env: 0.12, stars: 0.45, exp: 1.0 }],
  [6.85, { top: 0x5070aa, hor: 0xf7ad80, low: 0x5a4a52, sunCol: 0xffa565, sunInt: 2.4, sunDir: [0.75, 0.24, -0.62], hemiSky: 0xc0c4e4, hemiGnd: 0x9a7a66, hemiInt: 1.25, env: 0.25, stars: 0.0, exp: 1.0 }],
  [7.6, { top: 0x80aadc, hor: 0xf7dcc0, low: 0xc6b4a2, sunCol: 0xffd9ae, sunInt: 2.6, sunDir: [0.55, 0.42, -0.6], hemiSky: 0xd6e2f6, hemiGnd: 0xd2bc9c, hemiInt: 1.7, env: 0.4, stars: 0, exp: 1.0 }],
  [12.0, { top: 0x8cc0ec, hor: 0xe9f2f8, low: 0xdcd5c9, sunCol: 0xfff3e2, sunInt: 2.7, sunDir: [-0.32, 0.72, 0.62], hemiSky: 0xe0ebfa, hemiGnd: 0xe0d2b8, hemiInt: 2.1, env: 0.5, stars: 0, exp: 1.0 }],
  [15.7, { top: 0x88b7e5, hor: 0xf3e4cd, low: 0xdacdb8, sunCol: 0xffe4bc, sunInt: 2.6, sunDir: [-0.5, 0.5, 0.62], hemiSky: 0xdae4f6, hemiGnd: 0xdcc8a8, hemiInt: 1.9, env: 0.46, stars: 0, exp: 1.0 }],
  [18.55, { top: 0x6680ba, hor: 0xffb476, low: 0xb48468, sunCol: 0xffa050, sunInt: 3.0, sunDir: [-0.78, 0.17, -0.6], hemiSky: 0xd8b8c8, hemiGnd: 0xb88a68, hemiInt: 1.35, env: 0.32, stars: 0, exp: 1.0 }],
  [19.25, { top: 0x24305e, hor: 0xbf7262, low: 0x3a2c38, sunCol: 0xff7a50, sunInt: 0.5, sunDir: [-0.85, 0.06, -0.5], hemiSky: 0x70709c, hemiGnd: 0x3b3038, hemiInt: 0.75, env: 0.14, stars: 0.15, exp: 1.0 }],
  [20.4, { top: 0x080d1e, hor: 0x1a2342, low: 0x0b1020, sunCol: 0x93abff, sunInt: 0.35, sunDir: [-0.32, 0.72, 0.6], hemiSky: 0x58607e, hemiGnd: 0x1a1a1f, hemiInt: 0.3, env: 0.07, stars: 1.0, exp: 1.0 }],
  [24.0, { top: 0x050916, hor: 0x121b35, low: 0x080c18, sunCol: 0x93abff, sunInt: 0.3, sunDir: [-0.32, 0.72, 0.6], hemiSky: 0x4c5474, hemiGnd: 0x141418, hemiInt: 0.26, env: 0.05, stars: 1.0, exp: 1.0 }],
];
function lerpKey(h) {
  let i = 1; while (i < K.length - 1 && h > K[i][0]) i++;
  const [h0, a] = K[i - 1], [h1, b] = K[i];
  const x = smooth(clamp((h - h0) / (h1 - h0)));
  const c = (k) => mixc(hex(a[k]), hex(b[k]), x);
  const n = (k) => lerp(a[k], b[k], x);
  const d = [0, 1, 2].map((j) => lerp(a.sunDir[j], b.sunDir[j], x));
  return { top: c('top'), hor: c('hor'), low: c('low'), sunCol: c('sunCol'), sunInt: n('sunInt'), sunDir: d, hemiSky: c('hemiSky'), hemiGnd: c('hemiGnd'), hemiInt: n('hemiInt'), env: n('env'), stars: n('stars'), exp: n('exp') };
}
export function applyTimeOfDay(Wd, hour) {
  const L = lerpKey(hour);
  const S = THREE.SRGBColorSpace; Wd.skyU.top.value.setRGB(...L.top, S); Wd.skyU.hor.value.setRGB(...L.hor, S); Wd.skyU.low.value.setRGB(...L.low, S); Wd.skyU.stars.value = L.stars;
  const d = new THREE.Vector3(...L.sunDir).normalize();
  Wd.sun.position.copy(Wd.sun.target.position).addScaledVector(d, 40);
  Wd.sun.color.setRGB(...L.sunCol, S); Wd.sun.intensity = L.sunInt;
  Wd.hemi.color.setRGB(...L.hemiSky, S); Wd.hemi.groundColor.setRGB(...L.hemiGnd, S); Wd.hemi.intensity = L.hemiInt;
  Wd.scene.environmentIntensity = L.env;
  return L;
}
