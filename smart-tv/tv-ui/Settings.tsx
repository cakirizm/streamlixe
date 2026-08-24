// StreamLiveX TV — Ayarlar: dil, aktif liste (yenile/değiştir), profil, hakkında.
import { useEffect, useRef } from "react";
import { TV_LANGS, makeT, type TvLang } from "./i18n";
import type { Provider } from "./Setup";
import { focusFirst } from "./dpad";

export function Settings({ lang, setLang, provider, onChangeProfile, onLogout, onReload }: {
  lang: TvLang; setLang: (l: TvLang) => void; provider: Provider | null;
  onChangeProfile: () => void; onLogout: () => void; onReload: () => void;
}) {
  const t = makeT(lang);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { const id = setTimeout(() => focusFirst(ref.current), 80); return () => clearTimeout(id); }, []);

  const maskedServer = provider?.server ? provider.server.replace(/\/\/([^/]+)/, (_, h) => `//${h}`) : "";

  return (
    <div className="tv-page tv-settings" ref={ref}>
      <div className="tv-page-head"><h1>{t("settings")}</h1></div>

      <section className="tv-set-block">
        <h2>Dil</h2>
        <div className="tv-set-langs">
          {TV_LANGS.map((l) => (
            <button key={l.code} className={`tv-set-lang tv-focusable${lang === l.code ? " active" : ""}`} onClick={() => setLang(l.code)}>{l.label}</button>
          ))}
        </div>
      </section>

      <section className="tv-set-block">
        <h2>Aktif Liste</h2>
        <div className="tv-set-info">
          <div><span>Ad</span><b>{provider?.name || "—"}</b></div>
          <div><span>Yöntem</span><b>{provider?.method === "xtream" ? "Xtream Codes" : provider?.demo ? "Demo" : "Eşleştirme"}</b></div>
          {maskedServer && <div><span>Sunucu</span><b>{maskedServer}</b></div>}
        </div>
        <div className="tv-set-actions">
          {provider?.method === "xtream" && <button className="tv-btn tv-focusable" style={{ maxWidth: 240 }} onClick={onReload}>Kütüphaneyi yenile</button>}
          <button className="tv-btn ghost tv-focusable" style={{ maxWidth: 240 }} onClick={onLogout}>Listeyi değiştir</button>
        </div>
      </section>

      <section className="tv-set-block">
        <h2>Profil</h2>
        <div className="tv-set-actions">
          <button className="tv-btn ghost tv-focusable" style={{ maxWidth: 240 }} onClick={onChangeProfile}>Profil değiştir</button>
        </div>
      </section>

      <section className="tv-set-block">
        <h2>Hakkında</h2>
        <p className="tv-set-about">StreamLiveX · SMART IPTV PLAYER · webOS / Tizen</p>
      </section>
    </div>
  );
}
