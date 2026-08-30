// StreamLiveX TV — oynatıcı (native TvNativePlayer.kt birebir kontrol çubuğu/davranış).
// Ham yayın URL'sini doğrudan oynatır (residential IP; native gibi).
// .ts → mpegts.js, .m3u8 → hls.js/native, diğer (mp4/mkv) → native <video>.
import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import type { Media } from "./library";
import type { Provider } from "./Setup";
import { fetchSeriesInfo, episodeStreamUrl, parseSeasonEpisode, type SeriesEpisode } from "./library";
import { saveProgress, setWatched } from "./history";
import { makeT, type TvLang } from "./i18n";
import { log } from "./diagnosticsLog";
import {
  loadPlaybackSettings, savePlaybackSettings,
  type PlaybackSettings, type FitMode, type SubtitleColor, type SubtitleBackground,
} from "./playbackSettings";

type Status = "loading" | "ready" | "error";
type PanelId = "none" | "audio" | "subtitle" | "subtitleStyle" | "opensubtitles";
type TrackInfo = { id: string; label: string; lang: string };

const OS_LANGS: { code: string; label: string }[] = [
  { code: "tr", label: "TR" }, { code: "en", label: "EN" }, { code: "el", label: "GR" },
  { code: "fr", label: "FR" }, { code: "ru", label: "RU" },
];
const SUBTITLE_SIZES: { v: number; label: string }[] = [
  { v: 20, label: "Küçük" }, { v: 26, label: "Orta" }, { v: 32, label: "Büyük" }, { v: 38, label: "Çok büyük" },
];
const SUBTITLE_COLORS: { v: SubtitleColor; label: string; css: string }[] = [
  { v: "white", label: "Beyaz", css: "#ffffff" }, { v: "yellow", label: "Sarı", css: "#FFD54A" }, { v: "cyan", label: "Camgöbeği", css: "#22D3EE" },
];
const SUBTITLE_BGS: { v: SubtitleBackground; label: string }[] = [
  { v: "shadow", label: "Yumuşak gölge" }, { v: "outline", label: "Siyah çerçeve" }, { v: "black", label: "Siyah kutu" }, { v: "none", label: "Arka plansız" },
];
const SUBTITLE_DELAYS: { v: number; label: string }[] = [
  { v: 0, label: "Kapalı" }, { v: 500, label: "+0,5sn" }, { v: 1000, label: "+1sn" }, { v: 2000, label: "+2sn" }, { v: 3000, label: "+3sn" },
];

