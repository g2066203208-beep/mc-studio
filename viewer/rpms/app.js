import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";

// RPMS-Q12: actual interactive 3D hierarchy, purely kinematic. NOT Sable physics.
const $ = (id) => document.getElementById(id);
const stage = $("stage"), canvas = $("scene");
const scene = new THREE.Scene();
scene.background = new THREE.Color("#102030");
scene.fog = new THREE.FogExp2("#102030", 0.027);
const camera = new THREE.PerspectiveCamera(40, 1, 0.08, 180);
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false, preserveDrawingBuffer: true, powerPreference: "high-performance" });
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.35;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.setClearColor("#102030");
scene.add(new THREE.HemisphereLight(0xcdeaff, 0x233144, 2.65));
const key = new THREE.DirectionalLight(0xffe1b7, 3.2);
key.position.set(-6, 12, 7); key.castShadow = true;
key.shadow.mapSize.set(2048, 2048);
key.shadow.camera.left = key.shadow.camera.bottom = -16;
key.shadow.camera.right = key.shadow.camera.top = 16;
key.shadow.camera.near = 1; key.shadow.camera.far = 45;
key.shadow.bias = -0.0008; scene.add(key);
const fill = new THREE.DirectionalLight(0x74cfff, 1.65);
fill.position.set(6, 6, -9); scene.add(fill);
const amb = new THREE.AmbientLight(0xffffff, .34); scene.add(amb);
const orbit = new OrbitControls(camera, canvas);
orbit.enableDamping = true;
orbit.dampingFactor = 0.075;
orbit.minDistance = 3.7;
orbit.maxDistance = 34;
orbit.maxPolarAngle = Math.PI * .91;
orbit.target.set(0, 1.85, 0);
camera.position.set(9, 6.9, 11); orbit.update();

const ground = new THREE.Group(); scene.add(ground);
const floorMat = new THREE.MeshStandardMaterial({ color: 0x142737, metalness: .15, roughness: .93 });
const groundPlane = new THREE.Mesh(new THREE.PlaneGeometry(140, 140), floorMat);
groundPlane.rotation.x = -Math.PI / 2; groundPlane.position.y = -.07;
groundPlane.receiveShadow = true; ground.add(groundPlane);
const grid = new THREE.GridHelper(90, 90, 0x416176, 0x264457);
grid.position.y = -.055; grid.material.transparent = true; grid.material.opacity = .52; ground.add(grid);
for (let i = -8; i <= 8; i += 4) {
  const m = new THREE.Mesh(new THREE.CylinderGeometry(.07, .07, .015, 12), new THREE.MeshBasicMaterial({color: 0x708ca1}));
  m.position.set(i, -.046, -9); ground.add(m);
}

