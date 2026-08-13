const OFFICIAL_TMDB_ORIGIN = "https://api.themoviedb.org";

type TmdbParams = Record<string, string | number | undefined>;

function getRequestLocale(request: Request) {
  const supported = new Set(["tr-TR", "en-US", "ar-SA", "de-DE", "fr-FR", "es-ES"]);
  const cookie = request.headers.get("cookie")?.match(/(?:^|;\s*)slx-language=([^;]+)/)?.[1];
  const query = new URL(request.url).searchParams.get("lang");
  const value = decodeURIComponent(query || cookie || "tr-TR");
  return supported.has(value) ? value : "tr-TR";
}

function tmdbOrigins() {
  const configured = process.env.TMDB_API_BASE_URL?.trim().replace(/\/$/, "");
  return Array.from(new Set([configured, OFFICIAL_TMDB_ORIGIN].filter(Boolean))) as string[];
}

async function tmdbGet(path: string, locale: string, token: string, params: TmdbParams = {}) {
  const isV3 = /^[a-f0-9]{32}$/i.test(token);
  const headers: Record<string, string> = { accept: "application/json" };
  if (!isV3) headers.authorization = `Bearer ${token}`;

  let lastError: unknown;
  for (const origin of tmdbOrigins()) {
    try {
      const url = new URL(`${origin}/3/${path.replace(/^\//, "")}`);
      url.searchParams.set("language", locale);
      if (isV3) url.searchParams.set("api_key", token);
      for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== "") url.searchParams.set(key, String(value));
      }
      const response = await fetch(url, { headers, signal: AbortSignal.timeout(12000) });
      if (!response.ok) {
        lastError = new Error(`TMDB ${response.status}`);
        continue;
      }
      return response.json();
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError instanceof Error ? lastError : new Error("TMDB servisine ulaşılamadı");
}

