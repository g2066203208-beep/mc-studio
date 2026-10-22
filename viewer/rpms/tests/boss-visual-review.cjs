const {chromium}=require('playwright-core');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage({viewport:{width:1470,height:885},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.stack||e.message));
  await page.goto('http://127.0.0.1:8033/rpms/boss.html',{waitUntil:'domcontentloaded'});
  await page.waitForFunction(()=>window.__BOSS_API__?.ready||window.__BOSS_API__?.error,null,{timeout:90000});
  if(await page.evaluate(()=>window.__BOSS_API__.error))throw Error(await page.evaluate(()=>window.__BOSS_API__.error));
  // A real screenshot is valid only when the ORIGINAL Simulated bearing and
  // Create casings have replaced placeholder geometry, not after a timer.
  await page.waitForFunction(()=>{
   const t=window.__BOSS_API__.getAssetStatus().officialTypes;
   return t.includes('simulated:swivel_bearing')&&t.includes('create:brass_casing')&&t.includes('create:industrial_iron_block');
  },null,{timeout:120000});
  // One official resource ID can have MULTIPLE original Minecraft
  // blockstates. In particular left/right swivels face opposite directions.
  // Never reuse a right-facing model for the left-facing joint.
  const orientation=await page.evaluate(()=>window.__BOSS_API__.getAssetStatus().officialVariants);
  console.log('BOSS_OFFICIAL_MODEL_STATES',JSON.stringify(orientation));
  assert.ok(orientation.includes('simulated:swivel_bearing|axis=x|facing=east'),
    'right-facing Simulated bearing lacks original blockstate model');
  assert.ok(orientation.includes('simulated:swivel_bearing|axis=x|facing=west'),
    'left-facing Simulated bearing incorrectly reuses east-facing model');
  await page.waitForFunction(()=>window.__BOSS_API__.snapshot().supportContacts>=4,null,{timeout:35000});
  await page.waitForTimeout(450);
  const base=await page.evaluate(()=>({audit:window.__BOSS_API__.voxelAudit(),snapshot:window.__BOSS_API__.snapshot(),assets:window.__BOSS_API__.getAssetStatus()}));
  console.log('BOSS_VISUAL_AUDIT_START',JSON.stringify(base));
  const jpeg=await page.locator('#bossCanvas').screenshot({type:'jpeg',quality:65,animations:'disabled'});
  console.log('BOSS_SCREENSHOT_JPEG_BASE64_START'+jpeg.toString('base64')+'BOSS_SCREENSHOT_JPEG_BASE64_END');
  await page.evaluate(()=>{const r=window.__BOSS_API__;r.motor({rpm:28,torqueLimit:2200});r.mode('march');r.advance(180);r.render();});
  const gait=await page.evaluate(()=>window.__BOSS_API__.snapshot());
  console.log('BOSS_VISUAL_MARCH_GAIT',JSON.stringify({joints:gait.joints,contacts:gait.supportContacts,pose:gait.position,maxHingeError:gait.maxHingeError,angles:gait.legAngles}));
  const marching=await page.locator('#bossCanvas').screenshot({type:'jpeg',quality:65,animations:'disabled'});
  console.log('BOSS_MARCH_JPEG_BASE64_START'+marching.toString('base64')+'BOSS_MARCH_JPEG_BASE64_END');
  if(errors.length)console.log('BOSS_BROWSER_ERRORS',JSON.stringify(errors));
  console.log('BOSS_VISUAL_AUDIT_COMPLETE');
 }finally{await browser.close()}
})().catch(e=>{console.error('BOSS_VISUAL_AUDIT_FAILED',e.stack||e);process.exit(1)});
