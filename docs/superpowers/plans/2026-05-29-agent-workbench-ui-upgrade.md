# Agent Workbench UI Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Seahorse Agent 当前聊天页、Artifact、Trace、管理后台和记忆中心升级为面向 C 端 Web Agent 的 Agent Workbench，而不是继续做“深海发光聊天皮肤”。

**Architecture:** 保留现有 React 18 + Vite + Tailwind 3 + Zustand + Radix UI + lucide-react 技术栈。聊天数据仍由 `chatStore` 和 `artifactStore` 管理，新增 `workbenchStore` 只管理 UI 选择态；右侧 `WorkspaceInspector` 统一承载 Artifact、Timeline、Sources、Approvals、Cost、Memory、A2UI-lite 预览。管理后台保留现有路由能力，通过 Agent Console / Agent Inspector 重组信息架构。

**Tech Stack:** React 18, Vite 5, TypeScript, Tailwind CSS 3, Zustand, Radix UI Tabs/Dialog/Tooltip, lucide-react, react-resizable-panels, react-virtuoso, Recharts. Phase 1 不新增运行时依赖；仅建议新增 Vitest/Testing Library 作为前端测试 devDependencies。

---

## Design Inputs

| 来源项目文档 | 可借鉴点 | Seahorse 落地点 |
| --- | --- | --- |
| `D:\code\docs\stackblitz-bolt.new\12-workbench-and-editor.md` | Workbench 统一承载编辑/预览/状态，Artifact 能进入工作区 | `/chat` 改为聊天 + Workspace Inspector，而不是消息内堆叠所有 Trace |
| `D:\code\docs\stackblitz-bolt.new\11-chat-interface-system.md` | Prompt enhancer、Artifact 生命周期、工作台联动 | `ChatInput` 增加 Prompt Enhancer；Artifact 从右侧工作区打开、复制、下载、全屏 |
| `D:\code\docs\stackblitz-bolt.new\13-ui-components-library.md` | 语义化 theme tokens，按表面层级命名 | 收敛 `tokens.css` / `globals.css`，减少多色主题造成的演示感 |
| `D:\code\docs\CopilotKit-CopilotKit\21-a2ui-declarative-rendering.md` | Agent-to-UI 只发结构化组件树，客户端白名单渲染 | A2UI-lite：只支持本项目安全组件目录，不允许 HTML/JS 任意渲染 |
| `D:\code\docs\CopilotKit-CopilotKit\26-web-inspector-debugging.md` | Inspector 以 Events / State / Tools / Context 等视图调试 agent runtime | Admin 新增 Agent Inspector，基于 snapshot、cost、events 读 API 做调试视图 |
| `D:\code\docs\OpenBMB-ChatDev\3-web-console-guide.md` | `/launch` 分屏执行界面和控制台视图模式 | Chat 工作台采用左对话、右工作区；Admin 保持控制台式信息架构 |
| `D:\code\docs\mastra-ai-mastra\mastra-design-highlights.md` | Processor/HITL/eval/cost 形成运营闭环 | Inspector 和 Agent Console 把审批、成本、eval、失败恢复作为一等信息 |

## Scope Boundaries

- 不复制 bolt.new 的 WebContainer、终端、文件系统或完整 IDE。
- 不引入完整 CopilotKit、A2UI、Framer Motion 运行时依赖。
- 不开放任意 HTML、JS、React 组件由 Agent 直接渲染。
- 不改变 C 端 product mode 安全边界；高级 Admin 入口仍由 `frontend/src/config/productMode.ts` 控制。
- 不重写 `chatStore` 的 SSE 主链路；仅把展示和选择态拆到工作台组件。
- 不把企业版 Agent Mesh、MCP 管理、A2A 作为默认用户能力。

## File Map

### Existing files to modify

- `frontend/package.json` - 增加前端测试脚本和 devDependencies。
- `frontend/package-lock.json` - 随测试依赖更新。
- `frontend/vite.config.ts` - 配置 Vitest 环境和测试 setup。
- `frontend/src/types/index.ts` - 增加 Workbench/A2UI-lite/agent event 类型。
- `frontend/src/stores/chatStore.ts` - 在消息选择、snapshot 更新后同步 Workbench active message。
- `frontend/src/stores/artifactStore.ts` - 保留 Artifact 数据源，新增按 messageId 读取的 selector。
- `frontend/src/pages/ChatPage.tsx` - 改为三段式 workbench 布局和移动端 inspector sheet。
- `frontend/src/components/chat/MessageItem.tsx` - 增加打开 Inspector 的交互入口。
- `frontend/src/components/chat/MessageList.tsx` - 保持虚拟列表，给消息行提供稳定 active 状态。
- `frontend/src/components/chat/AgentTracePanel.tsx` - 缩减为消息内摘要，详细信息迁移到 Inspector tabs。
- `frontend/src/components/chat/ArtifactPanel.tsx` - 拆出可复用 Artifact preview，迁移为 Inspector tab 使用。
- `frontend/src/components/chat/ChatInput.tsx` - 增加 Prompt Enhancer 入口、应用增强草稿。
- `frontend/src/components/chat/SourceList.tsx` - 抽取来源条目渲染，供 Inspector tab 复用。
- `frontend/src/components/chat/ApprovalCard.tsx` - 保持审批动作，供 Inspector tab 复用。
- `frontend/src/services/agentRunService.ts` - 增加 run events 查询。
- `frontend/src/services/userMemoryService.ts` - 保持现有 API，Memory Center 本地过滤复用。
- `frontend/src/pages/admin/AdminLayout.tsx` - 管理端导航重组为 Agent Console / Agent Inspector。
- `frontend/src/router.tsx` - 增加 Agent Inspector 路由。
- `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx` - 改名或包装为 Agent Console 信息架构。
- `frontend/src/pages/admin/traces/RagTracePage.tsx` - 保留 RAG Trace，和 Agent Inspector 互相跳转。
- `frontend/src/pages/MemoryCenterPage.tsx` - 拆分组件，补筛选、来源跳转、错误状态。
- `frontend/src/styles/tokens.css` - 增加 neutral workbench tokens。
- `frontend/src/styles/globals.css` - 收敛主题变量、工作台布局、inspector、A2UI-lite、memory center 样式。
- `frontend/src/stores/themeStore.ts` - 默认主题收敛到 workbench neutral，保留旧主题读取兼容。
- `frontend/src/components/layout/Sidebar.tsx` - 主题选择 UI 收敛，减少多彩 swatch 入口。
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentRunController.java` - 增加 `/api/agent-runs/{runId}/events`。
- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebApiContractTests.java` - 覆盖 events API。

