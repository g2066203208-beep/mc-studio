import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';

const $=id=>document.getElementById(id);
const renderer=new THREE.WebGLRenderer({canvas:$('canvas'),antialias:true,preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(devicePixelRatio||1,2));
renderer.outputColorSpace=THREE.SRGBColorSpace;
renderer.toneMapping=THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure=1.4;
renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
const scene=new THREE.Scene();scene.background=new THREE.Color('#142436');
const camera=new THREE.PerspectiveCamera(42,1,.03,150);
camera.position.set(12,9,14);
const orbit=new OrbitControls(camera,renderer.domElement);
orbit.enableDamping=true;orbit.dampingFactor=.07;orbit.minDistance=3;orbit.maxDistance=55;
orbit.target.set(0,3.5,0);
scene.add(new THREE.HemisphereLight(0xcce7ff,0x344350,2.6));
const sun=new THREE.DirectionalLight(0xffffff,2.8);sun.position.set(-8,14,7);sun.castShadow=true;
sun.shadow.mapSize.set(2048,2048);sun.shadow.camera.left=sun.shadow.camera.bottom=-18;
sun.shadow.camera.right=sun.shadow.camera.top=18;sun.shadow.camera.near=.5;sun.shadow.camera.far=50;scene.add(sun);
const fill=new THREE.DirectionalLight(0x8bcaff,1.1);fill.position.set(7,5,-8);scene.add(fill);
const grid=new THREE.GridHelper(45,45,0x5e6d78,0x304c60);grid.position.y=-.03;scene.add(grid);
const floor=new THREE.Mesh(new THREE.PlaneGeometry(120,120),new THREE.MeshStandardMaterial({color:0x1b3040,roughness:.94}));
floor.rotation.x=-Math.PI/2;floor.position.y=-.045;floor.receiveShadow=true;scene.add(floor);
const root=new THREE.Group();root.position.y=6.1;scene.add(root);
const ray=new THREE.Raycaster(), pointer=new THREE.Vector2();
let mode='assembly', playing=true,speed=1,time=0,config=null,currentSelection=null;
const limbs=new Map(),jointNodes=new Map(),blocks=[],meshOwners=[],materials=new Map(),assetArchives=[],assets=new Map();
let archiveGeneration=0,selected="LF.HFE";
const MATERIALS={
 'minecraft:iron_block':0x9fb2bd,'minecraft:deepslate_tiles':0x4a5869,
 'minecraft:polished_andesite':0x818f9c,'create:andesite_casing':0x647f8c,
 'create:shaft':0x8d7764,
 'simulated:swivel_bearing':0xe1ac66,
 'simulated:physics_assembler':0x78b4c3,
 'simulated:analog_transmission':0xbd9555,
 'simulated:gimbal_sensor':0x72cdb6,
 'simulated:velocity_sensor':0x62bad2,
 'simulated:optical_sensor':0x9fd3b0,
 'simulated:gray_portable_engine':0x93a1a8
};
const officialIds=new Set(['simulated:swivel_bearing','simulated:physics_assembler','simulated:analog_transmission','simulated:gimbal_sensor','simulated:velocity_sensor','simulated:optical_sensor','simulated:gray_portable_engine']);
const defaultState={
 'simulated:swivel_bearing':{assembled:'false',facing:'up',powered:'false'},
 'simulated:physics_assembler':{face:'floor',facing:'north'},
 'simulated:analog_transmission':{axis:'y',powered:'false'},
 'simulated:gimbal_sensor':{axis:'z'},
 'simulated:velocity_sensor':{axis_along_first:'false',facing:'north',powered:'0'},
 'simulated:optical_sensor':{facing:'north',powered:'false',target:'wall'},
 'simulated:gray_portable_engine':{facing:'north',lit:'false'}
};
function stateFor(id,values={}){return Object.assign({},defaultState[id]||{},values);}
function block(group,segment,id,x,y,z,props={},role='structure'){
 const item={segment,id,local:[x,y,z],state:stateFor(id,props),role,group:null,pivot:group};
 const obj=new THREE.Group();obj.position.set(x,y,z);group.add(obj);item.group=obj;blocks.push(item);
 placeholder(item);return item;
}
const placeholderMats=new Map();
function matFor(id) {
 if(!placeholderMats.has(id)){
  placeholderMats.set(id,new THREE.MeshStandardMaterial({
   color:MATERIALS[id]||0x9870a0,metalness:.22,roughness:.67,transparent:true,opacity:.93
  }));
 }
 return placeholderMats.get(id);
}
function placeholder(item){
 while(item.group.children.length)item.group.remove(item.group.children[0]);
 // These are deliberately NOT official meshes. They are visible placeholders for a known block ID.
 const mesh=new THREE.Mesh(new THREE.BoxGeometry(.93,.93,.93),matFor(item.id));
 mesh.castShadow=true;mesh.receiveShadow=true;mesh.userData.item=item;item.group.add(mesh);meshOwners.push(mesh);
 const edges=new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(.93,.93,.93)),
   new THREE.LineBasicMaterial({color:0x111a22,transparent:true,opacity:.86}));
 item.group.add(edges);
 item.modelLoaded=false;
}
function createAssembly(){
 const body=(id,x,y,z,props,role)=>block(root,'BODY',id,x,y,z,props,role);
 for(let x=-1;x<=1;x++)for(let z=-3;z<=3;z++)body('create:andesite_casing',x,0,z);
 for(let z=-3;z<=3;z++){body('minecraft:iron_block',-1,1,z);body('minecraft:iron_block',1,1,z);}
 for(const z of [-2,0,2])body('minecraft:deepslate_tiles',0,1,z);
 body('simulated:physics_assembler',0,2,0);
 body('simulated:gimbal_sensor',0,2,-2,{axis:'z'},'sensor');
 body('simulated:velocity_sensor',0,2,2,{},'sensor');
 body('simulated:gray_portable_engine',0,2,-1,{},'power');
 body('simulated:analog_transmission',0,2,1,{axis:'y'},'transmission');
 body('simulated:optical_sensor',0,2,3,{facing:'south',target:'wall'},'sensor');
 for(const L of ['LF','RF','LR','RR']){
  const sign=L[0]==='L'?-1:1;
  const z=L[1]==='F'?2:-2;
  // The motor is an actual Simulated swivel_bearing block on its PARENT rigid segment.
  // The grouped child visually rotates at that bearing pivot. This is a kinematic design,
  // NOT an already verified native block assembly.
  const base=new THREE.Group();base.position.set(sign*2,0,z);root.add(base);
  block(base,'BODY','simulated:swivel_bearing',0,0,0,{facing:'south'},'joint '+L+'.HAA');
  const haa=new THREE.Group();base.add(haa);jointNodes.set(L+'.HAA',haa);
  block(haa,L+'_HIP_CARRIER','minecraft:iron_block',sign,0,0,{},'hip carrier');
  const pHfe=new THREE.Group();pHfe.position.set(sign,-1,0);haa.add(pHfe);
  block(pHfe,L+'_HIP_CARRIER','simulated:swivel_bearing',0,0,0,{facing:sign===1?'east':'west'},'joint '+L+'.HFE');
  const hfe=new THREE.Group();pHfe.add(hfe);jointNodes.set(L+'.HFE',hfe);
  block(hfe,L+'_UPPER','minecraft:iron_block',0,-1,0,{},'upper leg');
  block(hfe,L+'_UPPER','create:andesite_casing',0,-2,0,{},'upper leg');
  const pKfe=new THREE.Group();pKfe.position.y=-3;hfe.add(pKfe);
  block(pKfe,L+'_UPPER','simulated:swivel_bearing',0,0,0,{facing:sign===1?'east':'west'},'joint '+L+'.KFE');
  const kfe=new THREE.Group();pKfe.add(kfe);jointNodes.set(L+'.KFE',kfe);
  block(kfe,L+'_LOWER_FOOT','minecraft:iron_block',0,-1,0,{},'lower leg');
  block(kfe,L+'_LOWER_FOOT','minecraft:iron_block',0,-2,0,{},'lower leg');
  for(const xx of [-1,0,1])block(kfe,L+'_LOWER_FOOT','minecraft:deepslate_tiles',xx,-3,0,{},'foot');
  limbs.set(L,{base,haa,hfe,kfe,sign,z});
 }
}
createAssembly();
$('pieceCount').textContent=blocks.length;

