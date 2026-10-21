import * as THREE from 'three';

/**
 * Read user-owned Minecraft/Create/Simulated JARs IN MEMORY, not as executable code.
 * Importing a Mod JAR cannot execute a JVM Mod or BlockEntity mechanics in WebGL.
 * Support: common vanilla JSON parent models, variants, basic multipart, element rotations,
 * element texture UVs. Dynamic BER/animated block entities are NOT supported.
 */
const SIDES=['north','south','east','west','up','down'];
const FACE={north:[[1,0,0],[0,0,0],[1,1,0],[0,1,0]],south:[[0,0,1],[1,0,1],[0,1,1],[1,1,1]],
  east:[[1,0,1],[1,0,0],[1,1,1],[1,1,0]],west:[[0,0,0],[0,0,1],[0,1,0],[0,1,1]],
  up:[[0,1,1],[1,1,1],[0,1,0],[1,1,0]],down:[[0,0,0],[1,0,0],[0,0,1],[1,0,1]]};
const BASE_CUBE={from:[0,0,0],to:[16,16,16],faces:Object.fromEntries(SIDES.map(side=>[side,{texture:'#all'}]))};
const MAX_FILE_SIZE=180*1024*1024;

function refPath(name,folder,defaultNamespace='minecraft'){
  const pos=name.indexOf(':');
  const ns=pos<0?defaultNamespace:name.slice(0,pos);
  const target=pos<0?name:name.slice(pos+1);
  if(!/^[a-z0-9_.-]+$/.test(ns)||!target||target.includes('..')||! /^[a-z0-9_./-]+$/.test(target))return null;
  return 'assets/'+ns+'/'+folder+'/'+target+(folder==='textures'?'.png':'.json');
}
function isWhenMatch(when,state){
  if(!when)return true;
  if(Array.isArray(when.OR))return when.OR.some(x=>isWhenMatch(x,state));
  if(Array.isArray(when.AND))return when.AND.every(x=>isWhenMatch(x,state));
  return Object.entries(when).every(([key,value])=>String(value).split('|').includes(String(state[key]??'')));
}
export class ModResourceLoader{
  constructor(){
    this.archives=[];this.catalog=new Set();this.jsonCache=new Map();this.textureCache=new Map();
    this.modelCache=new Map();this.shapeCache=new Map();this.urls=[];this.generation=0;
  }
  async addFiles(files){
    if(typeof JSZip==='undefined')throw new Error('JSZip not loaded. Check CDN connection.');
    let found=0;
    const visit=async(name,zip,size,depth)=>{
      let n=0;
      for(const p of Object.keys(zip.files)){
        const m=p.match(/^assets\/([a-z0-9_.-]+)\/blockstates\/([a-z0-9_./-]+)\.json$/);
        if(m){this.catalog.add(m[1]+':'+m[2]);n++;}
      }
      found+=n;
      this.archives.push({name,zip,blocks:n,size});
      if(depth>=2)return;
      // Bundled Aeronautics may nest Simulated and Offroad in NeoForge JarJar.
      // These are data archives only. Never execute any uploaded Java class.
      const nested=Object.keys(zip.files).filter(p=>
        /(?:^|\/)(?:jarjar|jars)\/[^/]+\.jar$/i.test(p)).slice(0,20);
      for(const p of nested){
        const f=zip.file(p);
        if(!f||f._data?.uncompressedSize>MAX_FILE_SIZE)continue;
        try{
          const bytes=await f.async('uint8array');
          const embedded=await JSZip.loadAsync(bytes);
          await visit(name+'!/'+p,embedded,bytes.byteLength,depth+1);
        }catch(e){console.warn('Nested original mod JAR unreadable',p,e);}
      }
    };
    for(const file of files){
      if(!/\.(jar|zip)$/i.test(file.name)||file.size>MAX_FILE_SIZE)
        throw new Error('Only .jar/.zip under 180 MB: '+file.name);
      await visit(file.name,await JSZip.loadAsync(file),file.size,0);
    }
    this.generation++;
    this.jsonCache.clear();this.modelCache.clear();this.shapeCache.clear();
    return {newBlockstates:found,total:this.catalog.size,archives:this.archives.length};
  }
  async file(relativePath){
    for(let i=this.archives.length-1;i>=0;i--){
      const match=this.archives[i].zip.file(relativePath);
      if(match)return match;
    }
    return null;
  }
  async json(name,folder,namespace='minecraft'){
    const key=refPath(name,folder,namespace);
    if(!key)return null;
    if(this.jsonCache.has(key))return this.jsonCache.get(key);
    const file=await this.file(key);
    let result=null;
    if(file)try{result=JSON.parse(await file.async('text'));}catch(e){console.warn('Bad JSON resource',key,e);}
    this.jsonCache.set(key,result);
    return result;
  }
  async texture(path,ns){
    const filePath=refPath(path,'textures',ns);
    if(!filePath)return null;
    if(this.textureCache.has(filePath))return this.textureCache.get(filePath);
    const f=await this.file(filePath);
    if(!f){this.textureCache.set(filePath,null);return null;}
    const bytes=await f.async('uint8array');
    const blob=new Blob([bytes],{type:'image/png'}), url=URL.createObjectURL(blob);
    this.urls.push(url);
    try{
      const tex=await new THREE.TextureLoader().loadAsync(url);
      tex.magFilter=THREE.NearestFilter;tex.minFilter=THREE.NearestFilter;
      tex.generateMipmaps=false;tex.colorSpace=THREE.SRGBColorSpace;
      this.textureCache.set(filePath,tex);return tex;
    }catch(e){this.textureCache.set(filePath,null);return null;}
  }
  async model(name,seen=new Set(),namespace='minecraft'){
    const n=name.includes(':')?name:namespace+':'+name;
    if(seen.has(n))return null;seen.add(n);
    if(this.modelCache.has(n))return this.modelCache.get(n);
    const own=await this.json(n,'models');
    if(!own)return null;
    let parent=null;
    if(own.parent && !own.parent.startsWith('builtin/')){
      // Minecraft unqualified model references are minecraft namespaced.
      parent=await this.model(own.parent,seen,'minecraft');
    }
    const out={...parent,...own,textures:{...(parent?.textures||{}),...(own.textures||{})},
      elements:own.elements||parent?.elements||[]};
    if(!out.elements.length && /(?:^|:)block\/(?:cube_all|cube|cube_mirrored|cube_bottom_top|column)$/.test(own.parent||'')){
      out.elements=[BASE_CUBE];
    }
    this.modelCache.set(n,out);return out;
  }
  async variants(id,state){
    const data=await this.json(id,'blockstates');
    if(!data)return [];
    if(data.variants){
      let best=null,score=-1;
      for(const [key,value] of Object.entries(data.variants)){
        const args=key?key.split(',').map(p=>p.split('=')):[];
        if(args.every(([prop,val])=>String(state[prop]??'')===val)&&args.length>score){
          best=value;score=args.length;
        }
      }
      if(best===null)best=data.variants[''];
      return best?[Array.isArray(best)?best[0]:best]:[];
    }
    const result=[];
    for(const part of data.multipart||[]){
      if(!isWhenMatch(part.when,state))continue;
      const apply=part.apply;result.push(Array.isArray(apply)?apply[0]:apply);
    }
    return result;
  }
  async buildShape(id,state={}){
    const key=id+'|'+JSON.stringify(Object.entries(state).sort(([a],[b])=>a.localeCompare(b)));
    if(this.shapeCache.has(key))return this.shapeCache.get(key).clone(true);
    const variants=await this.variants(id,state);
    if(!variants.length)return null;
    const complete=new THREE.Group(),ns=id.split(':')[0];
    for(const v of variants){
      if(!v?.model)continue;
      const model=await this.model(v.model,new Set(),ns);
      if(!model?.elements?.length)continue;
      const assembled=new THREE.Group();
      for(const el of model.elements){
        if(!el.from||!el.to)continue;
        const elGroup=new THREE.Group();
        const center=new THREE.Vector3(...(el.rotation?.origin||[8,8,8])).divideScalar(16).addScalar(-.5);
        const from=el.from.map(n=>n/16-.5),to=el.to.map(n=>n/16-.5);
        for(const [side,face] of Object.entries(el.faces||{})){
          if(!FACE[side])continue;
          let texId=face.texture;
          for(let i=0;i<9&&texId?.startsWith('#');i++)texId=model.textures?.[texId.slice(1)];
          if(!texId)continue;
          const tex=await this.texture(texId,ns);
          if(!tex)continue;
          const vert=FACE[side].map(p=>[p[0]?to[0]:from[0],p[1]?to[1]:from[1],p[2]?to[2]:from[2]]);
          const arr=[...vert[0],...vert[1],...vert[2],...vert[1],...vert[3],...vert[2]];
          const uv=face.uv||[0,0,16,16];
          const u0=uv[0]/16,u1=uv[2]/16,v0=1-uv[1]/16,v1=1-uv[3]/16;
          const pts=[[u0,v1],[u1,v1],[u0,v0],[u1,v1],[u1,v0],[u0,v0]];
          const rotate=((face.rotation||0)/90)%4;
          const uvs=pts.flatMap(([u,v])=>{
             if(rotate===1)return [v,1-u];
             if(rotate===2)return [1-u,1-v];
             if(rotate===3)return [1-v,u];
             return [u,v];
          });
          const geo=new THREE.BufferGeometry();
          geo.setAttribute('position',new THREE.Float32BufferAttribute(arr,3));
          geo.setAttribute('uv',new THREE.Float32BufferAttribute(uvs,2));
          geo.computeVertexNormals();
          const mesh=new THREE.Mesh(geo,new THREE.MeshStandardMaterial({
            map:tex,roughness:.94,metalness:.03,alphaTest:.08,transparent:true,side:THREE.DoubleSide}));
          mesh.castShadow=true;mesh.receiveShadow=true;
          if(el.rotation?.angle){
            mesh.position.sub(center);
            elGroup.position.copy(center);
          }
          elGroup.add(mesh);
        }
        if(el.rotation?.angle){
          elGroup.rotation[el.rotation.axis]=THREE.MathUtils.degToRad(el.rotation.angle);
        }
        assembled.add(elGroup);
      }
      assembled.rotation.order='YXZ';
      assembled.rotation.set(THREE.MathUtils.degToRad(v.x||0),THREE.MathUtils.degToRad(v.y||0),0);
      complete.add(assembled);
    }
    if(!complete.children.length)return null;
    this.shapeCache.set(key,complete);
    return complete.clone(true);
  }
}