### New files to create

- `frontend/src/test/setup.ts`
- `frontend/src/stores/workbenchStore.ts`
- `frontend/src/stores/workbenchStore.test.ts`
- `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`
- `frontend/src/components/chat/workbench/WorkspaceInspector.test.tsx`
- `frontend/src/components/chat/workbench/InspectorTabButton.tsx`
- `frontend/src/components/chat/workbench/InspectorEmptyState.tsx`
- `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx`
- `frontend/src/components/chat/workbench/TimelineInspectorTab.tsx`
- `frontend/src/components/chat/workbench/SourcesInspectorTab.tsx`
- `frontend/src/components/chat/workbench/ApprovalsInspectorTab.tsx`
- `frontend/src/components/chat/workbench/CostQuotaInspectorTab.tsx`
- `frontend/src/components/chat/workbench/MemoryInspectorTab.tsx`
- `frontend/src/components/chat/workbench/MessageRunSummary.tsx`
- `frontend/src/components/chat/prompt/promptEnhancer.ts`
- `frontend/src/components/chat/prompt/promptEnhancer.test.ts`
- `frontend/src/components/chat/prompt/PromptEnhancerButton.tsx`
- `frontend/src/components/chat/prompt/PromptEnhancerDialog.tsx`
- `frontend/src/components/a2ui-lite/a2uiTypes.ts`
- `frontend/src/components/a2ui-lite/a2uiRegistry.tsx`
- `frontend/src/components/a2ui-lite/A2UILiteRenderer.tsx`
- `frontend/src/components/a2ui-lite/A2UILiteRenderer.test.tsx`
- `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx`
- `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.test.tsx`
- `frontend/src/pages/admin/agent-inspector/components/AgentEventStream.tsx`
- `frontend/src/pages/admin/agent-inspector/components/AgentStateView.tsx`
- `frontend/src/pages/admin/agent-inspector/components/AgentContextView.tsx`
- `frontend/src/pages/admin/agent-inspector/components/AgentToolsView.tsx`
- `frontend/src/pages/admin/agent-console/AgentConsolePage.tsx`
- `frontend/src/components/memory/MemoryToolbar.tsx`
- `frontend/src/components/memory/MemoryPrivacyBanner.tsx`
- `frontend/src/components/memory/MemoryCard.tsx`
- `frontend/src/components/memory/MemoryEmptyState.tsx`

---

## Task 0: Frontend Test Harness

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/vite.config.ts`
- Create: `frontend/src/test/setup.ts`

- [ ] **Step 1: Install dev-only test dependencies**

Run:

```bash
cd frontend
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
```

Expected: `package.json` and `package-lock.json` contain the new devDependencies.

- [ ] **Step 2: Add scripts**

In `frontend/package.json`, add:

```json
{
  "scripts": {
    "test": "vitest --run",
    "test:watch": "vitest"
  }
}
```

Keep existing `dev`, `build`, `preview`, `lint`, and `format` scripts.

- [ ] **Step 3: Configure Vite test environment**

Update `frontend/vite.config.ts` so the exported config includes:

```ts
test: {
  environment: "jsdom",
  setupFiles: "./src/test/setup.ts",
  globals: true
}
```

If TypeScript reports that `test` is not part of Vite config, import from `vitest/config` instead of `vite`:

```ts
import { defineConfig } from "vitest/config";
```

- [ ] **Step 4: Add setup file**

Create `frontend/src/test/setup.ts`:

```ts
import "@testing-library/jest-dom/vitest";
```

- [ ] **Step 5: Verify**

Run:

```bash
cd frontend
npm run test
npm run build
```

Expected: Vitest exits cleanly with no tests or passing tests; Vite production build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/src/test/setup.ts
git commit -m "test(frontend): add component test harness"
```

---

## Task 1: Workbench Visual Tokens And Theme Cleanup

**Files:**
- Modify: `frontend/src/styles/tokens.css`
- Modify: `frontend/src/styles/globals.css`
- Modify: `frontend/src/stores/themeStore.ts`
- Modify: `frontend/src/components/layout/Sidebar.tsx`

- [ ] **Step 1: Add semantic workbench tokens**

In `frontend/src/styles/tokens.css`, add these tokens under `:root` and `[data-theme="dark"]`:

```css
:root {
  --sh-workbench-bg: #f6f8fb;
  --sh-workbench-panel: #ffffff;
  --sh-workbench-panel-subtle: #f1f5f9;
  --sh-workbench-border: rgba(15, 23, 42, 0.1);
  --sh-workbench-border-strong: rgba(15, 23, 42, 0.16);
  --sh-workbench-accent: #0891b2;
  --sh-workbench-accent-soft: rgba(8, 145, 178, 0.12);
  --sh-workbench-shadow: 0 18px 48px -28px rgba(15, 23, 42, 0.36);
  --sh-radius-panel: 10px;
  --sh-radius-control: 8px;
  --sh-z-sidebar: 30;
  --sh-z-inspector-mobile: 40;
  --sh-z-dialog: 50;
}

[data-theme="dark"] {
  --sh-workbench-bg: #07111f;
  --sh-workbench-panel: #0b1726;
  --sh-workbench-panel-subtle: #0f2033;
  --sh-workbench-border: rgba(148, 163, 184, 0.16);
  --sh-workbench-border-strong: rgba(148, 163, 184, 0.26);
  --sh-workbench-accent: #06b6d4;
  --sh-workbench-accent-soft: rgba(6, 182, 212, 0.14);
  --sh-workbench-shadow: 0 18px 56px -30px rgba(2, 6, 23, 0.86);
}
```

- [ ] **Step 2: Map old theme variables to workbench tokens**

In `frontend/src/styles/globals.css`, update the base theme values so `--theme-bg-deep`, `--theme-bg-elevated`, `--theme-glass-border`, and `--theme-accent` reference the workbench tokens. Keep the old variable names because existing components already use them:

