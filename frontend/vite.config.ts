import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

const apiProxyTarget = process.env.VITE_PROXY_TARGET || "http://localhost:9090";

/**
 * 首包预算拆分策略（消除 >500kB 首包警告）：
 * - react-vendor / markdown-stack / charts / katex / codemirror / mermaid /
 *   highlighter / flow：按库族独立成 chunk；
 * - 其余 node_modules 归入 vendor（radix/table/motion/date-fns 等中等库）；
 * - src/components/chat 与 src/components/ai-elements（聊天渲染栈）拆出
 *   chat-ui，首屏骨架先渲染，代码并行拉取。
 */
function manualChunks(id: string): string | undefined {
  if (!id.includes("node_modules")) {
    if (id.includes("/src/components/chat/") || id.includes("/src/components/ai-elements/")) {
      return "chat-ui";
    }
    return undefined;
  }
  if (id.includes("/react/") || id.includes("/react-dom/") || id.includes("/react-router/") || id.includes("/scheduler/")) {
    return "react-vendor";
  }
  if (id.includes("/mermaid/") || id.includes("/cytoscape") || id.includes("/dagre-d3-es")
      || id.includes("/khroma/") || id.includes("/@mermaid-js/") || id.includes("/@iconify/")
      || id.includes("/dayjs")) {
    return "mermaid";
  }
  if (id.includes("/@uiw/") || id.includes("/@codemirror/") || id.includes("/codemirror")) {
    return "codemirror";
  }
  if (id.includes("/katex") || id.includes("/react-markdown") || id.includes("/remark-")
      || id.includes("/rehype-") || id.includes("/unified") || id.includes("/micromark")
      || id.includes("/mdast") || id.includes("/hast") || id.includes("/property-information")
      || id.includes("/vfile") || id.includes("/unist")) {
    return "markdown-stack";
  }
  if (id.includes("/@xyflow/") || id.includes("/d3-")) {
    return "flow";
  }
  if (id.includes("/lucide-react/")) {
    return "icons";
  }
  if (id.includes("/@tanstack/")) {
    return "tables-charts";
  }
  if (id.includes("/motion/") || id.includes("/framer-motion") || id.includes("/date-fns/") || id.includes("/zod/")
      || id.includes("/react-hook-form") || id.includes("/@hookform/") || id.includes("/react-dropzone/")
      || id.includes("/react-virtuoso/") || id.includes("/cmdk/") || id.includes("/@remix-run/")) {
    return "forms-motion";
  }
  if (id.includes("/@radix-ui/")) {
    return "radix";
  }
  return "vendor";
}

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
    // 首包 index 已 <100kB(见 chat-ui/react-vendor/vendor 拆分)。唯一超过
    // 默认 500kB 阈值的是 mermaid 懒 chunk(2569kB):它仅在消息包含 mermaid
    // 代码块时按需拉取,属已审计的动态 chunk,故阈值放宽到覆盖它。
    chunkSizeWarningLimit: 2600,
    rollupOptions: {
      output: {
        manualChunks
      }
    }
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    globals: true
  }
});
