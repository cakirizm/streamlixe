import React from "react";
import { createRoot } from "react-dom/client";
import PlayerApp from "../../app/PlayerApp";
import "../../app/globals.css";
import "../../app/ios-mobile.css";
import "../../app/legal.css";

declare global {
  interface Window {
    __SLX_PROXY_ORIGIN__?: string;
    __SLX_ASSET_BASE__?: string;
    __SLX_UI_BUILD__?: string;
    __SLX_UI_SOURCE__?: string;
    __SLX_LEGACY_EXPORT_BASE64__?: string;
  }
}

const serviceOrigin = "https://streamlivex.com";
const isBundledIOS = window.location.protocol === "streamlivex-local:";
window.__SLX_PROXY_ORIGIN__ = window.location.origin;
window.__SLX_ASSET_BASE__ = new URL("./", document.baseURI).href;
window.__SLX_UI_BUILD__ = import.meta.env.VITE_UI_BUILD_SHA || "local";
window.__SLX_UI_SOURCE__ = "Bundled iOS Branch";
document.documentElement.classList.add("streamlivex-ios");

// Relative API calls stay on the custom iOS origin. WKURLSchemeHandler forwards
// them to the service, avoiding CORS while absolute provider/media URLs remain
// untouched. The body header works around WebKit omitting custom-scheme bodies.
const browserFetch = window.fetch.bind(window);
window.fetch = (input: RequestInfo | URL, init?: RequestInit) => {
  if (isBundledIOS && typeof input === "string" && input.startsWith("/")) {
    const next = {...init, headers: new Headers(init?.headers)};
    if (typeof init?.body === "string") {
      const bytes = new TextEncoder().encode(init.body);
      let binary = "";
      bytes.forEach(byte => { binary += String.fromCharCode(byte); });
      next.headers.set("X-StreamLiveX-Body", btoa(binary));
      delete next.body;
    }
    return browserFetch(new URL(input, window.location.origin), next);
  }
  if (input instanceof URL && input.protocol === "file:") {
    return browserFetch(new URL(`${input.pathname}${input.search}`, serviceOrigin), init);
  }
  return browserFetch(input, init);
};

type LegacyExport = {localStorage?: Record<string, string>; sessionStorage?: Record<string, string>; library?: Array<{key: IDBValidKey; value: unknown}>};

async function migrateLegacyStorage() {
  const encoded = window.__SLX_LEGACY_EXPORT_BASE64__;
  if (!encoded) return;
  try {
    const bytes = Uint8Array.from(atob(encoded), value => value.charCodeAt(0));
    const legacy = JSON.parse(new TextDecoder().decode(bytes)) as LegacyExport;
    Object.entries(legacy.localStorage || {}).forEach(([key, value]) => {
      if (localStorage.getItem(key) === null) localStorage.setItem(key, value);
    });
    Object.entries(legacy.sessionStorage || {}).forEach(([key, value]) => {
      if (sessionStorage.getItem(key) === null) sessionStorage.setItem(key, value);
    });
    if (legacy.library?.length) {
      await new Promise<void>((resolve, reject) => {
        const request = indexedDB.open("streamlivex-v2", 1);
        request.onupgradeneeded = () => {
          if (!request.result.objectStoreNames.contains("library")) request.result.createObjectStore("library");
        };
        request.onerror = () => reject(request.error);
        request.onsuccess = () => {
          const transaction = request.result.transaction("library", "readwrite");
          const store = transaction.objectStore("library");
          legacy.library!.forEach(entry => {
            const lookup = store.get(entry.key);
            lookup.onsuccess = () => { if (lookup.result === undefined) store.put(entry.value, entry.key); };
          });
          transaction.oncomplete = () => { request.result.close(); resolve(); };
          transaction.onerror = () => reject(transaction.error);
        };
      });
    }
  } catch (error) {
    console.warn("Legacy iOS storage migration could not be completed", error);
  } finally {
    delete window.__SLX_LEGACY_EXPORT_BASE64__;
  }
}

async function startApp() {
  await migrateLegacyStorage();
  const root = document.getElementById("root");
  if (!root) throw new Error("iOS UI root is missing");
  createRoot(root).render(<React.StrictMode><PlayerApp /></React.StrictMode>);
  requestAnimationFrame(() => window.chrome?.webview?.postMessage({type: "ui-ready"}));
}

void startApp();
