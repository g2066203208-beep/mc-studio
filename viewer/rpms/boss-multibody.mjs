/**
 * P1: real articulated mechanics for the GitHub Pages Boss workbench.
 * 13 INDEPENDENT Rapier rigid bodies: chassis + 6 thighs + 6 shins.
 * 12 actual revolute constraints with position/velocity servomotors, real
 * masses, torques, joint limit stops, floor friction and collisions.
 * The Create-like gears are visual powertrain meshes, not a claim that
 * NeoForge Create and Simulated JAR code runs in the browser.
 *
 * NO kinematic body motion, per-frame world-space body translation, fake
 * animation pose setting, anti-gravity, or hidden leg-independent thrust.
 */
import {buildCreatePowerBus} from './boss-powertrain.mjs';
import {planTripodFoot} from './boss-gait.mjs';
export const DT=1/60;
export const GRAVITY=-11;
export const LEG_COUNT=6;
export const JOINT_COUNT=12;
// Physical hinge centers exactly coincide with the 2-voxel upper and lower limbs.
const UPPER_LENGTH=2,LOWER_LENGTH=2;
const HIP_Y=-.38;
export const LEG_COORDS=[-3,0,3].flatMap((z,i)=>[-1,1].map((side)=>({
 x:side*3,z,side,row:i,id:(side<0?'L':'R')+(i+1),
 tripod:(i+(side===1?1:0))%2
})));
const makeV=(x=0,y=0,z=0)=>({x,y,z});
const clamp=(x,a,b)=>Math.min(b,Math.max(a,x));
function rb(RAPIER,world,x,y,z,{massive=false}={}){
 const d=RAPIER.RigidBodyDesc.dynamic().setTranslation(x,y,z)
  .setLinearDamping(massive?.12:.17).setAngularDamping(massive?.34:.2)
  .setCanSleep(false);
 return world.createRigidBody(d);
}
function cuboid(RAPIER,world,body,dim,loc,density,friction=1.5){
 return world.createCollider(
  RAPIER.ColliderDesc.cuboid(...dim.map(v=>v/2))
   .setTranslation(...loc)
   .setDensity(density)
   .setFriction(friction)
   .setRestitution(0),body);
}
function getJointAngle(a,b){
 // Measured relative X hinge rotation from the solved two body quaternions.
 const p=a.rotation(),q=b.rotation();
 const rx=p.w*q.x-p.x*q.w-p.y*q.z+p.z*q.y;
 const rw=p.w*q.w+p.x*q.x+p.y*q.y+p.z*q.z;
 return 2*Math.atan2(rx,rw);
}
const safeFinite=v=>Number.isFinite(v)?v:0;
// Measure the true solver-space seam gap between each pair of hinges.
// Rendering a neatly arranged Minecraft cube does NOT prove its physical
// joint is there; check the transformed endpoints on the two real bodies.
function jointWorldPoint(body,v){
 const q=body.rotation(),p=body.translation();
 const tx=2*(q.y*v.z-q.z*v.y);
 const ty=2*(q.z*v.x-q.x*v.z);
 const tz=2*(q.x*v.y-q.y*v.x);
 return makeV(
  p.x+v.x+q.w*tx+(q.y*tz-q.z*ty),
  p.y+v.y+q.w*ty+(q.z*tx-q.x*tz),
  p.z+v.z+q.w*tz+(q.x*ty-q.y*tx)
 );
}
function seamError(a,b){return Math.hypot(a.x-b.x,a.y-b.y,a.z-b.z);}

