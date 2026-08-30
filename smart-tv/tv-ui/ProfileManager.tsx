// StreamLiveX TV — Profilleri Yönet (native: tv/profile/TvProfileScreens.kt
// TvProfileManagerScreen + TvEditProfileScreen + TvCreateProfileScreen birebir).
import { useEffect, useRef, useState } from "react";
import { focusFirst } from "./dpad";
import type { Profile } from "./profiles";
import { allProfiles, addProfile, updateProfile, removeProfile } from "./profiles";

const AVATARS = ["🙂", "😎", "👩", "👨", "🧒", "🦊"];

function ManagerButton({ text, selected, onClick }: { text: string; selected?: boolean; onClick: () => void }) {
  return (
    <button className={`tv-btn ghost tv-focusable${selected ? " active" : ""}`} style={{ width: "auto", height: "auto", padding: "12px 16px", margin: 0 }} onClick={onClick}>
      {text}
    </button>
  );
}

export function ProfileManager({ onBack }: { onBack: () => void }) {
  const [rows, setRows] = useState<Profile[]>(() => allProfiles());
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<Profile | null>(null);
  const backRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!adding && !editing) {
      const id = setTimeout(() => backRef.current?.focus({ preventScroll: true }), 120);
      return () => clearTimeout(id);
    }
  }, [adding, editing]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (adding || editing) return; // alt ekranlar kendi geri tuşunu yönetiyor
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        const tag = (e.target as HTMLElement)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;
        e.preventDefault(); e.stopPropagation(); onBack();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onBack, adding, editing]);

  if (editing) {
    return (
      <ProfileForm
        title="Profili Düzenle"
        profile={editing}
        onBack={() => setEditing(null)}
        onSaved={() => { setRows(allProfiles()); setEditing(null); }}
      />
    );
  }
  if (adding) {
    return (
      <ProfileForm
        title="Yeni Profil"
        profile={null}
        onBack={() => setAdding(false)}
        onSaved={() => { setRows(allProfiles()); setAdding(false); }}
      />
    );
  }

  return (
    <div className="tv-page" style={{ maxWidth: 900 }}>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 20 }}>
        <button ref={backRef} className="tv-series-back tv-focusable" onClick={onBack}>← Geri</button>
        <h1 style={{ margin: 0, fontSize: 30 }}>Profilleri Yönet</h1>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        {rows.map((p) => (
          <div key={p.id} style={{ display: "flex", alignItems: "center", gap: 14, background: "#151C28", borderRadius: 12, padding: 16 }}>
            <span style={{ fontSize: 34 }}>{p.avatar}</span>
            <div style={{ flex: 1 }}>
              <div style={{ color: "#fff", fontWeight: 700 }}>{p.name}</div>
              <div style={{ color: "#94A3B8" }}>
                {p.isKids ? "Çocuk Profili" : "Standart Profil"}{p.pinHash ? " · PIN korumalı" : ""}
              </div>
            </div>
            <ManagerButton text="Düzenle" onClick={() => setEditing(p)} />
            {rows.length > 1 && (
              <ManagerButton text="Sil" onClick={() => { removeProfile(p.id); setRows(allProfiles()); }} />
            )}
          </div>
        ))}
        <ManagerButton text="＋ Yeni Profil" onClick={() => setAdding(true)} />
      </div>
    </div>
  );
}

