import * as React from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, GitBranch, CheckCircle2, XCircle, Play, Loader2 } from "lucide-react";

import { MainLayout } from "@/components/layout/MainLayout";
import { Button } from "@/components/ui/button";
import { useReadinessStore } from "@/stores/readinessStore";
import { useTaskStore } from "@/stores/taskStore";

/** 内置示例 Agent：GitHub 项目图文介绍（Mermaid-only，禁用图片生成）。 */
const MERMAID_AGENT_ID = "github-visual-project-intro-agent";

export function GithubMermaidExamplePage() {
  const navigate = useNavigate();
  const { summary, loadSummary } = useReadinessStore();
  const { createTask, isLoading } = useTaskStore();
  const [repoUrl, setRepoUrl] = React.useState("");

  React.useEffect(() => {
    if (!summary) loadSummary();
  }, [summary, loadSummary]);

  // 运行条件：聊天模型可用即可（Agent 运行核心依赖）
  const chatCheck = summary?.checks.find((c) => c.id === "model.chat");
  const canRun = !chatCheck || chatCheck.status === "passed";

  const handleRun = async () => {
    const trimmed = repoUrl.trim();
    if (!trimmed) return;
    try {
      const task = await createTask({
        type: "agent_run",
        agentId: MERMAID_AGENT_ID,
        question: `请阅读这个 GitHub 仓库并生成中文 Markdown 项目介绍 + Mermaid 架构图：${trimmed}`,
        title: `Mermaid 架构图: ${trimmed}`,
        mode: "auto"
      });
      navigate(`/workspace/tasks/${task.taskId}`);
    } catch {
      // error toast handled by interceptor
    }
  };

  return (
    <MainLayout>
      <div className="mx-auto flex h-full max-w-2xl flex-col overflow-y-auto px-4 py-6">
        <div className="mb-6 flex items-center gap-3">
          <Button variant="ghost" size="icon" className="h-8 w-8 shrink-0" onClick={() => navigate("/workspace")}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div className="flex items-center gap-2">
            <GitBranch className="h-5 w-5" style={{ color: "var(--theme-accent)" }} />
            <h1 className="text-lg font-semibold" style={{ color: "var(--theme-text-primary)" }}>
              GitHub Mermaid 架构图生成
            </h1>
          </div>
        </div>

        <div className="space-y-4">
          {/* 适用场景 */}
          <div
            className="rounded-xl p-4"
            style={{ backgroundColor: "var(--theme-glass-bg)", border: "1px solid var(--theme-glass-border)" }}
          >
            <h3 className="mb-2 text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
              适用场景
            </h3>
            <p className="text-sm leading-relaxed" style={{ color: "var(--theme-text-secondary)" }}>
              输入一个 GitHub 仓库地址，自动阅读 README、文档和关键源码，输出中文 Markdown 项目介绍与
              Mermaid 架构图/流程图。产物类型为 Markdown 和 Mermaid，不依赖图片生成。
            </p>
          </div>

          {/* 运行条件 */}
          <div
            className="flex items-center gap-2 rounded-xl p-4"
            style={{ backgroundColor: "var(--theme-glass-bg)", border: "1px solid var(--theme-glass-border)" }}
          >
            {canRun ? (
              <CheckCircle2 className="h-4 w-4 shrink-0" style={{ color: "#22c55e" }} />
            ) : (
              <XCircle className="h-4 w-4 shrink-0" style={{ color: "#ef4444" }} />
            )}
            <span className="text-sm" style={{ color: "var(--theme-text-secondary)" }}>
              {canRun ? "当前系统满足运行条件（聊天模型可用）" : "聊天模型不可用，无法运行该示例"}
            </span>
            {!canRun && (
              <button
                type="button"
                className="ml-auto text-xs underline"
                style={{ color: "var(--theme-accent)" }}
                onClick={() => navigate("/admin/readiness")}
              >
                查看诊断
              </button>
            )}
          </div>

          {/* 输入 */}
          <div
            className="rounded-xl p-4"
            style={{ backgroundColor: "var(--theme-glass-bg)", border: "1px solid var(--theme-glass-border)" }}
          >
            <label className="mb-2 block text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
              GitHub 仓库地址
            </label>
            <input
              type="text"
              value={repoUrl}
              onChange={(e) => setRepoUrl(e.target.value)}
              placeholder="https://github.com/owner/repo"
              className="w-full rounded-lg px-3 py-2 text-sm outline-none"
              style={{
                backgroundColor: "var(--theme-bg-elevated)",
                border: "1px solid var(--theme-glass-border)",
                color: "var(--theme-text-primary)"
              }}
            />
            <p className="mt-2 text-xs" style={{ color: "var(--theme-text-muted)" }}>
              预期产物：Markdown 项目介绍 + Mermaid 架构图/流程图。若仓库读取失败，可改为上传 README 或压缩包。
            </p>
          </div>

          <Button
            className="w-full gap-2"
            disabled={!canRun || !repoUrl.trim() || isLoading}
            onClick={handleRun}
          >
            {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
            一键运行
          </Button>
        </div>
      </div>
    </MainLayout>
  );
}
