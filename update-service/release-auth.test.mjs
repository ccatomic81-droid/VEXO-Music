import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign} from 'node:crypto';
import {authenticateRelease} from './release-auth.mjs';
test('future manifests require signatures and bind every update field',async()=>{
 const {privateKey,publicKey}=generateKeyPairSync('rsa',{modulusLength:2048});
 const publicText=publicKey.export({type:'spki',format:'der'}).toString('base64');
 const base={applicationId:'space.vexo.music.radio',versionCode:16,versionName:'0.14.0',summary:'Signed',downloadUrl:'https://updates.test/a.apk',size:12,sha256:'a'.repeat(64),certificateSha256:'b'.repeat(64),sourceCommit:'c'.repeat(40)};
 const payload=Buffer.from(JSON.stringify({format:'vexo-release-v1',...base}));
 const release={...base,signedPayload:payload.toString('base64'),releaseSignature:sign('SHA256',payload,privateKey).toString('base64')};
 assert.equal(await authenticateRelease(release,publicText),true);
 for(const field of Object.keys(base))assert.equal(await authenticateRelease({...release,[field]:'tampered'},publicText),false);
 assert.equal(await authenticateRelease(base,publicText),false);
 assert.equal(await authenticateRelease(release),false);
 assert.equal(await authenticateRelease({versionCode:14}),true);
 assert.equal(await authenticateRelease({...release,releaseSignature:'invalid'},publicText),false);
});
