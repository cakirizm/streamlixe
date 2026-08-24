// StreamLiveX TV — favoriler (Listem). Yerel depoda Media listesi.
import type { Media } from "./library";

const KEY = "slx-tv-fav";

export function getFavs(): Media[] {
  try { return JSON.parse(localStorage.getItem(KEY) || "[]"); } catch { return []; }
}
export function isFav(id: string): boolean {
  return getFavs().some((m) => m.id === id);
}
export function toggleFav(m: Media): boolean {
  const list = getFavs();
  const i = list.findIndex((x) => x.id === m.id);
  if (i >= 0) list.splice(i, 1);
  else list.unshift({ id: m.id, name: m.name, logo: m.logo, group: m.group, kind: m.kind, url: m.url, streamId: m.streamId, seriesId: m.seriesId, year: m.year, rating: m.rating });
  localStorage.setItem(KEY, JSON.stringify(list.slice(0, 300)));
  return i < 0; // true = eklendi
}
