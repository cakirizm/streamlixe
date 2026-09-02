import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  root: fileURLToPath(new URL("./ios-web", import.meta.url)),
  base: "./",
  publicDir: fileURLToPath(new URL("./public", import.meta.url)),
  plugins: [react()],
  build: {
    outDir: fileURLToPath(new URL("./ios/StreamLiveX/Resources/Web", import.meta.url)),
    emptyOutDir: true,
    sourcemap: false,
    target: "safari17",
  },
});