export function createMultibodyBoss(RAPIER,opts={}){
 if(!RAPIER?.JointData?.revolute||!RAPIER?.World)throw Error('Rapier 3D revolute joint API unavailable');
 const world=new RAPIER.World(makeV(0,GRAVITY,0));
 world.integrationParameters.dt=DT;
 // Extra velocity/position iterations for stacked hinged support without
 // unrealistic stabilizing impulses injected into the body.
 if('numSolverIterations'in world.integrationParameters)world.integrationParameters.numSolverIterations=8;
 if('numAdditionalFrictionIterations'in world.integrationParameters)world.integrationParameters.numAdditionalFrictionIterations=6;
 const ground=world.createCollider(RAPIER.ColliderDesc.cuboid(50,.5,50)
  .setTranslation(0,-.5,0).setFriction(1.75).setRestitution(0));
 // Larger-than-player six-leg machine. All numbers in Minecraft blocks/metres.
 // Pixel-perfect voxel chassis: same integer block cells used by the renderer.
 const body=rb(RAPIER,world,0,6.18,0,{massive:true});
 const chest=[];
 for(let x=-2;x<=2;x++)for(let z=-3;z<=3;z++)
  chest.push(cuboid(RAPIER,world,body,[1,1,1],[x,0,z],.35));
 const core=cuboid(RAPIER,world,body,[1,1,1],[0,1,0],.42);
 for(const x of [-2,2])for(const z of [-3,0,3])
  cuboid(RAPIER,world,body,[1,1,1],[x,1,z],.27);
 const nose=cuboid(RAPIER,world,body,[1,1,1],[0,0,-4],.50);
 const motorSpecs=LEG_COORDS.map(config=>{
  const upper=rb(RAPIER,world,config.x,6.18+HIP_Y-UPPER_LENGTH/2,config.z);
  const lower=rb(RAPIER,world,config.x,6.18+HIP_Y-UPPER_LENGTH-LOWER_LENGTH/2,config.z);
  // Each visible integer-lattice block has an EXACT matching 1×1×1 Rapier
  // collider. This fixes the old 1.65/1.75m collider vs 2-block visual error.
  for(const y of [-.5,.5]){
   cuboid(RAPIER,world,upper,[1,1,1],[0,y,0],.38);
   cuboid(RAPIER,world,lower,[1,1,1],[0,y,0],.30);
  }
  const sole=cuboid(RAPIER,world,lower,[1,1,1],[0,-1.5,0],1.05,2.15);
  const axis=makeV(1,0,0);
  const hip=world.createImpulseJoint(
   RAPIER.JointData.revolute(
    makeV(config.x,HIP_Y,config.z),makeV(0,UPPER_LENGTH/2,0),axis),body,upper,true);
  const knee=world.createImpulseJoint(
   RAPIER.JointData.revolute(
    makeV(0,-UPPER_LENGTH/2,0),makeV(0,LOWER_LENGTH/2,0),axis),upper,lower,true);
  hip.setContactsEnabled(false);knee.setContactsEnabled(false);
  hip.setLimits(-.88,.88);knee.setLimits(-1.25,1.25);
  if(typeof hip.configureMotorPosition!=='function')
   throw Error('Rapier WASM build lacks joint servo API');
  const rec={...config,upper,lower,hip,knee,sole,hipTarget:0,kneeTarget:0,
   hipAngle:0,kneeAngle:0,detached:false};
  return rec;
 });
 const motors=motorSpecs;
 let mode='stand',elapsed=0,steps=0,commandRPM=18,torque=135,gearRatio=2,frequency=.76;
 // The controller MAY NOT create actuator torque unless Create's integer
 // grid shaft/cog/clutch graph actually reaches this physical joint.
 const disconnected=new Set();
 let powerBus=buildCreatePowerBus({rpm:commandRPM,gearRatio});
 const refreshBus=()=>{powerBus=buildCreatePowerBus({
  rpm:commandRPM,gearRatio,enabled:mode!=='relax',disconnected:[...disconnected]
 });return powerBus;};
 const disconnectBranch=(id,off=true)=>{
  if(!LEG_COORDS.some(x=>x.id===id))throw Error('Unknown motor branch '+id);
  if(off)disconnected.add(id);else disconnected.delete(id);
  refreshBus();
 };
 const allDriveStatus=()=>powerBus.bearings.map(b=>({id:b.id,connected:b.pathLive,
  rpm:b.outputRPM,sourceRPM:b.rpm,torqueMultiplier:b.torqueMultiplier,
  clutchPowered:disconnected.has(b.id)}));

 // Keep the measured-stable low-impulse PD gains. Overdriving the
 // motor caused large ballistic hops in the real Rapier integration.
 let hipGain=115,kneeGain=92,hipDamping=15,kneeDamping=13;
 let phase=0,stepStride=.45,stepClearance=.24;
 const setMotor=({rpm,torqueLimit,stiffness,damping,period,ratio,stride,clearance}={})=>{
  if(Number.isFinite(stride))stepStride=clamp(stride,.10,.75);
  if(Number.isFinite(clearance))stepClearance=clamp(clearance,.05,.40);
  if([1,2,4].includes(Number(ratio)))gearRatio=Number(ratio);
  if(Number.isFinite(rpm))commandRPM=clamp(rpm,0,96);
  if(Number.isFinite(torqueLimit))torque=clamp(torqueLimit,0,5500);
  if(Number.isFinite(stiffness))hipGain=clamp(stiffness,0,4000);
  if(Number.isFinite(damping))hipDamping=clamp(damping,0,500);
  if(Number.isFinite(period))frequency=clamp(period,.1,3);
  kneeGain=hipGain*.79;kneeDamping=hipDamping*.81;
  refreshBus();
 };
 const setMode=value=>{
  if(!['stand','march','walk','relax'].includes(value))throw Error('Unknown physical control mode');
  mode=value;
  if(mode==='stand')phase=0;
  refreshBus(); // "断电" actually cuts mechanical power; no decorative free source.
 };
 const servoTorque=(parent,child,target,kp,kd,limit)=>{
  // Rapier 0.17.3's motor maximum-force setter is not exposed in WASM.
  // Enforce a HARD physical torque cap ourselves via equal-and-opposite
  // angular impulses. These are REAL torques on separate dynamic bodies,
  // not joint-angle teleports or animation transforms.
  const q=parent.rotation();
  const axis=makeV(1-2*(q.y*q.y+q.z*q.z),
    2*(q.x*q.y+q.w*q.z),2*(q.x*q.z-q.w*q.y));
  const a=parent.angvel(),b=child.angvel();
  const omega=(b.x-a.x)*axis.x+(b.y-a.y)*axis.y+(b.z-a.z)*axis.z;
  const angle=getJointAngle(parent,child);
  const raw=kp*clamp(target-angle,-1.5,1.5)-kd*omega;
  const torqueImpulse=clamp(raw,-limit,limit)*DT;
  if(!torqueImpulse||!Number.isFinite(torqueImpulse))return;
  const v=makeV(axis.x*torqueImpulse,axis.y*torqueImpulse,axis.z*torqueImpulse);
  child.applyTorqueImpulse(v,true);
  parent.applyTorqueImpulse(makeV(-v.x,-v.y,-v.z),true);
 };
 const applyController=()=>{
  // Motor command = target relative hinge angle, NOT a visual pose.
  // Controlled by Create-like motor RPM and phase timings.
  const motorSpeed=powerBus.bearings.find(b=>b.pathLive)?.outputRPM||0;
  if((mode==='walk'||mode==='march')&&motorSpeed)
   phase+=DT*Math.PI*2*(Math.abs(motorSpeed)/60);
  for(const leg of motors){
   if(leg.detached)continue;
   const drive=mode==='walk'||mode==='march';
   const wave=Math.sin(phase+(leg.tripod?Math.PI:0));
   const lift=Math.max(0,wave);
   // Two tripod groups alternate: the STANCE tripod stays straight,
   // so body mass is supported by grounded collision boxes. Only the
   // SWING tripod bends its knee; the original .54 bend on a lagging
   // joint shortened both supports and left all soles in the air.
   const trajectory=mode==='walk'
    ?planTripodFoot(phase/(Math.PI*2),leg.tripod,{stride:stepStride,clearance:stepClearance})
    :null;
   const stride=mode==='walk'?.28:.18;
   const hipT=trajectory?trajectory.hip:drive?clamp(wave*stride,-.4,.4):0;
   const kneeT=trajectory?trajectory.knee:drive?clamp(.38*lift,0,.43):0;
   // Keep physical servo targets smooth on a large inertial rig.
   // Jumping straight from upright locked knees to a full swing created
   // ballistic tipping even though the joint anchors remained intact.
   const maxChange=mode==='walk'?.011:Infinity;
   leg.hipTarget=clamp(hipT,leg.hipTarget-maxChange,leg.hipTarget+maxChange);
   leg.kneeTarget=clamp(kneeT,leg.kneeTarget-maxChange,leg.kneeTarget+maxChange);
   leg.gaitPhase=trajectory?.phase||(drive?(lift>0?'swing':'stance'):'hold');
   leg.footTarget=trajectory?.target||null;
   const branch=powerBus.bearings.find(o=>o.id===leg.id);
   // The Create source/shaft/gear/clutch graph is AUTHORITATIVE within its
   // supported subset. A broken/disabled branch creates NO hinge torque.
   const budget=branch?.pathLive ? torque*branch.torqueMultiplier : 0;
   leg.deliveredTorque=budget;
   if(mode!=='relax'&&budget>0){
    servoTorque(body,leg.upper,leg.hipTarget,hipGain,hipDamping,budget);
    // A powered knee servo mounted on the upper/lower leg is a separate
    // simplified electrical/mechanical coupling, NOT a proven Create shaft
    // through a moving SubLevel. Explicitly report that limitation in UI.
    servoTorque(leg.upper,leg.lower,leg.kneeTarget,kneeGain,kneeDamping,budget*.85);
   }
  }
 };
 const allBodies=[body,...motors.flatMap(m=>[m.upper,m.lower])];
 const feetStatus=()=>motors.map(m=>{
  if(m.detached)return {id:m.id,connected:false};
  const q=m.lower.rotation();
  const bottomCenter=jointWorldPoint(m.lower,makeV(0,-1.5,0));
  const worldYBasis=makeV(2*(q.x*q.y+q.w*q.z),1-2*(q.x*q.x+q.z*q.z),
     2*(q.y*q.z-q.w*q.x));
  const halfExtent=.5*(Math.abs(worldYBasis.x)+Math.abs(worldYBasis.y)+Math.abs(worldYBasis.z));
  const worldFootBottom=bottomCenter.y-halfExtent;
  // Read actual collision manifolds against the REAL Rapier floor, rather
  // than labeling every foot within 0.12 blocks as touching.
  let contact=false,contactCount=0;
  world.narrowPhase.contactPair(m.sole.handle,ground.handle,manifold=>{
   const n=manifold.numContacts();if(n>0){contact=true;contactCount+=n;}
  });
  return {id:m.id,connected:true,estimatedSoleBottom:worldFootBottom,
   touching:contact,contacts:contactCount,gait:m.gaitPhase||'hold',
   desiredFoot:m.footTarget||null};
 });
 const snapshot=()=>{
  const feet=feetStatus();
  const nowPos=body.translation();
  const hingeErrors=motors.filter(m=>!m.detached).map(m=>({
   id:m.id,
   hip:seamError(
    jointWorldPoint(body,makeV(m.x,HIP_Y,m.z)),
    jointWorldPoint(m.upper,makeV(0,UPPER_LENGTH/2,0))),
   knee:seamError(
    jointWorldPoint(m.upper,makeV(0,-UPPER_LENGTH/2,0)),
    jointWorldPoint(m.lower,makeV(0,LOWER_LENGTH/2,0)))
  }));
  const maxHingeError=Math.max(0,...hingeErrors.flatMap(e=>[e.hip,e.knee]));
  return {
   solver:'Rapier WebAssembly',source:'actual-13-body-12-revolute-wasm',
   mode,elapsed:+elapsed.toFixed(2),steps,gravity:GRAVITY,
   mass:allBodies.reduce((a,b)=>a+b.mass(),0),
   powertrain:{source:powerBus.motor,sourcePowered:powerBus.motor.powered,
    connectedBranches:powerBus.bearings.filter(b=>b.pathLive).length,
    inputs:allDriveStatus(),blocks:powerBus.blocks.length,
    links:powerBus.graph.summary.links,capacitySU:powerBus.capacitySU,
    loadSU:powerBus.loadSU,overloaded:powerBus.overloaded,
    assumptions:powerBus.assumptions},
   bodyMass:body.mass(),
   feet:feet.filter(x=>x.connected).length,
   supportContacts:feet.filter(x=>x.touching).length,
   footMeasurements:feet,
   joints:motors.filter(x=>!x.detached).length*2,
   hingeErrors,maxHingeError,
   modelUnits:{upperVoxelLength:UPPER_LENGTH,lowerVoxelLength:LOWER_LENGTH,footCenterY:-1.5,hipGridX:3},
   rigidBodies:allBodies.length,torqueLimit:torque,motorRPM:commandRPM,
   gearRatio,outputRPM:powerBus.bearings.find(b=>b.pathLive)?.outputRPM||0,
   outputTorqueLimit:torque*gearRatio*.9,
   position:nowPos,velocity:body.linvel(),rotation:body.rotation(),
   bodyBottom:nowPos.y-.5,travel:nowPos.z,forwardDistance:-nowPos.z,
   gaitCycle:phase/(2*Math.PI),stepStride,stepClearance,
   groundedTripodA:feet.filter(x=>x.touching&&motors.find(m=>m.id===x.id)?.tripod===0).length,
   groundedTripodB:feet.filter(x=>x.touching&&motors.find(m=>m.id===x.id)?.tripod===1).length,
   legAngles:motors.map(m=>({id:m.id,hip:getJointAngle(body,m.upper),knee:getJointAngle(m.upper,m.lower),
    hipTarget:m.hipTarget,kneeTarget:m.kneeTarget,
    deliveredTorque:m.deliveredTorque||0,detached:m.detached}))
  };
 };
 const advance=(n=1)=>{
  if(!Number.isInteger(n)||n<1||n>600)throw Error('Invalid physics step count');
  for(let i=0;i<n;i++){
   applyController();
   world.step();
   elapsed+=DT;steps++;
  }
  return snapshot();
 };
 const removeLeg=id=>{
  const leg=motors.find(m=>m.id===id);
  if(!leg||leg.detached)return false;
  // Break joints, free the THIGH and SHIN as genuinely independent
  // dynamic debris; no deleting support geometry / imaginary detach.
  world.removeImpulseJoint(leg.hip,true);
  world.removeImpulseJoint(leg.knee,true);
  leg.detached=true;
  disconnectBranch(id,true); // destroyed hip motor network branch goes dead
  body.wakeUp();leg.upper.wakeUp();leg.lower.wakeUp();
  return true;
 };
 const impulse=(v)=>body.applyImpulseAtPoint(v,
  {x:body.translation().x,y:body.translation().y+.6,z:body.translation().z+1.4},true);
 const motorEnergy=()=>torque*Math.abs(commandRPM)*powerBus.live/6;
 return {RAPIER,world,ground,body,upperLength:UPPER_LENGTH,lowerLength:LOWER_LENGTH,
  motors,chest,core,nose,allBodies,advance,snapshot,removeLeg,
  setMode,setMotor,disconnectBranch,allDriveStatus,impulse,motorEnergy,
  get powerBus(){return powerBus},
  get mode(){return mode},
  get torque(){return torque}
 };
}
