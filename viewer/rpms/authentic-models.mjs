// Browser-only reader for Minecraft blockstate JSON, model JSON and PNG textures
// from user-selected, version-matched Mod/client/resource-pack archives.
// No Create/Simulated copyrighted assets are copied to the hosted website.
import * as THREE from 'three';

const FACE_VERTS={
 north:(a,b)=>[[b[0],a[1],a[2]],[a[0],a[1],a[2]],[b[0],b[1],a[2]],[a[0],b[1],a[2]]],
 south:(a,b)=>[[a[0],a[1],b[2]],[b[0],a[1],b[2]],[a[0],b[1],b[2]],[b[0],b[1],b[2]]],
 east:(a,b)=>[[b[0],a[1],b[2]],[b[0],a[1],a[2]],[b[0],b[1],b[2]],[b[0],b[1],a[2]]],
 west:(a,b)=>[[a[0],a[1],a[2]],[a[0],a[1],b[2]],[a[0],b[1],a[2]],[a[0],b[1],b[2]]],
 up:(a,b)=>[[a[0],b[1],b[2]],[b[0],b[1],b[2]],[a[0],b[1],a[2]],[b[0],b[1],a[2]]],
 down:(a,b)=>[[a[0],a[1],a[2]],[b[0],a[1],a[2]],[a[0],a[1],b[2]],[b[0],a[1],b[2]]]
};
const DIRS=Object.keys(FACE_VERTS);
const cubeFaces=Object.fromEntries(DIRS.map(d=>[d,{texture:'#all'}]));
const defaultBox=faces=>({from:[0,0,0],to:[16,16,16],faces});
const cube={elements:[defaultBox(cubeFaces)]};
const vanillaGeometries={
 'block/cube_all':cube,
 'block/cube':{elements:[defaultBox(Object.fromEntries(DIRS.map(d=>[d,{texture:'#'+d}])))]},
 'block/cube_bottom_top':{elements:[defaultBox(Object.fromEntries(DIRS.map(d=>[d,{texture:'#'+(d==='up'?'top':d==='down'?'bottom':'side')}])))]},
 'block/cube_column':{elements:[defaultBox(Object.fromEntries(DIRS.map(d=>[d,{texture:'#'+(d==='up'||d==='down'?'end':'side')}])))]},
 'block/orientable':{elements:[defaultBox(Object.fromEntries(DIRS.map(d=>[d,{texture:'#'+(d==='north'?'front':d==='up'||d==='down'?'top':'side')}])))]},
 'block/orientable_with_bottom':{elements:[defaultBox(Object.fromEntries(DIRS.map(d=>[d,{texture:'#'+(d==='north'?'front':d==='up'?'top':d==='down'?'bottom':'side')}])))]}
};
const q=n=>Number(n)/16-.5;
const norm=(v,base='minecraft')=>v.includes(':')?v:base+':'+v;
const assetPath=(id,folder)=>{const [ns,...rest]=id.split(':');return 'assets/'+ns+'/'+folder+'/'+rest.join(':')+(folder==='textures'?'.png':'.json');};
const parseKey=k=>Object.fromEntries((k||'').split(',').filter(Boolean).map(pair=>{const n=pair.indexOf('=');return [pair.slice(0,n),pair.slice(n+1)]}));
const choice=x=>Array.isArray(x)?x[0]:x;
const blend=(a,b)=>({...a,...b});
const matchWhen=(condition,state)=>{
 if(!condition)return true;
 if(Array.isArray(condition))return condition.some(x=>matchWhen(x,state));
 if(condition.OR)return condition.OR.some(x=>matchWhen(x,state));
 if(condition.AND)return condition.AND.every(x=>matchWhen(x,state));
 return Object.entries(condition).every(([k,v])=>String(v).split('|').includes(String(state[k])));
};
function defaultFaceUv(side,from,to){
 const [x,y,z]=from,[X,Y,Z]=to;
 switch(side){
 case 'north':return [16-X,16-Y,16-x,16-y];
 case 'south':return [x,16-Y,X,16-y];
 case 'east':return [16-Z,16-Y,16-z,16-y];
 case 'west':return [z,16-Y,Z,16-y];
 case 'up':return [x,z,X,Z];
 case 'down':return [x,16-Z,X,16-z];
 }
 return [0,0,16,16];
}
function uvFor(face,side,element){
 const uv=face.uv||defaultFaceUv(side,element.from,element.to);
 let arr=[[uv[0]/16,1-uv[3]/16],[uv[2]/16,1-uv[3]/16],[uv[0]/16,1-uv[1]/16],[uv[2]/16,1-uv[1]/16]];
 const quarter=((Number(face.rotation||0)%360)+360)%360/90;
 // Shift each rectangle corner around its midpoint by 90-degree steps.
 for(let k=0;k<quarter;k++)arr=arr.map(([u,v])=>[(uv[0]+uv[2])/32+(v-(1-(uv[1]+uv[3])/32)),1-(uv[1]+uv[3])/32-(u-(uv[0]+uv[2])/32)]);
 return arr;
}
function faceMesh(element,side,face,tex){
 const vertices=FACE_VERTS[side]([q(element.from[0]),q(element.from[1]),q(element.from[2])],[q(element.to[0]),q(element.to[1]),q(element.to[2])]);
 if(!vertices)return null;
 const geometry=new THREE.BufferGeometry();
 geometry.setAttribute('position',new THREE.Float32BufferAttribute([ ...vertices[0],...vertices[1],...vertices[2],...vertices[1],...vertices[3],...vertices[2]],3));
 const uv=uvFor(face,side,element);
 geometry.setAttribute('uv',new THREE.Float32BufferAttribute([...uv[0],...uv[1],...uv[2],...uv[1],...uv[3],...uv[2]],2));
 geometry.computeVertexNormals();
 const mat=new THREE.MeshStandardMaterial({map:tex,transparent:true,alphaTest:.08,roughness:.9,metalness:0,side:THREE.DoubleSide});
 return new THREE.Mesh(geometry,mat);
}

