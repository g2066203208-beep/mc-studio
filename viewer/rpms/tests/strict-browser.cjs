const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
const path=require('node:path');
const fs=require('node:fs');
(async()=>{
const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
try{
 const page=await browser.newPage({viewport:{width:1500,height:920}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 // Test what the user actually sees with zero manual JARs and no network.
 await page.route('https://api.modrinth.com/**',route=>route.abort());
 const r=await page.goto('http://127.0.0.1:8033/rpms/',{waitUntil:'domcontentloaded'});
 assert.equal(r.status(),200);
 await page.waitForFunction(()=>window.__STRICT_READY__===true||window.__STRICT_ERROR__,null,{timeout:50000});
 assert.equal(await page.evaluate(()=>window.__STRICT_ERROR__||null),null);
 const n=await page.evaluate(()=>window.__STRICT_API__.getCatalog().length);
 assert.ok(n>=216,'Official registration catalog missing: '+n);
 const count=await page.locator('#paletteItems .item').count();assert.ok(count>=216);
 assert.equal(await page.locator('#blocksNum').textContent(),'5','first visit must display Create and Aeronautics mechanical models immediately');
 const before=await page.evaluate(()=>({stats:window.__STRICT_API__.getVisualStats(),assets:window.__STRICT_API__.getAssetStatus()}));
 assert.ok(before.stats.meshes>=8,'FIRST-PAINT BLOCKS HAVE NO VISIBLE MESHES: '+JSON.stringify(before));
 assert.ok(before.assets.every(x=>x.status==='preview'),'Network offline must show 3D preview, not transparent wireframes');
 await page.screenshot({path:path.join(__dirname,'strict-first-open-no-jar.png'),fullPage:true});
 // Official publisher network is now mocked only for this deterministic
 // parser UI test. It represents auto-loaded JARs, NOT an official release.
 await page.unroute('https://api.modrinth.com/**');
 const fixture=fs.readFileSync(path.join(__dirname,'fixture-simulated.jar'));
 await page.route('https://api.modrinth.com/**',async route=>{
  const aero=route.request().url().includes('create-aeronautics');
  const name=aero?'create-aeronautics':'create';
  const version=aero?'1.3.2':'6.0.10';
  await route.fulfill({status:200,contentType:'application/json',
   headers:{'access-control-allow-origin':'*'},
   body:JSON.stringify([{version_number:version+'+mc1.21.1',version_type:'release',
    files:[{primary:true,url:'https://cdn.modrinth.com/data/'+name+'/versions/mock/'+name+'.jar',hashes:{}}]}])});
 });
 await page.route('https://cdn.modrinth.com/**',route=>route.fulfill({status:200,
  contentType:'application/java-archive',headers:{'access-control-allow-origin':'*'},body:fixture}));
 await page.locator('#loadOfficial').click();
 await page.waitForFunction(()=>window.__STRICT_API__.getOfficialState().length===2 &&
  window.__STRICT_API__.getOfficialState().every(s=>s.phase==='ready'),null,{timeout:45000});
 assert.equal(await page.evaluate(()=>window.__STRICT_API__.getJarCount()),2,
  'auto loader must install two official-source archives without user file input');
 await page.waitForFunction(()=>window.__STRICT_API__.getAssetStatus().some(x=>x.status==='authentic'),null,{timeout:25000});
 await page.screenshot({path:path.join(__dirname,'strict-automatic-models.png'),fullPage:true});

 const status=await page.evaluate(()=>window.__STRICT_API__.getResult());
 assert.equal(status.summary.blocks,5);
 assert.equal(status.summary.links,3);
 assert.equal(status.rpms['2,1,1'],-16);
 // User-facing live rotational preview: both primary gears must physically
 // advance in the Three.js render loop; Simulated bearing stays unassembled.
 await page.waitForTimeout(350);
 const movement=await page.evaluate(()=>window.__STRICT_API__.getAnimationState());
 assert.ok(movement.filter(x=>x.hasRotor&&Math.abs(x.phase)>0.01).length>=3,
   'shaft/gears/motor must ACTUALLY rotate in the canvas, not just output RPM text: '+JSON.stringify(movement));
 assert.equal(movement.find(x=>x.id==='simulated:swivel_bearing').hasRotor,false,'do not fake Sable bearing assembly');
 const stressInitial=await page.evaluate(()=>window.__STRICT_API__.getPreview());
 assert.equal(stressInitial.summary.capacity,16384*16,'Create official stress capacity default');
 assert.equal(stressInitial.summary.impact,0);
 await page.evaluate(()=>{
  const api=window.__STRICT_API__;
  api.place({id:'create:mechanical_press',pos:[3,1,0],axis:'x',facing:'east'},{ignoreSupport:true});
  const input=document.querySelector('#capacityScale');input.value='0.0001';input.dispatchEvent(new Event('input',{bubbles:true}));
 });
 const overload=await page.evaluate(()=>window.__STRICT_API__.getPreview());
 assert.equal(overload.summary.overstressed,1,'stress controls must stall overloaded network');
 assert.equal(overload.rpms['1,1,0'],0,'shaft must stop under simulated SU overload');
 assert.equal(await page.locator('#stressUtil').getAttribute('data-overloaded'),'true');
 await page.screenshot({path:path.join(__dirname,'strict-stress-overload.png'),fullPage:true});
 await page.evaluate(()=>{
  const slider=document.querySelector('#capacityScale');slider.value='1';slider.dispatchEvent(new Event('input',{bubbles:true}));
 });
 assert.equal(await page.evaluate(()=>window.__STRICT_API__.getPreview().summary.overstressed),0,'network must recover after adjusting capacity');
 // Test real UI button on a Create clutch: off=no power interruption,
 // on=disconnect downstream shaft; this is a clearly labelled preview.
 await page.evaluate(()=>{
  const api=window.__STRICT_API__;api.reset();
  api.place({id:'create:creative_motor',pos:[0,1,0],facing:'east',axis:'x',rpm:16});
  api.place({id:'create:shaft',pos:[2,1,0],axis:'x'});
  api.place({id:'create:clutch',pos:[1,1,0],axis:'x',powered:false});
 });
 assert.equal(await page.evaluate(()=>window.__STRICT_API__.getPreview().rpms['2,1,0']),16);
 await page.locator('#toggleRedstone').click();
 assert.equal(await page.evaluate(()=>window.__STRICT_API__.getPreview().rpms['2,1,0']),undefined,
   'powered clutch must stop the downstream shaft');
 await page.locator('#toggleRedstone').click();
 assert.equal(await page.evaluate(()=>window.__STRICT_API__.getPreview().rpms['2,1,0']),16,
   'disabling the redstone clutch must restore the downstream shaft');
 // Restore the original source-audited demo used by legacy browser checks.
 await page.locator('#example').click();
 await page.locator('[data-mode="inspect"]').click();
 assert.ok(await page.locator('[data-mode="inspect"]').evaluate(e=>e.classList.contains('active')));
 await page.locator('[data-view="top"]').click();
 await page.screenshot({path:path.join(__dirname,'strict-desktop.png'),fullPage:true});
 await page.locator('#resetScene').click();
 assert.equal(await page.locator('#blocksNum').textContent(),'0');
 await page.locator('[data-mode="place"]').click();
 // After full-page screenshot the Chromium scroll position may be at the page
 // bottom, so use locator.click() to scroll the canvas back into the viewport.
 await page.locator('#view').scrollIntoViewIfNeeded();
 await page.locator('#view').click({position:{x:180,y:150}});
 assert.equal(await page.locator('#blocksNum').textContent(),'1','click-to-place MC block must work');
 await page.locator('#view').click({button:'right',position:{x:180,y:150}});
 assert.equal(await page.locator('#blocksNum').textContent(),'0','right-click-to-break MC block must work');
 await page.locator('#search').fill('swivel_bearing');
 assert.ok(await page.locator('#paletteItems [data-id="simulated:swivel_bearing"]').count()===1);
 await page.locator('#search').fill('');
 await page.locator('#namespace').selectOption('simulated');
 assert.ok((await page.locator('#paletteItems .item').count())>=30);
 await page.locator('#namespace').selectOption('all');
 await page.locator('#jarInput').setInputFiles(path.join(__dirname,'fixture-simulated.jar'));
 await page.waitForFunction(()=>document.getElementById('jarStatus').textContent.includes('成功读取'),null,{timeout:30000});
 assert.ok((await page.locator('#jarStatus').textContent()).includes('种原版方块状态'));
 assert.ok((await page.locator('#jarStatus').textContent()).includes('1 个内嵌包'),'packaged Aeronautics inner mod must be scanned');
 await page.evaluate(()=>{
  const api=window.__STRICT_API__;api.reset();
  api.place({id:'simulated:physics_assembler',pos:[0,1,0],axis:'y',facing:'north'});
 });
 await page.waitForFunction(()=>window.__STRICT_API__.getAssetStatus().some(x=>x.status==='partial'));
 assert.ok((await page.evaluate(()=>window.__STRICT_API__.getAssetStatus()))[0].meshes>=6,
  'Nested Simulated physical assembler JAR model should appear with explicit partial state warning');
 await page.evaluate(()=>window.__STRICT_API__.selectBlock('create:shaft'));
 await page.waitForFunction(()=>document.querySelectorAll('#statePreset option').length>=4);
 const options=await page.evaluate(()=>window.__STRICT_API__.getStateOptions('create:shaft'));
 assert.ok(options.includes('axis=x')&&options.includes('axis=y'),'real blockstate variants loaded from JAR');
 await page.locator('#example').click();
 await page.waitForFunction(()=>window.__STRICT_API__.getAssetStatus().length===4);
 const visual=await page.evaluate(()=>window.__STRICT_API__.getAssetStatus());
 assert.ok(visual.some(m=>m.status==='authentic'),'Create fixture original 3D geometry must replace placeholders: '+JSON.stringify(visual));
 assert.ok(visual.some(m=>m.meshes>=6),'Real PNG textured faces must be rendered');
 await page.screenshot({path:path.join(__dirname,'strict-authentic-jar.png'),fullPage:true});
 await page.evaluate(()=>{const api=window.__STRICT_API__;api.reset();api.place({id:'simulated:swivel_bearing',pos:[0,1,0],axis:'y',facing:'up'});});
 await page.waitForFunction(()=>window.__STRICT_API__.getAssetStatus().some(x=>x.status==='authentic'));
 assert.ok((await page.locator('#modelCounter').textContent()).includes('原版模型 1/1'));

 assert.equal(errors.length,0,'Browser errors: '+errors.join('; '));
 // Mobile responsiveness in the same initialized WebGL session to avoid
 // testing flaky second CDN downloads instead of the layout.
 await page.setViewportSize({width:390,height:820});
 await page.waitForTimeout(450);
 await page.locator('#example').click();
 await page.screenshot({path:path.join(__dirname,'strict-mobile.png'),fullPage:true});
 assert.ok((await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth))<20,'mobile horizontal overflow');
 await page.goto('http://127.0.0.1:8033/rpms/native.html',{waitUntil:'domcontentloaded'});
 await page.waitForFunction(()=>document.querySelectorAll('#groups .case').length>=30&&document.querySelectorAll('#probes .case').length===8,null,{timeout:15000});
 assert.equal(await page.locator('#probes .case').count(),8,'actual native GameTest case evidence missing');
 assert.equal(await page.locator('#passed').textContent(),'0','Must never claim native mechanics work without game tests');
 assert.ok((await page.locator('.case').count())>=30,'Core Create/Aeronautics physics and wireless certification entries missing');
 await page.screenshot({path:path.join(__dirname,'strict-native-certification-mobile.png'),fullPage:true});
 console.log('PASS true browser MC workbench, registry '+n+', kinetic example, placement UI, filtering, local JAR, mobile screenshots');
}finally{await browser.close()}
})().catch(e=>{console.error(e.stack||e);process.exit(1)});