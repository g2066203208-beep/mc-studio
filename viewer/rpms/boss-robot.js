import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import RAPIER from '@dimforge/rapier3d-compat';
import {createMultibodyBoss,DT,LEG_COORDS} from './boss-multibody.mjs';
import {buildCreatePowerBus} from './boss-powertrain.mjs';
import {createPreviewModel} from './procedural-models.mjs';
import {MinecraftJarModels} from './authentic-models.mjs';
import {loadOfficialModelArchives} from './official-autoload.mjs';

const $=id=>document.getElementById(id);
const scene=new THREE.Scene();scene.background=new THREE.Color('#192935');scene.fog=new THREE.Fog('#192935',31,80);
const camera=new THREE.PerspectiveCamera(40,1,.05,140);
camera.position.set(7.9,6.5,9.0); // near enough to appreciate actual moving mechanical joints
const renderer=new THREE.WebGLRenderer({canvas:$('bossCanvas'),antialias:true,powerPreference:'high-performance'});
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,1.8));
renderer.outputColorSpace=THREE.SRGBColorSpace;
renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
const orbit=new OrbitControls(camera,renderer.domElement);
orbit.enableDamping=true;orbit.target.set(0,2.6,0);orbit.minDistance=5.5;orbit.maxDistance=52;
orbit.maxPolarAngle=Math.PI/2.06;
scene.add(new THREE.HemisphereLight('#edf6ff','#506570',2.25));
const sun=new THREE.DirectionalLight('#ffe6bd',3.1);sun.position.set(9,20,13);sun.castShadow=true;
sun.shadow.mapSize.set(1024,1024);
sun.shadow.camera.left=-18;sun.shadow.camera.right=18;
sun.shadow.camera.top=18;sun.shadow.camera.bottom=-18;sun.shadow.bias=-.0011;scene.add(sun);
const accent=new THREE.DirectionalLight('#80caca',1.35);accent.position.set(-9,9,-9);scene.add(accent);
const floor=new THREE.Mesh(new THREE.BoxGeometry(100,1,100),
 new THREE.MeshStandardMaterial({color:0x31464b,roughness:1}));
