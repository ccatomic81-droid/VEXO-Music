// Public release identity; no secret or signing key is stored here.
const PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqX7/c7897t7vrkC6OoZFOsx0kkrHTQ2AXQzrAp6mHDl2VH2jMimZdQ/NGKCpE6GhXbdyLl2aRVIxvboFYpuTb/CSOo27z5wG1NB77c1oUiNsMFbTUYZ0yZD4MBCHjJOP87Ab9reMel171abK7D1xnQtjqCghfUMNiU/5fkzl6jxWmM4TBx8DfYnREeZk1wVcnD50V52A5QJhWx65tW1vfcqhOEB5/G7qbbsAcUIL/RGi50dTebOjEDlvcfKGlY2ij2OPKMwtggfZqaNizRgSokQIRiR0sFxS8A+Ehh0cOoLaFOSR5bSL4SlKoUWmpFgX1x8+RSaHUn58nijL45Dr3QIDAQAB";
const fields=['applicationId','versionCode','versionName','summary','downloadUrl','size','sha256','certificateSha256','sourceCommit'];
export async function authenticateRelease(m, keyText=PUBLIC_KEY) {
 try {
  const signed=m.signedPayload!==undefined||m.releaseSignature!==undefined;
  if(!signed)return Number.isInteger(m.versionCode)&&m.versionCode<=14;
  if(typeof m.signedPayload!=='string'||m.signedPayload.length>50000||typeof m.releaseSignature!=='string'||m.releaseSignature.length>1500)return false;
  const bytes=Uint8Array.from(atob(m.signedPayload),c=>c.charCodeAt(0)),signature=Uint8Array.from(atob(m.releaseSignature),c=>c.charCodeAt(0));
  const key=await crypto.subtle.importKey('spki',Uint8Array.from(atob(keyText),c=>c.charCodeAt(0)),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);
  if(!await crypto.subtle.verify('RSASSA-PKCS1-v1_5',key,signature,bytes))return false;
  const payload=JSON.parse(new TextDecoder().decode(bytes));
  return payload.format==='vexo-release-v1'&&fields.every(k=>m[k]!==undefined&&String(m[k])===String(payload[k]));
 }catch{return false;}
}
