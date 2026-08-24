// StreamLiveX TV — bölüm ekranları. Home native TvHomeScreen tasarımına göre:
// Kaldığın Yerden Devam Et → Bugünün Sporları → Sizin İçin (gerçek /api verisiyle).
import { useEffect, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import type { Section } from "./Sidebar";
import { fetchSports, fetchForYou, countryFlag, hhmm, type SportsEvent, type PosterCard } from "./data";
import { focusFirst } from "./dpad";

// Kaldığın Yerden Devam Et — yerel geçmiş (henüz kütüphane yoksa boş → gizli).
type ContinueItem = { id: string; name: string; image?: string; progress?: number };
function loadContinue(): ContinueItem[] {
  try { return JSON.parse(localStorage.getItem("slx-tv-continue") || "[]"); } catch { return []; }
}

function SportsCard({ e }: { e: SportsEvent }) {
  const score = e.homeScore != null || e.awayScore != null;
  return (
    <button className="tv-sports-card tv-focusable">
      <div className="tv-sports-league">{countryFlag(e.country)} {e.league} • {hhmm(e.startMs)}</div>
      <div className="tv-sports-teams">
        {e.homeBadge ? <img className="logo" src={e.homeBadge} alt="" /> : <span className="logo" />}
        <span className="name">{e.home}</span>
        <span className="vs">{score ? `${e.homeScore ?? "–"}:${e.awayScore ?? "–"}` : "vs"}</span>
        <span className="name away">{e.away}</span>
        {e.awayBadge ? <img className="logo" src={e.awayBadge} alt="" /> : <span className="logo" />}
      </div>
      <div className="tv-sports-bcast">
        {(e.broadcasts && e.broadcasts.length) ? "📺 Yayın mevcut" : "Yayın seçeneği bulunamadı"}
      </div>
    </button>
  );
}

function HomeScreen({ lang }: { lang: TvLang }) {
  const t = makeT(lang);
  const [sports, setSports] = useState<SportsEvent[] | null>(null);
  const [forYou, setForYou] = useState<PosterCard[] | null>(null);
  const [cont] = useState<ContinueItem[]>(loadContinue());
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchSports().then(setSports).catch(() => setSports([]));
    fetchForYou("movie").then(setForYou).catch(() => setForYou([]));
  }, []);

  useEffect(() => {
    if (sports !== null || forYou !== null) {
      const id = setTimeout(() => focusFirst(ref.current), 80);
      return () => clearTimeout(id);
    }
  }, [sports, forYou]);

  return (
    <div className="tv-page" ref={ref}>
      {cont.length > 0 && (
        <section className="tv-rail">
          <h2 className="tv-rail-title">{t("continue_watching")}</h2>
          <div className="tv-rail-row">
            {cont.map((c) => (
              <button key={c.id} className="tv-continue-card tv-focusable">
                {c.image && <img src={c.image} alt="" />}
                <div className="cap">{c.name}</div>
                {c.progress != null && <div className="prog"><i style={{ width: `${c.progress}%` }} /></div>}
              </button>
            ))}
          </div>
        </section>
      )}

      <section className="tv-rail">
        <h2 className="tv-rail-title">{t("todays_sports")}</h2>
        {sports === null ? (
          <div className="tv-rail-loading">{t("loading")}</div>
        ) : sports.length === 0 ? (
          <div className="tv-rail-loading">{t("no_matches")}</div>
        ) : (
          <div className="tv-rail-row">
            {sports.map((e) => <SportsCard key={e.id} e={e} />)}
          </div>
        )}
      </section>

      <section className="tv-rail">
        <h2 className="tv-rail-title">{t("for_you")}</h2>
        {forYou === null ? (
          <div className="tv-rail-loading">{t("loading")}</div>
        ) : (
          <div className="tv-rail-row">
            {forYou.map((p) => (
              <button key={p.id} className="tv-poster-card tv-focusable" title={p.title}>
                <img src={p.poster} alt={p.title} loading="lazy" />
              </button>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

export function SectionPage({ section, lang }: { section: Section; lang: TvLang }) {
  const t = makeT(lang);
  const titleKey: Record<Section, string> = {
    Home: "home", Live: "live", Sports: "sports", Movies: "movies",
    Series: "series", Search: "search", MyList: "my_list", Settings: "settings",
  };
  if (section === "Home") return <HomeScreen lang={lang} />;
  return (
    <div className="tv-page">
      <div className="tv-page-head"><h1>{t(titleKey[section])}</h1></div>
      <div className="tv-coming">{t("coming_soon")}</div>
    </div>
  );
}
