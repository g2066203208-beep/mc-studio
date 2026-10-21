// Create 6.0.10 / MC 1.21.1 preview-only stress budget.
// The default multipliers below are source-audited from
// Creators-of-Create/Create mc1.21.1/dev AllBlocks.java CStress.setImpact()
// and CStress.setCapacity(). Actual server stressValues.v2 may be changed.
// No browser-side result is represented as authoritative in-game SU.
import {evaluate,axisOf} from './strict-core.mjs';

export const STRESS_DEFAULTS={
 'create:creative_motor':{capacity:16384},
 'create:water_wheel':{capacity:32},'create:large_water_wheel':{capacity:128},
 'create:mechanical_press':{impact:8},'create:mechanical_mixer':{impact:4},
 'create:mechanical_drill':{impact:4},'create:mechanical_saw':{impact:4},
 'create:mechanical_pump':{impact:4},'create:mechanical_bearing':{impact:4},
 'create:clockwork_bearing':{impact:4},'create:rope_pulley':{impact:4},
 'create:mechanical_piston':{impact:4},'create:sticky_mechanical_piston':{impact:4},
 'create:millstone':{impact:4},'create:deployer':{impact:4},
 'create:encased_fan':{impact:2},'create:mechanical_crafter':{impact:2},
 'create:crushing_wheel':{impact:8},'create:mechanical_arm':{impact:8},
 'create:shaft':{impact:0},'create:cogwheel':{impact:0},
 'create:large_cogwheel':{impact:0},'create:clutch':{impact:0}
};
const key=b=>b.pos.join(',');
const finite=(x,d)=>Number.isFinite(+x)?+x:d;
export function previewStress(world,{capacityScale=1,impactScale=1,overrides={},maxRpm=256}={}){
 const base=evaluate(world,{maxRpm});
 const parent=new Map();
 const find=x=>{if(!parent.has(x))parent.set(x,x);const p=parent.get(x);if(p===x)return x;const root=find(p);parent.set(x,root);return root};
 const union=(a,b)=>{const x=find(a),y=find(b);if(x!==y)parent.set(y,x)};
 for(const b of world)find(key(b));
 for(const e of base.edges)union(e.from,e.to);
 const groups=new Map(),byPos={};
 for(const b of world){const pos=key(b),id=find(pos);
  if(!groups.has(id))groups.set(id,{root:id,blocks:[],capacity:0,impact:0,unconfigured:[],sources:[],overstressed:false,status:'idle'});
  groups.get(id).blocks.push(b);
 }
 for(const g of groups.values()){
  const members=g.blocks.filter(b=>base.rpms[key(b)]!==undefined),sources=members.filter(b=>b.id==='create:creative_motor');
  for(const b of members){
   const pos=key(b),rpm=base.rpms[pos],cfg=overrides[b.id]||STRESS_DEFAULTS[b.id],entry={id:b.id,rpm,axis:axisOf(b),capacity:0,impact:0};
   if(cfg){
    const cap=finite(cfg.capacity,0),impact=finite(cfg.impact,0);
    if(cap>0 && b.id==='create:creative_motor')entry.capacity=cap*Math.abs(rpm)*Math.max(0,capacityScale);
    else if(cap>0 && b.id!=='create:creative_motor')g.unconfigured.push(b.id+'：动力源运行条件未模拟，未计入应力容量');
    if(impact>0)entry.impact=impact*Math.abs(rpm)*Math.max(0,impactScale);
   }else if(b.id.startsWith('create:')||b.id.startsWith('simulated:')||b.id.startsWith('aeronautics:')){
    g.unconfigured.push(b.id+'：尚未核实原版 SU 参数');
   }
   g.capacity+=entry.capacity;g.impact+=entry.impact;byPos[pos]=entry;
  }
  g.sources=sources.map(key);
  g.overstressed=g.sources.length>0&&g.impact>g.capacity+1e-6;
  g.status=g.sources.length?g.overstressed?'overstressed':'running':'idle';
  for(const b of g.blocks)if(byPos[key(b)]){byPos[key(b)].overstressed=g.overstressed;byPos[key(b)].network=g.root;}
  g.ratio=g.capacity>0?g.impact/g.capacity:(g.impact>0?Infinity:0);
  g.blocks=g.blocks.map(b=>key(b));
 }
 const errors=[...base.errors];
 const warnings=[...base.warnings];
 for(const g of groups.values()){
  if(g.overstressed)errors.push({code:'OVERSTRESSED_PREVIEW',at:g.root,detail:'预演应力过载：需求 '+g.impact.toFixed(1)+' SU > 已知容量 '+g.capacity.toFixed(1)+' SU；模拟传动停止（请核对游戏配置）'});
  if(g.unconfigured.length&&g.sources.length)warnings.push({code:'UNKNOWN_STRESS_SETTINGS',at:g.root,detail:[...new Set(g.unconfigured)].join('；')});
 }
 const rpm={};
 for(const [p,n] of Object.entries(base.rpms))rpm[p]=byPos[p]?.overstressed?0:n;
 return {base,rpms:rpm,byPos,networks:[...groups.values()],
  summary:{...base.summary,capacity:[...groups.values()].reduce((n,g)=>n+g.capacity,0),
   impact:[...groups.values()].reduce((n,g)=>n+g.impact,0),
   overstressed:[...groups.values()].filter(g=>g.overstressed).length,
   running:Object.values(rpm).filter(n=>Math.abs(n)>0.001).length},
  errors,warnings,assumptions:'预览采用 Create 6.0.10 默认配置的已核实部分方块应力值；未知物理/输出源不计入容量；不等于实际游戏'}
}
