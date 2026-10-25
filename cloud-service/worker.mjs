import {DurableObject} from 'cloudflare:workers';
import {MODEL,MAX_AGE,clean,json,random,hash,track,plan,nextRequest} from './policy.mjs';
const encode=v=>btoa(JSON.stringify(v)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
const decode=v=>JSON.parse(atob(v.replaceAll('-','+').replaceAll('_','/')));
async function hmac(env){return crypto.subtle.importKey('raw',new TextEncoder().encode(env.TOKEN_KEY),{name:'HMAC',hash:'SHA-256'},false,['sign','verify']);}
async function issue(env){const payload=encode({sub:random(),exp:Date.now()+7*86400000});const sig=await crypto.subtle.sign('HMAC',await hmac(env),new TextEncoder().encode(payload));return payload+'.'+btoa(String.fromCharCode(...new Uint8Array(sig))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');}
async function identity(request,env){try{const raw=request.headers.get('Authorization')?.replace(/^Bearer /,'')||'';const [payload,signature]=raw.split('.');if(!payload||!signature||raw.length>1500)return null;const ok=await crypto.subtle.verify('HMAC',await hmac(env),Uint8Array.from(atob(signature.replaceAll('-','+').replaceAll('_','/')),x=>x.charCodeAt(0)),new TextEncoder().encode(payload));const v=decode(payload);return ok&&v.exp>Date.now()&&/^[a-f0-9]{32}$/.test(v.sub)?v.sub:null;}catch{return null;}}
async function body(request){if(Number(request.headers.get('Content-Length'))>8192)throw Error('Request too large');const reader=request.body?.getReader();if(!reader)return {};let text='',size=0;const decoder=new TextDecoder();while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>8192){await reader.cancel();throw Error('Request too large');}text+=decoder.decode(value,{stream:true});}text+=decoder.decode();return JSON.parse(text||'{}');}
const bearer=request=>request.headers.get('Authorization')?.replace(/^Bearer /,'')||'';

export class DailyGate extends DurableObject {
 constructor(ctx,env){super(ctx,env);ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS quota (key TEXT PRIMARY KEY, count INTEGER NOT NULL)');}
 consume(limits){for(const [key,max]of limits){const rows=this.ctx.storage.sql.exec('SELECT count FROM quota WHERE key=?',key).toArray();if((rows[0]?.count||0)>=max)return false;}for(const [key]of limits)this.ctx.storage.sql.exec('INSERT INTO quota(key,count) VALUES(?,1) ON CONFLICT(key) DO UPDATE SET count=count+1',key);this.ctx.waitUntil(this.ctx.storage.setAlarm(Date.now()+2*86400000));return true;}
 async alarm(){await this.ctx.storage.deleteAll();}
}

export class MusicRoom extends DurableObject {
 constructor(ctx,env){super(ctx,env);ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS room (id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL)');}
 load(){const r=this.ctx.storage.sql.exec('SELECT data FROM room WHERE id=1').toArray();return r.length?JSON.parse(r[0].data):null;}
 save(s){this.ctx.storage.sql.exec('INSERT INTO room(id,data) VALUES(1,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data',JSON.stringify(s));}
 public(s){return {type:'state',serverTime:Date.now(),revision:s.revision,expires:s.expires,closed:!!s.closed,host:s.host,hostOnline:this.ctx.getWebSockets(s.host).some(ws=>ws.readyState===1),members:s.members.map(x=>({id:x.id,name:x.name,online:this.ctx.getWebSockets(x.id).some(ws=>ws.readyState===1)})),queue:s.queue.map(x=>({id:x.id,member:x.member,name:s.members.find(m=>m.id===x.member)?.name||'Listener',track:x.track})),playback:s.playback||null,advance:s.advance||''};}
 async create(name){if(this.load())return {error:'Room already exists'};const token=random()+random(),invite=random(),id=random();const tokenHash=await hash(token),inviteHash=await hash(invite);const s={host:id,inviteHash,expires:Date.now()+MAX_AGE,revision:1,members:[{id,name:clean(name,40)||'Host',tokenHash}],queue:[],playback:null,lastTurn:''};this.save(s);await this.ctx.storage.setAlarm(s.expires);return {member:id,token,invite,expires:s.expires,host:true};}
 async join(invite,name){const inviteHash=await hash(invite),token=random()+random(),tokenHash=await hash(token);const s=this.load();if(!s||s.closed||s.expires<Date.now())return {error:'This room has ended'};if(!crypto.subtle.timingSafeEqual(new TextEncoder().encode(s.inviteHash),new TextEncoder().encode(inviteHash)))return {error:'Invalid invite'};if(s.members.length>=8)return {error:'This room already has 8 listeners'};const id=random();s.members.push({id,name:clean(name,40)||'Listener',tokenHash});s.revision++;this.save(s);this.broadcast(s);return {member:id,token,expires:s.expires,host:false};}
 broadcast(s){const message=JSON.stringify(this.public(s));for(const ws of this.ctx.getWebSockets())try{ws.send(message);}catch{}}
 async fetch(request){const token=bearer(request);if(token.length!==64)return json({error:'Room authorization required'},401);const tokenHash=await hash(token),s=this.load();if(!s||s.closed||s.expires<Date.now())return json({error:'Room ended'},410);const member=s.members.find(x=>x.tokenHash===tokenHash);if(!member)return json({error:'Not a room member'},403);if(request.headers.get('Upgrade')!=='websocket')return json(this.public(s));
  for(const old of this.ctx.getWebSockets(member.id))try{old.close(1000,'Reconnected');}catch{}
  const pair=new WebSocketPair();this.ctx.acceptWebSocket(pair[1],[member.id]);pair[1].serializeAttachment({member:member.id,window:Date.now(),count:0});this.broadcast(s);return new Response(null,{status:101,webSocket:pair[0]});}
 async webSocketMessage(ws,message){try{if(typeof message!=='string'||message.length>8192)throw Error('Message too large');const attachment=ws.deserializeAttachment();if(!attachment)throw Error('Unauthorized');if(Date.now()-attachment.window>60000){attachment.window=Date.now();attachment.count=0;}if(++attachment.count>35)throw Error('Please slow down');ws.serializeAttachment(attachment);const s=this.load();if(!s||s.closed||s.expires<Date.now()){ws.close(1000,'Room ended');return;}const member=s.members.find(x=>x.id===attachment.member);if(!member)throw Error('Not a member');const v=JSON.parse(message);let changed=true;
  if(v.type==='add'){if(s.queue.length>=50||s.queue.filter(x=>x.member===member.id).length>=3)throw Error('Each listener may queue up to 3 songs');const t=track(v.track);if(s.queue.some(x=>x.track.url===t.url))throw Error('Already in this room queue');s.queue.push({id:random(),member:member.id,track:t});}
  else if(v.type==='remove'){const at=s.queue.findIndex(x=>x.id===v.id);if(at<0)throw Error('Request not found');if(s.host!==member.id&&s.queue[at].member!==member.id)throw Error('You can remove only your own requests');s.queue.splice(at,1);}
  else if(v.type==='next'){if(s.host!==member.id)throw Error('Only the host controls playback');const next=nextRequest(s);s.advance=random();s.playback=next?{track:next.track,position:0,playing:true,at:Date.now()}:s.playback?{...s.playback,playing:false,at:Date.now()}:null;}
  else if(v.type==='playback'){if(s.host!==member.id)throw Error('Only the host controls playback');const t=track(v.track);s.playback={track:t,position:Math.max(0,Math.min(Number(v.position)||0,t.duration*1000)),playing:v.playing===true,at:Date.now()};}
  else if(v.type==='end'){if(s.host!==member.id)throw Error('Only the host can end the room');s.closed=true;s.playback=null;}
  else if(v.type==='leave'){s.queue=s.queue.filter(x=>x.member!==member.id);s.members=s.members.filter(x=>x.id!==member.id);if(s.host===member.id){s.closed=true;s.playback=null;}}
  else if(v.type==='ping'){changed=false;ws.send(JSON.stringify({type:'pong',serverTime:Date.now()}));}
  else throw Error('Unknown room action');
  if(changed){s.revision++;this.save(s);this.broadcast(s);if(s.closed)for(const other of this.ctx.getWebSockets())try{other.close(1000,'Room ended');}catch{}}
 }catch(error){try{ws.send(JSON.stringify({type:'error',error:clean(error.message,160)}));}catch{}}}
 webSocketClose(ws,code){try{ws.close(code===1006?1000:code,'Disconnected');}catch{}const s=this.load();if(s)this.broadcast(s);}
 webSocketError(ws){try{ws.close(1011,'Reconnect');}catch{}}
 async alarm(){for(const ws of this.ctx.getWebSockets())try{ws.close(1000,'Room expired');}catch{}await this.ctx.storage.deleteAll();}
}

export default {async fetch(request,env){try{const url=new URL(request.url),path=url.pathname;
 if(path==='/health'&&request.method==='GET')return json({ok:true,aiModel:MODEL,roomLimit:8,roomHours:2});
 const socket=/^\/v1\/rooms\/([a-f0-9]{32})\/socket$/.exec(path);if(socket)return env.ROOMS.getByName(socket[1]).fetch(request);
 if(request.method!=='POST')return json({error:'Not found'},404);
 const day=new Date().toISOString().slice(0,10),gate=env.GATE.getByName(day);
 if(path==='/v1/device'){const ip=await hash(request.headers.get('CF-Connecting-IP')||'unknown');if(!await gate.consume([['register-all',200],['register-'+ip,10]]))return json({error:'Free service registration limit reached. Try tomorrow.'},429);return json({token:await issue(env)});}
 const who=await identity(request,env);if(!who)return json({error:'Device session expired'},401);
 const v=await body(request);
 if(path==='/v1/mindradio'){if(!await gate.consume([['ai-all',100],['ai-'+who,5]]))return json({error:'Today’s free AI allowance is used. Local radio still works.'},429);const command=clean(v.command,300);if(!command)throw Error('Describe what you want to hear');const preferences=Object.fromEntries(['genres','language','mood','energy','artists'].map(k=>[k,clean(v.preferences?.[k],80)]));const answer=await env.AI.run(MODEL,{messages:[{role:'system',content:'You plan music radio searches. Return ONLY a JSON object with queries (1 to 3 short music search strings), reply (one concise friendly sentence), mood, energy, language, excludeArtists (array). Understand English, Sinhala, Tamil and Romanized requests. Follow exclusions and changes in the last user request. Use priorContext only to continue the music request. Do not invent playable URLs, licenses, lyrics, history or audio measurements. Do not answer unrelated requests.'},{role:'user',content:JSON.stringify({command,preferences,priorContext:clean(v.context,300)})}],max_tokens:320,temperature:0.2});return json({...plan(answer.response),provider:'Cloudflare Workers AI',model:MODEL});}
 if(path==='/v1/rooms/create'){if(!await gate.consume([['rooms-all',20],['rooms-'+who,3]]))return json({error:'Free room limit reached. Try tomorrow.'},429);const room=random(),result=await env.ROOMS.getByName(room).create(clean(v.name,40));return json({...result,room});}
 if(path==='/v1/rooms/join'){if(!/^[a-f0-9]{32}$/.test(v.room||'')||!/^[a-f0-9]{32}$/.test(v.invite||''))throw Error('Paste the complete invite code');if(!await gate.consume([['join-'+who,20]]))return json({error:'Too many join attempts'},429);const result=await env.ROOMS.getByName(v.room).join(v.invite,v.name);return json({...result,room:v.room},result.error?400:200);}
 return json({error:'Not found'},404);
 }catch(error){return json({error:clean(error.message||'Service unavailable',180)},503);}}};
