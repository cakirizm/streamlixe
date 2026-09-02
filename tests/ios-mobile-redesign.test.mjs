import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = path => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("iOS redesign is scoped and keeps five primary tabs", async () => {
  const [app, css, bridge] = await Promise.all([read("app/PlayerApp.tsx"), read("app/ios-mobile.css"), read("ios/StreamLiveX/Bridge/NativeBridge.swift")]);
  assert.match(app, /function IOSDashboard/);
  assert.match(app, /const nav=\[\{id:"home"[\s\S]*id:"settings" as Section,label:"Profil"/);
  assert.doesNotMatch(app.match(/function IOSDashboard[\s\S]*?function Dashboard/)?.[0] ?? "", /label:"Spor"|label:"Kategoriler"/);
  assert.match(app, /function IOSCatalog/);
  assert.match(app, /function IOSLiveTV/);
  assert.match(app, /function IOSProfile/);
  assert.match(css, /html\.streamlivex-ios \.ios-tabbar/);
  assert.doesNotMatch(css, /(^|\})\s*\.dash>header>nav/);
  assert.match(bridge, /classList\.add\('streamlivex-ios'\)/);
  assert.match(app, /function IOSSearch/);
  assert.match(app, /function IOSFavorites/);
  assert.match(app, /Son Aramalar/);
  assert.match(app, /\[\["all","Tümü"\],\["movie","Filmler"\],\["series","Diziler"\],\["live","Kanallar"\]\]/);
});

test("iOS IPA renders the branch UI from its local bundle", async () => {
  const [configuration, container, plist, workflow, entry, packageJson, project] = await Promise.all([
    read("ios/StreamLiveX/App/AppConfiguration.swift"),
    read("ios/StreamLiveX/Web/WebContainer.swift"),
    read("ios/StreamLiveX/Resources/Info.plist"),
    read("codemagic.yaml"),
    read("ios-web/src/main.tsx"),
    read("package.json"),
    read("ios/project.yml"),
  ]);
  assert.match(configuration, /Bundle\.main\.url\(forResource: "index", withExtension: "html", subdirectory: "Web"\)/);
  assert.match(container, /loadFileURL\(bundledURL/);
  assert.doesNotMatch(plist, /https:\/\/streamlivex\.com\/app|SLXWebAppURL/);
  assert.match(workflow, /VITE_UI_BUILD_SHA=.*npm run build:ios-web/);
  assert.match(entry, /__SLX_UI_SOURCE__ = "Bundled iOS Branch"/);
  assert.match(entry, /__SLX_PROXY_ORIGIN__ = serviceOrigin/);
  assert.match(packageJson, /"build:ios-web"/);
  assert.match(project, /path: StreamLiveX\/Resources\/Web\s+type: folder/);
});

test("iOS supports portrait and iPad without a tvOS target", async () => {
  const [plist, project] = await Promise.all([read("ios/StreamLiveX/Resources/Info.plist"), read("ios/StreamLiveX.xcodeproj/project.pbxproj")]);
  assert.match(plist, /UIInterfaceOrientationPortrait/);
  assert.match(project, /TARGETED_DEVICE_FAMILY = "1,2"/);
  assert.doesNotMatch(project, /appletvos|product-type\.application\.appletv/i);
});

test("native player exposes seek, audio and subtitle controls", async () => {
  const [controller, view] = await Promise.all([read("ios/StreamLiveX/Player/NativePlayerController.swift"), read("ios/StreamLiveX/Player/NativePlayerView.swift")]);
  assert.match(controller, /func seek\(by seconds: Double\)/);
  assert.match(controller, /audioTrackNames/);
  assert.match(controller, /videoSubTitlesNames/);
  assert.match(controller, /addPlaybackSlave/);
  assert.match(view, /gobackward\.10/);
  assert.match(view, /confirmationDialog\("Altyazı"/);
});

test("downloads use AVAssetDownloadURLSession and PIN uses Keychain", async () => {
  const [downloads, screen, parental, pin] = await Promise.all([read("ios/StreamLiveX/Downloads/NativeDownloadManager.swift"), read("ios/StreamLiveX/Downloads/NativeDownloadsScreen.swift"), read("ios/StreamLiveX/Security/NativeParentalScreen.swift"), read("ios/StreamLiveX/Security/SecurePinStore.swift")]);
  assert.match(downloads, /AVAssetDownloadURLSession/);
  assert.match(downloads, /pathExtension\.lowercased\(\) == "m3u8"/);
  assert.match(screen, /AsyncImage\(url: item\.artworkURL\)/);
  assert.match(screen, /ByteCountFormatter/);
  assert.match(parental, /Kategori Kısıtlamaları/);
  assert.match(parental, /IOSPinPad/);
  assert.match(pin, /SecItemAdd/);
  assert.match(pin, /SHA256\.hash/);
  assert.doesNotMatch(pin, /UserDefaults/);
});
