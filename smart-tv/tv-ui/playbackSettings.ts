// StreamLiveX TV — TvPlaybackSettings eşdeğeri (native: tv/data/TvPlaybackSettings.kt).
// Playlist+profil scope'una göre kalıcı (native SharedPreferences key: streamlivex_tv_playback_${storageKey()}).
import { storageKey } from "./scope";

export type FitMode = "fit" | "fill";
export type SubtitleColor = "white" | "yellow" | "cyan";
export type SubtitleBackground = "shadow" | "outline" | "black" | "none";

export type PlaybackSettings = {
  fitMode: FitMode;
  audioLanguage: string; // "auto" | dil kodu
  subtitleLanguage: string;
  subtitlesEnabled: boolean;
  subtitleSizeSp: number; // 18-42, varsayılan 26 (seçenekler: 20/26/32/38)
  subtitleColor: SubtitleColor;
  subtitleBackground: SubtitleBackground;
  subtitleDelayMs: number; // 0-3000 (seçenekler: 0/500/1000/2000/3000)
  autoNextEpisode: boolean;
};

const DEFAULTS: PlaybackSettings = {
  fitMode: "fit",
  audioLanguage: "auto",
  subtitleLanguage: "tr",
  subtitlesEnabled: true,
  subtitleSizeSp: 26,
  subtitleColor: "white",
  subtitleBackground: "shadow",
  subtitleDelayMs: 0,
  autoNextEpisode: true,
};

function keyFor(): string {
  return `slx-tv-playback-${storageKey()}`;
}

export function loadPlaybackSettings(): PlaybackSettings {
  try {
    const raw = JSON.parse(localStorage.getItem(keyFor()) || "null");
    return raw ? { ...DEFAULTS, ...raw } : { ...DEFAULTS };
  } catch {
    return { ...DEFAULTS };
  }
}

export function savePlaybackSettings(next: PlaybackSettings) {
  localStorage.setItem(keyFor(), JSON.stringify(next));
}
