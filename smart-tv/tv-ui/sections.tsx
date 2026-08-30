// StreamLiveX TV — bölüm ekranları (native TvHomeScreen/TvMoviesScreen/TvSeriesScreen/TvSearchScreen/TvMyListScreen tasarımı).
import { useEffect, useMemo, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import type { Section } from "./Sidebar";
import {
  fetchSports, fetchTrending, fetchTmdbDetail, countryFlag, hhmm,
  type SportsEvent, type TmdbCard, type TmdbDetail,
} from "./data";
import { groupsOf, cleanCat, type Library, type Media } from "./library";
import type { Provider } from "./Setup";
import { focusFirst } from "./dpad";
import { LiveScreen } from "./LiveScreen";
import { getFavs, isFav, toggleFav } from "./favorites";
import { allHistory, continueWatching, isWatched, progressFor, removeFromHistory, type HistoryEntry } from "./history";
import { isKidsMode, policyAllow } from "./scope";
import { recentSearches, addRecentSearch, clearRecentSearches } from "./recentSearches";
import { SearchLivePlayer } from "./SearchLivePlayer";
import { getLiveFavChannels } from "./liveProfile";
import { SportsCard, SportsScreen, openSportsEvent } from "./Sports";

type Common = { lang: TvLang; library: Library | null; provider: Provider | null; onOpen: (m: Media) => void; onDetail: (m: Media) => void; onOpenSports?: () => void };

/* ---------------- Ortak: yerel kütüphaneyi TMDB trend listesiyle eşleştir ---------------- */
const DIACRITICS_RE = new RegExp("[\\u0300-\\u036f]", "g");
const norm = (s: string) => s.toLowerCase().normalize("NFKD").replace(DIACRITICS_RE, "").replace(/[^a-z0-9]/g, "");

function matchTrendingLocally(trending: TmdbCard[], items: Media[]): { media: Media; tmdb: TmdbCard }[] {
  const byName = new Map(items.map((m) => [norm(m.name), m]));
  const out: { media: Media; tmdb: TmdbCard }[] = [];
  for (const card of trending) {
    const m = byName.get(norm(card.title));
    if (m && policyAllow(m.name, m.group)) out.push({ media: m, tmdb: card });
  }
  return out;
}

// native TvHomeScreen "Sizin İçin": izleme geçmişinden en çok izlenen 4 kategori,
// her birinden 8'er öğe round-robin karıştırılır, izlenmişler hariç, 14'e kırpılır.
function affinityForYou(pool: Media[], topCategories: string[], excludeIds: Set<string>): Media[] {
  if (topCategories.length === 0) return [];
  const byCategory = topCategories.map((cat) => pool.filter((m) => m.group === cat && !excludeIds.has(m.id)));
  const out: Media[] = [];
  const seen = new Set<string>();
  for (let i = 0; i < 8 && out.length < 14; i++) {
    for (const arr of byCategory) {
      const item = arr[i];
      if (item && !seen.has(item.id)) { seen.add(item.id); out.push(item); }
      if (out.length >= 14) break;
    }
  }
  return out;
}

/* ---------------- Home ---------------- */
function HomeHero({ items, onDetail }: { items: { media: Media; tmdb: TmdbCard }[]; onDetail: (m: Media) => void }) {
  const [idx, setIdx] = useState(0);
  useEffect(() => { if (idx >= items.length) setIdx(0); }, [items.length, idx]);
  if (!items.length) return null;
  const cur = items[Math.min(idx, items.length - 1)];

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "ArrowLeft") { e.stopPropagation(); e.preventDefault(); setIdx((i) => (i - 1 + items.length) % items.length); }
    else if (e.key === "ArrowRight") { e.stopPropagation(); e.preventDefault(); setIdx((i) => (i + 1) % items.length); }
    else if (e.key === "Enter") { e.stopPropagation(); e.preventDefault(); onDetail(cur.media); }
  }

  return (
    <div className="tv-hero tv-focusable" tabIndex={0} onKeyDown={onKeyDown} onClick={() => onDetail(cur.media)}
      style={{ backgroundImage: cur.tmdb.backdrop ? `url(${cur.tmdb.backdrop})` : undefined }}>
      <div className="tv-hero-gradient-h" /><div className="tv-hero-gradient-v" />
      <div className="tv-hero-time">{hhmm(Date.now())}</div>
      <div className="tv-hero-info">
        <h2>{cur.tmdb.title}</h2>
        <div className="tv-hero-sub">
          {cur.tmdb.releaseDate && <span>{cur.tmdb.releaseDate.slice(0, 4)}</span>}
          {cur.tmdb.voteAverage != null && <span>★ {cur.tmdb.voteAverage.toFixed(1)}</span>}
        </div>
        {cur.tmdb.overview && <p>{cur.tmdb.overview}</p>}
        <span className="tv-hero-cta">Detayları Gör</span>
      </div>
      <div className="tv-hero-dots">
        {items.map((_, i) => <i key={i} className={i === idx ? "on" : ""} />)}
      </div>
    </div>
  );
}

