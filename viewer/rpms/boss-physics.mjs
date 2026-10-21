/**
 * Browser-native, ACTUAL WebAssembly rigid-body collision dynamics.
 * Same Rapier solver family as Sable, but not the Minecraft NeoForge runtime.
 * No upward magic forces / no setTranslation on a running rig.
 */
export const GRAVITY=-11;
export const DT=1/60;
export const BLOCKS=[
 // Top 3x3 physical torso at local Y=2.5 (nine visible voxel blocks).
 ...Array.from({length:9},(_,i)=>{
  const x=i%3-1,z=Math.floor(i/3)-1;
  return {name:'torso',x,y:2.5,z,density:2};
 }),
 // Four actual feet and supporting pillars.
 ...[-1,1].flatMap(x=>[-1,1].flatMap(z=>[
  {name:'pillar',x,y:1.5,z,density:1.375},
  {name:'foot',x,y:.5,z,density:1.375}
 ]))
];
export const SUPPORT_POSITIONS=[[-1,-1],[-1,1],[1,-1],[1,1]];
export function buildBossPhysics(RAPIER){
 if(!RAPIER?.World||!RAPIER?.RigidBodyDesc)throw Error('Rapier WASM unavailable');
 const world=new RAPIER.World({x:0,y:GRAVITY,z:0});
 world.integrationParameters.dt=DT;
 const ground=world.createCollider(RAPIER.ColliderDesc.cuboid(20,.5,20)
   .setTranslation(0,-.5,0).setFriction(1.1).setRestitution(0));
 const body=world.createRigidBody(RAPIER.RigidBodyDesc.dynamic()
   .setTranslation(0,4.6,0).setCanSleep(true).setLinearDamping(.035).setAngularDamping(.16));
 const pieces=BLOCKS.map((p,i)=>{
  const collider=world.createCollider(RAPIER.ColliderDesc.cuboid(.5,.5,.5)
   .setTranslation(p.x,p.y,p.z).setDensity(p.density)
   .setFriction(1.1).setRestitution(0),body);
  return {index:i,...p,collider,removed:false};
 });
 let elapsed=0,steps=0,removedCount=0;
 const pose=()=>({translation:body.translation(),rotation:body.rotation(),linear:body.linvel(),angular:body.angvel()});
 const floorGap=()=>{
  // The torso is 2 units above the boot soles; use local vertical axis
  // transformed by body quaternion (so tilting is represented honestly).
  const q=body.rotation(),p=body.translation();
  let lowest=Infinity;
  for(const piece of pieces){
   if(piece.removed)continue;
   const {x,y,z}=piece;
   // World-space Y projection of the quaternion rotation matrix.
   const ry=2*(q.x*q.y+q.w*q.z);
   const sy=1-2*(q.x*q.x+q.z*q.z);
   const tz=2*(q.y*q.z-q.w*q.x);
   const cy=p.y+ry*x+sy*y+tz*z;
   const halfExtent=.5*(Math.abs(ry)+Math.abs(sy)+Math.abs(tz));
   lowest=Math.min(lowest,cy-halfExtent);
  }
  return lowest;
 };
 const snapshot=()=>({
  solver:'Rapier WebAssembly',source:'browser-rigid-body-solver-not-NeoForge',
  elapsed:+elapsed.toFixed(2),steps,
  position:body.translation(),velocity:body.linvel(),rotation:body.rotation(),
  mass:body.mass(),feet:SUPPORT_POSITIONS.length-removedCount,
  bodyBottom:body.translation().y+2,
  footGap:floorGap(),
  blocks:pieces.filter(p=>!p.removed).length,
  contactState:Math.abs(floorGap())<.08&&Math.abs(body.linvel().y)<.23?'standing':
    body.linvel().y<-.16?'falling':'moving'
 });
 const advance=(n=1)=>{
  if(!Number.isInteger(n)||n<1||n>600)throw Error('Invalid simulation step count');
  for(let i=0;i<n;i++){world.step();elapsed+=DT;steps++;}
  return snapshot();
 };
 const removeLeg=(x,z)=>{
  if(![-1,1].includes(x)||![-1,1].includes(z))throw Error('Not one of four feet');
  const selected=pieces.filter(p=>!p.removed&&p.x===x&&p.z===z&&p.name!=='torso');
  if(!selected.length)return false;
  for(const p of selected){world.removeCollider(p.collider,true);p.removed=true;}
  removedCount++;
  body.wakeUp();
  return true;
 };
 const removeAllFeet=()=>{let count=0;for(const [x,z]of SUPPORT_POSITIONS)if(removeLeg(x,z))count++;return count;};
 const impulse=(x,y,z,atPoint=false)=>{
  if(atPoint)body.applyImpulseAtPoint({x,y,z},
   {x:body.translation().x+1.2,y:body.translation().y+2.5,z:body.translation().z},true);
  else body.applyImpulse({x,y,z},true);
 };
 return {RAPIER,world,ground,body,pieces,pose,snapshot,advance,removeLeg,removeAllFeet,impulse};
}