const cleanPlaylistTitle = (value: string) =>
  value
    .replace(/\[[^\]]*]|\([^)]*(?:19|20)\d{2}[^)]*\)|\b(?:19|20)\d{2}\b|\b(?:4K|UHD|FHD|HD|SD|TR|DUBLAJ|ALTYAZILI|MULTI)\b/gi, " ")
    .replace(/[._|]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();

export async function GET(request: Request) {
  const token = process.env.TMDB_TOKEN?.trim();
  if (!token) return Response.json({ configured: false, error: "TMDB anahtarı yapılandırılmamış" }, { status: 503 });

  try {
    const input = new URL(request.url);
    const locale = getRequestLocale(request);
    const query = input.searchParams.get("query");
    const mode = input.searchParams.get("mode");
    const person = input.searchParams.get("person");
    const id = input.searchParams.get("id");
    const kind = input.searchParams.get("kind") === "series" || input.searchParams.get("kind") === "tv" ? "tv" : "movie";

    if (mode === "genres") {
      const data = await tmdbGet(`genre/${kind}/list`, locale, token);
      return Response.json({ configured: true, genres: data.genres || [] }, { headers: { "cache-control": "public, max-age=86400" } });
    }

    if (mode === "season") {
      let tvId = Number(id) || 0;
      const season = Math.max(0, Number(input.searchParams.get("season") || 1));
      if (!tvId && query) {
        const search = await tmdbGet("search/tv", locale, token, { query });
        tvId = Number(search.results?.[0]?.id || 0);
      }
      if (!tvId) return Response.json({ configured: true, episodes: [] });
      const data = await tmdbGet(`tv/${tvId}/season/${season}`, locale, token);
      return Response.json(
        {
          configured: true,
          episodes: (data.episodes || []).map((item: any) => ({
            id: item.id,
            episode_number: item.episode_number,
            name: item.name,
            overview: item.overview,
            still_path: item.still_path,
            air_date: item.air_date,
            runtime: item.runtime,
            vote_average: item.vote_average,
          })),
        },
        { headers: { "cache-control": "public, max-age=86400" } },
      );
    }

    if (mode === "discover") {
      const data = await tmdbGet(`discover/${kind}`, locale, token, {
        with_genres: input.searchParams.get("genre") || undefined,
        sort_by: "popularity.desc",
        include_adult: "false",
      });
      return Response.json({ configured: true, results: (data.results || []).slice(0, 12) }, { headers: { "cache-control": "public, max-age=1800" } });
    }

    if (mode === "trending") {
      const pages = await Promise.all(
        [1, 2, 3].map((page) => tmdbGet(`trending/${kind}/week`, locale, token, { page })),
      );
      const results = pages
        .flatMap((page) => page.results || [])
        .filter((item: any, index: number, all: any[]) => all.findIndex((candidate) => candidate.id === item.id) === index)
        .slice(0, 60);
      return Response.json({ configured: true, results }, { headers: { "cache-control": "public, max-age=21600" } });
    }

    if (person) {
      const data = await tmdbGet(`person/${person}/combined_credits`, locale, token);
      const results = (data.cast || [])
        .filter((item: any) => item.media_type === "movie" || item.media_type === "tv")
        .sort((a: any, b: any) => (b.popularity || 0) - (a.popularity || 0))
        .filter((item: any, index: number, all: any[]) => all.findIndex((candidate) => candidate.id === item.id && candidate.media_type === item.media_type) === index)
        .slice(0, 18);
      return Response.json({ configured: true, results });
    }

    if (query || id) {
      let found: any = id ? { id: Number(id) } : null;
      if (!found && query) {
        const search = await tmdbGet(`search/${kind}`, locale, token, { query: cleanPlaylistTitle(query) || query });
        found = search.results?.[0];
      }
      if (!found?.id) return Response.json({ configured: true, result: null });
      const data = await tmdbGet(`${kind}/${found.id}`, locale, token, { append_to_response: "credits,recommendations,similar" });
      if (locale !== "en-US" && !String(data.overview || "").trim()) {
        try {
          const fallback = await tmdbGet(`${kind}/${found.id}`, "en-US", token);
          data.overview = fallback.overview || data.overview;
          data.tagline = fallback.tagline || data.tagline;
        } catch {
          // Ana dildeki bilgiler kullanılabilir durumda; İngilizce açıklama yedeği zorunlu değil.
        }
      }
      const creatorRows = kind === "tv" ? data.created_by || [] : [];
      const directors = [
        ...creatorRows,
        ...(data.credits?.crew || []).filter((item: any) => item.job === "Director" || item.job === "Creator" || item.job === "Executive Producer" || item.department === "Directing"),
      ]
        .filter((item: any, index: number, all: any[]) => all.findIndex((candidate) => candidate.id === item.id) === index)
        .slice(0, 5)
        .map((item: any) => ({ id: item.id, name: item.name, profile_path: item.profile_path }));
      const cast = (data.credits?.cast || []).slice(0, 10).map((item: any) => ({
        id: item.id,
        name: item.name,
        character: item.character,
        profile_path: item.profile_path,
      }));
      const recommendations = [...(data.recommendations?.results || []), ...(data.similar?.results || [])]
        .filter((item: any, index: number, all: any[]) => all.findIndex((candidate) => candidate.id === item.id) === index)
        .slice(0, 12);
      const overview = String(data.overview || "").trim();
      return Response.json({
        configured: true,
        result: {
          ...data,
          overview: overview || "Bu içerik için TMDB üzerinde açıklama bulunamadı.",
          media_type: kind,
          directors,
          cast,
          recommendations,
        },
      }, { headers: { "cache-control": "no-store" } });
    }

    const data = await tmdbGet("trending/all/week", locale, token);
    return Response.json({ configured: true, results: (data.results || []).slice(0, 20) }, { headers: { "cache-control": "public, max-age=1800" } });
  } catch (error) {
    console.error("TMDB GET failed", error);
    return Response.json({ configured: true, error: "TMDB servisine şu anda ulaşılamıyor" }, { status: 502 });
  }
}

export async function POST(request: Request) {
  const token = process.env.TMDB_TOKEN?.trim();
  if (!token) return Response.json({ configured: false, error: "TMDB anahtarı yapılandırılmamış" }, { status: 503 });

  try {
    const locale = getRequestLocale(request);
    const body = (await request.json()) as { items?: Array<{ id: string; name: string; kind: "movie" | "series" }> };
    const items = (body.items || []).slice(0, 72);
    const search = async (item: { id: string; name: string; kind: "movie" | "series" }) => {
      const mediaKind = item.kind === "series" ? "tv" : "movie";
      const data = await tmdbGet(`search/${mediaKind}`, locale, token, { query: cleanPlaylistTitle(item.name) || item.name });
      const found = data.results?.[0];
      return found
        ? {
            playlistId: item.id,
            kind: item.kind,
            tmdbId: found.id,
            title: found.title || found.name,
            poster_path: found.poster_path,
            backdrop_path: found.backdrop_path,
            overview: found.overview,
            vote_average: found.vote_average,
            release_date: found.release_date || found.first_air_date,
            genre_ids: found.genre_ids || [],
            popularity: found.popularity || 0,
            original_language: found.original_language || "",
            origin_country: found.origin_country || [],
          }
        : null;
    };

    const results: any[] = [];
    for (let index = 0; index < items.length; index += 8) {
      const page = await Promise.all(items.slice(index, index + 8).map(search));
      results.push(...page.filter(Boolean));
    }
    return Response.json({ configured: true, results }, { headers: { "cache-control": "private, max-age=3600" } });
  } catch (error) {
    console.error("TMDB POST failed", error);
    return Response.json({ configured: true, error: "TMDB eşleştirmesi tamamlanamadı" }, { status: 502 });
  }
}
