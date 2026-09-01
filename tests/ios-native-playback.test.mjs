import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("iOS uses the native bridge for preview and full-screen playback", async () => {
  const playerApp = await read("app/PlayerApp.tsx");

  assert.match(playerApp, /setNativePreview\(Boolean\(desktopBridge\(\)\)\)/);
  assert.match(playerApp, /useState\(\(\)=>Boolean\(desktopBridge\(\)\)\)/);
  assert.doesNotMatch(playerApp, /Boolean\(desktopBridge\(\)\)&&!isIOSNativeHost\(\)/);
});

test("iOS native playback starts with the provider URL and VLC headers", async () => {
  const controller = await read("ios/StreamLiveX/Player/NativePlayerController.swift");

  assert.match(controller, /import MobileVLCKit/);
  assert.match(controller, /let candidates = \[source\]/);
  assert.match(controller, /VLC\/3\.0 StreamLiveX-iOS/);
  assert.doesNotMatch(controller, /api\/stream/);
});

test("Codemagic installs MobileVLCKit and archives the CocoaPods workspace", async () => {
  const [podfile, workflow] = await Promise.all([
    read("ios/Podfile"),
    read("codemagic.yaml"),
  ]);

  assert.match(podfile, /pod ['"]MobileVLCKit['"]/);
  assert.match(workflow, /pod install/);
  assert.match(workflow, /--workspace "StreamLiveX\.xcworkspace"/);
  assert.doesNotMatch(workflow, /build-ipa[\s\S]*--project "StreamLiveX\.xcodeproj"/);
});
