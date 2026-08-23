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
  plugins: [react()],
  // Tailwind v4 postcss yapılandırması depo kökünde; SPA kökünden bulunması için elle veriyoruz.
  css: { postcss: __dirname },
  build: {
    outDir: path.resolve(__dirname, "smart-tv/spa/dist"),
    emptyOutDir: true,
    target: "es2019", // TV tarayıcı motorları için biraz daha geniş uyum
  },
});
