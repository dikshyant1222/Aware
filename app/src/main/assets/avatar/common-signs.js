import {compileUtterance} from './vendor/sign-speech.js';
import {SIGNS,lookupSign,signLookup} from './vendor/sign-dictionary.js';
export const VOCABULARY=[['Hello','HELLO'],['Thank you','THANK'],['Please','PLEASE'],['Sorry','SORRY'],['Yes','YES'],['No','NO'],['Help','HELP'],['Good','GOOD'],['More','MORE'],['Stop','STOP'],['Love','LOVE'],['Name','NAME'],['You','YOU'],['Me','ME']];
const supported=new Set(VOCABULARY.map(v=>v[1]));
const display=w=>w==='THANKYOU'||w==='THANK'||w==='THANKS'?'THANK YOU':w;
export function compileMessage(input,spellingOnly=false,options={}){
 const cleaned=String(input).toUpperCase().replace(/[’']/g,'').replace(/[^A-Z0-9\s-]/g,' ').replace(/-/g,' ').replace(/\s+/g,' ').trim();
 const text=spellingOnly?cleaned:cleaned.replace(/\bTHANK\s+YOU\b/g,'THANKYOU');
 const live=options.live===true,continuous=options.continuous===true,rate=live?1.15:.7,lookup=signLookup({rate});
 const timing=live?{holdSeconds:.20,transitionSeconds:.16,motionSeconds:.6,leadSeconds:.08,tailSeconds:.12}:{holdSeconds:.82,transitionSeconds:.3,motionSeconds:1.25,leadSeconds:.35,tailSeconds:.4};
 const result=compileUtterance(text,{signs:spellingOnly?null:(word,pos)=>{const found=lookupSign(word);return found&&supported.has(found.name)?lookup(word,pos):null},maxSeconds:Infinity,timing,continuous});
 const marks=[],cues=[];let previousRight='RELAXED',previousLeft='RELAXED';
 function cue(start,transitionEnd,end,right,left){cues.push({start,transitionEnd,end,right,left,previousRight,previousLeft});previousRight=right;previousLeft=left}
 for(let i=0;i<result.segments.length;i++){
 const segment=result.segments[i];
 if(segment.signed){const found=lookupSign(segment.word),phases=found.sign.phases;const twoHanded=phases.some(p=>p.both||p.left);marks.push({kind:'sign',label:display(segment.word),word:segment.word,start:segment.start,end:segment.end,description:found.sign.gloss,twoHanded});let cursor=segment.start;
 phases.forEach((p,j)=>{const duration=((p.t??.3)+(!continuous&&i===0&&j===0?.3:0))/rate;let right=p.right?.shape||p.both?.shape||'RELAXED',left=p.left?.shape||p.both?.shape||'RELAXED';if(found.name==='NO'&&j===1)right='PINCH_TWO';cue(cursor,cursor+duration,cursor+duration+(p.hold||0)/rate,right,left);cursor+=duration+(p.hold||0)/rate});
 if(!continuous&&i===result.segments.length-1)cue(cursor,segment.end,segment.end,'RELAXED','RELAXED');
 }else{
 for(const m of segment.letters){marks.push({...m,kind:'letter',label:m.letter,word:segment.word});cue(m.start,m.start+timing.transitionSeconds,m.end,m.letter,'RELAXED')}
 if(!continuous&&i===result.segments.length-1){const end=segment.letters.at(-1)?.end||segment.start;cue(end,segment.end,segment.end,'RELAXED','RELAXED')}
 }
 }
 return {clip:result.clip,marks,cues,signed:result.signed.map(display),spelled:result.spelled};
}
export function applyHandCues(rig,time,cues,cache){if(!cues.length)return;const c=cues.find(c=>time<c.end)||cues.at(-1);const t=Math.max(0,Math.min(1,(time-c.start)/Math.max(.001,c.transitionEnd-c.start))),smooth=t*t*t*(10+t*(-15+6*t));for(const side of ['Right','Left']){const key=side==='Right'?'right':'left',previous=side==='Right'?'previousRight':'previousLeft';const get=shape=>{const id=side+shape;if(!cache.has(id))cache.set(id,rig.pose(shape,side));return cache.get(id)};const a=get(c[previous]),b=get(c[key]);for(const [name,q]of b){rig.joints.get(name).b.quaternion.copy(a.get(name)||q).slerp(q,smooth)}}}
