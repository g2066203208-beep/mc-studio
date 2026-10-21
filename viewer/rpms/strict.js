import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import {behavior,canPlace,evaluate,axisOf,CORE_VERSION} from './strict-core.mjs';

const $=id=>document.getElementById(id);
const names={
 'create:shaft':'传动轴','create:cogwheel':'小齿轮','create:large_cogwheel':'大齿轮','create:creative_motor':'创造模式马达',
 'create:water_wheel':'水车','create:large_water_wheel':'大型水车','create:gearbox':'齿轮箱','create:clutch':'离合器',
 'create:mechanical_press':'机械压印机','create:mechanical_mixer':'机械搅拌器','create:mechanical_pump':'机械泵',
 'create:windmill_bearing':'风车轴承','create:steam_engine':'蒸汽引擎','create:hand_crank':'手摇曲柄',
 'simulated:swivel_bearing':'旋转轴承','simulated:physics_assembler':'物理装配器',
 'simulated:gimbal_sensor':'万向姿态传感器','simulated:analog_transmission':'模拟传动器',
 'simulated:velocity_sensor':'速度传感器','simulated:optical_sensor':'光学传感器',
 'simulated:rope_winch':'绳索绞盘','simulated:merging_glue':'合并胶','simulated:steering_wheel':'方向盘'};
