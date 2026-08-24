// StreamLiveX TV — Xtream kütüphane import'u ve medya modeli (PlayerApp eşleştirmesiyle birebir).
import type { Provider } from "./Setup";

export type Kind = "live" | "movie" | "series";
export type Media = {
  id: string; name: string; logo?: string; group: string; kind: Kind; url: string;
  streamId?: string; seriesId?: string; epgId?: string; rating?: string; year?: string;
  container?: string;
};
export type Library = { live: Media[]; movies: Media[]; series: Media[] };

const norm = (s?: string) => (s || "").replace(/\/+$/, "");
const catMap = (rows: any[]) =>
  Object.fromEntries((rows || []).map((x: any) => [String(x.category_id), x.category_name]));

// Xtream hesabından tam kütüphaneyi çeker ve Media[]'e dönüştürür.
export async function importXtream(p: Provider): Promise<Library> {
  const res = await fetch("/api/import", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ method: "xtream", server: p.server, username: p.username, password: p.password }),
  });
  if (!res.ok) {
    const e = await res.json().catch(() => ({} as any));
    throw new Error(e.error || `İçe aktarma başarısız (${res.status})`);
  }
  const data = await res.json();
  const base = norm(p.server);
  const u = p.username || "", pw = p.password || "";
  const liveMap = catMap(data.liveCategories), vodMap = catMap(data.vodCategories), seriesMap = catMap(data.seriesCategories);

  const live: Media[] = (data.live || []).map((x: any) => ({
    id: `l${x.stream_id}`, streamId: String(x.stream_id), epgId: x.epg_channel_id || "",
    name: x.name || "İsimsiz Kanal", logo: x.stream_icon,
    group: liveMap[String(x.category_id)] || x.category_name || "Diğer Canlı Kanallar",
    kind: "live", url: `${base}/live/${u}/${pw}/${x.stream_id}.${x.container_extension || "ts"}`,
  }));
  const movies: Media[] = (data.vod || []).map((x: any) => ({
    id: `v${x.stream_id}`, streamId: String(x.stream_id), name: x.name || "İsimsiz Film", logo: x.stream_icon,
    group: vodMap[String(x.category_id)] || x.category_name || "Diğer Filmler",
    kind: "movie", url: `${base}/movie/${u}/${pw}/${x.stream_id}.${x.container_extension || "mp4"}`,
    rating: String(x.rating || ""), year: x.year, container: x.container_extension,
  }));
  const series: Media[] = (data.series || []).map((x: any) => ({
    id: `s${x.series_id}`, seriesId: String(x.series_id), name: x.name || "İsimsiz Dizi", logo: x.cover,
    group: seriesMap[String(x.category_id)] || x.category_name || "Diğer Diziler",
    kind: "series", url: "", // bölüm URL'si series_info ile talep anında kurulur
    rating: String(x.rating || ""), year: x.releaseDate ? String(x.releaseDate).slice(0, 4) : x.year,
  }));
  return { live, movies, series };
}

// Bir Kind için kategori listesi ("Tümü" en üstte; önce TR grupları alfabetik, sonra diğerleri alfabetik).
const isTR = (s: string) => /^\s*tr\b/i.test(s);
export function groupsOf(items: Media[]): string[] {
  const set = new Set<string>();
  for (const m of items) if (m.group) set.add(m.group);
  const rest = Array.from(set).sort((a, b) => {
    const ta = isTR(a), tb = isTR(b);
    if (ta !== tb) return ta ? -1 : 1;            // TR grupları en üstte
    return a.localeCompare(b, "tr", { sensitivity: "base", numeric: true }); // alfabetik (Türkçe)
  });
  return ["Tümü", ...rest];
}

// Bir dizinin ilk bölümünü series_info ile çözüp oynatılabilir Media döndürür.
export async function resolveSeriesFirstEpisode(p: Provider, m: Media): Promise<Media | null> {
  if (!p || p.method !== "xtream" || !m.seriesId) return null;
  try {
    const r = await fetch("/api/import", {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ method: "series_info", server: p.server, username: p.username, password: p.password, seriesId: m.seriesId }),
    });
    const data = await r.json();
    const seasons = Object.values(data.episodes || {}) as any[][];
    const first = (seasons.find((s) => Array.isArray(s) && s.length) || [])[0];
    if (!first) return null;
    const base = (p.server || "").replace(/\/+$/, "");
    return { ...m, url: `${base}/series/${p.username}/${p.password}/${first.id}.${first.container_extension || "mp4"}`, name: `${m.name} · ${first.title || "1. Bölüm"}` };
  } catch { return null; }
}

// Kategori adındaki baştaki/sondaki "|" ve boşluk gürültüsünü temizler (görüntü için).
export function cleanCat(s: string): string {
  return s.replace(/^[\s|]+/, "").replace(/[\s|]+$/, "").replace(/\s*\|\s*/g, " · ").trim() || s;
}
