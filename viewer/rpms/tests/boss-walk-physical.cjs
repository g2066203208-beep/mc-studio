const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,
  args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage();
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8033/rpms/boss-multibody-smoke.html',{waitUntil:'domcontentloaded'});
  await page.waitForFunction(()=>window.__MULTIBODY_READY__||window.__MULTIBODY_ERROR__,null,{timeout:90000});
  assert.ok(!(await page.evaluate(()=>window.__MULTIBODY_ERROR__)),'real solver initialization failed');
  const samples=await page.evaluate(()=>{
   const rig=window.__MULTIBODY_RIG__;
   const initial=rig.advance(240);
   rig.setMode('walk');
   rig.setMotor({rpm:32,torqueLimit:2200,stride:.45,clearance:.24});
   const measurements=[];
   for(let i=0;i<12;i++){
    const state=rig.advance(90);
    measurements.push({time:state.elapsed,forward:state.forwardDistance,bodyY:state.position.y,
      contacts:state.supportContacts,tripod:[state.groundedTripodA,state.groundedTripodB],
      swing:state.footMeasurements.filter(x=>x.gait==='swing'&&!x.touching).length,
      stance:state.footMeasurements.filter(x=>x.gait==='stance'&&x.touching).length,
      maxJointError:state.maxHingeError,angles:state.legAngles.map(x=>({
       id:x.id,hip:+x.hip.toFixed(2),target:+x.hipTarget.toFixed(2),knee:+x.knee.toFixed(2),
       phase:state.footMeasurements.find(f=>f.id===x.id)?.gait}))});
   }
   return {initial,final:rig.snapshot(),measurements};
  });
  console.log('BOSS_REAL_RAPIER_WALK_DIAGNOSTIC',JSON.stringify(samples));
  const {initial,final,measurements}=samples;
  assert.equal(final.rigidBodies,13);
  assert.equal(final.joints,12);
  assert.ok(measurements.every(x=>Number.isFinite(x.forward)&&Number.isFinite(x.bodyY)),
    'forward movement must be from actual Rapier solved body pose');
  assert.ok(measurements.every(x=>x.maxJointError<.3),'joint motor broke real hinge constraints');
  assert.ok(measurements.some(x=>x.swing>0),'NONE of the six feet actually left the ground in the swing phase');
  assert.ok(measurements.every(x=>x.contacts>=2),'lost all support while supposed to walk');
  assert.ok(measurements.every(x=>x.bodyY>4.65&&x.bodyY<6.0),'leg support was lost or levitated');
  const forward=final.forwardDistance-initial.forwardDistance;
  assert.ok(forward>.30,
   'Boss did NOT achieve meaningful NET FORWARD TRAVEL with real footsteps; '+forward+' blocks after 18 sec');
  assert.ok(final.powertrain.connectedBranches===6,'motor power network must actually drive all hips');
  assert.equal(errors.length,0,'Browser errors '+errors.join('\n'));
  console.log('PASS REAL BODY FORWARD TRAVEL: '+forward.toFixed(3)+
   ' blocks in 18s, with Rapier feet contact, joint torque, no body teleports');
 }finally{await browser.close();}
})().catch(e=>{console.error('BOSS_REAL_WALK_FAILED',e.stack||e);process.exit(1)});
