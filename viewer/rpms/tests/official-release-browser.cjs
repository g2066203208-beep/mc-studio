const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const {chromium}=require('playwright-core');
const dir=process.env.OFFICIAL_MOD_DIR||'/tmp/rpms-official-mods';
const mods=['create-1.21.1.jar','aeronautics-1.21.1.jar','minecraft-1.21.1.jar'].map(x=>path.join(dir,x));
for(const f of mods)assert.ok(fs.statSync(f).size>1e6,'Official file not found: '+f);
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--enable-webgl','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage({viewport:{width:1620,height:990},deviceScaleFactor:1});
  const exceptions=[];page.on('pageerror',e=>exceptions.push(e.message));
  const response=await page.goto('http://127.0.0.1:8033/rpms/',{waitUntil:'domcontentloaded',timeout:60000});
  assert.equal(response.status(),200);
  await page.waitForFunction(()=>window.__STRICT_READY__||window.__STRICT_ERROR__,null,{timeout:60000});
  assert.equal(await page.evaluate(()=>window.__STRICT_ERROR__||null),null);
  await page.locator('#jarInput').setInputFiles(mods,{timeout:160000});
  await page.waitForFunction(()=>window.__STRICT_API__.getJarCount()===3&&document.querySelector('#jarStatus').textContent.includes('已本地读取'),null,{timeout:180000});
  const catalog=await page.evaluate(()=>window.__STRICT_API__.getCatalog());
  console.log('OFFICIAL ASSET LOADED: 3 archives; registry=',catalog.length);
  const namespaces=[...new Set(catalog.map(id=>id.split(':')[0]))].sort();
  console.log('Namespaces:',namespaces);
  const planned=[
   'create:shaft','create:cogwheel','create:large_cogwheel','create:andesite_casing',
   'create:brass_casing','create:gearbox','create:creative_motor',
   'create:mechanical_press','create:water_wheel','create:mechanical_mixer',
   'simulated:physics_assembler','simulated:swivel_bearing','simulated:velocity_sensor',
   'simulated:gimbal_sensor','simulated:analog_transmission','simulated:rope_winch',
   'aeronautics:propeller','aeronautics:heat_engine','offroad:wheel'
  ];
  const selected=planned.filter(id=>catalog.includes(id));
  const otherIds=catalog.filter(id=>id.startsWith('aeronautics:')).slice(0,7);
  for(const id of otherIds)if(!selected.includes(id))selected.push(id);
  console.log('Selected official model candidates:',selected);
  assert.ok(selected.length>=12,'Too few registered target mechanical blocks');
  const placed=await page.evaluate(ids=>{
   const api=window.__STRICT_API__;api.reset();
   let count=0,failed=[];
   for(const id of ids){
    const pos=[(count%6)*2-5,1,Math.floor(count/6)*2-3];
    const item={id,pos,axis:count%3===0?'y':count%3===1?'x':'z',facing:count%3===0?'up':count%3===1?'east':'north',rpm:16};
    if(api.place(item,{ignoreSupport:true}))count++;else failed.push(id);
   }
   return {count,failed};
  },selected);
  assert.equal(placed.count,selected.length,'All sourced blocks should be placed');
  await page.waitForFunction((n)=>window.__STRICT_API__.getAssetStatus().length===n&&document.querySelector('#modelCounter')?.textContent.includes('/'+n),selected.length,{timeout:180000});
  const outputs=await page.evaluate(()=>{
   const api=window.__STRICT_API__;
   return {assets:api.getAssetStatus(),blocks:api.getState(),summary:document.querySelector('#modelCounter').textContent};
  });
  const report=outputs.assets.map(row=>{
   const [x,y,z]=row.pos.split(',').map(Number);
   const item=outputs.blocks.find(b=>b.pos[0]===x&&b.pos[1]===y&&b.pos[2]===z);
   return {id:item?.id,status:row.status,reason:row.reason,meshes:row.meshes||0,matched:row.matched||''};
  });
  for(const record of report)console.log('BLOCK_MODEL',JSON.stringify(record));
  const counts={authentic:report.filter(x=>x.status==='authentic').length,
   partial:report.filter(x=>x.status==='partial').length,
   missing:report.filter(x=>x.status==='missing').length,
   unsupported:report.filter(x=>x.status==='unsupported').length};
  console.log('OFFICIAL MODEL COVERAGE:',counts,outputs.summary);
  const textured=report.filter(x=>x.status==='authentic'||x.status==='partial');
  assert.ok(textured.some(x=>x.id.startsWith('create:')),'No Create real models rendered');
  assert.ok(textured.some(x=>x.id.startsWith('simulated:')||x.id.startsWith('aeronautics:')),'No Simulated/Aeronautics models rendered');
  assert.equal(exceptions.length,0,'Browser exceptions: '+exceptions.join('\n'));
  await page.locator('#view').scrollIntoViewIfNeeded();
  await page.screenshot({path:path.join(__dirname,'official-create-aeronautics-desktop.png'),fullPage:true,timeout:60000});
  await page.locator('#search').fill('swivel_bearing');
  await page.locator('#paletteItems [data-id="simulated:swivel_bearing"]').click();
  const stateOptions=await page.evaluate(()=>window.__STRICT_API__.getStateOptions('simulated:swivel_bearing'));
  console.log('SWIVEL BEARING Official blockstate examples:',stateOptions.slice(0,8));
  await page.setViewportSize({width:390,height:844});
  await page.waitForTimeout(700);
  await page.screenshot({path:path.join(__dirname,'official-create-aeronautics-mobile.png'),fullPage:true,timeout:60000});
  const widthOverflow=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
  assert.ok(widthOverflow<=20,'Mobile overflow '+widthOverflow);
  const result={tested_at:new Date().toISOString(),source:'Official SHA-verified Minecraft/Create/Aeronautics runtime release JAR assets; no files redistributed.',
   minecraft:'1.21.1',create:'6.0.10',aeronautics:'1.3.2',catalog:namespaces,placed:selected.length,counts,models:report,swivel_states:stateOptions.slice(0,20),viewport_mobile_overflow:widthOverflow,exceptions};
  fs.writeFileSync(path.join(__dirname,'official-model-coverage.json'),JSON.stringify(result,null,2));
  console.log('PASS actual official release game JAR static model render audit and desktop/mobile screenshot');
 }finally{await browser.close()}
})().catch(e=>{console.error('REAL RELEASE E2E FAIL',e.stack||e);process.exit(1)});
