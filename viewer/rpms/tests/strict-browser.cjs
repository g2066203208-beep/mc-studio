const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
const path=require('node:path');
(async()=>{
const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
try{
 const page=await browser.newPage({viewport:{width:1500,height:920}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const r=await page.goto('http://127.0.0.1:8033/rpms/',{waitUntil:'domcontentloaded'});
 assert.equal(r.status(),200);
 await page.waitForFunction(()=>window.__STRICT_READY__===true||window.__STRICT_ERROR__,null,{timeout:50000});
 assert.equal(await page.evaluate(()=>window.__STRICT_ERROR__||null),null);
 const n=await page.evaluate(()=>window.__STRICT_API__.getCatalog().length);
 assert.ok(n>=216,'Official registration catalog missing: '+n);
 const count=await page.locator('#paletteItems .item').count();assert.ok(count>=216);
 assert.equal(await page.locator('#blocksNum').textContent(),'0','must start with EMPTY MC grid, not fake Q12');
 await page.locator('#example').click();
 const status=await page.evaluate(()=>window.__STRICT_API__.getResult());
 assert.equal(status.summary.blocks,4);
 assert.equal(status.summary.links,3);
 assert.equal(status.rpms['2,1,1'],-16);
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
 await page.waitForFunction(()=>document.getElementById('jarStatus').textContent.includes('本地读取'),null,{timeout:30000});
 assert.ok((await page.locator('#jarStatus').textContent()).includes('种变体'));
 assert.ok((await page.locator('#jarStatus').textContent()).includes('1 个嵌套组件'),'packaged Aeronautics inner mod must be scanned');
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
 assert.ok((await page.locator('#modelCounter').textContent()).includes('原始资源模型 1/1'));

 assert.equal(errors.length,0,'Browser errors: '+errors.join('; '));
 // Mobile responsiveness in the same initialized WebGL session to avoid
 // testing flaky second CDN downloads instead of the layout.
 await page.setViewportSize({width:390,height:820});
 await page.waitForTimeout(450);
 await page.locator('#example').click();
 await page.screenshot({path:path.join(__dirname,'strict-mobile.png'),fullPage:true});
 assert.ok((await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth))<20,'mobile horizontal overflow');
 console.log('PASS true browser MC workbench, registry '+n+', kinetic example, placement UI, filtering, local JAR, mobile screenshots');
}finally{await browser.close()}
})().catch(e=>{console.error(e.stack||e);process.exit(1)});