import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import RAPIER from '@dimforge/rapier3d-compat';
import {buildBossPhysics,DT,SUPPORT_POSITIONS} from './boss-physics.mjs';

const $=id=>document.getElementById(id);
let engine=null,renderPieces=[],rigVisual=null,paused=false,ready=false;
let last=0,accumulator=0;
const scene=new THREE.Scene();
scene.background=new THREE.Color('#182936');
scene.fog=new THREE.Fog('#182936',24,54);
const camera=new THREE.PerspectiveCamera(43,1,.08,100);
camera.position.set(9.7,8.4,12.8);
const renderer=new THREE.WebGLRenderer({canvas:$('bossCanvas'),antialias:true});
renderer.setPixelRatio(Math.min(2,window.devicePixelRatio||1));
renderer.outputColorSpace=THREE.SRGBColorSpace;
renderer.shadowMap.enabled=true;
renderer.shadowMap.type=THREE.PCFSoftShadowMap;
const orbit=new OrbitControls(camera,renderer.domElement);
orbit.enableDamping=true;orbit.dampingFactor=.11;
orbit.target.set(0,2.2,0);orbit.minDistance=4;orbit.maxDistance=33;
orbit.maxPolarAngle=Math.PI/2.1;
scene.add(new THREE.HemisphereLight('#eaf7ff','#596778',2.2));
const key=new THREE.DirectionalLight('#ffe8c3',3.0);
key.position.set(8,16,11);key.castShadow=true;
key.shadow.mapSize.set(2048,2048);
key.shadow.camera.left=-14;key.shadow.camera.right=14;
key.shadow.camera.top=14;key.shadow.camera.bottom=-14;
key.shadow.bias=-0.0004;
scene.add(key);
const rim=new THREE.DirectionalLight('#82bfc0',1.3);rim.position.set(-7,7,-6);scene.add(rim);

function texture(name){
 const c=document.createElement('canvas');c.width=32;c.height=32;
 const x=c.getContext('2d'),colors={
  copper:['#a36f42','#c58c52','#dca566','#8c5d38'],
  iron:['#728b95','#98aab0','#bbc7c8','#4f6975'],
  stone:['#4c555d','#667078','#808a8c','#38444a'],
  lamp:['#bf7443','#d29656','#f1ce88','#74533d']
 }[name];
 let seed=(name.charCodeAt(0)+32)*991;
 for(let iy=0;iy<16;iy++)for(let ix=0;ix<16;ix++){
  seed=(Math.imul(seed,1664525)+1013904223)|0;
  x.fillStyle=colors[(seed>>>30)%colors.length];
  x.fillRect(ix*2,iy*2,2,2);
 }
 x.fillStyle='#3d4343';
 x.fillRect(0,0,32,2);x.fillRect(0,30,32,2);
 x.fillRect(0,0,2,32);x.fillRect(30,0,2,32);
 x.fillStyle='#e9d1a1';
 for(const [px,py]of [[4,4],[26,4],[4,26],[26,26]])x.fillRect(px,py,3,3);
 if(name==='lamp'){x.fillStyle='#ecd5a0';x.fillRect(9,9,14,14);x.fillStyle='#9e714c';x.fillRect(12,12,8,8);}
 const t=new THREE.CanvasTexture(c);
 t.magFilter=THREE.NearestFilter;t.minFilter=THREE.NearestFilter;
 t.colorSpace=THREE.SRGBColorSpace;
 return t;
}
const textures=Object.fromEntries(['copper','iron','stone','lamp'].map(x=>[x,texture(x)]));
const materials=Object.fromEntries(Object.entries(textures).map(([id,map])=>[id,new THREE.MeshStandardMaterial({
 map,roughness:id==='iron'?.48:.83,metalness:id==='iron'?.66:id==='copper'?.23:.03
})]));
const blockGeom=new THREE.BoxGeometry(.995,.995,.995);
function meshBlock(piece){
 const mat=piece.name==='foot'?'iron':piece.name==='pillar'?'stone':
   piece.x===0&&piece.z===-1?'lamp':'copper';
 const m=new THREE.Mesh(blockGeom,materials[mat]);
 m.position.set(piece.x,piece.y,piece.z);m.castShadow=true;m.receiveShadow=true;
 m.userData.leg=piece.name!=='torso'?{x:piece.x,z:piece.z}:null;
 return m;
}
// Floor visual exactly matches static Rapier cuboid, top is y=0.
const floor=new THREE.Mesh(new THREE.BoxGeometry(40,1,40),
 new THREE.MeshStandardMaterial({color:'#354d50',roughness:.99}));
