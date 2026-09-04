import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// Tauri 固定使用 1420 端口作为 dev server
export default defineConfig({
  plugins: [vue()],
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
  },
  envPrefix: ["VITE_", "TAURI_"],
  build: {
    target: "es2021",
    minify: "esbuild",
    sourcemap: false,
  },
});
