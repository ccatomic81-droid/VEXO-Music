import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPair,exportJWK,SignJWT,jwtVerify,createLocalJWKSet} from 'jose';
import {PROJECT,createVerifier} from './policy.mjs';
import {createHandler} from './handler.mjs';
const now=Math.floor(Date.now()/1000);
const claims={iss:`https://securetoken.google.com/${PROJECT}`,aud:PROJECT,sub:'fixture-google-user',iat:now,exp:now+3600,auth_time:now,email:'listener@example.com',email_verified:true,firebase:{sign_in_provider:'google.com',identities:{'google.com':['1234567']}}};
const pair=await generateKeyPair('RS256');const jwk=await exportJWK(pair.publicKey);jwk.kid='fixture';jwk.alg='RS256';
const verify=createVerifier(jwtVerify,createLocalJWKSet({keys:[jwk]}));
const signed=p=>new SignJWT(p).setProtectedHeader({alg:'RS256',kid:'fixture'}).sign(pair.privateKey);
const valid=await signed(claims);
const post=body=>new Request('https://fixture.test/signin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
function fixture(overrides={}) {
 const calls=[];return {calls,handler:createHandler({verify:async token=>{calls.push('verify');return verify(token);},permit:async()=>{calls.push('permit');return true;},lookup:async()=>{calls.push('lookup');return {localId:claims.sub,email:claims.email,emailVerified:true,providerUserInfo:[{providerId:'google.com',email:claims.email}]};},session:async email=>{calls.push('session');return {access_token:'fixture-access',refresh_token:'fixture-refresh',expires_in:3600,user:{id:'fixture-vexo-user',email,user_metadata:{private:'not returned'}}};},...overrides})};
}
test('only signed, current project-bound Google identities are accepted',async()=>{
 assert.equal((await verify(valid)).email,claims.email);
 for(const p of [{...claims,aud:'other-project'},{...claims,iss:'https://evil.example'},{...claims,exp:now-100},{...claims,iat:now-4000},{...claims,email_verified:false},{...claims,firebase:{sign_in_provider:'password',identities:{}}}])await assert.rejects(async()=>verify(await signed(p)));
 const parts=valid.split('.');const tampered=parts[0]+'.'+Buffer.from(JSON.stringify({...claims,email:'victim@example.com'})).toString('base64url')+'.'+parts[2];await assert.rejects(()=>verify(tampered));
});
test('valid exchange returns a bounded regular session without private metadata',async()=>{
 const {handler,calls}=fixture();const r=await handler(post({idToken:valid}));assert.equal(r.status,200);
 const body=await r.json();assert.deepEqual(Object.keys(body.user).sort(),['email','id']);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.deepEqual(calls,['verify','permit','lookup','session']);
});
test('extra identity fields and oversized invalid bodies cannot reach privileged operations',async()=>{
 for(const body of [{idToken:valid,email:'victim@example.com'},{idToken:'x'.repeat(25000)},{idToken:17}]){const {handler,calls}=fixture();assert.equal((await handler(post(body))).status,401);assert.deepEqual(calls,[]);}
});
test('forged identity cannot reach lookup or session creation',async()=>{
 const {handler,calls}=fixture();assert.equal((await handler(post({idToken:'x'.repeat(200)}))).status,401);assert.deepEqual(calls,['verify']);
});
test('disabled or mismatched Firebase accounts cannot create VEXO sessions',async()=>{
 for(const user of [{localId:'other-user'},{localId:claims.sub,disabled:true,email:claims.email,emailVerified:true},{localId:claims.sub,email:'other@example.com',emailVerified:true,providerUserInfo:[{providerId:'google.com',email:'other@example.com'}]}]){
  let sessions=0;const {handler}=fixture({lookup:async()=>user,session:async()=>{sessions++;}});assert.equal((await handler(post({idToken:valid}))).status,401);assert.equal(sessions,0);
 }
});
test('budget failure is closed before privileged lookup',async()=>{
 let lookups=0;const {handler}=fixture({permit:async()=>false,lookup:async()=>{lookups++;}});assert.equal((await handler(post({idToken:valid}))).status,429);assert.equal(lookups,0);
});
test('backend failure never reveals internals or credentials',async()=>{
 const {handler}=fixture({session:async()=>{throw Error('private-fixture-material');}});const r=await handler(post({idToken:valid}));assert.equal(r.status,503);assert.ok(!(await r.text()).includes('private-fixture-material'));
});