function ProfileForm({
  title, profile, onBack, onSaved,
}: { title: string; profile: Profile | null; onBack: () => void; onSaved: (p: Profile) => void }) {
  const [name, setName] = useState(profile?.name || "");
  const [avatar, setAvatar] = useState(profile?.avatar || "🙂");
  const [kids, setKids] = useState(profile?.isKids || false);
  // create: pinEnabled toggle; edit: pinMode keep/off/new
  const [pinEnabled, setPinEnabled] = useState(false);
  const [pinMode, setPinMode] = useState<"keep" | "off" | "new">(profile?.pinHash ? "keep" : "off");
  const [pin, setPin] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || e.key === "Backspace" || e.keyCode === 461 || e.keyCode === 10009) {
        const tag = (e.target as HTMLElement)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return;
        e.preventDefault(); e.stopPropagation(); onBack();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onBack]);

  async function save() {
    if (!name.trim()) { setError("Profil adı gerekli."); return; }
    if (profile) {
      if (pinMode === "new" && pin.length !== 4) { setError("PIN 4 haneli olmalı."); return; }
      const updated = await updateProfile(profile.id, name, avatar, kids, pinMode === "new" ? pin : null, pinMode === "keep");
      if (updated) onSaved(updated);
    } else {
      if (pinEnabled && pin.length !== 4) { setError("PIN 4 haneli olmalı."); return; }
      const created = await addProfile(name, avatar, kids, pinEnabled ? pin : null);
      onSaved(created);
    }
  }

  return (
    <div className="tv-page" style={{ maxWidth: 700 }}>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 20 }}>
        <button className="tv-series-back tv-focusable" onClick={onBack}>← Geri</button>
        <h1 style={{ margin: 0, fontSize: 30 }}>{title}</h1>
      </div>

      <label className="tv-field">
        <span>Profil adı</span>
        <input className="tv-focusable" style={{ maxWidth: 430 }} value={name} onChange={(e) => { setName(e.target.value); setError(""); }} />
      </label>

      <div style={{ color: "#fff", fontWeight: 700, margin: "14px 0 10px" }}>Avatar</div>
      <div style={{ display: "flex", gap: 10, marginBottom: 14 }}>
        {AVATARS.map((a) => (
          <ManagerButton key={a} text={a} selected={avatar === a} onClick={() => setAvatar(a)} />
        ))}
      </div>

      <div style={{ marginBottom: 14 }}>
        <ManagerButton
          text={kids ? "Çocuk Profili: Açık" : "Çocuk Profili: Kapalı"}
          selected={kids}
          onClick={() => {
            const next = !kids;
            setKids(next);
            if (!profile && next && avatar === "🙂") setAvatar("🧒");
          }}
        />
      </div>

      {profile ? (
        <div style={{ display: "flex", gap: 10, marginBottom: 14 }}>
          <ManagerButton text="PIN'i Koru" selected={pinMode === "keep"} onClick={() => { setPinMode("keep"); setPin(""); }} />
          <ManagerButton text="PIN'i Kaldır" selected={pinMode === "off"} onClick={() => { setPinMode("off"); setPin(""); }} />
          <ManagerButton text="Yeni PIN" selected={pinMode === "new"} onClick={() => setPinMode("new")} />
        </div>
      ) : (
        <div style={{ marginBottom: 14 }}>
          <ManagerButton
            text={pinEnabled ? "PIN: Açık" : "PIN: Kapalı"}
            selected={pinEnabled}
            onClick={() => { const next = !pinEnabled; setPinEnabled(next); if (!next) setPin(""); }}
          />
        </div>
      )}

      {((profile && pinMode === "new") || (!profile && pinEnabled)) && (
        <label className="tv-field">
          <span>{profile ? "Yeni 4 haneli PIN" : "4 haneli PIN"}</span>
          <input
            className="tv-focusable" type="password" style={{ maxWidth: 430 }}
            value={pin} onChange={(e) => { setPin(e.target.value.replace(/\D/g, "").slice(0, 4)); setError(""); }}
          />
        </label>
      )}

      {error && <p className="tv-error">{error}</p>}

      {/* native: buton hiçbir zaman disabled değil — boş adla da odaklanıp tıklanabilir, tıklanınca "Profil adı gerekli." hatası gösterilir. */}
      <ManagerButton text={profile ? "Kaydet" : "Profili Oluştur"} selected={!!name.trim()} onClick={save} />
    </div>
  );
}