const axes=[];
const axesRoot=new THREE.Group();root.add(axesRoot);
for(const [name,pivot] of jointNodes){
 const t=name.split('.')[1];
 const dir=new THREE.Vector3(t==='HAA'?0:1,0,t==='HAA'?1:0);
 const line=new THREE.ArrowHelper(dir,new THREE.Vector3(),.67,0xffd18d,.15,.09);
 pivot.add(line);axes.push(line);
}
const highlight=new THREE.Mesh(new THREE.TorusGeometry(.55,.035,8,40),new THREE.MeshBasicMaterial({color:0xfff09e,depthTest:false}));
highlight.renderOrder=10;
function selectJoint(id){
 selected=id;
 const node=jointNodes.get(id);if(!node)return;
 highlight.parent?.remove(highlight);node.add(highlight);
 highlight.rotation.set(id.endsWith('HAA')?0:0,id.endsWith('HAA')?0:Math.PI/2,0);
 for(const b of document.querySelectorAll('[data-joint]'))b.classList.toggle('active',b.dataset.joint===id);
}
const jointUi=$('joints');
for(const type of ['HAA','HFE','KFE']){
 for(const L of ['LF','RF','LR','RR']){
  const button=document.createElement('button');button.type='button';button.dataset.joint=L+'.'+type;
  button.textContent=L+' '+type;button.addEventListener('click',()=>selectJoint(button.dataset.joint));jointUi.append(button);
 }
}
selectJoint('LF.HFE');
function updateCatalog(){
 const counts=new Map();
 for(const b of blocks)counts.set(b.id,(counts.get(b.id)||0)+1);
 const target=$('catalog');target.textContent='';
 for(const [id,num] of [...counts].sort((a,b)=>a[0].localeCompare(b[0]))){
  const row=document.createElement('div');row.className='catalog-line';
  const txt=document.createElement('span');txt.textContent=id;
  const c=document.createElement('strong');c.textContent='× '+num;
  row.append(txt,c);target.append(row);
 }
}
updateCatalog();
function showItem(item){
 currentSelection=item;
 $('selection').replaceChildren();
 const title=document.createElement('strong');title.textContent=item.segment;
 const code=document.createElement('code');code.textContent=item.id+'['+Object.entries(item.state).map(([k,v])=>k+'='+v).join(',')+']';
 const local=document.createElement('small');local.textContent='分区: '+item.segment+' · 本地坐标: '+item.local.join(', ')+' · '+item.role;
 const res=document.createElement('small');res.textContent=item.modelLoaded?'正在使用你本地 JAR 中的原版方块模型':'当前为方块占位显示（请导入原版 JAR）';
 $('selection').append(title,document.createElement('br'),code,local,res);
 if(item.role.startsWith('joint'))selectJoint(item.role.split(' ')[1]);
}
$('selection').textContent='点击方块查看 Mod ID 与具体 BlockState 属性';

