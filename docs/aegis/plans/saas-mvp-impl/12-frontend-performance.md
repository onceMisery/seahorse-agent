# 块K · 前端性能优化 — Web 性能优化方案

> 文档定位：SaaS MVP 执行计划第 12 篇。功能增强系列之「前端性能」。  
> 关键属性：**P1 优先级、用户体验直接影响、独立可实施**。  
> 编写依据：2026-06-05 前端性能分析 + Web Vitals 最佳实践。  
> 工作量口径：1 人 × 2-3 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 首屏加载慢（3-5 秒，用户等待过久）
- ❌ 打包体积大（2.5MB，全量加载）
- ❌ 无代码分割（所有页面一次性加载）
- ❌ 无资源缓存策略（每次刷新重新下载）
- ❌ 无性能监控（不知道用户真实体验）
- ❌ 图片未优化（原图直接加载）

**用户痛点**：
- 😤 首次访问等待时间长
- 😤 网络慢时完全无法使用
- 😤 切换页面卡顿
- 😤 移动端流量消耗大

**本方案价值**：
- ✅ 代码分割（按路由懒加载，首屏 -60%）
- ✅ 资源预加载（关键资源优先）
- ✅ 缓存策略（Service Worker + HTTP 缓存）
- ✅ 打包优化（Tree Shaking、压缩、CDN）
- ✅ 首屏优化（骨架屏、SSR 可选）
- ✅ 性能监控（Web Vitals 埋点）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 | 目标 |
|------|------|--------|------|------|
| G1 | 代码分割与懒加载 | **P0** | 全量加载 ❌ | 首屏 -60% |
| G2 | 资源预加载与优先级 | **P0** | 无 ❌ | LCP < 2.5s |
| G3 | 缓存策略（Service Worker） | P1 | 无 ❌ | 二次访问 < 1s |
| G4 | 打包优化（Tree Shaking） | **P0** | 未优化 ⚠️ | 体积 -40% |
| G5 | 首屏优化（骨架屏、SSR） | P1 | 无骨架屏 ⚠️ | FCP < 1.5s |
| G6 | 性能监控（Web Vitals） | **P0** | 无 ❌ | 实时监控 |

### 1.2 Web Vitals 目标

| 指标 | 含义 | 现状 | 目标 |
|------|------|------|------|
| **LCP**（Largest Contentful Paint） | 最大内容绘制 | 4.5s | < 2.5s |
| **INP**（Interaction to Next Paint） | 交互到下次绘制（替代 FID，2024 年起） | 200ms | < 200ms |
| **CLS**（Cumulative Layout Shift） | 累积布局偏移 | 0.2 | < 0.1 |
| **FCP**（First Contentful Paint） | 首次内容绘制 | 2.8s | < 1.5s |
| **TTFB**（Time to First Byte） | 首字节时间 | 800ms | < 800ms |

> **注意**：FID（First Input Delay）已于 2024 年被 Google 正式替换为 INP（Interaction to Next Paint）。
> INP 衡量所有交互的响应延迟，而非仅首次交互，更能反映真实用户体验。
> TTI（Time to Interactive）也已不再作为 Core Web Vitals 指标，改用 TTFB 衡量服务器响应速度。

### 1.3 验收信号

#### P0 验收

1. ✅ 首屏加载时间 < 2.5s（LCP，原 4.5s）
2. ✅ 打包体积 < 1.5MB（原 2.5MB）
3. ✅ 路由懒加载（按需加载，非全量）
4. ✅ Web Vitals 监控上报

#### P1 验收

5. ⚠️ Service Worker 缓存生效（二次访问 < 1s）
6. ⚠️ 骨架屏显示（首屏白屏时间 < 500ms）

---

## 2. 现状（性能测试分析）

### 2.1 Lighthouse 测试结果

**测试环境**：Chrome DevTools，Fast 3G 网络

```
Performance: 45/100 ⚠️
├── First Contentful Paint: 2.8s
├── Largest Contentful Paint: 4.5s
├── Time to Interactive: 5.2s
├── Speed Index: 4.1s
└── Total Blocking Time: 800ms

问题：
❌ 打包体积过大（2.5MB）
❌ 无代码分割（全量加载）
❌ 无缓存策略
❌ 图片未压缩
```

### 2.2 打包分析

```bash
npm run build -- --stats
npx webpack-bundle-analyzer dist/stats.json
```