const mats = {
  frame: new THREE.MeshStandardMaterial({ color: 0x273b50, metalness: .76, roughness: .4 }),
  panel: new THREE.MeshStandardMaterial({ color: 0x485d6b, metalness: .73, roughness: .38 }),
  panelDark: new THREE.MeshStandardMaterial({ color: 0x304350, metalness: .69, roughness: .48 }),
  core: new THREE.MeshStandardMaterial({ color: 0xe49a4b, metalness: .67, roughness: .3, emissive: 0x9d4d1c, emissiveIntensity: .18 }),
  bolt: new THREE.MeshStandardMaterial({ color: 0xcad6db, metalness: .84, roughness: .25 }),
  hydraulic: new THREE.MeshStandardMaterial({ color: 0x7892a0, metalness: .87, roughness: .2 }),
  glow: new THREE.MeshStandardMaterial({ color: 0x70d8c5, emissive: 0x27aca5, emissiveIntensity: .9, metalness: .38, roughness: .27 }),
  rubber: new THREE.MeshStandardMaterial({ color: 0x17222b, metalness: .04, roughness: .95 }),
  yellow: new THREE.MeshStandardMaterial({ color: 0xeeb870, metalness: .6, roughness: .38 }),
  red: new THREE.MeshStandardMaterial({ color: 0xd27270, emissive: 0x903a25, emissiveIntensity: .19, roughness: .5 })
};
const solidMeshes = [], selectableMeshes = [], axisMarkers = [], contactMarkers = [];
const unit = new THREE.Vector3();
function tagged(mesh, segmentId, jointId) {
  mesh.castShadow = true; mesh.receiveShadow = true;
  mesh.userData.segmentId = segmentId;
  if (jointId) mesh.userData.jointId = jointId;
  solidMeshes.push(mesh); selectableMeshes.push(mesh);
  return mesh;
}
function box(group, segmentId, w, h, d, x, y, z, mat, jointId = "") {
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
  mesh.position.set(x, y, z); group.add(tagged(mesh, segmentId, jointId)); return mesh;
}
function cyl(group, segmentId, rad, length, x, y, z, mat, axis = "y", jointId = "") {
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(rad, rad, length, 20), mat);
  if (axis === "x") mesh.rotation.z = Math.PI / 2;
  else if (axis === "z") mesh.rotation.x = Math.PI / 2;
  mesh.position.set(x, y, z); group.add(tagged(mesh, segmentId, jointId)); return mesh;
}
function rod(group, segmentId, from, to, radius, material) {
  const start = new THREE.Vector3(...from), end = new THREE.Vector3(...to);
  const vec = end.clone().sub(start);
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, vec.length(), 10), material);
  mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), vec.clone().normalize());
  mesh.position.copy(start).add(end).multiplyScalar(.5);
  group.add(tagged(mesh, segmentId)); return mesh;
}
function label(group, segmentId, message, width, height, x, y, z, rotationX = -Math.PI / 2) {
  const c = document.createElement("canvas"); c.width = 512; c.height = 128;
  const ctx = c.getContext("2d");
  ctx.clearRect(0, 0, c.width, c.height);
  ctx.fillStyle = "#182b3b"; ctx.fillRect(0, 0, c.width, c.height);
  ctx.strokeStyle = "#8ea6b3"; ctx.lineWidth = 3; ctx.strokeRect(5, 5, 502, 118);
  ctx.fillStyle = "#e5c38f"; ctx.textAlign = "center"; ctx.textBaseline = "middle";
  ctx.font = "bold 43px Consolas, monospace"; ctx.fillText(message, 256, 70);
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace;
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height),
    new THREE.MeshBasicMaterial({map: tex, transparent: true, side: THREE.DoubleSide}));
  mesh.rotation.x = rotationX;
  mesh.position.set(x, y, z); group.add(tagged(mesh, segmentId)); return mesh;
}
function axial(group, jointId, dir, length = .67) {
  const d = new THREE.Vector3(...dir).normalize();
  const arrow = new THREE.ArrowHelper(d, new THREE.Vector3(), length, 0xf9c06e, .14, .08);
  group.add(arrow); axisMarkers.push(arrow);
  const origin = new THREE.Mesh(new THREE.SphereGeometry(.046, 12, 8),
    new THREE.MeshBasicMaterial({color: 0xf9c06e}));
  group.add(origin); axisMarkers.push(origin);
  arrow.userData.jointId = jointId;
}
function signal(group, segmentId, x, y, z) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(.12, .095, .3), mats.glow);
  m.position.set(x, y, z); group.add(tagged(m, segmentId));
}
const root = new THREE.Group();
root.name = "BODY | Root Rigid Segment";
root.position.y = 2.87; scene.add(root);
root.userData.segmentId = "BODY";

