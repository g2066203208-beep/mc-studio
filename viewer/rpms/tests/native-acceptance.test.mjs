import assert from 'node:assert/strict';
import fs from 'node:fs';
const manifest=JSON.parse(fs.readFileSync(new URL('../native-acceptance.json',import.meta.url),'utf8'));
assert.equal(manifest.schema,1);
const mandatory=['动力','传动','应力','无线','红石','物品','重力','阻力','风场','升力','装配','刚体','碰撞','传感器','同步','控制'];
for(const name of mandatory)assert.ok(manifest.domains.some(d=>d.name.includes(name)), 'Missing coverage domain: '+name);
const ids=new Set(),valid=new Set(['native_unverified','native_passed','preview_only']);
for(const d of manifest.domains){
 assert.ok(!ids.has(d.id), 'duplicate: '+d.id);ids.add(d.id);
 assert.ok(valid.has(d.status),'invalid status: '+d.id);
 assert.ok(d.family&&d.name&&d.criterion);
 if(d.status==='native_passed'){
   assert.ok(d.native_evidence?.github_run_url?.startsWith('https://github.com/'),'native claimed without workflow evidence: '+d.id);
   assert.ok(d.native_evidence?.test_name&&d.native_evidence?.game_version&&d.native_evidence?.mod_versions,'native claimed without test details: '+d.id);
 }
}
const passed=manifest.domains.filter(d=>d.status==='native_passed').length;
assert.equal(passed,manifest.confirmed_native_checks,'declared counts disagree');
const report={total:manifest.domains.length,passed,preview:manifest.domains.filter(d=>d.status==='preview_only').length,unverified:manifest.domains.filter(d=>d.status==='native_unverified').length,certified:passed===manifest.domains.length};
console.log('NATIVE_GAME_CERTIFICATION',JSON.stringify(report));
assert.equal(report.certified,false,'This build should not accidentally assert full runtime parity without actual game tests');
console.log('PASS coverage honesty and runtime verification barrier (NOT a pass of native gameplay)');
