// StreamLiveX TV — Canlı TV (native LiveTvScreen birebir):
// [Kategori] [Kanal listesi] [Önizleme paneli: canlı preview + EPG]. OK=önizleme, tekrar OK=tam ekran.
// Tam ekranda: yukarı/aşağı=kanal değiştir (zapping), 3.5sn'de otomatik gizlenen bilgi bandı, Geri=çık.
import { useEffect, useMemo, useRef, useState } from "react";
import Hls from "hls.js";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import { groupsOf, cleanCat, type Library, type Media } from "./library";
import type { Provider } from "./Setup";
import { fetchEpg, hhmm, type EpgProgram } from "./data";
import { focusFirst } from "./dpad";
import { isLiveFav, toggleLiveFav, recordChannelView } from "./liveProfile";
import { isKidsMode, policyAllow } from "./scope";

function qualityLabel(w: number, h: number): string {
  if (!w || !h) return "";
  const longest = Math.max(w, h);
  if (longest >= 3840) return "4K";
  if (longest >= 1920) return "FHD";
  if (longest >= 1280) return "HD";
  return "SD";
}

// Küçük önizleme oynatıcısı (sessiz). .ts→mpegts, .m3u8→hls, diğer→native.
function MiniPlayer({ url, onQuality }: { url: string; kind: "live"; onQuality?: (q: string) => void }) {
  const ref = useRef<HTMLVideoElement>(null);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  useEffect(() => {
    const video = ref.current; if (!video) return;
    let disposed = false; let cleanup: (() => void) | undefined;
    setState("loading");
    const isTs = /(?:\.|\/)ts(?:$|[?#])/i.test(url); const isHls = /\.m3u8($|\?)/i.test(url);
    const reportQuality = () => onQuality?.(qualityLabel(video.videoWidth, video.videoHeight));
    video.addEventListener("loadedmetadata", reportQuality);
    video.addEventListener("resize", reportQuality);
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
    return () => {
      disposed = true; cleanup?.();
      video.removeEventListener("loadedmetadata", reportQuality);
      video.removeEventListener("resize", reportQuality);
      try { video.pause(); video.removeAttribute("src"); video.load(); } catch {}
    };
  }, [url]);
  return (
    <>
      <video ref={ref} className="tv-preview-video" autoPlay muted playsInline />
      {state !== "ready" && <div className="tv-preview-overlay">{state === "loading" ? "Yayın açılıyor…" : "Önizleme açılamadı"}</div>}
    </>
  );
}

// Tam ekran oynatıcı — Live TV'nin kendi fullscreen modu (genel Player.tsx'ten bağımsız, native gibi).
function FullscreenVideo({ url, onQuality }: { url: string; onQuality: (q: string) => void }) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const video = ref.current; if (!video) return;
    let disposed = false; let cleanup: (() => void) | undefined;
    const isTs = /(?:\.|\/)ts(?:$|[?#])/i.test(url); const isHls = /\.m3u8($|\?)/i.test(url);
    const reportQuality = () => onQuality(qualityLabel(video.videoWidth, video.videoHeight));
    video.addEventListener("loadedmetadata", reportQuality);
    video.addEventListener("resize", reportQuality);
    (async () => {
      try {
        if (isTs) {
          const mpegts = (await import("mpegts.js")).default;
          if (disposed) return;
          if (mpegts.isSupported()) {
            const p = mpegts.createPlayer({ type: "mpegts", isLive: true, url }, { enableWorker: true, liveBufferLatencyChasing: true });
            cleanup = () => { try { p.pause(); p.unload(); p.detachMediaElement(); p.destroy(); } catch {} };
            p.attachMediaElement(video); p.load();
            return;
          }
        }
        if (isHls && !video.canPlayType("application/vnd.apple.mpegurl") && Hls.isSupported()) {
          const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
          cleanup = () => hls.destroy();
          hls.loadSource(url); hls.attachMedia(video);
          return;
        }
        video.src = url; video.load();
      } catch { /* sessiz — kumandanın geri tuşuyla çıkılabilir */ }
    })();
    return () => {
      disposed = true; cleanup?.();
      video.removeEventListener("loadedmetadata", reportQuality);
      video.removeEventListener("resize", reportQuality);
      try { video.pause(); video.removeAttribute("src"); video.load(); } catch {}
    };
  }, [url]);
  return <video ref={ref} className="tv-video" autoPlay />;
}

function LiveFullscreen({
  channel, index, upcoming, onExit, onSwitch,
}: {
  channel: Media; index: number; upcoming: EpgProgram | null;
  onExit: () => void; onSwitch: (delta: number) => void;
}) {
  const [showBar, setShowBar] = useState(true);
  const [quality, setQuality] = useState("");
  const hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  function bump() {
    setShowBar(true);
    if (hideTimer.current) clearTimeout(hideTimer.current);
    hideTimer.current = setTimeout(() => setShowBar(false), 3500);
  }
  useEffect(() => { bump(); return () => { if (hideTimer.current) clearTimeout(hideTimer.current); }; }, [channel.id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        e.preventDefault(); e.stopPropagation(); onExit(); return;
      }
      if (e.key === "ArrowUp") { e.preventDefault(); e.stopPropagation(); onSwitch(-1); return; }
      if (e.key === "ArrowDown") { e.preventDefault(); e.stopPropagation(); onSwitch(1); return; }
      e.preventDefault(); e.stopPropagation(); bump();
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onExit, onSwitch]);

  return (
    <div className="tv-live-fullscreen">
      <FullscreenVideo url={channel.url} onQuality={setQuality} />
      {showBar && (
        <div className="tv-live-fs-overlay">
          <span className="tv-live-fs-num">{index + 1}</span>
          <div className="tv-live-fs-info">
            <div className="tv-live-fs-title-row">
              <b>{channel.name}</b>
              <span className="tv-live-badge">CANLI</span>
              {quality && <span className="tv-live-fs-quality">{quality}</span>}
            </div>
            {upcoming && <small>Sonraki {hhmm(upcoming.start)} · {upcoming.title}</small>}
          </div>
        </div>
      )}
    </div>
  );
}