floor.position.y=-.5;floor.receiveShadow=true;scene.add(floor);
const grid=new THREE.GridHelper(100,100,0x5b7775,0x365056);grid.position.y=.014;scene.add(grid);
function pixMat(kind){
 const c=document.createElement('canvas');c.width=c.height=32;
 const x=c.getContext('2d');let seed=kind.charCodeAt(0)*911;
 const palettes={
  copper:['#785337','#b58252','#c9945b','#947049'],steel:['#435961','#6c8188','#90a2a7','#65777e'],
  brass:['#8b663c','#b68c4d','#e0b979','#8d7049'],dark:['#29393f','#3c4a4f','#54646b','#303b40'],
  teal:['#365b62','#527e85','#6b9da3','#3d6d71'],foot:['#556a73','#849197','#adb9b8','#697c84']
 };
 const col=palettes[kind];
 for(let j=0;j<16;j++)for(let i=0;i<16;i++){
  seed=(Math.imul(seed,1664525)+1013904223)|0;
  x.fillStyle=col[(seed>>>30)&3];x.fillRect(i*2,j*2,2,2);
 }
 x.strokeStyle='#152630';x.lineWidth=2;x.strokeRect(1,1,30,30);
 for(const [a,b]of [[4,4],[26,4],[4,26],[26,26]]){x.fillStyle='#d5b586';x.fillRect(a,b,2,2);}
 const map=new THREE.CanvasTexture(c);map.magFilter=map.minFilter=THREE.NearestFilter;map.colorSpace=THREE.SRGBColorSpace;
 return new THREE.MeshStandardMaterial({map,roughness:.64,metalness:kind==='brass'?.6:.25});
}
const mats=Object.fromEntries(['copper','steel','brass','dark','teal','foot'].map(k=>[k,pixMat(k)]));
const hullGeom=new THREE.BoxGeometry(1,1,1);
const modules=[],visualBodies=[],spinning=[];
let engine=null,root=null,ready=false,paused=false,accum=0,last=0,hidePhysics=false;
let blueprintEpoch=0,officialCount=0;
const jarModels=new MinecraftJarModels();
const assetCache=new Map();
function block(parent,shape,pos,scale,kind,legId=null){
 const mesh=new THREE.Mesh(hullGeom,mats[kind]);
 mesh.position.set(...pos);mesh.scale.set(...scale);
 mesh.castShadow=true;mesh.receiveShadow=true;
 if(legId)mesh.userData.legId=legId;
 parent.add(mesh);return mesh;
}
function officialModule(parent,id,pos,{scale=1,axis='x',facing='east',legId=null,rotor=false}={}){
 const frame=new THREE.Group();frame.position.set(...pos);frame.scale.setScalar(scale);parent.add(frame);
 const old=createPreviewModel({id,axis,facing});
 old.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;if(legId)o.userData.legId=legId;}});
 frame.add(old);
 modules.push({id,frame,old,axis,facing,legId});
 if(rotor)spinning.push({group:frame,axis});
 if(assetCache.has(id))swapOfficial(frame,id,legId);
 return frame;
}
function swapOfficial(frame,id,legId){
 const template=assetCache.get(id);if(!template)return false;
 frame.clear();
 const clone=template.clone(true);
 clone.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;if(legId)o.userData.legId=legId;}});
 frame.add(clone);return true;
}
const metalRing=new THREE.TorusGeometry(.46,.09,8,18);
const ringMat=new THREE.MeshStandardMaterial({color:0xcfa46b,metalness:.73,roughness:.31});
function ring(parent,pos,axis='x',legId){
 const mesh=new THREE.Mesh(metalRing,ringMat);
 mesh.position.set(...pos);if(axis==='x')mesh.rotation.y=Math.PI/2;
 if(axis==='y')mesh.rotation.x=Math.PI/2;
 mesh.castShadow=true;mesh.userData.legId=legId;parent.add(mesh);
 return mesh;
}
function chassisVisual(g){
 block(g,'box',[0,0,0],[5.6,1.15,5.3],'copper');
 block(g,'box',[0,.84,0],[2.3,.85,2.3],'dark');
 block(g,'box',[0,-.08,-3.0],[1.65,.65,1.1],'teal');
 // Exterior plate layering: industrial MC block silhouette.
 for(const z of [-2.1,-1.05,0,1.05,2.1])for(const x of [-2.35,-1.175,0,1.175,2.35]){
  block(g,'box',[x,.61,z],[1.07,.16,.99],Math.abs(x)===2.35?'steel':'copper');
 }
 for(const z of [-1.85,0,1.85]){
  block(g,'box',[0,1.3,z],[.86,.47,.88],'steel');
  officialModule(g,'create:large_cogwheel',[0,1.64,z],{axis:'y',scale:1.27,rotor:true});
  for(const sx of [-1,1]){
   officialModule(g,'create:cogwheel',[sx*1.20,1.25,z],{axis:'y',scale:.94,rotor:true});
   officialModule(g,'create:creative_motor',[sx*2.15,-.01,z],{axis:'x',facing:sx>0?'east':'west',scale:.83});
   officialModule(g,'simulated:swivel_bearing',[sx*2.9,-.38,z],{axis:'x',facing:sx>0?'east':'west',scale:1.0});
  }
 }
 // Drive shafts and transmission inspection lines are *visual*; the
 // actual reaction torque is applied to real Hip/Knee Rapier hinges.
 for(const z of [-1.85,0,1.85]){
  officialModule(g,'create:shaft',[0,.98,z],{axis:'x',scale:1.0,rotor:true});
  block(g,'box',[-2.7,.36,z],[.25,.31,.29],'brass');
  block(g,'box',[2.7,.36,z],[.25,.31,.29],'brass');
 }
 for(const x of [-2.28,2.28])for(const z of [-2.38,2.38])
  block(g,'box',[x,-.68,z],[.5,.19,.55],'foot');
}
function linkVisual(g,length,legId,segment){
 const isUpper=segment==='upper';
 block(g,'box',[0,0,0],[isUpper?.68:.53,length,isUpper?.72:.64],isUpper?'teal':'steel',legId);
 for(const y of [-length*.3,0,length*.3]){
  block(g,'box',[0,y,0],[.78,.21,.74],isUpper?'brass':'copper',legId);
 }
 for(const sign of [-1,1]){
  block(g,'box',[sign*.32,0,0],[.12,length*.90,.23],'dark',legId);
 }
 if(isUpper){
  officialModule(g,'simulated:swivel_bearing',[0,length/2,0],{axis:'x',facing:'east',scale:1.08,legId});
  ring(g,[0,-length/2,0],'x',legId);
  officialModule(g,'create:cogwheel',[.36,length*.18,.04],{axis:'x',scale:.68,legId});
 }else{
  officialModule(g,'simulated:swivel_bearing',[0,length/2,0],{axis:'x',facing:'east',scale:1.0,legId});
  ring(g,[0,length/2,0],'x',legId);
  block(g,'box',[0,-length/2-.17,0],[1.24,.38,1.18],'foot',legId);
  for(const sign of [-1,1])block(g,'box',[sign*.42,-length/2-.06,.05],[.16,.12,.85],'brass',legId);
 }
 g.traverse(obj=>{if(obj.isMesh)obj.userData.legId=legId;});
}
function syncVisual(){
 if(!engine)return;
 for(const {mesh,body} of visualBodies){
  const t=body.translation(),r=body.rotation();
  mesh.position.set(t.x,t.y,t.z);mesh.quaternion.set(r.x,r.y,r.z,r.w);
 }
 const s=engine.snapshot();
 const bus=s.powertrain;
 $('driveSource').textContent=bus.sourcePowered?'马达供电 '+bus.source.rpm+' RPM':'动力已断开';
 $('driveConnected').textContent=bus.connectedBranches+' / 6';
 $('driveLinks').textContent=bus.links+' 条';
 $('driveStress').textContent=Math.round(bus.capacitySU)+' / '+Math.round(bus.loadSU)+' SU*';
 $('legPowerStatus').replaceChildren(...bus.inputs.map(item=>{
  const chip=document.createElement('span');
  chip.textContent=item.id+' '+(item.connected?item.rpm.toFixed(1)+' RPM':'断电');
  chip.className=item.connected?'':'off';
  return chip;
 }));
 $('bodyHeight').textContent=s.bodyBottom.toFixed(2)+' 格';
 $('jointNum').textContent=s.rigidBodies+' / '+s.joints;
 $('feetCount').textContent=s.feet+' / 6';
 $('bodyMass').textContent=s.mass.toFixed(1);
 $('contactCount').textContent=s.supportContacts+' / 6';
 const knee=s.legAngles.find(x=>!x.detached);
 $('jointAngle').textContent=knee?(knee.hip*180/Math.PI).toFixed(1)+'°':'—';
 $('distance').textContent=s.travel.toFixed(2)+' 格';
 $('velocity').textContent=s.velocity.y.toFixed(2)+' m/s';
 $('steps').textContent=s.steps+' 个物理步长';
 $('worldState').textContent=s.mode.toUpperCase()+' · '+s.joints+' 铰链 · '+bus.connectedBranches+'/6 传动支路有动力 · '+s.supportContacts+' 脚近地';
}
function setMessage(message){$('statusMessage').textContent=message;}
function createRig(){
 blueprintEpoch++;
 if(root){scene.remove(root);root.clear();}
 modules.length=0;spinning.length=0;visualBodies.length=0;
 engine=createMultibodyBoss(RAPIER);
 root=new THREE.Group();scene.add(root);
 const chest=new THREE.Group();root.add(chest);
 chassisVisual(chest);visualBodies.push({body:engine.body,mesh:chest});
 for(const l of engine.motors){
  const thigh=new THREE.Group(),shin=new THREE.Group();root.add(thigh,shin);
  linkVisual(thigh,engine.upperLength,l.id,'upper');
  linkVisual(shin,engine.lowerLength,l.id,'lower');
  visualBodies.push({body:l.upper,mesh:thigh},{body:l.lower,mesh:shin});
 }
 engine.setMotor({rpm:Number($('motorRPM').value),torqueLimit:Number($('motorTorque').value),ratio:Number($('gearRatio').value)});
 engine.setMode('stand');
 paused=false;accum=0;last=0;
 $('pause').textContent='暂停物理';
 setModeButton('stand');
 syncVisual();applyAssetCache();
 setMessage('已装配大型 Create × Simulated 六足机：13 个真正的物理刚体、12 个物理旋转约束。现正由有限力矩支撑机身。');
}
function applyAssetCache(){
 let replaced=0;
 for(const m of modules)if(swapOfficial(m.frame,m.id,m.legId))replaced++;
 officialCount=replaced;
 $('modelSource').textContent=assetCache.size
  ?'官方 JAR 静态模型已加载：'+replaced+'/'+modules.length+' 个模块用官方 JSON/PNG 渲染；齿轮外观并不等同于完整 Create 原版动力网络。'
  :'正在显示原创 Create/Simulated 风格机械模块；官方版本资源能下载后自动替换为原版静态 JSON/PNG。';
}
async function autoloadAssets(){
 if(new URLSearchParams(location.search).get('autoload')==='off')return;
 if(!window.JSZip){$('modelSource').textContent='浏览器 JAR 解包工具不可用，使用清晰可见的机械齿轮与轴承几何预览。';return;}
 try{
  await loadOfficialModelArchives({
   onState:state=>{if(!assetCache.size)$('modelSource').textContent=state.message;},
   onFile:async file=>{
    await jarModels.addFile(file,window.JSZip);
    const idList=[...new Set(modules.map(x=>x.id))];
    for(const id of idList){
     const item=modules.find(m=>m.id===id);
     const result=await jarModels.render({id,axis:item.axis,facing:item.facing,pos:[0,0,0]});
     if(result.group&&result.status==='authentic')assetCache.set(id,result.group);
    }
    applyAssetCache();
   },
   onFailed:(_project,err)=>{console.warn('Original Mod textures unavailable, geometry remains visible:',err);}
  });
 }catch(err){console.warn('Boss official model loading failed:',err);}
}
function setModeButton(mode){
 for(const id of ['stand','march','walk','relax'])$(id).classList.toggle('active',id===mode);
}
for(const mode of ['stand','march','walk','relax'])$(mode).addEventListener('click',()=>{
 if(!engine)return;
 engine.setMode(mode);setModeButton(mode);
 setMessage({
  stand:'六足髋膝伺服器正在真实受力保持站立；腿承重不足时身体会塌。',
  march:'两组三角支撑腿交替抬腿：12 个真实物理关节驱动，关节角度由求解器更新。',
  walk:'前进步态实验：改变关节角度与足底摩擦产生实际运动，不保证未经调参就能稳定直线走。',
  relax:'已断电：不再向关节施加主动转矩，真实重力将使腿和身体失稳。'
 }[mode]);
});
$('motorRPM').addEventListener('input',e=>{
 const rpm=Number(e.target.value);$('rpmLabel').textContent=rpm+' RPM';engine?.setMotor({rpm});updateRatioLabel();
});
$('motorTorque').addEventListener('input',e=>{
 const torque=Number(e.target.value);$('torqueLabel').textContent=torque+' N·m';engine?.setMotor({torqueLimit:torque});
});
function updateRatioLabel(){
 const ratio=Number($('gearRatio').value);
 const rpm=Number($('motorRPM').value);
 $('ratioLabel').textContent=ratio+':1 · 输出 '+(rpm/ratio).toFixed(1)+' RPM';
}
$('gearRatio').addEventListener('change',e=>{
 engine?.setMotor({ratio:Number(e.target.value)});
 updateRatioLabel();syncVisual();
});


