/** Identity is accepted only after signature verification, never from a submitted email field. */
export const PROJECT = 'vexo-music-kavindu-2026';
export function checkClaims(payload, now = Math.floor(Date.now()/1000)) {
  const identity = payload.firebase;
  if (payload.iss !== `https://securetoken.google.com/${PROJECT}` || payload.aud !== PROJECT
      || typeof payload.sub !== 'string' || !payload.sub || payload.sub.length > 128
      || !Number.isFinite(payload.iat) || payload.iat > now + 5 || payload.iat < now - 600
      || !Number.isFinite(payload.exp) || payload.exp <= now || payload.exp - payload.iat > 3700
      || !Number.isFinite(payload.auth_time) || payload.auth_time <= 0 || payload.auth_time > now + 5
      || payload.email_verified !== true || typeof payload.email !== 'string'
      || payload.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)
      || identity?.sign_in_provider !== 'google.com'
      || !Array.isArray(identity.identities?.['google.com']) || !identity.identities['google.com'].length)
    throw new Error('Unverified Google identity');
  return { uid:payload.sub, email:payload.email.toLowerCase() };
}
export function createVerifier(jwtVerify, keys) {
  return async token => {
    const { payload } = await jwtVerify(token, keys, {
      algorithms:['RS256'], issuer:`https://securetoken.google.com/${PROJECT}`,
      audience:PROJECT, clockTolerance:5, maxTokenAge:'10m'
    });
    return checkClaims(payload);
  };
}
