// StreamLiveX TV — film+dizi ORTAK detay ekranı (native GenericDetailScreen, TvContentScreens.kt:1603-2427 birebir).
// Her iki türde de postere OK/tıklama burayı açar; oynatma yalnızca buradaki "Oynat" ile başlar.
import { useEffect, useMemo, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import type { Media } from "./library";
import { fetchSeriesInfo, episodeStreamUrl, parseSeasonEpisode, type SeriesEpisode } from "./library";
import type { Provider } from "./Setup";
import { focusFirst } from "./dpad";
import { isFav, toggleFav } from "./favorites";
import { isWatched, toggleWatched, progressFor, latestUnfinishedEpisode } from "./history";
import { fetchTmdbDetail, fetchPersonCredits, type TmdbDetail, type TmdbCard } from "./data";

// TMDB-only kart (filmografi/benzer içerik) için oynatılamaz Media taslağı — GenericDetailScreen
// isme göre kendi TMDB detayını zaten çekiyor (fetchTmdbDetail), bu yüzden gerçek meta veri gelir;
// yerel kütüphanede karşılığı yoksa mevcut "not_in_playlist"/"no_description" durumları zaten devreye girer.
function tmdbCardToMedia(c: TmdbCard): Media {
  return { id: `tmdb-${c.kind}-${c.id}`, name: c.title, logo: c.poster, group: "", kind: c.kind, url: "" };
}

export function GenericDetailScreen({ media, provider, lang, onOpen, onDetail, onClose }: {
  media: Media; provider: Provider | null; lang: TvLang; onOpen: (m: Media) => void; onDetail: (m: Media) => void; onClose: () => void;
}) {
  const t = makeT(lang);
  const isSeries = media.kind === "series";
  const [tmdb, setTmdb] = useState<TmdbDetail | null | undefined>(undefined);
  const [episodes, setEpisodes] = useState<Record<string, SeriesEpisode[]> | null>(null);
  const [season, setSeason] = useState("");
  const [seriesError, setSeriesError] = useState("");
  const [fav, setFav] = useState(() => isFav(media.id));
  const [personId, setPersonId] = useState<number | null>(null);
  const [personName, setPersonName] = useState("");
  const [personCredits, setPersonCredits] = useState<TmdbCard[] | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const episodesRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setTmdb(undefined);
    fetchTmdbDetail(isSeries ? "series" : "movie", media.name).then(setTmdb);
  }, [media.id, media.name, isSeries]);

  useEffect(() => {
    if (!isSeries) return;
    if (!provider || provider.method !== "xtream" || !media.seriesId) { setSeriesError(t("no_description")); return; }
    fetchSeriesInfo(provider, media.seriesId).then((r) => {
      if (!r) { setSeriesError(t("no_description")); return; }
      setEpisodes(r.episodes);
      const keys = Object.keys(r.episodes).sort((a, b) => Number(a) - Number(b));
      setSeason(keys[0] || "");
    });
  }, [isSeries, media.seriesId, provider]);

  useEffect(() => { const id = setTimeout(() => focusFirst(ref.current), 90); return () => clearTimeout(id); }, [tmdb, episodes, personId]);

  // Geri: kişi filmografi paneli açıksa önce onu kapat, değilse ekranı kapat.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        const tag = (e.target as HTMLElement)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;
        e.preventDefault(); e.stopPropagation();
        if (personId != null) setPersonId(null); else onClose();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onClose, personId]);

  const seasons = useMemo(() => episodes ? Object.keys(episodes).sort((a, b) => Number(a) - Number(b)) : [], [episodes]);
  const episodeList = useMemo(() => (episodes && season ? episodes[season] : []) || [], [episodes, season]);

  const progress = progressFor(media.id);
  const showProgress = !isSeries && (progress?.positionMs || 0) >= 15_000 && (progress?.durationMs || 0) > 0;

  // Dizi için "devam et" hedefi: bu diziye ait, tamamlanmamış, en son güncellenen bölüm.
  const resumeEntry = isSeries && media.seriesId ? latestUnfinishedEpisode(media.seriesId) : null;
  const resumeParsed = resumeEntry ? parseSeasonEpisode(resumeEntry.name) : null;

  function playEpisode(ep: SeriesEpisode) {
    if (!provider) return;
    const url = episodeStreamUrl(provider, ep);
    onOpen({
      id: `s-ep-${ep.id}`, name: `${media.name} · S${season} B${ep.episode_num}${ep.title && ep.title !== String(ep.episode_num) ? ` · ${ep.title}` : ""}`,
      logo: media.logo, group: media.group, kind: "series", url, seriesId: media.seriesId,
      streamId: ep.id, container: ep.container_extension,
    });
  }

  function openPerson(p: { id: number; name: string }) {
    setPersonId(p.id);
    setPersonName(p.name);
    setPersonCredits(null);
    fetchPersonCredits(p.id).then(setPersonCredits);
  }

  if (personId != null) {
    return (
      <div className="tv-detail" ref={ref}>
        <div className="tv-series-topbar">
          <button className="tv-series-back tv-focusable" onClick={() => setPersonId(null)}>← {t("back")}</button>
        </div>
        <h1 className="tv-detail-person-title">{personName} · {t("people_movies")}</h1>
        {personCredits === null ? <div className="tv-rail-loading">{t("loading")}</div> : (
          <div className="tv-detail-filmography">
            {personCredits.map((c) => (
              <button key={`${c.kind}-${c.id}`} className="tv-poster-card tv-focusable" title={c.title} onClick={() => onDetail(tmdbCardToMedia(c))}>
                {c.poster ? <img src={c.poster} alt={c.title} loading="lazy" /> : <span className="tv-poster-ph">{c.title.slice(0, 2)}</span>}
              </button>
            ))}
          </div>
        )}
      </div>
    );
  }

  const backdrop = tmdb?.backdrop;
  const availability = media.url || media.seriesId ? t("in_playlist") : t("not_in_playlist");
  const minutesWatched = progress ? Math.round(progress.positionMs / 60000) : 0;

  return (
    <div className="tv-detail" ref={ref}>
      {backdrop && (
        <div className="tv-detail-backdrop" style={{ backgroundImage: `url(${backdrop})` }}>
          <div className="tv-detail-gradient-h" />
          <div className="tv-detail-gradient-v" />
        </div>
      )}
      <div className="tv-series-topbar">
        <button className="tv-series-back tv-focusable" onClick={onClose}>← {t("back")}</button>
      </div>

      <div className="tv-series-head tv-detail-head">
        <div className="tv-series-poster">
          {(tmdb?.poster || media.logo) ? <img src={tmdb?.poster || media.logo} alt="" /> : <span>{media.name.slice(0, 2)}</span>}
        </div>
        <div className="tv-series-meta">
          <h1>{tmdb?.title || media.name}</h1>
          <div className="tv-series-sub">
            {(tmdb?.releaseDate || media.year) && <span>{(tmdb?.releaseDate || media.year || "").slice(0, 4)}</span>}
            {(tmdb?.voteAverage || media.rating) && <span>★ {tmdb?.voteAverage ? tmdb.voteAverage.toFixed(1) : media.rating}</span>}
            <span>{availability}</span>
          </div>

          {showProgress && (
            <div className="tv-detail-progress">
              <small>{minutesWatched} dk izlendi · kaldığın yer</small>
              <div className="tv-epg-bar"><i style={{ width: `${Math.min(100, ((progress!.positionMs / progress!.durationMs) * 100))}%` }} /></div>
            </div>
          )}

          {tmdb === undefined ? null : (
            <p className="tv-series-plot">{tmdb?.overview || t("no_description")}</p>
          )}

          <div className="tv-detail-actions">
            {!isSeries ? (
              media.url ? (
                <button className="tv-detail-btn primary tv-focusable" onClick={() => onOpen(media)}>▶ {t("play")}</button>
              ) : (
                <span className="tv-detail-btn disabled">{t("not_in_playlist")}</span>
              )
            ) : resumeEntry && resumeParsed ? (
              <button
                className="tv-detail-btn primary tv-focusable"
                onClick={() => onOpen({
                  id: resumeEntry.id, name: resumeEntry.name, logo: resumeEntry.logo, group: resumeEntry.group,
                  kind: "series", url: resumeEntry.url, seriesId: resumeEntry.seriesId, streamId: resumeEntry.streamId,
                  container: resumeEntry.container,
                })}
              >
                ▶ S{resumeParsed.season} B{resumeParsed.ep} · {Math.round(resumeEntry.positionMs / 60000)} dk'dan devam
              </button>
            ) : (
              <button
                className="tv-detail-btn primary tv-focusable"
                onClick={() => episodesRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })}
              >
                ↓ Bölümler
              </button>
            )}
            <button className="tv-detail-btn tv-focusable" onClick={() => setFav(toggleFav(media))}>
              {fav ? `★ ${t("remove_list")}` : `☆ ${t("add_list")}`}
            </button>
            {tmdb?.trailerKey && (
              <button className="tv-detail-btn tv-focusable" onClick={() => window.open(`https://www.youtube.com/watch?v=${tmdb.trailerKey}`, "_blank", "noopener")}>
                ▶ Fragman
              </button>
            )}
          </div>

          {!!tmdb?.directors?.length && (
            <div className="tv-detail-people">
              <b>{t("director")}</b>
              <div className="tv-detail-people-row">
                {tmdb.directors.map((p) => (
                  <button key={p.id} className="tv-detail-person-chip tv-focusable" onClick={() => openPerson(p)}>
                    {p.profilePath ? <img src={p.profilePath} alt="" /> : <span className="ph">{p.name.slice(0, 2)}</span>}
                    <small>{p.name}</small>
                  </button>
                ))}
              </div>
            </div>
          )}
          {!!tmdb?.cast?.length && (
            <div className="tv-detail-people">
              <b>{t("cast")}</b>
              <div className="tv-detail-people-row">
                {tmdb.cast.map((p) => (
                  <button key={p.id} className="tv-detail-person-chip tv-focusable" onClick={() => openPerson(p)}>
                    {p.profilePath ? <img src={p.profilePath} alt="" /> : <span className="ph">{p.name.slice(0, 2)}</span>}
                    <small>{p.name}</small>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {isSeries && (
        <div ref={episodesRef}>
          <h2 className="tv-detail-section-title">{t("seasons_episodes")}</h2>
          {seriesError ? <div className="tv-coming">{seriesError}</div> : !episodes ? <div className="tv-rail-loading">{t("loading")}</div> : (
            <>
              {seasons.length > 1 && (
                <div className="tv-search-tabs">
                  {seasons.map((s) => (
                    <button key={s} className={`tv-search-tab tv-focusable${season === s ? " active" : ""}`} onClick={() => setSeason(s)} onFocus={() => setSeason(s)}>
                      {t("season")} {s}
                    </button>
                  ))}
                </div>
              )}
              <div className="tv-episodes">
                {episodeList.map((ep) => {
                  const epId = `s-ep-${ep.id}`;
                  const still = ep.info?.movie_image || ep.info?.cover_big || tmdb?.poster || media.logo;
                  const watched = isWatched(epId);
                  const epProgress = progressFor(epId);
                  return (
                    <div key={ep.id} className="tv-episode-row">
                      <button className="tv-episode tv-focusable" onClick={() => playEpisode(ep)}>
                        <div className="tv-episode-still">
                          {still ? <img src={still} alt="" loading="lazy" /> : <span>{ep.episode_num}</span>}
                          <i>▶</i>
                        </div>
                        <div className="tv-episode-meta">
                          <b>{ep.episode_num}. {t("episode")}{ep.title && ep.title !== String(ep.episode_num) ? ` · ${ep.title}` : ""}</b>
                          <small className="tv-episode-sub">
                            {ep.info?.air_date || ep.info?.releasedate || ""}
                            {ep.info?.duration ? ` • ${ep.info.duration}` : ""}
                            {watched ? " • ✓ İzlendi" : ""}
                          </small>
                          {ep.info?.plot && <small>{ep.info.plot}</small>}
                          {epProgress && epProgress.positionMs >= 15_000 && epProgress.durationMs > 0 && (
                            <div className="tv-epg-bar"><i style={{ width: `${Math.min(100, (epProgress.positionMs / epProgress.durationMs) * 100)}%` }} /></div>
                          )}
                        </div>
                      </button>
                      <button
                        className="tv-episode-watch tv-focusable"
                        onClick={() => toggleWatched({ id: epId, name: ep.title || String(ep.episode_num), group: media.group, kind: "series", url: "" })}
                      >
                        {watched ? "✓ İzlendi" : "İzlendi olarak işaretle"}
                      </button>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </div>
      )}

      {!!tmdb?.recommendations?.length && (
        <section className="tv-rail">
          <h2 className="tv-rail-title">{isSeries ? t("similar_series") : t("similar_movies")}</h2>
          <div className="tv-rail-row">
            {tmdb.recommendations.map((c) => (
              <button key={`${c.kind}-${c.id}`} className="tv-poster-card tv-focusable" title={c.title} onClick={() => onDetail(tmdbCardToMedia(c))}>
                {c.poster ? <img src={c.poster} alt={c.title} loading="lazy" /> : <span className="tv-poster-ph">{c.title.slice(0, 2)}</span>}
              </button>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
