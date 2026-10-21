const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
const path=require('node:path'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,
  args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage({viewport:{width:1500,height:900}});
  const errors=[],rejected=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('requestfailed',r=>{if(/modrinth/.test(r.url()))rejected.push(r.url()+' '+r.failure()?.errorText)});
  await page.goto('http://127.0.0.1:8033/rpms/',{waitUntil:'domcontentloaded',timeout:60000});
  await page.waitForFunction(()=>window.__STRICT_READY__||window.__STRICT_ERROR__,null,{timeout:60000});
  assert.equal(await page.evaluate(()=>window.__STRICT_ERROR__||null),null);
  const initial=await page.evaluate(()=>({stats:window.__STRICT_API__.getVisualStats(),
    models:window.__STRICT_API__.getAssetStatus()}));
  assert.ok(initial.stats.meshes>8,'Default must render actual visible 3D objects BEFORE JAR');
  console.log('FIRST_OPEN_VISIBLE_3D',JSON.stringify(initial));
  await page.waitForFunction(()=>window.__STRICT_API__.getOfficialState().length===2 &&
    window.__STRICT_API__.getOfficialState().every(x=>x.phase==='ready'||x.phase==='failed'),null,{timeout:210000});
  await page.waitForFunction(()=>{
    const assets=window.__STRICT_API__.getAssetStatus();
    return assets.length===4&&assets.some(x=>x.status==='authentic');
  },null,{timeout:120000});
  const states=await page.evaluate(()=>window.__STRICT_API__.getOfficialState());
  const models=await page.evaluate(()=>window.__STRICT_API__.getAssetStatus());
  const summary={states,models,errors,rejected,visible:await page.evaluate(()=>window.__STRICT_API__.getVisualStats())};
  console.log('REAL_CDN_MODEL_LOAD',JSON.stringify(summary));
  fs.writeFileSync(path.join(__dirname,'real-cdn-autoload-result.json'),JSON.stringify(summary,null,2));
  await page.screenshot({path:path.join(__dirname,'real-cdn-autoload-desktop.png'),fullPage:true,timeout:60000});
  assert.ok(states.every(x=>x.phase==='ready'),'Official user-browser direct CDN access failed: '+JSON.stringify(summary));
  assert.ok(models.some(x=>x.status==='authentic'),'Auto-import did not replace original preview with static Minecraft model');
  assert.equal(errors.length,0,errors.join('\n'));
  console.log('PASS real Chrome browser: zero-upload first-visit original Create/Aeronautics 3D model display');
 }finally{await browser.close()}
})().catch(e=>{console.error('ORIGINAL_CDN_CORS_TEST_FAIL',e.stack||e);process.exit(1)});
