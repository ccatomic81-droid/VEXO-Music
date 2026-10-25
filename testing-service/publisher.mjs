// Temporary entrypoint: remove after publication; cannot write any stable object.
import service from './service.mjs';
import { ARTIFACTS, PREFIX } from './artifacts.mjs';
const json=(v,status)=>Response.json(v,{status,headers:{'Cache-Control':'no-store'}});
export function createPublisher(artifacts=ARTIFACTS,prefix=PREFIX) { return {
 async fetch(request,env){
  const path=new URL(request.url).pathname;
  if(!path.startsWith('/__publish/'))return service.fetch(request,env);
  if(request.method!=='PUT')return json({error:'Method not allowed'},405);
  const token=request.headers.get('Authorization')?.replace(/^Bearer /,'')||'';
  if(!token||token.length>200||!env.PUBLISH_UNTIL||Date.now()>Number(env.PUBLISH_UNTIL)||!/^[a-f0-9]{64}$/.test(env.PUBLISH_HASH||''))return json({error:'Unauthorized'},401);
  const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(token));
  const expected=new Uint8Array(env.PUBLISH_HASH.match(/../g).map(v=>parseInt(v,16)));
  if(!crypto.subtle.timingSafeEqual(digest,expected))return json({error:'Unauthorized'},401);
  const name=path.slice('/__publish/'.length);
  if(!Object.hasOwn(artifacts,name))return json({error:'Not found'},404);
  const artifact=artifacts[name],length=Number(request.headers.get('Content-Length'));
  if(length!==artifact.size||request.headers.get('X-Upload-Sha256')!==artifact.sha256)return json({error:'Incorrect file metadata'},400);
  try{
   if(name==='manifest.json')for(const [file,details]of Object.entries(artifacts)){if(file==='manifest.json')continue;const stored=await env.RELEASES.head(prefix+file);if(!stored||stored.size!==details.size||stored.customMetadata?.sha256!==details.sha256)return json({error:'Upload all verified files before publishing'},409);}
   const prior=await env.RELEASES.head(prefix+name);
   if(prior)return prior.customMetadata?.sha256===artifact.sha256&&prior.size===artifact.size?json({uploaded:true,existing:true},200):json({error:'Immutable test version already exists'},409);
   const stored=await env.RELEASES.put(prefix+name,request.body,{sha256:artifact.sha256,onlyIf:{etagDoesNotMatch:'*'},httpMetadata:{contentType:name.endsWith('.apk')?'application/vnd.android.package-archive':name.endsWith('.zip')?'application/zip':name.endsWith('.json')?'application/json':'text/plain'},customMetadata:{sha256:artifact.sha256}});
   return stored?json({uploaded:true,published:name==='manifest.json'},200):json({error:'Concurrent upload conflict'},409);
  }catch{return json({error:'Test upload failed'},400);}
 }
}; }
export default createPublisher();
