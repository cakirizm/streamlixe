const API_FOOTBALL_ORIGIN = "https://v3.football.api-sports.io";
const FOOTBALL_DATA_ORIGIN = "https://api.football-data.org/v4";
const TURKEY_FIXTURE_FEED = "https://fixturedownload.com/feed/json/super-lig-2026";

const FOOTBALL_DATA_FALLBACKS: Record<string, { slug: string; country: string }> = {
  PL: { slug: "epl-2026", country: "England" },
  ELC: { slug: "championship-2026", country: "England" },
  PD: { slug: "la-liga-2026", country: "Spain" },
  BL1: { slug: "bundesliga-2026", country: "Germany" },
  SA: { slug: "serie-a-2026", country: "Italy" },
  FL1: { slug: "ligue-1-2026", country: "France" },
  PPL: { slug: "primeira-liga-2026", country: "Portugal" },
  DED: { slug: "eredivisie-2026", country: "Netherlands" },
};

type TargetLeague = { key: string; country?: string; names: string[] };
type ApiResult = { ok: boolean; status: number; data: any; errors: unknown; results: number; paging: unknown };
type DiscoveredLeague = TargetLeague & { leagueId: number; leagueName: string; season: number; seasonStart?: string; seasonEnd?: string; fixturesCovered: boolean };

export const TARGET_LEAGUES: TargetLeague[] = [
  { key: "turkey-super-lig", country: "Turkey", names: ["Süper Lig", "Super Lig"] },
  { key: "england-premier-league", country: "England", names: ["Premier League"] },
  { key: "spain-la-liga", country: "Spain", names: ["La Liga"] },
  { key: "germany-bundesliga", country: "Germany", names: ["Bundesliga"] },
  { key: "italy-serie-a", country: "Italy", names: ["Serie A"] },
  { key: "france-ligue-1", country: "France", names: ["Ligue 1"] },
  { key: "portugal-primeira-liga", country: "Portugal", names: ["Primeira Liga", "Liga Portugal"] },
  { key: "uefa-champions-league", names: ["UEFA Champions League", "Champions League"] },
  { key: "uefa-europa-league", names: ["UEFA Europa League", "Europa League"] },
  { key: "uefa-conference-league", names: ["UEFA Europa Conference League", "UEFA Conference League", "Conference League"] },
];

const discoveryCache = new Map<string, { expiresAt: number; value: DiscoveredLeague | null; debug: unknown }>();
const dailyCache = new Map<string, { expiresAt: number; body: unknown }>();
const footballDataCoverageCache = new Map<string, { expiresAt: number; body: any }>();
let nextApiFootballCallAt = 0;

async function respectProviderRateLimit() {
  const waitMs = Math.max(0, nextApiFootballCallAt - Date.now());
  if (waitMs > 0) await new Promise(resolve => setTimeout(resolve, waitMs));
  nextApiFootballCallAt = Date.now() + 1_500;
}

async function footballDataGet(path: string, key: string) {
  const response = await fetch(`${FOOTBALL_DATA_ORIGIN}${path}`, {
    headers: { accept: "application/json", "X-Auth-Token": key },
    signal: AbortSignal.timeout(15000),
  });
  let data: any = {};
  try { data = await response.json(); } catch { data = {}; }
  const error = response.ok ? null : { status: response.status, message: data?.message || "football-data.org request failed", errorCode: data?.errorCode };
  console.info("FootballData provider response", { path, status: response.status, count: data?.count ?? data?.matches?.length ?? data?.competitions?.length ?? null, error });
  return { ok: response.ok, status: response.status, data, error };
}

function footballDataFixture(row: any) {
  return {
    id: String(row.id || ""), source: "FOOTBALL_DATA", date: row.utcDate,
    timestamp: row.utcDate ? Math.floor(Date.parse(row.utcDate) / 1000) : 0, timezone: "UTC",
    status: row.status, statusLong: row.status, liveMinute: row.minute, venue: row.venue,
    leagueId: String(row.competition?.id || row.competition?.code || ""), competitionCode: row.competition?.code, league: row.competition?.name,
    leagueLogo: row.competition?.emblem, country: row.area?.name, season: row.season?.startDate ? Number(String(row.season.startDate).slice(0, 4)) : null,
    round: row.stage || (row.matchday ? `Matchday ${row.matchday}` : null), homeId: String(row.homeTeam?.id || ""),
    home: row.homeTeam?.name, homeLogo: row.homeTeam?.crest, awayId: String(row.awayTeam?.id || ""),
    away: row.awayTeam?.name, awayLogo: row.awayTeam?.crest,
    homeScore: row.score?.fullTime?.home, awayScore: row.score?.fullTime?.away,
  };
}

