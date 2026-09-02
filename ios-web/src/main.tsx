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
  }
}

const serviceOrigin = "https://streamlivex.com";
window.__SLX_PROXY_ORIGIN__ = serviceOrigin;
window.__SLX_ASSET_BASE__ = new URL("./", document.baseURI).href;
window.__SLX_UI_BUILD__ = import.meta.env.VITE_UI_BUILD_SHA || "local";
window.__SLX_UI_SOURCE__ = "Bundled iOS Branch";
document.documentElement.classList.add("streamlivex-ios");

// The presentation layer is local, while the existing server-only import/TMDB/
// EPG endpoints remain on the StreamLiveX service. Keep provider media URLs and
// absolute third-party requests untouched.
const browserFetch = window.fetch.bind(window);
window.fetch = (input: RequestInfo | URL, init?: RequestInit) => {
  if (typeof input === "string" && input.startsWith("/")) {
    return browserFetch(new URL(input, serviceOrigin), init);
  }
  if (input instanceof URL && input.protocol === "file:") {
    return browserFetch(new URL(`${input.pathname}${input.search}`, serviceOrigin), init);
  }
  return browserFetch(input, init);
};

const root = document.getElementById("root");
if (!root) throw new Error("iOS UI root is missing");
createRoot(root).render(
  <React.StrictMode>
    <PlayerApp />
  </React.StrictMode>,
);
