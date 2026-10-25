// Temporary publishing entrypoint. Deploy service.mjs after publishing to remove all write routes.
import service, { FILE } from "./service.mjs";

const certificate = "0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3";
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!url.pathname.startsWith("/__publish/")) return service.fetch(request, env);
    if (request.method !== "PUT") return json({ error: "Method not allowed" }, 405);
    const token = request.headers.get("Authorization")?.replace(/^Bearer /, "") || "";
    if (!token || token.length > 200 || (env.PUBLISH_UNTIL && Date.now() > Number(env.PUBLISH_UNTIL)) || !/^[a-f0-9]{64}$/.test(env.PUBLISH_HASH || "")) return json({ error: "Unauthorized" }, 401);
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
    const expected = new Uint8Array(env.PUBLISH_HASH.match(/../g).map(v => parseInt(v, 16)));
    if (!crypto.subtle.timingSafeEqual(digest, expected)) return json({ error: "Unauthorized" }, 401);
    const name = url.pathname.slice("/__publish/".length);
    const length = Number(request.headers.get("Content-Length"));
    if (!Number.isSafeInteger(length) || length < 1 || length > 30 * 1024 * 1024) return json({ error: "Invalid length" }, 400);
    try {
      if (name === "stable.json") {
        if (length > 16384) return json({ error: "Manifest too large" }, 400);
        const release = await request.json();
        if (release.applicationId !== "space.vexo.music.radio" || !Number.isSafeInteger(release.versionCode) || release.versionCode < 1 || !/^\d+\.\d+\.\d+$/.test(release.versionName) || typeof release.summary !== "string" || !release.summary.trim() || release.summary.length > 12000 || !/^[a-f0-9]{64}$/.test(release.sha256 || "") || release.certificateSha256 !== certificate)
          return json({ error: "Invalid release" }, 400);
        const apk = `VEXO-Music-${release.versionName}.apk`, source = `VEXO-Music-${release.versionName}-Source.zip`;
        const publicSource = /^[a-f0-9]{40}$/.test(release.sourceCommit || "") && release.sourceUrl === `https://github.com/ccatomic81-droid/VEXO-Music/archive/${release.sourceCommit}.zip`;
        if (release.downloadUrl !== `${url.origin}/releases/${apk}` || (!publicSource && release.sourceUrl !== `${url.origin}/releases/${source}`)) return json({ error: "Wrong download address" }, 400);
        if (env.PUBLISH_VERSION && (release.versionName !== env.PUBLISH_VERSION || release.sha256 !== env.PUBLISH_APK_HASH)) return json({ error: "Unreviewed release" }, 400);
        const stored = await env.RELEASES.head("releases/" + apk), sourceObject = publicSource || await env.RELEASES.head("releases/" + source);
        if (!stored || !sourceObject || stored.customMetadata?.sha256 !== release.sha256 || stored.size !== release.size) return json({ error: "Upload verified artifacts before publishing" }, 409);
        await env.RELEASES.put("stable.json", JSON.stringify(release), { httpMetadata: { contentType: "application/json" } });
        return json({ published: true, version: release.versionName });
      }
      if (!FILE.test(name) || (env.PUBLISH_VERSION && name !== `VEXO-Music-${env.PUBLISH_VERSION}.apk`)) return json({ error: "Not found" }, 404);
      const sha256 = request.headers.get("X-Upload-Sha256") || "";
      if (env.PUBLISH_APK_HASH && sha256 !== env.PUBLISH_APK_HASH) return json({ error: "Unreviewed artifact" }, 400);
      if (!/^[a-f0-9]{64}$/.test(sha256)) return json({ error: "Checksum required" }, 400);
      const key = "releases/" + name;
      const prior = await env.RELEASES.head(key);
      if (prior) return prior.customMetadata?.sha256 === sha256 ? json({ uploaded: true, existing: true }) : json({ error: "Versioned files are immutable" }, 409);
      await env.RELEASES.put(key, request.body, {
        sha256, httpMetadata: { contentType: name.endsWith(".apk") ? "application/vnd.android.package-archive" : "application/zip" }, customMetadata: { sha256 },
      });
      return json({ uploaded: true });
    } catch { return json({ error: "Publish failed" }, 400); }
  },
};