export function LiveScreen({ lang, library, provider, onOpen }: { lang: TvLang; library: Library | null; provider: Provider | null; onOpen: (m: Media) => void }) {
  void onOpen; // native: Live TV kendi tam ekran modunu kullanır, genel Player'ı açmaz.
  const t = makeT(lang);
  const kids = isKidsMode();
  const allChannels = library?.live || [];
  const channels = useMemo(() => allChannels.filter((c) => policyAllow(c.name, c.group)), [allChannels]);
  const cats = useMemo(() => groupsOf(channels), [channels]);
  const [cat, setCat] = useState("Tümü");
  const [selId, setSelId] = useState<string>("");
  const [playbackChannelId, setPlaybackChannelId] = useState<string>("");
  const [fullscreen, setFullscreen] = useState(false);
  const [catFocused, setCatFocused] = useState(true);
  const [quality, setQuality] = useState("");
  const [fav, setFav] = useState(false);
  const [epg, setEpg] = useState<EpgProgram[] | null>(null);
  const catRef = useRef<HTMLDivElement>(null);
  const chanRef = useRef<HTMLDivElement>(null);
  const epgCacheRef = useRef<Map<string, { rows: EpgProgram[]; at: number }>>(new Map());

  const list = useMemo(() => cat === "Tümü" ? channels : channels.filter((c) => c.group === cat), [channels, cat]);
  const selected = useMemo(() => list.find((c) => c.id === selId) || null, [list, selId]);
  const playback = useMemo(() => list.find((c) => c.id === playbackChannelId) || null, [list, playbackChannelId]);
  const activeGroup = useMemo(() => channels.find((c) => c.id === playbackChannelId)?.group, [channels, playbackChannelId]);

  // Native: başlangıç odağı kategori panelindedir (kanal veya önizleme değil).
  useEffect(() => { const id = setTimeout(() => focusFirst(catRef.current), 90); return () => clearTimeout(id); }, [library]);

  useEffect(() => { setFav(selected ? isLiveFav(selected.id) : false); }, [selected?.id]);

  // Seçili kanal değişince EPG çek — 5dk bellek-içi cache + 180ms debounce (native).
  useEffect(() => {
    if (!selected || !provider || provider.method !== "xtream" || !selected.streamId) { setEpg([]); return; }
    const cache = epgCacheRef.current;
    const cached = cache.get(selected.streamId);
    if (cached && Date.now() - cached.at < 5 * 60 * 1000) { setEpg(cached.rows); return; }
    setEpg(null);
    let active = true;
    const id = setTimeout(() => {
      fetchEpg(provider.server, provider.username, provider.password, selected.streamId)
        .then((rows) => { if (active) { cache.set(selected.streamId!, { rows, at: Date.now() }); setEpg(rows); } });
    }, 180);
    return () => { active = false; clearTimeout(id); };
  }, [selected?.id, provider]);

  function activate(ch: Media) {
    if (playbackChannelId === ch.id) setFullscreen(true);       // aynı kanalda 2. OK → tam ekran
    else { setPlaybackChannelId(ch.id); setFullscreen(false); } // 1. OK → küçük önizleme
    recordChannelView(ch);
  }

  function moveChannel(delta: number) {
    if (!list.length) return;
    const idx = list.findIndex((c) => c.id === playbackChannelId);
    const next = list[(((idx < 0 ? 0 : idx) + delta) % list.length + list.length) % list.length];
    if (next) { setPlaybackChannelId(next.id); setSelId(next.id); recordChannelView(next); }
  }

  function exitFullscreen() {
    setFullscreen(false);
    setSelId(playbackChannelId);
    const id = setTimeout(() => focusFirst(chanRef.current), 60);
    return () => clearTimeout(id);
  }

  if (!library) return <div className="tv-page"><div className="tv-page-head"><h1>{t("live")}</h1></div><div className="tv-coming">Canlı kanallar için Xtream hesabınla giriş yap.</div></div>;
  if (channels.length === 0) {
    return <div className="tv-page"><div className="tv-page-head"><h1>{t("live")}</h1></div>
      <div className="tv-coming">{kids ? "Çocuk profili için canlı TV kategorisi bulunamadı." : "Canlı TV kategorisi bulunamadı."}</div></div>;
  }

  const current = epg ? epg.find((p) => Date.now() >= p.start && Date.now() < p.stop) : null;
  const upcoming = epg ? epg.filter((p) => p.start >= Date.now()).slice(0, 4) : [];

  if (fullscreen && playback) {
    return (
      <LiveFullscreen
        channel={playback}
        index={list.findIndex((c) => c.id === playback.id)}
        upcoming={upcoming[0] || null}
        onExit={exitFullscreen}
        onSwitch={moveChannel}
      />
    );
  }

  return (
    <div className="tv-live3">
      {/* Kategori sütunu — odakta 240px, odaksız 140px (native 140dp↔240dp) */}
      <aside
        className={`tv-live-cats${catFocused ? " expanded" : ""}`}
        ref={catRef}
        onFocusCapture={() => setCatFocused(true)}
        onBlurCapture={(e) => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setCatFocused(false); }}
      >
        <div className="tv-live-channels-head"><b>Kategoriler</b><span>{cats.length}</span></div>
        {cats.map((c) => (
          <button
            key={c}
            className={`tv-live-cat tv-focusable${cat === c ? " selected" : ""}${c === activeGroup ? " on-air" : ""}`}
            onClick={() => setCat(c)} onFocus={() => setCat(c)}
          >
            {c === "Tümü" ? c : cleanCat(c)}{c === activeGroup && <i className="on-air-dot"> ● AÇIK</i>}
          </button>
        ))}
      </aside>

      {/* Kanal listesi */}
      <div className="tv-live-channels" ref={chanRef}>
        <div className="tv-live-channels-head"><b>Kanallar</b><span>{list.length}</span></div>
        {list.length === 0 ? <div className="tv-rail-loading">Bu kategoride kanal yok</div> : (
        <div className="tv-live-channels-list">
          {list.slice(0, 500).map((ch, i) => (
            <button
              key={ch.id}
              className={`tv-live-row tv-focusable${selId === ch.id ? " selected" : ""}${playbackChannelId === ch.id ? " playing" : ""}`}
              onFocus={() => setSelId(ch.id)}
              onClick={() => activate(ch)}
            >
              <span className="clogo">{ch.logo ? <img src={ch.logo} alt="" loading="lazy" /> : <b>{ch.name.slice(0, 2)}</b>}</span>
              <span className="cnum">{i + 1}</span>
              <span className="cinfo">
                <b>{ch.name}</b>
                <small className={playbackChannelId === ch.id ? "on-air-dot" : undefined}>{playbackChannelId === ch.id ? "● ŞU AN AÇIK" : ch.epgId ? "EPG mevcut" : "Canlı yayın"}</small>
              </span>
            </button>
          ))}
        </div>
        )}
      </div>

      {/* Önizleme paneli */}
      <div className="tv-live-preview">
        <div className="tv-preview-box">
          {playback ? <MiniPlayer url={playback.url} kind="live" onQuality={setQuality} /> : <div className="tv-preview-empty">OK ile kanalı aç</div>}
          <span className="tv-live-badge">● CANLI</span>
          {playback && quality && <span className="tv-live-quality">{quality}</span>}
        </div>
        <div className="tv-preview-info">
          <div className="tv-preview-head">
            <span className="plogo">{selected?.logo ? <img src={selected.logo} alt="" /> : <b>{(selected?.name || "?").slice(0, 2)}</b>}</span>
            <div className="pmeta">
              <b>{selected?.name || "Kanal seç"}</b>
              <small>{selected?.group ? cleanCat(selected.group) : ""}</small>
            </div>
            {selected && (
              <button className="tv-live-fav-btn tv-focusable" onClick={() => setFav(toggleLiveFav(selected))}>
                {fav ? "★ Favorilerden Çıkar" : "☆ Favoriye Ekle"}
              </button>
            )}
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
