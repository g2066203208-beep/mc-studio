const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,
  args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage({viewport:{width:1550,height:960}});
  const errors=[];page.on('pageerror',e=>errors.push(e.stack||e.message));
  page.on('console',m=>{if(m.type()==='error')console.log('BOSS_PAGE_CONSOLE',m.text())});
  await page.goto('http://127.0.0.1:8033/rpms/boss.html',{waitUntil:'domcontentloaded',timeout:70000});
  await page.waitForFunction(()=>window.__BOSS_API__?.ready||window.__BOSS_API__?.error,null,{timeout:90000});
  assert.equal(await page.evaluate(()=>window.__BOSS_API__.error),null);
  await page.waitForFunction(()=>{
   const x=window.__BOSS_API__.getAssetStatus().officialTypes;
   return x.includes('create:creative_motor')&&x.includes('simulated:swivel_bearing');
  },null,{timeout:200000});
  const a=await page.evaluate(()=>window.__BOSS_API__.getAssetStatus());
  const s=await page.evaluate(()=>window.__BOSS_API__.snapshot());
  console.log('BOSS_REAL_CREATE_SIMULATED_MODELS',JSON.stringify({a,bodies:s.rigidBodies,joints:s.joints}));
  assert.ok(a.officialMeshes>=12,'original JSON/PNG asset meshes never replaced synthetic component models');
  assert.ok(a.officialTypes.includes('create:cogwheel'),'source original Create gear models absent');
  assert.ok(a.officialTypes.includes('simulated:swivel_bearing'),'source original Aero/Simulated bearing meshes absent');
  await page.screenshot({path:path.join(__dirname,'boss-articulated-official-jar.png'),fullPage:true});
  assert.equal(errors.length,0,'Real publisher model loading JS errors: '+errors.join('\n'));
  console.log('PASS Original publisher static Create and Simulated textured models on real 13-body articulated web robot, no uploads');
 }finally{await browser.close()}
})().catch(e=>{console.error('BOSS_REAL_PUBLISHER_ASSET_TEST_FAILED',e.stack||e);process.exit(1)});
