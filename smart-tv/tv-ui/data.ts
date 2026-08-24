// StreamLiveX TV — veri katmanı (streamlivex.com /api uçları; fetch köprüsü main.tsx'te).

export type SportsEvent = {
  id: string; league: string; leagueBadge?: string | null;
  home: string; away: string; homeBadge?: string | null; awayBadge?: string | null;
  startMs: number; status: string; homeScore?: number | null; awayScore?: number | null;
  country?: string | null; broadcasts?: unknown[];
};

export type PosterCard = { id: string; title: string; poster: string; kind: "movie" | "series" };

const TMDB_IMG = "https://image.tmdb.org/t/p/w342";

export function todayISO(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export async function fetchSports(date = todayISO()): Promise<SportsEvent[]> {
  try {
    const r = await fetch(`/api/sports/hub?date=${date}`);
    if (!r.ok) return [];
    const data = await r.json();
    return Array.isArray(data.events) ? data.events : [];
  } catch { return []; }
}

export async function fetchForYou(kind: "movie" | "series" = "movie"): Promise<PosterCard[]> {
  // Popüler türlerden karışık öneri (native "Sizin İçin" mantığına yakın).
  const genres = kind === "movie" ? [28, 35, 18, 878, 27] : [18, 10765, 80, 35, 9648];
  const seen = new Set<string>();
  const out: PosterCard[] = [];
  for (const g of genres) {
    try {
      const r = await fetch(`/api/tmdb?mode=discover&kind=${kind}&genre=${g}`);
      if (!r.ok) continue;
      const data = await r.json();
      for (const x of data.results || []) {
        const poster = x.poster_path ? `${TMDB_IMG}${x.poster_path}` : "";
        const id = String(x.id);
        if (!poster || seen.has(id)) continue;
        seen.add(id);
        out.push({ id, title: x.title || x.name || "", poster, kind });
      }
    } catch { /* geç */ }
    if (out.length >= 20) break;
  }
  return out;
}

export type Genre = { id: number; name: string };

export async function fetchGenres(kind: "movie" | "series"): Promise<Genre[]> {
  try {
    const r = await fetch(`/api/tmdb?mode=genres&kind=${kind}`);
    if (!r.ok) return [];
    const data = await r.json();
    return (data.genres || []).map((g: any) => ({ id: g.id, name: g.name }));
  } catch { return []; }
}

export async function fetchDiscover(kind: "movie" | "series", genre: number): Promise<PosterCard[]> {
  try {
    const r = await fetch(`/api/tmdb?mode=discover&kind=${kind}&genre=${genre}`);
    if (!r.ok) return [];
    const data = await r.json();
    const out: PosterCard[] = [];
    for (const x of data.results || []) {
      const poster = x.poster_path ? `${TMDB_IMG}${x.poster_path}` : "";
      if (!poster) continue;
      out.push({ id: String(x.id), title: x.title || x.name || "", poster, kind });
    }
    return out;
  } catch { return []; }
}

export function countryFlag(country?: string | null): string {
  switch ((country || "").toLowerCase()) {
    case "turkey": case "türkiye": return "🇹🇷";
    case "england": return "🇬🇧";
    case "spain": return "🇪🇸";
    case "germany": return "🇩🇪";
    case "italy": return "🇮🇹";
    case "france": return "🇫🇷";
    case "portugal": return "🇵🇹";
    case "netherlands": return "🇳🇱";
    case "brazil": return "🇧🇷";
    default: return "🌍";
  }
}

export function hhmm(ms: number): string {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${p(d.getHours())}:${p(d.getMinutes())}`;
}
