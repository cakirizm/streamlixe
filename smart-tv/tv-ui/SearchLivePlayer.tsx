// StreamLiveX TV — arama sonucunda kanal seçilince açılan hafif oynatıcı (native SearchLivePlayer birebir):
// sade video + sol üst "← {kanal adı}" geri, EPG/bar/altyazı yok.
import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import type { Media } from "./library";

type Status = "loading" | "ready" | "error";

export function SearchLivePlayer({ channel, onClose }: { channel: Media; onClose: () => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const backRef = useRef<HTMLButtonElement>(null);
  const [status, setStatus] = useState<Status>("loading");

  useEffect(() => { const id = setTimeout(() => backRef.current?.focus({ preventScroll: true }), 90); return () => clearTimeout(id); }, []);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    let disposed = false;
    let cleanup: (() => void) | undefined;
    const url = channel.url.trim();
    const isTs = /(?:\.|\/)ts(?:$|[?#])/i.test(url);
    const isHls = /\.m3u8($|\?)/i.test(url);
    setStatus("loading");
    const ready = () => { if (!disposed) { setStatus("ready"); video.play().catch(() => {}); } };
    const fail = () => { if (!disposed) setStatus("error"); };

    (async () => {
      try {
        if (isTs) {
          const mpegts = (await import("mpegts.js")).default;
          if (disposed) return;
          if (mpegts.isSupported()) {
            const p = mpegts.createPlayer({ type: "mpegts", isLive: true, url }, { enableWorker: true, liveBufferLatencyChasing: true });
            cleanup = () => { try { p.pause(); p.unload(); p.detachMediaElement(); p.destroy(); } catch {} };
            p.attachMediaElement(video); p.load(); p.on(mpegts.Events.ERROR, fail);
            video.oncanplay = ready; return;
          }
        }
        if (isHls && !video.canPlayType("application/vnd.apple.mpegurl") && Hls.isSupported()) {
          const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
          cleanup = () => hls.destroy();
          hls.loadSource(url); hls.attachMedia(video);
          hls.on(Hls.Events.MANIFEST_PARSED, ready);
          hls.on(Hls.Events.ERROR, (_, d) => { if (d.fatal) fail(); });
          return;
        }
        video.src = url; video.oncanplay = ready; video.onerror = fail; video.load();
      } catch { fail(); }
    })();

    return () => { disposed = true; cleanup?.(); try { video.pause(); video.removeAttribute("src"); video.load(); } catch {} };
  }, [channel.url]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        e.preventDefault(); e.stopPropagation(); onClose();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onClose]);

  return (
    <div className="tv-player">
      <video ref={videoRef} className="tv-video" autoPlay />
      {status !== "ready" && (
        <div className="tv-player-state">{status === "loading" ? "Yayın açılıyor…" : "Yayın açılamadı. Geri tuşuyla dönün."}</div>
      )}
      <button ref={backRef} className="tv-search-live-back tv-focusable" onClick={onClose}>← {channel.name}</button>
    </div>
  );
}
