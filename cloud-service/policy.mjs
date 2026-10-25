export const MODEL='@cf/meta/llama-3.1-8b-instruct-fp8';
export const MAX_AGE=2*60*60*1000;
export const clean=(s,n=200)=>String(s||'').replace(/[\u0000-\u001f]/g,' ').trim().slice(0,n);
export const json=(v,status=200)=>Response.json(v,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
export const random=()=>crypto.randomUUID().replaceAll('-','');
export async function hash(s){return [...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))].map(x=>x.toString(16).padStart(2,'0')).join('');}
export function track(value){
 if(!value||typeof value!=='object')throw Error('Choose an online song');
 const u=new URL(value.url);if(u.protocol!=='https:'||!['www.youtube.com','youtube.com','music.youtube.com','youtu.be'].includes(u.hostname))throw Error('Only online catalogue songs can be shared');
 const id=u.hostname==='youtu.be'?u.pathname.slice(1):u.searchParams.get('v');if(!/^[A-Za-z0-9_-]{11}$/.test(id||''))throw Error('Choose a valid catalogue song');
 const canonical='https://www.youtube.com/watch?v='+id;
 const duration=Number(value.duration);if(!Number.isFinite(duration)||duration<30||duration>900)throw Error('Choose a song between 30 seconds and 15 minutes');
 let artwork='';try{const a=new URL(value.artwork);if(a.protocol==='https:'&&(a.hostname==='i.ytimg.com'||a.hostname.endsWith('.ytimg.com')))if(a.href.length<=300)artwork=a.toString();}catch{}
 return {url:canonical,title:clean(value.title),artist:clean(value.artist,100),artwork,duration};
}
export function plan(value){
 const v=typeof value==='string'?JSON.parse(value.replace(/^```(?:json)?\s*|\s*```$/g,'')):value;
 const queries=Array.isArray(v?.queries)?v.queries.map(x=>clean(x,120)).filter(Boolean).slice(0,3):[];
 if(!queries.length)throw Error('AI returned no playable search plan. Try a clearer request.');
 return {queries,reply:clean(v.reply,350),mood:clean(v.mood,40),energy:clean(v.energy,40),language:clean(v.language,40),excludeArtists:Array.isArray(v.excludeArtists)?v.excludeArtists.map(x=>clean(x,80)).slice(0,8):[]};
}
export function nextRequest(state){
 const queue=state.queue||[];if(!queue.length)return null;
 const people=[...new Set(queue.map(x=>x.member))];let idx=people.indexOf(state.lastTurn);idx=(idx+1)%people.length;
 const at=queue.findIndex(x=>x.member===people[idx]);const chosen=queue.splice(at,1)[0];state.lastTurn=chosen.member;return chosen;
}

// Hashes identify only the daily request budget, never an account or a permanent IP history.
export function requestLimits(route, ip, who, now = Date.now()) {
 const minute=Math.floor(now/60000);
 const burst=(name,global,perIp)=>[[`${name}-burst-all-${minute}`,global],[`${name}-burst-ip-${ip}-${minute}`,perIp]];
 if(route==='device')return [...burst('register',20,5),['register-all',200],['register-'+ip,30]];
 if(route==='ai')return [...burst('ai',20,3),['ai-all',100],['ai-'+who,5],['ai-ip-'+ip,20]];
 if(route==='create')return [...burst('rooms',10,2),['rooms-all',20],['rooms-'+who,3],['rooms-ip-'+ip,5]];
 if(route==='join')return [...burst('join',60,10),['join-'+who,20],['join-ip-'+ip,40]];
 if(route==='socket')return burst('socket',120,30);
 return [];
}