function buildChassis() {
  box(root,"BODY",3.08,.48,5.55,0,.36,0,mats.frame);
  box(root,"BODY",3.65,.22,5.72,0,.78,-.02,mats.panelDark);
  box(root,"BODY",2.44,.19,4.56,0,.97,0,mats.panel);
  box(root,"BODY",2.6,.13,4.1,0,1.1,-.1,mats.frame);
  for (const side of [-1,1]) {
    box(root,"BODY",.18,.66,5.65,side*1.65,.36,0,mats.panel);
    box(root,"BODY",.26,.15,5.65,side*1.62,.77,0,mats.yellow);
    box(root,"BODY",.15,.19,4.3,side*1.3,1.18,-.15,mats.frame);
    for (const z of [-2.25,-1.4,-.55,.3,1.15,2.0]) {
      box(root,"BODY",.31,.07,.5,side*1.19,1.26,z,mats.bolt);
      box(root,"BODY",.26,.35,.14,side*1.62,.15,z,mats.panelDark);
    }
    for (const z of [-1.9,1.9]) {
      cyl(root,"BODY",.27,.54,side*1.84,.07,z,mats.frame,"x");
      cyl(root,"BODY",.16,.57,side*1.84,.07,z,mats.yellow,"x");
      cyl(root,"BODY",.065,.59,side*1.84,.07,z,mats.bolt,"x");
    }
    rod(root,"BODY",[side*1.05,.21,-2.8],[side*1.05,.9,2.6],.072,mats.hydraulic);
  }
  box(root,"BODY",1.8,.6,1.52,0,1.42,-.1,mats.frame);
  for (const x of [-.58, .58]) {
    cyl(root,"BODY",.23,.87,x,1.75,-.1,mats.yellow);
    cyl(root,"BODY",.15,.9,x,1.77,-.1,mats.rubber);
    signal(root,"BODY",x,1.94,.2);
  }
  box(root,"BODY",1.9,.18,1.82,0,1.91,-.1,mats.panelDark);
  label(root,"BODY","POWER / BUS",1.50,.34,0,2.011,-.1);
  box(root,"BODY",2.32,.57,.96,0,.63,2.76,mats.panelDark);
  box(root,"BODY",2.33,.2,.56,0,.95,2.89,mats.yellow);
  for (const x of [-.76, -.27, .27, .76]) signal(root,"BODY",x,.67,3.25);
  box(root,"BODY",1.88,.46,.65,0,.47,-3.18,mats.frame);
  for (const x of [-.58,.58]) {
    cyl(root,"BODY",.13,.58,x,.5,-3.49,mats.bolt,"z");
  }
  label(root,"BODY","RPMS / Q12",1.65,.39,0,1.211,1.47);
  box(root,"BODY",.86,.14,1.3,0,1.15,-2.05,mats.core);
  box(root,"BODY",.5,.17,.58,0,1.31,-2.12,mats.glow);
  for (const z of [-2.0,-1.2,-.3,.6,1.5,2.3]) {
    box(root,"BODY",2.64,.09,.12,0,.98,z,mats.bolt);
  }
}
buildChassis();

const rig = new Map(), axesByJoint = new Map();
const baseAngles = {}, actualAngles = {}, manualAngles = {}, jointDefs = new Map();
let config = null, mode = "walk", playing = true, elapsed = 0, rate = 1,
    selectedJoint = "LF.HFE", activeLeg = "LF", drawWire = false;
const UPPER = 1.42, LOWER = 1.37;
const legNames = { LF:"左前", RF:"右前", LR:"左后", RR:"右后" };
const legNamesFull = {LF:"LEFT FRONT",RF:"RIGHT FRONT",LR:"LEFT REAR",RR:"RIGHT REAR"};
const jointTypeNames = {HAA:"髋侧摆 · HIP ABDUCTION",HFE:"髋俯仰 · HIP FLEXION",KFE:"膝俯仰 · KNEE FLEXION"};