```css
:root {
  --theme-bg-deep: var(--sh-workbench-bg);
  --theme-bg-elevated: var(--sh-workbench-panel);
  --theme-bg-surface: var(--sh-workbench-panel-subtle);
  --theme-glass-border: var(--sh-workbench-border);
  --theme-accent: var(--sh-workbench-accent);
  --theme-accent-muted: var(--sh-workbench-accent-soft);
}
```

- [ ] **Step 3: Add workbench layout classes**

In `frontend/src/styles/globals.css`, add:

```css
.workbench-shell {
  min-height: 100dvh;
  background: var(--sh-workbench-bg);
  color: var(--theme-text-primary);
}

.workbench-panel {
  background: var(--sh-workbench-panel);
  border-color: var(--sh-workbench-border);
}

.workbench-panel-subtle {
  background: var(--sh-workbench-panel-subtle);
  border-color: var(--sh-workbench-border);
}

.workbench-control {
  border-radius: var(--sh-radius-control);
  transition: background-color 160ms ease, border-color 160ms ease, transform 160ms ease;
}

.workbench-control:active {
  transform: translateY(1px);
}
```

- [ ] **Step 4: Reduce theme picker choices without breaking stored old keys**

In `frontend/src/stores/themeStore.ts`, keep `ColorThemeKey` compatibility but expose only `marine` and `white` in the picker component. `applyColorTheme` must still remove `theme-purple`, `theme-emerald`, and `theme-amber` classes so old persisted values do not keep stale styling.

Target behavior:

```ts
export const VISIBLE_COLOR_THEME_KEYS: ColorThemeKey[] = ["marine", "white"];
```

- [ ] **Step 5: Update Sidebar theme selector**

In `frontend/src/components/layout/Sidebar.tsx`, render swatches from `VISIBLE_COLOR_THEME_KEYS`. Keep the existing `COLOR_THEMES` labels so persisted users do not see a crash.

- [ ] **Step 6: Verify**

Run:

```bash
cd frontend
npm run build
```

Manual checks:
- `/chat` still opens in light and dark mode.
- Old `localStorage.seahorse-color-theme = "purple"` no longer leaves purple UI after page reload.
- No panel uses large neon glow as the primary visual hierarchy.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/styles/tokens.css frontend/src/styles/globals.css frontend/src/stores/themeStore.ts frontend/src/components/layout/Sidebar.tsx
git commit -m "feat(frontend): add workbench visual tokens"
```

---

## Task 2: Workbench Store And Workspace Inspector Shell

**Files:**
- Create: `frontend/src/stores/workbenchStore.ts`
- Create: `frontend/src/stores/workbenchStore.test.ts`
- Create: `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`
- Create: `frontend/src/components/chat/workbench/WorkspaceInspector.test.tsx`
- Create: `frontend/src/components/chat/workbench/InspectorTabButton.tsx`
- Create: `frontend/src/components/chat/workbench/InspectorEmptyState.tsx`
- Modify: `frontend/src/pages/ChatPage.tsx`
- Modify: `frontend/src/components/chat/MessageItem.tsx`
- Modify: `frontend/src/components/chat/MessageList.tsx`

- [ ] **Step 1: Write store tests**

Create `frontend/src/stores/workbenchStore.test.ts`:

```ts
import { describe, expect, it, beforeEach } from "vitest";
import { useWorkbenchStore } from "@/stores/workbenchStore";

describe("workbenchStore", () => {
  beforeEach(() => {
    useWorkbenchStore.getState().resetWorkbench();
  });

  it("opens the inspector on a selected message and tab", () => {
    useWorkbenchStore.getState().openInspector("message-1", "timeline");
    expect(useWorkbenchStore.getState().activeMessageId).toBe("message-1");
    expect(useWorkbenchStore.getState().activeTab).toBe("timeline");
    expect(useWorkbenchStore.getState().inspectorOpen).toBe(true);
  });

  it("keeps the active message when switching tabs", () => {
    useWorkbenchStore.getState().openInspector("message-2", "artifacts");
    useWorkbenchStore.getState().setActiveTab("sources");
    expect(useWorkbenchStore.getState().activeMessageId).toBe("message-2");
    expect(useWorkbenchStore.getState().activeTab).toBe("sources");
  });
});
```

- [ ] **Step 2: Create store**

Create `frontend/src/stores/workbenchStore.ts`:

```ts
import { create } from "zustand";

export type WorkbenchTab = "artifacts" | "timeline" | "sources" | "approvals" | "cost" | "memory" | "ui";

interface WorkbenchState {
  activeMessageId: string | null;
  activeTab: WorkbenchTab;
  inspectorOpen: boolean;
  openInspector: (messageId: string, tab?: WorkbenchTab) => void;
  closeInspector: () => void;
  setActiveTab: (tab: WorkbenchTab) => void;
  resetWorkbench: () => void;
}

export const useWorkbenchStore = create<WorkbenchState>((set, get) => ({
  activeMessageId: null,
  activeTab: "timeline",
  inspectorOpen: false,
  openInspector: (messageId, tab) => {
    set({
      activeMessageId: messageId,
      activeTab: tab ?? get().activeTab,
      inspectorOpen: true
    });
  },
  closeInspector: () => set({ inspectorOpen: false }),
  setActiveTab: (tab) => set({ activeTab: tab, inspectorOpen: true }),
  resetWorkbench: () => set({ activeMessageId: null, activeTab: "timeline", inspectorOpen: false })
}));
```

- [ ] **Step 3: Create tab button**

Create `frontend/src/components/chat/workbench/InspectorTabButton.tsx` with a Radix Tabs trigger-friendly button that shows icon, label, and count. Use `Tooltip` for collapsed or narrow states.

Required props:

```ts
export interface InspectorTabButtonProps {
  value: string;
  label: string;
  count?: number;
  children: React.ReactNode;
}
```

- [ ] **Step 4: Create empty state**

Create `frontend/src/components/chat/workbench/InspectorEmptyState.tsx`:
- Use `FileText` or `Activity` lucide icon.
- Text: `选择一条带有运行信息的回复` and `Artifact、来源、审批和成本会在这里集中查看。`
- No card-in-card layout; use centered content inside the inspector surface.

- [ ] **Step 5: Create WorkspaceInspector shell test**

Create `frontend/src/components/chat/workbench/WorkspaceInspector.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { WorkspaceInspector } from "@/components/chat/workbench/WorkspaceInspector";
import type { Message } from "@/types";

