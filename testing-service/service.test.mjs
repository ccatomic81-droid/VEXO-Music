import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, timingSafeEqual } from 'node:crypto';
import { createService, rangeFor } from './service.mjs';
import { createPublisher } from './publisher.mjs';
import { ARTIFACTS, TEST23_ARTIFACTS, TEST23_PREFIX, PREVIOUS_ARTIFACTS, PREVIOUS_PREFIX, OLDER_ARTIFACTS, OLDER_PREFIX, LEGACY_ARTIFACTS, LEGACY_PREFIX, ARCHIVED_ARTIFACTS, ARCHIVED_PREFIX, TEST21_ARTIFACTS, TEST21_PREFIX, TEST20_ARTIFACTS, TEST20_PREFIX } from './artifacts.mjs';
const data = {'VEXO-Music-0.10.0-dev.apk':new TextEncoder().encode('test signed APK bytes'),'VEXO-Music-0.10.0-dev-Source.zip':new Uint8Array([1,2,3]),'SHA256SUMS.txt':new TextEncoder().encode('test checksums'),'manifest.json':new TextEncoder().encode('{"channel":"testing"}')};
const artifacts=Object.fromEntries(Object.entries(data).map(([k,v])=>[k,{size:v.length,sha256:createHash('sha256').update(v).digest('hex')}]));
const prefix='testing/0.10.0-dev/';
function bucket(){const objects=new Map();return {objects,async head(key){const o=objects.get(key);return o?{size:o.bytes.length,customMetadata:o.metadata,httpEtag:'"test-etag"'}:null;},async get(key,opts){const o=objects.get(key);if(!o)return null;const r=opts?.range;return {body:r?o.bytes.slice(r.offset,r.offset+r.length):o.bytes};},async put(key,body,opts){const bytes=new Uint8Array(await new Response(body).arrayBuffer());if(opts.sha256!==createHash('sha256').update(bytes).digest('hex'))throw Error('checksum mismatch');if(objects.has(key)&&opts.onlyIf?.etagDoesNotMatch==='*')return null;objects.set(key,{bytes,metadata:opts.customMetadata});return this.head(key);}};}
function seeded(){const b=bucket();for(const [name,bytes]of Object.entries(data))b.objects.set(prefix+name,{bytes,metadata:{sha256:artifacts[name].sha256}});return b;}
const get=(path,init)=>new Request('https://testing.test'+path,init);

