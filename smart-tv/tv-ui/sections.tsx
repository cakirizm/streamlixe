// StreamLiveX TV — bölüm ekranları (native TvHomeScreen/LiveTvScreen/TvMoviesScreen tasarımı).
import { useEffect, useMemo, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import type { Section } from "./Sidebar";
import { fetchSports, fetchForYou, fetchGenres, fetchDiscover, countryFlag, hhmm, type SportsEvent, type PosterCard, type Genre } from "./data";
import { groupsOf, cleanCat, resolveSeriesFirstEpisode, type Library, type Media } from "./library";
import type { Provider } from "./Setup";
import { focusFirst } from "./dpad";
import { LiveScreen } from "./LiveScreen";

type Common = { lang: TvLang; library: Library | null; provider: Provider | null; onOpen: (m: Media) => void };

/* ---------------- Home ---------------- */
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
      <div className="tv-sports-bcast">{(e.broadcasts && e.broadcasts.length) ? "📺 Yayın mevcut" : "Yayın seçeneği bulunamadı"}</div>
    </button>
  );
}

function HomeScreen({ lang, library, onOpen }: Common) {
  const t = makeT(lang);
  const [sports, setSports] = useState<SportsEvent[] | null>(null);
  const [forYou, setForYou] = useState<PosterCard[] | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  // Kütüphane varsa "Sizin İçin" yerine gerçek filmleri göster.
  const libMovies = useMemo(() => (library ? library.movies.slice(0, 20) : null), [library]);

  useEffect(() => { fetchSports().then(setSports).catch(() => setSports([])); }, []);
  useEffect(() => { if (!libMovies) fetchForYou("movie").then(setForYou).catch(() => setForYou([])); }, [libMovies]);
  useEffect(() => { const id = setTimeout(() => focusFirst(ref.current), 80); return () => clearTimeout(id); }, [sports, forYou, libMovies]);

  return (
    <div className="tv-page" ref={ref}>
      <section className="tv-rail">
        <h2 className="tv-rail-title">{t("todays_sports")}</h2>
        {sports === null ? <div className="tv-rail-loading">{t("loading")}</div>
          : sports.length === 0 ? <div className="tv-rail-loading">{t("no_matches")}</div>
          : <div className="tv-rail-row">{sports.map((e) => <SportsCard key={e.id} e={e} />)}</div>}
      </section>

      <section className="tv-rail">
        <h2 className="tv-rail-title">{t("for_you")}</h2>
        {libMovies ? (
          <div className="tv-rail-row">
            {libMovies.map((m) => (
              <button key={m.id} className="tv-poster-card tv-focusable" title={m.name} onClick={() => onOpen(m)}>
                {m.logo ? <img src={m.logo} alt={m.name} loading="lazy" /> : <span className="tv-poster-ph">{m.name.slice(0, 2)}</span>}
              </button>
            ))}
          </div>
        ) : forYou === null ? <div className="tv-rail-loading">{t("loading")}</div>
          : <div className="tv-rail-row">{forYou.map((p) => (
              <button key={p.id} className="tv-poster-card tv-focusable" title={p.title}><img src={p.poster} alt={p.title} loading="lazy" /></button>
            ))}</div>}
      </section>
    </div>
  );
}

/* ---------------- Filmler / Diziler ---------------- */
function ContentScreen({ kind, lang, library, provider, onOpen }: Common & { kind: "movie" | "series" }) {
  const t = makeT(lang);
  const libItems = kind === "movie" ? library?.movies : library?.series;
  const useLib = !!(libItems && libItems.length);

  // Kütüphane modu
  const [cat, setCat] = useState("Tümü");
  const cats = useMemo(() => useLib ? groupsOf(libItems!) : [], [useLib, libItems]);
  const libList = useMemo(() => !useLib ? [] : (cat === "Tümü" ? libItems! : libItems!.filter((m) => m.group === cat)).slice(0, 400), [useLib, libItems, cat]);

  // TMDB modu (demo)
  const [genres, setGenres] = useState<Genre[] | null>(null);
  const [genre, setGenre] = useState<number | null>(null);
  const [cards, setCards] = useState<PosterCard[] | null>(null);
  const catRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (useLib) return;
    fetchGenres(kind).then((g) => { setGenres(g); if (g.length) setGenre(g[0].id); });
  }, [kind, useLib]);
  useEffect(() => { if (useLib || genre == null) return; setCards(null); fetchDiscover(kind, genre).then(setCards); }, [kind, genre, useLib]);
  useEffect(() => { const id = setTimeout(() => focusFirst(catRef.current), 80); return () => clearTimeout(id); }, [genres, library]);

  async function openMedia(m: Media) {
    if (kind === "movie") { onOpen(m); return; }
    if (!provider) return;
    const ep = await resolveSeriesFirstEpisode(provider, m);
    if (ep) onOpen(ep);
  }

  return (
    <div className="tv-content-screen">
      <aside className="tv-cat-col" ref={catRef}>
        <h1 className="tv-cat-title">{t(kind === "movie" ? "movies" : "series")}</h1>
        {useLib ? (
          cats.map((c) => <button key={c} className={`tv-cat tv-focusable${cat === c ? " active" : ""}`} onClick={() => setCat(c)} onFocus={() => setCat(c)}>{c === "Tümü" ? c : cleanCat(c)}</button>)
        ) : genres === null ? <div className="tv-rail-loading">{t("loading")}</div>
          : genres.map((g) => <button key={g.id} className={`tv-cat tv-focusable${genre === g.id ? " active" : ""}`} onClick={() => setGenre(g.id)} onFocus={() => setGenre(g.id)}>{g.name}</button>)}
      </aside>
      <div className="tv-grid-wrap">
        {useLib ? (
          <div className="tv-grid">
            {libList.map((m) => (
              <button key={m.id} className="tv-poster-card tv-focusable" title={m.name} onClick={() => openMedia(m)}>
                {m.logo ? <img src={m.logo} alt={m.name} loading="lazy" /> : <span className="tv-poster-ph">{m.name.slice(0, 2)}</span>}
              </button>
            ))}
          </div>
        ) : cards === null ? <div className="tv-rail-loading">{t("loading")}</div>
          : <div className="tv-grid">{cards.map((c) => (
              <button key={c.id} className="tv-poster-card tv-focusable" title={c.title}><img src={c.poster} alt={c.title} loading="lazy" /></button>
            ))}</div>}
      </div>
    </div>
  );
}

