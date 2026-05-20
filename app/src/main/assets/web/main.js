import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { GUI } from "three/addons/libs/lil-gui.module.min.js";

const app = document.getElementById("app");
const isDevPanelEnabled = new URLSearchParams(window.location.search).get("dev") === "1";

const scene = new THREE.Scene();
scene.background = null;

function createSoftEnvironmentTexture() {
  const canvas = document.createElement("canvas");
  canvas.width = 512;
  canvas.height = 256;
  const ctx = canvas.getContext("2d");
  const sky = ctx.createLinearGradient(0, 0, 0, canvas.height);
  sky.addColorStop(0, "#ffffff");
  sky.addColorStop(0.42, "#9fb1cc");
  sky.addColorStop(0.58, "#3b414a");
  sky.addColorStop(1, "#111318");
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  const highlight = ctx.createRadialGradient(130, 78, 0, 130, 78, 160);
  highlight.addColorStop(0, "rgba(255,255,255,0.95)");
  highlight.addColorStop(0.36, "rgba(255,255,255,0.32)");
  highlight.addColorStop(1, "rgba(255,255,255,0)");
  ctx.fillStyle = highlight;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  const texture = new THREE.CanvasTexture(canvas);
  texture.mapping = THREE.EquirectangularReflectionMapping;
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

scene.environment = createSoftEnvironmentTexture();

const camera = new THREE.PerspectiveCamera(40, window.innerWidth / window.innerHeight, 0.1, 200);
// Default view: oblique ~45° looking at the ground.
camera.position.set(-35, 25, 40); 
camera.lookAt(-2,0, -20);

const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.setClearColor(0x000000, 0);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.12;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
app.appendChild(renderer.domElement);

// Lock camera interaction unless dev mode is enabled (?dev=1).
const controls = isDevPanelEnabled ? new OrbitControls(camera, renderer.domElement) : null;
if (controls) {
  controls.enableDamping = true;
  controls.target.set(0, 0.6, 0);
}

scene.add(new THREE.HemisphereLight(0xffffff, 0x334455, 1.05));
const rimLight = new THREE.DirectionalLight(0x9fb7ff, 0.85);
rimLight.position.set(-18, 12, -16);
scene.add(rimLight);
const sun = new THREE.DirectionalLight(0xffffff, 1.35);
// Placeholder until GLB loads; then aligned to model center + overhead offset.
sun.position.set(0, 18, 2);
sun.castShadow = true;
// Shadow "square" edge appears when the ortho shadow frustum is too small and its
// boundary becomes visible on the ground. Start with a generous extent, then
// fit it to the model after GLB loads.
let shadowExtent = 60;
sun.shadow.mapSize.set(2048, 2048);
sun.shadow.camera.near = 0.4;
sun.shadow.camera.far = 140;
sun.shadow.camera.left = -shadowExtent;
sun.shadow.camera.right = shadowExtent;
sun.shadow.camera.top = shadowExtent;
sun.shadow.camera.bottom = -shadowExtent;
sun.shadow.bias = -0.00025;
sun.shadow.normalBias = 0.035;
sun.shadow.radius = 3.5;
scene.add(sun);
scene.add(sun.target);

// Grid floor: frosted-glass-ish dark gray plane + subtle grid lines.
// Plane exists mainly to receive shadows; grid provides the "raster" look.
// Ground "map" size (plane + grid). Increase to show more area.
const groundSize = 80;
const groundGeo = new THREE.PlaneGeometry(groundSize, groundSize);
const groundMat = new THREE.MeshPhysicalMaterial({
  color: 0x76684a,
  roughness: 0.92,
  metalness: 0,
  transparent: true,
  opacity: 0.05,
  transmission: 0.25,
  thickness: 0.8,
  ior: 1.35
});
const ground = new THREE.Mesh(groundGeo, groundMat);
ground.rotation.x = -Math.PI / 2;
ground.position.y = -0.02;
ground.receiveShadow = true;
scene.add(ground);

// Keep grid cell density roughly consistent when scaling groundSize.
const grid = new THREE.GridHelper(groundSize, 160, 0x6f7886, 0x404652);
grid.position.y = -0.019; // slightly above plane to avoid z-fighting
grid.material.transparent = true;
grid.material.opacity = 0.28;
grid.material.depthWrite = false;
scene.add(grid);

function createContactShadowTexture(size = 512) {
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d");
  const gradient = ctx.createRadialGradient(size / 2, size / 2, size * 0.08, size / 2, size / 2, size * 0.48);
  gradient.addColorStop(0, "rgba(0,0,0,0.34)");
  gradient.addColorStop(0.45, "rgba(0,0,0,0.18)");
  gradient.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, size, size);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

const contactShadow = new THREE.Mesh(
  new THREE.PlaneGeometry(1, 1),
  new THREE.MeshBasicMaterial({
    map: createContactShadowTexture(),
    transparent: true,
    opacity: 0.75,
    depthWrite: false
  })
);
contactShadow.rotation.x = -Math.PI / 2;
contactShadow.position.y = -0.016;
contactShadow.renderOrder = 0;
scene.add(contactShadow);


if (isDevPanelEnabled) {
  const axes = new THREE.AxesHelper(2.5);
  scene.add(axes);
}

const state = {
  main: {
    roll: 0,
    pitch: 0,
    yaw: 0
  },
  lengths: {
    boom: 1,
    stick: 1
  },
  joints: {
    base: { x: 0, y: 0, z: 0 },
    boom: { x: 0, y: 0, z: 0 },
    stick: { x: 0, y: 0, z: 0 },
    bucket: { x: 0, y: 0, z: 0 }
  }
};

const nodes = {
  main: null,
  car: null,
  armature: null,
  base: null,
  boom: null,
  stick: null,
  bucket: null,
  driverCabin: null,
  track: null,
  diggingBucket: null
};

const baseScale = {
  boom: new THREE.Vector3(1, 1, 1),
  stick: new THREE.Vector3(1, 1, 1)
};

const lengthAxis = {
  boom: "y",
  stick: "y"
};

const partColors = {
  theme: 0x365fff,
  themeEdge: 0x8fa7ff,
  themeSilhouette: 0x132aff,
  transparentBody: 0x24282d,
  transparentBodyEdge: 0x5c6670,
  silhouette: 0x020304
};

const EDGE_OUTLINE_KEY = "excavatorEdgeOutline";
const SILHOUETTE_OUTLINE_KEY = "excavatorSilhouetteOutline";

function degToRad(value) {
  return THREE.MathUtils.degToRad(value);
}

// IMU values arrive in the [-180, 180] range. For joints with a calibrated
// start pose, compare against the start reading with wrap-around normalization
// so values remain continuous when they cross the +/-180 boundary.
const IMU_CONFIG = {
  boom:   { sign: -1 },
  stick:  { inputStart: -171.66, outputStart: 96, sign: 1 },
  bucket: { inputStart: 70, outputStart: 20, sign: -1, scale: 33 / (60 - 35.14) }
  // 35.15,但是这里偏差应该稍微大一点的
};

function normalizeAngleDelta(value) {
  return THREE.MathUtils.euclideanModulo(value + 180, 360) - 180;
}

function imuToLocalAngle(jointName, imuValue) {
  const cfg = IMU_CONFIG[jointName];
  if (!cfg) return imuValue;
  if (typeof cfg.inputStart === "number" && typeof cfg.outputStart === "number") {
    const delta = normalizeAngleDelta(imuValue - cfg.inputStart);
    return cfg.outputStart + cfg.sign * delta * (cfg.scale ?? 1);
  }
  return cfg.sign * imuValue;
}

// Visible on-screen debug overlay (enable with ?debug=1).
// Shows the latest raw IMU input and the resulting rotZ actually being applied.
const isDebugOverlayEnabled = new URLSearchParams(window.location.search).get("debug") === "1";
const debugOverlay = (() => {
  if (!isDebugOverlayEnabled) return { update() {}, setSource() {} };
  const el = document.createElement("div");
  el.style.cssText = [
    "position:fixed", "top:8px", "left:8px", "z-index:9999",
    "padding:8px 10px", "background:rgba(0,0,0,0.72)", "color:#fff",
    "font:12px/1.4 ui-monospace,Menlo,monospace", "border-radius:6px",
    "pointer-events:none", "white-space:pre", "max-width:50vw"
  ].join(";");
  el.textContent = "waiting for data...";
  document.body.appendChild(el);
  let lastSource = "-";
  return {
    setSource(src) { lastSource = src; },
    update(imu) {
      const fmt = (n) => (typeof n === "number" ? n.toFixed(2) : "-");
      const rot = state?.joints || {};
      const imuLine = imu
        ? `IMU in   boom=${fmt(imu.boom)}  stick=${fmt(imu.stick)}  bucket=${fmt(imu.bucket)}`
        : `IMU in   (n/a)`;
      el.textContent =
        `source: ${lastSource}\n` +
        `${imuLine}\n` +
        `rotZ     boom=${fmt(rot.boom?.z)}  stick=${fmt(rot.stick?.z)}  bucket=${fmt(rot.bucket?.z)}`;
    }
  };
})();

function findByNameCaseInsensitive(root, name) {
  const nameLower = name.toLowerCase();
  let exact = null;
  let includes = null;

  root.traverse((obj) => {
    if (!obj.name) return;
    const objName = obj.name.toLowerCase();
    if (!exact && objName === nameLower) exact = obj;
    if (!includes && objName.includes(nameLower)) includes = obj;
  });

  return exact || includes;
}

function enableExcavatorCastShadows(root) {
  if (!root) return;
  root.traverse((obj) => {
    if (obj.isMesh) obj.castShadow = true;
  });
}

function addOrUpdateEdgeOutline(mesh, colorHex, opacity = 0.75) {
  if (!mesh.geometry) return;

  let outline = mesh.children.find((child) => child.userData?.[EDGE_OUTLINE_KEY]);
  if (!outline) {
    outline = new THREE.LineSegments(
      new THREE.EdgesGeometry(mesh.geometry, 36),
      new THREE.LineBasicMaterial()
    );
    outline.userData[EDGE_OUTLINE_KEY] = true;
    outline.renderOrder = 2;
    mesh.add(outline);
  }

  outline.material.color = new THREE.Color(colorHex);
  outline.material.transparent = opacity < 1;
  outline.material.opacity = opacity;
  outline.material.depthTest = true;
}

function addOrUpdateSilhouetteOutline(mesh, colorHex, opacity = 0.54, scale = 1.026) {
  if (!mesh.geometry) return;

  let outline = mesh.children.find((child) => child.userData?.[SILHOUETTE_OUTLINE_KEY]);
  if (!outline) {
    outline = new THREE.Mesh(
      mesh.geometry,
      new THREE.MeshBasicMaterial({
        side: THREE.BackSide,
        depthWrite: false
      })
    );
    outline.userData[SILHOUETTE_OUTLINE_KEY] = true;
    outline.castShadow = false;
    outline.receiveShadow = false;
    outline.renderOrder = 1;
    mesh.add(outline);
  }

  outline.scale.setScalar(scale);
  outline.material.color = new THREE.Color(colorHex);
  outline.material.transparent = opacity < 1;
  outline.material.opacity = opacity;
}

function tintMeshes(
  target,
  colorHex,
  metalness = 0.35,
  roughness = 0.7,
  opacity = 1,
  edgeColorHex = null,
  edgeOpacity = 0.75,
  silhouetteColorHex = partColors.silhouette,
  silhouetteOpacity = 0.48,
  silhouetteScale = 1.026
) {
  if (!target) return;
  target.traverse((obj) => {
    if (obj.userData?.[SILHOUETTE_OUTLINE_KEY]) return;
    if (!obj.isMesh) return;
    const nextMat = new THREE.MeshPhysicalMaterial({
      color: colorHex,
      metalness,
      roughness,
      transparent: opacity < 1,
      opacity,
      clearcoat: opacity < 1 ? 0.75 : 0.45,
      clearcoatRoughness: opacity < 1 ? 0.2 : 0.26,
      envMapIntensity: opacity < 1 ? 1.65 : 1.15,
      transmission: opacity < 1 ? 0.06 : 0,
      thickness: opacity < 1 ? 0.28 : 0,
      ior: 1.42,
      flatShading: false
    });
    nextMat.depthWrite = opacity >= 1;
    obj.material = nextMat;
    if (edgeColorHex !== null) addOrUpdateEdgeOutline(obj, edgeColorHex, edgeOpacity);
    if (silhouetteColorHex !== null) {
      addOrUpdateSilhouetteOutline(obj, silhouetteColorHex, silhouetteOpacity, silhouetteScale);
    }
  });
}

function applyExcavatorColors() {
  // Default the whole excavator to dark translucent gray, then restore
  // boom/stick/bucket as solid blue so the arm stays highlighted.
  tintMeshes(nodes.main, partColors.transparentBody, 0.72, 0.18, 0.72, partColors.transparentBodyEdge, 0.22, partColors.silhouette, 0.34, 1.014);
  const armMat = [partColors.theme, 0.34, 0.2, 0.9, partColors.themeEdge, 0.16, partColors.themeSilhouette, 0.16, 1.008];
  tintMeshes(nodes.boom, ...armMat);
  tintMeshes(nodes.stick, ...armMat);
  tintMeshes(nodes.bucket, ...armMat);
  tintMeshes(nodes.diggingBucket, ...armMat);
}

function applyStateToModel() {
  if (!nodes.main) return;

  nodes.main.rotation.set(
    degToRad(state.main.pitch),
    degToRad(state.main.yaw),
    degToRad(state.main.roll)
  );

  ["base", "boom", "stick", "bucket"].forEach((jointName) => {
    const node = nodes[jointName];
    if (!node) return;
    const joint = state.joints[jointName];
    node.rotation.set(degToRad(joint.x), degToRad(joint.y), degToRad(joint.z));
  });

  ["boom", "stick"].forEach((jointName) => {
    const node = nodes[jointName];
    if (!node) return;
    const base = baseScale[jointName];
    const axis = lengthAxis[jointName];
    const lengthScale = Math.max(0.1, Number(state.lengths[jointName]) || 1);

    node.scale.set(base.x, base.y, base.z);
    if (axis === "x") node.scale.x = base.x * lengthScale;
    if (axis === "y") node.scale.y = base.y * lengthScale;
    if (axis === "z") node.scale.z = base.z * lengthScale;
  });
}

const loader = new GLTFLoader();
loader.load(
  "./model/excavator.glb",
  (gltf) => {
    const root = gltf.scene;
    scene.add(root);
    enableExcavatorCastShadows(root);

    nodes.main = findByNameCaseInsensitive(root, "main") || root;
    nodes.car = findByNameCaseInsensitive(root, "car");
    nodes.armature = findByNameCaseInsensitive(nodes.main, "armature");
    nodes.base = findByNameCaseInsensitive(root, "base");
    nodes.boom = findByNameCaseInsensitive(root, "boom");
    nodes.stick = findByNameCaseInsensitive(root, "stick");
    nodes.bucket = findByNameCaseInsensitive(root, "bucket");
    nodes.driverCabin = findByNameCaseInsensitive(root, "driver-cabin") || findByNameCaseInsensitive(root, "cabin");
    nodes.track = findByNameCaseInsensitive(root, "track");
    nodes.diggingBucket =
      findByNameCaseInsensitive(root, "digging-bucket") || findByNameCaseInsensitive(root, "front-bucket");

    console.log("Excavator nodes:", {
      main: nodes.main,
      car: nodes.car,
      armature: nodes.armature,
      base: nodes.base,
      boom: nodes.boom,
      stick: nodes.stick,
      bucket: nodes.bucket,
      driverCabin: nodes.driverCabin,
      track: nodes.track,
      diggingBucket: nodes.diggingBucket
    });

    if (nodes.boom) baseScale.boom.copy(nodes.boom.scale);
    if (nodes.stick) baseScale.stick.copy(nodes.stick.scale);
    applyExcavatorColors();

    const box = new THREE.Box3().setFromObject(root);
    const center = box.getCenter(new THREE.Vector3());
    const size = box.getSize(new THREE.Vector3());
    contactShadow.position.x = center.x;
    contactShadow.position.z = center.z;
    contactShadow.scale.set(Math.max(size.x * 1.35, 18), Math.max(size.z * 1.35, 18), 1);
    // Fit shadow frustum to model size so the shadow-map boundary stays offscreen.
    shadowExtent = Math.max(60, Math.max(size.x, size.z) * 3.0);
    sun.shadow.camera.left = -shadowExtent;
    sun.shadow.camera.right = shadowExtent;
    sun.shadow.camera.top = shadowExtent;
    sun.shadow.camera.bottom = -shadowExtent;
    sun.shadow.camera.far = Math.max(140, size.y * 12);
    sun.shadow.camera.updateProjectionMatrix();
    sun.shadow.needsUpdate = true;
    sun.target.position.copy(center);
    // Sun sits above the cab / upper body so light and shadow read as “overhead”.
    const sunOverhead = new THREE.Vector3(1.2, 14, 1.5);
    sun.position.copy(center).add(sunOverhead);
    if (controls) controls.target.copy(center);
    if (isDevPanelEnabled) camera.lookAt(center);

    applyStateToModel();
  },
  undefined,
  (error) => {
    console.error("Failed to load ./model/excavator.glb", error);
  }
);

const guiControllers = [];

function trackController(controller) {
  if (!controller) return null;
  guiControllers.push(controller);
  return controller;
}

function refreshGui() {
  guiControllers.forEach((controller) => controller.updateDisplay());
}

if (isDevPanelEnabled) {
  const gui = new GUI({ title: "Excavator Dev Panel" });
  const mainFolder = gui.addFolder("main (cab sway)");
  trackController(mainFolder.add(state.main, "roll", -45, 45, 0.1)).name("roll (left/right)").onChange(applyStateToModel);
  trackController(mainFolder.add(state.main, "pitch", -45, 45, 0.1)).name("pitch (front/back)").onChange(applyStateToModel);
  trackController(mainFolder.add(state.main, "yaw", -180, 180, 0.1)).name("yaw").onChange(applyStateToModel);

  const jointsFolder = gui.addFolder("armature joints");
  ["base", "boom", "stick", "bucket"].forEach((jointName) => {
    const folder = jointsFolder.addFolder(jointName);
    trackController(folder.add(state.joints[jointName], "x", -180, 180, 0.1)).name("rotX").onChange(applyStateToModel);
    trackController(folder.add(state.joints[jointName], "y", -180, 180, 0.1)).name("rotY").onChange(applyStateToModel);
    trackController(folder.add(state.joints[jointName], "z", -180, 180, 0.1)).name("rotZ").onChange(applyStateToModel);
  });

  const lengthsFolder = gui.addFolder("arm length scale (1 = 1m)");
  trackController(lengthsFolder.add(state.lengths, "boom", 0.5, 2.5, 0.01)).name("boom length").onChange(applyStateToModel);
  trackController(lengthsFolder.add(state.lengths, "stick", 0.5, 2.5, 0.01)).name("stick length").onChange(applyStateToModel);

  mainFolder.open();
  jointsFolder.open();
  lengthsFolder.open();
}

window.excavatorController = {
  state,
  apply: applyStateToModel,
  setMain(partialMain = {}) {
    Object.assign(state.main, partialMain);
    applyStateToModel();
    refreshGui();
  },
  setJoint(jointName, partialJoint = {}) {
    if (!state.joints[jointName]) return;
    // Arm joints (boom/stick/bucket) treat incoming .z as IMU reading,
    // so sign conversion is applied consistently with setImu / postMessage.
    const converted = { ...partialJoint };
    if ((jointName === "boom" || jointName === "stick" || jointName === "bucket") &&
        typeof converted.z === "number") {
      converted.z = imuToLocalAngle(jointName, converted.z);
    }
    Object.assign(state.joints[jointName], converted);
    debugOverlay.setSource(`setJoint(${jointName})`);
    debugOverlay.update();
    applyStateToModel();
    refreshGui();
  },
  setLengths(partialLengths = {}) {
    if (typeof partialLengths.boom === "number") state.lengths.boom = partialLengths.boom;
    if (typeof partialLengths.stick === "number") state.lengths.stick = partialLengths.stick;
    applyStateToModel();
    refreshGui();
  },
  setAll(nextState = {}) {
    if (nextState.main) Object.assign(state.main, nextState.main);
    if (nextState.lengths) {
      if (typeof nextState.lengths.boom === "number") state.lengths.boom = nextState.lengths.boom;
      if (typeof nextState.lengths.stick === "number") state.lengths.stick = nextState.lengths.stick;
    }
    if (nextState.joints) {
      Object.keys(nextState.joints).forEach((jointName) => {
        if (!state.joints[jointName]) return;
        const incoming = nextState.joints[jointName];
        const converted = { ...incoming };
        if ((jointName === "boom" || jointName === "stick" || jointName === "bucket") &&
            incoming && typeof incoming.z === "number") {
          converted.z = imuToLocalAngle(jointName, incoming.z);
        }
        Object.assign(state.joints[jointName], converted);
      });
    }
    debugOverlay.setSource("setAll");
    debugOverlay.update();
    applyStateToModel();
    refreshGui();
  },
  // Accept raw IMU readings (already local/relative angles per embedded protocol).
  // Usage: window.excavatorController.setImu({ boom: -60, stick: -175, bucket: 19.2 })
  setImu({ boom: imuBoom, stick: imuStick, bucket: imuBucket } = {}) {
    if (typeof imuBoom === "number")   state.joints.boom.z   = imuToLocalAngle("boom",   imuBoom);
    if (typeof imuStick === "number")  state.joints.stick.z  = imuToLocalAngle("stick",  imuStick);
    if (typeof imuBucket === "number") state.joints.bucket.z = imuToLocalAngle("bucket", imuBucket);
    debugOverlay.update({ boom: imuBoom, stick: imuStick, bucket: imuBucket });
    applyStateToModel();
    refreshGui();
  },
  // Runtime tuning, e.g.:
  //   setImuConfig({ boom: { sign: 1 } })
  //   setImuConfig({ stick: { inputStart: -171.66, outputStart: 76 } })
  setImuConfig(partial = {}) {
    Object.keys(partial).forEach((joint) => {
      if (!IMU_CONFIG[joint]) return;
      Object.assign(IMU_CONFIG[joint], partial[joint]);
    });
  }
};

function applyExternalPayload(payload) {
  if (!payload || typeof payload !== "object") return false;
  console.log(`[applyExternalPayload] received: ${JSON.stringify(payload, null, 2)}`);//"[applyExternalPayload] received:", payload

  // Explicit IMU payload: { imu: { boom, stick, bucket } }
  if (payload.imu) {
    debugOverlay.setSource("postMessage → imu");
    window.excavatorController.setImu(payload.imu);
    return true;
  }

  // Shorthand: top-level boom/stick/bucket are treated as IMU readings too.
  // Accepts both { boom: -60 } and { boom: { z: -60 } } styles.
  const hasShorthandArm =
    ("boom" in payload || "stick" in payload || "bucket" in payload) &&
    !("joints" in payload);
  if (hasShorthandArm) {
    const pickAngle = (v) => (typeof v === "number" ? v : v && typeof v.z === "number" ? v.z : undefined);
    debugOverlay.setSource("postMessage → shorthand");
    window.excavatorController.setImu({
      boom: pickAngle(payload.boom),
      stick: pickAngle(payload.stick),
      bucket: pickAngle(payload.bucket)
    });
    return true;
  }

  debugOverlay.setSource("postMessage → setAll");
  window.excavatorController.setAll(payload);
  return true;
}

// Android can call: window.applyExcavatorPayload({...}) directly.
window.applyExcavatorPayload = (payload) => applyExternalPayload(payload);

// Android can inject JS: window.dispatchEvent(new MessageEvent("message",{data: ...}))
window.addEventListener("message", (event) => {
  const data = event.data;
  if (typeof data === "string") {
    try {
      applyExternalPayload(JSON.parse(data));
    } catch {
      // Ignore non-JSON strings.
    }
    return;
  }
  applyExternalPayload(data);
});

// 可暂停的 animate loop。
// 原来无条件 requestAnimationFrame 会在 WebView visibility=GONE / Activity 不前台时仍以 60Hz 跑，
// 与 FPV 直播 TextureView 抢 GPU/合成预算，导致 RTSP 播放规律性掉帧（每隔几秒一次）。
// 这里支持：
//   1) Android 端调 window.__pauseAnim() / window.__resumeAnim() 显式控制
//   2) document.visibilitychange 自动联动（Android WebView.onPause 也会触发）
let __animRunning = false;
let __animPausedExternally = false;
let __lastRenderedAt = 0;
function __tick(ts) {
  if (!__animRunning) return;
  // 限频到 30fps：3D 姿态本身角度更新不超过 1Hz/UDP 频率，60fps 渲染只是空转浪费 GPU。
  if (ts - __lastRenderedAt < 33) {
    requestAnimationFrame(__tick);
    return;
  }
  __lastRenderedAt = ts;
  if (controls) controls.update();
  renderer.render(scene, camera);
  requestAnimationFrame(__tick);
}
function __startAnim() {
  if (__animRunning) return;
  __animRunning = true;
  __lastRenderedAt = 0;
  requestAnimationFrame(__tick);
}
function __stopAnim() {
  __animRunning = false;
}
window.__pauseAnim = function () {
  __animPausedExternally = true;
  __stopAnim();
};
window.__resumeAnim = function () {
  __animPausedExternally = false;
  if (!document.hidden) __startAnim();
};
document.addEventListener("visibilitychange", () => {
  if (document.hidden) {
    __stopAnim();
  } else if (!__animPausedExternally) {
    __startAnim();
  }
});
if (!document.hidden) __startAnim();

window.addEventListener("resize", () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});
