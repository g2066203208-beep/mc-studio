const assert=require('node:assert/strict'),{chromium}=require('playwright-core');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage({viewport:{width:1240,height:800}});
  page.on('console',m=>{if(m.type()==='error')console.log('BROWSER:',m.text());});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8033/aero-world/',{waitUntil:'domcontentloaded'});
  await page.waitForFunction(()=>window.__AEROWORLD_READY__,null,{timeout:60000});
  await page.locator('#startImport').click();
  await page.locator('#jarInput').setInputFiles(['/tmp/aero-original.jar','/tmp/create-original.jar']);
  await page.waitForFunction(()=>window.__AEROWORLD_API__.archives()===2,null,{timeout:90000});
  const count=await page.evaluate(()=>window.__AEROWORLD_API__.modBlocks());
  assert.ok(count>40,'Actual released Create + Aero JAR should register more than 40 static blocks, got '+count);
  const result=await page.evaluate(async()=>window.__AEROWORLD_API__.probeOriginalModel('simulated:swivel_bearing',{
     assembled:'false',facing:'up',powered:'false'
  }));
  assert.ok(result.renderable&&result.meshes>0,'Official swivel_bearing model not rendered: '+JSON.stringify(result));
  const source=await page.evaluate(async()=>window.__AEROWORLD_API__.probeOriginalModel('create:andesite_casing',{}));
  console.log('OFFICIAL original Create/Aero JARs parsed',count,'blocks; swivel bearing',result,'andesite casing',source);
  assert.deepEqual(errors,[],'Browser errors loading actual publicly released original mod JARs');
  console.log('PASS: real original Create and Aeronautics JAR static render (no original graphics redistributed)');
 }finally{await browser.close();}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
