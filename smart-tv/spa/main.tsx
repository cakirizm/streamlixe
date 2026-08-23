// StreamLiveX — Smart TV (webOS/Tizen) SPA girişi.
// Native Android TV arayüzünün web portu olan TvApp'i mount eder ve TV'ye özgü ayarları uygular:
//   1) Tüm /api/* çağrılarını streamlivex.com backend'ine yönlendirir (Faz 2 içerik için).
//   2) Yayınları doğrudan ham URL ile oynatmayı tercih ettiren global bayrağı açar.
import { createRoot } from "react-dom/client";
import TvApp from "../tv-ui/TvApp";

// TV backend'i. İleride farklı ortam için VITE_API_BASE ile override edilebilir.
const API_BASE: string =
  (import.meta as any).env?.VITE_API_BASE?.replace(/\/$/, "") || "https://streamlivex.com";

(window as any).__SLX_PROXY_ORIGIN__ = API_BASE;
(window as any).__SLX_TV_DIRECT__ = true;

// Göreli /api/* isteklerini uzak backend'e yönlendiren fetch köprüsü.
const nativeFetch = window.fetch.bind(window);
window.fetch = ((input: any, init?: any) => {
  try {
    if (typeof input === "string" && input.startsWith("/api/")) {
      return nativeFetch(API_BASE + input, init);
    }
    if (input instanceof URL && input.pathname.startsWith("/api/")) {
      return nativeFetch(API_BASE + input.pathname + input.search, init);
    }
    if (input instanceof Request) {
      const u = new URL(input.url, location.href);
      if (u.origin === location.origin && u.pathname.startsWith("/api/")) {
        return nativeFetch(new Request(API_BASE + u.pathname + u.search, input));
      }
    }
  } catch {
    /* köprü başarısız olursa yerel fetch'e düş */
  }
  return nativeFetch(input, init);
}) as typeof fetch;

createRoot(document.getElementById("root")!).render(<TvApp />);