**结果**：
```
dist/
├── main.js (1.2MB) ⚠️ 过大
│   ├── react-dom: 120KB
│   ├── antd: 500KB ⚠️ 全量引入
│   ├── lodash: 80KB ⚠️ 全量引入
│   └── 业务代码: 500KB
├── vendor.js (800KB)
└── assets/ (500KB)
```

**问题**：
- ❌ Ant Design 全量引入（应按需引入）
- ❌ Lodash 全量引入（应 Tree Shaking）
- ❌ 无代码分割（所有路由一次性加载）

---

## 3. 技术方案

### 3.1 代码分割与懒加载（P0）

#### 3.1.1 路由懒加载

**优化前**：
```typescript
// App.tsx
import Dashboard from './pages/Dashboard';
import KnowledgeBase from './pages/KnowledgeBase';
import ChatWindow from './pages/ChatWindow';

function App() {
  return (
    <Routes>
      <Route path="/dashboard" element={<Dashboard />} />
      <Route path="/kb" element={<KnowledgeBase />} />
      <Route path="/chat" element={<ChatWindow />} />
    </Routes>
  );
}
```

**优化后**：
```typescript
// App.tsx
import { lazy, Suspense } from 'react';
import { Spin } from 'antd';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const KnowledgeBase = lazy(() => import('./pages/KnowledgeBase'));
const ChatWindow = lazy(() => import('./pages/ChatWindow'));

function App() {
  return (
    <Routes>
      <Route path="/dashboard" element={
        <Suspense fallback={<Spin size="large" />}>
          <Dashboard />
        </Suspense>
      } />
      {/* 其他路由同理 */}
    </Routes>
  );
}
```

**效果**：首屏体积 1.2MB → 0.5MB（-58%）

#### 3.1.2 组件懒加载

**大型组件懒加载**：
```typescript
// 编辑器组件（500KB）懒加载
const MarkdownEditor = lazy(() => import('./components/MarkdownEditor'));

function KnowledgeBaseEdit() {
  return (
    <Suspense fallback={<Skeleton active />}>
      <MarkdownEditor />
    </Suspense>
  );
}
```

#### 3.1.3 动态导入

**按需加载工具库**：
```typescript
// 优化前
import * as XLSX from 'xlsx';

// 优化后（按需导入）
async function exportExcel(data: any[]) {
  const XLSX = await import('xlsx');  // 仅在导出时加载（500KB）
  const ws = XLSX.utils.json_to_sheet(data);
  // ...
}
```

---

### 3.2 资源预加载与优先级（P0）

#### 3.2.1 Preload 关键资源

**HTML 头部**：
```html
<!-- 预加载关键字体 -->
<link rel="preload" href="/fonts/inter.woff2" as="font" type="font/woff2" crossorigin>

<!-- 预加载首屏组件 -->
<link rel="preload" href="/static/js/main.chunk.js" as="script">

<!-- 预加载关键 CSS -->
<link rel="preload" href="/static/css/main.css" as="style">
```

#### 3.2.2 Prefetch 次要资源

```html
<!-- 预获取下一页可能用到的资源 -->
<link rel="prefetch" href="/static/js/knowledge-base.chunk.js">
<link rel="prefetch" href="/static/js/chat-window.chunk.js">
```

#### 3.2.3 动态 Prefetch

```typescript
// 路由预加载（鼠标悬停时）
function NavLink({ to, children }: { to: string; children: React.ReactNode }) {
  const handleMouseEnter = () => {
    // 预加载目标路由
    const link = document.createElement('link');
    link.rel = 'prefetch';
    link.href = `/static/js/${to}.chunk.js`;
    document.head.appendChild(link);
  };
  
  return (
    <Link to={to} onMouseEnter={handleMouseEnter}>
      {children}
    </Link>
  );
}
```

---

### 3.3 缓存策略（P1）

#### 3.3.1 HTTP 缓存

**Nginx 配置**：
```nginx
location /static/ {
    # 静态资源强缓存 1 年（带 hash 文件名）
    expires 1y;
    add_header Cache-Control "public, immutable";
}

location /index.html {
    # 入口文件协商缓存
    add_header Cache-Control "no-cache";
    etag on;
}

location /api/ {
    # API 不缓存
    add_header Cache-Control "no-store";
}
```

#### 3.3.2 Service Worker（基于 vite-plugin-pwa）

