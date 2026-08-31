// StreamLiveX TV — izleme ilerlemesi / geçmişi (native TvContentStore eşdeğeri).
// Playlist+profil scope'una göre izole (native TvActiveScope.storageKey()).
import type { Media } from "./library";
import { storageKey } from "./scope";

export type HistoryEntry = {
  id: string; name: string; logo?: string; group: string; kind: Media["kind"]; url: string;
  streamId?: string; seriesId?: string; year?: string; rating?: string; container?: string;
  positionMs: number; durationMs: number; watched: boolean; updatedAt: number;
};

function keyFor(): string {
  return `slx-tv-history-${storageKey()}`;
}

function readAll(): Record<string, HistoryEntry> {
  try { return JSON.parse(localStorage.getItem(keyFor()) || "{}"); } catch { return {}; }
}
function writeAll(rows: Record<string, HistoryEntry>) {
  localStorage.setItem(keyFor(), JSON.stringify(rows));
}

function upsert(m: Media, patch: Partial<HistoryEntry>) {
  const rows = readAll();
  const prev = rows[m.id];
  rows[m.id] = {
    id: m.id, name: m.name, logo: m.logo, group: m.group, kind: m.kind, url: m.url,
    streamId: m.streamId, seriesId: m.seriesId, year: m.year, rating: m.rating, container: m.container,
    positionMs: prev?.positionMs || 0, durationMs: prev?.durationMs || 0, watched: prev?.watched || false,
    updatedAt: Date.now(),
    ...patch,
  };
  writeAll(rows);
}

// Native: 10sn'de bir saveProgress; %95 ve üzeri izlenmişse setWatched(true).
export function saveProgress(m: Media, positionMs: number, durationMs: number) {
  if (m.kind === "live") return;
  const watched = durationMs > 0 && positionMs / durationMs >= 0.95;
  upsert(m, { positionMs, durationMs, watched: watched || readAll()[m.id]?.watched || false });
}

export function progressFor(id: string): HistoryEntry | null {
  return readAll()[id] || null;
}

export function isWatched(id: string): boolean {
  return !!readAll()[id]?.watched;
}

export function setWatched(m: Media, watched: boolean) {
  upsert(m, { watched });
}

export function toggleWatched(m: Media): boolean {
  const next = !isWatched(m.id);
  setWatched(m, next);
  return next;
}

// Native: store.restart(id) — pozisyonu sıfırlar, izlendi işaretini korumaz/temizler.
export function restart(id: string) {
  const rows = readAll();
  if (rows[id]) { rows[id].positionMs = 0; rows[id].updatedAt = Date.now(); writeAll(rows); }
}

export function removeFromHistory(id: string) {
  const rows = readAll();
  delete rows[id];
  writeAll(rows);
}

export function clearViewingHistory() {
  writeAll({});
}

export function allHistory(): HistoryEntry[] {
  return Object.values(readAll()).sort((a, b) => b.updatedAt - a.updatedAt);
}

// Native "Kaldığın Yerden Devam Et": pozisyon > 0 ve henüz tamamlanmamış, en son güncellenen önce.
export function continueWatching(limit = 10): HistoryEntry[] {
  return allHistory().filter((h) => h.positionMs > 0 && !h.watched).slice(0, limit);
}

// Bir diziye ait, en son güncellenen (tamamlanmamış) bölüm — GenericDetailScreen "devam et" hedefi.
export function latestUnfinishedEpisode(seriesId: string): HistoryEntry | null {
  const rows = allHistory().filter((h) => h.seriesId === seriesId && h.positionMs > 0 && !h.watched);
  return rows[0] || null;
}