export class MinecraftJarModels {
 constructor(){this.archives=[];this.jsonCache=new Map();this.textureCache=new Map();this.meshCache=new Map();this.urls=[];this.stats={archives:0,models:0,textures:0};}
 async addFile(file,Zip=globalThis.JSZip){
  if(!Zip)throw Error('JSZip 尚未加载');
  // The official Aeronautics 1.3.2 distribution is a wrapper with
  // THREE nested JARs (Simulated / Aeronautics / Offroad). Without traversing
  // META-INF/jarjar, no genuine Aeronautics model can ever load.
  const added=[],allPaths=new Set(),maxDepth=2,maxArchives=24;
  const unpack=async(bytes,name,depth)=>{
   if(added.length>=maxArchives)throw Error('超出嵌套 JAR 安全数量');
   const zip=await Zip.loadAsync(bytes);
   const paths=Object.keys(zip.files).filter(p=>p.startsWith('assets/')&&!zip.files[p].dir);
   this.archives.push({name,zip,paths:new Set(paths)});
   added.push({name,blocks:paths.filter(p=>/^assets\/[^/]+\/blockstates\/.+\.json$/.test(p)).length});
   paths.forEach(p=>allPaths.add(p));
   if(depth>=maxDepth)return;
   const children=Object.keys(zip.files).filter(p=>/^META-INF\/jarjar\/[^/]+\.jar$/i.test(p));
   if(children.length>maxArchives)throw Error('包含过多嵌套依赖 JAR');
   for(const child of children){
    const entry=zip.file(child);
    if(!entry)continue;
    if((entry._data?.uncompressedSize||0)>90*1024*1024)
     throw Error('超大内部 JAR 不允许在浏览器解析: '+child);
    const raw=await entry.async('uint8array');
    if(raw.length>90*1024*1024)throw Error('内部 JAR 体积超限');
    await unpack(raw,name+'!/'+child,depth+1);
   }
  };
  await unpack(file,file.name||'local zip',0);
  this.jsonCache.clear();this.meshCache.clear();
  // Layering a resource pack or client after a Mod JAR must also invalidate
  // cached MISSING textures, otherwise previously absent assets stay invisible.
  for(const url of this.urls)URL.revokeObjectURL(url);
  this.urls=[];
  for(const texture of this.textureCache.values())texture?.dispose?.();
  this.textureCache.clear();
  this.stats.archives=this.archives.length;
  return {archive:file.name,embedded:Math.max(0,added.length-1),
   blockstates:added.reduce((n,o)=>n+o.blocks,0),paths:[...allPaths]};
 }
 has(id){
  const path=assetPath(id,'blockstates');
  return this.archives.some(a=>a.paths.has(path));
 }
 async file(path){
  for(let i=this.archives.length-1;i>=0;i--){
   const arc=this.archives[i];
   if(arc.paths.has(path))return arc.zip.file(path);
  }
  return null;
 }
 async json(id,folder){
  const path=assetPath(id,folder);
  if(this.jsonCache.has(path))return this.jsonCache.get(path);
  const file=await this.file(path);
  let result=null;
  if(file){try{result=JSON.parse(await file.async('text'));}catch(e){console.warn('Invalid Minecraft JSON:',path,e);}}
  this.jsonCache.set(path,result);return result;
 }
 async stateKeys(id){
  const json=await this.json(id,'blockstates');
  return json?.variants?Object.keys(json.variants):[];
 }
 async model(ref,seen=new Set()){
  if(seen.has(ref))return null;
  seen.add(ref);
  let local=await this.json(ref,'models');
  if(!local&&ref.startsWith('minecraft:'))local=vanillaGeometries[ref.slice(10)]||null;
  if(!local)return null;
  let parent=null;
  if(local.parent){
   // Mojang ResourceLocation parsing: unqualified model refs are minecraft:...
   parent=await this.model(norm(local.parent),seen);
  }
  // Include geometries but never fabricate absent materials, textures or custom loaders.
  return {...(parent||{}),...local,
   textures:blend(parent?.textures||{},local.textures||{}),
   elements:local.elements??parent?.elements??[],
   unsupportedLoader:!!(local.loader||local.custom_loader||parent?.unsupportedLoader)};
 }
 async texture(ref){
  if(this.textureCache.has(ref))return this.textureCache.get(ref);
  const path=assetPath(ref,'textures'),file=await this.file(path);
  if(!file){this.textureCache.set(ref,null);return null;}
  const url=URL.createObjectURL(await file.async('blob'));this.urls.push(url);
  const tex=await new THREE.TextureLoader().loadAsync(url);
  tex.colorSpace=THREE.SRGBColorSpace;tex.magFilter=THREE.NearestFilter;
  tex.minFilter=THREE.NearestFilter;tex.generateMipmaps=false;
  this.textureCache.set(ref,tex);this.stats.textures++;
  return tex;
 }
 textureId(model,texture,base){
  let t=texture;
  for(let n=0;n<16&&typeof t==='string'&&t.startsWith('#');n++)t=model.textures?.[t.slice(1)];
  if(!t||t.startsWith('#'))return null;
  return norm(t,base);
 }
 resolveVariants(data,item){
  const variants=data.variants||{};
  const props=blend({axis:item.axis||'y',facing:item.facing||'north',powered:'false',assembled:'false'},item.state||{});
  if(item.stateKey!==undefined&&item.stateKey!==''&&Object.hasOwn(variants,item.stateKey))
   return {parts:[choice(variants[item.stateKey])],matched:item.stateKey,exact:true};
  const candidates=Object.entries(variants).map(([key,value])=>{
   const pairs=Object.entries(parseKey(key));
   let matches=0,mismatches=0,unknown=0;
   for(const [k,v] of pairs){
    if(props[k]===undefined)unknown++;
    else if(String(props[k])===v)matches++;
    else mismatches++;
   }
   // First choose an exact state if possible. Otherwise select the closest
   // genuine JAR variant and label it PARTIAL, never an invented fake mesh.
   return {key,value,matches,mismatches,unknown,
    score:matches*10-mismatches*100-unknown};
  }).sort((a,b)=>b.score-a.score||a.key.localeCompare(b.key));
  const found=candidates[0];
  if(!found)return {parts:[],matched:null,exact:false};
  return {parts:[choice(found.value)],matched:found.key,
   exact:found.mismatches===0&&found.unknown===0};
 }
 async create(item){
  const data=await this.json(item.id,'blockstates');
  if(!data)return {status:'missing',reason:'未找到该方块的 blockstates JSON',group:null};
  const stateResult=this.resolveVariants(data,item);
  const props=blend({axis:item.axis||'y',facing:item.facing||'north',powered:'false',assembled:'false'},
    blend(parseKey(stateResult.matched||item.stateKey||''),item.state||{}));
  let parts=[...stateResult.parts];
  for(const entry of data.multipart||[]){
   if(matchWhen(entry.when,props)){
    const selected=choice(entry.apply);if(selected)parts.push(selected);
   }
  }
  if(!parts.length)return {status:'unsupported',reason:'未找到匹配的方块状态，或缺少完整状态参数',group:null};
  const root=new THREE.Group();
  let missingTextures=0,unsupported=0,meshes=0;
  for(const part of parts){
   if(!part?.model){unsupported++;continue;}
   const model=await this.model(norm(part.model));
   if(!model||model.unsupportedLoader||!model.elements?.length){unsupported++;continue;}
   const layer=new THREE.Group();
   const ns=part.model.includes(':')?part.model.split(':')[0]:'minecraft';
   for(const el of model.elements){
    if(!Array.isArray(el.from)||!Array.isArray(el.to))continue;
    const egroup=new THREE.Group();
    for(const [side,face] of Object.entries(el.faces||{})){
     if(!FACE_VERTS[side])continue;
     const textureId=this.textureId(model,face.texture,ns);
     if(!textureId){missingTextures++;continue;}
     const tex=await this.texture(textureId);
     if(!tex){missingTextures++;continue;}
     const m=faceMesh(el,side,face,tex);
     if(m){egroup.add(m);meshes++;}
    }
    if(!egroup.children.length)continue;
    if(el.rotation?.angle){
     const pivot=(el.rotation.origin||[8,8,8]).map(q);
     egroup.position.set(...pivot);
     for(const child of egroup.children)child.position.sub(new THREE.Vector3(...pivot));
     const ax=el.rotation.axis;
     if(['x','y','z'].includes(ax))egroup.rotation[ax]=THREE.MathUtils.degToRad(el.rotation.angle);
    }
    layer.add(egroup);
   }
   layer.rotation.order='YXZ';
   layer.rotation.set(THREE.MathUtils.degToRad(part.x||0),THREE.MathUtils.degToRad(part.y||0),0);
   root.add(layer);
  }
  if(!meshes)return {status:'unsupported',reason:unsupported?'该方块使用自定义模型加载器 / 动态渲染或缺少依赖资源':'模型缺少 PNG 贴图/方块材质',group:null};
  const fallback=!!Object.keys(data.variants||{}).length&&!stateResult.exact;
  const partial=unsupported>0||missingTextures>0||fallback;
  const reason=fallback
   ?'完整 BlockState 尚未提供；显示正式 JAR 的接近变体 '+stateResult.matched+'，需在下方原版状态列表选择'
   :partial?'部分静态模型/贴图缺失，动态原版渲染另行检查':'已读取 JAR 原始静态 JSON 元素与 PNG 贴图';
  return {status:partial?'partial':'authentic',reason,
    group:root,matched:stateResult.matched,meshes,missingTextures,unsupported};
 }
 async render(item){
  const key=JSON.stringify([item.id,item.axis,item.facing,item.stateKey||'',item.state||{}]);
  if(!this.meshCache.has(key))this.meshCache.set(key,this.create(item));
  try{
   const result=await this.meshCache.get(key);
   return {...result,group:result.group?.clone(true)||null};
  }catch(e){this.meshCache.delete(key);return {status:'unsupported',reason:String(e.message||e),group:null};}
 }
 countStates(){return this.archives.reduce((n,a)=>n+[...a.paths].filter(p=>p.includes('/blockstates/')).length,0);}
}
