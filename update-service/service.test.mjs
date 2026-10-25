import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash, timingSafeEqual } from "node:crypto";
import service from "./service.mjs";
import publisher from "./publisher.mjs";

const bytes = new TextEncoder().encode("a signed APK fixture");
function bucket() {
  const objects = new Map();
  const make = (key, value, metadata = {}) => objects.set(key, { value, metadata });
  make("releases/VEXO-Music-0.8.0.apk", bytes);
  return {
    objects, make,
    async head(key) { const o = objects.get(key); return o ? { size: o.value.length, httpEtag: '"fixture"', customMetadata: o.metadata } : null; },
    async get(key, options) { const o = objects.get(key); if (!o) return null; const range = options?.range; return { size: o.value.length, httpEtag: '"fixture"', body: range ? o.value.slice(range.offset, range.offset + range.length) : o.value }; },
    async put(key, value, options) {
      const data = typeof value === "string" ? new TextEncoder().encode(value) : new Uint8Array(await new Response(value).arrayBuffer());
      if (options?.sha256 && createHash("sha256").update(data).digest("hex") !== options.sha256) throw Error("Checksum mismatch");
      make(key, data, options?.customMetadata); return this.head(key);
    },
  };
}

test("public service streams real bytes, supports resume and does not expose write routes", async () => {
  const env = { RELEASES: bucket() };
  const full = await service.fetch(new Request("https://updates.test/releases/VEXO-Music-0.8.0.apk"), env);
  assert.equal(full.status, 200); assert.equal(full.headers.get("Content-Type"), "application/vnd.android.package-archive");
  assert.deepEqual(new Uint8Array(await full.arrayBuffer()), bytes);
  const partial = await service.fetch(new Request("https://updates.test/releases/VEXO-Music-0.8.0.apk", { headers: { Range: "bytes=2-5" } }), env);
  assert.equal(partial.status, 206); assert.equal(partial.headers.get("Content-Length"), "4"); assert.deepEqual(new Uint8Array(await partial.arrayBuffer()), bytes.slice(2,6));
  assert.equal((await service.fetch(new Request("https://updates.test/releases/VEXO-Music-0.8.0.apk", { headers: { Range: "bytes=999-" } }), env)).status, 416);
  assert.equal((await service.fetch(new Request("https://updates.test/releases/secrets.txt"), env)).status, 404);
  assert.equal((await service.fetch(new Request("https://updates.test/__publish/stable.json", { method: "PUT", body: "{}" }), env)).status, 405);
  assert.equal((await service.fetch(new Request("https://updates.test/stable.json"), env)).status, 503);
});

test("publishing rejects unauthenticated writes and refuses manifest before signed files exist", async () => {
  crypto.subtle.timingSafeEqual = (a,b) => timingSafeEqual(Buffer.from(a),Buffer.from(b));
  const token = "test publishing capability";
  const env = { RELEASES: bucket(), PUBLISH_HASH: createHash("sha256").update(token).digest("hex") };
  const release = {
    applicationId: "space.vexo.music.radio", versionCode: 10, versionName: "0.8.0", summary: "Profile and clearer artwork",
    downloadUrl: "https://updates.test/releases/VEXO-Music-0.8.0.apk", sourceUrl: "https://updates.test/releases/VEXO-Music-0.8.0-Source.zip",
    sha256: createHash("sha256").update(bytes).digest("hex"), size: bytes.length,
    certificateSha256: "0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3",
  };
  function request(value, auth = token) { const body = JSON.stringify(value); return new Request("https://updates.test/__publish/stable.json", { method: "PUT", body, headers: { Authorization: "Bearer " + auth, "Content-Length": String(Buffer.byteLength(body)) } }); }
  assert.equal((await publisher.fetch(request(release,"wrong"),env)).status,401);
  assert.equal((await publisher.fetch(request(release),env)).status,409);
  env.RELEASES.make("releases/VEXO-Music-0.8.0.apk", bytes, { sha256: release.sha256 });
  env.RELEASES.make("releases/VEXO-Music-0.8.0-Source.zip", new Uint8Array([1,2,3]));
  assert.equal((await publisher.fetch(request({ ...release, downloadUrl: "https://other.test/app.apk" }),env)).status,400);
  assert.equal((await publisher.fetch(request(release),env)).status,200);
  const live = await service.fetch(new Request("https://updates.test/stable.json"),env);
  assert.equal(live.status,200);assert.deepEqual(await live.json(),release);
});

test("reviewed release may link to existing public source and publishing capability expires", async () => {
  crypto.subtle.timingSafeEqual = (a,b) => timingSafeEqual(Buffer.from(a),Buffer.from(b));
  const token="reviewed capability", sha256=createHash("sha256").update(bytes).digest("hex"), sourceCommit="a".repeat(40);
  const env={RELEASES:bucket(), PUBLISH_HASH:createHash("sha256").update(token).digest("hex"), PUBLISH_UNTIL:String(Date.now()+60000), PUBLISH_VERSION:"0.12.0", PUBLISH_APK_HASH:sha256};
  env.RELEASES.make("releases/VEXO-Music-0.12.0.apk",bytes,{sha256});
  const release={applicationId:"space.vexo.music.radio",versionCode:14,versionName:"0.12.0",summary:"Solid colors",downloadUrl:"https://updates.test/releases/VEXO-Music-0.12.0.apk",sourceCommit,sourceUrl:`https://github.com/ccatomic81-droid/VEXO-Music/archive/${sourceCommit}.zip`,sha256,size:bytes.length,certificateSha256:"0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3"};
  function request(value){const body=JSON.stringify(value);return new Request("https://updates.test/__publish/stable.json",{method:"PUT",body,headers:{Authorization:"Bearer "+token,"Content-Length":String(Buffer.byteLength(body))}});}
  assert.equal((await publisher.fetch(request({...release,sourceUrl:"https://github.com/another/repo/archive/"+sourceCommit+".zip"}),env)).status,400);
  assert.equal((await publisher.fetch(request({...release,sha256:"0".repeat(64)}),env)).status,400);
  assert.equal((await publisher.fetch(request(release),env)).status,200);
  env.PUBLISH_UNTIL=String(Date.now()-1);
  assert.equal((await publisher.fetch(request(release),env)).status,401);
});
