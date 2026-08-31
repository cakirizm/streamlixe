// StreamLiveX TV — Spor (native TvSportsScreen.kt + SportsBroadcasting.kt birebir):
// Liste (lig filtresi + ızgara) → Event Detay (yayın kanalı grupları) → Kaynak Seçici → Oynat.
// 3 kademeli geri: kaynak seçici→kanal grubu, kanal grubu→maç listesi. Maçtan dönünce odak hafızası.
import { useEffect, useMemo, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import { fetchSports, countryFlag, hhmm, type SportsEvent } from "./data";
import type { Library, Media } from "./library";
import { focusFirst } from "./dpad";
import { resolveBroadcastGroups, guessQuality, type BroadcastGroup } from "./sportsBroadcast";
import { recordChannelView } from "./liveProfile";

// Home'daki "Bugünün Sporları" kartından bir maça doğrudan atlamak için basit köprü
// (native onOpenSportsEvent eşdeğeri — TvApp section'ı değiştirir, bu değişken hangi event'in açılacağını taşır).
let pendingEventId: string | null = null;
export function openSportsEvent(id: string) { pendingEventId = id; }
function consumePendingSportsEvent(): string | null {
  const id = pendingEventId; pendingEventId = null; return id;
}

export function SportsCard({ e, onClick }: { e: SportsEvent; onClick?: () => void }) {
  const score = e.homeScore != null || e.awayScore != null;
  return (
    <button className="tv-sports-card tv-focusable" data-event-id={e.id} onClick={onClick}>
      <div className="tv-sports-league">{countryFlag(e.country)} {e.league} • {hhmm(e.startMs)}</div>
      <div className="tv-sports-teams">
        {e.homeBadge ? <img className="logo" src={e.homeBadge} alt="" /> : <span className="logo" />}
        <span className="name">{e.home}</span>
        <span className="vs">{score ? `${e.homeScore ?? "–"} : ${e.awayScore ?? "–"}` : "vs"}</span>
        <span className="name away">{e.away}</span>
        {e.awayBadge ? <img className="logo" src={e.awayBadge} alt="" /> : <span className="logo" />}
      </div>
      <div className="tv-sports-bcast">{(e.broadcasts && e.broadcasts.length) ? "📺 Yayın mevcut" : "Yayın seçeneği bulunamadı"}</div>
    </button>
  );
}

function SourcePicker({ group, onBack, onPlay }: { group: BroadcastGroup; onBack: () => void; onPlay: (ch: Media) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { const id = setTimeout(() => focusFirst(ref.current), 60); return () => clearTimeout(id); }, [group.canonicalName]);
  return (
    <div className="tv-sports-detail" ref={ref}>
      <div className="tv-sports-detail-top">
        <button className="tv-series-back tv-focusable" onClick={onBack}>←  Geri</button>
        <h2>{group.canonicalName}</h2>
      </div>
      <p className="tv-sports-detail-sub">{group.channels.length} gerçek oynatma listesi kaynağı</p>
      <div className="tv-sports-source-list">
        {group.channels.map((ch, i) => (
          <button key={ch.id} className="tv-sports-source-row tv-focusable" onClick={() => onPlay(ch)}>
            <span>Kaynak {i + 1}</span>
            <b>{guessQuality(ch.name)}</b>
          </button>
        ))}
      </div>
    </div>
  );
}

function EventDetail({
  event, library, onBack, onPlay,
}: { event: SportsEvent; library: Library | null; onBack: () => void; onPlay: (ch: Media) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [pickerGroup, setPickerGroup] = useState<BroadcastGroup | null>(null);
  const resolution = useMemo(() => resolveBroadcastGroups(event, library?.live || []), [event, library]);

  useEffect(() => { if (!pickerGroup) { const id = setTimeout(() => focusFirst(ref.current), 60); return () => clearTimeout(id); } }, [pickerGroup]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        const tag = (e.target as HTMLElement)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;
        e.preventDefault(); e.stopPropagation();
        if (pickerGroup) setPickerGroup(null); else onBack();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [pickerGroup, onBack]);

  if (pickerGroup) {
    return <SourcePicker group={pickerGroup} onBack={() => setPickerGroup(null)} onPlay={onPlay} />;
  }

  const score = event.homeScore != null || event.awayScore != null;
  const title = resolution.evidence === "EXACT" ? "Yayın Kanalı" : "Türkiye Yayın Seçenekleri";

  return (
    <div className="tv-sports-detail" ref={ref}>
      <div className="tv-sports-detail-top">
        <button className="tv-series-back tv-focusable" onClick={onBack}>←  Geri</button>
      </div>
      <div className="tv-sports-detail-head">
        <div className="tv-sports-detail-league">{countryFlag(event.country)} {event.league} • {hhmm(event.startMs)}</div>
        <div className="tv-sports-detail-teams">
          <div className="team">
            {event.homeBadge ? <img src={event.homeBadge} alt="" /> : <span className="ph" />}
            <b>{event.home}</b>
          </div>
          <span className="score">{score ? `${event.homeScore ?? "–"} : ${event.awayScore ?? "–"}` : "VS"}</span>
          <div className="team">
            {event.awayBadge ? <img src={event.awayBadge} alt="" /> : <span className="ph" />}
            <b>{event.away}</b>
          </div>
        </div>
        {event.venue && <div className="tv-sports-detail-venue">{event.venue}</div>}
      </div>

      {resolution.groups.length === 0 ? (
        <div className="tv-coming">Bu karşılaşma için doğrulanmış yayın seçeneği bulunamadı. Uygun kanal oynatma listenizde bulunamadı.</div>
      ) : (
        <>
          <h3 className="tv-detail-section-title">{title}</h3>
          {resolution.evidence === "FALLBACK" && <p className="tv-sports-detail-sub">Yayın ağına göre alternatifler</p>}
          <div className="tv-sports-group-list">
            {resolution.groups.map((g) => (
              <button
                key={g.canonicalName} className="tv-sports-group-row tv-focusable"
                onClick={() => (g.channels.length > 1 ? setPickerGroup(g) : onPlay(g.channels[0]))}
              >
                <b>{g.canonicalName}</b>
                <span>{g.channels.length} kaynak</span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

export function SportsScreen({
  lang, library, onOpen,
}: { lang: TvLang; library: Library | null; provider: unknown; onOpen: (m: Media) => void; onDetail?: unknown }) {
  const t = makeT(lang);
  const [events, setEvents] = useState<SportsEvent[] | null>(null);
  const [league, setLeague] = useState<string | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<SportsEvent | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const lastFocusedId = useRef<string>("");

  useEffect(() => { fetchSports().then(setEvents).catch(() => setEvents([])); }, []);
  useEffect(() => {
    if (!events) return;
    const pending = consumePendingSportsEvent();
    if (pending) {
      const ev = events.find((e) => e.id === pending);
      if (ev) { setSelectedEvent(ev); return; }
    }
    const id = setTimeout(() => {
      if (lastFocusedId.current) {
        const el = ref.current?.querySelector<HTMLElement>(`[data-event-id="${CSS.escape(lastFocusedId.current)}"]`);
        if (el) { el.focus({ preventScroll: false }); return; }
      }
      focusFirst(ref.current);
    }, 80);
    return () => clearTimeout(id);
  }, [events]);

  const leagues = useMemo(() => events ? Array.from(new Set(events.map((e) => e.league))) : [], [events]);
  const visible = useMemo(() => !events ? [] : (league ? events.filter((e) => e.league === league) : events), [events, league]);
  const dateStr = new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "long" }).format(new Date());

  function playChannel(ch: Media) {
    recordChannelView(ch);
    onOpen(ch);
  }

  if (selectedEvent) {
    return (
      <EventDetail
        event={selectedEvent}
        library={library}
        onBack={() => { lastFocusedId.current = selectedEvent.id; setSelectedEvent(null); }}
        onPlay={playChannel}
      />
    );
  }

  return (
    <div className="tv-page" ref={ref}>
      <div className="tv-sports-head">
        <h1>{t("todays_sports")}</h1>
        <span>{dateStr}</span>
      </div>
      {events && leagues.length > 0 && (
        <div className="tv-search-tabs">
          <button className={`tv-search-tab tv-league-chip tv-focusable${league === null ? " active" : ""}`} onClick={() => setLeague(null)} onFocus={() => setLeague(null)}>Tüm Ligler</button>
          {leagues.map((l) => (
            <button key={l} className={`tv-search-tab tv-league-chip tv-focusable${league === l ? " active" : ""}`} onClick={() => setLeague(l)} onFocus={() => setLeague(l)}>{l}</button>
          ))}
        </div>
      )}
      {events === null ? <div className="tv-rail-loading">{t("loading")}</div>
        : visible.length === 0 ? <div className="tv-coming">{league ? "Bugün bu spor için veri bulunamadı." : "Bugün desteklenen sporlarda etkinlik bulunamadı."}</div>
        : <div className="tv-sports-grid">
            {visible.map((e) => <SportsCard key={e.id} e={e} onClick={() => setSelectedEvent(e)} />)}
          </div>}
    </div>
  );
}