/* ---------------- Ara (Search) ---------------- */
type SearchFilter = "all" | "movie" | "series" | "live";
function SearchScreen({ lang, library, provider, onOpen }: Common) {
  const t = makeT(lang);
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<SearchFilter>("all");
  const inputRef = useRef<HTMLInputElement>(null);
  const all = useMemo(() => library ? [...library.live, ...library.movies, ...library.series] : [], [library]);

  // Sorguya uyan tüm sonuçlar (sayımlar için) ve filtreye göre gösterilenler.
  const matched = useMemo(() => {
    const query = q.trim().toLocaleLowerCase("tr");
    if (query.length < 2) return [] as Media[];
    return all.filter((m) => m.name.toLocaleLowerCase("tr").includes(query));
  }, [all, q]);
  const counts = useMemo(() => ({
    all: matched.length,
    live: matched.filter((m) => m.kind === "live").length,
    movie: matched.filter((m) => m.kind === "movie").length,
    series: matched.filter((m) => m.kind === "series").length,
  }), [matched]);
  const results = useMemo(() => (filter === "all" ? matched : matched.filter((m) => m.kind === filter)).slice(0, 80), [matched, filter]);

  useEffect(() => { const id = setTimeout(() => inputRef.current?.focus(), 80); return () => clearTimeout(id); }, []);

  async function open(m: Media) {
    if (m.kind === "series") { if (provider) { const ep = await resolveSeriesFirstEpisode(provider, m); if (ep) onOpen(ep); } }
    else onOpen(m);
  }
  const kindTag = (k: Media["kind"]) => k === "live" ? "CANLI" : k === "movie" ? "FİLM" : "DİZİ";

  const showCount = q.trim().length >= 2;
  const tabs: { id: SearchFilter; base: string; n: number }[] = [
    { id: "all", base: "Tümü", n: counts.all },
    { id: "movie", base: "Filmler", n: counts.movie },
    { id: "series", base: "Diziler", n: counts.series },
    { id: "live", base: "Kanallar", n: counts.live },
  ];

  return (
    <div className="tv-page">
      <div className="tv-page-head"><h1>{t("search")}</h1></div>

      {/* Önce tür seç, sonra yaz — sekmeler yazma kutusunun tam üstünde ve her zaman görünür */}
      {library && (
        <div className="tv-search-tabs">
          {tabs.map((tab) => (
            <button key={tab.id} className={`tv-search-tab tv-focusable${filter === tab.id ? " active" : ""}`} onClick={() => setFilter(tab.id)} onFocus={() => setFilter(tab.id)}>
              {tab.base}{showCount ? ` (${tab.n})` : ""}
            </button>
          ))}
        </div>
      )}

      <input ref={inputRef} className="tv-search-input tv-focusable" placeholder={filter === "all" ? "Kanal, film veya dizi ara…" : filter === "movie" ? "Film ara…" : filter === "series" ? "Dizi ara…" : "Kanal ara…"} value={q} onChange={(e) => setQ(e.target.value)} />

      {!library ? <div className="tv-coming">Arama için Xtream hesabınla giriş yap.</div>
        : q.trim().length < 2 ? <div className="tv-rail-loading">Aramak için en az 2 harf yaz…</div>
        : results.length === 0 ? <div className="tv-rail-loading">Sonuç bulunamadı.</div>
        : (
          <div className="tv-grid" style={{ marginTop: 20 }}>
            {results.map((m) => (
              <button key={m.id} className="tv-poster-card tv-focusable" title={m.name} onClick={() => open(m)}>
                {m.logo ? <img src={m.logo} alt={m.name} loading="lazy" /> : <span className="tv-poster-ph">{m.name.slice(0, 2)}</span>}
                <span className="tv-kind-tag">{kindTag(m.kind)}</span>
              </button>
            ))}
          </div>
        )}
    </div>
  );
}

/* ---------------- Router ---------------- */
export function SectionPage({ section, lang, library, provider, onOpen }: { section: Section } & Common) {
  const t = makeT(lang);
  const titleKey: Record<Section, string> = {
    Home: "home", Live: "live", Sports: "sports", Movies: "movies",
    Series: "series", Search: "search", MyList: "my_list", Settings: "settings",
  };
  if (section === "Home") return <HomeScreen lang={lang} library={library} provider={provider} onOpen={onOpen} />;
  if (section === "Live") return <LiveScreen lang={lang} library={library} provider={provider} onOpen={onOpen} />;
  if (section === "Movies") return <ContentScreen kind="movie" lang={lang} library={library} provider={provider} onOpen={onOpen} />;
  if (section === "Series") return <ContentScreen kind="series" lang={lang} library={library} provider={provider} onOpen={onOpen} />;
  if (section === "Search") return <SearchScreen lang={lang} library={library} provider={provider} onOpen={onOpen} />;
  return (
    <div className="tv-page">
      <div className="tv-page-head"><h1>{t(titleKey[section])}</h1></div>
      <div className="tv-coming">{t("coming_soon")}</div>
    </div>
  );
}
