// StreamLiveX TV — sol menü (native TvSideMenu birebir: ikon+etiket, odakta genişler)
import { useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";

export type Section = "Home" | "Live" | "Sports" | "Movies" | "Series" | "Search" | "MyList" | "Settings";

// History native'de sidebar'dan gizli; sıralama native TvSection ile aynı.
const ITEMS: { id: Section; ico: string; key: string }[] = [
  { id: "Home", ico: "⌂", key: "home" },
  { id: "Live", ico: "●", key: "live" },
  { id: "Sports", ico: "⚽", key: "sports" },
  { id: "Movies", ico: "▶", key: "movies" },
  { id: "Series", ico: "▤", key: "series" },
  { id: "Search", ico: "⌕", key: "search" },
  { id: "MyList", ico: "★", key: "my_list" },
  { id: "Settings", ico: "⚙", key: "settings" },
];

export function Sidebar({
  selected, onSelect, lang,
}: {
  selected: Section;
  onSelect: (s: Section) => void;
  lang: TvLang;
}) {
  const t = makeT(lang);
  const [expanded, setExpanded] = useState(false);

  return (
    <nav
      className={`tv-sidebar${expanded ? " expanded" : ""}`}
      onFocusCapture={() => setExpanded(true)}
      onBlurCapture={(e) => {
        // menüden tamamen çıkıldıysa daralt
        if (!e.currentTarget.contains(e.relatedTarget as Node)) setExpanded(false);
      }}
    >
      <div className="tv-brand">
        <img src="/streamlivex-logo.jpeg" alt="StreamLiveX" />
        <b>StreamLive<i>X</i></b>
      </div>
      {ITEMS.map((it) => (
        <button
          key={it.id}
          className={`tv-navitem tv-focusable${selected === it.id ? " selected" : ""}`}
          onClick={() => onSelect(it.id)}
          onFocus={() => setExpanded(true)}
        >
          <span className="tv-nav-ico">{it.ico}</span>
          <span className="tv-nav-label">{t(it.key)}</span>
        </button>
      ))}
    </nav>
  );
}
