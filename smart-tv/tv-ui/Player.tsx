// StreamLiveX TV — oynatıcı. Ham yayın URL'sini doğrudan oynatır (residential IP; native gibi).
// .ts → mpegts.js, .m3u8 → hls.js/native, diğer (mp4/mkv) → native <video>.
import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import type { Media } from "./library";

type Status = "loading" | "ready" | "error";

export function Player({ media, onClose }: { media: Media; onClose: () => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [engine, setEngine] = useState("");
  const [showBar, setShowBar] = useState(true);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    let disposed = false;
    let cleanup: (() => void) | undefined;
    const url = media.url.trim();
    const isTs = /(?:\.|\/)ts(?:$|[?#])/i.test(url);
    const isHls = /\.m3u8($|\?)/i.test(url);
    setStatus("loading");

    const ready = (label: string) => { if (disposed) return; setEngine(label); setStatus("ready"); video.play().catch(() => {}); };
    const fail = () => { if (!disposed) setStatus("error"); };

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
            cleanup = () => hls.destroy();
            hls.loadSource(url); hls.attachMedia(video);
            hls.on(Hls.Events.MANIFEST_PARSED, () => ready("HLS"));
            hls.on(Hls.Events.ERROR, (_, d) => { if (d.fatal) fail(); });
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

    return () => { disposed = true; cleanup?.(); video.onloadedmetadata = null; video.oncanplay = null; video.onerror = null; try { video.pause(); video.removeAttribute("src"); video.load(); } catch {} };
  }, [media.url, media.kind]);

  // Kumanda: Geri = kapat, herhangi tuş = bar göster
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        e.preventDefault(); e.stopPropagation(); onClose(); return;
      }
      setShowBar(true);
    };
    window.addEventListener("keydown", onKey, true);
    const timer = setInterval(() => setShowBar(false), 4000);
    return () => { window.removeEventListener("keydown", onKey, true); clearInterval(timer); };
  }, [onClose]);

  return (
    <div className="tv-player">
      <video ref={videoRef} className="tv-video" autoPlay />
      {status !== "ready" && (
        <div className="tv-player-state">
          {status === "loading" ? "Yayın açılıyor…" : "Yayın açılamadı. Geri tuşuyla dönün."}
        </div>
      )}
      {showBar && (
        <div className="tv-player-bar">
          <button className="tv-player-back tv-focusable" onClick={onClose}>← Geri</button>
          <div className="tv-player-meta">
            <b>{media.name}</b>
            {engine && <small>{engine}{status === "ready" ? " · Oynatılıyor" : ""}</small>}
          </div>
        </div>
      )}
    </div>
  );
}
