const blocked = /^(localhost|127\.|0\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1\]?)/i;
const STARTUP_RETRYABLE = new Set([403, 404, 429, 500, 502, 503, 504]);
const RESPONSE_TIMEOUT_MS = 15000;

function target(value: string | null) {
  if (!value) throw new Error("Adres eksik");
  const url = new URL(value);
  if (!/^https?:$/.test(url.protocol) || blocked.test(url.hostname)) throw new Error("Adres engellendi");
  return url;
}
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
      try {
        const { done, value } = await reader.read();
        if (done) controller.close(); else controller.enqueue(value);
      } catch (error) {
        if (error instanceof Error && error.name === "AbortError") controller.close();
        else controller.error(error);
      }
    },
    async cancel(reason) { try { await reader.cancel(reason); } catch {} },
  });
}

async function wait(ms: number, signal: AbortSignal) {
  await new Promise<void>((resolve, reject) => {
    if (signal.aborted) { reject(signal.reason); return; }
    const timer = setTimeout(done, ms);
    function done() { signal.removeEventListener("abort", aborted); resolve(); }
    function aborted() { clearTimeout(timer); signal.removeEventListener("abort", aborted); reject(signal.reason); }
    signal.addEventListener("abort", aborted, { once: true });
  });
}

// The timeout only covers receiving the response headers. Once streaming starts,
// cancellation is handled by resilientBody so long-running live streams are not
// accidentally cut off after a fixed duration.
async function fetchHeaders(url: URL | string, init: RequestInit, requestSignal: AbortSignal) {
  const controller = new AbortController();
  const abort = () => controller.abort(requestSignal.reason);
  requestSignal.addEventListener("abort", abort, { once: true });
  const timer = setTimeout(() => controller.abort(new DOMException("Yayın sunucusu zaman aşımına uğradı", "TimeoutError")), RESPONSE_TIMEOUT_MS);
  try {
    return await fetch(url, { ...init, cache: "no-store", signal: controller.signal });
  } finally {
    clearTimeout(timer);
    requestSignal.removeEventListener("abort", abort);
  }
}

async function fetchUpstream(source: URL, headers: Headers, signal: AbortSignal, startup: boolean) {
  let attempts = 0;
  while (true) {
    attempts++;
    const response = await fetchHeaders(source, { headers, redirect: "follow" }, signal);
    const status = response.status;
    const retryLimit = status === 403 || status === 404 ? 2 : 3;
    if (!startup || response.ok || status === 206 || !STARTUP_RETRYABLE.has(status) || attempts >= retryLimit) {
      return { response, attempts };
    }
    try { await response.body?.cancel("startup retry"); } catch {}
    await wait(350 * attempts + Math.floor(Math.random() * 250), signal);
  }
}

export async function GET(request: Request) {
  try {
    const requestUrl = new URL(request.url);
    const source = target(requestUrl.searchParams.get("url"));
    const startup = requestUrl.searchParams.get("startup") === "1";
    const headers = new Headers({ "user-agent": "VLC/3.0.21 LibVLC/3.0.21", accept: "*/*", "accept-language": "tr-TR,tr;q=0.9,en;q=0.7" });
    const range = request.headers.get("range"); if (range) headers.set("range", range);

    if (RELAY_ORIGIN) {
      const relayHeaders = new Headers();
      if (range) relayHeaders.set("range", range);
      if (RELAY_HOST) relayHeaders.set("host", RELAY_HOST);
      const relayUrl = new URL("/api/stream", `${RELAY_ORIGIN}/`);
      relayUrl.searchParams.set("url", source.href);
      if (startup) relayUrl.searchParams.set("startup", "1");
      const relayed = await fetchHeaders(relayUrl, { headers: relayHeaders, redirect: "follow" }, request.signal);
      const out = new Headers();
      ["content-type", "content-length", "content-range", "accept-ranges", "cache-control", "x-streamlivex-attempts"].forEach(k => { const v = relayed.headers.get(k); if (v) out.set(k, v); });
      out.set("access-control-allow-origin", "*");
      // Cloudflare, Worker'dan gelen 502/504 durum kodlu yanıtların gövdesini kendi jenerik
      // hata sayfasıyla değiştiriyor; gerçek durumu Cloudflare'in dokunmadığı 500'e eşliyoruz.
      return new Response(resilientBody(relayed.body), { status: relayed.status === 502 || relayed.status === 504 ? 500 : relayed.status, headers: out });
    }

    const { response: upstream, attempts } = await fetchUpstream(source, headers, request.signal, startup);
    if (!upstream.ok && upstream.status !== 206) {
      const status = upstream.status === 502 || upstream.status === 504 ? 500 : upstream.status;
      return new Response("Yayın alınamadı", { status, headers: { "cache-control": "no-store", "x-streamlivex-attempts": String(attempts) } });
    }
    const type = upstream.headers.get("content-type") || "";
    const finalSource = new URL(upstream.url || source.href);
    const isPlaylist = type.includes("mpegurl") || /\.m3u8($|\?)/i.test(finalSource.href) || /\.m3u8($|\?)/i.test(source.href);
    if (isPlaylist) {
      let playlist = await upstream.text();
      playlist = playlist.split(/\r?\n/).map(line => {
        const trimmed = line.trim();
        if (!trimmed) return line;
        if (!trimmed.startsWith("#")) return proxied(trimmed, finalSource);
        return line.replace(/URI="([^"]+)"/g, (_, uri) => `URI="${proxied(uri, finalSource)}"`);
      }).join("\n");
      return new Response(playlist, { headers: { "content-type": "application/vnd.apple.mpegurl", "cache-control": "no-store", "access-control-allow-origin": "*", "x-streamlivex-attempts": String(attempts) } });
    }
    const out = new Headers();
    ["content-type", "content-length", "content-range", "accept-ranges", "cache-control"].forEach(k => { const v = upstream.headers.get(k); if (v) out.set(k, v); });
    out.set("access-control-allow-origin", "*");
    out.set("x-streamlivex-attempts", String(attempts));
    return new Response(resilientBody(upstream.body), { status: upstream.status === 502 || upstream.status === 504 ? 500 : upstream.status, headers: out });
  } catch (error) {
    const aborted = request.signal.aborted || error instanceof Error && error.name === "AbortError";
    const timedOut = error instanceof Error && error.name === "TimeoutError";
    const networkError = error instanceof TypeError;
    // Cloudflare, Worker'dan gelen 502/504 durum kodlu yanıtların gövdesini kendi jenerik hata
    // sayfasıyla değiştiriyor (Türkçe mesajımızı yutuyor); bu yüzden 500 kullanıyoruz.
    return new Response(aborted ? null : timedOut ? "Yayın sunucusu zaman aşımına uğradı" : networkError ? "Yayın sunucusuna bağlanılamadı" : error instanceof Error ? error.message : "Yayın açılamadı", { status: aborted ? 499 : timedOut || networkError ? 500 : 400 });
  }
}