function ContinueCard({ h, onOpen, onRemoved }: { h: HistoryEntry; onOpen: (m: Media) => void; onRemoved: () => void }) {
  const [menu, setMenu] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pct = h.durationMs > 0 ? Math.min(100, (h.positionMs / h.durationMs) * 100) : 0;
  const media: Media = { id: h.id, name: h.name, logo: h.logo, group: h.group, kind: h.kind, url: h.url, streamId: h.streamId, seriesId: h.seriesId, year: h.year, rating: h.rating, container: h.container };

  function start() { timer.current = setTimeout(() => setMenu(true), 700); }
  function cancelAndActivate() {
    if (timer.current) { clearTimeout(timer.current); timer.current = null; if (!menu) onOpen(media); }
  }

  if (menu) {
    return (
      <div className="tv-continue-card tv-continue-menu">
        <button className="tv-focusable" onClick={() => { toggleFav(media); setMenu(false); }}>Bu listeden kaldır</button>
        <button className="tv-focusable" onClick={() => { removeFromHistory(h.id); setMenu(false); onRemoved(); }}>Oynatma geçmişinden kaldır</button>
        <button className="tv-focusable" onClick={() => setMenu(false)}>Vazgeç</button>
      </div>
    );
  }

  return (
    <button
      className="tv-continue-card tv-focusable" title={h.name}
      onKeyDown={(e) => { if (e.key === "Enter") start(); }}
      onKeyUp={(e) => { if (e.key === "Enter") cancelAndActivate(); }}
      onMouseDown={start} onMouseUp={cancelAndActivate}
    >
      {h.logo ? <img src={h.logo} alt={h.name} loading="lazy" /> : <span className="tv-poster-ph">{h.name.slice(0, 2)}</span>}
      <span className="cap">{h.name}</span>
      <span className="prog"><i style={{ width: `${pct}%` }} /></span>
    </button>
  );
}