test('tester page is opt-in, download is streamed and range/HEAD/cache work',async()=>{
 const service=createService(artifacts,prefix), env={RELEASES:seeded()};
 const page=await service.fetch(get('/'),env);assert.equal(page.status,200);assert.match(await page.text(),/app’s Update button checks the latest public release/);
 const file='/releases/VEXO-Music-0.10.0-dev.apk';
 const full=await service.fetch(get(file),env);assert.equal(full.status,200);assert.equal(full.headers.get('Content-Type'),'application/vnd.android.package-archive');assert.deepEqual(new Uint8Array(await full.arrayBuffer()),data['VEXO-Music-0.10.0-dev.apk']);
 const part=await service.fetch(get(file,{headers:{Range:'bytes=2-5'}}),env);assert.equal(part.status,206);assert.equal(part.headers.get('Content-Range'),`bytes 2-5/${artifacts['VEXO-Music-0.10.0-dev.apk'].size}`);assert.deepEqual(new Uint8Array(await part.arrayBuffer()),data['VEXO-Music-0.10.0-dev.apk'].slice(2,6));
 const head=await service.fetch(get(file,{method:'HEAD'}),env);assert.equal(head.status,200);assert.equal(await head.text(),'');
 assert.equal((await service.fetch(get(file,{headers:{Range:'bytes=999-'}}),env)).status,416);
 assert.equal((await service.fetch(get(file,{headers:{'If-None-Match':'"test-etag"'}}),env)).status,304);
 const manifest=await service.fetch(get('/testing.json'),env);assert.equal(manifest.status,200);assert.deepEqual(await manifest.json(),{channel:'testing'});
});
test('public server has no write routes and exposes no stable or arbitrary bucket objects',async()=>{
 const service=createService(artifacts,prefix),env={RELEASES:seeded()};
 for(const path of ['/stable.json','/releases/manifest.json','/releases/../stable.json','/releases/secrets.txt'])assert.equal((await service.fetch(get(path),env)).status,404);
 for(const method of ['POST','PUT','DELETE'])assert.equal((await service.fetch(get('/__publish/manifest.json',{method}),env)).status,405);
 const empty={RELEASES:bucket()};assert.equal((await service.fetch(get('/'),empty)).status,503);
});
test('temporary publisher requires unexpired token, exact hashes and artifacts before manifest',async()=>{
 crypto.subtle.timingSafeEqual=(a,b)=>timingSafeEqual(Buffer.from(a),Buffer.from(b));
 const publisher=createPublisher(artifacts,prefix), token='temporary test capability';
 const env={RELEASES:bucket(),PUBLISH_HASH:createHash('sha256').update(token).digest('hex'),PUBLISH_UNTIL:String(Date.now()+60000)};
 function put(name,options={}){const bytes=options.bytes||data[name]||new Uint8Array([0]);return get('/__publish/'+name,{method:'PUT',body:bytes,headers:{Authorization:'Bearer '+(options.token||token),'Content-Length':String(bytes.length),'X-Upload-Sha256':options.sha||artifacts[name]?.sha256||'0'.repeat(64)}});}
 assert.equal((await publisher.fetch(put('manifest.json',{token:'wrong'}),env)).status,401);
 assert.equal((await publisher.fetch(put('manifest.json'),env)).status,409);
 assert.equal((await publisher.fetch(put('stable.json'),env)).status,404);
 assert.equal((await publisher.fetch(put('VEXO-Music-0.10.0-dev.apk',{sha:'0'.repeat(64)}),env)).status,400);
 const corrupted=new Uint8Array(data['VEXO-Music-0.10.0-dev.apk'].length);
 assert.equal((await publisher.fetch(put('VEXO-Music-0.10.0-dev.apk',{bytes:corrupted}),env)).status,400);
 for(const name of Object.keys(data))assert.equal((await publisher.fetch(put(name),env)).status,200);
 assert.equal((await publisher.fetch(put('manifest.json'),env)).status,200);
 assert.equal(env.RELEASES.objects.has('stable.json'),false);
 env.PUBLISH_UNTIL=String(Date.now()-1);assert.equal((await publisher.fetch(put('manifest.json'),env)).status,401);
});
test('range parser rejects malformed, multiple and unsafe integer ranges',()=>{
 for(const value of ['bytes=','bytes=0-2,4-6','bytes=9007199254740992-','bytes=-0','bytes=8-1','items=0-2'])assert.equal(rangeFor(value,20),null);
 assert.deepEqual(rangeFor('bytes=-5',20),{offset:15,length:5});
});

test("production publishing allowlist contains only APK and release information",()=>{assert.deepEqual(Object.keys(ARTIFACTS).sort(),["SHA256SUMS.txt","VEXO-Music-0.24.0-dev.apk","manifest.json"].sort());});

test('earlier opt-in APK addresses still resolve immutable archived versions',async()=>{
 const service=createService();
 for(const [name,files,prefix] of [['VEXO-Music-0.23.0-dev.apk',TEST23_ARTIFACTS,TEST23_PREFIX],['VEXO-Music-0.21.0-dev.apk',TEST21_ARTIFACTS,TEST21_PREFIX],['VEXO-Music-0.20.0-dev.apk',TEST20_ARTIFACTS,TEST20_PREFIX],['VEXO-Music-0.13.1-dev.apk',ARCHIVED_ARTIFACTS,ARCHIVED_PREFIX],['VEXO-Music-0.13.0-dev.apk',PREVIOUS_ARTIFACTS,PREVIOUS_PREFIX],['VEXO-Music-0.11.0-dev.apk',OLDER_ARTIFACTS,OLDER_PREFIX],['VEXO-Music-0.10.0-dev.apk',LEGACY_ARTIFACTS,LEGACY_PREFIX]]) {
  const seen=[];const env={RELEASES:{async head(key){seen.push(key);if(key===prefix+'manifest.json')return {};if(key===prefix+name)return {size:files[name].size,customMetadata:{sha256:files[name].sha256},httpEtag:'"archived"'};return null;}}};
  const r=await service.fetch(get('/releases/'+name,{method:'HEAD'}),env);assert.equal(r.status,200);assert.equal(Number(r.headers.get('Content-Length')),files[name].size);assert.ok(seen.every(key=>key.startsWith(prefix)));
 }
});
