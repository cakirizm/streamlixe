// StreamLiveX TV — bölüm ekranları. Faz 1: başlık + placeholder (Home hero iskeleti).
// Faz 2/3'te Home hero+raflar, Movies/Series ızgaraları, Live, Player gerçek veriyle gelir.
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import type { Section } from "./Sidebar";

export function SectionPage({ section, lang }: { section: Section; lang: TvLang }) {
  const t = makeT(lang);
  const titleKey: Record<Section, string> = {
    Home: "home", Live: "live", Sports: "sports", Movies: "movies",
    Series: "series", Search: "search", MyList: "my_list", Settings: "settings",
  };

  if (section === "Home") {
    return (
      <div className="tv-page">
        <div className="tv-page-head">
          <h1>{t("home")}</h1>
          <p>{t("setup_sub")}</p>
        </div>
        {/* Hero iskeleti (Faz 2'de gerçek içerik) */}
        <div style={{
          height: "var(--tv-hero-h)", borderRadius: 18,
          background: "linear-gradient(120deg,#0e2233,#101a22 70%)",
          border: "1px solid var(--tv-card-border)", display: "flex",
          alignItems: "flex-end", padding: 28, marginBottom: 26,
        }}>
          <div>
            <div style={{ fontSize: 12, letterSpacing: 2, color: "var(--tv-primary)", fontWeight: 800 }}>ÖNE ÇIKAN</div>
            <div style={{ fontSize: 30, fontWeight: 800 }}>StreamLiveX</div>
          </div>
        </div>
        {/* Raf iskeletleri */}
        {[0, 1].map((r) => (
          <div key={r} style={{ marginBottom: 26 }}>
            <div style={{ height: 14, width: 160, background: "#12202c", borderRadius: 6, marginBottom: 12 }} />
            <div style={{ display: "flex", gap: 14 }}>
              {Array.from({ length: 7 }).map((_, i) => (
                <div key={i} className="tv-focusable" tabIndex={0} style={{
                  width: 150, height: 96, flex: "none", borderRadius: 12,
                  background: "var(--tv-card)", border: "1px solid var(--tv-card-border)",
                }} />
              ))}
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="tv-page">
      <div className="tv-page-head">
        <h1>{t(titleKey[section])}</h1>
      </div>
      <div className="tv-coming">{t("coming_soon")}</div>
    </div>
  );
}
