// StreamLiveX TV — TvActiveScope + TvProfilePolicy eşdeğeri (native: tv/profile/TvProfileStore.kt).
// Playlist+profil bazlı depolama izolasyonu ve çocuk-modu içerik filtresi.

type ScopeState = { playlistId: string; profileId: string; kidsMode: boolean };

let current: ScopeState = { playlistId: "legacy", profileId: "default", kidsMode: false };

export function activateScope(playlistId: string | null | undefined, profileId: string, isKids: boolean) {
  current = { playlistId: (playlistId || "").trim() || "legacy", profileId, kidsMode: isKids };
}

// Native sanitize: value.replace(Regex("[^A-Za-z0-9_-]"), "_")
export function storageKey(): string {
  return `${current.playlistId}_${current.profileId}`.replace(/[^A-Za-z0-9_-]/g, "_");
}

export function isKidsMode(): boolean {
  return current.kidsMode;
}

// --- TvProfilePolicy ---
const BLOCKED_TOKENS = ["adult", "xxx", "18+", "18 plus", "erotic", "erotik", "porn", "porno"];
const KIDS_TOKENS = [
  "çocuk", "cocuk", "kids", "kid", "child", "cartoon", "çizgi", "cizgi", "animasyon", "animation",
  "anime", "aile", "family", "disney", "pixar", "nick", "nickelodeon", "cartoon network", "baby",
  "bebek", "junior", "toon",
];

export function isKidsCategory(name?: string | null): boolean {
  const value = (name || "").toLowerCase();
  if (!value) return false;
  if (BLOCKED_TOKENS.some((t) => value.includes(t))) return false;
  return KIDS_TOKENS.some((t) => value.includes(t));
}

// Native TvProfilePolicy.allow(title, category)
export function policyAllow(title?: string | null, category?: string | null): boolean {
  if (!current.kidsMode) return true;
  const haystack = [title, category].filter(Boolean).join(" ").toLowerCase();
  if (BLOCKED_TOKENS.some((t) => haystack.includes(t))) return false;
  if (category != null) return isKidsCategory(category);
  return true;
}