async function openFixtureFallback(date: string, competition: any, config: { slug: string; country: string }) {
  try {
    const response = await fetch(`https://fixturedownload.com/feed/json/${config.slug}`, {
      headers: { accept: "application/json", "user-agent": "StreamLiveX/1.0" }, signal: AbortSignal.timeout(15000),
    });
    if (!response.ok) return { fixtures: [], error: { status: response.status } };
    const rows: any[] = await response.json();
    const selected = Array.isArray(rows) ? rows.filter(row => String(row.DateUtc || "").slice(0, 10) === date) : [];
    return { fixtures: selected.map(row => {
      const isoDate = String(row.DateUtc || "").replace(" ", "T");
      return {
        id: `${competition.code}-${row.MatchNumber || `${row.HomeTeam}-${row.AwayTeam}`}`, source: "FIXTURE_DOWNLOAD", date: isoDate,
        timestamp: Math.floor(Date.parse(isoDate) / 1000), timezone: "UTC", status: row.HomeTeamScore == null ? "SCHEDULED" : "FINISHED",
        statusLong: row.HomeTeamScore == null ? "SCHEDULED" : "FINISHED", liveMinute: null, venue: row.Location,
        leagueId: String(competition.id || competition.code), competitionCode: competition.code, league: competition.name,
        leagueLogo: competition.emblem, country: competition.area?.name || config.country,
        season: competition.currentSeason?.startDate ? Number(String(competition.currentSeason.startDate).slice(0, 4)) : 2026,
        round: row.RoundNumber ? `Matchday ${row.RoundNumber}` : null, homeId: "", home: row.HomeTeam, homeLogo: null,
        awayId: "", away: row.AwayTeam, awayLogo: null, homeScore: row.HomeTeamScore, awayScore: row.AwayTeamScore,
      };
    }), error: null };
  } catch (error) { return { fixtures: [], error: String(error) }; }
}

async function footballDataSnapshot(date: string, key: string) {
  const coverageKey = "football-data-coverage";
  let coverage = footballDataCoverageCache.get(coverageKey)?.body;
  let coverageNetwork = false;
  if (!coverage || (footballDataCoverageCache.get(coverageKey)?.expiresAt || 0) <= Date.now()) {
    const result = await footballDataGet("/competitions", key);
    if (!result.ok) return { ok: false, error: result.error, fixtures: [], diagnostics: [], coverageNetwork, fixtureNetwork: false };
    coverage = result.data;
    coverageNetwork = true;
    footballDataCoverageCache.set(coverageKey, { expiresAt: Date.now() + 14 * 24 * 60 * 60 * 1000, body: coverage });
  }
  const competitions: any[] = Array.isArray(coverage.competitions) ? coverage.competitions : [];
  if (competitions.length === 0) return { ok: true, fixtures: [], diagnostics: [], accessibleCompetitionCount: 0, coverageNetwork, fixtureNetwork: false };
  // The top-level Match resource supports a date range and returns matches from every
  // competition accessible to this token, so this deliberately avoids a hardcoded code filter.
  const matchesResult = await footballDataGet(`/matches?dateFrom=${date}&dateTo=${date}`, key);
  if (!matchesResult.ok) return { ok: false, error: matchesResult.error, fixtures: [], diagnostics: [], accessibleCompetitionCount: competitions.length, coverageNetwork, fixtureNetwork: true };
  const primaryRows: any[] = Array.isArray(matchesResult.data.matches) ? matchesResult.data.matches : [];
  const merged = primaryRows.map(footballDataFixture);
  const diagnostics = [];
  for (const competition of competitions) {
    const primaryCount = primaryRows.filter(match => match.competition?.code === competition.code).length;
    const fallbackConfig = FOOTBALL_DATA_FALLBACKS[competition.code];
    let fallback = { fixtures: [] as any[], error: null as any };
    if (primaryCount === 0 && fallbackConfig) fallback = await openFixtureFallback(date, competition, fallbackConfig);
    if (fallback.fixtures.length) merged.push(...fallback.fixtures);
    diagnostics.push({
      competition: competition.name, country: competition.area?.name, code: competition.code, leagueId: competition.id,
      provider: fallback.fixtures.length ? "FixtureDownload fallback" : "football-data.org",
      currentSeason: competition.currentSeason?.startDate ? Number(String(competition.currentSeason.startDate).slice(0, 4)) : null,
      todayResults: primaryCount + fallback.fixtures.length,
      status: primaryCount > 0 ? "AVAILABLE" : fallback.fixtures.length ? "PROVIDER_DATA_INCOMPLETE" : fallback.error ? "FALLBACK_ERROR" : "NO_FIXTURES_TODAY",
      footballDataResults: primaryCount, fallbackResults: fallback.fixtures.length, errors: fallback.error,
    });
  }
  const seen = new Set<string>();
  const fixtures = merged.filter((row: any) => {
    const identity = `${Math.floor(Number(row.timestamp || 0) / 300)}|${normalized(row.home)}|${normalized(row.away)}`;
    if (seen.has(identity)) return false; seen.add(identity); return true;
  });
  return {
    ok: true, fixtures, diagnostics, accessibleCompetitionCount: competitions.length, coverageNetwork, fixtureNetwork: true,
    raw: { resultSet: matchesResult.data.resultSet || null, matchCount: primaryRows.length },
  };
}