const textureCache=new Map(),modelCache=new Map(),jsonCache=new Map(),allUrls=[];
function normalizeRef(id,ref){
 if(ref.includes(':'))return ref;
 const ns=id.includes(':')?id.split(':')[0]:'minecraft';
 return ns+':'+ref;
}
function normalizePath(ref,kind){
 const [ns,loc]=ref.includes(':')?ref.split(':'):['minecraft',ref];
 return 'assets/'+ns+'/'+kind+'/'+(loc.includes('/')?loc:loc)+(kind==='textures'?'.png':'.json');
}
async function archiveFile(filePath){
 for(let i=assetArchives.length-1;i>=0;i--){
  const a=assetArchives[i].zip.file(filePath);
  if(a)return a;
 }
 return null;
}
async function loadJson(ref,kind){
 const path=normalizePath(ref,kind);
 if(jsonCache.has(path))return jsonCache.get(path);
 const entry=await archiveFile(path);
 const ret=entry?JSON.parse(await entry.async('text')):null;
 jsonCache.set(path,ret);return ret;
}
async function findBlockVariant(item){
 const id=item.id;
 const data=await loadJson(id.replace(':',':'),'blockstates');
 if(!data)return null;
 const keys=Object.entries(item.state).sort(([a],[b])=>a.localeCompare(b)).map(([a,b])=>a+'='+b).join(',');
 const match=data.variants?.[keys] || data.variants?.[''] ||
    Object.entries(data.variants||{}).find(([key])=>key.split(',').every(x=>!x||keys.split(',').includes(x)))?.[1];
 const itemModel=Array.isArray(match)?match[0]:match;
 const part=itemModel||data.multipart?.find(x=>x.apply)?.apply;
 return Array.isArray(part)?part[0]:part;
}
async function resolveModel(ref,seen=new Set()){
 if(seen.has(ref))return null;
 seen.add(ref);
 const data=await loadJson(ref,'models');
 if(!data)return null;
 let merged={elements:[],textures:{},texture_size:data.texture_size||[16,16]};
 if(data.parent){
  const p=normalizeRef(ref,data.parent.startsWith('block/')?data.parent:
    data.parent.startsWith('minecraft:')||data.parent.startsWith('create:')?data.parent:data.parent);
  const parentData=await resolveModel(p,seen);
  if(parentData)merged=parentData;
 }
 return {...merged,...data,textures:{...merged.textures,...(data.textures||{})},
   elements:data.elements||merged.elements||[]};
}
function texRef(model,key){
 let value=key;
 for(let i=0;i<8 && value?.startsWith('#');i++)value=model.textures?.[value.slice(1)];
 return value||null;
}
async function texture(ref) {
 if(textureCache.has(ref))return textureCache.get(ref);
 const path=normalizePath(ref,'textures');
 const f=await archiveFile(path);
 if(!f){textureCache.set(ref,null);return null;}
 const blob=await f.async('blob');
 const url=URL.createObjectURL(blob);allUrls.push(url);
 const t=await new THREE.TextureLoader().loadAsync(url);
 t.colorSpace=THREE.SRGBColorSpace;t.magFilter=THREE.NearestFilter;
 t.minFilter=THREE.NearestFilter;t.generateMipmaps=false;
 textureCache.set(ref,t);return t;
}
const faceData={
 north:{normal:[0,0,-1],indices:[0,1,2,3]},
 south:{normal:[0,0,1],indices:[0,1,2,3]},
 east:{normal:[1,0,0],indices:[0,1,2,3]},
 west:{normal:[-1,0,0],indices:[0,1,2,3]},
 up:{normal:[0,1,0],indices:[0,1,2,3]},
 down:{normal:[0,-1,0],indices:[0,1,2,3]}
};
function faceVertices(from,to,side){
 const [x1,y1,z1]=from.map(v=>v/16-.5),[x2,y2,z2]=to.map(v=>v/16-.5);
 switch(side){
 case'north':return [[x2,y1,z1],[x1,y1,z1],[x2,y2,z1],[x1,y2,z1]];
 case'south':return [[x1,y1,z2],[x2,y1,z2],[x1,y2,z2],[x2,y2,z2]];
 case'east':return [[x2,y1,z2],[x2,y1,z1],[x2,y2,z2],[x2,y2,z1]];
 case'west':return [[x1,y1,z1],[x1,y1,z2],[x1,y2,z1],[x1,y2,z2]];
 case'up':return [[x1,y2,z2],[x2,y2,z2],[x1,y2,z1],[x2,y2,z1]];
 case'down':return [[x1,y1,z1],[x2,y1,z1],[x1,y1,z2],[x2,y1,z2]];
 }}
