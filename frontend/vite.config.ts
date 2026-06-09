import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

const apiProxyTarget = process.env.VITE_PROXY_TARGET || "http://localhost:9090";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: apiProxyTarget,
        changeOrigin: true,
        secure: false,
        rewrite: (path: string) => path.replace(/^\/api/, "")
      }
    }
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    globals: true
  }
});
