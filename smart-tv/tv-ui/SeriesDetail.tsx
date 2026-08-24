// StreamLiveX TV — dizi detay (sezon seçici + bölüm listesi). Bölüme tıkla → oynat.
import { useEffect, useMemo, useRef, useState } from "react";
import type { Media } from "./library";
import type { Provider } from "./Setup";
import { focusFirst } from "./dpad";

type Episode = { id: string; title: string; episode_num: number; container_extension?: string; info?: any };

export function SeriesDetail({ media, provider, onOpen, onClose }: {
  media: Media; provider: Provider | null; onOpen: (m: Media) => void; onClose: () => void;
}) {
  const [info, setInfo] = useState<any>(null);
  const [episodes, setEpisodes] = useState<Record<string, Episode[]> | null>(null);
  const [season, setSeason] = useState<string>("");
  const [error, setError] = useState("");
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!provider || provider.method !== "xtream" || !media.seriesId) { setError("Dizi bilgisi alınamadı."); return; }
    (async () => {
      try {
        const r = await fetch("/api/import", {
          method: "POST", headers: { "content-type": "application/json" },
          body: JSON.stringify({ method: "series_info", server: provider.server, username: provider.username, password: provider.password, seriesId: media.seriesId }),
        });
        const data = await r.json();
        setInfo(data.info || {});
        const eps: Record<string, Episode[]> = data.episodes || {};
        setEpisodes(eps);
        const keys = Object.keys(eps).sort((a, b) => Number(a) - Number(b));
        setSeason(keys[0] || "");
      } catch { setError("Dizi bilgisi alınamadı."); }
    })();
  }, [media.seriesId, provider]);

  const seasons = useMemo(() => episodes ? Object.keys(episodes).sort((a, b) => Number(a) - Number(b)) : [], [episodes]);
  const list = useMemo(() => (episodes && season ? episodes[season] : []) || [], [episodes, season]);

  useEffect(() => { const id = setTimeout(() => focusFirst(ref.current), 90); return () => clearTimeout(id); }, [episodes, season]);

  // Geri tuşu
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        const tag = (e.target as HTMLElement)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;
        e.preventDefault(); e.stopPropagation(); onClose();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onClose]);

  function playEpisode(ep: Episode) {
    if (!provider) return;
    const base = (provider.server || "").replace(/\/+$/, "");
    const url = `${base}/series/${provider.username}/${provider.password}/${ep.id}.${ep.container_extension || "mp4"}`;
    onOpen({ ...media, url, name: `${media.name} · S${season} B${ep.episode_num} · ${ep.title || ""}`.trim() });
  }

  const cover = info?.cover || media.logo;
  const plot = info?.plot || info?.description || "";

  return (
    <div className="tv-series" ref={ref}>
      <button className="tv-series-back tv-focusable" onClick={onClose}>← Geri</button>
      <div className="tv-series-head">
        <div className="tv-series-poster">{cover ? <img src={cover} alt="" /> : <span>{media.name.slice(0, 2)}</span>}</div>
        <div className="tv-series-meta">
          <h1>{info?.name || media.name}</h1>
          <div className="tv-series-sub">
            {media.year && <span>{media.year}</span>}
            {media.rating && <span>★ {media.rating}</span>}
            {seasons.length > 0 && <span>{seasons.length} sezon</span>}
          </div>
          {plot && <p className="tv-series-plot">{plot}</p>}
        </div>
      </div>

      {error ? <div className="tv-coming">{error}</div> : !episodes ? <div className="tv-rail-loading">Bölümler yükleniyor…</div> : (
        <>
          {seasons.length > 1 && (
            <div className="tv-search-tabs">
              {seasons.map((s) => (
                <button key={s} className={`tv-search-tab tv-focusable${season === s ? " active" : ""}`} onClick={() => setSeason(s)} onFocus={() => setSeason(s)}>{s}. Sezon</button>
              ))}
            </div>
          )}
          <div className="tv-episodes">
            {list.map((ep) => {
              const still = ep.info?.movie_image || ep.info?.cover_big || cover;
              return (
                <button key={ep.id} className="tv-episode tv-focusable" onClick={() => playEpisode(ep)}>
                  <div className="tv-episode-still">{still ? <img src={still} alt="" loading="lazy" /> : <span>{ep.episode_num}</span>}<i>▶</i></div>
                  <div className="tv-episode-meta">
                    <b>{ep.episode_num}. Bölüm{ep.title && ep.title !== String(ep.episode_num) ? ` · ${ep.title}` : ""}</b>
                    {ep.info?.plot && <small>{ep.info.plot}</small>}
                  </div>
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
