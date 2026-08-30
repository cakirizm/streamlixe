// StreamLiveX TV — çoklu oynatma listesi kaydı (native: tv/setup/TvPlaylistRegistry.kt eşdeğeri).
import type { Provider } from "./Setup";

export type PlaylistAccount = { id: string; provider: Provider; addedAt: number; lastUsedAt: number };

const KEY = "slx-tv-playlists-v1";
const ACTIVE_KEY = "slx-tv-playlists-active";
const LEGACY_PROVIDER_KEY = "slx-tv-provider";
const MIGRATED_FLAG = "slx-tv-playlists-migrated";

function readAll(): PlaylistAccount[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) || "[]");
  } catch {
    return [];
  }
}
function writeAll(rows: PlaylistAccount[]) {
  localStorage.setItem(KEY, JSON.stringify(rows));
}

// Native sameProvider(): server sondaki "/" temizlenmiş + case-insensitive, username eşleşmesi.
function sameProvider(a: Provider, b: Provider): boolean {
  const norm = (s?: string) => (s || "").replace(/\/+$/, "").toLowerCase();
  return norm(a.server) === norm(b.server) && (a.username || "") === (b.username || "");
}

function migrateLegacyIfNeeded() {
  if (localStorage.getItem(MIGRATED_FLAG)) return;
  localStorage.setItem(MIGRATED_FLAG, "1");
  try {
    const raw = localStorage.getItem(LEGACY_PROVIDER_KEY);
    if (!raw) return;
    const legacy = JSON.parse(raw) as Provider | null;
    if (!legacy || !legacy.server || !legacy.username) return;
    addOrUpdatePlaylist(legacy, true);
  } catch {
    /* göz ardı */
  }
}

export function allPlaylists(): PlaylistAccount[] {
  migrateLegacyIfNeeded();
  return readAll().sort((a, b) => b.lastUsedAt - a.lastUsedAt);
}

export function activePlaylistId(): string | null {
  migrateLegacyIfNeeded();
  return localStorage.getItem(ACTIVE_KEY);
}

export function activePlaylist(): PlaylistAccount | null {
  const id = activePlaylistId();
  const rows = readAll();
  return rows.find((r) => r.id === id) || rows[0] || null;
}

export function addOrUpdatePlaylist(provider: Provider, makeActive = true): PlaylistAccount {
  const now = Date.now();
  const rows = readAll();
  const existingIdx = rows.findIndex((r) => sameProvider(r.provider, provider));
  let account: PlaylistAccount;
  if (existingIdx >= 0) {
    const existing = rows[existingIdx];
    account = {
      ...existing,
      provider: { ...provider, name: provider.name || existing.provider.name },
      lastUsedAt: makeActive ? now : existing.lastUsedAt,
    };
    rows[existingIdx] = account;
  } else {
    account = { id: `${now}-${Math.random().toString(36).slice(2, 8)}`, provider, addedAt: now, lastUsedAt: now };
    rows.push(account);
  }
  writeAll(rows);
  if (makeActive) localStorage.setItem(ACTIVE_KEY, account.id);
  return account;
}

export function selectPlaylist(id: string): PlaylistAccount | null {
  const rows = readAll();
  const idx = rows.findIndex((r) => r.id === id);
  if (idx < 0) return null;
  rows[idx] = { ...rows[idx], lastUsedAt: Date.now() };
  writeAll(rows);
  localStorage.setItem(ACTIVE_KEY, rows[idx].id);
  return rows[idx];
}

export function removePlaylist(id: string): PlaylistAccount | null {
  const rows = readAll().filter((r) => r.id !== id);
  writeAll(rows);
  const next = rows.reduce<PlaylistAccount | null>((best, r) => (!best || r.lastUsedAt > best.lastUsedAt ? r : best), null);
  if (next) localStorage.setItem(ACTIVE_KEY, next.id);
  else localStorage.removeItem(ACTIVE_KEY);
  return next;
}
