const test=require('node:test');const assert=require('node:assert/strict');const {load,storage}=require('./load.cjs');
function harness(fetcher){
 const disk=storage(),handles=new Map();const indexedDB={open:()=>{const request={};setImmediate(()=>{request.result={close(){},transaction(){const tx={objectStore:()=>({put(value,id){handles.set(id,value);const r={result:value};setImmediate(()=>tx.oncomplete());return r;},get(id){const r={result:handles.get(id)};setImmediate(()=>tx.oncomplete());return r;}})};return tx;}};request.onsuccess();});return request;}};
 const api=load('lib/downloads.ts',{'./storage':disk},{indexedDB,fetch:fetcher,window:{}});
 let data=new Uint8Array(),position=0;const handle={requestPermission:async()=> 'granted',getFile:async()=>({size:data.length}),createWritable:async()=>({seek:async n=>{position=n;},truncate:async n=>{const next=new Uint8Array(n);next.set(data.slice(0,n));data=next;},write:async bytes=>{const next=new Uint8Array(Math.max(data.length,position+bytes.length));next.set(data);next.set(bytes,position);position+=bytes.length;data=next;},close:async()=>{}})};
 return {api,handle,disk,bytes:()=>Array.from(data),handles};
}
const tick=()=>new Promise(r=>setImmediate(r));
async function done(api){for(let i=0;i<30;i++){await tick();const e=api.downloadEntries()[0];if(e&&!['downloading','paused'].includes(e.status))return e;}throw Error('Download did not settle');}
test('managed download verifies transferred bytes and records a completed local file',async()=>{
 const h=harness(async()=>new Response(new Uint8Array([1,2,3]),{headers:{'Content-Length':'3'}}));await h.api.startManagedDownload('Fixture','https://example.test/file','account:profile',h.handle);
 const entry=await done(h.api);assert.equal(entry.status,'complete');assert.equal(entry.received,3);assert.deepEqual(h.bytes(),[1,2,3]);
});
test('interrupted download resumes the partial file with Range rather than duplicating bytes',async()=>{
 const requests=[];let n=0;const h=harness(async(_url,options)=>{requests.push(options.headers);if(++n===1)return new Response(new Uint8Array([1,2]),{headers:{'Content-Length':'4'}});return new Response(new Uint8Array([3,4]),{status:206,headers:{'Content-Range':'bytes 2-3/4','Content-Length':'2'}});});
 await h.api.startManagedDownload('Fixture','https://example.test/file','a:p',h.handle);let entry=await done(h.api);assert.equal(entry.status,'failed');
 await h.api.resumeDownload(entry.id);entry=h.api.downloadEntries()[0];assert.equal(entry.status,'complete');assert.equal(requests[1].Range,'bytes=2-');assert.deepEqual(h.bytes(),[1,2,3,4]);
});
test('a provider that ignores Range restarts cleanly instead of corrupting the file',async()=>{
 let n=0;const h=harness(async()=>++n===1?new Response(new Uint8Array([1,2]),{headers:{'Content-Length':'4'}}):new Response(new Uint8Array([1,2,3,4])));
 await h.api.startManagedDownload('Fixture','https://example.test/file','a:p',h.handle);const entry=await done(h.api);await h.api.resumeDownload(entry.id);
 assert.equal(h.api.downloadEntries()[0].status,'complete');assert.deepEqual(h.bytes(),[1,2,3,4]);
});
test('unsupported-browser handoffs do not claim that a download finished',()=>{
 const h=harness(()=>assert.fail());h.api.recordDownloadHandoff('Fixture','a:p');assert.equal(h.api.downloadEntries()[0].status,'handoff');
});
