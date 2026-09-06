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
  build: {
    rollupOptions: {
      output: {
        // 重依赖独立分包：管理台页面已按路由懒加载，这些 vendor 包只被
        // 引用到的页面按需拉取，避免全部进入首屏主包。
        manualChunks: {
          "react-vendor": ["react", "react-dom", "react-router-dom"],
          mermaid: ["mermaid"],
          codemirror: [
            "@uiw/react-codemirror",
            "@uiw/codemirror-theme-monokai",
            "@codemirror/lang-javascript",
            "@codemirror/lang-json",
            "@codemirror/lang-python"
          ],
          charts: ["recharts"],
          katex: ["katex"],
          highlighter: ["react-syntax-highlighter"],
          flow: ["@xyflow/react"]
        }
      }
    }
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    globals: true
  }
});
