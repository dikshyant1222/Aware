// Install the official model into the debug app's private storage, byte for byte.
import {createReadStream} from 'node:fs';
import {stat} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {spawn, execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {pipeline} from 'node:stream/promises';

const run = promisify(execFile);
const adb = process.env.ADB_PATH || `${process.env.LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe`;
const model = process.argv[2] || 'C:/ab/models/gemma-4-E2B-it.litertlm';
const expectedHash = '181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c';
const expectedSize = 2588147712;
if ((await stat(model)).size !== expectedSize) throw Error('Incomplete or incorrect Gemma 4 E2B download');
const hash = createHash('sha256');
for await (const bytes of createReadStream(model)) hash.update(bytes);
if (hash.digest('hex') !== expectedHash) throw Error('Official model SHA-256 mismatch');
console.log('Official Gemma 4 E2B SHA-256 verified.');
const devices = (await run(adb, ['devices'])).stdout.split(/\r?\n/).filter(line => /\sdevice$/.test(line)).map(line => line.split(/\s+/)[0]);
const serial = process.argv[3] || (devices.length === 1 ? devices[0] : null);
if (!serial) throw Error('Specify one authorized device serial');
const call = async (...args) => {
  for(let attempt=0;attempt<3;attempt++) {
    await run(adb, ['-s', serial, 'wait-for-device'], {timeout:30000});
    try { return await run(adb, ['-s', serial, ...args]); }
    catch(error) { if(attempt===2||!/not found|offline|closed|daemon/.test(error.stderr||''))throw error; }
  }
};
await call('shell', 'am', 'force-stop', 'com.aware.app');
await call('shell', 'run-as', 'com.aware.app', 'mkdir', '-p', 'files/llm');
let alreadyCopied = false;
try { alreadyCopied=(await call('shell','run-as','com.aware.app','sha256sum','files/llm/model.litertlm.partial')).stdout.split(/\s+/)[0]===expectedHash; } catch {}
if(!alreadyCopied) {
const transfer = spawn(adb, ['-s', serial, 'exec-in', 'run-as', 'com.aware.app', 'dd',
  'of=files/llm/model.litertlm.partial', 'bs=1048576'], {windowsHide: true, stdio: ['pipe', 'ignore', 'pipe']});
let error = '';
transfer.stderr.on('data', bytes => {error += bytes;});
const completed = new Promise((resolve, reject) => {
  transfer.on('error', reject);
  transfer.on('close', code => code === 0 ? resolve() : reject(Error(`Model transfer failed: ${error}`)));
});
await Promise.all([pipeline(createReadStream(model), transfer.stdin), completed]);
}
const remoteHash = (await call('shell', 'run-as', 'com.aware.app', 'sha256sum', 'files/llm/model.litertlm.partial')).stdout.split(/\s+/)[0];
if (remoteHash !== expectedHash) throw Error('Device copy SHA-256 mismatch; previous model retained');
await call('shell', 'run-as', 'com.aware.app', 'mv', 'files/llm/model.litertlm.partial', 'files/llm/model.litertlm');
console.log('Gemma 4 E2B installed and verified in Aware private storage on '+serial+'.');