const message: Message = {
  id: "assistant-1",
  role: "assistant",
  content: "done",
  timeline: [{ id: "step-1", title: "PLAN", status: "DONE" }],
  sources: [{ id: "source-1", title: "Source one" }],
  artifacts: [{ id: "artifact-1", title: "Report", language: "markdown", code: "# Report", isComplete: true }]
};

describe("WorkspaceInspector", () => {
  it("shows tab counts for active message data", () => {
    render(<WorkspaceInspector message={message} open onClose={() => undefined} />);
    expect(screen.getByText("Artifacts")).toBeInTheDocument();
    expect(screen.getAllByText("1").length).toBeGreaterThan(0);
  });
});
```

- [ ] **Step 6: Create WorkspaceInspector shell**

Create `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`:
- Use `@radix-ui/react-tabs`.
- Header contains active message status, run id if available, close icon.
- Tabs: `Artifacts`, `Timeline`, `Sources`, `Approvals`, `Cost`, `Memory`, `UI`.
- For now, each tab can render the target tab component after later tasks. In this task, render tab labels and `InspectorEmptyState` if `message` is null.

- [ ] **Step 7: Integrate ChatPage layout**

Modify `frontend/src/pages/ChatPage.tsx`:
- Read `activeMessageId`, `inspectorOpen`, `closeInspector` from `useWorkbenchStore`.
- Find `activeMessage` from `messages`.
- Keep `react-resizable-panels`.
- Desktop: left/main chat panel `minSize={44}`, right inspector `defaultSize={34}`, `minSize={26}`, `maxSize={48}`.
- Mobile: bottom sheet height `68dvh`, using existing `fixed inset-x-0 bottom-0` pattern.
- Remove automatic Artifact-only panel opening from `ChatPage`; inspector opens through Artifact event publish or message click.

- [ ] **Step 8: Add message-level open actions**

Modify `frontend/src/components/chat/MessageItem.tsx`:
- For assistant messages with `timeline`, `sources`, `artifacts`, `serverArtifacts`, `approvals`, `quota`, `memories`, or `costSummary`, show a compact `MessageRunSummary` button area.
- On click, call `useWorkbenchStore.getState().openInspector(message.id, "timeline")`.

- [ ] **Step 9: Verify**

Run:

```bash
cd frontend
npm run test -- workspaceInspector workbenchStore
npm run build
```

Manual checks:
- On desktop, the Inspector resizes without covering messages.
- On mobile, Inspector appears as bottom sheet and can close.
- Selecting another assistant message changes Inspector content.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/stores/workbenchStore.ts frontend/src/stores/workbenchStore.test.ts frontend/src/components/chat/workbench frontend/src/pages/ChatPage.tsx frontend/src/components/chat/MessageItem.tsx frontend/src/components/chat/MessageList.tsx
git commit -m "feat(frontend): add workspace inspector shell"
```

---

## Task 3: Artifact Inspector Tab And Safe Preview

**Files:**
- Create: `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx`
- Modify: `frontend/src/components/chat/ArtifactPanel.tsx`
- Modify: `frontend/src/stores/artifactStore.ts`
- Modify: `frontend/src/services/agentArtifactService.ts`
- Modify: `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`

- [ ] **Step 1: Add artifact store selector**

In `frontend/src/stores/artifactStore.ts`, export:

```ts
export function useMessageArtifacts(messageId: string | null) {
  return useArtifactStore((state) => {
    const snapshot = messageId ? state.snapshots[messageId] : undefined;
    return {
      artifacts: snapshot?.artifacts ?? [],
      serverArtifacts: snapshot?.serverArtifacts ?? [],
      version: snapshot?.version ?? 0
    };
  });
}
```

- [ ] **Step 2: Add image preview helper**

In `frontend/src/services/agentArtifactService.ts`, ensure `downloadAgentArtifact(artifactId)` is used for binary preview. Do not render `storageRef` directly in an `<img>` URL, because storage references are implementation details and may not be browser-safe.

Add helper:

```ts
export async function createAgentArtifactObjectUrl(artifactId: string) {
  const blob = await downloadAgentArtifact(artifactId);
  return URL.createObjectURL(blob);
}
```

- [ ] **Step 3: Create ArtifactInspectorTab**

Create `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx`:
- Merge streamed artifacts and server artifacts by id.
- Left/top selector lists all artifacts with status and type.
- Main preview supports markdown/plain text from `code` or `previewText`.
- Image preview uses `createAgentArtifactObjectUrl`.
- Actions: copy, download, fullscreen.
- Disable download when `scanStatus !== CLEAN`.

- [ ] **Step 4: Refactor ArtifactPanel as compatibility wrapper**

Keep `ArtifactPanel` exported to avoid breaking imports. Internally delegate to `ArtifactInspectorTab` with a simple panel shell.

Required compatibility:

```tsx
export function ArtifactPanel(props: ArtifactPanelProps) {
  return <ArtifactInspectorTab artifacts={props.artifacts} serverArtifacts={props.serverArtifacts} onClose={props.onClose} />;
}
```

- [ ] **Step 5: Wire Inspector Artifacts tab**

In `WorkspaceInspector.tsx`, for the active message:
- Prefer `message.artifacts` / `message.serverArtifacts`.
- If the message is active in `artifactStore`, merge with `useMessageArtifacts(message.id)`.
- Render `ArtifactInspectorTab`.

- [ ] **Step 6: Verify**

Run:

```bash
cd frontend
npm run build
```

