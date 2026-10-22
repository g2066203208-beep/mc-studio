/**
 * A source-audited, INTEGER-BLOCK Create topology powering the six Hip axes.
 *
 * This is a genuine connected graph using the RPMS Create 6.0.10 topology
 * resolver (RotationPropagator subset), NOT cosmetic rotating meshes.
 *
 *                         Create creative motor (0,0,2), facing north
 *                               large cog Z (0,0,1)
 *                                shaft Z (0,0,0)
 *                               large cog Z (0,0,-1)
 *                                shaft Z (0,0,-2)
 *                               large cog Z (0,0,-3)
 *                / each large cog perpendicular diagonal -> both sides
 *  large cog X (±1,0,[-2,0,2]) -> redstone clutch X (±2) -> swivel bearing X (±3)
 *
 * Create's existing official small/large cog rules determine handedness.
 * The knee servo is ON THE MOVING SHIN and its flexible electrical/mechanical
 * power coupling is not physically simulated; do not claim native fidelity.
 */
import {evaluate} from './strict-core.mjs';

const ROWS=[-2,0,2];
export const DRIVE_OUTPUTS=ROWS.flatMap((z,row)=>[-1,1].map(side=>({
 id:(side<0?'L':'R')+(row+1),side,z,
 motorBearingPos:[side*3,0,z],
 clutchPos:[side*2,0,z],
 outputGearPos:[side,0,z]
})));
const make=(id,x,y,z,props={})=>({id,pos:[x,y,z],...props});
const k=pos=>pos.join(',');

export function buildCreatePowerBus({rpm=18,enabled=true,gearRatio=2,disconnected=[]}={}){
 if(!Number.isFinite(rpm)||rpm<0||rpm>96)throw new RangeError('Motor speed must be between 0 and 96 RPM');
 if(![1,2,4].includes(gearRatio))throw new RangeError('Only tested preview gear reductions 1,2,4');
 const removed=new Set(disconnected);
 const blocks=[
  make('create:creative_motor',0,0,2,{facing:'north',axis:'z',rpm:enabled&&rpm>0?rpm:0}),
  make('create:large_cogwheel',0,0,1,{axis:'z'}),
  make('create:shaft',0,0,0,{axis:'z'}),
  make('create:large_cogwheel',0,0,-1,{axis:'z'}),
  make('create:shaft',0,0,-2,{axis:'z'}),
  make('create:large_cogwheel',0,0,-3,{axis:'z'})
 ];
 for(const o of DRIVE_OUTPUTS){
  blocks.push(make('create:large_cogwheel',...o.outputGearPos,{axis:'x'}));
  blocks.push(make('create:clutch',...o.clutchPos,{axis:'x',powered:removed.has(o.id)}));
  blocks.push(make('simulated:swivel_bearing',...o.motorBearingPos,{
   axis:'x',facing:o.side>0?'east':'west'
  }));
 }
 const graph=evaluate(blocks);
 const powered=enabled&&rpm>0;
 const bearings=DRIVE_OUTPUTS.map(o=>{
  const upstream=graph.rpms[k(o.motorBearingPos)];
  const nativeRPM=powered&&Number.isFinite(upstream)?upstream:0;
  const pathLive=powered&&Math.abs(nativeRPM)>.001&&!removed.has(o.id);
  // Configurable Create-LIKE gearbox reduction downstream from the
  // proven shaft topology. Do not assert the gearbox ratio is native-tested.
  const outputRPM=pathLive?nativeRPM/gearRatio:0;
  return {...o,pathLive,rpm:nativeRPM,outputRPM,
   direction:Math.sign(outputRPM),torqueMultiplier:pathLive?gearRatio*.9:0,
   powerPath:[...o.motorBearingPos]};
 });
 const live=bearings.filter(b=>b.pathLive);
 const loadSU=live.reduce((n,b)=>n+Math.abs(b.rpm)*8,0);
 const capacitySU=powered?Math.abs(rpm)*16384:0;
 const overloaded=loadSU>capacitySU;
 return {
  blocks,graph,
  motor:{id:'create:creative_motor',pos:[0,0,2],rpm:powered?rpm:0,powered,defaultCapacity:16384},
  gearRatio,live:live.length,bearings:bearings.map(b=>({
   ...b,pathLive:b.pathLive&&!overloaded,
   outputRPM:overloaded?0:b.outputRPM,
   torqueMultiplier:overloaded?0:b.torqueMultiplier
  })),
  capacitySU,loadSU,overloaded,
  assumptions:'Rotational connectivity follows verified Create shaft / perpendicular big-gear rules; gearbox reduction and 8 SU/RPM per Simulated bearing are simplified browser assumptions. Knee power couplers are not built.'
 };
}

export function validatePowerBus(bus) {
 const check=bus.bearings.every(o=>o.pathLive ? Math.abs(o.outputRPM)>0 : o.outputRPM===0);
 return {
  ok:check&&!bus.graph.errors.some(e=>e.code==='ROTATION_CONFLICT'),
  attached:bus.live,errors:bus.graph.errors,
  speeds:Object.fromEntries(bus.bearings.map(b=>[b.id,b.outputRPM]))
 };
}
