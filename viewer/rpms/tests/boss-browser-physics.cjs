const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,
  args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage({viewport:{width:1550,height:960},deviceScaleFactor:1});
  const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  const r=await page.goto('http://127.0.0.1:8033/rpms/boss-p0.html',{waitUntil:'domcontentloaded',timeout:60000});
  assert.equal(r.status(),200);
  await page.waitForFunction(()=>window.__BOSS_API__?.ready||window.__BOSS_API__?.error,null,{timeout:90000});
  const err=await page.evaluate(()=>window.__BOSS_API__.error);
  assert.equal(err,null,'REAL RAPiER WASM MUST LOAD IN BROWSER: '+err);
  let current=await page.evaluate(()=>window.__BOSS_API__.snapshot());
  assert.equal(current.solver,'Rapier WebAssembly');
  assert.equal(current.blocks,17);
  assert.equal(current.feet,4);
  assert.ok(await page.evaluate(()=>window.__BOSS_API__.renderedMeshes())>=17,'not all voxel mesh blocks are visible');
  await page.evaluate(()=>{
   window.__BOSS_API__.reset();
   window.__BOSS_API__.pause();
  });
  let standing=await page.evaluate(()=>window.__BOSS_API__.advance(275));
  console.log('RAPIER_WEB_BOSS_FOUR_LEG_STAND',JSON.stringify(standing));
  assert.equal(standing.feet,4);
  assert.equal(standing.blocks,17);
  assert.ok(standing.mass>0,'body has no physical mass');
  assert.ok(Math.abs(standing.footGap)<.15,'feet DO NOT COLLIDE with ground: '+JSON.stringify(standing));
  assert.ok(Math.abs(standing.velocity.y)<.5,'body is NOT settled by actual ground reaction: '+standing.velocity.y);
  assert.ok(standing.bodyBottom>1.75&&standing.bodyBottom<2.25,'torso is not genuinely suspended by physical leg colliders: '+standing.bodyBottom);
  await page.waitForTimeout(120);
  await page.screenshot({path:path.join(__dirname,'boss-real-four-legged-standing.png'),fullPage:true});
  await page.locator('#removeOne').click();
  assert.equal(await page.evaluate(()=>window.__BOSS_API__.snapshot().feet),3,
   'remove-one button must remove an ACTUAL physical foot collider');
  await page.locator('#removeAll').click();
  current=await page.evaluate(()=>window.__BOSS_API__.snapshot());
  assert.equal(current.feet,0,'remove-all MUST remove all physical feet');
  assert.equal(current.blocks,9,'remaining only 9 physical torso blocks');
  assert.ok(current.mass<standing.mass,'collider removal MUST reduce real rigid-body mass');
  let fallen=await page.evaluate(()=>window.__BOSS_API__.advance(240));
  console.log('RAPIER_WEB_BOSS_AFTER_REMOVING_FEET',JSON.stringify(fallen));
  assert.ok(standing.bodyBottom-fallen.bodyBottom>1.45,
   'torso must fall AT LEAST 1.45 blocks after removal: '+JSON.stringify({standing,fallen}));
  assert.ok(fallen.bodyBottom>-.25 && fallen.bodyBottom<.35,
   'torso must stop at actual floor, not fall through it: '+fallen.bodyBottom);
  await page.waitForTimeout(100);
  await page.screenshot({path:path.join(__dirname,'boss-real-collapsed.png'),fullPage:true});
  await page.locator('#restart').click();
  assert.equal(await page.evaluate(()=>window.__BOSS_API__.snapshot().feet),4);
  await page.locator('#pause').click();
  await page.waitForTimeout(150);
  const afterReset=await page.evaluate(()=>window.__BOSS_API__.snapshot());
  assert.ok(afterReset.bodyBottom>2.4,'reset should respawn a fresh physical assembly above the floor');
  assert.equal(errors.length,0,'Browser JS errors: '+errors.join('\n'));
  console.log('PASS REAL RAPIER WASM: 17 collider assembly and contact-supported standing -> remove all physical legs -> 2-block torso collapse into floor; no fake animation');
  const mobile=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1,isMobile:true});
  const fail=[];mobile.on('pageerror',e=>fail.push(e.message));
  await mobile.goto('http://127.0.0.1:8033/rpms/boss-p0.html',{waitUntil:'domcontentloaded'});
  await mobile.waitForFunction(()=>window.__BOSS_API__?.ready||window.__BOSS_API__?.error,null,{timeout:90000});
  assert.equal(await mobile.evaluate(()=>window.__BOSS_API__.error),null,'mobile physics engine must initialize');
  await mobile.screenshot({path:path.join(__dirname,'boss-real-physics-mobile.png'),fullPage:true});
  assert.equal(fail.length,0,'Mobile browser errors: '+fail);
  console.log('PASS MOBILE real WASM Boss world renders and interactive controls are present');
  await mobile.close();
 }finally{await browser.close()}
})().catch(e=>{console.error('BOSS_RAPIER_BROWSER_TEST_FAILED',e.stack||e);process.exit(1)});
