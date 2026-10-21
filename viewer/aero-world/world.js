import * as THREE from 'three';
import {ModResourceLoader} from './resources.js';

const $=id=>document.getElementById(id);
const canvas=$('world'),scene=new THREE.Scene(),assets=new ModResourceLoader();
scene.background=new THREE.Color(0x86c6e9);scene.fog=new THREE.Fog(0x86c6e9,22,72);
const camera=new THREE.PerspectiveCamera(75,1,.05,120);
camera.rotation.order='YXZ';
camera.position.set(0,3.12,9);
const renderer=new THREE.WebGLRenderer({canvas,antialias:false,alpha:false});
renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.5));renderer.outputColorSpace=THREE.SRGBColorSpace;
scene.add(new THREE.HemisphereLight(0xe8f7ff,0x6e705d,2.6));
const sun=new THREE.DirectionalLight(0xfff1d1,1.25);sun.position.set(-20,25,12);scene.add(sun);
const root=new THREE.Group();scene.add(root);
const geometry=new THREE.BoxGeometry(1,1,1);
const cells=new Map(),nodes=new Map(),textures=new Map(),matCache=new Map(),keyPressed=new Set(),touchPressed=new Set();
const terrainInstances=[],terrainLocations=new Map(); // InstancedMesh reduces terrain to ~12 draw calls.
const registry=['minecraft:grass_block','minecraft:stone','minecraft:oak_planks','minecraft:iron_block','minecraft:glass','minecraft:bricks',
'minecraft:oak_log','minecraft:polished_andesite','create:andesite_casing','create:shaft','create:creative_motor',
'simulated:swivel_bearing','simulated:physics_assembler','simulated:gimbal_sensor','simulated:analog_transmission','simulated:optical_sensor'];
let hotbar=['create:andesite_casing','simulated:swivel_bearing','create:shaft','create:creative_motor',
'simulated:physics_assembler','minecraft:iron_block','simulated:gimbal_sensor','minecraft:glass','minecraft:oak_planks'];
let slot=0,yaw=0,pitch=-.09,flying=true,paused=true,vy=0,grounded=false,ready=false,assetRevision=0;
let hovered=null,placeAt=null,noticeUntil=0,lastTime=performance.now(),touchStart=null,renderFrames=0;
const eyeHeight=1.62;
const cubeKey=(x,y,z)=>String(x)+','+String(y)+','+String(z);
const fromKey=k=>k.split(',').map(Number);
const defaults={
'simulated:swivel_bearing':{facing:'up',assembled:'false',powered:'false'},
'simulated:physics_assembler':{facing:'north'},
'simulated:analog_transmission':{axis:'y'},
'simulated:gimbal_sensor':{axis:'z'},
'simulated:optical_sensor':{facing:'north'},
'create:shaft':{axis:'y'},'create:creative_motor':{facing:'north'}
};
const outlines=new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(1.01,1.01,1.01)),new THREE.LineBasicMaterial({color:0x151719,depthTest:false}));
outlines.renderOrder=90;outlines.visible=false;scene.add(outlines);
function notice(text,duration=3){$('notice').textContent=text;$('notice').style.opacity=1;noticeUntil=performance.now()+duration*1000;}
function tint(id,face){
 if(id==='minecraft:grass_block')return face==='up'?'#679b47':'#82623f';
 if(id.includes('glass'))return '#9fcbd4';
 if(id.includes('iron'))return '#adb9bb';
 if(id.includes('diamond'))return '#75bfbc';
 if(id.includes('stone')||id.includes('andesite'))return '#868e91';
 if(id.includes('wood')||id.includes('log')||id.includes('planks'))return '#a48659';
 if(id.includes('brick'))return '#a77561';
 if(id.includes('bearing')||id.includes('motor'))return '#d5a76c';
 if(id.includes('sensor'))return '#75b5a8';
 if(id.includes('shaft'))return '#ae8958';
 let hash=0;for(const ch of id)hash=(Math.imul(hash,31)+ch.charCodeAt(0))|0;
 return new THREE.Color().setHSL((Math.abs(hash)%360)/360,.22,.48).getStyle();
}
function tex(id,face){
 const cacheKey=id+'|'+face;
 if(textures.has(cacheKey))return textures.get(cacheKey);
 const canvas=document.createElement('canvas');canvas.width=canvas.height=16;
 const c=canvas.getContext('2d');c.fillStyle=tint(id,face);c.fillRect(0,0,16,16);
 let seed=0;for(const ch of cacheKey)seed=(Math.imul(seed,33)+ch.charCodeAt(0))>>>0;
 for(let i=0;i<115;i++){
    seed=(Math.imul(seed,1664525)+1013904223)>>>0;const x=seed%16;
    seed=(Math.imul(seed,1664525)+1013904223)>>>0;const y=seed%16;
    c.fillStyle=(seed%3===0?'#ffffff1d':'#00000018');c.fillRect(x,y,1,1);
 }
 if(id==='minecraft:grass_block'&&face!=='up'){
   c.fillStyle='#5c9d4c';c.fillRect(0,0,16,4);
 }
 if(id.startsWith('simulated:')||id.startsWith('create:')){
   c.fillStyle='#252a2a';c.fillRect(3,3,10,10);
   c.fillStyle=tint(id,face);c.fillRect(5,5,6,6);
   c.fillStyle='#f4dbaf';c.fillRect(7,7,2,2);
 }
 const texture=new THREE.CanvasTexture(canvas);
 texture.magFilter=THREE.NearestFilter;texture.minFilter=THREE.NearestFilter;
 texture.generateMipmaps=false;texture.colorSpace=THREE.SRGBColorSpace;
 textures.set(cacheKey,texture);return texture;
}
function materials(id){
 if(matCache.has(id))return matCache.get(id);
 const parts=['east','west','up','down','south','north'].map(face=>new THREE.MeshLambertMaterial({
  map:tex(id,face),transparent:id.includes('glass'),opacity:id.includes('glass')?.55:1}));
 matCache.set(id,parts);return parts;
}
function fallback(id){
 const g=new THREE.Group();const mesh=new THREE.Mesh(geometry,materials(id));g.add(mesh);return g;
}
function retireTerrain(k){
 const ref=terrainLocations.get(k);
 if(!ref)return;
 const blank=new THREE.Object3D();blank.scale.setScalar(0);blank.updateMatrix();
 ref.mesh.setMatrixAt(ref.index,blank.matrix);
 ref.mesh.instanceMatrix.needsUpdate=true;
 terrainLocations.delete(k);
}
function batchTerrain(){
 for(const batch of terrainInstances)root.remove(batch);
 terrainInstances.length=0;terrainLocations.clear();
 for(const id of ['minecraft:stone','minecraft:grass_block']){
  const slots=[...cells].filter(([k,b])=>b.id===id).map(([k])=>k);
  if(!slots.length)continue;
  const inst=new THREE.InstancedMesh(geometry,materials(id),slots.length);
  inst.frustumCulled=false;
  const pose=new THREE.Object3D();
  slots.forEach((k,i)=>{
    const old=nodes.get(k);if(old){root.remove(old);nodes.delete(k);}
    pose.position.set(...fromKey(k));pose.rotation.set(0,0,0);pose.scale.set(1,1,1);pose.updateMatrix();
    inst.setMatrixAt(i,pose.matrix);terrainLocations.set(k,{mesh:inst,index:i});
  });
  inst.userData.terrainKeys=slots;inst.instanceMatrix.needsUpdate=true;
  root.add(inst);terrainInstances.push(inst);
 }
}
function updateVisual(k){
 const block=cells.get(k);if(!block)return;
 const prev=nodes.get(k);if(prev)root.remove(prev);
 const group=fallback(block.id);
 group.position.set(...fromKey(k));group.traverse(o=>{if(o.isMesh)o.userData.voxelKey=k;});
 root.add(group);nodes.set(k,group);
 if(assets.archives.length)void replaceWithAsset(k,group,block,assetRevision);
}
async function replaceWithAsset(k,group,block,rev){
 try{
   const orig=await assets.buildShape(block.id,block.state);
   if(!orig||rev!==assetRevision||nodes.get(k)!==group||cells.get(k)!==block)return;
   group.clear();group.add(orig);orig.traverse(o=>{if(o.isMesh)o.userData.voxelKey=k;});
 }catch(e){console.warn('Unrendered JSON model',block.id,e);}
}
function edit(x,y,z,id,state=null,silent=false){
 if(![x,y,z].every(Number.isInteger)||[x,y,z].some(n=>Math.abs(n)>96))return false;
 const k=cubeKey(x,y,z);
 if(!id){cells.delete(k);retireTerrain(k);const group=nodes.get(k);if(group)root.remove(group);nodes.delete(k);}
 else{
  if(!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id))return false;
  retireTerrain(k);
  cells.set(k,{id,state:{...(defaults[id]||{}),...(state||{})}});
  updateVisual(k);
 }
 if(!silent)autosave();return true;
}
function generate(){
 assetRevision++;root.clear();cells.clear();nodes.clear();terrainInstances.length=0;terrainLocations.clear();
 for(let x=-14;x<=14;x++)for(let z=-14;z<=14;z++){
  const height=Math.max(-1,Math.min(1,Math.round(1.2*Math.sin(x*.18)*Math.cos(z*.22))));
  for(let y=-2;y<=height;y++)edit(x,y,z,y===height?'minecraft:grass_block':'minecraft:stone',null,true);
 }
 for(let x=-2;x<=2;x++)for(let z=-9;z<=-5;z++)edit(x,2,z,'create:andesite_casing',null,true);
 edit(0,3,-8,'simulated:swivel_bearing',null,true);
 edit(0,4,-8,'minecraft:iron_block',null,true);
 edit(2,3,-8,'create:creative_motor',null,true);
 edit(2,3,-7,'create:shaft',null,true);
 edit(-2,3,-8,'simulated:physics_assembler',null,true);
 edit(-2,3,-7,'simulated:gimbal_sensor',null,true);
 edit(0,3,-5,'simulated:optical_sensor',null,true);
 batchTerrain();
 camera.position.set(0,3.12,9);yaw=0;pitch=-.09;flying=true;vy=0;
 notice('测试世界已加载：WASD移动、I选方块、F切换飞行。',6);autosave();
}
function data(){
 return {schema:1,type:'AEROWORLD_BROWSER_WORLD_NOT_MC_SAVE',
 blocks:[...cells].map(([k,v])=>({pos:fromKey(k),id:v.id,state:v.state})),
 player:{pos:camera.position.toArray(),yaw,pitch,flying},
 hotbar:[...hotbar],slot};
}
let saveTimer=null;
function autosave(){clearTimeout(saveTimer);saveTimer=setTimeout(()=>{
 try{localStorage.setItem('aeroworld-v1',JSON.stringify(data()));}catch(e){console.warn(e);}
},1000);}
function restore(obj){
 if(obj?.schema!==1||!Array.isArray(obj.blocks)||obj.blocks.length>25000)throw Error('不是有效的 AeroWorld 存档');
 const seen=new Set();
 for(const e of obj.blocks){
  if(!Array.isArray(e.pos)||e.pos.length!==3||!e.pos.every(n=>Number.isInteger(n)&&Math.abs(n)<=96)||
   typeof e.id!=='string'||!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(e.id))throw Error('存档数据包含非法方块');
  const k=cubeKey(...e.pos);if(seen.has(k))throw Error('重复坐标');seen.add(k);
 }
 assetRevision++;root.clear();cells.clear();nodes.clear();
 for(const e of obj.blocks)edit(...e.pos,e.id,e.state,true);
 batchTerrain();
 if(Array.isArray(obj.player?.pos)&&obj.player.pos.length===3&&obj.player.pos.every(Number.isFinite)){
   camera.position.fromArray(obj.player.pos);yaw=Number(obj.player.yaw)||0;pitch=Number(obj.player.pitch)||0;
   flying=!!obj.player.flying;
 }
 if(Array.isArray(obj.hotbar)&&obj.hotbar.length===9&&obj.hotbar.every(x=>typeof x==='string'&&x.length<150))hotbar=obj.hotbar.slice();
 slot=Number.isInteger(obj.slot)?Math.min(8,Math.max(0,obj.slot)):0;
 renderHotbar();autosave();notice('已还原 '+cells.size+' 个方块',3);
}
function download(){
 const blob=new Blob([JSON.stringify(data(),null,2)],{type:'application/json'});
 const u=URL.createObjectURL(blob);const a=document.createElement('a');
 a.href=u;a.download='aeroworld-browser-construction.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),1500);
}
function hotbarPick(i){slot=i;renderHotbar();autosave();}
function renderHotbar(){
 const el=$('hotbar');el.textContent='';
 for(let i=0;i<9;i++){
  const id=hotbar[i],b=document.createElement('button');b.className='slot'+(i===slot?' selected':'');
  b.title=id;b.type='button';
  const num=document.createElement('span');num.className='num';num.textContent=i+1;
  const icon=document.createElement('span');icon.className='iconcube';icon.style.background=tint(id,'up');
  const label=document.createElement('small');label.textContent=id.split(':').pop();
  b.append(num,icon,label);b.addEventListener('click',()=>hotbarPick(i));el.append(b);
 }
 $('selectedItem').textContent='当前：'+hotbar[slot];
}
function allIds(){return [...new Set([...registry,...assets.catalog,...hotbar])].sort();}
function renderPalette(){
 const ids=allIds(),nsSelect=$('namespace'),saved=nsSelect.value;
 const namespaces=[...new Set(ids.map(x=>x.split(':')[0]))];
 nsSelect.textContent='';const o=document.createElement('option');o.value='';o.textContent='全部命名空间';nsSelect.append(o);
 for(const ns of namespaces){const v=document.createElement('option');v.value=ns;v.textContent=ns;nsSelect.append(v);}
 nsSelect.value=namespaces.includes(saved)?saved:'';
 const q=$('search').value.toLowerCase().trim(),n=nsSelect.value;
 const shown=ids.filter(x=>(!n||x.startsWith(n+':'))&&x.toLowerCase().includes(q));
 $('inventoryStats').textContent=ids.length+' 个 ID · '+shown.length+' 个搜索匹配（仅静态资源，不包含 Mod 游戏逻辑）';
 const list=$('palette');list.textContent='';
 for(const id of shown.slice(0,260)){
  const b=document.createElement('button');b.type='button';b.className=id===hotbar[slot]?'active':'';
  const sample=document.createElement('span');sample.className='iconcube';sample.style.background=tint(id,'up');
  const label=document.createElement('small');label.textContent=id;
  b.append(sample,label);b.addEventListener('click',()=>{hotbar[slot]=id;renderHotbar();renderPalette();autosave();});
  list.append(b);
 }
 if(shown.length>260){const p=document.createElement('p');p.textContent='只显示前260项，请搜索筛选';list.append(p);}
}
let libraryReturn='menu';
function show(id){
 if(id==='library')libraryReturn=['start','inventory','menu'].find(x=>!$(x).classList.contains('hidden'))||'menu';
 for(const x of ['start','menu','inventory','library'])$(x).classList.add('hidden');
 $(id).classList.remove('hidden');paused=true;keyPressed.clear();touchPressed.clear();
 if(document.pointerLockElement===canvas)document.exitPointerLock();
 if(id==='inventory')renderPalette();
}
function resume(){
 for(const x of ['start','menu','inventory','library'])$(x).classList.add('hidden');
 paused=false;
 if(matchMedia('(pointer:fine)').matches&&canvas.requestPointerLock)
  Promise.resolve(canvas.requestPointerLock()).catch(()=>notice('鼠标锁定被拒绝，仍可用键盘操作'));
}
$('enter').addEventListener('click',resume);$('resume').addEventListener('click',resume);
$('menuButton').addEventListener('click',()=>show('menu'));
$('inventoryButton').addEventListener('click',()=>show('inventory'));
$('libraryButton').addEventListener('click',()=>show('library'));
$('inventoryImport').addEventListener('click',()=>show('library'));
$('startImport').addEventListener('click',()=>show('library'));
$('inventoryClose').addEventListener('click',()=>show('menu'));
$('libraryClose').addEventListener('click',()=>show(libraryReturn));
$('libraryDone').addEventListener('click',()=>show(libraryReturn));
$('save').addEventListener('click',download);
$('load').addEventListener('click',()=>$('worldInput').click());
$('worldInput').addEventListener('change',async e=>{
 const file=e.target.files?.[0];if(!file)return;
 try{if(file.size>6000000)throw Error('超过 6MB');restore(JSON.parse(await file.text()));show('menu');}
 catch(error){notice('载入失败：'+error.message,6);}
 e.target.value='';
});
$('newWorld').addEventListener('click',()=>{if(confirm('确定清空当前建造吗？')){generate();show('menu');}});
$('search').addEventListener('input',renderPalette);
$('namespace').addEventListener('change',renderPalette);
$('jarInput').addEventListener('change',async e=>{
 const files=[...e.target.files];if(!files.length)return;
 $('jarStatus').textContent='正在读取本地 JAR…';
 try{
  const n=await assets.addFiles(files);const list=$('jarList');list.textContent='';
  for(const a of assets.archives){const el=document.createElement('div');el.textContent=a.name+' · '+a.blocks+' blockstates';list.append(el);}
  $('jarStatus').textContent='已识别 '+n.total+' 个方块 ID，正在刷新三维模型。';
  renderPalette();void refreshWorld();
 }catch(error){$('jarStatus').textContent='导入失败：'+error.message;}
 e.target.value='';
});
async function refreshWorld(){
 const version=++assetRevision,entries=[...nodes.entries()];let count=0;
 // Use authentic installed vanilla 16x16 terrain textures when the user's vanilla JAR is present.
 for(const id of ['minecraft:grass_block','minecraft:stone']){
   const faces=id.endsWith('grass_block')?['minecraft:block/grass_block_side','minecraft:block/grass_block_side','minecraft:block/grass_block_top','minecraft:block/dirt','minecraft:block/grass_block_side','minecraft:block/grass_block_side']:
     Array(6).fill('minecraft:block/stone');
   const mats=materials(id);
   for(let i=0;i<6;i++){const tex=await assets.texture(faces[i],'minecraft');if(tex)mats[i].map=tex;mats[i].needsUpdate=true;}
 }

 for(const [k,group] of entries){
  if(version!==assetRevision)return;
  const cell=cells.get(k);if(cell)await replaceWithAsset(k,group,cell,version);
  if(++count%55===0){$('jarStatus').textContent='原版网格 '+count+'/'+entries.length;await new Promise(r=>setTimeout(r,0));}
 }
 if(version===assetRevision){$('jarStatus').textContent='资源已载入；可用 ID '+assets.catalog.size+' 种。动态 Mod 逻辑未执行。';notice('原版方块资源已刷新',4);}
}

