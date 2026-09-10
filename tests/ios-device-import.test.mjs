import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("the bundled iOS shell fetches playlists from the device, not the Worker", async () => {
  const container = await read("ios/StreamLiveX/Web/WebContainer.swift");

  assert.match(container, /requestURL\.path == "\/native-fetch"/);
  assert.match(container, /proxyDeviceRequest\(urlSchemeTask, requestURL: requestURL\)/);
  // Only http/https, and never the LAN the phone happens to sit on.
  assert.match(container, /scheme == "http" \|\| scheme == "https"/);
  assert.match(container, /blockedHostPattern/);
  assert.match(container, /192\\\\\.168\\\\\./);
  assert.match(container, /VLC\/3\.0\.21 LibVLC\/3\.0\.21/);
  assert.match(container, /timeoutInterval: 45/);
  // The /api/ proxy must keep working alongside it, through the shared sender.
  assert.match(container, /requestURL\.path\.hasPrefix\("\/api\/"\)/);
  assert.match(container, /private func perform\(_ upstreamRequest: URLRequest/);
});

test("importRequest prefers the device path and falls back to /api/import", async () => {
  const app = await read("app/PlayerApp.tsx");

  assert.match(app, /const nativeImportOrigin=\(\)=>[^\n]*streamlivex-local:/);
  assert.match(app, /\/native-fetch\?url=\$\{encodeURIComponent\(url\.href\)\}/);
  assert.match(app, /if\(nativeImportOrigin\(\)&&!isDemoAccount\(payload\)\)\{\s*try\{return await deviceImport\(payload\)\}/);
  assert.match(app, /await fetch\("\/api\/import",\{method:"POST"/);
  // Every Xtream call the Worker makes has a device-side counterpart.
  for (const action of ["get_live_streams", "get_vod_streams", "get_series", "get_live_categories", "get_vod_categories", "get_series_categories", "get_series_info", "get_vod_info", "get_short_epg"]) {
    assert.match(app, new RegExp(`"${action}"`), `missing device-side Xtream action: ${action}`);
  }
  assert.match(app, /payload\.method==="m3u"/);
  assert.match(app, /#EXTINF/);
  // When both paths fail the provider's own error is the one worth showing.
  assert.match(app, /throw deviceError\|\|new Error\(data\.error/);
});