Manual checks:
- Streaming report appears incrementally in the Artifacts tab.
- Copy button is disabled while artifact has no content.
- Image artifact preview creates and revokes object URL on unmount.
- Unsafe server artifact cannot be downloaded.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx frontend/src/components/chat/ArtifactPanel.tsx frontend/src/stores/artifactStore.ts frontend/src/services/agentArtifactService.ts frontend/src/components/chat/workbench/WorkspaceInspector.tsx
git commit -m "feat(frontend): move artifacts into workspace inspector"
```

---

## Task 4: Timeline, Sources, Approvals, Cost, Memory Tabs

**Files:**
- Create: `frontend/src/components/chat/workbench/TimelineInspectorTab.tsx`
- Create: `frontend/src/components/chat/workbench/SourcesInspectorTab.tsx`
- Create: `frontend/src/components/chat/workbench/ApprovalsInspectorTab.tsx`
- Create: `frontend/src/components/chat/workbench/CostQuotaInspectorTab.tsx`
- Create: `frontend/src/components/chat/workbench/MemoryInspectorTab.tsx`
- Create: `frontend/src/components/chat/workbench/MessageRunSummary.tsx`
- Modify: `frontend/src/components/chat/AgentTracePanel.tsx`
- Modify: `frontend/src/components/chat/SourceList.tsx`
- Modify: `frontend/src/components/chat/ApprovalCard.tsx`
- Modify: `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`
- Modify: `frontend/src/services/agentRunService.ts`

- [ ] **Step 1: Create `MessageRunSummary`**

Create a compact assistant-message summary:
- Shows step count, source count, artifact count, approval count, cost if present.
- Uses icon buttons with labels.
- Each metric opens the matching inspector tab.

Required tab mapping:

```ts
const tabByMetric = {
  steps: "timeline",
  sources: "sources",
  artifacts: "artifacts",
  approvals: "approvals",
  cost: "cost",
  memories: "memory"
} as const;
```

- [ ] **Step 2: Convert AgentTracePanel to compact compatibility component**

Modify `AgentTracePanel.tsx`:
- Keep export name.
- Render `MessageRunSummary` instead of the full long panel.
- Remove duplicate detailed sections that now live in inspector tabs.
- Keep `return null` when no trace data exists.

- [ ] **Step 3: Create `TimelineInspectorTab`**

Render timeline as a vertical list with:
- localized step title using existing `RESEARCH_STEP_LABELS`.
- status pill.
- duration.
- detail text only when present.
- active/current step highlighted.

- [ ] **Step 4: Create `SourcesInspectorTab`**

Render sources with:
- title, source type, trust level, score.
- snippet.
- URL open button only when `url` is present.
- Citation index if present.

Reuse or extract from `SourceList.tsx`; avoid duplicated trust-level mapping.

- [ ] **Step 5: Create `ApprovalsInspectorTab`**

Render approvals using `ApprovalCard`.
Acceptance:
- Pending approval stays visually prominent.
- Approved/rejected/modified approvals remain visible but lower emphasis.
- The tab shows an empty state when no approval is present.

- [ ] **Step 6: Create `CostQuotaInspectorTab`**

Render:
- `message.costSummary.totalTokens`, `totalCalls`, `totalCost`.
- `message.quota` cards.
- Resume/retry controls when `message.canResume` or `message.canRetry` and `message.agentRunId` exist.

Use existing service functions:

```ts
import { resumeAgentRun, retryAgentRun } from "@/services/agentRunService";
```

After resume/retry, call `refreshRunSnapshot(message.id, message.agentRunId)`.

- [ ] **Step 7: Create `MemoryInspectorTab`**

Render `message.memories` as context chips/cards:
- title.
- content.
- action if present.
- Empty state explains that no memory was used for this run.

- [ ] **Step 8: Wire all tabs in WorkspaceInspector**

In `WorkspaceInspector.tsx`, replace the temporary tab body from Task 2 with actual tab components.

- [ ] **Step 9: Verify**

Run:

```bash
cd frontend
npm run build
```

Manual checks:
- A deep research run shows Timeline, Sources, Artifact, Cost tabs.
- A pending approval opens Approvals tab without flicker.
- Resume/retry buttons are hidden when `canResume`/`canRetry` are false.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/components/chat/workbench frontend/src/components/chat/AgentTracePanel.tsx frontend/src/components/chat/SourceList.tsx frontend/src/components/chat/ApprovalCard.tsx frontend/src/services/agentRunService.ts
git commit -m "feat(frontend): add agent run inspector tabs"
```

---

## Task 5: Prompt Enhancer For Research Tasks

**Files:**
- Create: `frontend/src/components/chat/prompt/promptEnhancer.ts`
- Create: `frontend/src/components/chat/prompt/promptEnhancer.test.ts`
- Create: `frontend/src/components/chat/prompt/PromptEnhancerButton.tsx`
- Create: `frontend/src/components/chat/prompt/PromptEnhancerDialog.tsx`
- Modify: `frontend/src/components/chat/ChatInput.tsx`

- [ ] **Step 1: Write enhancer tests**

Create `frontend/src/components/chat/prompt/promptEnhancer.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { enhanceResearchPrompt } from "@/components/chat/prompt/promptEnhancer";

describe("enhanceResearchPrompt", () => {
  it("adds research structure without replacing the user's intent", () => {
    const result = enhanceResearchPrompt({
      original: "分析 Seahorse Agent 的 UI 升级方向",
      outputType: "report",
      sourcePreference: "official-and-current",
      depth: "deep"
    });

    expect(result).toContain("分析 Seahorse Agent 的 UI 升级方向");
    expect(result).toContain("输出结构");
    expect(result).toContain("引用来源");
  });
});
```

- [ ] **Step 2: Add deterministic enhancer**

Create `frontend/src/components/chat/prompt/promptEnhancer.ts`:
- No backend call in this task.
- Input fields: `original`, `outputType`, `sourcePreference`, `depth`.
- Return a Chinese structured prompt.
- Preserve user intent verbatim in the first paragraph.

Required interface:

```ts
export interface EnhanceResearchPromptInput {
  original: string;
  outputType: "answer" | "report" | "comparison" | "plan";
  sourcePreference: "official-and-current" | "broad-web" | "uploaded-files";
  depth: "quick" | "standard" | "deep";
}
```

- [ ] **Step 3: Add PromptEnhancerButton**

Create a small icon button using `Lightbulb` from lucide-react:
- `aria-label="Improve prompt"`.
- Tooltip: `整理问题`.
- Disabled while streaming.

- [ ] **Step 4: Add PromptEnhancerDialog**

Create a Radix Dialog:
- Shows original draft.
- Fields: output type, source preference, depth.
- Preview enhanced prompt.
- Buttons: `取消`, `应用到输入框`.
- On apply, call `onApply(enhancedText)`.