async function turkeyFixtureSnapshot(date: string) {
  try {
    const response = await fetch(TURKEY_FIXTURE_FEED, {
      headers: { accept: "application/json", "user-agent": "StreamLiveX/1.0" },
      signal: AbortSignal.timeout(15000),
    });
    if (!response.ok) return { fixtures: [], diagnostic: { competition: "Süper Lig", code: "TR1", provider: "FixtureDownload", currentSeason: 2026, todayResults: null, status: "API_ERROR", errors: { status: response.status } } };
    const rows: any[] = await response.json();
    const selected = Array.isArray(rows) ? rows.filter(row => String(row.DateUtc || "").slice(0, 10) === date) : [];
    const fixtures = selected.map(row => {
      const isoDate = String(row.DateUtc || "").replace(" ", "T");
      return {
        id: String(row.MatchNumber || `${row.HomeTeam}-${row.AwayTeam}`), source: "TURKEY_FIXTURE_OPEN", date: isoDate,
        timestamp: Math.floor(Date.parse(isoDate) / 1000), timezone: "UTC", status: row.HomeTeamScore == null ? "SCHEDULED" : "FINISHED",
        statusLong: row.HomeTeamScore == null ? "SCHEDULED" : "FINISHED", liveMinute: null, venue: row.Location,
        leagueId: "TR1", competitionCode: "TR1", league: "Süper Lig", leagueLogo: null, country: "Turkey", season: 2026,
        round: row.RoundNumber ? `Matchday ${row.RoundNumber}` : null, homeId: "", home: row.HomeTeam, homeLogo: null,
        awayId: "", away: row.AwayTeam, awayLogo: null, homeScore: row.HomeTeamScore, awayScore: row.AwayTeamScore,
      };
    });
    return { fixtures, diagnostic: { competition: "Süper Lig", code: "TR1", provider: "FixtureDownload", currentSeason: 2026, todayResults: fixtures.length, status: fixtures.length ? "AVAILABLE" : "NO_FIXTURES_TODAY", errors: null } };
  } catch (error) {
    return { fixtures: [], diagnostic: { competition: "Süper Lig", code: "TR1", provider: "FixtureDownload", currentSeason: 2026, todayResults: null, status: "API_ERROR", errors: String(error) } };
  }
}

function validDate(value: string | null) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value || "") ? value! : null;
}

function normalized(value: unknown) {
  return String(value || "").toLocaleLowerCase("tr").normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .replace(/ı/g, "i").replace(/[^a-z0-9]+/g, " ").trim();
}

async function apiFootball(path: string, key: string): Promise<ApiResult> {
  await respectProviderRateLimit();
  const response = await fetch(`${API_FOOTBALL_ORIGIN}${path}`, {
    headers: { accept: "application/json", "x-apisports-key": key },
    signal: AbortSignal.timeout(15000),
  });
  let data: any = {};
  try { data = await response.json(); } catch { data = {}; }
  const errors = data?.errors || {};
  const result = {
    ok: response.ok && Object.keys(errors).length === 0,
    status: response.status,
    data,
    errors,
    results: Number(data?.results || 0),
    paging: data?.paging || null,
  };
  console.info("Sports provider response", { path, status: result.status, errors: result.errors, results: result.results, paging: result.paging, responseCount: Array.isArray(data?.response) ? data.response.length : 0 });
  return result;
}

function currentSeason(row: any) {
  return (row?.seasons || []).find((season: any) => season.current === true);
}