$('powerCut').addEventListener('click',()=>{
 if(!engine)return;
 engine.setMode('relax');setModeButton('relax');
 syncVisual();setMessage('动力源已断电：6 条 Create 传动支路失去 RPM，真实髋膝关节驱动转矩归零。');
});
$('powerRestore').addEventListener('click',()=>{
 if(!engine)return;
 engine.setMode('stand');setModeButton('stand');
 syncVisual();setMessage('动力源已重新接通：只对齿轮／离合器网络中仍然连通的关节供能。');
});
$('clutchCut').addEventListener('click',()=>{
 if(!engine)return;
 engine.disconnectBranch('L1',true);syncVisual();
 setMessage('左前 Create 离合器接通红石并断开机械轴：L1 关节驱动转矩真实归零。');
});
$('clutchRestore').addEventListener('click',()=>{
 if(!engine)return;
 engine.disconnectBranch('L1',false);syncVisual();
 setMessage('左前离合器解除红石：Create 动力网络恢复连接。');
});
function removeLeg(id){
 if(!engine)return false;
 const ok=engine.removeLeg(id);
 if(ok){setMessage('腿部 '+id+' 的髋、膝共 2 个真实物理铰链已断裂，上下腿继续作为单独受重力的刚体坠落。');syncVisual();}
 return ok;
}
$('removeOne').addEventListener('click',()=>{
 const target=engine?.motors.find(x=>!x.detached);if(target)removeLeg(target.id);
});
$('removeAll').addEventListener('click',()=>{
 if(!engine)return;
 for(const m of engine.motors)if(!m.detached)engine.removeLeg(m.id);
 syncVisual();setMessage('已切断全部 12 个物理旋转约束：机械腿变成受重力影响的独立碎块，机身不再由腿支撑。');
});
$('push').addEventListener('click',()=>{
 if(!engine)return;engine.impulse({x:0,y:0,z:55});
 setMessage('已向机身偏心位置施加真实碰撞冲量；姿态、关节反作用力、摩擦均交由 Rapier 求解。');
});
$('restart').addEventListener('click',()=>createRig());
$('pause').addEventListener('click',()=>{
 if(!engine)return;paused=!paused;$('pause').textContent=paused?'继续物理':'暂停物理';
 setMessage(paused?'物理求解暂停；所有部件保持上个求解器状态。':'物理求解恢复。');
});
$('step').addEventListener('click',()=>{
 if(!engine)return;paused=true;$('pause').textContent='继续物理';
 engine.advance(1);syncVisual();
 setMessage('已计算单个 Rapier 物理步长 1/60 秒，关节、重力与接触全部真实运算。');
});
const pickRay=new THREE.Raycaster(),mouse=new THREE.Vector2();let pointerDown=null;
renderer.domElement.addEventListener('pointerdown',e=>{pointerDown={x:e.clientX,y:e.clientY};});
renderer.domElement.addEventListener('pointerup',e=>{
 if(!engine||!pointerDown)return;
 const moved=Math.hypot(e.clientX-pointerDown.x,e.clientY-pointerDown.y);
 pointerDown=null;if(moved>7)return;
 const box=renderer.domElement.getBoundingClientRect();
 mouse.set((e.clientX-box.left)/box.width*2-1,-(e.clientY-box.top)/box.height*2+1);
 pickRay.setFromCamera(mouse,camera);
 const meshes=[];root.traverse(o=>{if(o.isMesh&&o.userData.legId)meshes.push(o);});
 const hit=pickRay.intersectObjects(meshes,false)[0];
 if(hit)removeLeg(hit.object.userData.legId);
});
function resize(){
 const el=$('canvasWrap'),w=el.clientWidth,h=el.clientHeight;
 if(w<5||h<5)return;
 renderer.setSize(w,h,false);camera.aspect=w/h;
 if(w<560&&camera.position.distanceTo(orbit.target)<13.2)camera.position.set(11.1,8.2,12.2);
 camera.updateProjectionMatrix();
}
window.addEventListener('resize',resize);const ro=new ResizeObserver(resize);ro.observe($('canvasWrap'));
document.addEventListener('visibilitychange',()=>{hidePhysics=document.hidden;accum=0;last=0;});
function animate(now=0){
 requestAnimationFrame(animate);
 const dt=last?Math.min(.05,Math.max(0,(now-last)/1000)):0;last=now;
 if(ready&&engine&&!paused&&!hidePhysics){
  accum=Math.min(.12,accum+dt);let cycles=0;
  while(accum>=DT&&cycles<5){engine.advance(1);accum-=DT;cycles++;}
  if(cycles)syncVisual();
 }
 if(engine){
  const operating=engine.snapshot();
  for(const {group,axis} of spinning){
   // Preview animation now uses the SAME energized Create connectivity as
   // the REAL Rapier joint torque controller. A broken source or clutch
   // cannot leave an unrelated decorative wheel spinning.
   const bus=operating.powertrain;
   const idx=Math.max(0,Math.min(2,Math.round((group.position.z+2)/2)));
   const branch=(group.position.x<0?'L':'R')+(idx+1);
   const output=bus.inputs.find(b=>b.id===branch);
   const commanded=group.position.x===0
    ?(bus.sourcePowered?bus.source.rpm:0)
    :(output?.connected?output.rpm:0);
   group.rotation[axis]=operating.steps*DT*commanded*2*Math.PI/60;
  }
 }
 orbit.update();
 renderer.render(scene,camera);
}
requestAnimationFrame(animate);
window.__BOSS_API__={
 get ready(){return ready},get engine(){return engine},get error(){return $('engineState').dataset.error||null},
 snapshot:()=>engine?.snapshot(),advance:n=>{const s=engine.advance(n);syncVisual();return s;},
 mode:m=>{engine.setMode(m);setModeButton(m)},
 motor:o=>{engine.setMotor(o);syncVisual();},
 disconnectBranch:(id,cut)=>{engine.disconnectBranch(id,cut);syncVisual();},
 getPowertrain:()=>engine?.snapshot().powertrain,
 removeLeg,removeAllFeet:()=>{$('removeAll').click()},
 reset:createRig,physicsBodies:()=>visualBodies.length,visibleMeshes:()=>{let n=0;root?.traverse(o=>{if(o.isMesh&&o.visible)n++});return n;},
 getAssetStatus:()=>({officialTypes:[...assetCache.keys()],officialMeshes:officialCount,components:modules.length}),
 render:syncVisual
};
try{
 await RAPIER.init();
 createRig();ready=true;
 $('engineState').textContent='Rapier 多体动力求解运行中';
 $('liveBadge').textContent='● 13 个刚体 · 12 个旋转铰链';
 resize();autoloadAssets();
}catch(err){
 console.error('Boss articulated physics initialization failed',err);
 $('engineState').dataset.error=String(err?.stack||err);
 $('engineState').textContent='物理关节初始化失败';
 $('liveBadge').textContent='● 物理错误';$('liveBadge').classList.add('error');
 setMessage('真实关节引擎初始化失败：'+err.message+'。不以普通动画假装成功。');
}
