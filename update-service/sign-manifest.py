"""Offline signing for future releases. Original signing identity must be supplied through environment."""
from pathlib import Path
import argparse,base64,hashlib,json,re,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('--manifest',required=True);p.add_argument('--apk',required=True);p.add_argument('--output',required=True);a=p.parse_args()
m=json.loads(Path(a.manifest).read_text());apk=Path(a.apk)
assert m['applicationId']=='space.vexo.music.radio' and isinstance(m['versionCode'],int) and m['versionCode']>14
assert re.fullmatch(r'\d+\.\d+\.\d+(?:-dev)?',m['versionName'])
assert m['downloadUrl']=='https://vexo-music-updates.kavindukavishka785.workers.dev/releases/VEXO-Music-'+m['versionName']+'.apk'
assert re.fullmatch('[a-f0-9]{40}',m['sourceCommit']) and 0<len(m['summary'])<=12000
assert m['certificateSha256']=='0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3'
assert m['size']==apk.stat().st_size and m['sha256']==hashlib.sha256(apk.read_bytes()).hexdigest()
with zipfile.ZipFile(apk) as z:assert z.testzip() is None
fields=['applicationId','versionCode','versionName','summary','downloadUrl','size','sha256','certificateSha256','sourceCommit']
payload=json.dumps({'format':'vexo-release-v1',**{k:m[k] for k in fields}},ensure_ascii=False,separators=(',',':')).encode()
signer=Path(__file__).resolve().parent.parent/'tools/SignRelease.java'
r=subprocess.run(['java',str(signer)],input=payload,capture_output=True)
if r.returncode:raise SystemExit('Signing failed; private signing details were not printed.')
m['signedPayload']=base64.b64encode(payload).decode();m['releaseSignature']=r.stdout.decode().strip()
Path(a.output).write_text(json.dumps(m,ensure_ascii=False,indent=2)+'\n')
print('Authenticated release manifest prepared; no publication performed.')