async function discover(target: TargetLeague, key: string): Promise<{ league: DiscoveredLeague | null; debug: unknown }> {
  const cached = discoveryCache.get(target.key);
  if (cached && cached.expiresAt > Date.now()) return { league: cached.value, debug: cached.debug };
  const query = target.country
    ? `/leagues?country=${encodeURIComponent(target.country)}&current=true`
    : `/leagues?search=${encodeURIComponent(target.names[0])}`;
  const result = await apiFootball(query, key);
  const candidates = Array.isArray(result.data?.response) ? result.data.response : [];
  const wanted = target.names.map(normalized);
  const row = candidates.find((candidate: any) => wanted.includes(normalized(candidate.league?.name)))
    || candidates.find((candidate: any) => wanted.some(name => normalized(candidate.league?.name).includes(name)));
  const season = currentSeason(row);
  const league = row?.league?.id && season?.year ? {
    ...target,
    leagueId: Number(row.league.id),
    leagueName: String(row.league.name),
    season: Number(season.year),
    seasonStart: season.start,
    seasonEnd: season.end,
    fixturesCovered: Boolean(season.coverage?.fixtures?.events !== false),
  } : null;
  const debug = { query, status: result.status, errors: result.errors, results: result.results, paging: result.paging, found: league };
  if (result.ok) discoveryCache.set(target.key, { expiresAt: Date.now() + 14 * 24 * 60 * 60 * 1000, value: league, debug });
  return { league, debug };
}

function fixtureRow(row: any, source = "API-Football") {
  return {
    id: String(row.fixture?.id || ""), source, date: row.fixture?.date, timestamp: row.fixture?.timestamp,
    timezone: row.fixture?.timezone || "UTC", status: row.fixture?.status?.short, statusLong: row.fixture?.status?.long,
    liveMinute: row.fixture?.status?.elapsed, venue: row.fixture?.venue?.name, leagueId: String(row.league?.id || ""),
    league: row.league?.name, leagueLogo: row.league?.logo, country: row.league?.country, season: row.league?.season,
    round: row.league?.round, homeId: String(row.teams?.home?.id || ""), home: row.teams?.home?.name,
    homeLogo: row.teams?.home?.logo, awayId: String(row.teams?.away?.id || ""), away: row.teams?.away?.name,
    awayLogo: row.teams?.away?.logo, homeScore: row.goals?.home, awayScore: row.goals?.away,
  };
}

function classify(result: ApiResult) {
  if (!result.ok) {
    const text = JSON.stringify(result.errors).toLowerCase();
    if ((text.includes("plan") || text.includes("season")) && !text.includes("rate")) return "SEASON_NOT_AVAILABLE_ON_FREE_PLAN";
    return "API_ERROR";
  }
  return result.results > 0 ? "AVAILABLE" : "NO_FIXTURES_TODAY";
}

async function targetFixtures(league: DiscoveredLeague, date: string, key: string, includeNext: boolean) {
  const today = await apiFootball(`/fixtures?league=${league.leagueId}&season=${league.season}&date=${date}&timezone=UTC`, key);
  const next = includeNext ? await apiFootball(`/fixtures?league=${league.leagueId}&season=${league.season}&next=10&timezone=UTC`, key) : null;
  return {
    fixtures: (Array.isArray(today.data?.response) ? today.data.response : []).map((row: any) => fixtureRow(row)),
    diagnostic: {
      competition: league.leagueName, provider: "API-Football", leagueId: league.leagueId, currentSeason: league.season,
      seasonStart: league.seasonStart, seasonEnd: league.seasonEnd, fixturesCovered: league.fixturesCovered,
      todayResults: today.results, nextFixtures: next?.results ?? null, status: classify(today), errors: today.errors,
      todayPaging: today.paging, nextErrors: next?.errors || null,
    },
  };
}