function HomeScreen({ lang, library, onOpen, onDetail, onOpenSports }: Common) {
  const t = makeT(lang);
  const [sports, setSports] = useState<SportsEvent[] | null>(null);
  const [popularMovies, setPopularMovies] = useState<{ media: Media; tmdb: TmdbCard }[] | null>(null);
  const [popularSeries, setPopularSeries] = useState<{ media: Media; tmdb: TmdbCard }[] | null>(null);
  const [continueRows, setContinueRows] = useState<HistoryEntry[]>(() => continueWatching(10));
  const ref = useRef<HTMLDivElement>(null);
  const kids = isKidsMode();

  // Native: Home "Canlı Şimdi" = TvLiveProfileStore.favoriteChannelIds (My List'teki genel favoriden AYRI).
  const liveFavorites = useMemo(() => (kids ? [] : getLiveFavChannels()), [kids]);
  const libMovies = useMemo(() => (library ? library.movies.filter((m) => policyAllow(m.name, m.group)) : []), [library]);
  const libSeries = useMemo(() => (library ? library.series.filter((m) => policyAllow(m.name, m.group)) : []), [library]);
  const forYou = useMemo(() => {
    const history = allHistory();
    const freq = new Map<string, number>();
    for (const h of history) freq.set(h.group, (freq.get(h.group) || 0) + 1);
    const topCategories = Array.from(freq.entries()).sort((a, b) => b[1] - a[1]).slice(0, 4).map(([g]) => g);
    const watchedIds = new Set(history.filter((h) => h.watched).map((h) => h.id));
    const affinity = affinityForYou([...libMovies, ...libSeries], topCategories, watchedIds);
    return affinity.length > 0 ? affinity : libMovies.slice(0, 20);
  }, [libMovies, libSeries]);

  useEffect(() => { fetchSports().then(setSports).catch(() => setSports([])); }, []);
  useEffect(() => {
    if (!libMovies.length) { setPopularMovies([]); return; }
    fetchTrending("movie").then((rows) => setPopularMovies(matchTrendingLocally(rows, libMovies)));
  }, [libMovies]);
  useEffect(() => {
    if (!libSeries.length) { setPopularSeries([]); return; }
    fetchTrending("series").then((rows) => setPopularSeries(matchTrendingLocally(rows, libSeries)));
  }, [libSeries]);
  useEffect(() => { const id = setTimeout(() => focusFirst(ref.current), 80); return () => clearTimeout(id); }, [sports, popularMovies, popularSeries, continueRows.length]);

  const featured = useMemo(() => [...(popularSeries || []), ...(popularMovies || [])].slice(0, 5), [popularSeries, popularMovies]);

  return (
    <div className="tv-page tv-home" ref={ref}>
      {featured.length > 0 && <HomeHero items={featured} onDetail={onDetail} />}

      {continueRows.length > 0 && (
        <section className="tv-rail">
          <h2 className="tv-rail-title">{t("continue")}</h2>
          <div className="tv-rail-row">
            {continueRows.map((h) => (
              <ContinueCard key={h.id} h={h} onOpen={h.kind === "series" ? onDetail : onOpen} onRemoved={() => setContinueRows(continueWatching(10))} />
            ))}
          </div>
        </section>
      )}

      {liveFavorites.length > 0 && (
        <section className="tv-rail">
          <h2 className="tv-rail-title">Canlı Şimdi</h2>
          <div className="tv-rail-row">
            {liveFavorites.map((m) => (
              <button key={m.id} className="tv-live-fav-card tv-focusable" title={m.name} onClick={() => onOpen(m)}>
                {m.logo ? <img src={m.logo} alt={m.name} loading="lazy" /> : <span className="tv-poster-ph">{m.name.slice(0, 2)}</span>}
                <span className="badge">● CANLI</span>
              </button>
            ))}
          </div>
        </section>
      )}

      <section className="tv-rail">
        <h2 className="tv-rail-title">{t("todays_sports")}</h2>
        {sports === null ? <div className="tv-rail-loading">{t("loading")}</div>
          : sports.length === 0 ? <div className="tv-rail-loading">{t("no_matches")}</div>
          : <div className="tv-rail-row">{sports.map((e) => (
              <SportsCard key={e.id} e={e} onClick={() => { openSportsEvent(e.id); onOpenSports?.(); }} />
            ))}</div>}
      </section>

      {(popularMovies === null || (popularMovies && popularMovies.length > 0)) && (
        <section className="tv-rail">
          <h2 className="tv-rail-title">{t("trending_movies")}</h2>
          {popularMovies === null ? <div className="tv-rail-loading">{t("loading")}</div>
            : <div className="tv-rail-row">{popularMovies.map(({ media, tmdb }) => (
                <button key={media.id} className="tv-poster-card tv-focusable" title={media.name} onClick={() => onDetail(media)}>
                  {tmdb.poster || media.logo ? <img src={tmdb.poster || media.logo} alt={media.name} loading="lazy" /> : <span className="tv-poster-ph">{media.name.slice(0, 2)}</span>}
                </button>
              ))}</div>}
        </section>
      )}

      {(popularSeries === null || (popularSeries && popularSeries.length > 0)) && (
        <section className="tv-rail">
          <h2 className="tv-rail-title">{t("trending_series")}</h2>
          {popularSeries === null ? <div className="tv-rail-loading">{t("loading")}</div>
            : <div className="tv-rail-row">{popularSeries.map(({ media, tmdb }) => (
                <button key={media.id} className="tv-poster-card tv-focusable" title={media.name} onClick={() => onDetail(media)}>
                  {tmdb.poster || media.logo ? <img src={tmdb.poster || media.logo} alt={media.name} loading="lazy" /> : <span className="tv-poster-ph">{media.name.slice(0, 2)}</span>}
                </button>
              ))}</div>}
        </section>
      )}

      <section className="tv-rail">
        <h2 className="tv-rail-title">{t("for_you")}</h2>
        {!library ? <div className="tv-rail-loading">{t("library_preparing")}</div>
          : forYou.length === 0 ? <div className="tv-rail-loading">{t("library_preparing")}</div>
          : <div className="tv-rail-row">
              {forYou.map((m) => (
                <button key={m.id} className="tv-poster-card tv-focusable" title={m.name} onClick={() => onDetail(m)}>
                  {m.logo ? <img src={m.logo} alt={m.name} loading="lazy" /> : <span className="tv-poster-ph">{m.name.slice(0, 2)}</span>}
                </button>
              ))}
            </div>}
      </section>
    </div>
  );
}

