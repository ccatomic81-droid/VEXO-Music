import { ARTIFACTS, PREFIX, TEST20_ARTIFACTS, TEST20_PREFIX, LEGACY_ARTIFACTS, LEGACY_PREFIX, PREVIOUS_ARTIFACTS, PREVIOUS_PREFIX, OLDER_ARTIFACTS, OLDER_PREFIX, ARCHIVED_ARTIFACTS, ARCHIVED_PREFIX } from './artifacts.mjs';

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
const landing = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>VEXO Music · Test 0.21.0</title><style>
:root{color-scheme:dark;font-family:system-ui,sans-serif;background:#0d1017;color:#f5f6f8}*{box-sizing:border-box}body{margin:0;padding:40px 20px}main{max-width:760px;margin:auto}.brand{color:#d4fc79;font-size:12px;letter-spacing:.15em;font-weight:700}.tag{display:inline-block;border:1px solid #485b2d;border-radius:50px;padding:7px 12px;color:#d4fc79;margin-top:30px;font-size:12px}h1{font-size:clamp(34px,7vw,58px);letter-spacing:-.04em;line-height:1.08;margin:20px 0}p,li{color:#a9b0be;line-height:1.65}section{background:#191e29;border-radius:22px;padding:24px;margin:24px 0}h2{font-size:20px;margin-top:0}a{color:#d4fc79}.download{display:inline-block;padding:17px 24px;background:#d4fc79;color:#121713;border-radius:40px;font-weight:700;text-decoration:none;margin-top:8px}.small{font-size:13px}.links{display:flex;flex-wrap:wrap;gap:20px;margin:20px 0}ol{padding-left:20px}code{font-size:12px;word-break:break-all}footer{margin-top:35px;color:#8992a5;font-size:12px}</style></head><body><main><div class="brand">VEXO MUSIC</div><div class="tag">OPT-IN TEST · ANDROID 8+</div><h1>Your music.<br>Your next experience.</h1><p>VEXO Music 0.21.0-dev · recommendations that learn from your VEXO listening.<br>Developer: Kavindu Kavishka</p><a class="download" href="https://vexo-music-testing.kavindukavishka785.workers.dev/releases/VEXO-Music-0.21.0-dev.apk">Download tester APK</a><p class="small">Signed with VEXO’s existing app certificate. Debug mode disabled. This test can update VEXO 0.19.3 and earlier versions using the same app identity while keeping its data. Physical-phone acceptance testing is still required.</p><section><h2>Install as an update</h2><ol><li>Download the APK on your Android phone.</li><li>Open it and choose <strong>Update</strong>. Android may ask you to allow installation from your browser.</li><li>Keep the existing app installed. Uninstalling removes its private data.</li><li>Check playback, playlists, crossfade and background listening. Send issues using <strong>Settings → Report a problem</strong>.</li></ol></section><section><h2>What’s new in this test</h2><ul><li>Recommendations learn from recent VEXO listening; YouTube connection stays optional.</li><li>Manual queue additions play before automatic recommendations.</li><li>Play next preserves shuffle, your current song and playback position.</li><li>Recommended queue entries are labelled and survive reopening.</li><li>Better radio repeat protection and less unrelated station drift.</li><li>Listening saves move off the playback callback thread.</li></ul><p>MindRadio, Pure Song intro controls, Smart Experience, personalized discovery, accounts, crossfade, offline music, playlists and update delivery remain accessible.</p><h2>Before you test</h2><p>Check your saved music and profile, Home customization, search suggestions, radio without restarting the song, crossfade, intro settings and background playback. Test playback and navigation on your phone; automated checks cannot verify audible quality or every device.</p><p>This is a public opt-in test link. Only install if you want to test. Keep VEXO installed; do not uninstall or clear app data. The test stays installed until a newer test or public update is available.</p></section><p class="small">The app’s Update button checks the latest public release. This opt-in test requires this download link; stable users are not automatically moved to testing. Earlier APK addresses remain available.</p><div class="links small"><a href="https://github.com/ccatomic81-droid/VEXO-Music/archive/b4c8a3b724d561552ed1fe24b9f49091199a5ae6.zip">Corresponding source</a><a href="/releases/SHA256SUMS.txt">Checksums</a><a href="https://github.com/ccatomic81-droid/VEXO-Music/pull/16">Development report &amp; PR</a></div><footer>Opt-in test · version code 30 · original signing certificate verified</footer></main></body></html>`;

export function createService(artifacts=ARTIFACTS,prefix=PREFIX) { return {
  async fetch(request,env) {
    if(request.method!=='GET'&&request.method!=='HEAD')return reply('Method not allowed',405,{Allow:'GET, HEAD'});
    const path=new URL(request.url).pathname;
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
