// StreamLiveX TV — Tanılama (native: tv/diagnostic/TvDiagnosticsScreen.kt tarayıcı-uyumlu karşılığı).
// Cihaz/Aktif Kütüphane/Bağlantı Testi/Son Sağlık Kontrolü/Son Hatalar — 6 bölüm native ile birebir.
import { useEffect, useRef, useState } from "react";
import type { Provider } from "./Setup";
import type { Library } from "./library";
import { importXtream, lastIndexedAtMs } from "./library";
import { allPlaylists } from "./playlists";
import { getLogs, log, type DiagnosticsEntry } from "./diagnosticsLog";

type HealthResult = {
  atMs: number;
  live: { ok: boolean; count: number; error?: string };
  movies: { ok: boolean; count: number; error?: string };
  series: { ok: boolean; count: number; error?: string };
};

function fmtDate(ms: number): string {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${p(d.getDate())}.${p(d.getMonth() + 1)}.${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function deviceSummary(): string {
  const ua = navigator.userAgent || "Bilinmeyen tarayıcı";
  // navigator.deviceMemory yalnızca Chromium tabanlı motorlarda (webOS) mevcut; Tizen WebKit'te yok — o zaman satırı hiç ekleme.
  const mem = (navigator as any).deviceMemory as number | undefined;
  return mem ? `${ua} · ${mem} GB RAM` : ua;
}

export function DiagnosticsScreen({
  provider, library, onBack,
}: { provider: Provider | null; library: Library | null; onBack: () => void }) {
  const backRef = useRef<HTMLButtonElement>(null);
  const [testing, setTesting] = useState(false);
  const [health, setHealth] = useState<HealthResult | null>(null);
  const [logs, setLogs] = useState<DiagnosticsEntry[]>(() => getLogs());

  useEffect(() => { const id = setTimeout(() => backRef.current?.focus({ preventScroll: true }), 90); return () => clearTimeout(id); }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        e.preventDefault(); e.stopPropagation(); onBack();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onBack]);

  const lastIndexed = lastIndexedAtMs();
  const stale = lastIndexed == null || Date.now() - lastIndexed > 24 * 60 * 60 * 1000;
  const contentCount = library ? library.live.length + library.movies.length + library.series.length : 0;

  async function runTest() {
    if (!provider || provider.method !== "xtream") return;
    setTesting(true);
    const atMs = Date.now();
    try {
      const lib = await importXtream(provider);
      const result: HealthResult = {
        atMs,
        live: { ok: lib.live.length > 0, count: lib.live.length },
        movies: { ok: lib.movies.length > 0, count: lib.movies.length },
        series: { ok: lib.series.length > 0, count: lib.series.length },
      };
      setHealth(result);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Bağlantı testi başarısız";
      log("health", msg);
      setHealth({ atMs, live: { ok: false, count: 0, error: msg }, movies: { ok: false, count: 0, error: msg }, series: { ok: false, count: 0, error: msg } });
      setLogs(getLogs());
    } finally {
      setTesting(false);
    }
  }

  return (
    <div className="tv-page tv-diagnostics" style={{ maxWidth: 900 }}>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 20 }}>
        <button ref={backRef} className="tv-series-back tv-focusable" onClick={onBack}>← Ayarlara Dön</button>
        <h1 style={{ margin: 0, fontSize: 30 }}>Tanılama</h1>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <div className="tv-diag-card">
          <b>Cihaz</b>
          <span>{deviceSummary()}</span>
          <span>Kayıtlı playlist: {allPlaylists().length}</span>
        </div>

        <div className="tv-diag-card">
          <b>Aktif Kütüphane</b>
          <span>{provider?.name || "—"}</span>
          <span>{provider?.server || ""}</span>
          <span>İçerik: {contentCount}</span>
          <span>Son index: {lastIndexed ? fmtDate(lastIndexed) : "—"}</span>
          <span>24 saatlik yenileme: {stale ? "Gerekli" : "Güncel"}</span>
        </div>

        <button className="tv-btn tv-focusable" style={{ width: "auto", padding: "0 24px" }} disabled={testing || !provider || provider.method !== "xtream"} onClick={runTest}>
          {testing ? "Test ediliyor…" : "Bağlantıyı Test Et"}
        </button>

        {health && (
          <div className="tv-diag-card">
            <b>Son Sağlık Kontrolü</b>
            <span>Canlı TV: {health.live.ok ? `OK · ${health.live.count} kanal` : `HATA${health.live.error ? " · " + health.live.error : ""}`}</span>
            <span>Filmler: {health.movies.ok ? `OK · ${health.movies.count} kategori` : `HATA${health.movies.error ? " · " + health.movies.error : ""}`}</span>
            <span>Diziler: {health.series.ok ? `OK · ${health.series.count} kategori` : `HATA${health.series.error ? " · " + health.series.error : ""}`}</span>
            <span>{fmtDate(health.atMs)}</span>
          </div>
        )}

        <div>
          <b style={{ display: "block", marginBottom: 10 }}>Son Hatalar</b>
          {logs.length === 0 ? (
            <div className="tv-coming">Kayıtlı hata yok.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {logs.map((entry, i) => (
                <div key={i} className="tv-diag-card">
                  <b>{entry.module}</b>
                  <span>{fmtDate(entry.atMs)}</span>
                  <span>{entry.message}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
