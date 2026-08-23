// TV'deki SportsDataAggregator'ın web karşılığı. Futbol verisini mevcut
// /api/sports proxy'sinden (API-Football / football-data) alır ve üzerine
// TheSportsDB ücretsiz V1 (key=123) çok sporlu fallback'ini ekler; böylece
// API anahtarı olmadan da bugünün maçları (futbol, basketbol, tenis, motor)
// ve yayın kanalları gelir. Çıktı, TV SportsEvent modeliyle aynı şekildedir.

const TSDB_BASE = "https://www.thesportsdb.com/api/v1/json/123";

type SportsBroadcast = { id: string; eventId: string; country: string; channelName: string; channelLogo: string | null };
type SportsEvent = {
  id: string; sport: string; leagueId: string | null; league: string; leagueBadge: string | null;
  home: string; away: string; homeBadge: string | null; awayBadge: string | null; startMs: number;
  status: string | null; homeScore: string | null; awayScore: string | null; venue: string | null;
  season: string | null; round: string | null; artwork: string | null; broadcasts: SportsBroadcast[];
  country: string | null; liveMinute: number | null; apiFootballId: string | null; footballDataId: string | null;
  theSportsDbId: string | null; dataSources: string[];
};

// Kaba günlük önbellek (sıcak sunucu örneği başına). TheSportsDB free kotası dar.
const hubCache = new Map<string, { expiresAt: number; body: unknown }>();

function validDate(value: string | null) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value || "") ? value! : null;
}

function clean(value: unknown): string | null {
  const text = String(value ?? "").trim();
  return text && text !== "null" ? text : null;
}

function identity(value: string): string {
  return String(value || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^a-z0-9]+/g, " ").trim();
}

function eventIdentity(value: string): string {
  return String(value || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^a-z0-9]+/g, "");
}

function parseStartMs(date: string, time: string): number | null {
  const normalizedTime = (time || "00:00:00").slice(0, 8).padEnd(8, "0");
  const ms = Date.parse(`${date}T${normalizedTime}Z`);
  return Number.isFinite(ms) ? ms : null;
}

async function tsdbGet(path: string): Promise<any> {
  const response = await fetch(`${TSDB_BASE}${path}`, {
    headers: { accept: "application/json", "user-agent": "StreamLiveX/1.0" },
    signal: AbortSignal.timeout(18000),
  });
  if (!(response.status >= 200 && response.status < 300)) throw new Error(`TheSportsDB HTTP ${response.status}`);
  return response.json();
}

function parseBroadcasts(rows: any[] | null | undefined): SportsBroadcast[] {
  if (!Array.isArray(rows)) return [];
  return rows.map((row, index) => {
    const eventId = clean(row?.idEvent);
    const name = clean(row?.strChannel);
    if (!eventId || !name) return null;
    return { id: clean(row?.id) || `${eventId}-${index}`, eventId, country: clean(row?.strCountry) || "", channelName: name, channelLogo: clean(row?.strLogo) };
  }).filter((row): row is SportsBroadcast => Boolean(row));
}

const TSDB_SPORTS = ["Soccer", "Basketball", "Tennis", "Motorsport"];
const TSDB_ALLOWED = new Set(["soccer", "football", "basketball", "tennis", "motorsport", "formula 1"]);

async function theSportsDbEvents(date: string): Promise<SportsEvent[]> {
  const arrays = await Promise.all(TSDB_SPORTS.map(async sport => {
    try { return (await tsdbGet(`/eventsday.php?d=${date}&s=${sport}`))?.events as any[] | null; } catch { return null; }
  }));
  let broadcasts: Record<string, SportsBroadcast[]> = {};
  try {
    const tv = await tsdbGet(`/eventstv.php?d=${date}`);
    broadcasts = parseBroadcasts(tv?.tvevent).reduce<Record<string, SportsBroadcast[]>>((map, row) => {
      (map[row.eventId] ||= []).push(row); return map;
    }, {});
  } catch { broadcasts = {}; }

  const events: SportsEvent[] = [];
  for (const rows of arrays) {
    if (!Array.isArray(rows)) continue;
    for (const row of rows) {
      const sport = clean(row?.strSport);
      if (!sport || !TSDB_ALLOWED.has(sport.toLowerCase())) continue;
      const id = clean(row?.idEvent);
      if (!id) continue;
      const start = parseStartMs(clean(row?.dateEvent) || date, clean(row?.strTime) || "00:00:00");
      if (start == null) continue;
      events.push({
        id, sport, leagueId: clean(row?.idLeague), league: clean(row?.strLeague) || sport, leagueBadge: clean(row?.strLeagueBadge),
        home: clean(row?.strHomeTeam) || clean(row?.strEvent) || sport, away: clean(row?.strAwayTeam) || "",
        homeBadge: clean(row?.strHomeTeamBadge), awayBadge: clean(row?.strAwayTeamBadge), startMs: start,
        status: clean(row?.strStatus), homeScore: clean(row?.intHomeScore), awayScore: clean(row?.intAwayScore),
        venue: clean(row?.strVenue), season: clean(row?.strSeason), round: clean(row?.intRound),
        artwork: clean(row?.strThumb) || clean(row?.strBanner) || clean(row?.strPoster), broadcasts: broadcasts[id] || [],
        country: clean(row?.strCountry), liveMinute: null, apiFootballId: null, footballDataId: null, theSportsDbId: id,
        dataSources: ["THESPORTSDB"],
      });
    }
  }
  const seen = new Set<string>();
  return events.filter(e => (seen.has(e.id) ? false : (seen.add(e.id), true))).sort((a, b) => a.startMs - b.startMs);
}