const ray=new THREE.Raycaster();ray.far=7;
function aim(){
 hovered=null;placeAt=null;outlines.visible=false;
 ray.setFromCamera(new THREE.Vector2(0,0),camera);
 const targets=ray.intersectObjects([...nodes.values(),...terrainInstances],true);
 for(const hit of targets){
   const k=hit.object.userData.voxelKey||hit.object.userData.terrainKeys?.[hit.instanceId];
   if(!k||!cells.has(k))continue;
   const normal=hit.face?.normal?.clone().transformDirection(hit.object.matrixWorld)||new THREE.Vector3(0,1,0);
   // Geometry is centered at integer voxel coordinates; using the normal yields the adjacent block.
   const p=fromKey(k),adj=p.map((v,i)=>v+Math.round(normal.getComponent(i)));
   hovered={key:k,pos:p,...cells.get(k)};placeAt=adj;
   outlines.position.set(...p);outlines.visible=true;break;
 }
 $('target').textContent=hovered?hovered.id+' · '+hovered.pos.join(', '):'瞄准方块';
}
function playerOverlaps(x,y,z){
 const p=camera.position,r=.31;
 return p.x+r>x-.5&&p.x-r<x+.5&&p.z+r>z-.5&&p.z-r<z+.5&&
   p.y+.18>y-.5&&p.y-eyeHeight<y+.5;
}
function dig(){
 if(paused||!hovered)return false;
 const old=hovered;edit(...old.pos,null);aim();notice('拆除 '+old.id,2);return true;
}
function build(){
 if(paused||!placeAt)return false;
 const [x,y,z]=placeAt;
 if(cells.has(cubeKey(x,y,z))||playerOverlaps(x,y,z)){notice('此处不能放置方块',2);return false;}
 const id=hotbar[slot];edit(x,y,z,id);aim();notice('放置 '+id,2);return true;
}
function interact(){
 if(!hovered)return;
 if(hovered.id.startsWith('simulated:')||hovered.id.startsWith('create:')){
   notice(hovered.id+'：网页不能执行原版 Java BlockEntity/轴承动力；这里仅支持建造和检视。',8);
 }else notice('该方块的原版交互暂未实现：'+hovered.id,5);
}
function rotate(){
 if(!hovered)return;
 const dirs=['north','east','south','west','up','down'];
 const curr=dirs.indexOf(hovered.state.facing||'north');
 const state={...hovered.state,facing:dirs[(curr+1)%6]};
 edit(...hovered.pos,hovered.id,state);notice('调整展示朝向 '+state.facing,2);
}
canvas.addEventListener('contextmenu',e=>e.preventDefault());
document.addEventListener('keydown',e=>{
 if(['INPUT','TEXTAREA','SELECT'].includes(document.activeElement?.tagName))return;
 if(e.code==='Escape'){
   e.preventDefault();
   if(!paused)show('menu');
   else if(!$('inventory').classList.contains('hidden'))show('menu');
   else if(!$('library').classList.contains('hidden'))show(libraryReturn);
   return;
 }
 if(e.code==='KeyI'){if(!paused)show('inventory');return;}
 if(paused)return;
 if(/^Digit[1-9]$/.test(e.code)){hotbarPick(Number(e.code.slice(-1))-1);return;}
 if(e.code==='KeyF'){flying=!flying;vy=0;notice(flying?'飞行模式：空格上升 / Shift下降':'行走模式：空格跳跃',3);}
 if(e.code==='KeyE')interact();
 if(e.code==='KeyR')rotate();
 keyPressed.add(e.code);
 if(['Space','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.code))e.preventDefault();
});
document.addEventListener('keyup',e=>keyPressed.delete(e.code));
window.addEventListener('blur',()=>{keyPressed.clear();touchPressed.clear();});
let lockedOnce=false;
document.addEventListener('pointerlockchange',()=>{
 if(document.pointerLockElement===canvas)lockedOnce=true;
 else if(lockedOnce&&!paused&&!document.hidden)show('menu');
});
document.addEventListener('mousemove',e=>{
 if(paused||document.pointerLockElement!==canvas)return;
 yaw-=e.movementX*.0024;
 pitch=THREE.MathUtils.clamp(pitch-e.movementY*.0024,-1.52,1.52);
});
canvas.addEventListener('pointerdown',e=>{
 if(paused)return;
 if(e.pointerType==='touch'){touchStart=[e.clientX,e.clientY];return;}
 if(e.button===0)dig();
 if(e.button===2)build();
});
canvas.addEventListener('pointermove',e=>{
 if(paused||e.pointerType!=='touch'||!touchStart)return;
 yaw-=(e.clientX-touchStart[0])*.006;
 pitch=THREE.MathUtils.clamp(pitch-(e.clientY-touchStart[1])*.006,-1.52,1.52);
 touchStart=[e.clientX,e.clientY];
});
canvas.addEventListener('pointerup',e=>{if(e.pointerType==='touch')touchStart=null;});
canvas.addEventListener('wheel',e=>{
 if(paused)return;
 hotbarPick((slot+(e.deltaY>0?1:8))%9);e.preventDefault();
},{passive:false});
for(const b of document.querySelectorAll('[data-move]')){
 const id=b.dataset.move;
 b.addEventListener('pointerdown',e=>{e.preventDefault();b.setPointerCapture(e.pointerId);touchPressed.add(id);});
 b.addEventListener('pointerup',()=>touchPressed.delete(id));
 b.addEventListener('pointercancel',()=>touchPressed.delete(id));
}
$('mobileJump').addEventListener('click',()=>{
 if(paused)return;
 if(flying)camera.position.y+=.5;
 else if(grounded){vy=7;grounded=false;}
});
$('mobileBreak').addEventListener('click',dig);
$('mobilePlace').addEventListener('click',build);
function collide(pos){
 const r=.3,minX=Math.floor(pos.x-r+.5),maxX=Math.floor(pos.x+r+.5);
 const minZ=Math.floor(pos.z-r+.5),maxZ=Math.floor(pos.z+r+.5);
 const minY=Math.floor(pos.y-eyeHeight+.5),maxY=Math.floor(pos.y+.18+.5);
 for(let x=minX;x<=maxX;x++)for(let y=minY;y<=maxY;y++)for(let z=minZ;z<=maxZ;z++){
  if(!cells.has(cubeKey(x,y,z)))continue;
  if(pos.x+r>x-.499&&pos.x-r<x+.499&&pos.z+r>z-.499&&pos.z-r<z+.499&&
     pos.y+.18>y-.499&&pos.y-eyeHeight<y+.499)return true;
 }
 return false;
}
function motion(dt){
 if(paused)return;
 const f=Number(keyPressed.has('KeyW')||keyPressed.has('ArrowUp')||touchPressed.has('forward'))-
         Number(keyPressed.has('KeyS')||keyPressed.has('ArrowDown')||touchPressed.has('back'));
 const s=Number(keyPressed.has('KeyD')||touchPressed.has('right'))-
         Number(keyPressed.has('KeyA')||touchPressed.has('left'));
 const norm=Math.hypot(f,s)||1,step=(keyPressed.has('ControlLeft')?8:5)*dt;
 const dx=(-Math.sin(yaw)*f+Math.cos(yaw)*s)*step/norm;
 const dz=(-Math.cos(yaw)*f-Math.sin(yaw)*s)*step/norm;
 const p=camera.position,test=p.clone();test.x+=dx;
 if(flying||!collide(test))p.x=test.x;
 test.copy(p);test.z+=dz;
 if(flying||!collide(test))p.z=test.z;
 if(flying){
  p.y+=(Number(keyPressed.has('Space'))-Number(keyPressed.has('ShiftLeft')||keyPressed.has('ShiftRight')))*step;
  vy=0;grounded=false;
 }else{
  if(keyPressed.has('Space')&&grounded){vy=6.8;grounded=false;}
  vy=Math.max(-26,vy-22*dt);
  const dy=vy*dt,n=Math.max(1,Math.ceil(Math.abs(dy)/.17));
  for(let i=0;i<n;i++){
   test.copy(p);test.y+=dy/n;
   if(collide(test)){if(vy<0)grounded=true;vy=0;break;}
   p.y=test.y;grounded=false;
  }
 }
 p.x=THREE.MathUtils.clamp(p.x,-90,90);p.z=THREE.MathUtils.clamp(p.z,-90,90);
 if(p.y< -12){p.set(0,5,9);vy=0;flying=true;notice('已恢复飞行，避免跌出世界',4);}
}
function resize(){
 const w=Math.max(1,canvas.clientWidth),h=Math.max(1,canvas.clientHeight);
 renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();
}
new ResizeObserver(resize).observe(canvas);
function render(now){
 const dt=Math.min((now-lastTime)/1000||0,.06);lastTime=now;
 motion(dt);camera.rotation.set(pitch,yaw,0,'YXZ');
 if(!paused)aim();
 renderer.render(scene,camera);
 renderFrames++;
 $('coords').textContent='X '+camera.position.x.toFixed(1)+' · Y '+camera.position.y.toFixed(1)+
 ' · Z '+camera.position.z.toFixed(1)+(flying?' · FLY':'');
 if(now>noticeUntil)$('notice').style.opacity=0;
 requestAnimationFrame(render);
}
function init(){
 try{
  const saved=localStorage.getItem('aeroworld-v1');
  if(saved)restore(JSON.parse(saved));else generate();
 }catch(e){console.warn('Local world could not be restored',e);generate();}
 renderHotbar();renderPalette();resize();ready=true;
 window.__AEROWORLD_READY__=true;
 window.__AEROWORLD_API__={
  getBlockCount:()=>cells.size,getId:(x,y,z)=>cells.get(cubeKey(x,y,z))?.id||null,
  getPlayer:()=>camera.position.toArray(),getHotbar:()=>[...hotbar],isPlaying:()=>!paused,
  modBlocks:()=>assets.catalog.size,archives:()=>assets.archives.length,
  pressedKeys:()=>[...keyPressed],terrainBatchCount:()=>terrainInstances.length,frames:()=>renderFrames,
  addBlock:(x,y,z,id)=>edit(x,y,z,id),removeBlock:(x,y,z)=>edit(x,y,z,null),
  exportWorld:data,importWorld:restore,refreshModels:refreshWorld,enter:resume,
  inspect:()=>({hovered,placeAt}),
  probeOriginalModel:async(id,state)=>{const obj=await assets.buildShape(id,state||defaults[id]||{});let n=0;if(obj)obj.traverse(m=>{if(m.isMesh)n++;});return {renderable:!!obj,meshes:n};},
  traceAsset:async(id,state)=>{const st=state||defaults[id]||{};const bs=await assets.json(id,'blockstates');const variants=await assets.variants(id,st);const m=variants[0]?.model?await assets.model(variants[0].model):null;return {hasBlockstate:!!bs,model:variants[0]?.model||null,modelElements:m?.elements?.length||0,textures:m?.textures||{},archiveNames:assets.archives.map(a=>a.name),hasOriginalNamespace:[...assets.catalog].some(x=>x.startsWith('simulated:'))};}
 };
 notice('AeroWorld已准备好，点击进入并搭建原版航空学方块。',8);
 requestAnimationFrame(render);
}
init();
