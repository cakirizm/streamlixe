// StreamLiveX TV — kök bileşen. Native TvRoot akışı: Setup → (import) → Profil → Ana ekran → Oynatıcı.
import { useEffect, useState } from "react";
import "./tokens.css";
import "./tv.css";
import { TV_LANGS, isRtl, type TvLang } from "./i18n";
import { useDpad, focusFirst } from "./dpad";
import { Sidebar, type Section } from "./Sidebar";
import { Setup, type Provider } from "./Setup";
import { ProfileSelect } from "./ProfileSelect";
import type { Profile } from "./profiles";
import { SectionPage } from "./sections";
import { importXtream, type Library, type Media } from "./library";
import { Player } from "./Player";
import { GenericDetailScreen } from "./GenericDetailScreen";
import { Settings } from "./Settings";
import { PlaylistManager } from "./PlaylistManager";
import { ProfileManager } from "./ProfileManager";
import { HistoryScreen } from "./HistoryScreen";
import { DiagnosticsScreen } from "./DiagnosticsScreen";
import { activePlaylist, activePlaylistId, addOrUpdatePlaylist, removePlaylist } from "./playlists";
import { activateScope } from "./scope";
import { BrandFull } from "./Brand";

const LANG_KEY = "slx-tv-lang";

function loadLang(): TvLang {
  const v = (localStorage.getItem(LANG_KEY) || "").toLowerCase();
  return (TV_LANGS.find((l) => l.code === v)?.code) || "tr";
}
function loadProvider(): Provider | null {
  return activePlaylist()?.provider ?? null;
}

function LoadingScreen() {
  return (
    <main className="tv-loading">
      <div className="tv-loading-brand"><BrandFull size={44} /></div>
      <div className="tv-loading-spin" />
      <h2>Kütüphaneniz yükleniyor</h2>
      <p>Profil ve yerel kütüphane hazırlanıyor…</p>
    </main>
  );
}

// native TvRoot.kt settingsRoute: "playlists" | "profiles" | "history" | "diagnostics" | null.
type SettingsRoute = "playlists" | "profiles" | "history" | "diagnostics" | null;

export default function TvApp() {
  const [lang, setLang] = useState<TvLang>("tr");
  const [provider, setProvider] = useState<Provider | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [section, setSection] = useState<Section>("Home");
  const [settingsRoute, setSettingsRoute] = useState<SettingsRoute>(null);
  const [library, setLibrary] = useState<Library | null>(null);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState("");
  const [playing, setPlaying] = useState<Media | null>(null);
  const [detail, setDetail] = useState<Media | null>(null);
  // native TvMainScreen: menuExpanded — içerik odak alınca daralır, menüye dönünce genişler.
  const [menuExpanded, setMenuExpanded] = useState(true);

  useEffect(() => { setLang(loadLang()); setProvider(loadProvider()); }, []);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = isRtl(lang) ? "rtl" : "ltr";
    localStorage.setItem(LANG_KEY, lang);
  }, [lang]);

  // Xtream sağlayıcı için gerçek kütüphaneyi çek.
  useEffect(() => {
    if (!provider || provider.method !== "xtream" || library || importing) return;
    setImporting(true); setImportError("");
    importXtream(provider)
      .then(setLibrary)
      .catch((e) => setImportError(e instanceof Error ? e.message : "İçe aktarma başarısız"))
      .finally(() => setImporting(false));
  }, [provider, library, importing]);

  const onBack = () => { if (profile) setProfile(null); };
  useDpad(playing || detail || settingsRoute ? undefined : onBack);

  useEffect(() => { const id = setTimeout(() => focusFirst(), 60); return () => clearTimeout(id); },
    [provider, profile, section, settingsRoute, library, playing, detail]);

  function selectProfile(p: Profile) {
    activateScope(activePlaylistId(), p.id, p.isKids);
    setProfile(p);
  }

  function switchProvider(p: Provider) {
    // native onProviderSelected: kütüphane/önbellek temizlenir, profil yeniden seçilir.
    setLibrary(null);
    setProvider(p);
    setProfile(null);
    setSettingsRoute(null);
  }

  const langBar = (
    <div className="tv-lang">
      {TV_LANGS.map((l) => (
        <button key={l.code} className={`tv-focusable${lang === l.code ? " active" : ""}`} onClick={() => setLang(l.code)}>{l.label}</button>
      ))}
    </div>
  );

  if (!provider) {
    return (<>{langBar}<Setup lang={lang} onComplete={(p) => { addOrUpdatePlaylist(p, true); setLibrary(null); setProvider(p); }} /></>);
  }

  // Xtream import sürüyor / hata
  if (importing) return <LoadingScreen />;
  if (importError) {
    return (
      <main className="tv-loading">
        <h2>Kütüphane yüklenemedi</h2>
        <p style={{ maxWidth: 560, textAlign: "center" }}>{importError}</p>
        <button className="tv-btn tv-focusable" style={{ maxWidth: 260 }} onClick={() => { setProvider(null); setImportError(""); }}>
          Kuruluma dön
        </button>
      </main>
    );
  }

  if (!profile) return (<>{langBar}<ProfileSelect lang={lang} onSelect={selectProfile} /></>);

  if (playing) return <Player media={playing} provider={provider} lang={lang} onClose={() => setPlaying(null)} onAdvance={setPlaying} />;
  if (detail) return <GenericDetailScreen media={detail} provider={provider} lang={lang} onOpen={(m) => { setDetail(null); setPlaying(m); }} onDetail={setDetail} onClose={() => setDetail(null)} />;

  return (
    <div className="tv-shell">
      <Sidebar
        selected={section}
        onSelect={(s) => { setSection(s); if (s !== "Settings") setSettingsRoute(null); }}
        lang={lang}
        expanded={menuExpanded}
        onMenuFocused={() => setMenuExpanded(true)}
      />
      <div className="tv-content" onFocusCapture={() => setMenuExpanded(false)}>
        {section === "Settings" ? (
          settingsRoute === "playlists" ? (
            <PlaylistManager lang={lang} onBack={() => setSettingsRoute(null)} onSelected={switchProvider} />
          ) : settingsRoute === "profiles" ? (
            <ProfileManager onBack={() => setSettingsRoute(null)} />
          ) : settingsRoute === "history" ? (
            <HistoryScreen onBack={() => setSettingsRoute(null)} onOpen={(m) => setPlaying(m)} />
          ) : settingsRoute === "diagnostics" ? (
            <DiagnosticsScreen provider={provider} library={library} onBack={() => setSettingsRoute(null)} />
          ) : (
            <Settings
              lang={lang} setLang={setLang} provider={provider}
              onChangeProfile={() => setProfile(null)}
              onLogout={() => {
                // native onDisconnect: aktif listeyi kayıttan kaldırır, kalan varsa ona geçer.
                const id = activePlaylistId();
                const next = id ? removePlaylist(id) : null;
                setLibrary(null); setProfile(null); setProvider(next?.provider ?? null);
              }}
              onReload={() => setLibrary(null)}
              onManagePlaylists={() => setSettingsRoute("playlists")}
              onManageProfiles={() => setSettingsRoute("profiles")}
              onHistory={() => setSettingsRoute("history")}
              onDiagnostics={() => setSettingsRoute("diagnostics")}
            />
          )
        ) : (
          <SectionPage section={section} lang={lang} library={library} provider={provider} onOpen={setPlaying} onDetail={setDetail} onOpenSports={() => setSection("Sports")} />
        )}
      </div>
    </div>
  );
}