> **架构决策**：不使用手写 `sw.js`（手动维护 `urlsToCache` 在真实项目中不可维护，每次新版本都需更新）。
> 改用 `vite-plugin-pwa`（基于 Workbox），自动生成 precache manifest，支持版本更新和离线缓存。

**依赖**：
```bash
npm install --save-dev vite-plugin-pwa
```

**Vite 配置**：
```typescript
// vite.config.ts
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',  // 自动更新 Service Worker
      includeAssets: ['favicon.ico', 'robots.txt', 'apple-touch-icon.png'],
      manifest: {
        name: 'Seahorse Agent',
        short_name: 'Seahorse',
        description: 'Enterprise AI Agent Platform',
        theme_color: '#1677ff',
        background_color: '#ffffff',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
      workbox: {
        // 自动 precache 所有构建产物（JS/CSS/字体等）
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        // 运行时缓存策略
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/fonts\.googleapis\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'google-fonts-cache',
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 365 },
            },
          },
          {
            urlPattern: /^https:\/\/api\..*/i,
            handler: 'NetworkFirst',  // API 请求优先网络，失败回退缓存
            options: {
              cacheName: 'api-cache',
              expiration: { maxEntries: 50, maxAgeSeconds: 60 * 5 },
            },
          },
        ],
      },
    }),
  ],
});
```

> **优势**：
> - ✅ 自动生成 precache manifest（无需手动维护 `urlsToCache`）
> - ✅ 支持 `autoUpdate`（新版本自动激活）
> - ✅ 运行时缓存策略（`CacheFirst`/`NetworkFirst`/`StaleWhileRevalidate`）
> - ✅ 自动生成 Web App Manifest（PWA 支持）

---

### 3.4 打包优化（P0）

#### 3.4.1 Vite 配置优化

```typescript
// vite.config.ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { visualizer } from 'rollup-plugin-visualizer';

export default defineConfig({
  plugins: [
    react(),
    visualizer({ open: true })  // 打包分析
  ],
  
  build: {
    target: 'es2015',
    
    // 代码分割
    rollupOptions: {
      output: {
        manualChunks: {
          // 框架代码
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          
          // UI 库
          'antd-vendor': ['antd', '@ant-design/icons'],
          
          // 工具库
          'utils-vendor': ['lodash-es', 'axios', 'dayjs'],
          
          // 编辑器（大型组件单独分包）
          'editor': ['@toast-ui/react-editor'],
        },
      },
    },
    
    // 压缩
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,  // 移除 console
        drop_debugger: true,
      },
    },
    
    // Chunk 大小警告阈值
    chunkSizeWarningLimit: 500,
  },
  
  // 依赖预构建
  optimizeDeps: {
    include: ['react', 'react-dom', 'antd'],
  },
});
```

#### 3.4.2 Tree Shaking

**Lodash 按需引入**：
```typescript
// 优化前
import _ from 'lodash';
_.debounce(() => {}, 300);

// 优化后
import debounce from 'lodash-es/debounce';
debounce(() => {}, 300);
```

**Ant Design 按需引入**（Vite 自动支持）：
```typescript
// 自动按需引入（无需手动配置）
import { Button, Table } from 'antd';
```

#### 3.4.3 图片优化

**使用 WebP 格式**：
```typescript
// components/OptimizedImage.tsx
export const OptimizedImage = ({ src, alt }: { src: string; alt: string }) => {
  const webpSrc = src.replace(/\.(jpg|png)$/, '.webp');
  
  return (
    <picture>
      <source srcSet={webpSrc} type="image/webp" />
      <img src={src} alt={alt} loading="lazy" />
    </picture>
  );
};
```

**图片懒加载**：
```typescript
<img src={url} loading="lazy" alt="..." />
```

---

### 3.5 首屏优化（P1）

#### 3.5.1 骨架屏

```typescript
// components/PageSkeleton.tsx
import { Skeleton } from 'antd';

export const DashboardSkeleton = () => (
  <div style={{ padding: 24 }}>
    <Skeleton active paragraph={{ rows: 4 }} />
    <Skeleton active paragraph={{ rows: 6 }} style={{ marginTop: 24 }} />
  </div>
);

// App.tsx
const Dashboard = lazy(() => import('./pages/Dashboard'));

<Suspense fallback={<DashboardSkeleton />}>
  <Dashboard />
</Suspense>
```

#### 3.5.2 SSR（延迟至 V2）