/* ---------------- Filmler / Diziler (native: kategori | grid | önizleme) ---------------- */
// native BrowsePreviewPanel: 180ms gecikmeyle TMDB detayı çeker (poster/yıl/tür/puan/özet),
// iki buton — "Detaya Git" (primary) + favori toggle.
function BrowsePreviewPanel({ media, kind, onOpen }: { media: Media | null; kind: "movie" | "series"; onOpen: (m: Media) => void }) {
  const [fav, setFav] = useState(() => (media ? isFav(media.id) : false));
  const [tmdb, setTmdb] = useState<TmdbDetail | null>(null);
  useEffect(() => { setFav(media ? isFav(media.id) : false); }, [media?.id]);
  useEffect(() => {
    setTmdb(null);
    if (!media) return;
    const id = setTimeout(() => { fetchTmdbDetail(kind, media.name).then(setTmdb); }, 180);
    return () => clearTimeout(id);
  }, [media?.id, media?.name, kind]);

  if (!media) return <aside className="tv-browse-preview" />;
  const metadata = [
    (tmdb?.releaseDate || media.year || "").slice(0, 4),
    tmdb?.genres?.slice(0, 2).join(" • "),
    tmdb?.voteAverage != null ? `★ ${tmdb.voteAverage.toFixed(1)}` : (media.rating ? `★ ${media.rating}` : undefined),
  ].filter(Boolean).join(" • ");

  return (
    <aside className="tv-browse-preview">
      <div className="tv-browse-preview-poster">
        {(tmdb?.poster || media.logo) ? <img src={tmdb?.poster || media.logo} alt="" /> : <span>{media.name.slice(0, 2)}</span>}
      </div>
      <b className="tv-browse-preview-title">{tmdb?.title || media.name}</b>
      {metadata && <small className="tv-browse-preview-meta">{metadata}</small>}
      {tmdb?.overview && <p className="tv-browse-preview-overview">{tmdb.overview}</p>}
      <button className="tv-detail-btn primary tv-focusable" onClick={() => onOpen(media)}>Detaya Git</button>
      <button className="tv-detail-btn tv-focusable" onClick={() => setFav(toggleFav(media))}>
        {fav ? "✓  Listemde" : "♡  Listeme Ekle"}
      </button>
    </aside>
  );
}

const focusMemory = new Map<string, string>(); // kategori -> son odaklı içerik id (native seriesFocusedByCategory eşdeğeri)

function ContentScreen({ kind, lang, library, provider, onOpen, onDetail }: Common & { kind: "movie" | "series" }) {
  const t = makeT(lang);
  void provider; void onOpen;
  const libItems = useMemo(() => {
    const raw = kind === "movie" ? library?.movies : library?.series;
    return (raw || []).filter((m) => policyAllow(m.name, m.group));
  }, [kind, library]);

  const [cat, setCat] = useState("Tümü");
  const cats = useMemo(() => groupsOf(libItems), [libItems]);
  const list = useMemo(() => (cat === "Tümü" ? libItems : libItems.filter((m) => m.group === cat)), [libItems, cat]);
  const [preview, setPreview] = useState<Media | null>(null);
  const [catFocused, setCatFocused] = useState(true);
  const catRef = useRef<HTMLDivElement>(null);
  const memoryKey = `${kind}:${cat}`;

  useEffect(() => {
    setPreview(list[0] || null);
    const id = setTimeout(() => {
      const remembered = focusMemory.get(memoryKey);
      const root = catRef.current?.parentElement;
      if (remembered && root) {
        const el = root.querySelector<HTMLElement>(`[data-media-id="${CSS.escape(remembered)}"]`);
        if (el) { el.focus({ preventScroll: false }); return; }
      }
      focusFirst(catRef.current);
    }, 80);
    return () => clearTimeout(id);
  }, [cat, library]);

  function openMedia(m: Media) {
    focusMemory.set(memoryKey, m.id);
    onDetail(m);
  }

  return (
    <div className="tv-content-screen">
      <aside
        className={`tv-cat-col${catFocused ? " expanded" : ""}`}
        ref={catRef}
        onFocusCapture={() => setCatFocused(true)}
        onBlurCapture={(e) => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setCatFocused(false); }}
      >
        <h1 className="tv-cat-title">{t(kind === "movie" ? "movies" : "series")}</h1>
        {cats.length === 0 ? <div className="tv-rail-loading">{t("loading")}</div> : cats.map((c) => (
          <button key={c} className={`tv-cat tv-focusable${cat === c ? " active" : ""}`} onClick={() => setCat(c)} onFocus={() => setCat(c)}>
            {c === "Tümü" ? c : cleanCat(c)}
          </button>
        ))}
      </aside>
      <div className="tv-grid-wrap">
        {!library ? <div className="tv-rail-loading">{t("loading")}</div>
          : list.length === 0 ? <div className="tv-coming">{t("empty")}</div>
          : (
            <div className="tv-grid tv-grid-fixed4">
              {list.map((m) => (
                <button
                  key={m.id} data-media-id={m.id} className="tv-poster-card tv-focusable" title={m.name}
                  onClick={() => openMedia(m)} onFocus={() => setPreview(m)}
                >
                  {m.logo ? <img src={m.logo} alt={m.name} loading="lazy" /> : <span className="tv-poster-ph">{m.name.slice(0, 2)}</span>}
                </button>
              ))}
            </div>
          )}
      </div>
      <BrowsePreviewPanel media={preview} kind={kind} onOpen={openMedia} />
    </div>
  );
}

