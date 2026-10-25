// Record only the hand canvas. No microphone, camera or server upload.
export function createHandVideo(source,onDone,onError){
 if(!globalThis.MediaRecorder||!HTMLCanvasElement.prototype.captureStream)throw Error('Video export is unavailable in this browser. Try Chrome or Edge.');
 const canvas=document.createElement('canvas');canvas.width=720;canvas.height=720;
 const ctx=canvas.getContext('2d'),stream=canvas.captureStream(30);
 const mime=['video/webm;codecs=vp9','video/webm;codecs=vp8','video/webm','video/mp4'].find(t=>MediaRecorder.isTypeSupported(t));
 let recorder;try{recorder=new MediaRecorder(stream,mime?{mimeType:mime,videoBitsPerSecond:4000000}:undefined)}catch(e){stream.getTracks().forEach(t=>t.stop());throw e}
 let cancelled=false,finished=false;const chunks=[];
 const cleanup=()=>stream.getTracks().forEach(t=>t.stop());
 const frame=()=>{ctx.fillStyle='#edf1e7';ctx.fillRect(0,0,720,720);const scale=Math.min(720/source.width,720/source.height),w=source.width*scale,h=source.height*scale;ctx.drawImage(source,(720-w)/2,(720-h)/2,w,h)};
 recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data)};
 recorder.onerror=()=>{cancelled=true;cleanup();onError(new Error('Recording failed. Please try a shorter message.'))};
 recorder.onstop=()=>{cleanup();if(cancelled)return;const blob=new Blob(chunks,{type:recorder.mimeType||mime||'video/webm'});if(!blob.size){onError(new Error('No video frames were recorded. Please try again.'));return}onDone(blob,blob.type.includes('mp4')?'mp4':'webm')};
 frame();recorder.start(250);
 return {frame,stop(cancel=false){if(finished)return;finished=true;cancelled=cancel;if(recorder.state!=='inactive')recorder.stop();else cleanup()}};
}
