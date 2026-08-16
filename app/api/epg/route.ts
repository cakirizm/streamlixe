const TURKEY_EPG_URL = "https://iptv-epg.org/files/epg-tr.xml";
const MAX_EPG_BYTES = 35_000_000;

function clean(value: string) {
  return value
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ").trim();
}

function normalize(value: string) {
  return clean(value).toLocaleLowerCase("tr")
    .replace(/[İIı]/g, "i").normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .replace(/\b(?:tr|turkey|turkiye|live|canli|4k|uhd|fhd|full\s*hd|hd|sd|hevc|h265|raw)\b/g, " ")
    .replace(/[^a-z0-9çğıöşü]+/gi, " ").replace(/\s+/g, " ").trim();
}

function score(query: string, candidate: string) {
  if (!query || !candidate) return 0;
  if (query === candidate) return 100;
  const compactQuery = query.replace(/\s/g, "");
  const compactCandidate = candidate.replace(/\s/g, "");
  if (compactQuery === compactCandidate) return 95;
  if (query.length >= 4 && (candidate.includes(query) || query.includes(candidate))) return 70;
  const queryWords = new Set(query.split(" "));
  const words = candidate.split(" ");
  const shared = words.filter(word => queryWords.has(word)).length;
  return shared >= 2 ? 40 + shared * 5 : 0;
}

export async function GET(request: Request) {
  const input = new URL(request.url);
  const channelName = input.searchParams.get("channel")?.trim() || "";
  const channelId = input.searchParams.get("id")?.trim() || "";
  if (!channelName && !channelId) return Response.json({ error: "Kanal adı gerekli" }, { status: 400 });

  try {
    const response = await fetch(TURKEY_EPG_URL, {
      headers: { accept: "application/xml,text/xml" },
      redirect: "follow",
      signal: AbortSignal.timeout(20000),
    });
    if (!response.ok) throw new Error(`EPG ${response.status}`);
    const size = Number(response.headers.get("content-length") || 0);
    if (size > MAX_EPG_BYTES) throw new Error("EPG dosyası çok büyük");
    const xml = await response.text();
    if (xml.length > MAX_EPG_BYTES) throw new Error("EPG dosyası çok büyük");

    const queryNames = [channelName, channelId].map(normalize).filter(Boolean);
    let best = { id: "", name: "", score: 0 };
    for (const match of xml.matchAll(/<channel\s+([^>]+)>([\s\S]*?)<\/channel>/gi)) {
      const id = match[1].match(/id=["']([^"']+)/i)?.[1] || "";
      const names = [...match[2].matchAll(/<display-name[^>]*>([\s\S]*?)<\/display-name>/gi)].map(row => clean(row[1]));
      for (const name of [id, ...names]) {
        const candidate = normalize(name);
        const candidateScore = Math.max(...queryNames.map(query => score(query, candidate)), 0);
        if (candidateScore > best.score) best = { id, name: names[0] || name, score: candidateScore };
      }
    }
    if (!best.id || best.score < 70) return Response.json({ matched: false, programmes: [] }, { headers: { "cache-control": "public, max-age=900" } });

    const escaped = best.id.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const programmes = [];
    for (const match of xml.matchAll(/<programme\s+([^>]+)>([\s\S]*?)<\/programme>/gi)) {
      if (!new RegExp(`channel=["']${escaped}["']`, "i").test(match[1])) continue;
      programmes.push({
        start: match[1].match(/start=["']([^"']+)/i)?.[1] || "",
        stop: match[1].match(/stop=["']([^"']+)/i)?.[1] || "",
        title: clean(match[2].match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] || "Program bilgisi"),
        description: clean(match[2].match(/<desc[^>]*>([\s\S]*?)<\/desc>/i)?.[1] || ""),
      });
      if (programmes.length >= 48) break;
    }
    return Response.json({ matched: true, channel: best.name, programmes }, { headers: { "cache-control": "public, max-age=1800" } });
  } catch (error) {
    console.error("Automatic EPG failed", error);
    return Response.json({ error: "İnternet program rehberine ulaşılamadı", programmes: [] }, { status: 502 });
  }
}