function createLeg(L) {
  const side = L.endsWith("F") ? (L.startsWith("L")?-1:1) : (L.startsWith("L")?-1:1);
  const z = L.endsWith("F") ? 2.00 : -2.00;
  const hip = new THREE.Group();
  hip.name = L+".HAA AXIS"; hip.position.set(side*1.79, .07, z); root.add(hip);
  const carrier = new THREE.Group(); carrier.userData.segmentId = L+"_HIP_CARRIER";
  hip.add(carrier);
  box(carrier,L+"_HIP_CARRIER",.52,.4,.85,side*.15,-.05,0,mats.panel);
  box(carrier,L+"_HIP_CARRIER",.54,.12,.67,side*.16,.17,0,mats.core);
  cyl(carrier,L+"_HIP_CARRIER",.24,.51,.0,0,0,mats.yellow,"z",L+".HAA");
  cyl(carrier,L+"_HIP_CARRIER",.12,.54,.0,0,0,mats.bolt,"z",L+".HAA");
  axial(hip,L+".HAA",[0,0,1]);
  const hipPitch = new THREE.Group(); hipPitch.name = L+".HFE AXIS";
  hipPitch.position.set(side*.24,-.19,0); carrier.add(hipPitch);
  cyl(hipPitch,L+"_UPPER",.26,.59,0,0,0,mats.yellow,"x",L+".HFE");
  cyl(hipPitch,L+"_UPPER",.115,.63,0,0,0,mats.bolt,"x",L+".HFE");
  axial(hipPitch,L+".HFE",[1,0,0]);
  const upper = new THREE.Group(); upper.userData.segmentId = L+"_UPPER"; hipPitch.add(upper);
  box(upper,L+"_UPPER",.35,UPPER*.89,.53,0,-UPPER*.51,0,mats.panel);
  box(upper,L+"_UPPER",.43,UPPER*.55,.66,0,-UPPER*.49,0,mats.frame);
  box(upper,L+"_UPPER",.24,.61,.71,0,-UPPER*.45,.07,mats.yellow);
  rod(upper,L+"_UPPER",[-.1,-.34,-.31],[-.1,-UPPER+.25,-.31],.07,mats.hydraulic);
  rod(upper,L+"_UPPER",[.1,-.20,.34],[.1,-UPPER+.2,.34],.06,mats.bolt);
  box(upper,L+"_UPPER",.5,.19,.71,0,-UPPER+.21,0,mats.panelDark);
  signal(upper,L+"_UPPER",.02,-.45,.4);
  const knee = new THREE.Group(); knee.name = L+".KFE AXIS"; knee.position.y = -UPPER; upper.add(knee);
  cyl(knee,L+"_LOWER_FOOT",.31,.67,0,0,0,mats.yellow,"x",L+".KFE");
  cyl(knee,L+"_LOWER_FOOT",.145,.72,0,0,0,mats.bolt,"x",L+".KFE");
  axial(knee,L+".KFE",[1,0,0]);
  const lower = new THREE.Group(); lower.userData.segmentId = L+"_LOWER_FOOT"; knee.add(lower);
  box(lower,L+"_LOWER_FOOT",.30,LOWER*.88,.43,0,-LOWER*.53,0,mats.panel);
  box(lower,L+"_LOWER_FOOT",.44,LOWER*.48,.5,0,-LOWER*.59,.03,mats.frame);
  rod(lower,L+"_LOWER_FOOT",[-.11,-.24,-.26],[-.11,-LOWER+.2,-.26],.075,mats.hydraulic);
  box(lower,L+"_LOWER_FOOT",.65,.28,.78,0,-LOWER+.1,.09,mats.panelDark);
  const foot = new THREE.Group();foot.position.set(0,-LOWER-.04,.18);lower.add(foot);
  box(foot,L+"_LOWER_FOOT",1,.19,1.12,0,-.08,.07,mats.rubber);
  box(foot,L+"_LOWER_FOOT",.9,.18,1,0,.075,.07,mats.panelDark);
  for (const x of [-.29,.29]) for (const zz of [-.26,.32]) {
    box(foot,L+"_LOWER_FOOT",.28,.07,.27,x,-.19,zz,mats.rubber);
  }
  for (const x of [-.28,.28]) {
    box(foot,L+"_LOWER_FOOT",.17,.065,.18,x,.20,.41,mats.glow);
    cyl(foot,L+"_LOWER_FOOT",.056,.16,x,.21,-.38,mats.bolt);
  }
  const indicator = new THREE.Mesh(new THREE.RingGeometry(.28,.35,24),new THREE.MeshBasicMaterial({color:0x64dabd,side:THREE.DoubleSide,transparent:true,opacity:.8,depthWrite:false}));
  indicator.rotation.x = -Math.PI/2;indicator.position.set(0,-.27,.07);foot.add(indicator);contactMarkers.push(indicator);
  rig.set(L,{side, hip, carrier, hipPitch,upper,knee,lower,foot,indicator});
  baseAngles[L+".HAA"] = side*7;
  baseAngles[L+".HFE"] = L.endsWith("F")?-14:-18;
  baseAngles[L+".KFE"] = L.endsWith("F")?31:35;
  for(const joint of ["HAA","HFE","KFE"]){
    axesByJoint.set(L+"."+joint, joint==="HAA"?hip:joint==="HFE"?hipPitch:knee);
  }
}
for(const L of ["LF","RF","LR","RR"])createLeg(L);
Object.assign(manualAngles,baseAngles);
Object.assign(actualAngles,baseAngles);

const highlight = new THREE.Mesh(new THREE.TorusGeometry(.385,.024,8,44),
  new THREE.MeshBasicMaterial({color:0xffda92,transparent:true,opacity:.95,depthTest:false}));