/* ---------------- Ara (Search) — native: Filmler/Diziler/Kanallar (varsayılan Filmler), "Tümü" YOK ---------------- */
type SearchFilter = "movie" | "series" | "live";
function SearchScreen({ lang, library, onDetail }: Common) {
  const t = makeT(lang);
  const [qRaw, setQRaw] = useState("");
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<SearchFilter>("movie");
  const [recents, setRecents] = useState<string[]>(() => recentSearches());
  const [liveChannel, setLiveChannel] = useState<Media | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Native: 180ms debounce.
  useEffect(() => {
    const id = setTimeout(() => setQ(qRaw), 180);
    return () => clearTimeout(id);
  }, [qRaw]);

  const source = useMemo(() => {
    if (!library) return [] as Media[];
    if (filter === "live") return library.live;
    if (filter === "series") return library.series;
    return library.movies;
  }, [library, filter]);

  const results = useMemo(() => {
    const query = q.trim().toLocaleLowerCase("tr");
    if (query.length < 2) return [] as Media[];
    return source.filter((m) => m.name.toLocaleLowerCase("tr").includes(query) && policyAllow(m.name, m.group)).slice(0, 80);
  }, [source, q]);

  useEffect(() => { const id = setTimeout(() => inputRef.current?.focus(), 80); return () => clearTimeout(id); }, []);

  function open(m: Media) {
    addRecentSearch(qRaw);
    setRecents(recentSearches());
    if (m.kind === "live") setLiveChannel(m);
    else onDetail(m);
  }

  if (liveChannel) return <SearchLivePlayer channel={liveChannel} onClose={() => setLiveChannel(null)} />;

  const tabs: { id: SearchFilter; label: string }[] = [
    { id: "movie", label: "Filmler" }, { id: "series", label: "Diziler" }, { id: "live", label: "Kanallar" },
  ];

  return (
    <div className="tv-page">
      <div className="tv-page-head"><h1>{t("search")}</h1></div>

      {library && (
        <div className="tv-search-tabs">
          {tabs.map((tab) => (
            <button key={tab.id} className={`tv-search-tab tv-focusable${filter === tab.id ? " active" : ""}`} onClick={() => setFilter(tab.id)} onFocus={() => setFilter(tab.id)}>
              {tab.label}
            </button>
          ))}
        </div>
      )}

      {qRaw.trim().length === 0 && recents.length > 0 && (
        <div className="tv-search-recent">
          {recents.map((r) => (
            <button key={r} className="tv-search-tab tv-focusable" onClick={() => setQRaw(r)}>{r}</button>
          ))}
          <button className="tv-search-tab tv-focusable" onClick={() => { clearRecentSearches(); setRecents([]); }}>Son Aramaları Temizle</button>
        </div>
      )}

      <input ref={inputRef} className="tv-search-input tv-focusable" placeholder={t("search_hint")} value={qRaw} onChange={(e) => setQRaw(e.target.value)} />

      {!library ? <div className="tv-coming">Arama için Xtream hesabınla giriş yap.</div>
        : q.trim().length < 2 ? null
        : results.length === 0 ? <div className="tv-rail-loading">{t("empty")}</div>
        : (
          <div className="tv-grid tv-grid-fixed4" style={{ marginTop: 20 }}>
            {results.map((m) => (
              <button key={m.id} className="tv-poster-card tv-focusable" title={m.name} onClick={() => open(m)}>
                {m.logo ? <img src={m.logo} alt={m.name} loading="lazy" /> : <span className="tv-poster-ph">{m.name.slice(0, 2)}</span>}
              </button>
            ))}
          </div>
        )}
    </div>
  );
}

