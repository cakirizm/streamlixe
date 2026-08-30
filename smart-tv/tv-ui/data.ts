// StreamLiveX TV — veri katmanı (streamlivex.com /api uçları; fetch köprüsü main.tsx'te).

export type SportsEvent = {
  id: string; league: string; leagueBadge?: string | null;
  home: string; away: string; homeBadge?: string | null; awayBadge?: string | null;
  startMs: number; status: string; homeScore?: number | null; awayScore?: number | null;
  country?: string | null; broadcasts?: unknown[]; venue?: string | null;
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

export type EpgProgram = { title: string; description: string; start: number; stop: number };

function b64(s?: string): string {
  if (!s) return "";
  try { return decodeURIComponent(escape(atob(s))); } catch { try { return atob(s); } catch { return s; } }
}

// Xtream short_epg — bir kanalın program rehberi (şimdi/sonraki).
export async function fetchEpg(server?: string, username?: string, password?: string, streamId?: string): Promise<EpgProgram[]> {
  if (!server || !streamId) return [];
  try {
    const r = await fetch("/api/import", {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ method: "short_epg", server, username, password, streamId }),
    });
    if (!r.ok) return [];
    const data = await r.json();
    const rows = data.epg_listings || data.epg || [];
    return rows.map((x: any) => ({
      title: b64(x.title) || "Program",
      description: b64(x.description),
      start: Number(x.start_timestamp || 0) * 1000,
      stop: Number(x.stop_timestamp || 0) * 1000,
    })).filter((p: EpgProgram) => p.stop > Date.now() - 3600000).sort((a: EpgProgram, b: EpgProgram) => a.start - b.start);
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

/* ---------------- GenericDetailScreen / Home hero için TMDB katmanı ---------------- */

export type TmdbCard = {
  id: number; title: string; poster?: string; backdrop?: string; overview?: string;
  voteAverage?: number; releaseDate?: string; kind: "movie" | "series";
};

const TMDB_BACKDROP = "https://image.tmdb.org/t/p/w1280";

function toCard(x: any, kind: "movie" | "series"): TmdbCard {
  return {
    id: x.id, title: x.title || x.name || "",
    poster: x.poster_path ? `${TMDB_IMG}${x.poster_path}` : undefined,
    backdrop: x.backdrop_path ? `${TMDB_BACKDROP}${x.backdrop_path}` : undefined,
    overview: x.overview, voteAverage: x.vote_average,
    releaseDate: x.release_date || x.first_air_date, kind,
  };
}

export async function fetchTrending(kind: "movie" | "series"): Promise<TmdbCard[]> {
  try {
    const r = await fetch(`/api/tmdb?mode=trending&kind=${kind}`);
    if (!r.ok) return [];
    const data = await r.json();
    return (data.results || []).map((x: any) => toCard(x, kind));
  } catch { return []; }
}

export type LocalTmdbMatch = {
  playlistId: string; kind: "movie" | "series"; tmdbId: number; title: string;
  poster_path?: string; backdrop_path?: string; overview?: string; vote_average?: number;
  release_date?: string;
};

// Yerel kütüphane öğelerini (id/isim/tür) TMDB'ye toplu eşleştirir (native "yerelde eşleşen trend" mantığı).
export async function matchLocalToTmdb(items: { id: string; name: string; kind: "movie" | "series" }[]): Promise<LocalTmdbMatch[]> {
  if (!items.length) return [];
  try {
    const r = await fetch("/api/tmdb", {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ items }),
    });
    if (!r.ok) return [];
    const data = await r.json();
    return data.results || [];
  } catch { return []; }
}

export type TmdbPerson = { id: number; name: string; profilePath?: string };
export type TmdbDetail = {
  id: number; title: string; overview?: string; tagline?: string;
  backdrop?: string; poster?: string; voteAverage?: number; releaseDate?: string;
  runtime?: number; genres?: string[]; directors: TmdbPerson[]; cast: TmdbPerson[];
  recommendations: TmdbCard[]; trailerKey?: string;
};

const PROFILE_IMG = "https://image.tmdb.org/t/p/w185";

// query (başlık) veya id ile tam TMDB detayını (yönetmen/oyuncu/öneri/fragman dahil) çeker.
export async function fetchTmdbDetail(kind: "movie" | "series", query: string): Promise<TmdbDetail | null> {
  try {
    const r = await fetch(`/api/tmdb?kind=${kind}&query=${encodeURIComponent(query)}`);
    if (!r.ok) return null;
    const data = await r.json();
    const x = data.result;
    if (!x) return null;
    const trailer = (x.videos?.results || []).find((v: any) => v.site === "YouTube" && (v.type === "Trailer" || v.type === "Teaser"));
    return {
      id: x.id, title: x.title || x.name || "", overview: x.overview, tagline: x.tagline,
      backdrop: x.backdrop_path ? `${TMDB_BACKDROP}${x.backdrop_path}` : undefined,
      poster: x.poster_path ? `${TMDB_IMG}${x.poster_path}` : undefined,
      voteAverage: x.vote_average, releaseDate: x.release_date || x.first_air_date,
      runtime: x.runtime || x.episode_run_time?.[0],
      genres: (x.genres || []).map((g: any) => g.name),
      directors: (x.directors || []).map((p: any) => ({ id: p.id, name: p.name, profilePath: p.profile_path ? `${PROFILE_IMG}${p.profile_path}` : undefined })),
      cast: (x.cast || []).map((p: any) => ({ id: p.id, name: p.name, profilePath: p.profile_path ? `${PROFILE_IMG}${p.profile_path}` : undefined })),
      recommendations: (x.recommendations || []).map((c: any) => toCard(c, kind)),
      trailerKey: trailer?.key,
    };
  } catch { return null; }
}

export async function fetchPersonCredits(personId: number): Promise<TmdbCard[]> {
  try {
    const r = await fetch(`/api/tmdb?person=${personId}`);
    if (!r.ok) return [];
    const data = await r.json();
    return (data.results || []).map((x: any) => toCard(x, x.media_type === "tv" ? "series" : "movie"));
  } catch { return []; }
}
