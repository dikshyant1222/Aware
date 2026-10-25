// Exercise the bundled HTML, WebGL model, and Android bridge callbacks in a
// clean headless Chrome profile. Android speech recognition still needs a device.
import {createServer} from 'node:http';
import {readFile, mkdir, mkdtemp, writeFile} from 'node:fs/promises';
import {resolve, extname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../app/src/main/assets/avatar/', import.meta.url));
const output = fileURLToPath(new URL('../app/build/avatar-smoke/', import.meta.url));
await mkdir(output, {recursive: true});
const profile = await mkdtemp(join(output, 'chrome-'));
const events = [];
const errors = [];
let finish;
const result = new Promise(resolve => {finish = resolve;});
const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/event') {
      const event = JSON.parse(url.searchParams.get('data'));
      events.push(event);
      if (event[0] === 'error') errors.push(event[1]);
      if (event[0] === 'done' || event[0] === 'error') finish();
      res.end('ok');
      return;
    }
    const filename = resolve(root, '.' + decodeURIComponent(url.pathname));
    if (!filename.startsWith(resolve(root) + '\\')) {res.writeHead(403).end(); return;}
    const mime = {'.html': 'text/html', '.js': 'text/javascript', '.glb': 'model/gltf-binary', '.webp': 'image/webp'};
    let body = await readFile(filename);
    if (url.pathname === '/embed.html') {
      // Only the served test copy receives a fake Android bridge.
      body = Buffer.from(body.toString().replace('<script>', `<script>
        const report=(...event)=>fetch('/event?data='+encodeURIComponent(JSON.stringify(event)));
        const started=[],completed=[],seams=[];
        let previousFrame=-1,previousId=null,previousJoints=null,finished=false;
        function watchTransitions(){
          const d=window.avatarDiagnostics();
          if(d.frames!==previousFrame){
            if(previousId&&d.currentId&&previousId!==d.currentId){
              const angle=(a,b)=>2*Math.acos(Math.min(1,Math.abs(a.reduce((sum,v,i)=>sum+v*b[i],0))));
              seams.push(Math.max(...previousJoints.map((q,i)=>angle(q,d.renderedJoints[i]))));
            }
            previousFrame=d.frames;previousId=d.currentId;previousJoints=d.renderedJoints;
          }
          if(!finished)requestAnimationFrame(watchTransitions);
        }
        window.AwareBridge={
          onAvatarReady(){
            const canvas=document.querySelector('canvas');
            const rect=canvas.getBoundingClientRect();
            if(!canvas.width||!canvas.height||rect.width<=0||rect.height<=0){report('error','Avatar canvas has no visible area');return}
            if(Math.abs(rect.width-innerWidth)>2||Math.abs(rect.height-(visualViewport?.height||innerHeight))>2){report('error','Avatar canvas does not fit its viewport');return}
            report('ready',JSON.stringify({width:rect.width,height:rect.height}));
            requestAnimationFrame(watchTransitions);
            window.enqueueCaptions([{id:'1',text:'Yes'},{id:'2',text:'No'}]);
            window.enqueueCaptions([{id:'2',text:'No'},{id:'3',text:'Help'}]);
          },
          onSigningStarted(id){
            started.push(id);report('start',id);
            if(id==='3'){
              window.clearSigns();
              window.enqueueCaptions([{id:'4',text:'Me'},{id:'5',text:'You'}]);
              window.clearSigns();
              setTimeout(()=>{
                finished=true;
                report('done',JSON.stringify({started,completed,seams,active:document.getElementById('idle-indicator').classList.contains('active')}));
              },300);
            }
          },
          onSigningComplete(id){completed.push(id);report('complete',id)},
          onAvatarError(error){report('error',error)},
        };
      </script><script>`));
    }
    res.setHeader('Content-Type', mime[extname(filename)] || 'application/octet-stream');
    res.end(body);
  } catch {
    res.writeHead(404).end();
  }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const port = server.address().port;
const executable = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const browser = spawn(executable, [
  '--headless=new', '--no-first-run', '--no-default-browser-check',
  '--use-angle=swiftshader', '--enable-unsafe-swiftshader',
  '--disable-background-timer-throttling', '--window-size=600,800',
  `--user-data-dir=${profile}`, `http://127.0.0.1:${port}/embed.html`,
], {windowsHide: true, stdio: ['ignore', 'ignore', 'pipe']});
let stderr = '';
browser.stderr.on('data', chunk => {stderr += chunk;});
browser.on('error', error => {errors.push(String(error)); finish();});
browser.on('exit', code => {if (code) {errors.push(`Chrome exited ${code}`); finish();}});
const timer = setTimeout(() => {errors.push('Avatar runtime test timed out'); finish();}, 60_000);
try {
  await result;
  await writeFile(join(output, 'result.json'), JSON.stringify({events, errors}, null, 2));
  await writeFile(join(output, 'chrome.log'), stderr);
  assert.deepEqual(errors, []);
  const completed = JSON.parse(events.find(e => e[0] === 'done')?.[1] || '{}');
  assert.deepEqual(completed.started, ['1', '2', '3', '4']);
  assert.deepEqual(completed.completed, ['1', '2', '3', '4']);
  assert.equal(completed.active, false);
  assert.equal(completed.seams.length,1);
  assert.ok(completed.seams[0]<.08, 'Rendered pose jumped at the word handoff: '+completed.seams[0]);
  console.log('Avatar WebGL runtime passed: model ready, ordered signs, smooth handoff, bridge callbacks, duplicate filtering, clear/cancel.');
  console.log('Word-boundary joint change: '+(completed.seams[0]*180/Math.PI).toFixed(3)+' degrees.');
} finally {
  clearTimeout(timer);
  browser.kill();
  server.closeAllConnections();
  server.close();
}
