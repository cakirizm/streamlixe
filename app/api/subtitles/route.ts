const OPENSUBTITLES_ORIGIN = "https://api.opensubtitles.com/api/v1";
const USER_AGENT = "StreamLiveX v0.1.0";

type SearchResult = {
  fileId: string;
  language: string;
  release: string;
  downloadCount: number;
};

function authHeaders(apiKey: string) {
  return {
    "Api-Key": apiKey,
    "User-Agent": USER_AGENT,
    accept: "application/json",
  };
}

async function searchSubtitles(apiKey: string, query: string, langs: string) {
  const url = new URL(`${OPENSUBTITLES_ORIGIN}/subtitles`);
  url.searchParams.set("query", query);
  url.searchParams.set("languages", langs);
  const response = await fetch(url, { headers: authHeaders(apiKey), signal: AbortSignal.timeout(12000) });
  if (!response.ok) throw new Error(`OpenSubtitles ${response.status}`);
  const data = (await response.json()) as { data?: any[] };
  const results: SearchResult[] = [];
  for (const row of data.data || []) {
    const attrs = row.attributes || {};
    const file = attrs.files?.[0];
    if (!file?.file_id) continue;
    results.push({
      fileId: String(file.file_id),
      language: String(attrs.language || "und"),
      release: String(attrs.release || file.file_name || "Altyazı"),
      downloadCount: Number(attrs.download_count || 0),
    });
  }
  results.sort((a, b) => b.downloadCount - a.downloadCount);
  return results.slice(0, 20);
}

async function resolveDownloadLink(apiKey: string, fileId: string) {
  const response = await fetch(`${OPENSUBTITLES_ORIGIN}/download`, {
    method: "POST",
    headers: { ...authHeaders(apiKey), "content-type": "application/json" },
    body: JSON.stringify({ file_id: Number(fileId) }),
    signal: AbortSignal.timeout(12000),
  });
  if (!response.ok) throw new Error(`OpenSubtitles download ${response.status}`);
  const data = (await response.json()) as { link?: string; file_name?: string };
  if (!data.link) throw new Error("OpenSubtitles indirme bağlantısı boş döndü");
  return { url: data.link, fileName: data.file_name || "subtitle.srt" };
}

export async function GET(request: Request) {
  const apiKey = process.env.OPENSUBTITLES_API_KEY?.trim();
  if (!apiKey) return Response.json({ configured: false, error: "OpenSubtitles anahtarı yapılandırılmamış" }, { status: 503 });

  try {
    const input = new URL(request.url);
    const mode = input.searchParams.get("mode");

    if (mode === "download") {
      const fileId = input.searchParams.get("fileId")?.trim();
      if (!fileId) return Response.json({ configured: true, error: "fileId gerekli" }, { status: 400 });
      const result = await resolveDownloadLink(apiKey, fileId);
      return Response.json({ configured: true, ...result }, { headers: { "cache-control": "no-store" } });
    }

    const query = input.searchParams.get("query")?.trim();
    if (!query) return Response.json({ configured: true, error: "query gerekli" }, { status: 400 });
    const langs = input.searchParams.get("langs")?.trim() || "tr,en";
    const results = await searchSubtitles(apiKey, query, langs);
    return Response.json({ configured: true, results }, { headers: { "cache-control": "private, max-age=1800" } });
  } catch (error) {
    console.error("Subtitles GET failed", error);
    return Response.json({ configured: true, error: "Altyazı servisine şu anda ulaşılamıyor" }, { status: 502 });
  }
}