> **架构决策**：MVP 阶段不实施 SSR。原因：
> 1. SSR 增加服务器复杂度和运维成本（需 Node.js SSR 服务）
> 2. 当前 CSR + 骨架屏 + 路由懒加载已能将 FCP 优化到 < 1.5s
> 3. Seahorse Agent 是 SaaS 应用（登录后使用），SEO 不是核心需求
>
> **MVP 替代方案**：CSR + 骨架屏（DashboardSkeleton）+ 路由预加载（Prefetch）

如 V2 阶段确需 SSR，推荐使用 **Next.js** 或 **Remix** 重写前端，而非在 Vite 项目中自行实现 SSR。

---

### 3.6 性能监控（P0）

#### 3.6.1 Web Vitals 监控

```typescript
// utils/performance.ts
import { onCLS, onINP, onFCP, onLCP, onTTFB } from 'web-vitals';

function sendToAnalytics(metric: Metric) {
  // 上报到后端
  fetch('/api/metrics', {
    method: 'POST',
    body: JSON.stringify({
      name: metric.name,
      value: metric.value,
      rating: metric.rating,    // 'good' | 'needs-improvement' | 'poor'
      delta: metric.delta,
      id: metric.id,
    }),
    headers: { 'Content-Type': 'application/json' },
    // 使用 keepalive 确保页面卸载时也能上报
    keepalive: true,
  });
}

// 监控所有 Core Web Vitals 指标
onCLS(sendToAnalytics);    // Cumulative Layout Shift
onINP(sendToAnalytics);    // Interaction to Next Paint（替代 FID）
onFCP(sendToAnalytics);    // First Contentful Paint
onLCP(sendToAnalytics);    // Largest Contentful Paint
onTTFB(sendToAnalytics);   // Time to First Byte
```

**初始化**：
```typescript
// index.tsx
import './utils/performance';
```

#### 3.6.2 性能埋点

```typescript
// utils/performanceTracker.ts
export class PerformanceTracker {
  
  // 页面加载时间（使用 Navigation Timing Level 2）
  static trackPageLoad() {
    // 使用 PerformanceObserver 监听 navigation entry
    const observer = new PerformanceObserver((list) => {
      const entries = list.getEntriesByType('navigation') as PerformanceNavigationTiming[];
      if (entries.length === 0) return;
      
      const nav = entries[0];
      const metrics = {
        dns: nav.domainLookupEnd - nav.domainLookupStart,
        tcp: nav.connectEnd - nav.connectStart,
        ttfb: nav.responseStart - nav.requestStart,
        domReady: nav.domContentLoadedEventEnd - nav.startTime,
        loadComplete: nav.loadEventEnd - nav.startTime,
      };
      
      console.log('Page Load Metrics:', metrics);
      this.report('page_load', metrics);
    });
    
    observer.observe({ type: 'navigation', buffered: true });
  }
  
  // 路由切换时间
  static trackRouteChange(routeName: string) {
    const startTime = performance.now();
    
    return () => {
      const endTime = performance.now();
      const duration = endTime - startTime;
      
      console.log(`Route ${routeName} rendered in`, duration, 'ms');
      
      this.report('route_change', { route: routeName, duration });
    };
  }
  
  private static report(event: string, data: Record<string, unknown>) {
    // 使用 sendBeacon 确保页面卸载时也能上报
    const payload = JSON.stringify({ event, ...data, timestamp: Date.now() });
    const sent = navigator.sendBeacon('/api/metrics', payload);
    
    if (!sent) {
      // fallback to fetch with keepalive
      fetch('/api/metrics', {
        method: 'POST',
        body: payload,
        headers: { 'Content-Type': 'application/json' },
        keepalive: true,
      });
    }
  }
}

// 使用
PerformanceTracker.trackPageLoad();
```

---

## 4. 实施步骤

### Day 1：代码分割 + 打包优化
- 上午：路由懒加载 + 组件懒加载（2h）
- 下午：Vite 配置优化 + Tree Shaking（3h）

### Day 2：缓存策略 + 资源预加载
- 上午：HTTP 缓存 + Service Worker（3h）
- 下午：Preload/Prefetch（2h）

### Day 3：首屏优化 + 监控
- 上午：骨架屏 + 图片优化（2h）
- 下午：Web Vitals 监控（2h）

---

## 5. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ Lighthouse 评分 > 90
✅ 打包体积 < 1.5MB

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06  
**修订说明**：FID→INP、TTI→TTFB；performance.timing→PerformanceObserver；SW→vite-plugin-pwa；SSR 延迟至 V2
