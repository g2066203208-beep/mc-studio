const assert = require('node:assert/strict');
const { chromium } = require('playwright-core');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const browser = await chromium.launch({
    channel: 'chrome', headless: true,
    args: ['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader',
      '--enable-unsafe-swiftshader', '--enable-webgl', '--disable-dev-shm-usage']
  });
  const page = await browser.newPage({viewport:{width:1540,height:920},deviceScaleFactor:1});
  const jsErrors=[];
  page.on('pageerror',e=>jsErrors.push(e.stack||e.message));
  page.on('console',m=>{if(m.type()==='error')console.log('browser console:',m.text())});
  try {
    const resp=await page.goto('http://127.0.0.1:8033/rpms/',{waitUntil:'domcontentloaded'});
    assert.equal(resp.status(),200);
    await page.waitForFunction(()=>window.__RPMS_READY__===true || !!window.__RPMS_ERROR__,{timeout:45000});
    const error=await page.evaluate(()=>window.__RPMS_ERROR__||null);
    assert.equal(error,null,'WebGL/Three.js failed to initialize: '+error);
    const info=await page.evaluate(()=>({
      seg:window.__rpmsApi.getSegmentCount(),
      joints:window.__rpmsApi.getJointIds(),
      mode:window.__rpmsApi.getMode(),
      canvas:document.querySelector('canvas').getBoundingClientRect().width
    }));
    assert.equal(info.seg,13);
    assert.equal(info.joints.length,12);
    assert.ok(info.canvas>250);
    console.log('WebGL viewer ready:',info.seg,'segments and',info.joints.length,'live axis controls');

    await page.locator('[data-mode="idle"]').click();
    assert.equal(await page.evaluate(()=>window.__rpmsApi.getMode()),'idle');
    await page.locator('[data-mode="walk"]').click();
    const before=await page.evaluate(()=>window.__rpmsApi.getActualAngle('LF.KFE'));
    await page.waitForTimeout(1800);
    const after=await page.evaluate(()=>window.__rpmsApi.getActualAngle('LF.KFE'));
    assert.ok(Math.abs(after-before)>0.15, 'Walk mode does not animate a real knee group: '+before+' '+after);
    console.log('Animated knee preview moved:',before.toFixed(2),'->',after.toFixed(2));
    await page.locator('[data-joint="RF.KFE"]').click();
    assert.equal(await page.evaluate(()=>window.__rpmsApi.getSelected()),'RF.KFE');
    await page.locator('#jointSlider').fill('82');
    assert.equal(await page.evaluate(()=>window.__rpmsApi.getMode()),'manual');
    assert.ok(Math.abs(await page.evaluate(()=>window.__rpmsApi.getActualAngle('RF.KFE'))-82)<.1);
    await page.locator('#explode').locator('..').click();
    assert.equal(await page.locator('#explode').isChecked(),true);
    await page.locator('#axes').locator('..').click();
    assert.equal(await page.locator('#axes').isChecked(),false);
    await page.locator('[data-view="side"]').click();
    await page.locator('[data-mode="single"]').click();
    await page.locator('#legSelect').selectOption('LR');
    await page.waitForTimeout(700);
    assert.ok(jsErrors.length===0,'Browser JS errors: '+jsErrors.join('; '));

    const png=path.join(__dirname,'rpms-screenshot.png');
    await page.screenshot({path:png,fullPage:true});
    assert.ok(fs.statSync(png).size>14000,'Screenshot too small - canvas may be blank');
    console.log('Screenshot saved:',png,fs.statSync(png).size,'bytes');
    const mobile=await browser.newPage({viewport:{width:390,height:780},isMobile:true,deviceScaleFactor:1});
    await mobile.goto('http://127.0.0.1:8033/rpms/',{waitUntil:'domcontentloaded'});
    await mobile.waitForFunction(()=>window.__RPMS_READY__===true||!!window.__RPMS_ERROR__,{timeout:45000});
    assert.equal(await mobile.evaluate(()=>window.__RPMS_ERROR__||null),null);
    const over=await mobile.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
    assert.ok(over<12,'Mobile layout horizontal overflow: '+over);
    await mobile.screenshot({path:path.join(__dirname,'rpms-mobile.png')});
    await mobile.close();
    console.log('PASS: desktop and mobile actual Chrome WebGL 3D interactive smoke test');
  } finally {
    await browser.close();
  }
})().catch(e=>{console.error(e.stack||e);process.exit(1)});
