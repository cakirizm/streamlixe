// StreamLiveX TV — profil deposu (native: tv/profile/TvProfileStore.kt eşdeğeri).
// PIN: SHA-256 hex (native ile aynı format). Avatar paleti: Kotlin String.hashCode() ile aynı seçim.

export type Profile = { id: string; name: string; avatar: string; isKids: boolean; pinHash: string | null };

const KEY = "slx-tv-profiles-v2";

function readAll(): Profile[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) || "[]");
  } catch {
    return [];
  }
}
function writeAll(rows: Profile[]) {
  localStorage.setItem(KEY, JSON.stringify(rows));
}

function newId(): string {
  try {
    return crypto.randomUUID();
  } catch {
    return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  }
}

// Native: MessageDigest("SHA-256").digest(pin.toByteArray()) → hex (lowercase, "%02x").
export async function hashPin(pin: string): Promise<string> {
  const bytes = new TextEncoder().encode(pin);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

// Kotlin String.hashCode() birebir eşdeğeri (32-bit int taşması dahil) — ProfileVectorAvatar palet seçimi için.
export function kotlinHashCode(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  }
  return h;
}

export function allProfiles(): Profile[] {
  const rows = readAll();
  if (rows.length) return rows;
  const first: Profile = { id: newId(), name: "Profil 1", avatar: "🙂", isKids: false, pinHash: null };
  writeAll([first]);
  return [first];
}

export async function addProfile(name: string, avatar: string, isKids: boolean, pin: string | null): Promise<Profile> {
  const profile: Profile = {
    id: newId(),
    name: name.trim() || "Profil",
    avatar: avatar || (isKids ? "🧒" : "🙂"),
    isKids,
    pinHash: pin && pin.length === 4 ? await hashPin(pin) : null,
  };
  writeAll([...allProfiles(), profile]);
  return profile;
}

export async function updateProfile(
  id: string,
  name: string,
  avatar: string,
  isKids: boolean,
  pin: string | null,
  keepExistingPin: boolean,
): Promise<Profile | null> {
  const rows = allProfiles();
  const idx = rows.findIndex((r) => r.id === id);
  if (idx < 0) return null;
  const cur = rows[idx];
  const nextPinHash = pin && pin.length === 4 ? await hashPin(pin) : keepExistingPin ? cur.pinHash : null;
  const updated: Profile = {
    ...cur,
    name: name.trim() || cur.name,
    avatar: avatar || cur.avatar,
    isKids,
    pinHash: nextPinHash,
  };
  rows[idx] = updated;
  writeAll(rows);
  return updated;
}

export function removeProfile(id: string) {
  const rows = allProfiles().filter((r) => r.id !== id);
  writeAll(rows);
  if (rows.length === 0) allProfiles(); // native: liste boşalırsa "Profil 1" otomatik yeniden oluşur
}

export async function verifyPin(profile: Profile, pin: string): Promise<boolean> {
  if (!profile.pinHash) return true;
  return (await hashPin(pin)) === profile.pinHash;
}
