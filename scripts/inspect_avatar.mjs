// Local development inspection of the app's debug WebView forwarded by adb.
import {writeFile} from 'node:fs/promises';
const pages = await (await fetch('http://127.0.0.1:9223/json')).json();
const visible = pages.filter(p => p.url.endsWith('/avatar/embed.html') && JSON.parse(p.description).visible);
if (visible.length !== 1) throw Error(`Expected one visible avatar WebView, found ${visible.length}`);
const socket = new WebSocket(visible[0].webSocketDebuggerUrl);
await new Promise((resolve, reject) => {socket.onopen = resolve; socket.onerror = reject;});
let nextId = 0;
const pending = new Map();
socket.onmessage = event => {
  const message = JSON.parse(event.data);
  if (pending.has(message.id)) {
    pending.get(message.id)(message);
    pending.delete(message.id);
  }
};
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++nextId;
  const timeout = setTimeout(() => {pending.delete(id); reject(Error(`${method} timed out`));}, 10_000);
  pending.set(id, message => {clearTimeout(timeout); resolve(message);});
  socket.send(JSON.stringify({id, method, params}));
});
try {
  if (process.argv.includes('--repair-layout')) {
    await send('Runtime.evaluate', {expression: `
      const height=visualViewport?.height||innerHeight;
      document.documentElement.style.height=height+'px';document.body.style.height=height+'px';
      document.getElementById('avatar3d').style.cssText='position:fixed;inset:0;width:'+innerWidth+'px;height:'+height+'px';
      document.querySelector('canvas').style.cssText='display:block;width:100%;height:100%';
      new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve(true))))
    `, awaitPromise: true});
  }
  if (process.argv.includes('--resume')) await send('Runtime.evaluate', {expression: 'window.setPlaybackPaused(false)'});
  if (process.argv.includes('--sign')) await send('Runtime.evaluate', {expression: 'window.enqueueCaptions([{id:"render-check",text:"Hello"}])'});
  const result = await send('Runtime.evaluate', {expression: `JSON.stringify({
    ready:window.isReady?.(), hidden:document.hidden,
    viewport:{width:innerWidth,height:innerHeight,visual:visualViewport?.height},
    root:[document.documentElement,document.body].map(e=>({height:getComputedStyle(e).height,rect:e.getBoundingClientRect().toJSON()})),
    host:document.getElementById('avatar3d').getBoundingClientRect().toJSON(),
    canvas:[...document.querySelectorAll('canvas')].map(c=>({width:c.width,height:c.height,rect:c.getBoundingClientRect().toJSON()})),
    loading:document.getElementById('loading').textContent,
    diagnostics:window.avatarDiagnostics?.(),
  })`, returnByValue: true});
  console.log(result.result?.result?.value || JSON.stringify(result));
  if (process.argv.includes('--screenshot')) {
    const screenshot = await send('Page.captureScreenshot', {format: 'png'});
    await writeFile(new URL('../app/build/device-check/webview.png', import.meta.url), Buffer.from(screenshot.result.data, 'base64'));
  }
} finally {socket.close();}
