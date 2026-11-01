import { ARTIFACTS, PREFIX, TEST30_ARTIFACTS, TEST30_PREFIX, TEST28_ARTIFACTS, TEST28_PREFIX, TEST25_ARTIFACTS, TEST25_PREFIX, TEST24_ARTIFACTS, TEST24_PREFIX, TEST23_ARTIFACTS, TEST23_PREFIX, TEST21_ARTIFACTS, TEST21_PREFIX, TEST20_ARTIFACTS, TEST20_PREFIX, LEGACY_ARTIFACTS, LEGACY_PREFIX, PREVIOUS_ARTIFACTS, PREVIOUS_PREFIX, OLDER_ARTIFACTS, OLDER_PREFIX, ARCHIVED_ARTIFACTS, ARCHIVED_PREFIX } from './artifacts.mjs';

const common = { 'X-Content-Type-Options':'nosniff', 'Referrer-Policy':'no-referrer', 'X-Frame-Options':'DENY' };
const reply = (text,status=200,headers={}) => new Response(text,{status,headers:{...common,...headers}});
export function rangeFor(value,size) {
  const m=/^bytes=(\d*)-(\d*)$/.exec(value||'');
  if(!m||(!m[1]&&!m[2])||size<1)return null;
  let start,end;
  if(!m[1]){const length=Number(m[2]);if(!Number.isSafeInteger(length)||length<1)return null;start=Math.max(0,size-length);end=size-1;}
  else{start=Number(m[1]);end=m[2]?Math.min(Number(m[2]),size-1):size-1;}
  return Number.isSafeInteger(start)&&Number.isSafeInteger(end)&&start>=0&&start<size&&end>=start?{offset:start,length:end-start+1}:null;
}
const landing = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>VEXO Music · Tester 0.30.1-dev</title><style>
:root{color-scheme:dark;font-family:system-ui,sans-serif;background:#101310;color:#f5f8f5}*{box-sizing:border-box}body{margin:0;padding:48px 22px}main{max-width:760px;margin:auto}.brand{color:#1ed760;font-size:13px;letter-spacing:.14em;font-weight:750}.tag{display:inline-block;border:1px solid #355440;border-radius:50px;padding:8px 12px;color:#a8e9bd;margin-top:36px;font-size:12px}h1{font-size:clamp(36px,8vw,64px);letter-spacing:-.045em;line-height:1.07;margin:22px 0}p,li{color:#b4c0b7;line-height:1.65}section{background:#1a211c;border:1px solid #29362d;border-radius:20px;padding:24px;margin:24px 0}h2{font-size:21px;margin:0 0 12px}a{color:#75e49a}.download{display:inline-block;padding:17px 24px;background:#1ed760;color:#0b2413;border-radius:40px;font-weight:750;text-decoration:none;margin-top:10px;min-height:52px}.small{font-size:13px}.links{display:flex;flex-wrap:wrap;gap:20px;margin:24px 0}ol,ul{padding-left:22px}li+li{margin-top:8px}footer{margin-top:36px;color:#93a297;font-size:12px}</style></head><body><main><div class="brand">VEXO MUSIC</div><div class="tag">PLAYER &amp; HOME UPDATE · ANDROID 8+</div><h1>Your next mix.<br>A smoother flow.</h1><p>VEXO Music 0.30.1-dev · a new Home atmosphere and more control over what plays next.<br>Developer: Kavindu Kavishka</p><a class="download" href="/releases/VEXO-Music-0.30.1-dev.apk">Download tester APK</a><p class="small">Version code 51 · 9.2 MB · signed with VEXO’s existing app certificate. Install as an update over your current VEXO app to keep music and settings.</p><section><h2>What’s improved</h2><ul><li>Playback controls stay visible on smaller screens and when text is enlarged.</li><li>Quick Picks gives longer song titles up to two lines and more room for artists.</li><li>Queue rows make room for song details with one control: tap for options or hold to reorder.</li><li>Save, autoplay and clear actions are easier to find, and an empty queue explains where songs will appear.</li><li>The blended Home gradient, mood shortcuts, smart recommendation filters and Save mix remain available.</li></ul><p class="small">Popular and Deep cuts use music-search intent, rather than verified popularity charts or play counts.</p></section><section><h2>Install as an update</h2><ol><li>Download the APK on your Android phone.</li><li>Open it and choose <strong>Update</strong>. Allow installation from your browser if Android asks.</li><li>Keep VEXO installed to preserve your private music data.</li><li>Try the queue filters while playing and paused, drag order, Save mix and the Home mood shortcuts. Report issues through <strong>Settings → Report a problem</strong>.</li></ol></section><section><h2>Checked before upload</h2><p>Android builds, unit tests, debug/release lint and dependency checks passed. Android 15 emulator checks passed with normal and 160% text, including navigation, local playback, queue controls, filter changes and saving the mix. The original signing certificate, package identity and download checksum were verified.</p><p>Existing artist catalogue fixes, search, lyrics, crossfade, offline music, accounts and playlists are included. YouTube connection remains optional.</p></section><p class="small">The app’s Update button checks the latest public release. This opt-in tester uses this download link. Android cannot install a lower version code over this build. Earlier tester APK addresses remain available.</p><div class="links small"><a href="https://github.com/ccatomic81-droid/VEXO-Music/archive/b5a9de44e0e2dc3fe8a839f22d9efd03706c7a81.zip">Corresponding source</a><a href="/releases/SHA256SUMS.txt">Checksums</a><a href="https://github.com/ccatomic81-droid/VEXO-Music/pull/31">Changes and test report</a></div><footer>Opt-in test · 0.30.1-dev · original signing certificate verified</footer></main></body></html>`;

export function createService(artifacts=ARTIFACTS,prefix=PREFIX) { return {
  async fetch(request,env) {
    if(request.method!=='GET'&&request.method!=='HEAD')return reply('Method not allowed',405,{Allow:'GET, HEAD'});
    const path=new URL(request.url).pathname;
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.30.0-dev.apk')return createService(TEST30_ARTIFACTS,TEST30_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.28.0-dev.apk')return createService(TEST28_ARTIFACTS,TEST28_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.25.0-dev.apk')return createService(TEST25_ARTIFACTS,TEST25_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.24.0-dev.apk')return createService(TEST24_ARTIFACTS,TEST24_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.23.0-dev.apk')return createService(TEST23_ARTIFACTS,TEST23_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.21.0-dev.apk')return createService(TEST21_ARTIFACTS,TEST21_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.20.0-dev.apk')return createService(TEST20_ARTIFACTS,TEST20_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.13.1-dev.apk')return createService(ARCHIVED_ARTIFACTS,ARCHIVED_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.10.0-dev.apk')return createService(LEGACY_ARTIFACTS,LEGACY_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.11.0-dev.apk')return createService(OLDER_ARTIFACTS,OLDER_PREFIX).fetch(request,env);
    if(artifacts===ARTIFACTS&&path==='/releases/VEXO-Music-0.13.0-dev.apk')return createService(PREVIOUS_ARTIFACTS,PREVIOUS_PREFIX).fetch(request,env);
    if(path==='/robots.txt')return reply('User-agent: *\nDisallow: /\n',200,{'Content-Type':'text/plain'});
    const home=path==='/'||path==='/testing';
    const name=path==='/testing.json'?'manifest.json':path.startsWith('/releases/')?path.slice(10):'';
    if(!home&&(!Object.hasOwn(artifacts,name)||name==='manifest.json'&&path!=='/testing.json'))return reply('Not found',404);
    try {
      const ready=await env.RELEASES.head(prefix+'manifest.json');
      if(!ready)return reply('Test release is being prepared',503,{'Cache-Control':'no-store'});
      if(home)return reply(request.method==='HEAD'?null:landing,200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'public, max-age=60','Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"});
      const key=prefix+name;
      const info=await env.RELEASES.head(key);
      if(!info||info.size!==artifacts[name].size||info.customMetadata?.sha256!==artifacts[name].sha256)return reply('Test file unavailable',503,{'Cache-Control':'no-store'});
      const rangeValue=request.method==='GET'&&name!=='manifest.json'?request.headers.get('Range'):null;
      const range=rangeValue?rangeFor(rangeValue,info.size):null;
      if(rangeValue&&!range)return reply('Range not satisfiable',416,{'Content-Range':`bytes */${info.size}`});
      const headers=new Headers({...common,'Content-Type':name.endsWith('.apk')?'application/vnd.android.package-archive':name.endsWith('.zip')?'application/zip':name.endsWith('.json')?'application/json; charset=utf-8':'text/plain; charset=utf-8','Cache-Control':!name.endsWith('.apk')?'public, max-age=60, must-revalidate':'public, max-age=86400, immutable',ETag:info.httpEtag,'Content-Length':String(range?range.length:info.size)});
      if(name!=='manifest.json'){headers.set('Content-Disposition',`attachment; filename="${name}"`);headers.set('Accept-Ranges','bytes');}
      if(!range&&request.headers.get('If-None-Match')===info.httpEtag){headers.delete('Content-Length');return new Response(null,{status:304,headers});}
      if(range)headers.set('Content-Range',`bytes ${range.offset}-${range.offset+range.length-1}/${info.size}`);
      const object=request.method==='HEAD'?null:await env.RELEASES.get(key,range?{range}:undefined);
      if(request.method==='GET'&&!object)return reply('Not found',404);
      return new Response(request.method==='HEAD'?null:object.body,{status:range?206:200,headers});
    }catch{console.error(JSON.stringify({event:'test_release_read_failed'}));return reply('Test release temporarily unavailable',503,{'Cache-Control':'no-store'});}
  }
}; }
export default createService();
