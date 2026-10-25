const FILE = /^VEXO-Music-\d+\.\d+\.\d+(?:\.apk|-Source\.zip)$/;

function reply(text, status = 200, headers = {}) {
  return new Response(text, { status, headers: { "X-Content-Type-Options": "nosniff", ...headers } });
}

export function rangeFor(value, size) {
  const match = /^bytes=(\d*)-(\d*)$/.exec(value || "");
  if (!match || (!match[1] && !match[2]) || size < 1) return null;
  let start, end;
  if (!match[1]) { const count = Number(match[2]); if (!Number.isSafeInteger(count) || count < 1) return null; start = Math.max(0, size - count); end = size - 1; }
  else { start = Number(match[1]); end = match[2] ? Math.min(Number(match[2]), size - 1) : size - 1; }
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || start >= size || end < start) return null;
  return { offset: start, length: end - start + 1 };
}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (request.method !== "GET" && request.method !== "HEAD") return reply("Method not allowed", 405, { Allow: "GET, HEAD" });
    const stable = path === "/stable.json";
    const name = path.startsWith("/releases/") ? path.slice(10) : "";
    if (!stable && !FILE.test(name)) return reply("Not found", 404);
    const key = stable ? "stable.json" : "releases/" + name;
    try {
      const rangeHeader = !stable && request.method === "GET" ? request.headers.get("Range") : null;
      let range;
      if (rangeHeader) {
        const info = await env.RELEASES.head(key);
        if (!info) return reply("Not found", 404);
        range = rangeFor(rangeHeader, info.size);
        if (!range) return reply("Range not satisfiable", 416, { "Content-Range": `bytes */${info.size}` });
      }
      const object = request.method === "HEAD" ? await env.RELEASES.head(key) : await env.RELEASES.get(key, range ? { range } : undefined);
      if (!object) return reply(stable ? "Release is being prepared" : "Not found", stable ? 503 : 404, { "Cache-Control": "no-store" });
      if (stable && object.size > 32768) return reply("Release temporarily unavailable", 503, { "Cache-Control": "no-store" });
      const headers = new Headers({
        "Content-Type": stable ? "application/json; charset=utf-8" : name.endsWith(".apk") ? "application/vnd.android.package-archive" : "application/zip",
        "Cache-Control": stable ? "public, max-age=60, must-revalidate" : "public, max-age=86400, immutable",
        "X-Content-Type-Options": "nosniff",
        ETag: object.httpEtag,
        "Content-Length": String(range ? range.length : object.size),
      });
      if (!stable) { headers.set("Accept-Ranges", "bytes"); headers.set("Content-Disposition", `attachment; filename="${name}"`); }
      if (!range && request.headers.get("If-None-Match") === object.httpEtag) { headers.delete("Content-Length"); return new Response(null, { status: 304, headers }); }
      if (range) headers.set("Content-Range", `bytes ${range.offset}-${range.offset + range.length - 1}/${object.size}`);
      return new Response(request.method === "HEAD" ? null : object.body, { status: range ? 206 : 200, headers });
    } catch {
      console.error(JSON.stringify({ event: "release_read_failed" }));
      return reply("Release temporarily unavailable", 503, { "Cache-Control": "no-store" });
    }
  },
};

export { FILE };
