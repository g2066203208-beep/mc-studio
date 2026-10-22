/**
 * Grounded 2-link six-legged robot gait planner.
 *
 * All target feet are in the chassis-relative YZ plane; the ACTUAL feet and
 * chassis are separate dynamic Rapier bodies. No leg/world transforms are set.
 *
 * Angles are relative to body and upper leg around the physical X hinges.
 * The planner only returns joint commands and contact-intent; motors must move
 * the bodies against gravity and friction to realize them.
 */
export const STANCE_PORTION=.62;
export const MAX_SWING_HEIGHT=.64;
export const STANCE_HEIGHT=4.28;
const clamp=(v,a,b)=>Math.min(b,Math.max(a,v));
const smooth=t=>t*t*(3-2*t);
const wrap=t=>((t%1)+1)%1;

/** Solve knee-positive branch for two physical links measured hip-to-foot. */
export function solveLegIK({forward=0,vertical=-4.28,upper=2,lower=2.5}={}){
 const radius=Math.hypot(forward,vertical);
 const minReach=Math.abs(upper-lower)+1e-3;
 const maxReach=upper+lower-1e-3;
 const reach=clamp(radius,minReach,maxReach);
 const cosK=clamp((reach*reach-upper*upper-lower*lower)/(2*upper*lower),-1,1);
 const knee=Math.acos(cosK);
 // Rotating a downward Y link around +X gives a negative Z displacement.
 const aim=Math.atan2(-forward,-vertical);
 const offset=Math.atan2(lower*Math.sin(knee),upper+lower*Math.cos(knee));
 const hip=aim-offset;
 return {hip:clamp(hip,-.85,.85),knee:clamp(knee,-1.24,1.24),
  reachable:radius>=minReach&&radius<=upper+lower,
  target:{forward,vertical},error:Math.abs(radius-reach)};
}
/**
 * Use opposite tripod phases. During stance the foot slides backward
 * RELATIVE to the body (+Z = backward because nose is at -Z). Ground friction
 * can consequently propel the physical chassis toward world -Z.
 *
 * During swing, the foot returns forward in the air without pushing on ground.
 * The intended lift is not a claim of actual contact loss: Rapier measures it.
 */
export function planTripodFoot(cycle,tripod,{stride=1.15,clearance=MAX_SWING_HEIGHT,
   stance=STANCE_PORTION}={}){
 const u=wrap(cycle+(tripod?.5:0));
 let forward,vertical,phase;
 if(u<stance){
  const s=u/stance;
  phase='stance';
  forward=-stride*.5+stride*s;
  vertical=-STANCE_HEIGHT;
 }else{
  const s=(u-stance)/(1-stance);
  phase='swing';
  forward=stride*.5-stride*smooth(s);
  vertical=-(STANCE_HEIGHT-clearance*Math.sin(Math.PI*s));
 }
 return {...solveLegIK({forward,vertical}),phase,cycle:u};
}
