const blocked = /^(localhost|127\.|0\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1\]?)/i;

// Optional relay: large Xtream accounts (tens of thousands of channels) can
// exceed the Cloudflare Workers per-request CPU time limit (free plan). When
// set, forward the raw import request to another origin (e.g. a shared-
// hosting deployment of this same app, which has no such per-request CPU
// cap) and pass its response straight through.
async function getImportRelayConfig() {
  let origin = (process.env.IMPORT_RELAY_ORIGIN || "").trim().replace(/\/$/, "");
  let host = (process.env.IMPORT_RELAY_HOST || "").trim();
  if (!origin) {
    try {
      const { env } = await import("cloudflare:workers");
      const e = env as Record<string, unknown> | undefined;
      if (typeof e?.IMPORT_RELAY_ORIGIN === "string") origin = e.IMPORT_RELAY_ORIGIN.trim().replace(/\/$/, "");
      if (typeof e?.IMPORT_RELAY_HOST === "string") host = e.IMPORT_RELAY_HOST.trim();
    } catch {}
  }
  return { origin: origin || null, host: host || null };
}

class ImportError extends Error {
  constructor(message: string, readonly status = 400) { super(message); }
}

function safeUrl(value: unknown) {
  if (typeof value !== "string" || !value.trim()) throw new ImportError("Sunucu adresi eksik");
  const input = value.trim();
  let url: URL;
  try { url = new URL(/^[a-z][a-z\d+.-]*:\/\//i.test(input) ? input : `http://${input}`); }
  catch { throw new ImportError("Sunucu adresi geçerli değil"); }
  if (!/^https?:$/.test(url.protocol)) throw new ImportError("Yalnızca HTTP veya HTTPS adresleri kullanılabilir");
  if (blocked.test(url.hostname)) throw new ImportError("Yerel ve özel ağ adreslerine güvenlik nedeniyle izin verilmiyor");
  return url;
}

// 45 saniye: büyük kanal/VOD/dizi listelerinde gövdenin tamamının inmesi 30 saniyeden uzun
// sürebiliyordu ve bu durumda ham bir "aborted due to timeout" hatası kullanıcıya sızıyordu.
const REQUEST_TIMEOUT_MS = 45000;
async function get(url: URL) {
  try {
    const response = await fetch(url, { headers: { "user-agent": "VLC/3.0.21 LibVLC/3.0.21", accept: "*/*" }, redirect: "follow", signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) });
    // 502/504 kullanmıyoruz: relay mimarisinde bu istek bir Cloudflare Worker'ın fetch()
    // alt-isteği olarak görülebiliyor ve Cloudflare, 502/504 durum kodlu alt-istek yanıtlarının
    // gövdesini kendi jenerik hata sayfasıyla değiştiriyor -- bizim Türkçe mesajımızı yutuyor.
    if (!response.ok) throw new ImportError(`Yayın sunucusu ${response.status} hatası verdi`, 500);
    return response;
  } catch (error) {
    if (error instanceof ImportError) throw error;
    throw new ImportError(error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError") ? `Yayın sunucusu ${REQUEST_TIMEOUT_MS / 1000} saniye içinde yanıt vermedi` : "Yayın sunucusuna bağlanılamadı", 500);
  }
}
// Gövdeyi okurken de aynı zaman aşımı/bağlantı kopması olabilir (büyük listelerde veri inerken
// bağlantı kesilebilir) — bu, get()'in kendi try/catch'inin DIŞINDaydı ve ham bir tarayıcı/JS
// hatasını ("The operation was aborted due to timeout") doğrudan kullanıcıya sızdırıyordu. Tüm
// çağıranlar artık gövdeyi bu fonksiyon üzerinden okuyor, böylece her hata Türkçe ve anlaşılır.
async function getText(url: URL) {
  const response = await get(url);
  try { return await response.text(); }
  catch (error) {
    throw new ImportError(error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError") ? "Yayın sunucusundan veri indirilirken bağlantı zaman aşımına uğradı — liste çok büyük olabilir, tekrar deneyin" : "Yayın sunucusundan veri okunamadı", 500);
  }
}

export async function POST(request: Request) {
  const { origin: IMPORT_RELAY_ORIGIN, host: IMPORT_RELAY_HOST } = await getImportRelayConfig();
  if (IMPORT_RELAY_ORIGIN) {
    try {
      const bodyText = await request.text();
      const relayHeaders = new Headers({ "content-type": "application/json" });
      if (IMPORT_RELAY_HOST) relayHeaders.set("host", IMPORT_RELAY_HOST);
      // Kept comfortably under Cloudflare Workers' own request duration limit so
      // we always get to return our own JSON error instead of Cloudflare's raw
      // "error code: 502" page when a request runs long.
      const relayed = await fetch(`${IMPORT_RELAY_ORIGIN}/api/import`, { method: "POST", headers: relayHeaders, body: bodyText, signal: AbortSignal.timeout(45000) });
      const text = await relayed.text();
      // Cloudflare'in kendisi, Worker'dan gelen 502/504 durum kodlu yanıtların gövdesini kendi
      // jenerik hata sayfasıyla değiştiriyor (bizim Türkçe JSON hata mesajımızı yutuyor). Bu
      // yüzden gerçek durumu 500'e eşliyoruz -- Cloudflare bu koda dokunmuyor, kullanıcı bizim
      // gövdemizi görür.
      const status = relayed.status === 502 || relayed.status === 504 ? 500 : relayed.status;
      return new Response(text, { status, headers: { "content-type": "application/json" } });
    } catch (error) {
      const timedOut = error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError");
      return Response.json({ error: timedOut ? "İçe aktarma zaman aşımına uğradı — liste çok büyük veya sunucu yavaş yanıt veriyor olabilir, tekrar deneyin" : "İçe aktarma sunucusuna ulaşılamadı, tekrar deneyin" }, { status: 500 });
    }
  }
  try {
    const body = await request.json() as { method?: string; url?: string; server?: string; username?: string; password?: string; seriesId?: string; streamId?: string; channelId?: string; channelName?: string };
    if (body.method === "m3u") {
      const text = await getText(safeUrl(body.url));
      if (!text.includes("#EXTINF") && !text.includes("#EXTM3U")) throw new Error("Adres geçerli bir M3U listesi döndürmedi");
      return Response.json({ text });
    }
    if (body.method === "xtream") {
      if (!body.username?.trim() || !body.password) throw new ImportError("Kullanıcı adı ve şifre gerekli");
      const base = safeUrl(body.server);
      const endpoint = new URL("player_api.php", base.href.endsWith("/") ? base : new URL(base.href + "/"));
      endpoint.searchParams.set("username", body.username || ""); endpoint.searchParams.set("password", body.password || "");
      const call = async (action?: string) => {
        const url = new URL(endpoint); if (action) url.searchParams.set("action", action);
        // Gövdeyi tek seferde metin olarak okuyup öyle ayrıştırıyoruz (response.clone() DEĞİL) —
        // klonlamak gövdeyi iki kez bellekte tutar; büyük kanal/VOD listelerinde (birkaç MB'lık
        // JSON) bu, Workers'ın bellek sınırına takılıp isteğin tamamen başarısız olmasına yol
        // açabiliyordu. Tek okuma hem tanı bilgisini korur hem de fazladan bellek harcamaz.
        const text = await getText(url);
        try { return JSON.parse(text); }
        catch {
          // Sunucu 200 döndü ama gövde geçerli JSON değil — genelde panel bu adreste Xtream API
          // sunmuyor (giriş sayfası/HTML döndürüyor), yanlış protokol (http/https) kullanılıyor
          // veya panel bir güvenlik duvarı/CDN sayfasıyla karşılık veriyor. Kullanıcı bilgilerini
          // asla loglamadan, sunucunun gerçekte ne döndürdüğünü kısa bir örnekle gösteriyoruz.
          const snippet = text.replace(/\s+/g, " ").trim().slice(0, 160);
          const looksHtml = /^<(!doctype|html)/i.test(snippet);
          const hint = snippet ? ` Sunucu şunu döndürdü: "${snippet}"${looksHtml ? " — bu bir HTML sayfası, panel bu adreste Xtream API sunmuyor olabilir (adres/protokol http-https hatalı olabilir veya bu bir Xtream paneli değil)." : "."}` : "";
          throw new ImportError(`Yayın sunucusu geçerli Xtream verisi döndürmedi.${hint}`, 500);
        }
      };
      const account = await call();
      if (account?.user_info?.auth !== 1 && account?.user_info?.auth !== "1") throw new Error(account?.user_info?.message || "Xtream kullanıcı bilgileri kabul edilmedi");
      const [live, vod, series, liveCategories, vodCategories, seriesCategories] = await Promise.all([call("get_live_streams"), call("get_vod_streams"), call("get_series"), call("get_live_categories"), call("get_vod_categories"), call("get_series_categories")]);
      return Response.json({ live, vod, series, liveCategories, vodCategories, seriesCategories });
    }
    if (body.method === "series_info") {
      const base = safeUrl(body.server); const endpoint = new URL("player_api.php", base.href.endsWith("/") ? base : new URL(base.href + "/"));
      endpoint.searchParams.set("username", body.username || ""); endpoint.searchParams.set("password", body.password || ""); endpoint.searchParams.set("action", "get_series_info"); endpoint.searchParams.set("series_id", body.seriesId || "");
      return Response.json(JSON.parse(await getText(endpoint)));
    }
    if (body.method === "vod_info") {
      const base = safeUrl(body.server); const endpoint = new URL("player_api.php", base.href.endsWith("/") ? base : new URL(base.href + "/"));
      endpoint.searchParams.set("username", body.username || ""); endpoint.searchParams.set("password", body.password || ""); endpoint.searchParams.set("action", "get_vod_info"); endpoint.searchParams.set("vod_id", body.streamId || "");
      return Response.json(JSON.parse(await getText(endpoint)));
    }
    if (body.method === "short_epg") {
      const base = safeUrl(body.server); const endpoint = new URL("player_api.php", base.href.endsWith("/") ? base : new URL(base.href + "/"));
      endpoint.searchParams.set("username", body.username || ""); endpoint.searchParams.set("password", body.password || ""); endpoint.searchParams.set("action", "get_short_epg"); endpoint.searchParams.set("stream_id", body.streamId || ""); endpoint.searchParams.set("limit", "8");
      return Response.json(JSON.parse(await getText(endpoint)));
    }
    if (body.method === "xmltv_epg") {
      const xml = await getText(safeUrl(body.url)); const normalize=(v:string)=>v.toLocaleLowerCase("tr").replace(/[^a-z0-9çğıöşü]+/gi," ").trim();
      const clean=(v:string)=>v.replace(/<!\[CDATA\[|\]\]>/g,"").replace(/<[^>]+>/g,"").replace(/&amp;/g,"&").replace(/&lt;/g,"<").replace(/&gt;/g,">").trim(); const channels=new Map<string,string>();
      for(const match of xml.matchAll(/<channel\s+id=["']([^"']+)["'][^>]*>([\s\S]*?)<\/channel>/gi)){channels.set(match[1],clean(match[2].match(/<display-name[^>]*>([\s\S]*?)<\/display-name>/i)?.[1]||""))}
      const target=[...channels.entries()].find(([key,label])=>key.toLowerCase()===(body.channelId||"").toLowerCase()||normalize(label)===normalize(body.channelName||""))?.[0]||body.channelId||""; const escaped=target.replace(/[.*+?^${}()|[\]\\]/g,"\\$&"); const programmes=[];
      for(const match of xml.matchAll(/<programme\s+([^>]+)>([\s\S]*?)<\/programme>/gi)){if(!new RegExp(`channel=["']${escaped}["']`,"i").test(match[1]))continue;programmes.push({start:match[1].match(/start=["']([^"']+)/i)?.[1]||"",stop:match[1].match(/stop=["']([^"']+)/i)?.[1]||"",title:clean(match[2].match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]||"Program bilgisi"),description:clean(match[2].match(/<desc[^>]*>([\s\S]*?)<\/desc>/i)?.[1]||"")});if(programmes.length>=12)break}
      return Response.json({ programmes });
    }
    throw new ImportError("Desteklenmeyen içe aktarma yöntemi");
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Liste alınamadı" }, { status: error instanceof ImportError ? error.status : 400 });
  }
}
