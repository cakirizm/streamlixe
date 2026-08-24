// StreamLiveX TV — kök bileşen. Native TvRoot akışı: Setup → (import) → Profil → Ana ekran → Oynatıcı.
import { useEffect, useState } from "react";
import "./tokens.css";
import "./tv.css";
import { TV_LANGS, isRtl, type TvLang } from "./i18n";
import { useDpad, focusFirst } from "./dpad";
import { Sidebar, type Section } from "./Sidebar";
import { Setup, type Provider } from "./Setup";
import { ProfileSelect, type Profile } from "./ProfileSelect";
import { SectionPage } from "./sections";
import { importXtream, type Library, type Media } from "./library";
import { Player } from "./Player";

const LANG_KEY = "slx-tv-lang";
const PROVIDER_KEY = "slx-tv-provider";

function loadLang(): TvLang {
  const v = (localStorage.getItem(LANG_KEY) || "").toLowerCase();
  return (TV_LANGS.find((l) => l.code === v)?.code) || "tr";
}
function loadProvider(): Provider | null {
  try { return JSON.parse(localStorage.getItem(PROVIDER_KEY) || "null"); } catch { return null; }
}

function LoadingScreen() {
  return (
    <main className="tv-loading">
      <div className="tv-loading-brand">
        <img src="/streamlivex-logo.jpeg" alt="StreamLiveX" />
        <div><b>StreamLive<i>X</i></b><small>SMART IPTV PLAYER</small></div>
      </div>
      <div className="tv-loading-spin" />
      <h2>Kütüphaneniz yükleniyor</h2>
      <p>Profil ve yerel kütüphane hazırlanıyor…</p>
    </main>
  );
}

export default function TvApp() {
  const [lang, setLang] = useState<TvLang>("tr");
  const [provider, setProvider] = useState<Provider | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [section, setSection] = useState<Section>("Home");
  const [library, setLibrary] = useState<Library | null>(null);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState("");
  const [playing, setPlaying] = useState<Media | null>(null);

  useEffect(() => { setLang(loadLang()); setProvider(loadProvider()); }, []);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = isRtl(lang) ? "rtl" : "ltr";
    localStorage.setItem(LANG_KEY, lang);
  }, [lang]);

  // Xtream sağlayıcı için gerçek kütüphaneyi çek.
  useEffect(() => {
    if (!provider || provider.demo || provider.method !== "xtream" || library || importing) return;
    setImporting(true); setImportError("");
    importXtream(provider)
      .then(setLibrary)
      .catch((e) => setImportError(e instanceof Error ? e.message : "İçe aktarma başarısız"))
      .finally(() => setImporting(false));
  }, [provider, library, importing]);

  const onBack = () => { if (profile) setProfile(null); };
  useDpad(playing ? undefined : onBack);

  useEffect(() => { const id = setTimeout(() => focusFirst(), 60); return () => clearTimeout(id); },
    [provider, profile, section, library, playing]);

  const langBar = (
    <div className="tv-lang">
      {TV_LANGS.map((l) => (
        <button key={l.code} className={`tv-focusable${lang === l.code ? " active" : ""}`} onClick={() => setLang(l.code)}>{l.label}</button>
      ))}
    </div>
  );

  if (!provider) {
    return (<>{langBar}<Setup lang={lang} onComplete={(p) => { localStorage.setItem(PROVIDER_KEY, JSON.stringify(p)); setLibrary(null); setProvider(p); }} /></>);
  }

  // Xtream import sürüyor / hata
  if (importing) return <LoadingScreen />;
  if (importError) {
    return (
      <main className="tv-loading">
        <h2>Kütüphane yüklenemedi</h2>
        <p style={{ maxWidth: 560, textAlign: "center" }}>{importError}</p>
        <button className="tv-btn tv-focusable" style={{ maxWidth: 260 }} onClick={() => { localStorage.removeItem(PROVIDER_KEY); setProvider(null); setImportError(""); }}>
          Kuruluma dön
        </button>
      </main>
    );
  }

  if (!profile) return (<>{langBar}<ProfileSelect lang={lang} onSelect={setProfile} /></>);

  if (playing) return <Player media={playing} onClose={() => setPlaying(null)} />;

  return (
    <div className="tv-shell">
      <Sidebar selected={section} onSelect={setSection} lang={lang} />
      <div className="tv-content">
        <SectionPage section={section} lang={lang} library={library} provider={provider} onOpen={setPlaying} />
      </div>
    </div>
  );
}
