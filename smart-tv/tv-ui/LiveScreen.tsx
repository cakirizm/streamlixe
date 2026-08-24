// StreamLiveX TV — Canlı TV (native LiveTvScreen birebir):
// [Kategori] [Kanal listesi] [Önizleme paneli: canlı preview + EPG]. OK=önizleme, tekrar OK=tam ekran.
import { useEffect, useMemo, useRef, useState } from "react";
import Hls from "hls.js";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import { groupsOf, cleanCat, type Library, type Media } from "./library";
import type { Provider } from "./Setup";
import { fetchEpg, hhmm, type EpgProgram } from "./data";
import { focusFirst } from "./dpad";

// Küçük önizleme oynatıcısı (sessiz). .ts→mpegts, .m3u8→hls, diğer→native.
function MiniPlayer({ url, kind }: { url: string; kind: "live" }) {
  const ref = useRef<HTMLVideoElement>(null);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  useEffect(() => {
    const video = ref.current; if (!video) return;
    let disposed = false; let cleanup: (() => void) | undefined;
    setState("loading");
    const isTs = /(?:\.|\/)ts(?:$|[?#])/i.test(url); const isHls = /\.m3u8($|\?)/i.test(url);
    (async () => {
      try {
        if (isTs) {
          const mpegts = (await import("mpegts.js")).default;
          if (disposed) return;
          if (mpegts.isSupported()) {
            const p = mpegts.createPlayer({ type: "mpegts", isLive: true, url }, { enableWorker: true, liveBufferLatencyChasing: true });
            cleanup = () => { try { p.pause(); p.unload(); p.detachMediaElement(); p.destroy(); } catch {} };
            p.attachMediaElement(video); p.load(); p.on(mpegts.Events.ERROR, () => setState("error"));
            video.oncanplay = () => setState("ready"); return;
          }
        }
        if (isHls && !video.canPlayType("application/vnd.apple.mpegurl") && Hls.isSupported()) {
          const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
          cleanup = () => hls.destroy();
          hls.loadSource(url); hls.attachMedia(video);
          hls.on(Hls.Events.MANIFEST_PARSED, () => setState("ready"));
          hls.on(Hls.Events.ERROR, (_, d) => { if (d.fatal) setState("error"); });
          return;
        }
        video.src = url; video.oncanplay = () => setState("ready"); video.onerror = () => setState("error"); video.load();
      } catch { setState("error"); }
    })();
    return () => { disposed = true; cleanup?.(); try { video.pause(); video.removeAttribute("src"); video.load(); } catch {} };
  }, [url]);
  return (
    <>
      <video ref={ref} className="tv-preview-video" autoPlay muted playsInline />
      {state !== "ready" && <div className="tv-preview-overlay">{state === "loading" ? "Yayın açılıyor…" : "Önizleme açılamadı"}</div>}
    </>
  );
}

export function LiveScreen({ lang, library, provider, onOpen }: { lang: TvLang; library: Library | null; provider: Provider | null; onOpen: (m: Media) => void }) {
  const t = makeT(lang);
  const channels = library?.live || [];
  const cats = useMemo(() => groupsOf(channels), [channels]);
  const [cat, setCat] = useState("Tümü");
  const [selId, setSelId] = useState<string>("");
  const [previewId, setPreviewId] = useState<string>("");
  const [epg, setEpg] = useState<EpgProgram[] | null>(null);
  const catRef = useRef<HTMLDivElement>(null);
  const chanRef = useRef<HTMLDivElement>(null);

  const list = useMemo(() => cat === "Tümü" ? channels : channels.filter((c) => c.group === cat), [channels, cat]);
  const selected = useMemo(() => list.find((c) => c.id === selId) || null, [list, selId]);
  const preview = useMemo(() => list.find((c) => c.id === previewId) || null, [list, previewId]);

  useEffect(() => { const id = setTimeout(() => focusFirst(chanRef.current), 90); return () => clearTimeout(id); }, [library]);

  // Seçili kanal değişince EPG çek (önizleme başlatmadan).
  useEffect(() => {
    if (!selected || !provider || provider.method !== "xtream" || !selected.streamId) { setEpg([]); return; }
    setEpg(null);
    let active = true;
    fetchEpg(provider.server, provider.username, provider.password, selected.streamId)
      .then((rows) => { if (active) setEpg(rows); });
    return () => { active = false; };
  }, [selected?.id, provider]);

  function activate(ch: Media) {
    if (previewId === ch.id) onOpen(ch);      // aynı kanalda ikinci OK → tam ekran
    else setPreviewId(ch.id);                 // ilk OK → önizleme
  }

  if (!library) return <div className="tv-page"><div className="tv-page-head"><h1>{t("live")}</h1></div><div className="tv-coming">Canlı kanallar için Xtream hesabınla giriş yap.</div></div>;

  const current = epg ? epg.find((p) => Date.now() >= p.start && Date.now() < p.stop) : null;
  const upcoming = epg ? epg.filter((p) => p.start >= Date.now()).slice(0, 4) : [];

  return (
    <div className="tv-live3">
      {/* Kategori sütunu */}
      <aside className="tv-live-cats" ref={catRef}>
        <h1 className="tv-cat-title">{t("live")}</h1>
        {cats.map((c) => (
          <button key={c} className={`tv-cat tv-focusable${cat === c ? " active" : ""}`} onClick={() => setCat(c)} onFocus={() => setCat(c)}>{c === "Tümü" ? c : cleanCat(c)}</button>
        ))}
      </aside>

      {/* Kanal listesi */}
      <div className="tv-live-channels" ref={chanRef}>
        <div className="tv-live-channels-head"><b>Kanallar</b><span>{list.length}</span></div>
        <div className="tv-live-channels-list">
          {list.slice(0, 500).map((ch, i) => (
            <button
              key={ch.id}
              className={`tv-live-row tv-focusable${selId === ch.id ? " selected" : ""}${previewId === ch.id ? " playing" : ""}`}
              onFocus={() => setSelId(ch.id)}
              onClick={() => activate(ch)}
            >
              <span className="clogo">{ch.logo ? <img src={ch.logo} alt="" loading="lazy" /> : <b>{ch.name.slice(0, 2)}</b>}</span>
              <span className="cnum">{i + 1}</span>
              <span className="cinfo">
                <b>{ch.name}</b>
                <small>{ch.epgId ? "EPG mevcut" : "Canlı yayın"}</small>
              </span>
            </button>
          ))}
        </div>
      </div>

      {/* Önizleme paneli */}
      <div className="tv-live-preview">
        <div className="tv-preview-box">
          {preview ? <MiniPlayer url={preview.url} kind="live" /> : <div className="tv-preview-empty">OK ile kanalı aç</div>}
          <span className="tv-live-badge">● CANLI</span>
        </div>
        <div className="tv-preview-info">
          <div className="tv-preview-head">
            <span className="plogo">{selected?.logo ? <img src={selected.logo} alt="" /> : <b>{(selected?.name || "?").slice(0, 2)}</b>}</span>
            <div className="pmeta">
              <b>{selected?.name || "Kanal seç"}</b>
              <small>{selected?.group ? cleanCat(selected.group) : ""}</small>
            </div>
          </div>
          <div className="tv-epg">
            {epg === null ? <div className="tv-epg-loading">Program bilgisi yükleniyor…</div>
              : current ? (
                <>
                  <div className="tv-epg-now">
                    <span className="tag">ŞİMDİ</span>
                    <b>{current.title}</b>
                    <small>{hhmm(current.start)} – {hhmm(current.stop)} · {Math.max(0, Math.round((current.stop - Date.now()) / 60000))} dk kaldı</small>
                    <div className="tv-epg-bar"><i style={{ width: `${Math.min(100, Math.max(0, ((Date.now() - current.start) / (current.stop - current.start)) * 100))}%` }} /></div>
                  </div>
                  {upcoming.length > 0 && (
                    <div className="tv-epg-next">
                      {upcoming.map((p, i) => (
                        <div key={i} className="tv-epg-item"><span>{hhmm(p.start)}</span><b>{p.title}</b></div>
                      ))}
                    </div>
                  )}
                </>
              ) : <div className="tv-epg-loading">Program bilgisi bulunamadı</div>}
          </div>
        </div>
      </div>
    </div>
  );
}
