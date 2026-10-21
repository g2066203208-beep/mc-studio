// Fast independent 3D fallback while a user-visible, licensed Create/Aeronautics
// distribution loads directly from its original publisher. ALL meshes here are
// newly generated procedural shapes; never pretend these are original MC models.
import * as THREE from 'three';

const materials={
  shell:new THREE.MeshStandardMaterial({color:0x927a60,roughness:.78,metalness:.1}),
  brass:new THREE.MeshStandardMaterial({color:0xc49c4d,roughness:.48,metalness:.55}),
  steel:new THREE.MeshStandardMaterial({color:0x74858d,roughness:.48,metalness:.65}),
  dark:new THREE.MeshStandardMaterial({color:0x25333a,roughness:.8,metalness:.25}),
  wood:new THREE.MeshStandardMaterial({color:0x74513a,roughness:.9}),
  sim:new THREE.MeshStandardMaterial({color:0x477989,roughness:.55,metalness:.4}),
  highlight:new THREE.MeshStandardMaterial({color:0x9cc0b9,roughness:.32,metalness:.42})
};
function box(parent,size,position,mat){
 const mesh=new THREE.Mesh(new THREE.BoxGeometry(...size),materials[mat]||mat);
 mesh.position.set(...position);parent.add(mesh);return mesh;
}
function tube(parent,r,depth,position,mat,axis='y',sides=16){
 const m=new THREE.Mesh(new THREE.CylinderGeometry(r,r,depth,sides),materials[mat]);
 m.position.set(...position);
 if(axis==='x')m.rotation.z=Math.PI/2;
 if(axis==='z')m.rotation.x=Math.PI/2;
 parent.add(m);return m;
}
function ring(parent,outer,inner,depth,position,material,axis='z'){
 const shape=new THREE.Shape();
 shape.absarc(0,0,outer,0,Math.PI*2,false);
 const hole=new THREE.Path();hole.absarc(0,0,inner,0,Math.PI*2,true);
 shape.holes.push(hole);
 const g=new THREE.ExtrudeGeometry(shape,{depth,bevelEnabled:false,curveSegments:32});
 g.translate(0,0,-depth/2);
 const mesh=new THREE.Mesh(g,materials[material]);
 mesh.position.set(...position);
 if(axis==='x')mesh.rotation.y=Math.PI/2;
 else if(axis==='y')mesh.rotation.x=Math.PI/2;
 parent.add(mesh);
}
function gear(parent,r,depth,position,mat,axis='y',teeth=14){
 const shape=new THREE.Shape();const n=teeth*4;
 for(let i=0;i<n;i++){
  const angle=i/n*Math.PI*2,rad=r*((i%4===1||i%4===2)?1.12:.91);
  const x=Math.cos(angle)*rad,y=Math.sin(angle)*rad;
  if(i===0)shape.moveTo(x,y);else shape.lineTo(x,y);
 }
 shape.closePath();
 const hole=new THREE.Path();hole.absarc(0,0,.11,0,Math.PI*2,true);shape.holes.push(hole);
 const geom=new THREE.ExtrudeGeometry(shape,{depth,steps:1,bevelEnabled:false});
 geom.translate(0,0,-depth/2);
 const mesh=new THREE.Mesh(geom,materials[mat]);mesh.position.set(...position);
 // Extruded teeth are in XY plane, center axis defaults Z.
 if(axis==='x')mesh.rotation.y=Math.PI/2;
 else if(axis==='y')mesh.rotation.x=Math.PI/2;
 parent.add(mesh);
 tube(parent,.12,depth+.06,position,'dark',axis);
}
function axisOf(item){
 if(item.id==='create:creative_motor'||item.id.includes('bearing')){
  return ({east:'x',west:'x',up:'y',down:'y',north:'z',south:'z'})[item.facing]||item.axis||'x';
 }
 return item.axis||'y';
}
function shaft(parent,item,length=.99){const ax=axisOf(item);tube(parent,.12,length,[0,0,0],'steel',ax,8);tube(parent,.18,.15,[0,0,0],'brass',ax,8)}
function base(parent,kind='shell'){
 box(parent,[.91,.67,.91],[0,-.135,0],kind);
 box(parent,[.99,.1,.99],[0,-.45,0],'dark');
 box(parent,[.75,.075,.75],[0,.225,0],'steel');
}
function drill(parent,axis){tube(parent,.2,.6,[0,0,0],'steel',axis);tube(parent,.31,.11,[0,0,0],'brass',axis)}
function rotor(parent,r=.37){
 gear(parent,r,.19,[0,0,0],'brass','z',12);
 tube(parent,.12,.65,[0,0,0],'steel','z');
}
function simulateBearing(parent){
 box(parent,[.92,.74,.9],[0,-.13,0],'sim');
 ring(parent,.36,.22,.15,[0,0,.47],'steel');
 ring(parent,.23,.12,.18,[0,0,.58],'brass');
 tube(parent,.12,.72,[0,0,0],'steel','z');
 for(const x of [-.32,.32])for(const y of [-.35,.19])tube(parent,.055,.13,[x,y,.5],'dark','z',8);
}
function sensor(parent){
 base(parent,'sim');ring(parent,.32,.24,.06,[0,.26,0],'brass','y');
 const globe=new THREE.Mesh(new THREE.SphereGeometry(.24,16,12),materials.highlight);
 globe.position.y=.32;parent.add(globe);
}
function pump(parent){
 box(parent,[.76,.76,.76],[0,0,0],'shell');
 ring(parent,.3,.18,.07,[0,0,.415],'brass');
 tube(parent,.16,1,[0,0,0],'steel','x');
}
function wheel(parent,large=false){
 const r=large?.47:.35;ring(parent,r,r*.76,.095,[0,0,0],'wood','x');
 tube(parent,.095,.3,[0,0,0],'steel','x');
 for(let i=0;i<8;i++){
  const a=i*Math.PI/4;
  const spoke=box(parent,[.35,.055,.055],[0,0,0],'brass');
  spoke.rotation.x=a;spoke.position.set(0,r*.45*Math.cos(a),r*.45*Math.sin(a));
 }
}
export function createPreviewModel(item){
 const id=item.id;const b=id.split(':').pop()||'',g=new THREE.Group();
 if(b==='shaft'||b.endsWith('_shaft'))shaft(g,item);
 else if(b.includes('cogwheel')||b.includes('gearshift')){
  const ax=axisOf(item),large=b.includes('large');
  gear(g,large?.43:.28,.19,[0,0,0],b.includes('brass')?'brass':'steel',ax,large?20:12);
  if(b.includes('encased')){base(g,b.includes('brass')?'brass':'shell');gear(g,large?.41:.26,.18,[0,.15,0],'steel',ax,large?18:12);}
 }
 else if(b.includes('motor')||b.includes('engine')){
  base(g,b.includes('steam')?'brass':'shell');
  tube(g,.32,.24,[0,.25,0],'brass');
  gear(g,.2,.13,[0,0,.49],'steel','z',10);shaft(g,item,.45);
 }
 else if(b.includes('swivel')||b.includes('bearing')||b.includes('physics_assembler'))simulateBearing(g);
 else if(b.includes('sensor')||b.includes('gimbal'))sensor(g);
 else if(b.includes('propeller')||b.includes('fan')){
  base(g,'sim');tube(g,.12,.48,[0,.21,0],'steel');
  for(let i=0;i<4;i++){
   const a=i*Math.PI/2;
   const blade=box(g,[.13,.065,.56],[.0,.45,.21],'brass');
   blade.rotation.y=a;blade.position.set(Math.sin(a)*.24,.45,Math.cos(a)*.24);
  }
 }
 else if(b.includes('wheel'))wheel(g,b.includes('large'));
 else if(b.includes('pump')||b.includes('fluid'))pump(g);
 else if(b.includes('link')||b.includes('wireless')||b.includes('transmission')){
  base(g,'sim');tube(g,.04,.62,[0,.43,0],'steel');
  for(let i=0;i<3;i++)ring(g,.13+i*.08,.117+i*.08,.018,[0,.43,0],'highlight','y');
 }
 else if(b.includes('press')||b.includes('mixer')||b.includes('crush')){
  base(g,'shell');
  for(const x of [-.37,.37])box(g,[.09,.9,.1],[x,.03,.32],'brass');
  box(g,[.64,.12,.6],[0,.35,.18],'steel');
  tube(g,.15,.65,[0,-.01,.2],'brass','y');
 }
 else if(b.includes('gearbox')||b.includes('clutch')||b.includes('casing')||b.includes('block')){
  base(g,b.includes('brass')?'brass':'shell');
  if(b.includes('gearbox')||b.includes('clutch')){for(const a of ['x','z'])tube(g,.13,1,[0,0,0],'steel',a)}
  for(const x of [-.36,.36])for(const z of [-.36,.36])box(g,[.1,.05,.1],[x,.24,z],'brass');
 }
 else{
  base(g,id.startsWith('simulated:')||id.startsWith('aeronautics:')?'sim':'shell');
  rotor(g);
 }
 // No vanilla texture is in this fallback; it is an original 3D preview,
 // explicitly superseded by the official JSON/PNG if loadable.
 return g;
}
