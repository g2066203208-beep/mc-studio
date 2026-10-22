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
camera.position.set(9.4,7.9,11.7); // closer viewport without changing any Minecraft voxel dimensions
const renderer=new THREE.WebGLRenderer({canvas:$('bossCanvas'),antialias:true,powerPreference:'high-performance'});
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,1.8));
renderer.outputColorSpace=THREE.SRGBColorSpace;
renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
const orbit=new OrbitControls(camera,renderer.domElement);
orbit.enableDamping=true;orbit.target.set(0,3.25,0);orbit.minDistance=7;orbit.maxDistance=52;
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
// Official MC blockstate orientation is a part of the mesh identity.
const assetKey=(id,axis,facing)=>`${id}|axis=${axis||'x'}|facing=${facing||'east'}`;
function block(parent,shape,pos,scale,kind,legId=null){
 const mesh=new THREE.Mesh(hullGeom,mats[kind]);
 mesh.position.set(...pos);mesh.scale.set(...scale);
 mesh.castShadow=true;mesh.receiveShadow=true;
 if(legId)mesh.userData.legId=legId;
 mesh.userData.fullVoxel=true;
 parent.add(mesh);return mesh;
}
function officialModule(parent,id,pos,{scale=1,axis='x',facing='east',legId=null,rotor=false}={}){
 const frame=new THREE.Group();frame.position.set(...pos);frame.scale.setScalar(scale);
 frame.userData.fullVoxelModule={id};
 parent.add(frame);
 // Structural Create blocks MUST look like one solid Minecraft cube even
 // before the original JSON/PNG arrives. The generic mechanical preview
 // builds a hollow decorative casing and creates floating separated bricks.
 const plainVoxelMaterial={
  'create:brass_casing':'brass',
  'create:andesite_casing':'teal',
  'create:industrial_iron_block':'steel'
 };
 const old=plainVoxelMaterial[id]
  ?new THREE.Mesh(hullGeom,mats[plainVoxelMaterial[id]])
  :createPreviewModel({id,axis,facing});
 old.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;if(legId)o.userData.legId=legId;}});
 frame.add(old);
 modules.push({id,frame,old,axis,facing,legId});
 if(rotor)spinning.push({group:frame,axis});
 if(assetCache.has(assetKey(id,axis,facing)))swapOfficial(frame,id,axis,facing,legId);
 return frame;
}
function swapOfficial(frame,id,axis,facing,legId){
 const template=assetCache.get(assetKey(id,axis,facing));if(!template)return false;
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
 // Honest Minecraft-sized voxels, with no duplicate block coordinates.
 // Positions are derived from the very same Create network that supplies
 // actual joint torque rather than decorative independently moving gears.
 const graph=buildCreatePowerBus({rpm:18,gearRatio:2});
 const occupied=new Set(graph.blocks.filter(b=>Math.abs(b.pos[0])<=2&&Math.abs(b.pos[2])<=3)
  .map(b=>b.pos[0]+','+b.pos[2]));
 for(let x=-2;x<=2;x++)for(let z=-3;z<=3;z++){
  if(occupied.has(x+','+z))continue;
  officialModule(g,Math.abs(x)===2?'create:industrial_iron_block':'create:brass_casing',[x,0,z]);
 }
 for(const x of [-2,2])for(const z of [-3,0,3])
  officialModule(g,'create:industrial_iron_block',[x,1,z]);
 officialModule(g,'create:brass_casing',[0,1,0]);
 officialModule(g,'create:andesite_casing',[0,0,-4]);
 for(const b of graph.blocks){
  const [x,y,z]=b.pos;
  const id=b.id,driven=id==='create:shaft'||id==='create:large_cogwheel';
  officialModule(g,id,[x,y,z],{axis:b.axis||'x',
   facing:b.facing||'east',scale:1,rotor:driven});
 }
}
function linkVisual(g,length,legId,segment){
 const isUpper=segment==='upper';
 // All visible individual voxel blocks remain exactly one MC block.
 // Real 1x1 voxel leg blocks are stacked on centers ±.5; bearings at
 // +1.5 and boots at -1.5 never intersect these solid voxels.
 // Rapier segment lengths and hinge anchors are now precisely 2 voxel units.
 for(const y of [-.5,.5])
  officialModule(g,isUpper?'create:andesite_casing':'create:industrial_iron_block',[0,y,0],{legId});
 if(isUpper){
  officialModule(g,'simulated:swivel_bearing',[0,1.5,0],
   {axis:'x',facing:'east',scale:1,legId});
  officialModule(g,'create:cogwheel',[1,0,0],{axis:'x',scale:1,legId});
 }else{
  officialModule(g,'simulated:swivel_bearing',[0,1.5,0],
   {axis:'x',facing:'east',scale:1,legId});
  officialModule(g,'create:andesite_casing',[0,-1.5,0],{legId});
 }
 g.traverse(o=>{if(o.isMesh)o.userData.legId=legId;});
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
 // Report actual physical angles/contacts alongside gait commands.
 $('gaitLegs').replaceChildren(...s.legAngles.map(j=>{
  const phase=j.detached?'detached':j.gaitPhase||'stand';
  const foot=s.footMeasurements.find(f=>f.id===j.id);
  const chip=document.createElement('span');
  chip.className='gait-leg gait-'+phase;
  chip.textContent=j.id+' '+({stance:'支撑',swing:'抬腿',stand:'站立',detached:'断裂'}[phase]||phase)
   +' · '+(j.hip*180/Math.PI).toFixed(0)+'°'
   +' · '+(foot?.touching?'触地':'离地');
  return chip;
 }));
 $('gaitCycle').textContent=s.mode==='stand'?'六足站立伺服中'
   :'步态周期 '+((s.gait?.cycle||0)%1*100).toFixed(0)+'% · '
     +(s.gait?.stance||0)+' 支撑 / '+(s.gait?.swing||0)+' 摆动 · '
     +'净位移 '+s.travel.toFixed(2)+' 格';
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
 for(const m of modules)if(swapOfficial(m.frame,m.id,m.axis,m.facing,m.legId))replaced++;
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
    const variants=[...new Map(modules.map(m=>[assetKey(m.id,m.axis,m.facing),
     {id:m.id,axis:m.axis,facing:m.facing}])).entries()];
    for(const [key,variant] of variants){
     const result=await jarModels.render({...variant,pos:[0,0,0]});
     if(result.group&&result.status==='authentic')assetCache.set(key,result.group);
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
 // Stand-only default of 135 N·m cannot propel the tested six-foot gait.
 // Use a reproducible, real Chrome-measured locomotion motor configuration
 // when the player has not already chosen stronger values.
 if(mode==='walk'){
  const rpmEl=$('motorRPM'),torqueEl=$('motorTorque');
  if(Number(rpmEl.value)<40){rpmEl.value='48';rpmEl.dispatchEvent(new Event('input',{bubbles:true}));}
  if(Number(torqueEl.value)<750){torqueEl.value='2200';torqueEl.dispatchEvent(new Event('input',{bubbles:true}));}
 }
 engine.setMode(mode);setModeButton(mode);
 setMessage({
  stand:'六足髋膝伺服器正在真实受力保持站立；腿承重不足时身体会塌。',
  march:'两组三角支撑腿交替抬腿：12 个真实物理关节驱动，关节角度由求解器更新。',
  walk:'采用已由 Chrome 真实物理行走验证的 48 RPM / 2200 N·m，或你设定的更强动力；三脚架支撑与足底摩擦推动机身，不使用手工平移。',
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
 if(w<560&&camera.position.distanceTo(orbit.target)<17)camera.position.set(15,11.2,17);
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
// A small, deterministic Minecraft-style voxel integrity inspection.
 // A module occupies one lattice cell even when its source JSON consists of
 // hundreds of decorative mesh triangles. Full-block modules may TOUCH
 // neighbors at their faces; they must not overlap the same cube interior.
 function auditVoxelOccupancy(){
  const overlaps=[],offGrid=[];
  const near=(a,b)=>Math.abs(a-b)<1-1e-6;
  for(const entry of visualBodies){
   const g=entry.mesh;
   const cells=[];
   for(const child of g.children){
    if(child.userData.fullVoxel){
     cells.push({id:'voxel',p:child.position,scale:child.scale});
    }else if(child.userData.fullVoxelModule){
     cells.push({id:child.userData.fullVoxelModule.id,p:child.position,scale:child.scale});
    }
   }
   for(const cell of cells){
    if(Math.abs(cell.scale.x-1)>1e-6||Math.abs(cell.scale.y-1)>1e-6||Math.abs(cell.scale.z-1)>1e-6)
     offGrid.push({id:cell.id,reason:'a single Minecraft model block is scaled'});
   }
   for(let i=0;i<cells.length;i++)for(let j=i+1;j<cells.length;j++){
    const a=cells[i],b=cells[j];
    if(near(a.p.x,b.p.x)&&near(a.p.y,b.p.y)&&near(a.p.z,b.p.z))
     overlaps.push({body:visualBodies.indexOf(entry),a:a.id,b:b.id,
      at:[a.p.x,a.p.y,a.p.z],other:[b.p.x,b.p.y,b.p.z]});
   }
  }
  return {overlaps,offGrid,rigidBodies:visualBodies.length,modules:modules.length};
 }
window.__BOSS_API__={
 get ready(){return ready},get engine(){return engine},get error(){return $('engineState').dataset.error||null},
 snapshot:()=>engine?.snapshot(),advance:n=>{const s=engine.advance(n);syncVisual();return s;},
 mode:m=>{engine.setMode(m);setModeButton(m)},
 motor:o=>{engine.setMotor(o);syncVisual();},
 disconnectBranch:(id,cut)=>{engine.disconnectBranch(id,cut);syncVisual();},
 getPowertrain:()=>engine?.snapshot().powertrain,
 voxelAudit:auditVoxelOccupancy,
 removeLeg,removeAllFeet:()=>{$('removeAll').click()},
 reset:createRig,physicsBodies:()=>visualBodies.length,visibleMeshes:()=>{let n=0;root?.traverse(o=>{if(o.isMesh&&o.visible)n++});return n;},
 getAssetStatus:()=>({officialTypes:[...new Set([...assetCache.keys()].map(k=>k.split('|')[0]))],
   officialVariants:[...assetCache.keys()],officialMeshes:officialCount,components:modules.length}),
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