highlight.renderOrder = 99;
const highlightGlow = new THREE.PointLight(0xeeb870,.9,2); highlight.add(highlightGlow);
function refreshHighlight() {
  highlight.parent?.remove(highlight);
  const axis = axesByJoint.get(selectedJoint); if(!axis)return;
  axis.add(highlight);
  const type=selectedJoint.split(".")[1];
  highlight.rotation.set(type==="HAA"?0:0,type==="HAA"?0:Math.PI/2,0);
}

function clamp(value,min,max){return Math.max(min,Math.min(max,value));}
function frac(x){return x-Math.floor(x);}
function wavePose(L,t) {
  const offsets={LF:0,RF:.5,LR:.75,RR:.25};
  const p=frac(t*.47+offsets[L]);
  const swing=p>=.75, u=(p-.75)/.25;
  const stride=swing?(-9+18*u):(9-18*p/.75);
  const tuck=swing?33*Math.sin(Math.PI*clamp(u,0,1)):2*Math.sin(p/.75*Math.PI);
  return {HAA:baseAngles[L+".HAA"]+(swing?1.8*Math.sin(Math.PI*u):0),
    HFE:baseAngles[L+".HFE"]+stride,
    KFE:baseAngles[L+".KFE"]+tuck};
}
function singlePose(L,t) {
  const p=t*.42*Math.PI*2;
  const positive=Math.max(0,Math.sin(p));
  return {HAA:baseAngles[L+".HAA"]+4*Math.sin(p),
    HFE:baseAngles[L+".HFE"]-12*Math.cos(p),
    KFE:baseAngles[L+".KFE"]+26*positive};
}
function assignPoseAngles() {
  for(const [id,def] of jointDefs){
    const L=id.slice(0,2), t=id.split(".")[1];
    let a=baseAngles[id];
    if(mode==="walk")a=wavePose(L,elapsed)[t];
    else if(mode==="single" && L===activeLeg)a=singlePose(L,elapsed)[t];
    else if(mode==="manual")a=manualAngles[id];
    actualAngles[id]=clamp(a,def.minDeg,def.maxDeg);
    const pivot=t==="HAA"?rig.get(L).hip:t==="HFE"?rig.get(L).hipPitch:rig.get(L).knee;
    if(t==="HAA")pivot.rotation.z=THREE.MathUtils.degToRad(actualAngles[id]);
    else pivot.rotation.x=THREE.MathUtils.degToRad(actualAngles[id]);
  }
  root.position.y=2.87+(mode==="walk"?.035*Math.sin(elapsed*3):0);
  root.rotation.set(mode==="walk"?.006*Math.sin(elapsed*2.8):0,
    mode==="walk"?.01*Math.sin(elapsed*.8):0,
    mode==="walk"?.005*Math.sin(elapsed*3.1):0);
}
function setExplode(on){
  for(const L of ["LF","RF","LR","RR"]) {
    const l=rig.get(L);
    l.carrier.position.x=on?l.side*.32:0;
    l.upper.position.y=on?-.28:0;
    l.lower.position.y=on?-.25:0;
  }
  $("runtimeStatus").textContent=on?"装配分离视图 / 非物理分离":"运动学模型 / 非物理模拟";
}
function setupSelection() {
  const list=$("jointList");list.textContent="";
  const labels={HAA:"HAA",HFE:"HFE",KFE:"KFE"};
  const head=document.createElement("span");head.className="joint-head";head.textContent="AXIS";list.append(head);
  for(const L of ["LF","RF","LR","RR"]){
    const span=document.createElement("span");span.className="joint-head";span.textContent=L;list.append(span);
  }
  for(const t of ["HAA","HFE","KFE"]){
    const lab=document.createElement("span");lab.className="joint-head";lab.textContent=labels[t];list.append(lab);
    for(const L of ["LF","RF","LR","RR"]){
      const button=document.createElement("button");button.type="button";
      button.dataset.joint=L+"."+t;button.className="joint-cell";button.textContent=t==="HAA"?"↔":t==="HFE"?"↗":"⌁";
      button.setAttribute("aria-label",L+"."+t+" 关节");button.title=L+"."+t;
      button.addEventListener("click",()=>selectJoint(L+"."+t));list.append(button);
    }
  }
}
function selectJoint(id) {
  const def=jointDefs.get(id);if(!def)return;
  selectedJoint=id;
  const [L,t]=id.split(".");
  $("selectedLeg").textContent=legNamesFull[L]+" / "+L;
  $("selectedJoint").textContent=id;
  $("selectedDescription").textContent=jointTypeNames[t];
  $("minAngle").textContent=(def.minDeg>0?"+":"")+def.minDeg+"° MIN";
  $("maxAngle").textContent=(def.maxDeg>=0?"+":"")+def.maxDeg+"° MAX";
  $("partNames").textContent=def.parentSegment+" → "+def.childSegment;
  $("axisName").textContent=def.axisParentLocal[0]?"X / +1":def.axisParentLocal[2]?"Z / +1":"Y / +1";
  const slider=$("jointSlider");slider.min=def.minDeg;slider.max=def.maxDeg;slider.value=Math.round(actualAngles[id]??baseAngles[id]);
  document.querySelectorAll("[data-joint]").forEach(b=>b.classList.toggle("selected",b.dataset.joint===id));
  refreshHighlight(); updateJointReadout();
}
function updateJointReadout() {
  if(!selectedJoint || !jointDefs.has(selectedJoint))return;
  const j=jointDefs.get(selectedJoint);
  const a=actualAngles[selectedJoint]??baseAngles[selectedJoint]??0;
  const lim=clamp((a-j.minDeg)/(j.maxDeg-j.minDeg),0,1);
  $("angleFill").style.width=(lim*100).toFixed(2)+"%";
  $("actualAngle").textContent=(a>0?"+":"") + a.toFixed(1)+"°";
  if(document.activeElement!==$("jointSlider"))$("jointSlider").value=Math.round(a);
  $("targetAngle").textContent=(Number($("jointSlider").value)>0?"+":"")+$("jointSlider").value+"°";
}
function setMode(next) {
  if(next==="manual" && mode!=="manual") Object.assign(manualAngles,actualAngles);
  mode=next;
  if(next==="idle")elapsed=0;
  for(const b of document.querySelectorAll("[data-mode]")) b.classList.toggle("active",b.dataset.mode===next);
  $("kinematicState").textContent={idle:"静态关节",walk:"慢速步态",single:"单腿循环",manual:"手动限位"}[next];
  assignPoseAngles();updateJointReadout();
}
function setView(view) {
  const d=12;
  const positions={
    iso:[d*.7,6.5,d*.84],side:[d,4.1,0],front:[0,3.8,d],top:[0,15,.012]
  };
  if(!positions[view])return;
  camera.position.set(...positions[view]);
  orbit.target.set(0,1.9,0);orbit.update();
  document.querySelectorAll("[data-view]").forEach(b=>b.classList.toggle("active",b.dataset.view===view));
}
let hoverStart=null;
canvas.addEventListener("pointerdown",e=>{hoverStart={x:e.clientX,y:e.clientY};});
canvas.addEventListener("pointerup",e=>{
  if(!hoverStart||Math.hypot(e.clientX-hoverStart.x,e.clientY-hoverStart.y)>5)return;
  hoverStart=null;
  const bounds=canvas.getBoundingClientRect();
  const point=new THREE.Vector2((e.clientX-bounds.left)/bounds.width*2-1,
    -((e.clientY-bounds.top)/bounds.height*2-1));
  const ray=new THREE.Raycaster();ray.setFromCamera(point,camera);
  const hits=ray.intersectObjects(selectableMeshes,false);
  for(const h of hits) {
    const explicit=h.object.userData.jointId;
    if(explicit){selectJoint(explicit);return;}
    const s=h.object.userData.segmentId;
    if(!s||s==="BODY")continue;
    const p=s.split("_");
    const L=p[0], t=s.endsWith("HIP_CARRIER")?"HAA":s.endsWith("UPPER")?"HFE":"KFE";
    selectJoint(L+"."+t);return;
  }
});
$("legSelect").addEventListener("change",e=>{activeLeg=e.target.value;$("legSubLabel").textContent=activeLeg+" / "+legNamesFull[activeLeg];if(mode==="single")elapsed=0;selectJoint(activeLeg+".HFE");});
$("speed").addEventListener("input",e=>{rate=Number(e.target.value);$("speedValue").textContent=rate.toFixed(1)+"×";});
$("play").addEventListener("click",()=>{playing=!playing;$("play").textContent=playing?"Ⅱ　暂停运动":"▶　继续运动";});
$("neutral").addEventListener("click",()=>{Object.assign(manualAngles,baseAngles);elapsed=0;playing=true;$("play").textContent="Ⅱ　暂停运动";setMode("idle");});
$("jointSlider").addEventListener("input",e=>{
  if(mode!=="manual")setMode("manual");
  manualAngles[selectedJoint]=Number(e.target.value);
  assignPoseAngles();updateJointReadout();
});
$("explode").addEventListener("change",e=>setExplode(e.target.checked));
$("axes").addEventListener("change",e=>{axisMarkers.forEach(v=>v.visible=e.target.checked);});
$("contacts").addEventListener("change",e=>{contactMarkers.forEach(v=>v.visible=e.target.checked);});
$("ground").addEventListener("change",e=>{ground.visible=e.target.checked;});
$("wireframe").addEventListener("change",e=>{drawWire=e.target.checked;for(const v of solidMeshes){if(v.material && v.material.isMeshStandardMaterial){v.material.wireframe=drawWire;}}});
document.querySelectorAll("[data-view]").forEach(b=>b.addEventListener("click",()=>setView(b.dataset.view)));
document.querySelectorAll("[data-mode]").forEach(b=>b.addEventListener("click",()=>setMode(b.dataset.mode)));
$("capture").addEventListener("click",()=>{
  renderer.render(scene,camera);
  const a=document.createElement("a");a.download="RPMS-Q12-3D-engineering-demo.png";
  a.href=renderer.domElement.toDataURL("image/png");a.click();
});

