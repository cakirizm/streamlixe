// StreamLiveX TV — bölüm ekranları. Home native TvHomeScreen tasarımına göre:
// Kaldığın Yerden Devam Et → Bugünün Sporları → Sizin İçin (gerçek /api verisiyle).
import { useEffect, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import type { Section } from "./Sidebar";
import { fetchSports, fetchForYou, fetchGenres, fetchDiscover, countryFlag, hhmm, type SportsEvent, type PosterCard, type Genre } from "./data";
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

// Filmler / Diziler — native TvMoviesScreen/TvSeriesScreen: kategori sütunu + poster ızgarası.
// Demo/kütüphane yokken TMDB tür + keşif ile doldurulur (görünüş native ile aynı).
function ContentScreen({ kind, lang }: { kind: "movie" | "series"; lang: TvLang }) {
  const t = makeT(lang);
  const [genres, setGenres] = useState<Genre[] | null>(null);
  const [genre, setGenre] = useState<number | null>(null);
  const [cards, setCards] = useState<PosterCard[] | null>(null);
  const catRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchGenres(kind).then((g) => {
      setGenres(g);
      if (g.length) setGenre(g[0].id);
    });
  }, [kind]);

  useEffect(() => {
    if (genre == null) return;
    setCards(null);
    fetchDiscover(kind, genre).then(setCards);
  }, [kind, genre]);

  useEffect(() => {
    if (genres && genres.length) { const id = setTimeout(() => focusFirst(catRef.current), 80); return () => clearTimeout(id); }
  }, [genres]);

  return (
    <div className="tv-content-screen">
      <aside className="tv-cat-col" ref={catRef}>
        <h1 className="tv-cat-title">{t(kind === "movie" ? "movies" : "series")}</h1>
        {genres === null ? (
          <div className="tv-rail-loading">{t("loading")}</div>
        ) : (
          genres.map((g) => (
            <button
              key={g.id}
              className={`tv-cat tv-focusable${genre === g.id ? " active" : ""}`}
              onClick={() => setGenre(g.id)}
              onFocus={() => setGenre(g.id)}
            >
              {g.name}
            </button>
          ))
        )}
      </aside>
      <div className="tv-grid-wrap">
        {cards === null ? (
          <div className="tv-rail-loading">{t("loading")}</div>
        ) : (
          <div className="tv-grid">
            {cards.map((c) => (
              <button key={c.id} className="tv-poster-card tv-focusable" title={c.title}>
                <img src={c.poster} alt={c.title} loading="lazy" />
              </button>
            ))}
          </div>
        )}
      </div>
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
  if (section === "Movies") return <ContentScreen kind="movie" lang={lang} />;
  if (section === "Series") return <ContentScreen kind="series" lang={lang} />;
  return (
    <div className="tv-page">
      <div className="tv-page-head"><h1>{t(titleKey[section])}</h1></div>
      <div className="tv-coming">{t("coming_soon")}</div>
    </div>
  );
}
