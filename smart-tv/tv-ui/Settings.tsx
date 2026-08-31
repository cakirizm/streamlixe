// StreamLiveX TV — Ayarlar (native TvSettingsScreen, TvRoot.kt:1172-1671 birebir bölüm sırası/içeriği).
import { useEffect, useRef, useState } from "react";
import { TV_LANGS, makeT, type TvLang } from "./i18n";
import type { Provider } from "./Setup";
import { focusFirst } from "./dpad";
import { loadPlaybackSettings, savePlaybackSettings, type PlaybackSettings } from "./playbackSettings";
import { clearViewingHistory } from "./history";

const AUDIO_OPTIONS: { v: string; label: string }[] = [
  { v: "auto", label: "Otomatik" }, { v: "tr", label: "TR" }, { v: "en", label: "EN" }, { v: "ar", label: "AR" },
  { v: "de", label: "DE" }, { v: "fr", label: "FR" }, { v: "es", label: "ES" },
];
const SUBTITLE_LANGS: { v: string; label: string }[] = [
  { v: "tr", label: "TR" }, { v: "en", label: "EN" }, { v: "ar", label: "AR" }, { v: "de", label: "DE" }, { v: "fr", label: "FR" }, { v: "es", label: "ES" },
];

function SectionTitle({ text }: { text: string }) {
  return (
    <div className="tv-set-section-title">
      <span className="bar" />
      <b>{text.toUpperCase()}</b>
    </div>
  );
}

function SetButton({ text, selected, destructive, onClick }: { text: string; selected?: boolean; destructive?: boolean; onClick: () => void }) {
  const [focused, setFocused] = useState(false);
  const cls = ["tv-set-btn", focused && destructive ? "focused-destructive" : "", destructive ? "destructive" : "", focused ? "focused" : "", selected ? "selected" : ""].filter(Boolean).join(" ");
  return (
    <button className={`${cls} tv-focusable`} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} onClick={onClick}>
      {text}
    </button>
  );
}

export function Settings({
  lang, setLang, provider, onChangeProfile, onLogout, onReload,
  onManagePlaylists, onManageProfiles, onHistory, onDiagnostics,
}: {
  lang: TvLang; setLang: (l: TvLang) => void; provider: Provider | null;
  onChangeProfile: () => void; onLogout: () => void; onReload: () => void;
  onManagePlaylists: () => void; onManageProfiles: () => void; onHistory: () => void; onDiagnostics: () => void;
}) {
  const t = makeT(lang);
  const ref = useRef<HTMLDivElement>(null);
  const [pb, setPb] = useState<PlaybackSettings>(() => loadPlaybackSettings());
  const [historyCleared, setHistoryCleared] = useState(false);
  useEffect(() => { const id = setTimeout(() => focusFirst(ref.current), 80); return () => clearTimeout(id); }, []);

  function savePb(next: PlaybackSettings) { savePlaybackSettings(next); setPb(next); }

  return (
    <div className="tv-page tv-settings" ref={ref}>
      <div className="tv-page-head"><h1>{t("settings")}</h1></div>

      <SectionTitle text="Hesap ve Profiller" />
      <div className="tv-set-row">
        <SetButton text="Oynatma Listeleri" onClick={onManagePlaylists} />
        <SetButton text="Profilleri Yönet" onClick={onManageProfiles} />
        <SetButton text="Profil Değiştir" onClick={onChangeProfile} />
        <SetButton text="Tanılama" onClick={onDiagnostics} />
        <SetButton text="İzleme Geçmişi" onClick={onHistory} />
      </div>

      <div className="tv-set-info-card">
        <b>{provider?.name || "—"}</b>
        <span>{provider?.server || ""}</span>
      </div>

      <SectionTitle text={t("language")} />
      <div className="tv-set-row">
        {TV_LANGS.map((l) => (
          <SetButton key={l.code} text={l.label} selected={lang === l.code} onClick={() => setLang(l.code)} />
        ))}
      </div>

      <SectionTitle text="Player Görüntüsü" />
      <div className="tv-set-row">
        <SetButton text="Fit" selected={pb.fitMode === "fit"} onClick={() => savePb({ ...pb, fitMode: "fit" })} />
        <SetButton text="Fill" selected={pb.fitMode === "fill"} onClick={() => savePb({ ...pb, fitMode: "fill" })} />
      </div>

      <SectionTitle text="Varsayılan Ses Dili" />
      <div className="tv-set-row">
        {AUDIO_OPTIONS.map((o) => (
          <SetButton key={o.v} text={o.label} selected={pb.audioLanguage === o.v} onClick={() => savePb({ ...pb, audioLanguage: o.v })} />
        ))}
      </div>

      <SectionTitle text="Altyazı" />
      <div className="tv-set-row">
        <SetButton text={pb.subtitlesEnabled ? "Açık" : "Kapalı"} selected={pb.subtitlesEnabled} onClick={() => savePb({ ...pb, subtitlesEnabled: !pb.subtitlesEnabled })} />
        {SUBTITLE_LANGS.map((o) => (
          <SetButton key={o.v} text={o.label} selected={pb.subtitleLanguage === o.v} onClick={() => savePb({ ...pb, subtitlesEnabled: true, subtitleLanguage: o.v })} />
        ))}
      </div>

      <SectionTitle text="Dizi Oynatma" />
      <div className="tv-set-row">
        <SetButton
          text={pb.autoNextEpisode ? "Sonraki Bölüm: Otomatik" : "Sonraki Bölüm: Kapalı"}
          selected={pb.autoNextEpisode}
          onClick={() => savePb({ ...pb, autoNextEpisode: !pb.autoNextEpisode })}
        />
      </div>

      <SectionTitle text="Kütüphane ve Önbellek" />
      <div className="tv-set-row">
        {provider?.method === "xtream" && <SetButton text="Oynatma Listesini Yenile" onClick={onReload} />}
        {/* Native TvContentCache eşdeğeri web portunda ayrı bir bellek-içi katman değil — kütüphaneyi
            yeniden çekmek (onReload) en yakın işlevsel karşılığı. */}
        <SetButton text="Bellek Önbelleğini Temizle" onClick={onReload} />
        <SetButton
          text={historyCleared ? "Temizlendi" : "İzleme Geçmişini Temizle"}
          onClick={() => { clearViewingHistory(); setHistoryCleared(true); setTimeout(() => setHistoryCleared(false), 2000); }}
        />
      </div>

      <SetButton text={t("remove_playlist")} destructive onClick={onLogout} />
    </div>
  );
}
