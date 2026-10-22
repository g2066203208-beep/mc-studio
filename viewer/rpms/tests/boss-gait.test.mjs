import assert from 'node:assert/strict';
import {planTripodFoot,solveLegIK,STANCE_PORTION} from '../boss-gait.mjs';
const fk=(hip,knee,upper=2,lower=2.5)=>({
 forward:-upper*Math.sin(hip)-lower*Math.sin(hip+knee),
 vertical:-upper*Math.cos(hip)-lower*Math.cos(hip+knee)
});
for(const forward of [-.58,-.4,0,.4,.58]){
 for(const vertical of [-4.28,-3.92,-3.68]){
  const ik=solveLegIK({forward,vertical});
  assert.equal(ik.reachable,true);
  assert.ok(Math.abs(ik.hip)<.85+1e-8);
  assert.ok(ik.knee>=0&&ik.knee<=1.24);
  const real=fk(ik.hip,ik.knee);
  assert.ok(Math.abs(real.forward-forward)<.11,JSON.stringify({forward,vertical,ik,real}));
  assert.ok(Math.abs(real.vertical-vertical)<.11,JSON.stringify({forward,vertical,ik,real}));
 }
}
let stance=0,swing=0,opposite=0;
for(let i=0;i<400;i++){
 const t=i/400;
 const a=planTripodFoot(t,0),b=planTripodFoot(t,1);
 if(a.phase==='stance')stance++;else swing++;
 if(a.phase!==b.phase)opposite++;
 assert.ok(a.target.vertical<=-4.22 && a.target.vertical>=-4.48);
 assert.ok(Math.abs(a.target.forward)<=.23);
}
assert.ok(Math.abs(stance/400-STANCE_PORTION)<.005);
assert.ok(opposite>270,'opposing support tripods should alternate for most of a cycle');
const front=planTripodFoot(0,0),back=planTripodFoot(STANCE_PORTION-.001,0);
assert.ok(back.target.forward>front.target.forward,
 'stance feet must move BACKWARDS relative to chassis so friction can propel the body');
console.log('PASS BOSSGAIT IK finite real hinge targets + physical tripod stance/swing foot placement; does NOT alone prove actual travel');
