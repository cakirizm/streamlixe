// StreamLiveX TV — profil seçimi (native TvProfileSelectScreen birebir):
// "Kim izliyor?", gradient avatarlı kartlar (focus mavi + cyan ring), Profil Ekle, Profilleri Yönet.
import { useEffect, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import { focusFirst } from "./dpad";

export type Profile = { id: string; name: string };

const KEY = "slx-tv-profiles";

// Native ProfileVectorAvatar paleti (koyu → açık radial gradient).
const PALETTES: [string, string][] = [
  ["#006C8F", "#16B8C8"],
  ["#49358A", "#9B71E8"],
  ["#8A3B55", "#E46B8B"],
  ["#236A55", "#51C49B"],
  ["#8A5424", "#E5A14B"],
];
function hash(s: string): number { let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return Math.abs(h); }

function ProfileAvatar({ seed, focused, size = 82 }: { seed: string; focused: boolean; size?: number }) {
  const [dark, light] = PALETTES[hash(seed) % PALETTES.length];
  const gid = `pg-${hash(seed)}`;
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" style={{ display: "block" }}>
      <defs>
        <radialGradient id={gid} cx="34%" cy="25%" r="78%">
          <stop offset="0%" stopColor={light} />
          <stop offset="100%" stopColor={dark} />
        </radialGradient>
      </defs>
      <circle cx="50" cy="50" r="49" fill={`url(#${gid})`} />
      <circle cx="50" cy="50" r="47" fill="none" stroke={focused ? "#7DE3FF" : "rgba(255,255,255,.33)"} strokeWidth="2.5" />
      {/* kişi silüeti */}
      <circle cx="50" cy="38" r="14.5" fill="#F1C7A5" />
      <path d="M26 74 Q26 56 50 56 Q74 56 74 74 L74 80 L26 80 Z" fill="#12212B" />
    </svg>
  );
}

function load(): Profile[] {
  try { return JSON.parse(localStorage.getItem(KEY) || "[]"); } catch { return []; }
}
function save(list: Profile[]) { localStorage.setItem(KEY, JSON.stringify(list)); }

export function ProfileSelect({ lang, onSelect }: { lang: TvLang; onSelect: (p: Profile) => void }) {
  const t = makeT(lang);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [focusedId, setFocusedId] = useState<string>("");
  const rowRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let list = load();
    if (list.length === 0) { list = [{ id: "1", name: "Profil 1" }]; save(list); }
    setProfiles(list);
  }, []);

  useEffect(() => { if (profiles.length) { const id = setTimeout(() => focusFirst(rowRef.current), 60); return () => clearTimeout(id); } }, [profiles.length]);

  function addProfile() {
    const next: Profile = { id: String(Date.now()), name: `Profil ${profiles.length + 1}` };
    const list = [...profiles, next]; save(list); setProfiles(list);
  }

  return (
    <main className="tv-profiles">
      <h1>{t("who_watching")}</h1>
      <div className="tv-profile-row" ref={rowRef}>
        {profiles.map((p) => (
          <button
            key={p.id}
            className={`tv-profile-card tv-focusable${focusedId === p.id ? " tv-focused" : ""}`}
            onClick={() => onSelect(p)}
            onFocus={() => setFocusedId(p.id)}
            onBlur={() => setFocusedId((c) => (c === p.id ? "" : c))}
          >
            <ProfileAvatar seed={p.id + p.name} focused={focusedId === p.id} />
            <span className="pname">{p.name}</span>
          </button>
        ))}
        {profiles.length < 6 && (
          <button className="tv-addprofile tv-focusable" onClick={addProfile}>
            <span className="plus">＋</span>
            <span className="pname">{t("add_profile")}</span>
          </button>
        )}
      </div>

      <div className="tv-profile-brand">
        <img src="/streamlivex-logo.jpeg" alt="StreamLiveX" />
        <b>StreamLive<i>X</i></b>
      </div>
      <button className="tv-profile-manage tv-focusable">Profilleri Yönet</button>
    </main>
  );
}