async function apiFootballEvents(date: string, origin: string): Promise<SportsEvent[]> {
  // TV'deki ApiFootballProvider gibi mevcut /api/sports proxy'sini çağırır.
  try {
    const response = await fetch(`${origin}/api/sports?date=${date}`, { headers: { accept: "application/json" }, signal: AbortSignal.timeout(20000) });
    if (!(response.status >= 200 && response.status < 300)) return [];
    const root: any = await response.json();
    if (!root?.configured) return [];
    const rows: any[] = Array.isArray(root?.fixtures) ? root.fixtures : [];
    return rows.map(row => {
      const id = clean(row?.id);
      const home = clean(row?.home);
      const away = clean(row?.away);
      const timestamp = Number(row?.timestamp || 0);
      if (!id || !home || !away || !(timestamp > 0)) return null;
      const source = clean(row?.source) || "API_FOOTBALL";
      const prefixed = source === "FOOTBALL_DATA" ? `fd-${id}` : source === "TURKEY_FIXTURE_OPEN" ? `tr-${id}` : source === "FIXTURE_DOWNLOAD" ? `fx-${id}` : `af-${id}`;
      const liveMinute = Number(row?.liveMinute);
      return {
        id: prefixed, sport: "Soccer", leagueId: clean(row?.leagueId), league: clean(row?.league) || "Football", leagueBadge: clean(row?.leagueLogo),
        home, away, homeBadge: clean(row?.homeLogo), awayBadge: clean(row?.awayLogo), startMs: timestamp * 1000,
        status: clean(row?.status), homeScore: clean(row?.homeScore), awayScore: clean(row?.awayScore), venue: clean(row?.venue),
        season: clean(row?.season), round: clean(row?.round), artwork: null, broadcasts: [], country: clean(row?.country),
        liveMinute: Number.isFinite(liveMinute) && liveMinute >= 0 ? liveMinute : null,
        apiFootballId: source === "API_FOOTBALL" ? id : null, footballDataId: source === "FOOTBALL_DATA" ? id : null,
        theSportsDbId: null, dataSources: [source],
      } as SportsEvent;
    }).filter((row): row is SportsEvent => Boolean(row));
  } catch { return []; }
}

function isTurkishCompetition(event: SportsEvent): boolean {
  const value = `${event.country || ""} ${event.league}`.toLowerCase();
  return value.includes("turkey") || value.includes("türkiye") || value.includes("super lig") || value.includes("süper lig");
}

function competitionPriority(event: SportsEvent): number {
  const key = `${event.country || ""} ${event.league}`.toLowerCase();
  if (key.includes("turkey") || key.includes("türkiye") || key.includes("super lig") || key.includes("süper lig")) return 1;
  if (key.includes("premier league") && key.includes("england")) return 2;
  if (key.includes("champions league")) return 3;
  if (key.includes("la liga")) return 4;
  if (key.includes("bundesliga")) return 5;
  if (key.includes("serie a")) return 6;
  if (key.includes("ligue 1")) return 7;
  if (key.includes("europa league")) return 8;
  if (key.includes("conference league")) return 9;
  if (key.includes("primeira") || key.includes("liga portugal")) return 10;
  return 50;
}

function isFootball(event: SportsEvent): boolean {
  return ["soccer", "football"].includes(event.sport.toLowerCase());
}

function aggregate(football: SportsEvent[], fallback: SportsEvent[]): SportsEvent[] {
  const combined = football.length > 0
    ? [...football, ...fallback.filter(e => !isFootball(e) || isTurkishCompetition(e))]
    : fallback;
  const seen = new Set<string>();
  return combined
    .filter(e => {
      const key = `${Math.floor(e.startMs / 300_000)}|${eventIdentity(e.home)}|${eventIdentity(e.away)}`;
      return seen.has(key) ? false : (seen.add(key), true);
    })
    .sort((a, b) => competitionPriority(a) - competitionPriority(b) || a.startMs - b.startMs);
}

export async function GET(request: Request) {
  const input = new URL(request.url);
  const date = validDate(input.searchParams.get("date"));
  if (!date) return Response.json({ error: "Geçerli tarih gerekli" }, { status: 400 });

  const cacheKey = `hub:${date}`;
  const cached = hubCache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) {
    return Response.json(cached.body, { headers: { "cache-control": "public, max-age=1800, stale-while-revalidate=86400" } });
  }

  const [football, fallback] = await Promise.all([
    apiFootballEvents(date, input.origin),
    theSportsDbEvents(date).catch(() => [] as SportsEvent[]),
  ]);

  const events = aggregate(football, fallback);
  const body = {
    provider: football.length > 0 ? "API_FOOTBALL+THESPORTSDB" : "THESPORTSDB",
    footballCount: football.length,
    fallbackCount: fallback.length,
    events,
  };
  if (events.length > 0) hubCache.set(cacheKey, { expiresAt: Date.now() + 30 * 60 * 1000, body });
  return Response.json(body, { headers: { "cache-control": "public, max-age=1800, stale-while-revalidate=86400" } });
}
