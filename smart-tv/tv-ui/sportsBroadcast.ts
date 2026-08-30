// StreamLiveX TV — spor yayın kanalı eşleştirme (native SportsBroadcasting.kt/SportsChannelIndex birebir).
// event.broadcasts doluysa EXACT (gerçek veri), boşsa lig-bazlı Türkiye yayın ağı fallback listesi kullanılır;
// her iki durumda da kanonik kanal adı kullanıcının kendi Live TV kütüphanesindeki gerçek kanal(lar)la
// native'deki gibi token-bazlı (sayı birebir + kelime alt-küme) eşleştirilir.
import type { Media } from "./library";
import type { SportsEvent } from "./data";

// native SportsCanonicalChannels.names — birebir, fazla kanal EKLENMEDİ.
const CANONICAL_NAMES = [
  "beIN Sports 1", "beIN Sports 2", "beIN Sports 3", "beIN Sports 4", "beIN Sports 5",
  "S Sport", "S Sport 2", "Tivibu Spor 1", "Tivibu Spor 2", "Tivibu Spor 3", "TRT 1", "TRT Spor",
];

const NOISE = new Set(["tr", "turkiye", "turkey", "vip", "backup", "source", "server", "kanal", "channel", "tv"]);
const QUALITY_TOKEN = /^(4k|uhd|fhd|hd|sd|hevc|h265|h264|1080p?|2160p?|720p?)$/;

function normalizeText(value: string): string {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function tokens(value: string): string[] {
  return normalizeText(value).split(/\s+/).filter((t) => t && !NOISE.has(t) && !QUALITY_TOKEN.test(t));
}

const canonicalTokenCache = new Map<string, string[]>();
function canonicalTokensOf(name: string): string[] {
  let t = canonicalTokenCache.get(name);
  if (!t) { t = tokens(name); canonicalTokenCache.set(name, t); }
  return t;
}

// native SportsCanonicalChannels.canonicalFor — sayı token'ları birebir eşleşmeli, kelime token'ları alt-küme olmalı.
export function canonicalFor(channelName: string): string | null {
  const actual = tokens(channelName);
  if (actual.length === 0) return null;
  const actualNumbers = actual.filter((t) => /^\d+$/.test(t));
  const actualWords = actual.filter((t) => !/^\d+$/.test(t));
  for (const canonical of CANONICAL_NAMES) {
    const expected = canonicalTokensOf(canonical);
    const expectedNumbers = expected.filter((t) => /^\d+$/.test(t));
    const expectedWords = expected.filter((t) => !/^\d+$/.test(t));
    if (expectedNumbers.join(",") !== actualNumbers.join(",")) continue;
    if (expectedWords.every((w) => actualWords.includes(w))) return canonical;
  }
  return null;
}

// native SportsBroadcastResolver.isBigTurkeyTeam
function isBigTurkeyTeam(value: string): boolean {
  const t = normalizeText(value);
  return ["galatasaray", "gs", "fenerbahce", "fb", "besiktas", "bjk", "trabzonspor", "ts"].includes(t);
}

// native SportsBroadcastResolver.fallbackNames — lig/spor bazlı, birebir.
function fallbackNames(event: SportsEvent): string[] {
  const league = normalizeText(event.league || "");
  const sport = normalizeText((event as unknown as { sport?: string }).sport || "");
  if (sport.includes("formula 1") || sport.includes("motorsport") || league.includes("formula 1") || league === "f1") {
    return ["beIN Sports 4"];
  }
  if (league.includes("super lig")) {
    return isBigTurkeyTeam(event.home) || isBigTurkeyTeam(event.away)
      ? ["beIN Sports 1", "beIN Sports 2"]
      : ["beIN Sports 2", "beIN Sports 3", "beIN Sports 4"];
  }
  if (league.includes("premier league")) return ["beIN Sports 3", "beIN Sports 4", "beIN Sports 5"];
  if (league.includes("ligue 1")) return ["beIN Sports 1", "beIN Sports 2", "beIN Sports 3", "beIN Sports 4", "beIN Sports 5"];
  if (league.includes("la liga") || league.includes("serie a") || league.includes("bundesliga")) return ["S Sport", "S Sport 2"];
  if (league.includes("primeira") || league.includes("liga portugal") || league.includes("eredivisie")) {
    return ["Tivibu Spor 1", "Tivibu Spor 2", "Tivibu Spor 3"];
  }
  if (league.includes("champions league") || league.includes("europa league") || league.includes("conference league")) {
    return ["TRT 1", "TRT Spor"];
  }
  return [];
}

function extractName(entry: unknown): string | null {
  if (typeof entry === "string") return entry;
  if (entry && typeof entry === "object") {
    const o = entry as Record<string, unknown>;
    const v = o.name ?? o.channel ?? o.network ?? o.title;
    if (typeof v === "string") return v;
  }
  return null;
}

export type BroadcastGroup = { canonicalName: string; channels: Media[] };
export type BroadcastResolution = { evidence: "EXACT" | "FALLBACK" | "NONE"; groups: BroadcastGroup[] };

function matchChannels(canonicalName: string, libraryLive: Media[]): Media[] {
  return libraryLive.filter((ch) => canonicalFor(ch.name) === canonicalName);
}

// native SportsBroadcastResolver.options() + SportsChannelIndex.resolve()
export function resolveBroadcastGroups(event: SportsEvent, libraryLive: Media[]): BroadcastResolution {
  const exactNames = (event.broadcasts || [])
    .map(extractName)
    .filter((n): n is string => !!n && n.trim().length > 0)
    .map((n) => canonicalFor(n) ?? n.trim());

  const source = exactNames.length > 0 ? exactNames : fallbackNames(event);
  const evidence: BroadcastResolution["evidence"] = exactNames.length > 0 ? "EXACT" : "FALLBACK";

  const groups: BroadcastGroup[] = [];
  const seen = new Set<string>();
  for (const name of source) {
    const canonicalName = name.trim();
    if (seen.has(canonicalName.toLowerCase())) continue;
    seen.add(canonicalName.toLowerCase());
    const channels = matchChannels(canonicalName, libraryLive);
    if (channels.length > 0) groups.push({ canonicalName, channels });
  }
  if (groups.length === 0) return { evidence: "NONE", groups: [] };
  return { evidence, groups };
}

// native sportsSourceQuality — 4K/UHD/FHD/HD/SD/HEVC/H265/1080P/2160P/720P veya "Standart".
export function guessQuality(channelName: string): string {
  const m = channelName.toUpperCase().match(/\b(4K|UHD|FHD|HD|SD|HEVC|H265|1080P|2160P|720P)\b/);
  return m ? m[1] : "Standart";
}