/* ---------------- Listem (favoriler) ---------------- */
type MyListFilter = "all" | "movie" | "series";
function MyListScreen({ lang, onOpen, onDetail }: Common) {
  const t = makeT(lang);
  const [favs, setFavs] = useState<Media[]>(() => getFavs().filter((m) => m.kind !== "live"));
  const [filter, setFilter] = useState<MyListFilter>("all");
  const ref = useRef<HTMLDivElement>(null);
  const visible = useMemo(() => (filter === "all" ? favs : favs.filter((m) => m.kind === filter)), [favs, filter]);
  useEffect(() => { if (favs.length) { const id = setTimeout(() => focusFirst(ref.current), 80); return () => clearTimeout(id); } }, [favs.length]);

  function subtitle(m: Media): string {
    if (isWatched(m.id)) return "✓ İzlendi";
    const p = progressFor(m.id);
    if (p && p.positionMs > 0) return "Devam Et";
    return m.kind === "movie" ? t("movies") : t("series");
  }
  function open(m: Media) { if (m.kind === "series") onDetail(m); else onOpen(m); }
  function remove(m: Media) { toggleFav(m); setFavs(getFavs().filter((x) => x.kind !== "live")); }

  return (
    <div className="tv-page" ref={ref}>
      <div className="tv-page-head"><h1>{t("my_list")}</h1></div>
      <div className="tv-search-tabs">
        <button className={`tv-search-tab tv-focusable${filter === "all" ? " active" : ""}`} onClick={() => setFilter("all")}>Tümü</button>
        <button className={`tv-search-tab tv-focusable${filter === "movie" ? " active" : ""}`} onClick={() => setFilter("movie")}>{t("movies")}</button>
        <button className={`tv-search-tab tv-focusable${filter === "series" ? " active" : ""}`} onClick={() => setFilter("series")}>{t("series")}</button>
      </div>
      {visible.length === 0 ? <div className="tv-coming">Listen boş. İçerik oynatırken ★ ile ekleyebilirsin.</div>
        : <div className="tv-grid tv-grid-fixed6">
            {visible.map((m) => (
              <div key={m.id} className="tv-mylist-cell">
                <button className="tv-poster-card tv-focusable" title={m.name} onClick={() => open(m)}>
                  {m.logo ? <img src={m.logo} alt={m.name} loading="lazy" /> : <span className="tv-poster-ph">{m.name.slice(0, 2)}</span>}
                  <span className="tv-kind-tag">{subtitle(m)}</span>
                </button>
                <button className="tv-mylist-remove tv-focusable" onClick={() => remove(m)}>★ Listemden Çıkar</button>
              </div>
            ))}
          </div>}
    </div>
  );
}

/* ---------------- Router ---------------- */
export function SectionPage({ section, lang, library, provider, onOpen, onDetail, onOpenSports }: { section: Section } & Common) {
  const t = makeT(lang);
  const titleKey: Record<Section, string> = {
    Home: "home", Live: "live", Sports: "sports", Movies: "movies",
    Series: "series", Search: "search", MyList: "my_list", Settings: "settings",
  };
  if (section === "Home") return <HomeScreen lang={lang} library={library} provider={provider} onOpen={onOpen} onDetail={onDetail} onOpenSports={onOpenSports} />;
  if (section === "Live") return <LiveScreen lang={lang} library={library} provider={provider} onOpen={onOpen} />;
  if (section === "Movies") return <ContentScreen kind="movie" lang={lang} library={library} provider={provider} onOpen={onOpen} onDetail={onDetail} />;
  if (section === "Series") return <ContentScreen kind="series" lang={lang} library={library} provider={provider} onOpen={onOpen} onDetail={onDetail} />;
  if (section === "Search") return <SearchScreen lang={lang} library={library} provider={provider} onOpen={onOpen} onDetail={onDetail} />;
  if (section === "Sports") return <SportsScreen lang={lang} library={library} provider={provider} onOpen={onOpen} onDetail={onDetail} />;
  if (section === "MyList") return <MyListScreen lang={lang} library={library} provider={provider} onOpen={onOpen} onDetail={onDetail} />;
  return (
    <div className="tv-page">
      <div className="tv-page-head"><h1>{t(titleKey[section])}</h1></div>
      <div className="tv-coming">{t("coming_soon")}</div>
    </div>
  );
}