function fmt(ms: number): string {
  if (!ms || ms < 0 || Number.isNaN(ms)) return "0:00";
  const total = Math.floor(ms / 1000);
  const h = Math.floor(total / 3600), m = Math.floor((total % 3600) / 60), s = total % 60;
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}` : `${m}:${String(s).padStart(2, "0")}`;
}

function trackLabel(lang: string, label: string): string {
  return lang && lang !== "und" ? `${label} · ${lang.toUpperCase()}` : label;
}

function cueBackgroundCss(bg: SubtitleBackground): string {
  switch (bg) {
    case "shadow": return "background:transparent;text-shadow:0 0 4px #000,0 0 8px #000;";
    case "outline": return "background:transparent;text-shadow:-1px -1px 0 #000,1px -1px 0 #000,-1px 1px 0 #000,1px 1px 0 #000;";
    case "black": return "background:rgba(0,0,0,.75);";
    case "none": return "background:transparent;text-shadow:none;";
  }
}

function srtToVtt(srt: string, delayMs: number): string {
  const shifted = srt.replace(/(\d{2}):(\d{2}):(\d{2}),(\d{3})/g, (_all, h, m, s, ms) => {
    let total = (Number(h) * 3600 + Number(m) * 60 + Number(s)) * 1000 + Number(ms) + delayMs;
    if (total < 0) total = 0;
    const hh = String(Math.floor(total / 3600000)).padStart(2, "0");
    const mm = String(Math.floor((total % 3600000) / 60000)).padStart(2, "0");
    const ss = String(Math.floor((total % 60000) / 1000)).padStart(2, "0");
    const msec = String(total % 1000).padStart(3, "0");
    return `${hh}:${mm}:${ss}.${msec}`;
  });
  return `WEBVTT\n\n${shifted}`;
}

// Native ExoPlayer playlist otomatik-sıradaki-bölüm mekanizmasının web karşılığı.
async function resolveNextEpisode(provider: Provider, media: Media): Promise<Media | null> {
  if (!media.seriesId) return null;
  const parsed = parseSeasonEpisode(media.name);
  if (!parsed) return null;
  const info = await fetchSeriesInfo(provider, media.seriesId);
  if (!info) return null;
  const seasons = Object.keys(info.episodes).sort((a, b) => Number(a) - Number(b));
  const currentSeasonEps = info.episodes[parsed.season] || [];
  const idx = currentSeasonEps.findIndex((e) => e.id === media.streamId);
  let nextEp: SeriesEpisode | undefined;
  let nextSeason = parsed.season;
  if (idx >= 0 && idx + 1 < currentSeasonEps.length) {
    nextEp = currentSeasonEps[idx + 1];
  } else {
    const si = seasons.indexOf(parsed.season);
    if (si >= 0 && si + 1 < seasons.length) {
      nextSeason = seasons[si + 1];
      nextEp = (info.episodes[nextSeason] || [])[0];
    }
  }
  if (!nextEp) return null;
  const url = episodeStreamUrl(provider, nextEp);
  const baseName = media.name.split(" · ")[0];
  return {
    id: `s-ep-${nextEp.id}`,
    name: `${baseName} · S${nextSeason} B${nextEp.episode_num}${nextEp.title && nextEp.title !== String(nextEp.episode_num) ? ` · ${nextEp.title}` : ""}`,
    logo: media.logo, group: media.group, kind: "series", url, seriesId: media.seriesId,
    streamId: nextEp.id, container: nextEp.container_extension,
  };
}

export function Player({ media, provider, lang, onClose, onAdvance }: {
  media: Media; provider: Provider | null; lang: TvLang; onClose: () => void; onAdvance: (m: Media) => void;
}) {
  const t = makeT(lang);
  const videoRef = useRef<HTMLVideoElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const pauseRef = useRef<HTMLButtonElement>(null);
  // Panel ilk-odak referansı: panele göre button/input arasında değişir, gevşek tipleniyor.
  const panelFirstRef = useRef<any>(null);
  const seekRef = useRef<HTMLDivElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const vttBlobRef = useRef<string | null>(null);

  const [status, setStatus] = useState<Status>("loading");
  const [controlsVisible, setControlsVisible] = useState(true);
  const [panel, setPanel] = useState<PanelId>("none");
  const [paused, setPaused] = useState(false);
  const [currentMs, setCurrentMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [seekFocused, setSeekFocused] = useState(false);
  const [lastActivity, setLastActivity] = useState(0);
  const [settings, setSettings] = useState<PlaybackSettings>(() => loadPlaybackSettings());
  const [audioTracks, setAudioTracks] = useState<TrackInfo[]>([]);
  const [textTracks, setTextTracks] = useState<TrackInfo[]>([]);
  const [selectedAudio, setSelectedAudio] = useState("");
  const [selectedSubtitle, setSelectedSubtitle] = useState<string>("off");
  const [downloadedVtt, setDownloadedVtt] = useState<string | null>(null);
  const [osQuery, setOsQuery] = useState("");
  const [osLang, setOsLang] = useState("tr");
  const [osResults, setOsResults] = useState<{ fileId: string; language: string; release: string }[] | null>(null);
  const [osBusy, setOsBusy] = useState(false);

  const isLive = media.kind === "live";
  const [mainTitle, ...rest] = media.name.split(" · ");
  const episodeLabel = rest.join(" · ");

  function persist(next: PlaybackSettings) { savePlaybackSettings(next); setSettings(next); }

  // --- Kaynak yükleme (mpegts/hls/native) ---
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    let disposed = false;
    let cleanup: (() => void) | undefined;
    const url = media.url.trim();
    const isTs = /(?:\.|\/)ts(?:$|[?#])/i.test(url);
    const isHls = /\.m3u8($|\?)/i.test(url);
    setStatus("loading");
    hlsRef.current = null;

    const ready = (label: string) => { void label; if (disposed) return; setStatus("ready"); video.play().catch(() => {}); };
    const fail = () => { if (!disposed) { setStatus("error"); log("player", `Yayın açılamadı: ${media.name}`); } };

    (async () => {
      try {
        if (isTs) {
          const mpegts = (await import("mpegts.js")).default;
          if (disposed) return;
          if (mpegts.isSupported()) {
            const player = mpegts.createPlayer(
              { type: "mpegts", isLive: media.kind === "live", url },
              { enableWorker: true, liveBufferLatencyChasing: media.kind === "live", lazyLoad: false },
            );
            cleanup = () => { try { player.pause(); player.unload(); player.detachMediaElement(); player.destroy(); } catch {} };
            player.attachMediaElement(video);
            player.load();
            player.on(mpegts.Events.ERROR, fail);
            video.oncanplay = () => ready("MPEG-TS");
            return;
          }
        }
        if (isHls) {
          if (video.canPlayType("application/vnd.apple.mpegurl")) {
            video.src = url; video.onloadedmetadata = () => ready("HLS"); video.onerror = fail; video.load(); return;
          }
          if (Hls.isSupported()) {
            const hls = new Hls({ enableWorker: true, lowLatencyMode: media.kind === "live" });
            hlsRef.current = hls;
            cleanup = () => { hlsRef.current = null; hls.destroy(); };
            hls.loadSource(url); hls.attachMedia(video);
            hls.on(Hls.Events.MANIFEST_PARSED, () => ready("HLS"));
            hls.on(Hls.Events.ERROR, (_e, d) => { if (d.fatal) fail(); });
            return;
          }
        }
        // Düz dosya (mp4/mkv/avi) — tarayıcı/TV native oynatıcı
        video.src = url;
        video.onloadedmetadata = () => ready("Native");
        video.oncanplay = () => ready("Native");
        video.onerror = fail;
        video.load();
      } catch { fail(); }
    })();

    return () => {
      disposed = true; cleanup?.();
      video.onloadedmetadata = null; video.oncanplay = null; video.onerror = null;
      try { video.pause(); video.removeAttribute("src"); video.load(); } catch {}
    };
  }, [media.url, media.kind]);

  // --- İlerleme / süre / oynatma durumu ---
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    const onTime = () => setCurrentMs(video.currentTime * 1000);
    const onDur = () => setDurationMs((video.duration || 0) * 1000);
    const onPlay = () => setPaused(false);
    const onPause = () => setPaused(true);
    video.addEventListener("timeupdate", onTime);
    video.addEventListener("loadedmetadata", onDur);
    video.addEventListener("durationchange", onDur);
    video.addEventListener("play", onPlay);
    video.addEventListener("pause", onPause);
    return () => {
      video.removeEventListener("timeupdate", onTime);
      video.removeEventListener("loadedmetadata", onDur);
      video.removeEventListener("durationchange", onDur);
      video.removeEventListener("play", onPlay);
      video.removeEventListener("pause", onPause);
    };
  }, [media.url]);

  // --- Native: 10sn'de bir saveProgress; %95+ / "ended" → izlendi işaretlenir + autoNextEpisode ---
  useEffect(() => {
    if (isLive) return;
    const video = videoRef.current;
    if (!video) return;
    const persistProgress = () => {
      if (video.duration > 0 && !Number.isNaN(video.duration)) {
        saveProgress(media, video.currentTime * 1000, video.duration * 1000);
      }
    };
    const interval = setInterval(persistProgress, 10_000);
    const onEnded = async () => {
      setWatched(media, true);
      if (media.kind === "series" && media.seriesId && settings.autoNextEpisode && provider) {
        const next = await resolveNextEpisode(provider, media);
        if (next) onAdvance(next);
      }
    };
    video.addEventListener("ended", onEnded);
    return () => {
      clearInterval(interval);
      video.removeEventListener("ended", onEnded);
      persistProgress();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [media, isLive, settings.autoNextEpisode, provider]);

  // --- Ses/altyazı track keşfi (hazır olunca) ---
  useEffect(() => {
    if (status !== "ready") return;
    const video = videoRef.current as any;
    if (!video) return;
    if (hlsRef.current) {
      const list = hlsRef.current.audioTracks.map((tr, i) => ({ id: String(i), label: trackLabel(tr.lang || "und", tr.name || `Ses ${i + 1}`), lang: tr.lang || "und" }));
      setAudioTracks(list);
      setSelectedAudio(String(hlsRef.current.audioTrack ?? 0));
    } else if (video.audioTracks?.length) {
      const list = Array.from(video.audioTracks as any[]).map((tr, i) => ({ id: String(i), label: trackLabel(tr.language || "und", tr.label || `Ses ${i + 1}`), lang: tr.language || "und" }));
      setAudioTracks(list);
    } else {
      setAudioTracks([]);
    }
    const vlist: TrackInfo[] = Array.from((videoRef.current?.textTracks || []) as any).map((tr: any, i: number) => ({
      id: String(i), label: trackLabel(tr.language || "und", tr.label || `Altyazı ${i + 1}`), lang: tr.language || "und",
    }));
    setTextTracks(vlist);
  }, [status]);

  function selectAudio(id: string) {
    setSelectedAudio(id);
    if (hlsRef.current) { hlsRef.current.audioTrack = Number(id); }
    else {
      const v = videoRef.current as any;
      if (v?.audioTracks) for (let i = 0; i < v.audioTracks.length; i++) v.audioTracks[i].enabled = String(i) === id;
    }
    const chosen = audioTracks.find((a) => a.id === id);
    if (chosen) persist({ ...settings, audioLanguage: chosen.lang });
    setPanel("none");
    setTimeout(() => pauseRef.current?.focus(), 60);
  }

  function selectSubtitle(id: string) {
    setSelectedSubtitle(id);
    if (id === "off") {
      persist({ ...settings, subtitlesEnabled: false });
      const v = videoRef.current;
      if (v) for (let i = 0; i < v.textTracks.length; i++) v.textTracks[i].mode = "disabled";
    } else {
      const chosen = textTracks.find((tr) => tr.id === id);
      const next = { ...settings, subtitlesEnabled: true, subtitleLanguage: chosen?.lang || settings.subtitleLanguage };
      persist(next);
      const v = videoRef.current;
      if (v) for (let i = 0; i < v.textTracks.length; i++) v.textTracks[i].mode = String(i) === id ? "showing" : "disabled";
    }
    setPanel("none");
    setTimeout(() => pauseRef.current?.focus(), 60);
  }

  async function searchOpenSubtitles() {
    setOsBusy(true); setOsResults(null);
    try {
      const q = osQuery.trim() || mainTitle;
      const r = await fetch(`/api/subtitles?query=${encodeURIComponent(q)}&langs=${osLang}`);
      const data = await r.json();
      setOsResults(Array.isArray(data.results) ? data.results : []);
    } catch { setOsResults([]); }
    finally { setOsBusy(false); }
  }

  async function applyOpenSubtitle(fileId: string) {
    try {
      const dl = await fetch(`/api/subtitles?mode=download&fileId=${encodeURIComponent(fileId)}`).then((r) => r.json());
      if (!dl?.url) return;
      const srt = await fetch(dl.url).then((r) => r.text());
      const vtt = srtToVtt(srt, settings.subtitleDelayMs);
      const blob = new Blob([vtt], { type: "text/vtt" });
      if (vttBlobRef.current) URL.revokeObjectURL(vttBlobRef.current);
      const url = URL.createObjectURL(blob);
      vttBlobRef.current = url;
      setDownloadedVtt(url);
      persist({ ...settings, subtitlesEnabled: true });
      setPanel("none");
      setTimeout(() => pauseRef.current?.focus(), 60);
    } catch { /* Native de ağ hatasında ayrı bir hata ekranı göstermiyor — sessizce yut */ }
  }

  useEffect(() => () => { if (vttBlobRef.current) URL.revokeObjectURL(vttBlobRef.current); }, []);

  function toggleFit() { persist({ ...settings, fitMode: settings.fitMode === "fit" ? "fill" : "fit" }); }
  function seekBy(deltaMs: number) {
    const v = videoRef.current;
    if (!v || !v.duration) return;
    v.currentTime = Math.min(Math.max(0, v.currentTime + deltaMs / 1000), v.duration);
  }
  function togglePlay() {
    const v = videoRef.current;
    if (!v) return;
    if (v.paused) v.play().catch(() => {}); else v.pause();
  }

  // --- Kumanda: 3 kademeli geri, kontrolleri göster/gizle, SeekBar ±10sn ---
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const key = e.key; const kc = e.keyCode;
      const isBack = key === "Escape" || key === "Backspace" || kc === 461 || kc === 10009;
      const isEnter = key === "Enter";
      const isDir = key === "ArrowUp" || key === "ArrowDown" || key === "ArrowLeft" || key === "ArrowRight";

      if (isBack) {
        e.preventDefault(); e.stopPropagation();
        if (panel !== "none") { setPanel("none"); setTimeout(() => pauseRef.current?.focus(), 60); return; }
        if (controlsVisible) { setControlsVisible(false); setTimeout(() => rootRef.current?.focus(), 30); return; }
        onClose();
        return;
      }

      if (!controlsVisible) {
        if (isDir || isEnter) {
          e.preventDefault(); e.stopPropagation();
          setControlsVisible(true);
          setTimeout(() => pauseRef.current?.focus(), 60);
        }
        return;
      }

      if (panel === "none" && document.activeElement === seekRef.current && (key === "ArrowLeft" || key === "ArrowRight")) {
        e.preventDefault(); e.stopPropagation();
        seekBy(key === "ArrowLeft" ? -10_000 : 10_000);
        setLastActivity(Date.now());
        return;
      }

      setLastActivity(Date.now());
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [controlsVisible, panel]);

  // Kontroller 3.5sn sonra otomatik gizlenir (panel açıkken gizlenmez — native davranışı).
  useEffect(() => {
    if (!controlsVisible || panel !== "none") return;
    const id = setTimeout(() => { setControlsVisible(false); rootRef.current?.focus(); }, 3_500);
    return () => clearTimeout(id);
  }, [controlsVisible, panel, lastActivity]);

  // Panel açılışında ilk öğeye odaklan.
  useEffect(() => {
    if (panel === "none") return;
    const id = setTimeout(() => panelFirstRef.current?.focus(), 100);
    return () => clearTimeout(id);
  }, [panel]);

  const cueStyle = `#tv-player-video::cue{color:${SUBTITLE_COLORS.find((c) => c.v === settings.subtitleColor)?.css};font-size:${settings.subtitleSizeSp}px;${cueBackgroundCss(settings.subtitleBackground)}}`;

  return (
    <div
      className="tv-player"
      ref={rootRef}
      tabIndex={-1}
      onClick={() => { if (!controlsVisible) { setControlsVisible(true); setTimeout(() => pauseRef.current?.focus(), 60); } }}
    >
      <style>{cueStyle}</style>
      <video
        id="tv-player-video"
        ref={videoRef}
        className="tv-video"
        autoPlay
        style={{ objectFit: settings.fitMode === "fill" ? "cover" : "contain" }}
      >
        {downloadedVtt && <track src={downloadedVtt} kind="subtitles" srcLang={osLang} default />}
      </video>

      {status !== "ready" && (
        <div className="tv-player-state">
          {status === "loading" ? "Yayın açılıyor…" : "Yayın açılamadı. Geri tuşuyla dönün."}
        </div>
      )}

      {controlsVisible && (
        <>
          <button className="tv-player-topback tv-focusable" onClick={onClose} aria-label={t("back")}>←</button>

          {!isLive && (
            <div className="tv-player-center">
              <button className="tv-player-icon tv-focusable" onClick={() => seekBy(-10_000)} aria-label="Replay10">⏪</button>
              <button ref={pauseRef} className="tv-player-icon prominent tv-focusable" onClick={togglePlay} aria-label={paused ? t("resume") : t("pause")}>
                {paused ? "▶" : "⏸"}
              </button>
              <button className="tv-player-icon tv-focusable" onClick={() => seekBy(10_000)} aria-label="Forward10">⏩</button>
            </div>
          )}

          <div className="tv-player-bar">
            <div className="tv-player-meta">
              <b>{mainTitle}</b>
              {episodeLabel && <small>{episodeLabel}</small>}
            </div>
            <div className="tv-player-seekrow">
              <span className="tv-player-time">{fmt(currentMs)}</span>
              <div
                ref={seekRef}
                className={`tv-seekbar tv-focusable${seekFocused ? " tv-focused" : ""}`}
                tabIndex={0}
                onFocus={() => setSeekFocused(true)}
                onBlur={() => setSeekFocused(false)}
              >
                <div className="tv-seekbar-track"><i style={{ width: `${durationMs > 0 ? Math.min(100, (currentMs / durationMs) * 100) : 0}%` }} /></div>
                {seekFocused && <div className="tv-seekbar-hint">◀ 10 sn geri &nbsp;•&nbsp; basılı tutarak hızlı sar &nbsp;•&nbsp; 10 sn ileri ▶</div>}
              </div>
              <span className="tv-player-time">{fmt(durationMs)}</span>
            </div>
            <div className="tv-player-utility">
              <button className="tv-player-icon small tv-focusable" onClick={() => setPanel("audio")} aria-label={t("audio")}>🔊</button>
              <button className="tv-player-icon small tv-focusable" onClick={() => setPanel("subtitle")} aria-label={t("subtitles")}>💬</button>
              <button className="tv-player-icon small tv-focusable" onClick={() => setPanel("subtitleStyle")} aria-label="Altyazı Görünümü">Aa</button>
              <button className="tv-player-icon small tv-focusable" onClick={toggleFit} aria-label="Fit/Fill">{settings.fitMode === "fill" ? "⛶" : "▭"}</button>
            </div>
          </div>
        </>
      )}

      {panel === "audio" && (
        <div className="tv-player-panel">
          <h3>{t("audio")}</h3>
          {audioTracks.length === 0 ? <p className="tv-player-panel-empty">{t("no_description")}</p> : audioTracks.map((tr, i) => (
            <button key={tr.id} ref={i === 0 ? panelFirstRef : undefined} className="tv-player-panel-btn tv-focusable" onClick={() => selectAudio(tr.id)}>
              {selectedAudio === tr.id ? "✓ " : ""}{tr.label}
            </button>
          ))}
        </div>
      )}

      {panel === "subtitle" && (
        <div className="tv-player-panel">
          <h3>{t("subtitles")}</h3>
          <button ref={panelFirstRef} className="tv-player-panel-btn tv-focusable" onClick={() => selectSubtitle("off")}>
            {selectedSubtitle === "off" ? "✓ " : ""}{t("off")}
          </button>
          {textTracks.map((tr) => (
            <button key={tr.id} className="tv-player-panel-btn tv-focusable" onClick={() => selectSubtitle(tr.id)}>
              {selectedSubtitle === tr.id ? "✓ " : ""}{tr.label}
            </button>
          ))}
          <button className="tv-player-panel-btn ghost tv-focusable" onClick={() => setPanel("opensubtitles")}>OpenSubtitles • Altyazı bul</button>
        </div>
      )}

      {panel === "opensubtitles" && (
        <div className="tv-player-panel">
          <h3>OpenSubtitles</h3>
          <input
            ref={panelFirstRef}
            className="tv-player-panel-input tv-focusable"
            placeholder="Film / dizi adı"
            value={osQuery}
            onChange={(e) => setOsQuery(e.target.value)}
          />
          <div className="tv-player-panel-langs">
            {OS_LANGS.map((l) => (
              <button key={l.code} className={`tv-player-panel-chip tv-focusable${osLang === l.code ? " active" : ""}`} onClick={() => setOsLang(l.code)}>{l.label}</button>
            ))}
          </div>
          <button className="tv-player-panel-btn tv-focusable" onClick={searchOpenSubtitles} disabled={osBusy}>{osBusy ? "Aranıyor…" : "Ara"}</button>
          {osResults !== null && (
            osResults.length === 0 ? <p className="tv-player-panel-empty">Sonuç bulunamadı.</p> : (
              <div className="tv-player-panel-list">
                {osResults.map((r) => (
                  <button key={r.fileId} className="tv-player-panel-btn tv-focusable" onClick={() => applyOpenSubtitle(r.fileId)}>
                    {r.release} · {r.language.toUpperCase()}
                  </button>
                ))}
              </div>
            )
          )}
          <button className="tv-player-panel-btn ghost tv-focusable" onClick={() => setPanel("subtitle")}>Kapat</button>
        </div>
      )}

      {panel === "subtitleStyle" && (
        <div className="tv-player-panel wide">
          <h3>Altyazı Görünümü</h3>
          <b>Boyut</b>
          <div className="tv-player-panel-row">
            {SUBTITLE_SIZES.map((s, i) => (
              <button key={s.v} ref={i === 0 ? panelFirstRef : undefined} className={`tv-player-panel-chip tv-focusable${settings.subtitleSizeSp === s.v ? " active" : ""}`} onClick={() => persist({ ...settings, subtitleSizeSp: s.v })}>{s.label}</button>
            ))}
          </div>
          <b>Renk</b>
          <div className="tv-player-panel-row">
            {SUBTITLE_COLORS.map((c) => (
              <button key={c.v} className={`tv-player-panel-chip tv-focusable${settings.subtitleColor === c.v ? " active" : ""}`} onClick={() => persist({ ...settings, subtitleColor: c.v })}>{c.label}</button>
            ))}
          </div>
          <b>Arka Plan</b>
          <div className="tv-player-panel-row">
            {SUBTITLE_BGS.map((b) => (
              <button key={b.v} className={`tv-player-panel-chip tv-focusable${settings.subtitleBackground === b.v ? " active" : ""}`} onClick={() => persist({ ...settings, subtitleBackground: b.v })}>{b.label}</button>
            ))}
          </div>
          <b>Gecikme</b>
          <div className="tv-player-panel-row">
            {SUBTITLE_DELAYS.map((d) => (
              <button key={d.v} className={`tv-player-panel-chip tv-focusable${settings.subtitleDelayMs === d.v ? " active" : ""}`} onClick={() => persist({ ...settings, subtitleDelayMs: d.v })}>{d.label}</button>
            ))}
          </div>
          <button className="tv-player-panel-btn ghost tv-focusable" onClick={() => { setPanel("none"); setTimeout(() => pauseRef.current?.focus(), 60); }}>Kapat</button>
        </div>
      )}
    </div>
  );
}
