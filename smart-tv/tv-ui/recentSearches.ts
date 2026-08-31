// StreamLiveX TV — "Son Aramalar" (native store.addRecentSearch/recentSearches, en fazla 6).
const KEY = "slx-tv-recent-searches";

export function recentSearches(): string[] {
  try { return JSON.parse(localStorage.getItem(KEY) || "[]"); } catch { return []; }
}

export function addRecentSearch(query: string) {
  const q = query.trim();
  if (!q) return;
  const list = [q, ...recentSearches().filter((x) => x.toLowerCase() !== q.toLowerCase())].slice(0, 6);
  localStorage.setItem(KEY, JSON.stringify(list));
}

export function clearRecentSearches() {
  localStorage.setItem(KEY, "[]");
}
