// StreamLiveX TV — Oynatma Listeleri (native: tv/setup/TvPlaylistManagerScreen.kt birebir).
import { useEffect, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { Setup, type Provider } from "./Setup";
import { allPlaylists, addOrUpdatePlaylist, selectPlaylist, removePlaylist, activePlaylistId, type PlaylistAccount } from "./playlists";

export function PlaylistManager({
  lang, onBack, onSelected,
}: { lang: TvLang; onBack: () => void; onSelected: (p: Provider) => void }) {
  const [rows, setRows] = useState<PlaylistAccount[]>(() => allPlaylists());
  const [activeId, setActiveId] = useState<string | null>(() => activePlaylistId());
  const [adding, setAdding] = useState(false);
  const backRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!adding) { const id = setTimeout(() => backRef.current?.focus({ preventScroll: true }), 90); return () => clearTimeout(id); }
  }, [adding]);

  useEffect(() => {
    if (adding) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        e.preventDefault(); e.stopPropagation(); onBack();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onBack, adding]);

  if (adding) {
    return (
      <Setup
        lang={lang}
        onBack={() => setAdding(false)}
        onComplete={(p) => {
          addOrUpdatePlaylist(p, true);
          setRows(allPlaylists());
          setActiveId(activePlaylistId());
          setAdding(false);
          onSelected(p);
        }}
      />
    );
  }

  return (
    <div className="tv-page" style={{ maxWidth: 900 }}>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 20 }}>
        <button ref={backRef} className="tv-series-back tv-focusable" onClick={onBack}>← Ayarlara Dön</button>
        <h1 style={{ margin: 0, fontSize: 30 }}>Oynatma Listeleri</h1>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        {rows.map((row) => {
          const isActive = row.id === activeId;
          return (
            <div key={row.id} style={{ display: "flex", alignItems: "center", gap: 12, background: isActive ? "#172554" : "#151C28", borderRadius: 12, padding: 16 }}>
              <div style={{ flex: 1 }}>
                <div style={{ color: "#fff", fontWeight: 700 }}>{row.provider.name}</div>
                <div style={{ color: "#94A3B8" }}>{row.provider.server}</div>
                <div style={{ color: isActive ? "#60A5FA" : "#64748B" }}>{isActive ? "Aktif oynatma listesi" : "Kullanılabilir"}</div>
              </div>
              {!isActive && (
                <button className="tv-btn ghost tv-focusable" style={{ width: "auto", height: "auto", padding: "12px 16px", margin: 0 }}
                  onClick={() => { selectPlaylist(row.id); setActiveId(row.id); setRows(allPlaylists()); onSelected(row.provider); }}>
                  Bu Listeye Geç
                </button>
              )}
              {rows.length > 1 && (
                <button className="tv-btn ghost tv-focusable" style={{ width: "auto", height: "auto", padding: "12px 16px", margin: 0 }}
                  onClick={() => { const next = removePlaylist(row.id); setRows(allPlaylists()); setActiveId(activePlaylistId()); if (next) onSelected(next.provider); }}>
                  Sil
                </button>
              )}
            </div>
          );
        })}
        <button className="tv-btn tv-focusable" style={{ width: "auto", padding: "0 24px" }} onClick={() => setAdding(true)}>
          ＋ Yeni Liste Ekle
        </button>
      </div>
    </div>
  );
}
