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
export const DT=1/60;
export const GRAVITY=-11;
export const LEG_COUNT=6;
export const JOINT_COUNT=12;
const UPPER_LENGTH=1.65,LOWER_LENGTH=1.75;
const HIP_Y=-.38;
export const LEG_COORDS=[-2,0,2].flatMap((z,i)=>[-1,1].map((side)=>({
 x:side*2.9,z,side,row:i,id:(side<0?'L':'R')+(i+1),
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
 const body=rb(RAPIER,world,0,4.55,0,{massive:true});
 const chest=cuboid(RAPIER,world,body,[5.6,1.15,5.3],[0,0,0],.40);
 const core=cuboid(RAPIER,world,body,[2.3,.85,2.3],[0,.84,0],.42);
 const nose=cuboid(RAPIER,world,body,[1.65,.65,1.1],[0,-.08,-3.0],.55);
 const motorSpecs=LEG_COORDS.map(config=>{
  const upper=rb(RAPIER,world,config.x,4.55+HIP_Y-UPPER_LENGTH/2,config.z);
  const lower=rb(RAPIER,world,config.x,4.55+HIP_Y-UPPER_LENGTH-LOWER_LENGTH/2,config.z);
  cuboid(RAPIER,world,upper,[.68,UPPER_LENGTH,.72],[0,0,0],1.0);
  cuboid(RAPIER,world,lower,[.53,LOWER_LENGTH,.64],[0,0,0],.90);
  const sole=cuboid(RAPIER,world,lower,[1.24,.38,1.18],
    [0,-LOWER_LENGTH/2-.17,0],1.95,2.15);
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
 let mode='stand',elapsed=0,steps=0,commandRPM=18,torque=1450,frequency=.76;
 let hipGain=1100,kneeGain=870,hipDamping=75,kneeDamping=63;
 let phase=0;
 const setMotor=({rpm,torqueLimit,stiffness,damping,period}={})=>{
  if(Number.isFinite(rpm))commandRPM=clamp(rpm,0,96);
  if(Number.isFinite(torqueLimit))torque=clamp(torqueLimit,0,5500);
  if(Number.isFinite(stiffness))hipGain=clamp(stiffness,0,4000);
  if(Number.isFinite(damping))hipDamping=clamp(damping,0,500);
  if(Number.isFinite(period))frequency=clamp(period,.1,3);
  kneeGain=hipGain*.79;kneeDamping=hipDamping*.84;
 };
 const setMode=value=>{
  if(!['stand','march','walk','relax'].includes(value))throw Error('Unknown physical control mode');
  mode=value;
  if(mode==='stand')phase=0;
 };
 const applyController=()=>{
  // Motor command = target relative hinge angle, NOT a visual pose.
  // Controlled by Create-like motor RPM and phase timings.
  if(mode==='walk'||mode==='march')phase+=DT*Math.PI*2*(commandRPM/60);
  for(const leg of motors){
   if(leg.detached)continue;
   const drive=mode==='walk'||mode==='march';
   const wave=Math.sin(phase+(leg.tripod?Math.PI:0));
   const lift=Math.max(0,wave);
   const stride=mode==='walk'?.44:.28;
   const hipT=drive?clamp(wave*stride,-.55,.55):0;
   const kneeT=drive?clamp(.09+.54*lift,0,.84):0;
   leg.hipTarget=hipT;leg.kneeTarget=kneeT;
   if(mode==='relax'||torque<.01){
    leg.hip.configureMotorPosition(0,0,0);
    leg.knee.configureMotorPosition(0,0,0);
    leg.hip.setMotorMaxForce(0);leg.knee.setMotorMaxForce(0);
   }else{
    leg.hip.configureMotorPosition(hipT,hipGain,hipDamping);
    leg.knee.configureMotorPosition(kneeT,kneeGain,kneeDamping);
    leg.hip.setMotorMaxForce(torque);
    leg.knee.setMotorMaxForce(torque*.85);
   }
  }
 };
 const allBodies=[body,...motors.flatMap(m=>[m.upper,m.lower])];
 const feetStatus=()=>motors.map(m=>{
  if(m.detached)return {id:m.id,connected:false};
  const p=m.lower.translation(),q=m.lower.rotation();
  const offset=-LOWER_LENGTH/2-.17, wy=p.y+offset*(1-2*(q.x*q.x+q.z*q.z))-.19;
  return {id:m.id,connected:true,estimatedSoleBottom:wy,touching:Math.abs(wy)<.12};
 });
 const snapshot=()=>{
  const feet=feetStatus();
  const nowPos=body.translation();
  return {
   solver:'Rapier WebAssembly',source:'actual-13-body-12-revolute-wasm',
   mode,elapsed:+elapsed.toFixed(2),steps,gravity:GRAVITY,
   mass:allBodies.reduce((a,b)=>a+b.mass(),0),
   bodyMass:body.mass(),
   feet:feet.filter(x=>x.connected).length,
   supportContacts:feet.filter(x=>x.touching).length,
   footMeasurements:feet,
   joints:motors.filter(x=>!x.detached).length*2,
   rigidBodies:allBodies.length,torqueLimit:torque,motorRPM:commandRPM,
   position:nowPos,velocity:body.linvel(),rotation:body.rotation(),
   bodyBottom:nowPos.y-.575,travel:nowPos.z,
   legAngles:motors.map(m=>({id:m.id,hip:getJointAngle(body,m.upper),knee:getJointAngle(m.upper,m.lower),
    hipTarget:m.hipTarget,kneeTarget:m.kneeTarget,detached:m.detached}))
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
  body.wakeUp();leg.upper.wakeUp();leg.lower.wakeUp();
  return true;
 };
 const impulse=(v)=>body.applyImpulseAtPoint(v,
  {x:body.translation().x,y:body.translation().y+.6,z:body.translation().z+1.4},true);
 const motorEnergy=()=>torque*Math.abs(commandRPM)*(mode==='relax'?0:1);
 return {RAPIER,world,ground,body,upperLength:UPPER_LENGTH,lowerLength:LOWER_LENGTH,
  motors,chest,core,nose,allBodies,advance,snapshot,removeLeg,
  setMode,setMotor,impulse,motorEnergy,
  get mode(){return mode},
  get torque(){return torque}
 };
}
