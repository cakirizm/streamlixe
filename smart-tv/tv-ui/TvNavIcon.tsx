// StreamLiveX TV — sidebar ikonları (native TvRoot.kt `TvNavIcon` Canvas çizimi birebir SVG karşılığı).
// Emoji/Unicode glif KULLANILMIYOR: webOS/Tizen'in gömülü Chromium/WebKit motorlarında renkli-emoji
// fontu genelde yok, bu yüzden ⌂●⚽▶▤⌕★⚙ gibi glifler boş/görünmez render olabiliyor (simülatörde
// doğrulandı — sidebar daralıp etiket kaybolunca ikon da görünmez oluyordu). Native zaten emoji değil
// Canvas'la çizilmiş vektör ikon kullanıyor; burada da aynı yaklaşım (SVG path) uygulanıyor.
import type { Section } from "./Sidebar";

const FACE_ACTIVE = "#EAF9FF";
const ACCENT_ACTIVE = "#35BDF5";

export function TvNavIcon({ section, active }: { section: Section; active: boolean }) {
  const face = active ? FACE_ACTIVE : "currentColor";
  const accent = active ? ACCENT_ACTIVE : "currentColor";
  const accentOpacity = active ? 1 : 0.72;
  const lineProps = { fill: "none" as const, strokeLinecap: "butt" as const };

  switch (section) {
    case "Home":
      return (
        <svg viewBox="0 0 22 22" width="22" height="22" aria-hidden="true">
          <path d="M4.84 10.78 L10.34 5.94 L15.84 10.78" stroke={face} strokeWidth={1.75} {...lineProps} />
          <rect x="6.38" y="10.34" width="7.92" height="6.16" stroke={face} strokeWidth={1.75} fill="none" />
        </svg>
      );
    case "Live":
      return (
        <svg viewBox="0 0 22 22" width="22" height="22" aria-hidden="true">
          <rect x="4.4" y="6.16" width="12.1" height="8.8" rx="1.32" stroke={face} strokeWidth={1.75} fill="none" />
          <circle cx="14.52" cy="7.92" r="1.21" fill={accent} opacity={accentOpacity} />
          <path d="M6.82 5.94 L4.84 3.74" stroke={accent} strokeWidth={1.75} opacity={accentOpacity} {...lineProps} />
          <path d="M12.54 5.94 L14.52 3.74" stroke={accent} strokeWidth={1.75} opacity={accentOpacity} {...lineProps} />
          <path d="M7.7 15.84 L13.2 15.84" stroke={face} strokeWidth={1.75} {...lineProps} />
        </svg>
      );
    case "Sports":
      return (
        <svg viewBox="0 0 22 22" width="22" height="22" aria-hidden="true">
          <circle cx="10.34" cy="10.56" r="5.06" stroke={face} strokeWidth={1.75} fill="none" />
          <circle cx="10.34" cy="10.56" r="1.43" stroke={accent} strokeWidth={1.75} opacity={accentOpacity} fill="none" />
          <path d="M10.34 5.5 L10.34 9.02" stroke={accent} strokeWidth={1} opacity={accentOpacity} {...lineProps} />
          <path d="M5.94 9.46 L9.02 10.56" stroke={accent} strokeWidth={1} opacity={accentOpacity} {...lineProps} />
          <path d="M13.86 9.46 L11.66 10.56" stroke={accent} strokeWidth={1} opacity={accentOpacity} {...lineProps} />
        </svg>
      );
    case "Movies":
      return (
        <svg viewBox="0 0 22 22" width="22" height="22" aria-hidden="true">
          <rect x="4.4" y="7.48" width="11.88" height="8.36" rx="0.88" stroke={face} strokeWidth={1.75} fill="none" />
          <path d="M4.62 5.94 L16.06 4.4" stroke={accent} strokeWidth={1.75} opacity={accentOpacity} {...lineProps} />
          <path d="M7.48 5.5 L6.38 3.96" stroke={accent} strokeWidth={1} opacity={accentOpacity} {...lineProps} />
          <path d="M11 5.06 L9.9 3.52" stroke={accent} strokeWidth={1} opacity={accentOpacity} {...lineProps} />
          <path d="M9.24 9.46 L9.24 14.08 L13.2 11.77 Z" fill={accent} opacity={accentOpacity} />
        </svg>
      );
    case "Series":
      return (
        <svg viewBox="0 0 22 22" width="22" height="22" aria-hidden="true">
          <rect x="4.84" y="4.84" width="11" height="11.44" rx="1.1" stroke={face} strokeWidth={1.75} fill="none" />
          <path d="M6.6 4.84 L5.06 3.08" stroke={accent} strokeWidth={1} opacity={accentOpacity} {...lineProps} />
          <path d="M13.86 4.84 L15.4 3.08" stroke={accent} strokeWidth={1} opacity={accentOpacity} {...lineProps} />
          <path d="M6.6 13.42 L14.08 13.42" stroke={accent} strokeWidth={1} opacity={accentOpacity} {...lineProps} />
          <path d="M8.58 17.16 L12.32 17.16" stroke={face} strokeWidth={1.75} {...lineProps} />
        </svg>
      );
    case "Search":
      return (
        <svg viewBox="0 0 22 22" width="22" height="22" aria-hidden="true">
          <circle cx="9.02" cy="8.8" r="4.18" stroke={face} strokeWidth={1.75} fill="none" />
          <path d="M12.1 12.1 L15.84 15.84" stroke={accent} strokeWidth={1.75} opacity={accentOpacity} {...lineProps} />
        </svg>
      );
    case "MyList":
      return (
        <svg viewBox="0 0 22 22" width="22" height="22" aria-hidden="true">
          <rect x="6.38" y="4.4" width="8.36" height="12.1" rx="0.88" stroke={face} strokeWidth={1.75} fill="none" />
        </svg>
      );
    case "Settings":
      return (
        <svg viewBox="0 0 22 22" width="22" height="22" aria-hidden="true">
          <circle cx="11" cy="11" r="4.84" stroke={face} strokeWidth={1.75} fill="none" />
          <circle cx="11" cy="11" r="1.54" stroke={accent} strokeWidth={1.75} opacity={accentOpacity} fill="none" />
        </svg>
      );
    default:
      return null;
  }
}
