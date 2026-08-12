const blocked = /^(localhost|127\.|0\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1\]?)/i;

function safeUrl(value: unknown) {
  if (typeof value !== "string") throw new Error("Geçersiz adres");
  const url = new URL(value);
  if (!/^https?:$/.test(url.protocol) || blocked.test(url.hostname)) throw new Error("Bu sunucu adresine izin verilmiyor");
  return url;
}

async function get(url: URL) {
  const response = await fetch(url, { headers: { "user-agent": "StreamLiveX/1.0", accept: "*/*" }, redirect: "follow" });
  if (!response.ok) throw new Error(`Yayın sunucusu ${response.status} hatası verdi`);
  return response;
}

export async function POST(request: Request) {
  try {
    const body = await request.json() as { method?: string; url?: string; server?: string; username?: string; password?: string; seriesId?: string; streamId?: string; channelId?: string; channelName?: string };
    if (body.method === "m3u") {
      const response = await get(safeUrl(body.url));
      const text = await response.text();
      if (!text.includes("#EXTINF") && !text.includes("#EXTM3U")) throw new Error("Adres geçerli bir M3U listesi döndürmedi");
      return Response.json({ text });
    }
    if (body.method === "xtream") {
      const base = safeUrl(body.server);
      const endpoint = new URL("player_api.php", base.href.endsWith("/") ? base : new URL(base.href + "/"));
      endpoint.searchParams.set("username", body.username || ""); endpoint.searchParams.set("password", body.password || "");
      const call = async (action?: string) => { const url = new URL(endpoint); if (action) url.searchParams.set("action", action); return (await get(url)).json(); };
      const account = await call();
      if (account?.user_info?.auth !== 1 && account?.user_info?.auth !== "1") throw new Error(account?.user_info?.message || "Xtream kullanıcı bilgileri kabul edilmedi");
      const [live, vod, series, liveCategories, vodCategories, seriesCategories] = await Promise.all([call("get_live_streams"), call("get_vod_streams"), call("get_series"), call("get_live_categories"), call("get_vod_categories"), call("get_series_categories")]);
      return Response.json({ live, vod, series, liveCategories, vodCategories, seriesCategories });
    }
    if (body.method === "series_info") {
      const base = safeUrl(body.server); const endpoint = new URL("player_api.php", base.href.endsWith("/") ? base : new URL(base.href + "/"));
      endpoint.searchParams.set("username", body.username || ""); endpoint.searchParams.set("password", body.password || ""); endpoint.searchParams.set("action", "get_series_info"); endpoint.searchParams.set("series_id", body.seriesId || "");
      return Response.json(await (await get(endpoint)).json());
    }
    if (body.method === "short_epg") {
      const base = safeUrl(body.server); const endpoint = new URL("player_api.php", base.href.endsWith("/") ? base : new URL(base.href + "/"));
      endpoint.searchParams.set("username", body.username || ""); endpoint.searchParams.set("password", body.password || ""); endpoint.searchParams.set("action", "get_short_epg"); endpoint.searchParams.set("stream_id", body.streamId || ""); endpoint.searchParams.set("limit", "8");
      return Response.json(await (await get(endpoint)).json());
    }
    if (body.method === "xmltv_epg") {
      const xml = await (await get(safeUrl(body.url))).text(); const normalize=(v:string)=>v.toLocaleLowerCase("tr").replace(/[^a-z0-9çğıöşü]+/gi," ").trim();
      const clean=(v:string)=>v.replace(/<!\[CDATA\[|\]\]>/g,"").replace(/<[^>]+>/g,"").replace(/&amp;/g,"&").replace(/&lt;/g,"<").replace(/&gt;/g,">").trim(); const channels=new Map<string,string>();
      for(const match of xml.matchAll(/<channel\s+id=["']([^"']+)["'][^>]*>([\s\S]*?)<\/channel>/gi)){channels.set(match[1],clean(match[2].match(/<display-name[^>]*>([\s\S]*?)<\/display-name>/i)?.[1]||""))}
      const target=[...channels.entries()].find(([key,label])=>key.toLowerCase()===(body.channelId||"").toLowerCase()||normalize(label)===normalize(body.channelName||""))?.[0]||body.channelId||""; const escaped=target.replace(/[.*+?^${}()|[\]\\]/g,"\\$&"); const programmes=[];
      for(const match of xml.matchAll(/<programme\s+([^>]+)>([\s\S]*?)<\/programme>/gi)){if(!new RegExp(`channel=["']${escaped}["']`,"i").test(match[1]))continue;programmes.push({start:match[1].match(/start=["']([^"']+)/i)?.[1]||"",stop:match[1].match(/stop=["']([^"']+)/i)?.[1]||"",title:clean(match[2].match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]||"Program bilgisi"),description:clean(match[2].match(/<desc[^>]*>([\s\S]*?)<\/desc>/i)?.[1]||"")});if(programmes.length>=12)break}
      return Response.json({ programmes });
    }
    throw new Error("Desteklenmeyen içe aktarma yöntemi");
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Liste alınamadı" }, { status: 400 });
  }
}
