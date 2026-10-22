import assert from 'node:assert/strict';
import {buildCreatePowerBus,validatePowerBus,DRIVE_OUTPUTS} from '../boss-powertrain.mjs';
import {rotationRatio} from '../strict-core.mjs';

const initial=buildCreatePowerBus({rpm:18});
assert.equal(initial.motor.powered,true);
assert.equal(initial.blocks.length,26,'eight connected main-spine nodes + 6 x 3 distinct outputs');
assert.equal(initial.live,6,'every one of the 6 swivel bearings needs real Create graph connectivity');
assert.equal(initial.capacitySU,18*16384,'default Creative Motor SU capacity coefficient');
assert.equal(initial.loadSU,18*8*6,'explicitly ESTIMATED Simulated bearing SU load');
assert.equal(initial.bearings.length,6);
assert.ok(initial.bearings.every(b=>b.pathLive&&Math.abs(b.rpm)===18),'motor speed must propagate through meshed gear and clutch');
for(const row of [1,2,3]){
 const left=initial.bearings.find(b=>b.id==='L'+row);
 const right=initial.bearings.find(b=>b.id==='R'+row);
 assert.equal(left.outputRPM,9,'left perpendicular gear direction must be computed from actual z-offset');
 assert.equal(right.outputRPM,-9,'right perpendicular gear meshes with opposite axial direction');
}
assert.equal(validatePowerBus(initial).ok,true);
const disconnected=buildCreatePowerBus({rpm:18,disconnected:['L1','R2']});
assert.equal(disconnected.live,4);
assert.equal(disconnected.bearings.find(b=>b.id==='L1').outputRPM,0);
assert.equal(disconnected.bearings.find(b=>b.id==='R2').torqueMultiplier,0);
assert.equal(disconnected.bearings.find(b=>b.id==='L2').outputRPM,9);
assert.equal(disconnected.bearings.find(b=>b.id==='R3').outputRPM,-9);
const zero=buildCreatePowerBus({rpm:0});
assert.equal(zero.live,0);
assert.ok(zero.bearings.every(b=>!b.pathLive&&b.outputRPM===0));
const off=buildCreatePowerBus({rpm:18,enabled:false});
assert.equal(off.live,0);
const fast=buildCreatePowerBus({rpm:36,gearRatio:4});
assert.equal(fast.bearings.find(b=>b.id==='R1').outputRPM,-9);
assert.equal(fast.bearings.find(b=>b.id==='R1').torqueMultiplier,3.6);
const pre=buildCreatePowerBus({rpm:18,gearRatio:1});
assert.equal(pre.bearings.find(b=>b.id==='R1').outputRPM,-18);
assert.equal(initial.blocks.filter(b=>b.id==='create:creative_motor').length,1);
assert.equal(initial.blocks.filter(b=>b.id==='simulated:swivel_bearing').length,6);
assert.equal(initial.blocks.filter(b=>b.id==='create:clutch').length,6);
assert.ok(DRIVE_OUTPUTS.every(o=>o.motorBearingPos.every(n=>Number.isInteger(n))),
 'all Minecraft machine coordinates must be on integer block positions');
assert.ok(!initial.graph.errors.some(e=>e.code==='ROTATION_CONFLICT'),
 'gearbox cannot demand conflicting shaft RPM');
console.log('PASS: 26 integer Minecraft blocks: motor -> 3 meshed main drive gears -> six perpendicular branches -> six clutches -> six Simulated bearing RPM inputs; branch power actually gates physical servo torque');
