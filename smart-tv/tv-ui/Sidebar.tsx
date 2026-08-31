// StreamLiveX TV — sol menü (native TvSideMenu birebir: ikon+etiket, odakta genişler)
import { useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import { BrandFull } from "./Brand";
import { TvNavIcon } from "./TvNavIcon";

export type Section = "Home" | "Live" | "Sports" | "Movies" | "Series" | "Search" | "MyList" | "Settings";

// History native'de sidebar'dan gizli; sıralama native TvSection ile aynı.
const ITEMS: { id: Section; key: string }[] = [
  { id: "Home", key: "home" },
  { id: "Live", key: "live" },
  { id: "Sports", key: "sports" },
  { id: "Movies", key: "movies" },
  { id: "Series", key: "series" },
  { id: "Search", key: "search" },
  { id: "MyList", key: "my_list" },
  { id: "Settings", key: "settings" },
];

export function Sidebar({
  selected, onSelect, lang, expanded, onMenuFocused,
}: {
  selected: Section;
  onSelect: (s: Section) => void;
  lang: TvLang;
  expanded: boolean;
  onMenuFocused: () => void;
}) {
  const t = makeT(lang);
  const [focusedId, setFocusedId] = useState<Section | null>(null);

  return (
    <nav className={`tv-sidebar${expanded ? " expanded" : ""}`} onFocusCapture={onMenuFocused}>
      <div className="tv-brand"><BrandFull size={34} /></div>
      {ITEMS.map((it) => (
        <button
          key={it.id}
          className={`tv-navitem tv-focusable${selected === it.id ? " selected" : ""}`}
          onClick={() => onSelect(it.id)}
          onFocus={() => setFocusedId(it.id)}
          onBlur={() => setFocusedId((c) => (c === it.id ? null : c))}
        >
          <span className="tv-nav-ico"><TvNavIcon section={it.id} active={focusedId === it.id || selected === it.id} /></span>
          <span className="tv-nav-label">{t(it.key)}</span>
        </button>
      ))}
    </nav>
  );
}
