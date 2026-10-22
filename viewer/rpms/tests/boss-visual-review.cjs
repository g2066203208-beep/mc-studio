const {chromium}=require('playwright-core');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage({viewport:{width:1470,height:885},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.stack||e.message));
  await page.goto('http://127.0.0.1:8033/rpms/boss.html',{waitUntil:'domcontentloaded'});
  await page.waitForFunction(()=>window.__BOSS_API__?.ready||window.__BOSS_API__?.error,null,{timeout:90000});
  if(await page.evaluate(()=>window.__BOSS_API__.error))throw Error(await page.evaluate(()=>window.__BOSS_API__.error));
  await page.waitForTimeout(2000);
  const base=await page.evaluate(()=>({audit:window.__BOSS_API__.voxelAudit(),snapshot:window.__BOSS_API__.snapshot(),assets:window.__BOSS_API__.getAssetStatus()}));
  console.log('BOSS_VISUAL_AUDIT_START',JSON.stringify(base));
  const jpeg=await page.locator('#bossCanvas').screenshot({type:'jpeg',quality:60,animations:'disabled'});
  console.log('BOSS_SCREENSHOT_JPEG_BASE64_START'+jpeg.toString('base64')+'BOSS_SCREENSHOT_JPEG_BASE64_END');
  if(errors.length)console.log('BOSS_BROWSER_ERRORS',JSON.stringify(errors));
  console.log('BOSS_VISUAL_AUDIT_COMPLETE');
 }finally{await browser.close()}
})().catch(e=>{console.error('BOSS_VISUAL_AUDIT_FAILED',e.stack||e);process.exit(1)});
