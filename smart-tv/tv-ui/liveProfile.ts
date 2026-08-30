// StreamLiveX TV — canlı TV'ye özgü profil verisi (native TvLiveProfileStore eşdeğeri).
// My List'teki genel favoriden (favorites.ts) AYRI bir mekanizma: favori kanallar + son izlenen.
// Playlist+profil scope'una göre izole (native TvActiveScope.storageKey()).
import type { Media } from "./library";
import { storageKey } from "./scope";

type LiveProfileState = { favoriteIds: string[]; lastChannelId: string | null };

function keyFor(): string {
  return `slx-tv-liveprofile-${storageKey()}`;
}

function read(): LiveProfileState {
  try {
    const raw = JSON.parse(localStorage.getItem(keyFor()) || "null");
    if (raw && Array.isArray(raw.favoriteIds)) return raw;
  } catch { /* yut */ }
  return { favoriteIds: [], lastChannelId: null };
}
function write(s: LiveProfileState) {
  localStorage.setItem(keyFor(), JSON.stringify(s));
}

// Kanal referanslarını ayrı bir tabloda tutuyoruz (Home "Canlı Şimdi" rail'i için Media gerekiyor).
function refsKey(): string {
  return `slx-tv-liveprofile-refs-${storageKey()}`;
}
function readRefs(): Record<string, Media> {
  try { return JSON.parse(localStorage.getItem(refsKey()) || "{}"); } catch { return {}; }
}
function writeRefs(rows: Record<string, Media>) {
  localStorage.setItem(refsKey(), JSON.stringify(rows));
}

export function isLiveFav(channelId: string): boolean {
  return read().favoriteIds.includes(channelId);
}

export function toggleLiveFav(channel: Media): boolean {
  const s = read();
  const i = s.favoriteIds.indexOf(channel.id);
  const refs = readRefs();
  if (i >= 0) {
    s.favoriteIds.splice(i, 1);
    delete refs[channel.id];
  } else {
    s.favoriteIds.unshift(channel.id);
    refs[channel.id] = channel;
  }
  write(s);
  writeRefs(refs);
  return i < 0; // true = favoriye eklendi
}

// Home "Canlı Şimdi" rail'i — native: favori kanallar (TvLiveProfileStore.favoriteChannelIds).
export function getLiveFavChannels(): Media[] {
  const s = read();
  const refs = readRefs();
  return s.favoriteIds.map((id) => refs[id]).filter(Boolean);
}

export function recordChannelView(channel: Media) {
  const s = read();
  s.lastChannelId = channel.id;
  write(s);
  const refs = readRefs();
  refs[channel.id] = channel;
  writeRefs(refs);
}

export function getLastChannelId(): string | null {
  return read().lastChannelId;
}