- [ ] **Step 5: Integrate ChatInput**

Modify `ChatInput.tsx`:
- Add `PromptEnhancerButton` next to attachment button.
- Keep current `value` as source draft.
- When user applies enhanced prompt, call `setValue(enhancedText)` and focus textarea.
- Do not show the enhancer when input is empty.

- [ ] **Step 6: Verify**

Run:

```bash
cd frontend
npm run test -- promptEnhancer
npm run build
```

Manual checks:
- Button is absent or disabled for empty input.
- Applying enhanced text does not send automatically.
- IME Enter behavior still works after dialog closes.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/chat/prompt frontend/src/components/chat/ChatInput.tsx
git commit -m "feat(frontend): add prompt enhancer for research tasks"
```

---

## Task 6: A2UI-lite Whitelist Renderer

**Files:**
- Create: `frontend/src/components/a2ui-lite/a2uiTypes.ts`
- Create: `frontend/src/components/a2ui-lite/a2uiRegistry.tsx`
- Create: `frontend/src/components/a2ui-lite/A2UILiteRenderer.tsx`
- Create: `frontend/src/components/a2ui-lite/A2UILiteRenderer.test.tsx`
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx`
- Modify: `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`

- [ ] **Step 1: Define A2UI-lite schema**

Create `a2uiTypes.ts`:

```ts
export type A2UILiteComponentType = "metric" | "table" | "source_grid" | "callout" | "action_row";

export interface A2UILiteNode {
  id: string;
  type: A2UILiteComponentType;
  props: Record<string, unknown>;
  children?: A2UILiteNode[];
}

export interface A2UILiteSurface {
  version: "seahorse-a2ui-lite/v1";
  title?: string;
  root: A2UILiteNode;
}

export interface A2UILiteAction {
  type: "open_artifact" | "select_source" | "copy_text" | "set_prompt_draft";
  payload: Record<string, unknown>;
}
```

- [ ] **Step 2: Write renderer tests**

Create `A2UILiteRenderer.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { A2UILiteRenderer } from "@/components/a2ui-lite/A2UILiteRenderer";
import type { A2UILiteSurface } from "@/components/a2ui-lite/a2uiTypes";

const surface: A2UILiteSurface = {
  version: "seahorse-a2ui-lite/v1",
  title: "Research summary",
  root: {
    id: "root",
    type: "callout",
    props: { title: "结论", body: "可以升级为 Agent Workbench。" }
  }
};

describe("A2UILiteRenderer", () => {
  it("renders whitelisted components", () => {
    render(<A2UILiteRenderer surface={surface} onAction={() => undefined} />);
    expect(screen.getByText("结论")).toBeInTheDocument();
    expect(screen.getByText("可以升级为 Agent Workbench。")).toBeInTheDocument();
  });
});
```

- [ ] **Step 3: Create registry**

Create `a2uiRegistry.tsx`:
- `metric`: label/value/delta.
- `table`: columns/rows with string values only.
- `source_grid`: source title/trust/snippet.
- `callout`: title/body/tone.
- `action_row`: whitelisted action buttons.

Unknown component type must render a small diagnostic line:

```tsx
<div role="alert">Unsupported UI component: {node.type}</div>
```

- [ ] **Step 4: Create renderer**

Create `A2UILiteRenderer.tsx`:
- Validate `surface.version === "seahorse-a2ui-lite/v1"`.
- Recursively render children through the registry.
- Do not use `dangerouslySetInnerHTML`.
- Do not evaluate scripts.
- Do not navigate routes directly inside registry; emit `onAction`.

- [ ] **Step 5: Extend artifact rendering**

In `ArtifactInspectorTab.tsx`:
- If `serverArtifact.mimeType === "application/vnd.seahorse.a2ui+json"` or streamed artifact language is `"json"` and parsed object has version `"seahorse-a2ui-lite/v1"`, show `A2UILiteRenderer`.
- If JSON parse fails, show plain text with an inline parse error.

- [ ] **Step 6: Add type aliases**

In `frontend/src/types/index.ts`, add:

```ts
export type AgentUiSurfaceMimeType = "application/vnd.seahorse.a2ui+json";
```

- [ ] **Step 7: Verify**

Run:

```bash
cd frontend
npm run test -- A2UILiteRenderer
npm run build
```

Manual checks:
- A valid UI surface renders inside Artifact tab.
- Unknown component type does not crash the inspector.
- Plain markdown artifacts still render as before.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/a2ui-lite frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx frontend/src/components/chat/workbench/WorkspaceInspector.tsx frontend/src/types/index.ts
git commit -m "feat(frontend): add a2ui lite renderer"
```

---

## Task 7: Agent Console And Web Inspector-like Debug Views

**Files:**
- Modify: `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentRunController.java`
- Modify: `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebApiContractTests.java`
- Modify: `frontend/src/services/agentRunService.ts`
- Create: `frontend/src/pages/admin/agent-console/AgentConsolePage.tsx`
- Create: `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx`
- Create: `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.test.tsx`
- Create: `frontend/src/pages/admin/agent-inspector/components/AgentEventStream.tsx`
- Create: `frontend/src/pages/admin/agent-inspector/components/AgentStateView.tsx`
- Create: `frontend/src/pages/admin/agent-inspector/components/AgentContextView.tsx`
- Create: `frontend/src/pages/admin/agent-inspector/components/AgentToolsView.tsx`
- Modify: `frontend/src/pages/admin/AdminLayout.tsx`
- Modify: `frontend/src/router.tsx`
- Modify: `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx`
- Modify: `frontend/src/pages/admin/traces/RagTracePage.tsx`

- [ ] **Step 1: Add backend events API contract test**

In `SeahorseWebApiContractTests`, add a test for:

```http
GET /api/agent-runs/run-1/events?afterSeq=0
```

Expected JSON:
- `data[0].runId == "run-1"`
- `data[0].eventSeq == 1`
- `data[0].eventType` is present

- [ ] **Step 2: Add events API**

Modify `SeahorseAgentRunController.java`:
- Inject `ObjectProvider<AgentRunEventBufferPort>`.
- Add:

```java
@GetMapping("/api/agent-runs/{runId}/events")
public ApiResponse<Object> events(@PathVariable String runId,
                                  @RequestParam(defaultValue = "0") long afterSeq,
                                  HttpServletRequest request) {
    requireApiOrRunManagement(request);
    AgentRunEventBufferPort port = eventBufferPortProvider.getIfAvailable(AgentRunEventBufferPort::noop);
    return ApiResponses.success(port.getAfter(runId, Math.max(0L, afterSeq)));
}
```

Use the existing controller constructor pattern and do not call providers in constructor.

- [ ] **Step 3: Add frontend service**

In `frontend/src/services/agentRunService.ts`, add:

```ts
import type { StreamEventEnvelope } from "@/types";

