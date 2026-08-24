// StreamLiveX TV — sol menü (native TvSideMenu birebir: ikon+etiket, odakta genişler)
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import { BrandFull } from "./Brand";

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
  // Native tasarımda menü etiketleri her zaman görünür (ekran görüntüleriyle bire bir).
  const expanded = true;

  return (
    <nav className={`tv-sidebar${expanded ? " expanded" : ""}`}>
      <div className="tv-brand"><BrandFull size={34} /></div>
      {ITEMS.map((it) => (
        <button
          key={it.id}
          className={`tv-navitem tv-focusable${selected === it.id ? " selected" : ""}`}
          onClick={() => onSelect(it.id)}
        >
          <span className="tv-nav-ico">{it.ico}</span>
          <span className="tv-nav-label">{t(it.key)}</span>
        </button>
      ))}
    </nav>
  );
}
