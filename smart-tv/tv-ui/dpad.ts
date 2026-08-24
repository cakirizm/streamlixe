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

// Dikey hareketlerde yatay örtüşme, yatay hareketlerde dikey örtüşme aranır.
// Böylece sütun sonunda "aşağı" basınca çapraz olarak başka panele atlamaz —
// hizalı öğe yoksa hareket etmez (yerinde kalır). Native TV davranışı.
const overlapX = (a: DOMRect, b: DOMRect) => a.left < b.right - 4 && a.right > b.left + 4;
const overlapY = (a: DOMRect, b: DOMRect) => a.top < b.bottom - 4 && a.bottom > b.top + 4;

function findNext(dir: "up" | "down" | "left" | "right"): HTMLElement | null {
  const active = document.activeElement as HTMLElement | null;
  const all = Array.from(document.querySelectorAll<HTMLElement>(SELECTOR)).filter(visible);
  if (!active || !all.includes(active)) return all[0] || null;
  const a = active.getBoundingClientRect();
  const aCx = a.left + a.width / 2;
  const aCy = a.top + a.height / 2;

  const pick = (requireOverlap: boolean): HTMLElement | null => {
    let best: HTMLElement | null = null;
    let bestScore = Infinity;
    for (const el of all) {
      if (el === active) continue;
      const r = el.getBoundingClientRect();
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      let primary: number, cross: number, ok: boolean, overlap: boolean;
      if (dir === "down") { ok = r.top >= a.bottom - 6; primary = r.top - a.bottom; cross = Math.abs(cx - aCx); overlap = overlapX(a, r); }
      else if (dir === "up") { ok = r.bottom <= a.top + 6; primary = a.top - r.bottom; cross = Math.abs(cx - aCx); overlap = overlapX(a, r); }
      else if (dir === "right") { ok = r.left >= a.right - 6; primary = r.left - a.right; cross = Math.abs(cy - aCy); overlap = overlapY(a, r); }
      else { ok = r.right <= a.left + 6; primary = a.left - r.right; cross = Math.abs(cy - aCy); overlap = overlapY(a, r); }
      if (!ok) continue;
      if (requireOverlap && !overlap) continue;
      const score = Math.max(0, primary) + cross * (requireOverlap ? 0.25 : 3);
      if (score < bestScore) { bestScore = score; best = el; }
    }
    return best;
  };

  // Önce aynı sütun/satırda (örtüşen) öğe ara; yoksa HAREKET ETME (null).
  // Sadece hiç örtüşen yoksa ve yön yataysa gevşek aramaya izin ver (paneller arası geçiş).
  const strict = pick(true);
  if (strict) return strict;
  if (dir === "left" || dir === "right") return pick(false);
  return null;
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
