// StreamLiveX TV — tanılama hata kaydı (native: tv/data/TvDiagnosticsStore.kt eşdeğeri).
// Basit localStorage tabanlı, en son 20 kayıt, en yeni önde.
export type DiagnosticsEntry = { module: string; message: string; atMs: number };

const KEY = "slx-tv-diagnostics-log";
const MAX = 20;

function readAll(): DiagnosticsEntry[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) || "[]");
  } catch {
    return [];
  }
}

export function log(module: string, message: string): void {
  try {
    const rows = readAll();
    rows.unshift({ module, message, atMs: Date.now() });
    localStorage.setItem(KEY, JSON.stringify(rows.slice(0, MAX)));
  } catch {
    /* göz ardı — tanılama kaydı asla ana akışı bozmamalı */
  }
}

export function getLogs(): DiagnosticsEntry[] {
  return readAll();
}