export async function listAgentRunEvents(runId: string, afterSeq = 0) {
  return api.get<StreamEventEnvelope[], StreamEventEnvelope[]>(
    `${AGENT_RUNS_API_BASE}/${encodeURIComponent(runId)}/events`,
    { params: { afterSeq } }
  );
}
```

- [ ] **Step 4: Create Agent Inspector test**

Create `AgentInspectorPage.test.tsx`:
- Mock `agentRunService`.
- Render route with run id.
- Assert tabs `Events`, `State`, `Context`, `Tools` are present.

- [ ] **Step 5: Create Agent Inspector page**

`AgentInspectorPage.tsx`:
- Reads `runId` from route param or query input.
- Loads snapshot, cost summary, events.
- Tabs:
  - `Events`: raw stream envelopes with eventSeq, type, timestamp.
  - `State`: run status, current step, canResume/canRetry.
  - `Context`: sources, memories, artifacts summary.
  - `Tools`: approvals and tool-call related events.
- Uses Recharts only if a small cost/tokens chart adds useful signal; otherwise keep it as lists.

- [ ] **Step 6: Create component views**

Create:
- `AgentEventStream.tsx`: event list with copy JSON button.
- `AgentStateView.tsx`: run and step state.
- `AgentContextView.tsx`: sources/artifacts/memories.
- `AgentToolsView.tsx`: approvals/tool events.

- [ ] **Step 7: Create Agent Console wrapper**

Create `AgentConsolePage.tsx`:
- Wrap or reuse `AiInfraConsolePage`.
- Add top-level sections: Runs, Approvals, Eval, Cost.
- Keep original `/admin/ai-infra` route working by rendering `AgentConsolePage`.

- [ ] **Step 8: Update Admin navigation and routing**

Modify `AdminLayout.tsx`:
- Rename label `AI Infra 控制台` to `Agent Console`.
- Add route item `Agent Inspector` behind `AI_INFRA_CONSOLE` feature gate.

Modify `router.tsx`:
- `/admin/ai-infra` -> `AgentConsolePage`.
- `/admin/agent-inspector` -> `AgentInspectorPage`.
- `/admin/agent-inspector/:runId` -> `AgentInspectorPage`.

- [ ] **Step 9: Verify**

Run:

```bash
./mvnw -pl seahorse-agent-adapter-web,seahorse-agent-tests -am test "-Dtest=SeahorseWebApiContractTests"
cd frontend
npm run test -- AgentInspectorPage
npm run build
```

Manual checks:
- Consumer-web mode still hides advanced admin routes.
- Enterprise + advanced admin mode shows Agent Console and Agent Inspector.
- Agent Inspector can open from a run id and from trace page links.

- [ ] **Step 10: Commit**

```bash
git add seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentRunController.java seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebApiContractTests.java frontend/src/services/agentRunService.ts frontend/src/pages/admin/agent-console frontend/src/pages/admin/agent-inspector frontend/src/pages/admin/AdminLayout.tsx frontend/src/router.tsx frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx frontend/src/pages/admin/traces/RagTracePage.tsx
git commit -m "feat(admin): add agent console and inspector"
```

---

## Task 8: Memory Center Productization

**Files:**
- Modify: `frontend/src/pages/MemoryCenterPage.tsx`
- Create: `frontend/src/components/memory/MemoryToolbar.tsx`
- Create: `frontend/src/components/memory/MemoryPrivacyBanner.tsx`
- Create: `frontend/src/components/memory/MemoryCard.tsx`
- Create: `frontend/src/components/memory/MemoryEmptyState.tsx`
- Modify: `frontend/src/services/userMemoryService.ts`
- Modify: `frontend/src/types/index.ts`

- [ ] **Step 1: Extract MemoryPrivacyBanner**

Create `MemoryPrivacyBanner.tsx`:
- Props: `privacyMode`, `loading`, `onToggle`.
- Shows clear state: `隐私模式已开启` or `长期记忆可用于新对话`.
- Uses `ShieldCheck` and `ShieldOff`.
- Toggle button has loading state and disabled state.

- [ ] **Step 2: Extract MemoryToolbar**

Create `MemoryToolbar.tsx`:
- Props: `query`, `type`, `sensitivity`, `onQueryChange`, `onTypeChange`, `onSensitivityChange`, `onRefresh`.
- Search input filters local memories by `displayText`.
- Selects include `全部类型`, `PROFILE`, `PREFERENCE`, `PROJECT_CONTEXT`, `LONG_TERM_FACT`.

- [ ] **Step 3: Extract MemoryCard**

Create `MemoryCard.tsx`:
- Props: `memory`, `deleting`, `onDelete`.
- Show type, sensitivity, status, updatedAt.
- If `sourceConversationId` exists, render link to `/chat/{sourceConversationId}`.
- Delete action uses icon button and `aria-label`.

- [ ] **Step 4: Extract MemoryEmptyState**

Create `MemoryEmptyState.tsx`:
- For no memories: `还没有长期记忆`.
- For filter no results: `没有匹配当前筛选的记忆`.

- [ ] **Step 5: Refactor MemoryCenterPage**

Modify `MemoryCenterPage.tsx`:
- Keep data loading and API mutation logic.
- Add local derived `filteredMemories`.
- Use extracted components.
- Replace full-page generic cards with workbench panel surfaces.
- Add inline error state when load fails; do not rely only on toast.

- [ ] **Step 6: Verify**

Run:

```bash
cd frontend
npm run build
```

Manual checks:
- Search filters by text without API calls.
- Type/sensitivity filters compose correctly.
- Delete preserves current filters and removes the item.
- Source link opens the conversation route.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/MemoryCenterPage.tsx frontend/src/components/memory frontend/src/services/userMemoryService.ts frontend/src/types/index.ts
git commit -m "feat(frontend): productize memory center"
```

