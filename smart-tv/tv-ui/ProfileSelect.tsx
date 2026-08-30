// StreamLiveX TV — profil seçimi (native TvProfileSelectScreen birebir):
// "Kim izliyor?", gradient avatarlı kartlar (focus mavi + cyan ring), PIN modalı, Profil Ekle, Profilleri Yönet.
import { useEffect, useRef, useState } from "react";
import type { TvLang } from "./i18n";
import { makeT } from "./i18n";
import { focusFirst } from "./dpad";
import { BrandFull } from "./Brand";
import { allProfiles, verifyPin, kotlinHashCode, type Profile } from "./profiles";
import { ProfileManager } from "./ProfileManager";

// Native ProfileVectorAvatar paleti (koyu → açık radial gradient), Kotlin hashCode ile aynı seçim mantığı.
const PALETTES: [string, string][] = [
  ["#006C8F", "#16B8C8"],
  ["#49358A", "#9B71E8"],
  ["#8A3B55", "#E46B8B"],
  ["#236A55", "#51C49B"],
  ["#8A5424", "#E5A14B"],
];

function ProfileAvatar({ seed, focused, size = 82 }: { seed: string; focused: boolean; size?: number }) {
  const idx = (kotlinHashCode(seed) & 0x7fffffff) % PALETTES.length;
  const [dark, light] = PALETTES[idx];
  const gid = `pg-${Math.abs(kotlinHashCode(seed))}`;
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
      <circle cx="50" cy="38" r="14.5" fill="#F1C7A5" />
      <path d="M26 74 Q26 56 50 56 Q74 56 74 74 L74 80 L26 80 Z" fill="#12212B" />
    </svg>
  );
}

export function ProfileSelect({ lang, onSelect }: { lang: TvLang; onSelect: (p: Profile) => void }) {
  const t = makeT(lang);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [focusedId, setFocusedId] = useState<string>("");
  const [pending, setPending] = useState<Profile | null>(null);
  const [pin, setPin] = useState("");
  const [error, setError] = useState("");
  const [managing, setManaging] = useState(false);
  const rowRef = useRef<HTMLDivElement>(null);
  const pinRef = useRef<HTMLInputElement>(null);

  useEffect(() => { setProfiles(allProfiles()); }, []);

  useEffect(() => {
    if (!managing && !pending && profiles.length) {
      const id = setTimeout(() => focusFirst(rowRef.current), 120);
      return () => clearTimeout(id);
    }
  }, [profiles.length, managing, pending]);

  useEffect(() => {
    if (pending) { const id = setTimeout(() => pinRef.current?.focus({ preventScroll: true }), 120); return () => clearTimeout(id); }
  }, [pending]);

  function openManager() { setManaging(true); }

  if (managing) {
    return <ProfileManager onBack={() => { setProfiles(allProfiles()); setManaging(false); }} />;
  }

  function selectProfile(p: Profile) {
    if (!p.pinHash) { onSelect(p); return; }
    setPending(p); setPin(""); setError("");
  }

  async function unlock() {
    if (!pending) return;
    if (await verifyPin(pending, pin)) onSelect(pending);
    else setError("PIN yanlış.");
  }

  return (
    <main className="tv-profiles">
      <h1>{t("who_watching")}</h1>
      <div className="tv-profile-row" ref={rowRef}>
        {profiles.map((p) => (
          <button
            key={p.id}
            className={`tv-profile-card tv-focusable${focusedId === p.id ? " tv-focused" : ""}`}
            onClick={() => selectProfile(p)}
            onFocus={() => setFocusedId(p.id)}
            onBlur={() => setFocusedId((c) => (c === p.id ? "" : c))}
          >
            <ProfileAvatar seed={p.id + p.name} focused={focusedId === p.id} />
            <span className="pname">{p.name}</span>
            {p.isKids && <span style={{ color: "#93C5FD" }}>Çocuk</span>}
            {p.pinHash && <span style={{ color: "#CBD5E1" }}>🔒</span>}
          </button>
        ))}
        <button className="tv-addprofile tv-focusable" onClick={openManager}>
          <span className="plus">＋</span>
          <span className="pname">{t("add_profile")}</span>
        </button>
      </div>

      <div className="tv-profile-brand"><BrandFull size={38} subtitle={false} /></div>
      <button className="tv-profile-manage tv-focusable" onClick={openManager}>Profilleri Yönet</button>

      {pending && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,.8)", display: "grid", placeItems: "center" }}>
          <div style={{ width: 420, background: "#111827", borderRadius: 16, padding: 24, display: "flex", flexDirection: "column", gap: 14 }}>
            <div style={{ color: "#fff", fontWeight: 700, fontSize: 22 }}>{pending.avatar} {pending.name}</div>
            <div style={{ color: "#94A3B8" }}>4 haneli PIN</div>
            <input
              ref={pinRef} className="tv-focusable" type="password" style={{ height: 46, borderRadius: 10, border: "1px solid rgba(255,255,255,.14)", background: "#0a1017", color: "#fff", padding: "0 14px", fontSize: 15 }}
              value={pin} onChange={(e) => { setPin(e.target.value.replace(/\D/g, "").slice(0, 4)); setError(""); }}
              onKeyDown={(e) => { if (e.key === "Enter" && pin.length === 4) unlock(); }}
            />
            {error && <p className="tv-error" style={{ margin: 0 }}>{error}</p>}
            <div style={{ display: "flex", gap: 10 }}>
              <button className="tv-btn ghost tv-focusable" style={{ width: "auto", height: "auto", padding: "12px 16px", margin: 0 }} onClick={() => setPending(null)}>Geri</button>
              <button className={`tv-btn tv-focusable${pin.length === 4 ? "" : " ghost"}`} style={{ width: "auto", height: "auto", padding: "12px 16px", margin: 0 }} onClick={unlock}>Aç</button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