export async function GET(request: Request) {
  const input = new URL(request.url);
  const date = validDate(input.searchParams.get("date"));
  const debugMode = input.searchParams.get("debug") === "1";
  const targetKey = input.searchParams.get("target")?.trim() || null;
  const statusMode = input.searchParams.get("status") === "1";
  if (!date) return Response.json({ error: "Geçerli tarih gerekli" }, { status: 400 });
  const key = process.env.API_FOOTBALL_KEY?.trim();
  const footballDataKey = process.env.FOOTBALL_DATA_KEY?.trim();
  const footballDataTarget = targetKey ? TARGET_LEAGUES.find(target => target.key === targetKey) : null;

  if (statusMode) {
    const footballDataStatus = footballDataKey ? await footballDataGet("/competitions", footballDataKey) : null;
    return Response.json({
      footballDataConfigured: Boolean(footballDataKey),
      footballDataStatus: !footballDataKey ? "NOT_CONFIGURED" : footballDataStatus?.ok ? "AVAILABLE" : "API_ERROR",
      footballDataHttpStatus: footballDataStatus?.status ?? null,
      footballDataError: footballDataStatus?.error ?? null,
      apiFootballConfigured: Boolean(key),
    }, { headers: { "cache-control": "no-store" } });
  }

  if (footballDataKey && (!targetKey || footballDataTarget)) {
    const cacheKey = `football-data:${date}`;
    const cached = dailyCache.get(cacheKey);
    if (!debugMode && cached && cached.expiresAt > Date.now()) return Response.json(cached.body, { headers: { "cache-control": "public, max-age=3600" } });
    const snapshot = await footballDataSnapshot(date, footballDataKey);
    if (snapshot.ok) {
      const turkey = !targetKey ? await turkeyFixtureSnapshot(date) : null;
      const filteredDiagnostics = snapshot.diagnostics;
      const filteredFixtures = snapshot.fixtures;
      const body = {
        configured: true, footballDataConfigured: true, provider: "football-data.org",
        fixtures: turkey ? [...turkey.fixtures, ...filteredFixtures] : filteredFixtures,
        diagnostics: turkey ? [turkey.diagnostic, ...filteredDiagnostics] : filteredDiagnostics,
        accessibleCompetitionCount: snapshot.accessibleCompetitionCount,
        raw: debugMode ? snapshot.raw : undefined,
        cache: { coverageNetwork: snapshot.coverageNetwork, fixtureNetwork: snapshot.fixtureNetwork },
      };
      if (!debugMode && !targetKey) dailyCache.set(cacheKey, { expiresAt: Date.now() + 6 * 60 * 60 * 1000, body });
      return Response.json(body, { headers: { "cache-control": debugMode ? "no-store" : "public, max-age=3600, stale-while-revalidate=86400" } });
    }
    console.error("FootballData primary failed; API-Football fallback may be used", snapshot.error);
  }

  if (!key) return Response.json({
    configured: false, footballDataConfigured: Boolean(footballDataKey), provider: "API-Football", fixtures: [],
    diagnostics: TARGET_LEAGUES.map(target => ({ competition: target.names[0], provider: "API-Football", status: "API_ERROR", errors: { configuration: "API_FOOTBALL_KEY yapılandırılmamış" } })),
  }, { headers: { "cache-control": "no-store" } });

  const cacheKey = `targets:${date}`;
  const cached = dailyCache.get(cacheKey);
  if (!debugMode && cached && cached.expiresAt > Date.now()) return Response.json(cached.body, { headers: { "cache-control": "public, max-age=3600" } });
  try {
    const selectedTargets = targetKey ? TARGET_LEAGUES.filter(target => target.key === targetKey) : TARGET_LEAGUES;
    if (selectedTargets.length === 0) return Response.json({ configured: true, error: "Bilinmeyen hedef lig" }, { status: 400 });
    const discoveries = [];
    for (const target of selectedTargets) discoveries.push({ target, ...(await discover(target, key)) });
    const fixtureGroups = [];
    for (const item of discoveries) {
      if (!item.league) {
        fixtureGroups.push({ fixtures: [], diagnostic: { competition: item.target.names[0], provider: "API-Football", status: "API_ERROR", errors: item.debug } });
        continue;
      }
      fixtureGroups.push(await targetFixtures(item.league, date, key, debugMode));
    }
    const seen = new Set<string>();
    const fixtures = fixtureGroups.flatMap(group => group.fixtures).filter((row: any) => {
      const identity = `${String(row.date || "").slice(0, 10)}|${normalized(row.home)}|${normalized(row.away)}`;
      if (seen.has(identity)) return false;
      seen.add(identity); return true;
    });
    const body = { configured: true, provider: "API-Football", fixtures, diagnostics: fixtureGroups.map(group => group.diagnostic), discovery: debugMode ? discoveries.map(item => item.debug) : undefined };
    if (!debugMode) dailyCache.set(cacheKey, { expiresAt: Date.now() + 6 * 60 * 60 * 1000, body });
    return Response.json(body, { headers: { "cache-control": debugMode ? "no-store" : "public, max-age=3600, stale-while-revalidate=86400" } });
  } catch (error) {
    console.error("Target league fetch failed", error);
    return Response.json({ configured: true, provider: "API-Football", fixtures: [], diagnostics: [{ status: "API_ERROR", errors: String(error) }] }, { status: 502 });
  }
}