---

## Task 9: Responsive, Accessibility, Performance Pass

**Files:**
- Modify: `frontend/src/styles/globals.css`
- Modify: `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`
- Modify: `frontend/src/components/chat/ChatInput.tsx`
- Modify: `frontend/src/components/layout/MainLayout.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`

- [ ] **Step 1: Responsive pass**

Check:
- Desktop >= 1280px: chat + inspector both visible; no nested scroll conflict.
- Tablet 768-1024px: inspector can collapse; message list width remains readable.
- Mobile < 768px: inspector bottom sheet; input remains visible; no horizontal scroll.

CSS constraints:

```css
.workspace-inspector-mobile {
  height: min(68dvh, 720px);
  max-height: calc(100dvh - 72px);
}
```

- [ ] **Step 2: Keyboard and focus pass**

Requirements:
- All icon-only buttons have `aria-label`.
- Inspector close returns focus to the element that opened it.
- Radix tabs are keyboard reachable.
- Prompt enhancer dialog traps focus and Escape closes it.

- [ ] **Step 3: Stream performance pass**

Requirements:
- Artifact chunk updates only rerender Artifact tab and active message summary.
- Message list does not fully rerender on every `ARTIFACT_CONTENT`.
- CSS animations only use `transform` and `opacity`.

- [ ] **Step 4: Browser verification**

Run app:

```bash
cd frontend
npm run dev
```

Open:
- `http://localhost:5173/chat`
- `http://localhost:5173/memories`
- `http://localhost:5173/admin/agent-inspector`

Capture desktop and mobile screenshots if Playwright is available in the local environment.

- [ ] **Step 5: Final commands**

Run:

```bash
git diff --check
cd frontend
npm run lint
npm run test
npm run build
```

If backend events API was implemented in Task 7, also run:

```bash
./mvnw -pl seahorse-agent-adapter-web,seahorse-agent-tests -am test "-Dtest=SeahorseWebApiContractTests"
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/styles/globals.css frontend/src/components/chat/workbench/WorkspaceInspector.tsx frontend/src/components/chat/ChatInput.tsx frontend/src/components/layout/MainLayout.tsx frontend/src/components/layout/Sidebar.tsx
git commit -m "chore(frontend): polish workbench accessibility and responsiveness"
```

---

## Task 10: Documentation And Handover Sync

**Files:**
- Modify: `docs/HANDOVER.md`
- Modify: `docs/company-agent/开源项目设计亮点落地方案.md`
- Create: `docs/company-agent/Agent Workbench UI 升级验收记录.md`

- [ ] **Step 1: Add handover summary**

In `docs/HANDOVER.md`, add a short section under frontend status:
- Agent Workbench UI status.
- Key files.
- Verification commands and actual results.
- Known non-goals: no WebContainer, no arbitrary A2UI, no full CopilotKit dependency.

- [ ] **Step 2: Update open-source highlights document**

In `docs/company-agent/开源项目设计亮点落地方案.md`, add UI absorption status:
- bolt.new Workbench absorbed as Workspace Inspector.
- CopilotKit A2UI absorbed as A2UI-lite whitelist renderer.
- CopilotKit Web Inspector absorbed as Admin Agent Inspector.
- ChatDev launch console absorbed as split workbench layout.

- [ ] **Step 3: Add acceptance record**

Create `docs/company-agent/Agent Workbench UI 升级验收记录.md` with:
- Scope.
- Screens checked.
- Commands run.
- Screenshots path if captured.
- Remaining risks.

- [ ] **Step 4: Commit**

```bash
git add docs/HANDOVER.md docs/company-agent/开源项目设计亮点落地方案.md "docs/company-agent/Agent Workbench UI 升级验收记录.md"
git commit -m "docs: record agent workbench ui upgrade"
```

---

## Execution Order

Recommended order:

1. Task 0 - test harness.
2. Task 1 - tokens/theme cleanup.
3. Task 2 - inspector shell.
4. Task 3 - artifact tab.
5. Task 4 - run detail tabs.
6. Task 5 - prompt enhancer.
7. Task 6 - A2UI-lite.
8. Task 8 - memory center.
9. Task 7 - admin console/inspector, because it touches backend and advanced admin gates.
10. Task 9 - final UI quality pass.
11. Task 10 - docs sync.

Reasoning: Tasks 1-4 create the user-facing workbench foundation. Task 5 and Task 6 add higher-level interaction and rendering. Task 7 touches backend/admin and should be isolated. Task 9 should run after all UI surfaces exist.

## Verification Matrix

| Area | Evidence |
| --- | --- |
| Frontend static correctness | `cd frontend && npm run build` |
| Frontend lint | `cd frontend && npm run lint` |
| Component/unit behavior | `cd frontend && npm run test` |
| Backend events API | `./mvnw -pl seahorse-agent-adapter-web,seahorse-agent-tests -am test "-Dtest=SeahorseWebApiContractTests"` |
| Responsive layout | Browser check `/chat`, `/memories`, `/admin/agent-inspector` at 390px, 768px, 1440px |
| Product mode safety | Consumer-web hides advanced admin; enterprise + flag shows Agent Console/Inspector |
| Security boundary | A2UI-lite does not use `dangerouslySetInnerHTML`; artifact image preview uses download API object URL |

## Self-Review

- Spec coverage: Workbench layout, visual tokens, Artifact, Trace, Sources, Approvals, Cost, Memory, Prompt Enhancer, A2UI-lite, Admin Inspector, Memory Center, verification, and docs sync are each mapped to a task.
- 占位词扫描: The plan contains no deferred markers and no unspecified “handle edge cases” steps. Each task lists concrete files, behaviors, commands, and commit message.
- Type consistency: `WorkbenchTab`, `A2UILiteSurface`, `StreamEventEnvelope`, and existing `Message`/`AgentArtifact` types are used consistently across tasks.
- Dependency check: No new runtime UI dependency is planned. Vitest/Testing Library are dev-only dependencies introduced in Task 0.
- Scope check: Full IDE/WebContainer/CopilotKit dependency/Agent Mesh are explicitly out of scope.
