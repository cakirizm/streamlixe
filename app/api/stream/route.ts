const blocked = /^(localhost|127\.|0\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1\]?)/i;
function target(value: string | null) { if (!value) throw new Error("Adres eksik"); const url = new URL(value); if (!/^https?:$/.test(url.protocol) || blocked.test(url.hostname)) throw new Error("Adres engellendi"); return url; }
function proxied(value: string, base: URL) { return `/api/stream?url=${encodeURIComponent(new URL(value, base).href)}`; }

// Optional relay: some IPTV upstreams block Cloudflare's datacenter IP
// ranges. When set, forward through another origin (e.g. a shared-hosting
// deployment of this same app) that hits the upstream directly instead.
// That origin's own /api/stream does the real fetch + playlist rewriting,
// so this leg is a transparent pass-through — no double-processing.
const RELAY_ORIGIN = process.env.STREAM_RELAY_ORIGIN?.trim().replace(/\/$/, "");
const RELAY_HOST = process.env.STREAM_RELAY_HOST?.trim();
function resilientBody(body: ReadableStream<Uint8Array> | null) {
  if (!body) return null;
  const reader = body.getReader();
  return new ReadableStream<Uint8Array>({
    async pull(controller) {
      try { const { done, value } = await reader.read(); if (done) controller.close(); else controller.enqueue(value); }
      catch { controller.close(); }
    },
    async cancel(reason) { try { await reader.cancel(reason); } catch {} },
  });
}

export async function GET(request: Request) {
  try {
    const source = target(new URL(request.url).searchParams.get("url"));
    const headers = new Headers({ "user-agent": "VLC/3.0.21 LibVLC/3.0.21", accept: "*/*", "accept-language": "tr-TR,tr;q=0.9,en;q=0.7" });
    const range = request.headers.get("range"); if (range) headers.set("range", range);

    if (RELAY_ORIGIN) {
      const relayHeaders = new Headers();
      if (range) relayHeaders.set("range", range);
      if (RELAY_HOST) relayHeaders.set("host", RELAY_HOST);
      const relayed = await fetch(`${RELAY_ORIGIN}/api/stream?url=${encodeURIComponent(source.href)}`, { headers: relayHeaders, redirect: "follow" });
      const out = new Headers();
      ["content-type", "content-length", "content-range", "accept-ranges", "cache-control"].forEach(k => { const v = relayed.headers.get(k); if (v) out.set(k, v) });
      out.set("access-control-allow-origin", "*");
      return new Response(resilientBody(relayed.body), { status: relayed.status, headers: out });
    }

    const upstream = await fetch(source, { headers, redirect: "follow" });
    if (!upstream.ok && upstream.status !== 206) return new Response("Yayın alınamadı", { status: upstream.status });
    const type = upstream.headers.get("content-type") || ""; const finalSource=new URL(upstream.url||source.href);
    const isPlaylist = type.includes("mpegurl") || /\.m3u8($|\?)/i.test(finalSource.href) || /\.m3u8($|\?)/i.test(source.href);
    if (isPlaylist) {
      let text = await upstream.text();
      text = text.split(/\r?\n/).map(line => {
        const trimmed = line.trim();
        if (!trimmed) return line;
        if (!trimmed.startsWith("#")) return proxied(trimmed, finalSource);
        return line.replace(/URI="([^"]+)"/g, (_, uri) => `URI="${proxied(uri, finalSource)}"`);
      }).join("\n");
      return new Response(text, { headers: { "content-type": "application/vnd.apple.mpegurl", "cache-control": "no-store", "access-control-allow-origin": "*" } });
    }
    const out = new Headers();
    ["content-type","content-length","content-range","accept-ranges","cache-control"].forEach(k => { const v=upstream.headers.get(k); if(v)out.set(k,v) });
    out.set("access-control-allow-origin","*");
    return new Response(resilientBody(upstream.body), { status: upstream.status, headers: out });
  } catch (error) { const aborted=error instanceof Error&&(error.name==="AbortError"||error.message==="terminated");return new Response(aborted?null:error instanceof Error?error.message:"Yayın açılamadı", { status: aborted?499:400 }); }
}
