// Downloads current version-pinned JARs DIRECTLY from the official Modrinth
// distribution CDN inside the user's browser. The site stores/rehosts NOTHING.
// CORS/network limitations are reported, never hidden. Resource fallback is
// always an original procedural preview, NOT presented as an MC original.
const API='https://api.modrinth.com/v2';
const ALLOWED='https://cdn.modrinth.com';
export const OFFICIAL_RELEASES=[
 {slug:'create',version:'6.0.10',label:'Create 机械动力'},
 {slug:'create-aeronautics',version:'1.3.2',label:'Create Aeronautics / Simulated'}
];
async function requestWithTimeout(url,ms=85000){
 const control=new AbortController(), timer=setTimeout(()=>control.abort(),ms);
 try{
  const response=await fetch(url,{mode:'cors',credentials:'omit',signal:control.signal,cache:'force-cache'});
  if(!response.ok)throw Error('HTTP '+response.status+' '+url);
  return response;
 }finally{clearTimeout(timer);}
}
async function findVersion(project){
 const query=new URLSearchParams({loaders:'["neoforge"]',game_versions:'["1.21.1"]'});
 const response=await requestWithTimeout(API+'/project/'+project.slug+'/version?'+query,20000);
 const versions=await response.json();
 if(!Array.isArray(versions))throw Error('官方版本目录无效');
 const found=versions.filter(v=>v.version_number?.includes(project.version))
   .sort((a,b)=>(a.version_type==='release'?0:1)-(b.version_type==='release'?0:1));
 if(!found.length)throw Error('找不到 '+project.version+' 的 NeoForge 1.21.1 发布版本');
 const file=(found[0].files||[]).find(f=>f.primary)||(found[0].files||[])[0];
 if(!file)throw Error('没有发布包');
 const uri=new URL(file.url);
 if(uri.origin!==ALLOWED||!uri.pathname.startsWith('/data/'))
  throw Error('拒绝非官方 Modrinth CDN 文件源');
 return {url:uri.href,file,version:found[0].version_number};
}
async function verifiedDownload(info){
 const response=await requestWithTimeout(info.url,90000);
 const len=Number(response.headers.get('content-length')||0);
 if(len>90000000)throw Error('发布包超过 90 MB 安全上限');
 const bytes=await response.arrayBuffer();
 if(bytes.byteLength>90000000)throw Error('发布包超过 90 MB 安全上限');
 const expected=info.file.hashes?.sha512;
 if(expected&&globalThis.crypto?.subtle){
  const hash=await crypto.subtle.digest('SHA-512',bytes);
  const actual=[...new Uint8Array(hash)].map(b=>b.toString(16).padStart(2,'0')).join('');
  if(actual!==expected)throw Error('官方 JAR SHA-512 校验失败');
 }
 return new File([bytes],decodeURIComponent(new URL(info.url).pathname.split('/').at(-1)),{type:'application/java-archive'});
}
export async function loadOfficialModelArchives({onState,onFile,onFailed}){
 for(const project of OFFICIAL_RELEASES){
  try{
   onState?.({slug:project.slug,phase:'resolving',message:'查找官方 '+project.label+' '+project.version+' …'});
   const info=await findVersion(project);
   onState?.({slug:project.slug,phase:'downloading',message:'从官方 Modrinth 下载 '+project.label+' '+info.version+' 模型资源（首次需要网络）…'});
   const file=await verifiedDownload(info);
   onState?.({slug:project.slug,phase:'decoding',message:'解码原版 '+project.label+' 的 JSON/PNG 模型…'});
   await onFile?.(file,project,info);
   onState?.({slug:project.slug,phase:'ready',message:'原版 '+project.label+' 静态模型资源已就绪'});
  }catch(err){
   const message=err?.name==='AbortError'?'下载超时':err?.message||String(err);
   onState?.({slug:project.slug,phase:'failed',message:'无法自动载入 '+project.label+'：'+message+'；可手动导入本地 JAR'});
   onFailed?.(project,message);
  }
 }
}
