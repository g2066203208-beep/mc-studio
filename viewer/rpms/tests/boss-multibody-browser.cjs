const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,
  args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage();
  page.on('pageerror',e=>console.log('PAGE_ERROR',e.stack||e.message));
  const r=await page.goto('http://127.0.0.1:8033/rpms/boss-multibody-smoke.html',{waitUntil:'domcontentloaded'});
  assert.equal(r.status(),200);
  await page.waitForFunction(()=>window.__MULTIBODY_READY__||window.__MULTIBODY_ERROR__,null,{timeout:100000});
  const err=await page.evaluate(()=>window.__MULTIBODY_ERROR__);
  assert.ok(!err,'Physical body/joint creation failed: '+err);
  const initial=await page.evaluate(()=>window.__MULTIBODY_RIG__.snapshot());
  console.log('ARTICULATED_INITIAL',JSON.stringify(initial));
  assert.equal(initial.rigidBodies,13,'one heavy chassis + six upper and six lower legs');
  assert.equal(initial.joints,12,'all twelve real hinges required');
  assert.equal(initial.feet,6,'six load-bearing feet');
  assert.ok(initial.mass>20,'mechanical Boss has real structural mass');
  const standing=await page.evaluate(()=>window.__MULTIBODY_RIG__.advance(240));
  console.log('ARTICULATED_STAND',JSON.stringify(standing));
  assert.ok(Number.isFinite(standing.bodyBottom),'physics must return a body height');
  assert.ok(standing.position.y>1.4&&standing.position.y<7,'chassis must remain in a physically reasonable range');
  assert.ok(Math.abs(standing.velocity.y)<3,'servo support must not catapult the chassis');
  const moving=await page.evaluate(()=>{
   const rig=window.__MULTIBODY_RIG__;
   rig.setMode('march');rig.setMotor({rpm:28,torqueLimit:2200});
   return rig.advance(240);
  });
  console.log('ARTICULATED_MARCH',JSON.stringify(moving));
  assert.equal(moving.joints,12);
  const range=Math.max(...moving.legAngles.map(x=>Math.abs(x.hip)));
  assert.ok(range>.04,'HINGES MUST ROTATE REAL INDEPENDENT RIGID BODIES, not display-only target: '+range);
  assert.ok(moving.legAngles.some(x=>Math.abs(x.hipTarget)>.05),'tripod gait controller not operating');
  const detached=await page.evaluate(()=>{
   const rig=window.__MULTIBODY_RIG__;rig.removeLeg('L1');return rig.advance(80);
  });
  console.log('ARTICULATED_DETACH',JSON.stringify(detached));
  assert.equal(detached.joints,10,'physical destruction must remove two actual constraints');
  assert.equal(detached.feet,5);
  console.log('PASS BOSS MULTIBODY real 13 bodies, 12 revolute joints, finite-torque actuator gait, actual angles and physical leg detachment');
 }finally{await browser.close()}
})().catch(e=>{console.error('MULTIBODY_REAL_PHYSICS_FAILED',e.stack||e);process.exit(1)});