function resize() {
  const w=Math.max(1,stage.clientWidth),h=Math.max(1,stage.clientHeight);
  renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();
}
new ResizeObserver(resize).observe(stage);
window.addEventListener("resize",resize);
document.addEventListener("visibilitychange",()=>{if(document.hidden)last=performance.now();});
let frames=0, lastSample=performance.now(), last=performance.now();
function animate(now) {
  const dt=Math.min((now-last)/1000,.08);last=now;
  if(playing && mode!=="manual" && mode!=="idle") elapsed+=dt*rate;
  assignPoseAngles();updateJointReadout();
  orbit.update();renderer.render(scene,camera);
  frames++;
  if(now-lastSample>900) {
    $("fps").textContent=Math.round(frames*1000/(now-lastSample))+" FPS";
    frames=0;lastSample=now;
  }
  requestAnimationFrame(animate);
}
async function initialize() {
  try {
    const response=await fetch("./quad12.design.json",{cache:"no-cache"});
    if(!response.ok) throw Error("设计文件 HTTP "+response.status);
    config=await response.json();
    const segs=config.segments||[],joints=config.joints||[],feet=config.feet||[];
    if(segs.length!==13||joints.length!==12||feet.length!==4)
      throw Error("预期 13 结构段/12 关节/4 脚掌，实际得到不一致配置");
    for(const j of joints){
      if(jointDefs.has(j.id)||!axesByJoint.has(j.id))throw Error("不可识别或重复关节 "+j.id);
      if(!Number.isFinite(j.minDeg)||!Number.isFinite(j.maxDeg)||j.minDeg>=j.maxDeg)
        throw Error("关节限位无效 "+j.id);
      jointDefs.set(j.id,j);
    }
    $("rigidCount").textContent=segs.length;
    $("jointCount").textContent=joints.length;
    $("footCount").textContent=String(feet.length).padStart(2,"0");
    setupSelection();setMode("walk");selectJoint("LF.HFE");
    resize();
    $("loadscreen").hidden=true;
    $("runtimeStatus").textContent="运动学预览 / 非 Sable 物理";
    window.__RPMS_READY__=true;
    window.__rpmsApi={getMode:()=>mode,getActualAngle:(id)=>actualAngles[id],getJointIds:()=>[...jointDefs.keys()],getSelected:()=>selectedJoint,getSegmentCount:()=>config.segments.length};
    requestAnimationFrame(animate);
  } catch(e) {
    console.error("RPMS viewer error:",e);
    $("loadingTitle").textContent="初始化失败："+e.message;
    $("runtimeStatus").textContent="模型加载失败";
    window.__RPMS_ERROR__=String(e);
  }
}
initialize();
