// StreamLiveX TV — kök bileşen. Native TvRoot akışı: Setup → Profil → Ana ekran (sidebar).
import { useEffect, useState } from "react";
import "./tokens.css";
import "./tv.css";
import { TV_LANGS, isRtl, type TvLang } from "./i18n";
import { useDpad, focusFirst } from "./dpad";
import { Sidebar, type Section } from "./Sidebar";
import { Setup, type Provider } from "./Setup";
import { ProfileSelect, type Profile } from "./ProfileSelect";
import { SectionPage } from "./sections";

const LANG_KEY = "slx-tv-lang";
const PROVIDER_KEY = "slx-tv-provider";

function loadLang(): TvLang {
  const v = (localStorage.getItem(LANG_KEY) || "").toLowerCase();
  return (TV_LANGS.find((l) => l.code === v)?.code) || "tr";
}
function loadProvider(): Provider | null {
  try { return JSON.parse(localStorage.getItem(PROVIDER_KEY) || "null"); } catch { return null; }
}

export default function TvApp() {
  const [lang, setLang] = useState<TvLang>("tr");
  const [provider, setProvider] = useState<Provider | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [section, setSection] = useState<Section>("Home");

  // İlk yükleme
  useEffect(() => {
    setLang(loadLang());
    setProvider(loadProvider());
  }, []);

  // Dil + RTL uygula
  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = isRtl(lang) ? "rtl" : "ltr";
    localStorage.setItem(LANG_KEY, lang);
  }, [lang]);

  // Geri tuşu davranışı: Ana→Profil→Setup
  const onBack = () => {
    if (profile) { setProfile(null); return; }
    if (provider) { /* profilde: çıkış yok, Setup'a dönmeyi engelle */ return; }
  };
  useDpad(onBack);

  // Ekran değişince ilk odak
  useEffect(() => { const id = setTimeout(() => focusFirst(), 60); return () => clearTimeout(id); },
    [provider, profile, section]);

  const langBar = (
    <div className="tv-lang">
      {TV_LANGS.map((l) => (
        <button
          key={l.code}
          className={`tv-focusable${lang === l.code ? " active" : ""}`}
          onClick={() => setLang(l.code)}
        >
          {l.label}
        </button>
      ))}
    </div>
  );

  // 1) Kurulum
  if (!provider) {
    return (
      <>
        {langBar}
        <Setup lang={lang} onComplete={(p) => { localStorage.setItem(PROVIDER_KEY, JSON.stringify(p)); setProvider(p); }} />
      </>
    );
  }

  // 2) Profil seçimi
  if (!profile) {
    return (
      <>
        {langBar}
        <ProfileSelect lang={lang} onSelect={setProfile} />
      </>
    );
  }

  // 3) Ana ekran: sidebar + içerik
  return (
    <div className="tv-shell">
      <Sidebar selected={section} onSelect={setSection} lang={lang} />
      <div className="tv-content">
        <SectionPage section={section} lang={lang} />
      </div>
    </div>
  );
}
