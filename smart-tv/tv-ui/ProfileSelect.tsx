// StreamLiveX TV — profil seçimi (native TvProfileSelectScreen tasarımına göre).
import { useEffect, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import { focusFirst } from "./dpad";

export type Profile = { id: string; name: string; emoji: string };

const KEY = "slx-tv-profiles";
const EMOJIS = ["🦁", "🐼", "🦊", "🐵", "🐧", "🐯", "🐨", "🦉"];

function load(): Profile[] {
  try { return JSON.parse(localStorage.getItem(KEY) || "[]"); } catch { return []; }
}
function save(list: Profile[]) { localStorage.setItem(KEY, JSON.stringify(list)); }

export function ProfileSelect({ lang, onSelect }: { lang: TvLang; onSelect: (p: Profile) => void }) {
  const t = makeT(lang);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const rowRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let list = load();
    if (list.length === 0) {
      list = [{ id: "1", name: "Profil 1", emoji: "🦁" }];
      save(list);
    }
    setProfiles(list);
  }, []);

  useEffect(() => { if (profiles.length) focusFirst(rowRef.current); }, [profiles.length]);

  function addProfile() {
    const idx = profiles.length;
    const next: Profile = { id: String(Date.now()), name: `Profil ${idx + 1}`, emoji: EMOJIS[idx % EMOJIS.length] };
    const list = [...profiles, next];
    save(list);
    setProfiles(list);
  }

  return (
    <main className="tv-profiles">
      <h1>{t("who_watching")}</h1>
      <div className="tv-profile-row" ref={rowRef}>
        {profiles.map((p) => (
          <button key={p.id} className="tv-profile tv-focusable" onClick={() => onSelect(p)}>
            <span className="avatar">{p.emoji}</span>
            <span>{p.name}</span>
          </button>
        ))}
        {profiles.length < 8 && (
          <button className="tv-profile tv-focusable" onClick={addProfile}>
            <span className="avatar">＋</span>
            <span>{t("add_profile")}</span>
          </button>
        )}
      </div>
    </main>
  );
}
