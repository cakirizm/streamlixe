// StreamLiveX — Smart TV (webOS/Tizen) için saf istemci (SPA) derleme yapılandırması.
// Ana vite.config.ts (Vinext/Cloudflare/RSC) worker tabanlı sunucu derlemesi yapar; bu config
// ise PlayerApp'i TV paketine gömmek üzere statik bir SPA olarak derler.
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

const spaRoot = path.resolve(__dirname, "smart-tv/spa");

export default defineConfig({
  root: spaRoot,
  base: "./", // TV paketinde göreli varlık yolları gerekir (file:// / app:// origin)
  // Kök-mutlak varlıklar (ör. /streamlivex-logo.jpeg) paket köküne gömülür ki TV'de
  // yerel origin kökünden çözülebilsin. Bu klasör build script'i tarafından public/'ten
  // doldurulur (bkz. build-webos.sh) ve git'e girmez.
  publicDir: path.resolve(spaRoot, "public"),
  plugins: [react()],
  // Tailwind v4 postcss yapılandırması depo kökünde; SPA kökünden bulunması için elle veriyoruz.
  css: { postcss: __dirname },
  build: {
    outDir: path.resolve(__dirname, "smart-tv/spa/dist"),
    emptyOutDir: true,
    target: "es2019", // TV tarayıcı motorları için biraz daha geniş uyum
  },
});
