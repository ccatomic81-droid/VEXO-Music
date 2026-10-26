import { createRemoteJWKSet, jwtVerify } from "npm:jose@6.2.13";
import { createClient } from "npm:@supabase/supabase-js@2.118.0";
import { createVerifier } from "./policy.mjs";
import { createHandler } from "./handler.mjs";

const keys = createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"), {timeoutDuration:6000});
const verify = createVerifier(jwtVerify,keys);
const boundedFetch: typeof fetch = (url,init={}) => fetch(url,{...init,redirect:"error",signal:AbortSignal.timeout(8000)});
function admin() {
  const url=Deno.env.get("SUPABASE_URL")!;
  if(url!=="https://vjkogwihjvlonnyedflq.supabase.co")throw Error("Wrong project");
  return createClient(url,Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,{auth:{persistSession:false,autoRefreshToken:false,detectSessionInUrl:false},global:{fetch:boundedFetch}});
}
async function permit(uid: string) {
  const bytes=await crypto.subtle.digest("SHA-256",new TextEncoder().encode("vexo-music-kavindu-2026:"+uid));
  const fingerprint=Array.from(new Uint8Array(bytes),v=>v.toString(16).padStart(2,"0")).join("");
  const {data,error}=await admin().rpc("vexo_google_login_permit",{subject_hash:fingerprint});
  if(error)throw Error("Budget unavailable");
  return data===true;
}
async function lookup(idToken: string) {
  const response=await boundedFetch("https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=AIzaSyDZeUFTiNAdq5m8k4R62ZjCLPLLxqyG97A",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({idToken})});
  if(!response.ok)throw Error("Firebase account unavailable");
  const reader=response.body!.getReader();let size=0;const pieces:Uint8Array[]=[];
  try{for(;;){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>65536)throw Error("Account response too large");pieces.push(value);}}finally{await reader.cancel().catch(()=>{});}
  const bytes=new Uint8Array(size);let at=0;for(const p of pieces){bytes.set(p,at);at+=p.length;}
  const body=JSON.parse(new TextDecoder().decode(bytes));
  if(body.users?.length!==1)throw Error("Ambiguous Firebase account");
  return body.users[0];
}
async function session(email:string) {
  const {data:link,error}=await admin().auth.admin.generateLink({type:"magiclink",email});
  if(error || !link?.properties?.hashed_token || !link.user?.id)throw Error("Account session unavailable");
  const kind=link.properties.verification_type;
  if(kind!=="magiclink" && kind!=="signup")throw Error("Unexpected verification type");
  const client=createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_ANON_KEY")!,{auth:{persistSession:false,autoRefreshToken:false,detectSessionInUrl:false},global:{fetch:boundedFetch}});
  const result=await client.auth.verifyOtp({token_hash:link.properties.hashed_token,type:kind});
  if(result.error || !result.data.session || result.data.user?.id!==link.user.id)throw Error("Account verification failed");
  return {...result.data.session,user:{id:result.data.user.id,email:result.data.user.email}};
}
Deno.serve(createHandler({verify,permit,lookup,session}));