floor.position.y=-.5;floor.receiveShadow=true;scene.add(floor);
const grid=new THREE.GridHelper(40,40,0x57706e,0x3e5457);
grid.position.y=.006;scene.add(grid);
const floorCenter=new THREE.Group();scene.add(floorCenter);
const groundPatches=[];
for(let x=-6;x<7;x++)for(let z=-6;z<7;z++){
 if((x+z)%2===0){
 const patch=new THREE.Mesh(new THREE.PlaneGeometry(.98,.98),
  new THREE.MeshBasicMaterial({color:0x496661,transparent:true,opacity:.13,depthWrite:false,side:THREE.DoubleSide}));
 patch.rotation.x=-Math.PI/2;patch.position.set(x+.5,.009,z+.5);floorCenter.add(patch);groundPatches.push(patch);
 }
}
const footMarkers=new THREE.Group();scene.add(footMarkers);
for(const [x,z]of SUPPORT_POSITIONS){
 const p=new THREE.Mesh(new THREE.RingGeometry(.36,.46,24),
 new THREE.MeshBasicMaterial({color:0x7dd9ba,transparent:true,opacity:.38,side:THREE.DoubleSide}));
 p.rotation.x=-Math.PI/2;p.position.set(x,.016,z);footMarkers.add(p);
}
function initializeRig(){
 if(rigVisual){scene.remove(rigVisual);rigVisual.traverse(obj=>{if(obj.geometry&&obj.geometry!==blockGeom)obj.geometry.dispose();});}
 engine=buildBossPhysics(RAPIER);
 rigVisual=new THREE.Group();scene.add(rigVisual);
 renderPieces=engine.pieces.map(piece=>{
  const mesh=meshBlock(piece);rigVisual.add(mesh);
  return {piece,mesh};
 });
 paused=false;accumulator=0;last=0;
 $('pause').textContent='暂停物理';
 setMessage('已创建真实 Rapier 物理刚体和 17 个方块碰撞体，观察四脚落地。');
 updateRig();
}
function updateRig(){
 if(!engine)return;
 const {translation:t,rotation:r}=engine.pose();
 rigVisual.position.set(t.x,t.y,t.z);
 rigVisual.quaternion.set(r.x,r.y,r.z,r.w);
 for(const o of renderPieces)o.mesh.visible=!o.piece.removed;
 const s=engine.snapshot();
 $('bodyHeight').textContent=s.bodyBottom.toFixed(2)+' 格';
 $('feetCount').textContent=s.feet+' / 4';
 $('bodyMass').textContent=s.mass.toFixed(2);
 $('footGap').textContent=s.footGap.toFixed(3)+' 格';
 $('velocity').textContent=s.velocity.y.toFixed(3);
 $('steps').textContent=s.steps;
 $('worldState').textContent=(s.contactState==='standing'?'脚部实际接触地面':s.contactState==='falling'?'重力下落中':'真实刚体运动中')+
  ' · '+s.blocks+' 方块碰撞体';
}
function setMessage(t){$('statusMessage').textContent=t;}
function updateFootMarker(){
 if(!engine)return;
 // Foot markers remain on the floor as guide lines; removed soles disappear.
 for(const [i,el]of footMarkers.children.entries()){
  const [x,z]=SUPPORT_POSITIONS[i];
  el.material.opacity=engine.pieces.some(p=>p.name==='foot'&&p.x===x&&p.z===z&&!p.removed)?.38:.06;
 }
}
function removeLeg(x,z){
 if(!engine)return;
 const ok=engine.removeLeg(x,z);if(!ok)return;
 updateFootMarker();
 setMessage('已删除 ('+x+','+z+') 支撑腿的两个真实碰撞体。剩余 '+engine.snapshot().feet+' 条支撑腿，重力仍然生效。');
 updateRig();
}
$('removeOne').addEventListener('click',()=>{
 const p=SUPPORT_POSITIONS.find(([x,z])=>engine?.pieces.some(q=>q.name==='foot'&&q.x===x&&q.z===z&&!q.removed));
 if(p)removeLeg(...p);else setMessage('全部腿已被拆除；点击“重新生成”恢复。');
});
$('removeAll').addEventListener('click',()=>{
 if(!engine)return;const n=engine.removeAllFeet();updateFootMarker();updateRig();
 setMessage(n?'已真实删除 '+n+' 条腿的 8 个碰撞体！机身失去支撑，将由 Rapier 重力求解下坠。':'没有剩余支撑腿。');
});
$('push').addEventListener('click',()=>{
 if(!engine)return;engine.impulse(0,0,35,true);
 setMessage('已在机身上施加真实侧向冲量：翻滚、碰撞和摩擦都由物理引擎计算。');
});
$('restart').addEventListener('click',()=>{initializeRig();updateFootMarker();});
$('pause').addEventListener('click',()=>{
 if(!engine)return;paused=!paused;
 $('pause').textContent=paused?'继续物理':'暂停物理';
 setMessage(paused?'已暂停物理世界（不是把模型位置锁定）':'已恢复真实物理积分。');
});
$('step').addEventListener('click',()=>{
 if(!engine)return;paused=true;$('pause').textContent='继续物理';
 engine.advance(1);updateRig();
 setMessage('已经让 Rapier 求解器前进一步（1/60 秒）。');
});
const ray=new THREE.Raycaster(),pointer=new THREE.Vector2();
let pointerDown=null;
renderer.domElement.addEventListener('pointerdown',e=>{pointerDown={x:e.clientX,y:e.clientY};});
renderer.domElement.addEventListener('pointerup',e=>{
 if(!pointerDown||Math.hypot(pointerDown.x-e.clientX,pointerDown.y-e.clientY)>7){pointerDown=null;return;}
 pointerDown=null;
 const b=renderer.domElement.getBoundingClientRect();
 pointer.set(((e.clientX-b.left)/b.width)*2-1,-((e.clientY-b.top)/b.height)*2+1);
 ray.setFromCamera(pointer,camera);
 const hits=ray.intersectObjects(renderPieces.filter(o=>!o.piece.removed).map(o=>o.mesh),false);
 const leg=hits.find(h=>h.object.userData.leg)?.object.userData.leg;
 if(leg)removeLeg(leg.x,leg.z);
});
function resize(){
 const el=$('canvasWrap'),w=el.clientWidth,h=el.clientHeight;
 if(!w||!h)return;
 if(renderer.domElement.width!==Math.round(w*renderer.getPixelRatio())||
    renderer.domElement.height!==Math.round(h*renderer.getPixelRatio())){
  renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();
 }
}
window.addEventListener('resize',resize);
let visibilityWasHidden=false;
document.addEventListener('visibilitychange',()=>{visibilityWasHidden=document.hidden;last=0;accumulator=0;});
function tick(now){
 requestAnimationFrame(tick);
 const d=last?Math.min(.05,Math.max(0,(now-last)/1000)):0;last=now;
 if(ready&&engine&&!paused&&!visibilityWasHidden){
  accumulator=Math.min(.15,accumulator+d);
  let frames=0;
  while(accumulator>=DT&&frames<5){
   engine.advance(1);accumulator-=DT;frames++;
  }
  if(frames)updateRig();
 }
 orbit.update();resize();renderer.render(scene,camera);
}
requestAnimationFrame(tick);
window.__BOSS_API__={
 get ready(){return ready},get engine(){return engine},get error(){return $('engineState').dataset.error||null},
 snapshot:()=>engine?.snapshot(),advance:n=>{if(!ready)throw Error('Physics engine not ready');const result=engine.advance(n);updateRig();return result;},
 removeAllFeet:()=>{const r=engine.removeAllFeet();updateFootMarker();updateRig();return r},
 removeLeg:(x,z)=>removeLeg(x,z),reset:()=>initializeRig(),
 pause:()=>{$('pause').click()},parts:()=>engine?.pieces.map(p=>({kind:p.name,pos:[p.x,p.y,p.z],removed:p.removed})),
 renderedMeshes:()=>rigVisual?.children.filter(c=>c.isMesh&&c.visible).length
};
try{
 await RAPIER.init();
 initializeRig();updateFootMarker();ready=true;
 $('engineState').textContent='Rapier WASM 已启动';
 $('liveBadge').textContent='● 实际求解中';
 $('liveBadge').classList.remove('error');
}catch(err){
 console.error('Boss WebAssembly physics initialization failed',err);
 $('engineState').textContent='真实物理引擎加载失败';
 $('engineState').dataset.error=String(err?.message||err);
 $('liveBadge').textContent='● 初始化失败';$('liveBadge').classList.add('error');
 setMessage('真正的 Rapier 物理引擎无法加载：'+String(err?.message||err)+'。请检查网络或 CDN；不会显示伪造的行走动画。');
}