const COLORS={create:0x96724d,simulated:0x5b9d9c,minecraft:0x8a9caf};
let mode='place',registry=[],world=[],selectedId=null,inspected=null;
let jarFiles=0,activeLayer=1,lastResult=null;
const scene=new THREE.Scene();scene.background=new THREE.Color('#172736');
const camera=new THREE.PerspectiveCamera(43,1,.05,200);camera.position.set(12,13,17);
const renderer=new THREE.WebGLRenderer({canvas:$('view'),antialias:true});
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,2));renderer.outputColorSpace=THREE.SRGBColorSpace;
const orbit=new OrbitControls(camera,$('view'));orbit.enableDamping=true;orbit.dampingFactor=.09;orbit.maxDistance=55;orbit.minDistance=2;orbit.target.set(0,1,0);
scene.add(new THREE.HemisphereLight('#e8f4ff','#597182',2.4));
const sun=new THREE.DirectionalLight('#ffffff',2.2);sun.position.set(6,13,9);scene.add(sun);
const ground=new THREE.Mesh(new THREE.BoxGeometry(38,.08,38),new THREE.MeshStandardMaterial({color:'#233545',roughness:1}));
ground.position.y=.44;scene.add(ground);
const grid=new THREE.GridHelper(38,38,'#627c8d','#385263');grid.position.y=.5;scene.add(grid);
const axisHelper=new THREE.AxesHelper(2);axisHelper.position.set(-7,.58,-7);scene.add(axisHelper);
const placedGroup=new THREE.Group();scene.add(placedGroup);
const ray=new THREE.Raycaster(),pointer=new THREE.Vector2();
const matCache=new Map();let clickable=[],selectionOutline=null;
function labelFor(id){return names[id]||id.split(':')[1].replaceAll('_',' ');}
function tagFor(id){let c=behavior(id).coverage;return c==='topology'?'旋转拓扑预检':c==='speed-preview'?'RPM 预览':c==='input-topology-only'?'输入端预检':'游戏内原版执行';}
function glyph(id){return {shaft:'─',small:'⚙',large:'◎',source:'↻',consumer:'↓',bearing:'◉','native-only':'▧'}[behavior(id).kind]||'▦';}
function drawPalette(){
 const needle=$('search').value.trim().toLowerCase(),ns=$('namespace').value,cv=$('coverage').value;
 const matching=registry.filter(o=>(ns==='all'||o.id.startsWith(ns+':'))&&(!needle||(o.id+' '+labelFor(o.id)).toLowerCase().includes(needle))&&
 (cv==='all'||(cv==='topology'?behavior(o.id).coverage!=='requires-game':behavior(o.id).coverage==='requires-game')));
 $('registryCount').textContent=registry.filter(o=>o.source==='registered-in-source').length;
 $('jarCount').textContent=jarFiles?'+ '+registry.filter(o=>o.source!=='registered-in-source').length+' 本地 JAR 新项':'';
 const parent=$('paletteItems');parent.replaceChildren();
 for(const o of matching){
  const b=document.createElement('button');b.type='button';b.className='item'+(selectedId===o.id?' active':'');b.dataset.id=o.id;
  const g=document.createElement('span');g.className='block-glyph';g.textContent=glyph(o.id);
  const wrap=document.createElement('span');
  const nm=document.createElement('span');nm.className='name';nm.textContent=labelFor(o.id);
  const id=document.createElement('span');id.className='id';id.textContent=o.id;
  const status=document.createElement('span');status.className='feature';status.textContent=tagFor(o.id);
  wrap.append(nm,id,status);b.append(g,wrap);b.addEventListener('click',()=>selectBlock(o.id));parent.append(b);
 }
 if(!matching.length){const p=document.createElement('p');p.className='hint';p.textContent='没有匹配条目。可导入对应版本 Mod JAR 扩展目录。';parent.append(p);}
}
function selectBlock(id){
 selectedId=id;$('selectedName').textContent=id?labelFor(id)+' · '+id:'尚未选择方块';
 $('coverageState').textContent=id?tagFor(id):'';drawPalette();
 if(id)displayInspector({id,axis:$('axis').value,facing:$('facing').value,pos:['待放置','—','—']});
}
function displayInspector(item){
 const box=$('inspection');box.replaceChildren();
 const title=document.createElement('b');title.textContent=labelFor(item.id);
 const id=document.createElement('code');id.textContent=item.id;
 const pos=document.createElement('p');pos.textContent='坐标 '+item.pos.join(', ')+' · Axis '+axisOf(item);
 const desc=document.createElement('p');desc.textContent=behavior(item.id).desc;
 const cov=document.createElement('p');cov.textContent='规则覆盖：'+tagFor(item.id);
 box.append(title,id,pos,desc,cov);
 if(item.id==='simulated:swivel_bearing'){
  const warning=document.createElement('p');warning.style.color='#ffd18d';warning.textContent='当前仅核对机械轴线，未创建 Sable 刚体约束。';box.append(warning);
 }
 if(item.id==='create:creative_motor'){
  const p=document.createElement('p');p.textContent='预览设置：'+(item.rpm??16)+' RPM（非游戏实测）';box.append(p);
 }
}
function materialFor(id){
 if(!matCache.has(id))matCache.set(id,new THREE.MeshStandardMaterial({color:COLORS[id.split(':')[0]]??0x8499a8,roughness:.72,metalness:.08}));
 return matCache.get(id);
}
function visualFor(b){
 const geo=new THREE.BoxGeometry(.95,.95,.95),mesh=new THREE.Mesh(geo,materialFor(b.id));
 const g=new THREE.Group();g.position.set(...b.pos);g.userData.block=b;mesh.userData.block=b;g.add(mesh);
 const edge=new THREE.LineSegments(new THREE.EdgesGeometry(geo),new THREE.LineBasicMaterial({color:0x182839}));g.add(edge);
 const k=behavior(b.id).kind;
 // Stylized geometry is only a placeholder; never claim it is an official Mod model.
 if(['shaft','small','large','source','bearing'].includes(k)){
  const rod=new THREE.Mesh(new THREE.CylinderGeometry(k==='large'?.36:.2,k==='large'?.36:.2,1.03,16),
  new THREE.MeshStandardMaterial({color:k==='source'?0xf0ce76:0xe0e6e6,metalness:.3,roughness:.48}));
  if(axisOf(b)==='x')rod.rotation.z=Math.PI/2;
  if(axisOf(b)==='z')rod.rotation.x=Math.PI/2;
  rod.userData.block=b;g.add(rod);
 }
 // Raycasting only the full block cube ensures placement uses MC cardinal
 // block faces, never arbitrary diagonal normals from decorative rods.
 clickable.push(mesh);
 return g;
}
function renderWorld(){
 clickable=[];while(placedGroup.children.length)placedGroup.remove(placedGroup.children[0]);
 for(const b of world)placedGroup.add(visualFor(b));
 if(selectionOutline){selectionOutline.parent?.remove(selectionOutline);selectionOutline=null;}
 if(inspected){
  const t=placedGroup.children.find(o=>o.userData.block.pos.join(',')===inspected.pos.join(','));
  if(t){selectionOutline=new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(1.05,1.05,1.05)),new THREE.LineBasicMaterial({color:0xffdd83,depthTest:false}));selectionOutline.renderOrder=9;t.add(selectionOutline);}
 }
 updateStatus();
}
function updateStatus(){
 const r=evaluate(world);lastResult=r;
 $('blocksNum').textContent=r.summary.blocks;$('linksNum').textContent=r.summary.links;$('poweredNum').textContent=r.summary.powered;
 $('overall').className='diagnostic-status '+(r.errors.length?'bad':r.warnings.length?'warn':'');
 $('overall').textContent=r.errors.length?'检测到旋转冲突（不等于原版执行结果）':r.warnings.length?'部分拓扑预检完成 · 原版运行未验证':'无待检查的连接';
 const panel=$('diagnostics');panel.replaceChildren();
 for(const d of [...r.errors,...r.warnings].slice(0,28)){
  const row=document.createElement('div');row.className='diagnostic-item '+(r.errors.includes(d)?'error':'');
  const title=document.createElement('strong');title.textContent=d.code+(d.at?' @ '+d.at:'');
  const detail=document.createElement('span');detail.textContent=d.detail;row.append(title,detail);panel.append(row);
 }
 if(!r.errors.length&&!r.warnings.length)panel.textContent='放置方块后分析动力连接与不支持的游戏机制。';
 $('canvasStatus').textContent=r.summary.blocks+' blocks · '+r.summary.links+' rotation links · '+r.summary.powered+' RPM-assigned';
}
function setMode(v){
 mode=v;document.querySelectorAll('[data-mode]').forEach(b=>b.classList.toggle('active',b.dataset.mode===v));
 $('worldHint').textContent={place:'点击地面放置；点击已有方块表面向外放置；右键拆除',
 inspect:'点击方块查看其 Mod ID、位置和功能覆盖状态',erase:'点击方块删除（不模拟掉落和物理坠落）',
 wrench:'点击方块模拟扳手旋转轴线（原版限制待验证）'}[v];
}
function setView(v){
 const p={iso:[12,13,17],top:[0,24,.001],side:[20,10,0]}[v];if(!p)return;
 camera.position.set(...p);orbit.target.set(0,1,0);orbit.update();
 document.querySelectorAll('[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===v));
}
function projectPointer(e){
 const rect=$('view').getBoundingClientRect();
 pointer.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);ray.setFromCamera(pointer,camera);
 const hits=ray.intersectObjects(clickable,false);
 if(hits.length){
  const hit=hits[0],b=hit.object.userData.block;
  let n=hit.face?.normal?.clone().transformDirection(hit.object.matrixWorld);
  if(!n)n=new THREE.Vector3(0,1,0);
  return {target:b,where:b.pos.map((x,i)=>x+Math.round(n.getComponent(i))),onBlock:true};
 }
 const p=new THREE.Vector3(),plane=new THREE.Plane(new THREE.Vector3(0,1,0),-(activeLayer-.5));
 if(!ray.ray.intersectPlane(plane,p))return null;
 return {target:null,onBlock:false,where:[Math.round(p.x),activeLayer,Math.round(p.z)]};
}
function notify(t){$('canvasStatus').textContent=t;}
function place(b,opts={}){
 const state=canPlace(world,b,opts);
 if(!state.ok){notify('无法放置：'+state.message);return false;}
 if(!registry.some(o=>o.id===b.id)){notify('无法放置：未知 Mod 方块，需要来源 JAR');return false;}
 world.push(b);inspected=b;renderWorld();displayInspector(b);return true;
}
function removeAt(pos){const i=world.findIndex(b=>b.pos.every((x,k)=>x===pos[k]));if(i<0)return false;world.splice(i,1);inspected=null;renderWorld();return true;}
function interaction(e,actionMode){
 const hit=projectPointer(e);if(!hit)return;
 if(actionMode==='erase'){if(hit.target)removeAt(hit.target.pos);return;}
 if(actionMode==='inspect'){if(hit.target){inspected=hit.target;displayInspector(inspected);renderWorld();}return;}
 if(actionMode==='wrench'){
  const b=hit.target;if(!b)return;
  if(!['shaft','small','large','source'].includes(behavior(b.id).kind)){notify('此方块真实扳手动作需游戏内验证');return;}
  const axes=['x','y','z'];b.axis=axes[(axes.indexOf(axisOf(b))+1)%3];renderWorld();displayInspector(b);return;
 }
 if(!selectedId){notify('先在左侧选择方块');return;}
 const b={id:selectedId,pos:hit.where,axis:$('axis').value,facing:$('facing').value};
 if(b.id==='create:creative_motor')b.rpm=Number($('rpm').value);place(b);
}
let down=null;
$('view').addEventListener('pointerdown',e=>{down={x:e.clientX,y:e.clientY,button:e.button}});
$('view').addEventListener('pointerup',e=>{if(!down)return;const distance=Math.hypot(e.clientX-down.x,e.clientY-down.y),button=down.button;down=null;if(distance>6)return;interaction(e,button===2?'erase':mode)});
$('view').addEventListener('contextmenu',e=>e.preventDefault());
$('layer').addEventListener('input',e=>{activeLayer=Number(e.target.value);$('layerValue').textContent=activeLayer});
for(const b of document.querySelectorAll('[data-mode]'))b.addEventListener('click',()=>setMode(b.dataset.mode));
for(const b of document.querySelectorAll('[data-view]'))b.addEventListener('click',()=>setView(b.dataset.view));
for(const id of ['search','namespace','coverage'])$(id).addEventListener(id==='search'?'input':'change',drawPalette);
$('resetScene').addEventListener('click',()=>{world=[];inspected=null;renderWorld();notify('已清空预览场景')});
$('example').addEventListener('click',()=>{
 world=[
 {id:'create:creative_motor',pos:[0,1,0],axis:'x',facing:'east',rpm:16},
 {id:'create:shaft',pos:[1,1,0],axis:'x',facing:'east'},
 {id:'create:cogwheel',pos:[2,1,0],axis:'x'},
 {id:'create:cogwheel',pos:[2,1,1],axis:'x'}];
 inspected=world[0];renderWorld();displayInspector(world[0]);
 notify('源码规则示例：创造马达 → 轴 → 小齿轮 → 反向小齿轮。原版游戏行为未验证');
});
$('exportJson').addEventListener('click',()=>{
 const obj={format:'rpms-verified-layout',version:1,notMinecraftSchematic:true,
 disclaimer:'Native Create/Sable game required; this JSON is NOT a validated runnable schematic.',
 blocks:world.map(o=>({...o}))};
 const blob=new Blob([JSON.stringify(obj,null,2)],{type:'application/json'});
 const a=document.createElement('a'),u=URL.createObjectURL(blob);a.href=u;a.download='rpms-block-layout.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);
});
$('importJson').addEventListener('change',async e=>{
 const f=e.target.files?.[0];if(!f)return;
 try{
  const v=JSON.parse(await f.text());
  if(v.format!=='rpms-verified-layout'||!Array.isArray(v.blocks)||v.blocks.length>4000)throw Error('不是受支持的 RPMS JSON 蓝图');
  const valid=[];for(const b of v.blocks){
   if(!registry.some(r=>r.id===b.id))throw Error('未知或未导入的 Mod 方块 '+b.id);
   const s=canPlace(valid,b,{ignoreSupport:true});if(!s.ok)throw Error(s.message);valid.push({...b});
  }
  world=valid;inspected=null;renderWorld();notify('载入 JSON 坐标完成；不代表已验证装配及游戏运行');
 }catch(err){notify('导入失败：'+err.message)}e.target.value='';
});
$('jarInput').addEventListener('change',async e=>{
 if(typeof JSZip==='undefined'){$('jarStatus').textContent='JSZip 未加载，无法读取本地 JAR';return;}
 const files=[...e.target.files];let added=0,count=0,variants=0,failed=[];
 for(const f of files){
  try{
   const zip=await JSZip.loadAsync(f),paths=Object.keys(zip.files).filter(s=>/^assets\/(create|simulated)\/blockstates\/[^/]+\.json$/.test(s));
   for(const path of paths){
    const match=path.match(/^assets\/(create|simulated)\/blockstates\/([^/]+)\.json$/),id=match[1]+':'+match[2];
    if(!registry.some(b=>b.id===id)){registry.push({id,source:'local-jar'});added++;}
    try{const val=JSON.parse(await zip.file(path).async('text'));if(val.variants)variants+=Object.keys(val.variants).length;}
    catch(err){failed.push(path+' '+err.message)}
   }
   count++;
  }catch(err){failed.push(f.name+' '+err.message)}
 }
 jarFiles+=count;registry.sort((a,b)=>a.id.localeCompare(b.id));
 $('jarStatus').textContent='本地读取 '+count+' 个 JAR、+'+added+' 方块目录项、'+variants+' 个方块状态 variants。浏览器本地处理，不上传。'+(failed.length?' 解析失败 '+failed.length+' 项。':'')+' 动态原版功能仍由 MC 游戏执行。';
 drawPalette();e.target.value='';
});
function resize(){const e=$('viewWrap');const w=Math.max(100,e.clientWidth),h=Math.max(100,e.clientHeight);renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();}
new ResizeObserver(resize).observe($('viewWrap'));resize();
function draw(){requestAnimationFrame(draw);orbit.update();renderer.render(scene,camera);}
async function init(){
 try{
  const r=await fetch('./registry.json',{cache:'no-store'});if(!r.ok)throw Error('registry.json HTTP '+r.status);
  const data=await r.json();if(!Array.isArray(data.entries)||data.entries.length<200)throw Error('源码方块清单缺失');
  registry=data.entries;drawPalette();renderWorld();selectBlock('create:shaft');
  window.__STRICT_API__={version:CORE_VERSION,getCatalog:()=>registry.map(o=>o.id),getState:()=>world.map(o=>({...o,pos:[...o.pos]})),
  getResult:()=>evaluate(world),place,removeAt,setMode,selectBlock,reset:()=>{world=[];inspected=null;renderWorld()}};
  window.__STRICT_READY__=true;notify('方块注册表已加载 · MC 整数网格施工预览');
 }catch(err){window.__STRICT_ERROR__=err.message;notify('初始化失败：'+err.message);console.error(err)}
}
init();draw();