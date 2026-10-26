const headers = { 'Content-Type':'application/json', 'Cache-Control':'no-store', 'Pragma':'no-cache', 'X-Content-Type-Options':'nosniff' };
const reply = (status, error) => new Response(JSON.stringify({error}),{status,headers});
async function body(request) {
  if (!request.body) throw Error('Missing body');
  const reader=request.body.getReader();const parts=[];let length=0;
  try { for(;;) { const {done,value}=await reader.read();if(done)break;length+=value.length;if(length>12288)throw Error('Body limit');parts.push(value); } }
  finally { await reader.cancel().catch(()=>{}); }
  const bytes=new Uint8Array(length);let offset=0;for(const part of parts){bytes.set(part,offset);offset+=part.length;}
  const value=JSON.parse(new TextDecoder().decode(bytes));
  if(!value || typeof value!=='object' || Array.isArray(value) || Object.keys(value).length!==1 || typeof value.idToken!=='string' || value.idToken.length<100 || value.idToken.length>10000)throw Error('Invalid body');
  return value.idToken;
}
export function createHandler({verify,permit,lookup,session}) { return async request => {
  if(request.method!=='POST')return reply(405,'Use POST.');
  if(!request.headers.get('Content-Type')?.toLowerCase().startsWith('application/json'))return reply(415,'Use JSON.');
  let identity,token;
  try {token=await body(request);identity=await verify(token);}catch{return reply(401,'Google sign-in could not be verified. Please try again.');}
  try {
    if(!await permit(identity.uid))return reply(429,'Too many Google sign-in attempts. Try again later.');
    const user=await lookup(token);
    if(user?.localId!==identity.uid || user?.disabled || user?.emailVerified!==true || user?.email?.toLowerCase()!==identity.email
       || !user?.providerUserInfo?.some(p=>p.providerId==='google.com' && p.email?.toLowerCase()===identity.email))
      return reply(401,'Google sign-in could not be verified.');
    const result=await session(identity.email);
    if(typeof result?.access_token!=='string' || !result.access_token || typeof result?.refresh_token!=='string' || !result.refresh_token
       || typeof result?.user?.id!=='string' || result.user.email?.toLowerCase()!==identity.email)
      return reply(502,'Account connection did not complete. Please try again.');
    return new Response(JSON.stringify({access_token:result.access_token,refresh_token:result.refresh_token,
      expires_in:result.expires_in,token_type:'bearer',user:{id:result.user.id,email:result.user.email}}),{status:200,headers});
  }catch{return reply(503,'Google sign-in is temporarily unavailable. Please try again.');}
}; }
