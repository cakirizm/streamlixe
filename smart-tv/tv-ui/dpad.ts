// StreamLiveX TV — D-pad (uzaktan kumanda) uzamsal navigasyonu.
// Ok tuşlarıyla en yakın .tv-focusable öğeye geometrik olarak geçer, Enter tıklar,
// Geri (Back/Escape) geri gider. webOS Back tuşu = 461, Tizen Return = 10009.
import { useEffect } from "react";

const SELECTOR = ".tv-focusable:not([disabled]):not([aria-hidden='true'])";

function visible(el: HTMLElement) {
  const r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 &&
    r.top < innerHeight && r.left < innerWidth;
}

function center(el: Element) {
  const r = el.getBoundingClientRect();
  return { x: r.left + r.width / 2, y: r.top + r.height / 2, r };
}

function findNext(dir: "up" | "down" | "left" | "right"): HTMLElement | null {
  const active = document.activeElement as HTMLElement | null;
  const all = Array.from(document.querySelectorAll<HTMLElement>(SELECTOR)).filter(visible);
  if (!active || !all.includes(active)) return all[0] || null;
  const a = center(active);
  let best: HTMLElement | null = null;
  let bestScore = Infinity;
  for (const el of all) {
    if (el === active) continue;
    const c = center(el);
    const dx = c.x - a.x;
    const dy = c.y - a.y;
    // Yön filtresi
    if (dir === "left" && dx > -6) continue;
    if (dir === "right" && dx < 6) continue;
    if (dir === "up" && dy > -6) continue;
    if (dir === "down" && dy < 6) continue;
    const primary = dir === "left" || dir === "right" ? Math.abs(dx) : Math.abs(dy);
    const cross = dir === "left" || dir === "right" ? Math.abs(dy) : Math.abs(dx);
    // Ana eksende yakın + çapraz sapması az olanı seç
    const score = primary + cross * 2;
    if (score < bestScore) { bestScore = score; best = el; }
  }
  return best;
}

export function useDpad(onBack?: () => void) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const key = e.key;
      const kc = e.keyCode;
      // Geri tuşları: Escape, Backspace, webOS(461), Tizen(10009)
      if (key === "Escape" || key === "Backspace" || kc === 461 || kc === 10009) {
        const tag = (e.target as HTMLElement)?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA") return; // metin girişinde geri = silme
        e.preventDefault();
        onBack?.();
        return;
      }
      if (key === "Enter") {
        const el = document.activeElement as HTMLElement | null;
        if (el && el.classList.contains("tv-focusable")) {
          const tag = el.tagName;
          if (tag !== "INPUT" && tag !== "TEXTAREA" && tag !== "SELECT") {
            e.preventDefault();
            el.click();
          }
        }
        return;
      }
      const map: Record<string, "up" | "down" | "left" | "right"> = {
        ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right",
      };
      const dir = map[key];
      if (!dir) return;
      const tag = (e.target as HTMLElement)?.tagName;
      if ((tag === "INPUT" || tag === "TEXTAREA") && (dir === "left" || dir === "right")) return;
      const next = findNext(dir);
      if (next) {
        e.preventDefault();
        next.focus({ preventScroll: false });
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onBack]);
}

// İlk odak: verilen kapsayıcıda ilk .tv-focusable öğeye odaklan.
export function focusFirst(container?: HTMLElement | null) {
  const root = container || document;
  const el = root.querySelector<HTMLElement>(SELECTOR);
  el?.focus({ preventScroll: true });
}
