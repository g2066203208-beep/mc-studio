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
  assert.ok(standing.position.y>3.85&&standing.position.y<4.4,'legs must ACTUALLY hold the heavy chassis off the floor: '+standing.position.y);
  assert.ok(Math.abs(standing.velocity.y)<.1,'actual loaded six-leg platform did not settle under gravity');
  assert.ok(Math.abs(standing.position.x)<.25&&Math.abs(standing.position.z)<.25,
    'standing must not drift around as a runaway body');
  assert.ok(standing.supportContacts>=4,'at least four feet must touch real Rapier static world floor');
  assert.ok(standing.legAngles.every(x=>Math.abs(x.hip)<.09&&Math.abs(x.knee)<.09),
    'motor must HOLD actual joint angles while chassis weight bears on feet');
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
  assert.ok(moving.legAngles.filter(x=>Math.abs(x.knee)>.15).length>=2,
    'gait cannot only spin decorative Create gears; actual Rapier knee bodies must bend');
  const detached=await page.evaluate(()=>{
   const rig=window.__MULTIBODY_RIG__;rig.removeLeg('L1');return rig.advance(80);
  });
  console.log('ARTICULATED_DETACH',JSON.stringify(detached));
  assert.equal(detached.joints,10,'physical destruction must remove two actual constraints');
  assert.equal(detached.feet,5);
  console.log('PASS BOSS MULTIBODY real 13 bodies, 12 revolute joints, finite-torque actuator gait, actual angles and physical leg detachment');
  const site=await browser.newPage({viewport:{width:1520,height:1020},deviceScaleFactor:1});
  const pageErrors=[];
  site.on('pageerror',e=>pageErrors.push(e.stack||e.message));
  await site.goto('http://127.0.0.1:8033/rpms/boss.html?autoload=off',
    {waitUntil:'domcontentloaded',timeout:60000});
  await site.waitForFunction(()=>window.__BOSS_API__?.ready||window.__BOSS_API__?.error,null,{timeout:90000});
  const siteError=await site.evaluate(()=>window.__BOSS_API__.error);
  assert.equal(siteError,null,'Hosted robot must initialize true WebAssembly articulations: '+siteError);
  const status=await site.evaluate(()=>({
    snapshot:window.__BOSS_API__.snapshot(),
    meshes:window.__BOSS_API__.visibleMeshes(),
    bodies:window.__BOSS_API__.physicsBodies(),
    assets:window.__BOSS_API__.getAssetStatus()
  }));
  console.log('ARTICULATED_WEB_VISUAL',JSON.stringify(status));
  assert.equal(status.snapshot.rigidBodies,13);
  assert.equal(status.snapshot.joints,12);
  assert.equal(status.bodies,13,'every physical body must have its own separately synchronized visual group');
  assert.ok(status.meshes>65,'Not a true gear and bearing machine; insufficient visible mechanical components: '+status.meshes);
  assert.ok(status.assets.components>=35,'Must show Create gears, motors, shafts, Simulated bearings');
  await site.screenshot({path:require('node:path').join(__dirname,'boss-articulated-desktop.png'),fullPage:true});
  await site.locator('#march').click();
  assert.equal(await site.evaluate(()=>window.__BOSS_API__.snapshot().mode),'march');
  const move=await site.evaluate(()=>window.__BOSS_API__.advance(180));
  assert.ok(move.legAngles.some(x=>Math.abs(x.hip)>.03),'physical visual must reflect true motor-driven hinge bending');
  assert.equal(await site.locator('#jointNum').textContent(),'13 / 12');
  await site.screenshot({path:require('node:path').join(__dirname,'boss-articulated-marching.png'),fullPage:true});
  await site.locator('#removeOne').click();
  assert.equal(await site.evaluate(()=>window.__BOSS_API__.snapshot().joints),10);
  assert.equal(await site.evaluate(()=>window.__BOSS_API__.snapshot().feet),5);
  await site.locator('#relax').click();
  assert.equal(await site.evaluate(()=>window.__BOSS_API__.snapshot().mode),'relax');
  await site.locator('#restart').click();
  assert.equal(await site.evaluate(()=>window.__BOSS_API__.snapshot().joints),12);
  await site.locator('#motorTorque').evaluate(el=>{el.value='0';el.dispatchEvent(new Event('input',{bubbles:true}));});
  assert.equal(await site.evaluate(()=>window.__BOSS_API__.snapshot().torqueLimit),0);
  assert.equal(pageErrors.length,0,'Hosted robot threw an error: '+pageErrors.join('\n'));
  console.log('PASS ARTICULATED HOSTED BOSS: 13 rendered dynamic rigid-bodies, Create machinery, twelve physical powered joints and breakable limbs');
  const mobile=await browser.newPage({viewport:{width:393,height:852},deviceScaleFactor:1,isMobile:true});
  const mobileErrors=[];mobile.on('pageerror',e=>mobileErrors.push(e.message));
  await mobile.goto('http://127.0.0.1:8033/rpms/boss.html?autoload=off',{waitUntil:'domcontentloaded'});
  await mobile.waitForFunction(()=>window.__BOSS_API__?.ready||window.__BOSS_API__?.error,null,{timeout:90000});
  assert.equal(await mobile.evaluate(()=>window.__BOSS_API__.error),null);
  await mobile.screenshot({path:require('node:path').join(__dirname,'boss-articulated-mobile.png'),fullPage:true});
  assert.equal(mobileErrors.length,0);
  await mobile.close();

 }finally{await browser.close()}
})().catch(e=>{console.error('MULTIBODY_REAL_PHYSICS_FAILED',e.stack||e);process.exit(1)});
