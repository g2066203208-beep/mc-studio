// RPMS source-audited kinetic topology preview. NOT a reimplementation of Create/Sable.
// Source: Create RotationPropagator.java mc1.21.1/dev@7ff2760; Simulated SwivelBearingBlock.java@3705df2
export const CORE_VERSION='strict-v1';
const key=p=>p.join(',');
const eq=(a,b)=>a.every((v,i)=>v===b[i]);
const diff=(a,b)=>b.map((v,i)=>v-a[i]);
const axisVec={x:[1,0,0],y:[0,1,0],z:[0,0,1]};
const dot=(v,a)=>v['xyz'.indexOf(a)];
const man=v=>v.reduce((n,x)=>n+Math.abs(x),0);
const DIRECTION_AXIS={north:'z',south:'z',east:'x',west:'x',up:'y',down:'y'};
const AXIS_TYPES=new Set(['shaft','cogwheel','large_cogwheel','andesite_encased_shaft','brass_encased_shaft',
'andesite_encased_cogwheel','brass_encased_cogwheel','andesite_encased_large_cogwheel','brass_encased_large_cogwheel']);
const SMALL=new Set(['cogwheel','andesite_encased_cogwheel','brass_encased_cogwheel']);
const LARGE=new Set(['large_cogwheel','andesite_encased_large_cogwheel','brass_encased_large_cogwheel']);
const SHAFT=new Set(['shaft','andesite_encased_shaft','brass_encased_shaft']);
export const SOURCE_IDS=new Set(['create:creative_motor']);
export const KINETIC_IDS=new Set(['create:creative_motor','create:hand_crank','create:water_wheel','create:large_water_wheel',
'create:mechanical_press','create:mechanical_mixer','create:mechanical_drill','create:mechanical_saw',
'create:mechanical_pump','create:mechanical_bearing','create:gearbox','create:clutch','create:gearshift',
'create:rotation_speed_controller','create:stressometer','create:speedometer',
'create:encased_chain_drive','create:windmill_bearing','create:steam_engine',
'simulated:swivel_bearing','simulated:analog_transmission','simulated:rope_winch']);
export const MECHANICS={
 'create:shaft':{kind:'shaft',desc:'直线旋转轴：仅沿 axis 方向连接动力。',coverage:'topology'},
 'create:andesite_encased_shaft':{kind:'shaft',desc:'安山岩包覆轴，保持轴向旋转连接。',coverage:'topology'},
 'create:brass_encased_shaft':{kind:'shaft',desc:'黄铜包覆轴，保持轴向旋转连接。',coverage:'topology'},
 'create:cogwheel':{kind:'small',desc:'小齿轮：同轴传动、相邻平行小齿轮反转。',coverage:'topology'},
 'create:large_cogwheel':{kind:'large',desc:'大齿轮：同轴、与小齿轮对角啮合时 2:1 传速。',coverage:'topology'},
 'create:creative_motor':{kind:'source',desc:'创造模式动力源；允许输入预览 RPM，真实 SU 参数以 MC 配置为准。',coverage:'speed-preview'},
 'simulated:swivel_bearing':{kind:'bearing',desc:'动力输入与旋转轴同轴；只有真实物理装配成功才会出现活动子刚体。',coverage:'input-topology-only'},
 'create:mechanical_press':{kind:'consumer',desc:'机械压印机需动力和加工环境。网页不执行配方。',coverage:'input-topology-only'},
 'create:mechanical_mixer':{kind:'consumer',desc:'机械搅拌器需动力、工作盆与配方。网页不执行配方。',coverage:'input-topology-only'}
};
export const RULES={topology:'Create RotationPropagator.java 1.21.1: shaft and small cog gear link, large/small diagonal ratio. Axes, facing and distance matter.',assembly:'Simulated swivel bearing requires in-game assembled sub-level, correct facing, Create input and Sable constraint.',stress:'Stress capacities and impacts are server-configurable. No arbitrary SU values are assumed.'};
export function behavior(id){
 if(MECHANICS[id])return MECHANICS[id];
 if(KINETIC_IDS.has(id)||id.startsWith('simulated:'))return {kind:'native-only',desc:'注册名可用；完整 BlockEntity / 物理 / 方块交互仅由原版 Mod 执行。',coverage:'requires-game'};
 return {kind:'block',desc:'可用于方块布局；放置、合成、红石或特殊行为必须在 MC 原版引擎验证。',coverage:'requires-game'};
}
export function axisOf(b){
 if(typeof b.axis==='string'&&'xyz'.includes(b.axis))return b.axis;
 return DIRECTION_AXIS[b.facing]||'y';
}
export function isKinetic(b){
 const id=b.id,tail=id.split(':')[1];
 return (b.id.startsWith('create:')&&AXIS_TYPES.has(tail))||KINETIC_IDS.has(id);
}
function kind(b){return behavior(b.id).kind;}
function hasShaft(b,along){
 const k=kind(b),a=axisOf(b);
 if(k==='shaft'||k==='small'||k==='large')return along===a;
 if(k==='source')return along===a;
 if(k==='bearing')return along===a; // assembly remains unverified
 if(k==='consumer')return along===a; // conservative topology indicator only
 return false;
}
// Returns multiplicative directed rotation speed link, or 0 when the source rule doesn't establish a link.
// Unsupported special mechanisms deliberately never form authoritative links.
export function rotationRatio(a,b){
 const v=diff(a.pos,b.pos),d=man(v);
 const ak=kind(a),bk=kind(b),aa=axisOf(a),ba=axisOf(b);
 if(d===1){
  const along=v.findIndex(x=>x!==0),axis='xyz'[along];
  if(hasShaft(a,axis)&&hasShaft(b,axis))return 1;
  if(ak==='small'&&bk==='small'&&aa===ba&&axis!==aa)return -1;
 }
 if(d===2){
  const offsets=v.filter(x=>Math.abs(x)===1).length;
  if(offsets!==2)return 0;
  if(ak==='large'&&bk==='small'&&aa===ba&&dot(v,aa)===0)return -2;
  if(ak==='small'&&bk==='large'&&aa===ba&&dot(v,aa)===0)return -.5;
  if(ak==='large'&&bk==='large'&&aa!==ba){
   const unused='xyz'.split('').find(ax=>ax!==aa&&ax!==ba);
   if(dot(v,unused)!==0||dot(v,aa)===0||dot(v,ba)===0)return 0;
   return Math.sign(dot(v,aa))===Math.sign(dot(v,ba))?1:-1;
  }
 }
 return 0;
}
export function canPlace(world,b,opts={}){
 if(!b||!Array.isArray(b.pos)||b.pos.length!==3||b.pos.some(n=>!Number.isInteger(n)))return {ok:false,message:'方块坐标必须是整数网格'};
 const [x,y,z]=b.pos;
 if(Math.abs(x)>16||Math.abs(z)>16||y<1||y>16)return {ok:false,message:'超出演示施工区域（X/Z ±16、Y 1–16）'};
 if(world.some(o=>eq(o.pos,b.pos)))return {ok:false,message:'此位置已有方块，不能重叠放置'};
 if(!opts.ignoreSupport&&y>1){
  const dirs=[[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]];
  if(!dirs.some(v=>world.some(o=>eq(o.pos,[x+v[0],y+v[1],z+v[2]]))))
   return {ok:false,message:'悬空放置：需要点击相邻方块面或搭建支撑'};
 }
 return {ok:true,message:'坐标合法（特殊方块生存条件仍待原版核验）'};
}
export function evaluate(world,options={}){
 const maxRpm=Number.isFinite(options.maxRpm)?options.maxRpm:256;
 const errors=[],warnings=[],kinetic=world.filter(isKinetic),nodes=new Map(kinetic.map(b=>[key(b.pos),b]));
 const edges=[],edgesFrom=new Map(kinetic.map(b=>[key(b.pos),[]]));
 for(let i=0;i<kinetic.length;i++)for(let j=i+1;j<kinetic.length;j++){
  const a=kinetic[i],b=kinetic[j],r=rotationRatio(a,b);
  if(r!==0){
   const ak=key(a.pos),bk=key(b.pos);
   edges.push({from:ak,to:bk,ratio:r});
   edgesFrom.get(ak).push({to:bk,ratio:r});
   edgesFrom.get(bk).push({to:ak,ratio:1/r});
  }
 }
 const speed=new Map(),poweredBy=new Map(),conflicts=new Set();
 const sources=kinetic.filter(b=>SOURCE_IDS.has(b.id));
 for(const source of sources){
  const start=key(source.pos);
  const rpm=Number(source.rpm??16);
  if(!Number.isFinite(rpm)||rpm===0||Math.abs(rpm)>maxRpm){
   errors.push({at:start,code:'INVALID_RPM',detail:'动力源转速必须非零且不超过预览限制'});continue;
  }
  const queue=[{at:start,value:rpm,source:start}];let n=0;
  while(queue.length&&n++<10000){
   const item=queue.shift(),existing=speed.get(item.at);
   if(existing!==undefined){
    if(Math.abs(existing-item.value)>0.001){conflicts.add(item.at);errors.push({at:item.at,code:'ROTATION_CONFLICT',detail:'多个传动路径要求不同转速或反向旋转'});}
    continue;
   }
   if(Math.abs(item.value)>maxRpm){errors.push({at:item.at,code:'OVERSPEED_PREVIEW',detail:'传动比导致转速超过预览阈值，原版结果需游戏检查'});continue;}
   speed.set(item.at,item.value);poweredBy.set(item.at,item.source);
   for(const e of edgesFrom.get(item.at)||[])queue.push({at:e.to,value:item.value*e.ratio,source:item.source});
  }
 }
 for(const b of kinetic){
  const k=key(b.pos);
  if(kind(b)==='native-only')warnings.push({at:k,code:'NATIVE_BE_REQUIRED',detail:b.id+'：缺少原版 BlockEntity 机制，不参与网页动力计算'});
  if(!speed.has(k)&&!SOURCE_IDS.has(b.id))warnings.push({at:k,code:'UNPOWERED_OR_UNSUPPORTED',detail:b.id+'：未建立已支持规则的动力输入'});
  if(kind(b)==='bearing')warnings.push({at:k,code:'SABLE_NOT_SIMULATED',detail:'轴承真实装配、胶水范围、刚体质量、力矩及 Sable 约束尚未验证'});
 }
 if(world.some(b=>b.id.startsWith('create:')||b.id.startsWith('simulated:')))
  warnings.push({at:null,code:'NATIVE_GAME_REQUIRED',detail:'本网页只检查部分方块动力拓扑；应力、红石、合成、流体、机器执行与航空学物理必须在 NeoForge 客户端验证'});
 return {status:errors.length?'CONFLICT':warnings.length?'PREVIEW_UNVERIFIED':'EMPTY',errors,warnings,
  summary:{blocks:world.length,kinetic:kinetic.length,links:edges.length,powered:speed.size,sources:sources.length},
  edges,rpms:Object.fromEntries(speed),conflicts:[...conflicts],testedRules:['shaft-axial','small-cog-adjacent','small-large-diagonal','large-large-perpendicular-diagonal']};
}
