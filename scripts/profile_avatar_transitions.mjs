// Profile rendered signing transitions on the connected app WebView. Forward it
// with adb forward tcp:9223 localabstract:webview_devtools_remote_<app-pid> first.
import {writeFile, mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
const pages = await (await fetch('http://127.0.0.1:9223/json')).json();
const candidates = pages.filter(p => p.url.endsWith('/avatar/embed.html') && JSON.parse(p.description).visible);
if (candidates.length !== 1) throw Error(`Expected one visible avatar, found ${candidates.length}`);
const socket = new WebSocket(candidates[0].webSocketDebuggerUrl);
await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
let sequence=0;
const calls=new Map();
socket.onmessage=event=>{const message=JSON.parse(event.data);calls.get(message.id)?.(message);calls.delete(message.id)};
const send=(method,params={})=>new Promise((resolve,reject)=>{
  const id=++sequence,timer=setTimeout(()=>{calls.delete(id);reject(Error(`${method} timed out`))},45_000);
  calls.set(id,result=>{clearTimeout(timer);result.error?reject(Error(JSON.stringify(result.error))):resolve(result.result)});
  socket.send(JSON.stringify({id,method,params}));
});
try {
  const result=await send('Runtime.evaluate',{awaitPromise:true,returnByValue:true,expression:`(async()=>{
    if(!window.isReady())throw Error('Avatar is not ready');
    window.clearSigns();window.setPlaybackPaused(false);
    const texts=['Hello','thank you','world','Please','help','me','Zoe'];
    const completed=window.avatarDiagnostics().stats.completed;
    const entries=texts.map((text,index)=>({id:'profile-'+index,lineId:'profile',text,start:0,end:text.length}));
    window.enqueueCaptions(entries);
    const samples=[],started=performance.now();let frame=-1;
    return await new Promise((resolve,reject)=>{
      function sample(now){
        const d=window.avatarDiagnostics();
        if(d.frames!==frame){
          samples.push({at:now,id:d.currentId,time:d.time,frame:d.frames,joints:d.renderedJoints||d.joints,transition:d.transition});frame=d.frames;
        }
        if(d.stats.completed-completed>=entries.length){resolve({samples,stats:d.stats,transition:d.transition,elapsed:now-started});return}
        if(now-started>30_000){reject(Error('Signing profile timed out'));return}
        requestAnimationFrame(sample);
      }
      requestAnimationFrame(sample);
    });
  })()`});
  if(result.exceptionDetails)throw Error(JSON.stringify(result.exceptionDetails));
  const profile=result.result.value;
  const angle=(a,b)=>2*Math.acos(Math.min(1,Math.abs(a.reduce((sum,value,index)=>sum+value*b[index],0))));
  const gaps=[],seams=[];
  for(let index=1;index<profile.samples.length;index++){
    const before=profile.samples[index-1],after=profile.samples[index];
    gaps.push(after.at-before.at);
    if(before.id&&after.id&&before.id!==after.id){
      seams.push({from:before.id,to:after.id,gapMs:after.at-before.at,maxJointAngle:Math.max(...before.joints.map((q,joint)=>angle(q,after.joints[joint])))});
    }
  }
  gaps.sort((a,b)=>a-b);
  const summary={frames:profile.samples.length,elapsedMs:profile.elapsed,medianFrameMs:gaps[Math.floor(gaps.length*.5)],p95FrameMs:gaps[Math.floor(gaps.length*.95)],maxFrameMs:gaps.at(-1),seams,transition:profile.transition};
  const output=resolve('app/build/device-check',process.argv[2]||'transitions.json');
  await mkdir(resolve('app/build/device-check'),{recursive:true});
  await writeFile(output,JSON.stringify({summary,...profile},null,2));
  console.log(JSON.stringify(summary,null,2));
} finally {socket.close();}