function quadMesh(from,to,side,face,tex,texSize){
 const vertices=faceVertices(from,to,side);if(!vertices)return null;
 const uvs=face.uv||[0,0,16,16],tw=texSize?.[0]||16,th=texSize?.[1]||16;
 const v1=uvs[1]/th,v2=uvs[3]/th,u1=uvs[0]/tw,u2=uvs[2]/tw;
 const g=new THREE.BufferGeometry();
 const arr=[...vertices[0],...vertices[1],...vertices[2],...vertices[1],...vertices[3],...vertices[2]];
 const uv=[u1,1-v2,u2,1-v2,u1,1-v1,u2,1-v2,u2,1-v1,u1,1-v1];
 g.setAttribute('position',new THREE.Float32BufferAttribute(arr,3));
 g.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));g.computeVertexNormals();
 const material=new THREE.MeshStandardMaterial({map:tex,roughness:.72,metalness:.15,side:THREE.DoubleSide,transparent:true,alphaTest:.08});
 const m=new THREE.Mesh(g,material);m.castShadow=true;m.receiveShadow=true;return m;
}
async function loadAuthenticShape(item) {
 const variant=await findBlockVariant(item);
 if(!variant?.model)return false;
 const model=await resolveModel(variant.model);
 if(!model?.elements?.length)return false;
 const meshRoot=new THREE.Group();meshRoot.name=item.id+' / OFFICIAL JAR BLOCK MODEL';
 for(const element of model.elements){
  const layer=new THREE.Group();
  for(const [side,face] of Object.entries(element.faces||{})){
   const ref=texRef(model,face.texture);
   if(!ref)continue;
   const image=await texture(ref);
   if(!image)continue;
   const faceMesh=quadMesh(element.from,element.to,side,face,image,model.texture_size||[16,16]);
   if(faceMesh)layer.add(faceMesh);
  }
  if(!layer.children.length)continue;
  if(element.rotation?.angle){
   const pivot=(element.rotation.origin||[8,8,8]).map(v=>v/16-.5);
   const axis=element.rotation.axis;
   layer.position.set(...pivot);
   for(const f of layer.children)f.position.sub(new THREE.Vector3(...pivot));
   layer.rotation[axis]=THREE.MathUtils.degToRad(element.rotation.angle);
  }
  meshRoot.add(layer);
 }
 if(!meshRoot.children.length)return false;
 // Blockstate rotations take place about block center, matching Minecraft's block model space.
 meshRoot.rotation.order='YXZ';
 meshRoot.rotation.set(THREE.MathUtils.degToRad(variant.x||0),THREE.MathUtils.degToRad(variant.y||0),0);
 item.group.clear();item.group.add(meshRoot);
 meshRoot.traverse(o=>{if(o.isMesh)meshOwners.push(o);}); // avoid circular userData before Three.js clone()
 item.modelLoaded=true;return true;
}
async function renderFromArchives(){
 const gen=++archiveGeneration;
 $('assetStatus').textContent='正在从本地 JAR 解析方块状态 / 模型 / 纹理…';
 $('modelState').textContent='解析中';
 for(const u of allUrls)URL.revokeObjectURL(u);
 allUrls.length=0;jsonCache.clear();textureCache.clear();
 let matched=0,possible=0;
 // Cache a representative model by id+blockstate, then clone THREE groups at each position.
 const distinct=new Map();
 for(const item of blocks)distinct.set(item.id+JSON.stringify(item.state),item);
 const templates=new Map();
 for(const [key,item] of distinct){
  if(gen!==archiveGeneration)return;
  try {
   const success=await loadAuthenticShape(item);
   if(success)templates.set(key,item.group.children[0].clone(true));
  }catch(err){console.warn('Missing/unsupported JAR asset',item.id,err);}
 }
 for(const item of blocks) {
  if(gen!==archiveGeneration)return;
  possible++;
  const k=item.id+JSON.stringify(item.state);
  const prototype=templates.get(k);
  if(prototype) {
   item.group.clear();
   const clone=prototype.clone(true);clone.traverse(o=>{if(o.isMesh)o.userData.item=item;});
   item.group.add(clone);item.modelLoaded=true;matched++;
  }else{placeholder(item);}
 }
 $('assetStatus').textContent='本地 JAR 已解析：'+matched+' / '+possible+' 个位置载入真实方块模型，其余清楚标记为占位。';
 $('modelState').textContent=matched+' / '+possible;
 if(currentSelection)showItem(currentSelection);
 window.__AERO_ASSET_COUNT__=matched;
}
$('jars').addEventListener('change',async e=>{
 if(typeof JSZip==='undefined'){$('assetStatus').textContent='JSZip 加载失败，请刷新网页';return;}
 const inputs=[...e.target.files];let loaded=0;
 for(const file of inputs){
  try{
   const zip=await JSZip.loadAsync(file);assetArchives.push({name:file.name,zip});loaded++;
  }catch(err){console.warn('Invalid jar',file.name,err);}
 }
 $('assetStatus').textContent='成功读取 '+loaded+' 个本地 JAR；读取原版模型…';
 await renderFromArchives();
});
$('clearJars').addEventListener('click',()=>{
 archiveGeneration++;assetArchives=[];assets.clear();jsonCache.clear();textureCache.clear();
 for(const item of blocks)placeholder(item);
 $('assetStatus').textContent='未导入 · 仅显示方块占位结构';
 $('modelState').textContent='未导入';
 $('jars').value='';window.__AERO_ASSET_COUNT__=0;
 if(currentSelection)showItem(currentSelection);
});
$('export').addEventListener('click',()=>{
 const data={schema:1,origin:'RPMS-Q12 / Aeronautics Block Assembly Preview',status:'PROPOSED_NOT_TESTED_IN_MINECRAFT',
  notes:'Coordinates are segment-local; real Sable assembly and bearing glue/shaft connections MUST be validated in-game.',
  segments:[...new Set(blocks.map(b=>b.segment))],joints:[...jointNodes.keys()],
  blocks:blocks.map(b=>({segment:b.segment,id:b.id,blockstate:b.state,positionLocal:b.local,role:b.role}))};
 const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'});
 const a=document.createElement('a'),u=URL.createObjectURL(blob);a.href=u;a.download='rpms-q12-aeronautics-block-blueprint.json';a.click();
 setTimeout(()=>URL.revokeObjectURL(u),1000);
});

