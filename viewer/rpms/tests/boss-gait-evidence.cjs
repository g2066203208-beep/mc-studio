const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,
  args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage();
  page.on('pageerror',e=>console.error('PAGE_ERROR',e.message));
  await page.goto('http://127.0.0.1:8033/rpms/boss-multibody-smoke.html',{waitUntil:'domcontentloaded'});
  await page.waitForFunction(()=>window.__MULTIBODY_READY__||window.__MULTIBODY_ERROR__,null,{timeout:90000});
  const initError=await page.evaluate(()=>window.__MULTIBODY_ERROR__||null);
  assert.equal(initError,null,'Real Rapier joint initialization');
  const results=await page.evaluate(()=>{
   const rig=window.__MULTIBODY_RIG__;
   rig.advance(240);
   const rest=rig.snapshot();
   rig.setMode('walk');
   rig.setMotor({rpm:48,torqueLimit:2200,ratio:2});
   const sequence=[];
   for(let i=0;i<12;i++){
    const s=rig.advance(60);
    sequence.push({t:s.elapsed,z:s.position.z,x:s.position.x,y:s.position.y,
      vy:s.velocity.y,contacts:s.supportContacts,
      maxFootClearance:Math.max(...s.footMeasurements.filter(f=>f.connected).map(f=>f.estimatedSoleBottom)),stance:s.gait.stance,
      swing:s.gait.swing,maxHingeError:s.maxHingeError,
      hip:s.legAngles[0].hip,hipTarget:s.legAngles[0].hipTarget,
      knee:s.legAngles[0].knee,kneeTarget:s.legAngles[0].kneeTarget});
   }
   const end=rig.snapshot();
   return {rest:{z:rest.position.z,y:rest.position.y},end:{z:end.position.z,y:end.position.y},
    deltaZ:end.position.z-rest.position.z,sequence};
  });
  console.log('BOSS_REAL_WALK_DISTANCE_EVIDENCE',JSON.stringify(results));
  assert.ok(results.sequence.every(v=>Number.isFinite(v.z)&&Number.isFinite(v.y)),
    'NaN / unstable physical simulation');
  assert.ok(results.sequence.every(v=>v.maxHingeError<.35),
    'Joint anchors disconnected / visual limbs will separate');
  assert.ok(results.sequence.some(v=>Math.abs(v.knee)>.10),
    'Knees do not actually bend under Rapier solved torque');
  assert.ok(results.sequence.some(v=>v.maxFootClearance>.10),
    'No foot physically lifts 0.10 blocks above the real Rapier ground, so cannot walk');
  assert.ok(results.sequence.every(v=>v.contacts>=3),
    'Tripod walking must keep at least three true collision-height ground supports');
  assert.ok(results.sequence.every(v=>v.y>3.2&&v.y<8),
    'Body falls or flies while walking');
  assert.ok(Math.abs(results.deltaZ)>.22,'NO MEASURABLE WALKING - body must move >0.22 block by legs alone, not a preview animation');
  console.log('PASS: motor-powered 13-body robot advances on world ground; distance and gait tracked from actual Rapier pose.');
 }finally{await browser.close()}
})().catch(err=>{console.error('BOSS_WALK_REAL_EVIDENCE_FAILED',err.stack||err);process.exit(1)});
