// StreamLiveX TV — İzleme Geçmişi (native TvHistoryScreen birebir).
// Erişim: sidebar'da YOK, yalnızca Ayarlar → "İzleme Geçmişi".
import { useEffect, useMemo, useRef, useState } from "react";
import type { Media } from "./library";
import { allHistory, restart, removeFromHistory, clearViewingHistory, type HistoryEntry } from "./history";
import { focusFirst } from "./dpad";

type Filter = "all" | "movie" | "series" | "watched";

function toMedia(h: HistoryEntry): Media {
  return { id: h.id, name: h.name, logo: h.logo, group: h.group, kind: h.kind, url: h.url, streamId: h.streamId, seriesId: h.seriesId, year: h.year, rating: h.rating, container: h.container };
}

export function HistoryScreen({ onBack, onOpen }: { onBack: () => void; onOpen: (m: Media) => void }) {
  const [rows, setRows] = useState<HistoryEntry[]>(() => allHistory());
  const [filter, setFilter] = useState<Filter>("all");
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => { const id = setTimeout(() => focusFirst(ref.current), 80); return () => clearTimeout(id); }, [rows.length]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        e.preventDefault(); e.stopPropagation(); onBack();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onBack]);

  const visible = useMemo(() => {
    if (filter === "movie") return rows.filter((r) => r.kind === "movie");
    if (filter === "series") return rows.filter((r) => r.kind === "series");
    if (filter === "watched") return rows.filter((r) => r.watched);
    return rows;
  }, [rows, filter]);

  function refresh() { setRows(allHistory()); }

  return (
    <div className="tv-page" ref={ref}>
      <div className="tv-page-head"><h1>İzleme Geçmişi</h1></div>
      <div className="tv-search-tabs">
        <button className={`tv-search-tab tv-focusable${filter === "all" ? " active" : ""}`} onClick={() => setFilter("all")}>Tümü</button>
        <button className={`tv-search-tab tv-focusable${filter === "movie" ? " active" : ""}`} onClick={() => setFilter("movie")}>Filmler</button>
        <button className={`tv-search-tab tv-focusable${filter === "series" ? " active" : ""}`} onClick={() => setFilter("series")}>Diziler</button>
        <button className={`tv-search-tab tv-focusable${filter === "watched" ? " active" : ""}`} onClick={() => setFilter("watched")}>İzlenenler</button>
        <button className="tv-search-tab tv-focusable" onClick={() => { clearViewingHistory(); refresh(); }}>Geçmişi Temizle</button>
      </div>

      {visible.length === 0 ? <div className="tv-coming">İzleme geçmişi boş.</div> : (
        <div className="tv-history-list">
          {visible.map((h) => (
            <div key={h.id} className="tv-history-row">
              <button className="tv-poster-card tv-history-poster tv-focusable" title={h.name} onClick={() => onOpen(toMedia(h))}>
                {h.logo ? <img src={h.logo} alt={h.name} loading="lazy" /> : <span className="tv-poster-ph">{h.name.slice(0, 2)}</span>}
                <span className="tv-kind-tag">{h.watched ? "✓ İzlendi" : h.positionMs > 0 ? "Kaldığın yerden devam" : h.kind}</span>
              </button>
              <div className="tv-history-meta">
                <b>{h.name}</b>
                <div className="tv-history-actions">
                  <button className="tv-detail-btn tv-focusable" onClick={() => onOpen(toMedia(h))}>Devam Et</button>
                  <button className="tv-detail-btn tv-focusable" onClick={() => { restart(h.id); onOpen(toMedia(h)); }}>Baştan Başlat</button>
                  <button className="tv-detail-btn tv-focusable" onClick={() => { removeFromHistory(h.id); refresh(); }}>Geçmişten Kaldır</button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