function setMode(v) {
 mode=v;
 for(const b of document.querySelectorAll('[data-mode]'))b.classList.toggle('active',b.dataset.mode===v);
 for(const l of limbs.values())l.base.position.x=l.sign*2;
 if(mode==='explode'){
  for(const l of limbs.values()){l.base.position.x=l.sign*3;l.hfe.position.y=-.2;l.kfe.position.y=-.2;}
 }else{
  for(const l of limbs.values()){l.hfe.position.y=0;l.kfe.position.y=0;}
 }
}
function setView(v){
 const p={iso:[12,9,14],side:[14,7,0],front:[0,7,15],top:[0,21,.005]}[v];
 if(!p)return;
 camera.position.set(...p);orbit.target.set(0,3.5,0);orbit.update();
 document.querySelectorAll('[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===v));
}
for(const b of document.querySelectorAll('[data-view]'))b.addEventListener('click',()=>setView(b.dataset.view));
for(const b of document.querySelectorAll('[data-mode]'))b.addEventListener('click',()=>setMode(b.dataset.mode));
$('showAxes').addEventListener('change',e=>axes.forEach(a=>a.visible=e.target.checked));
$('showGrid').addEventListener('change',e=>{grid.visible=e.target.checked;floor.visible=e.target.checked;});
$('showLabels').addEventListener('change',e=>{const show=e.target.checked;$('selection').style.opacity=show?1:.55;});
$('speed').addEventListener('input',e=>{speed=Number(e.target.value);$('speedLabel').textContent=speed.toFixed(1)+'×';});
$('pause').addEventListener('click',()=>{playing=!playing;$('pause').textContent=playing?'Ⅱ 暂停':'▶ 播放';});
$('reset').addEventListener('click',()=>{time=0;playing=true;$('pause').textContent='Ⅱ 暂停';setMode('assembly');});
function updateKinematics(){
 for(const [name,pivot] of jointNodes){
  const [L,t]=name.split('.'),i=['LF','RF','LR','RR'].indexOf(L);
  const phase=(time*.45+[0,.5,.75,.25][i])%1;
  const swing=phase>.75;
  const u=swing?(phase-.75)/.25:phase/.75;
  const advance=swing?(-12+24*u):(12-24*u);
  let deg=t==='HAA'?0:t==='HFE'?-9:t==='KFE'?16:0;
  if(mode==='gait'){
   if(t==='HFE')deg+=advance;
   if(t==='KFE')deg+=swing?26*Math.sin(Math.PI*u):0;
  }
  const def=config?.joints?.find(j=>j.id===name);
  if(def)deg=THREE.MathUtils.clamp(deg,def.minDeg,def.maxDeg);
  if(t==='HAA')pivot.rotation.z=THREE.MathUtils.degToRad(deg);
  else pivot.rotation.x=THREE.MathUtils.degToRad(deg);
 }
}
let pointerDown;
$('canvas').addEventListener('pointerdown',e=>pointerDown=[e.clientX,e.clientY]);
$('canvas').addEventListener('pointerup',e=>{
 if(!pointerDown||Math.hypot(e.clientX-pointerDown[0],e.clientY-pointerDown[1])>5)return;
 const rect=$('canvas').getBoundingClientRect();
 pointer.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);
 ray.setFromCamera(pointer,camera);
 const nodes=ray.intersectObjects(root.children,true);
 for(const hit of nodes){
  const item=hit.object.userData.item;
  if(item){showItem(item);break;}
 }
});
function resize(){
 const w=$('stage').clientWidth,h=$('stage').clientHeight;
 renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();
}
new ResizeObserver(resize).observe($('stage'));resize();
let previous=performance.now();
function draw(now){
 const dt=Math.min(.06,(now-previous)/1000);previous=now;
 if(playing&&mode==='gait')time+=dt*speed;
 updateKinematics();orbit.update();renderer.render(scene,camera);
 requestAnimationFrame(draw);
}
async function init(){
 try{
  const resp=await fetch('./quad12.design.json');
  if(!resp.ok)throw new Error('Missing Q12 design metadata');
  config=await resp.json();
  if(config.segments.length!==13||config.joints.length!==12)throw new Error('Engineering rigid-body graph mismatch');
  for(const id of jointNodes.keys())if(!config.joints.some(j=>j.id===id))throw new Error('Missing joint in RPMS design '+id);
  $('runtime').textContent='方块装配结构 · '+blocks.length+' 块 / 13 段 / 12 关节';
  $('loading').hidden=true;
  window.__AERO_READY__=true;
  window.__AERO_API__={blockCount:blocks.length,ids:[...new Set(blocks.map(b=>b.id))],joints:[...jointNodes.keys()],getMode:()=>mode,
    getLoadedModels:()=>blocks.filter(b=>b.modelLoaded).length,getSnapshot:()=>blocks.map(b=>({id:b.id,segment:b.segment,pos:b.local}))};
  requestAnimationFrame(draw);
 }catch(e){$('loading').textContent='装配数据载入失败：'+e.message;console.error(e);window.__AERO_ERROR__=e.message;}
}
init();