import * as React from "react";
import { useNavigate } from "react-router-dom";
import { formatDistanceToNow } from "date-fns";
import { zhCN } from "date-fns/locale";
import {
  MessageSquarePlus,
  FileText,
  Database,
  Bot,
  Clock,
  ArrowRight,
  CheckCircle,
  AlertTriangle,
  XCircle,
  Sparkles
} from "lucide-react";

import { MainLayout } from "@/components/layout/MainLayout";
import { ReadinessStatusBar } from "@/components/readiness/ReadinessStatusBar";
import { QuickTaskCard } from "@/pages/workspace/QuickTaskCard";
import { useAuthStore } from "@/stores/authStore";
import { useChatStore } from "@/stores/chatStore";
import { useReadinessStore } from "@/stores/readinessStore";

const QUICK_TASKS = [
  {
    icon: MessageSquarePlus,
    title: "快速聊天",
    description: "开始一段新的对话",
    gradient: "linear-gradient(135deg, #6366f1, #8b5cf6)"
  },
  {
    icon: FileText,
    title: "文档问答",
    description: "上传文档，智能问答",
    gradient: "linear-gradient(135deg, #0ea5e9, #06b6d4)"
  },
  {
    icon: Database,
    title: "知识库问答",
    description: "基于知识库检索回答",
    gradient: "linear-gradient(135deg, #f59e0b, #f97316)"
  },
  {
    icon: Bot,
    title: "运行 Agent",
    description: "选择 Agent 执行任务",
    gradient: "linear-gradient(135deg, #10b981, #059669)"
  }
];

const CAPABILITY_CHECKS = [
  { id: "model.chat", label: "基础聊天" },
  { id: "model.embedding", label: "文档问答" },
  { id: "app.boot", label: "Agent 运行" }
];

export function WorkspaceHomePage() {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const { sessions, fetchSessions, startNewSessionDraft } = useChatStore();
  const { summary, isLoading, error, loadSummary } = useReadinessStore();

  React.useEffect(() => {
    if (sessions.length === 0) {
      fetchSessions().catch(() => null);
    }
  }, [fetchSessions, sessions.length]);

  React.useEffect(() => {
    if (!summary && !isLoading && !error) {
      loadSummary();
    }
  }, [summary, isLoading, error, loadSummary]);

  const recentSessions = React.useMemo(() => {
    return sessions.slice(0, 8);
  }, [sessions]);

  const handleQuickTask = (index: number) => {
    startNewSessionDraft();
    navigate("/chat");
  };

  const greeting = React.useMemo(() => {
    const hour = new Date().getHours();
    if (hour < 6) return "夜深了";
    if (hour < 12) return "早上好";
    if (hour < 14) return "中午好";
    if (hour < 18) return "下午好";
    return "晚上好";
  }, []);

  const getCapabilityStatus = (checkId: string) => {
    if (!summary) return { status: "unknown" as const, label: "检查中" };
    const check = summary.checks.find((c) => c.id === checkId);
    if (!check) return { status: "unknown" as const, label: "未配置" };
    if (check.status === "passed") return { status: "ok" as const, label: "可用" };
    if (check.severity === "error") return { status: "blocked" as const, label: "不可用" };
    return { status: "degraded" as const, label: "降级" };
  };

  return (
    <MainLayout>
      <div className="h-full overflow-y-auto">
        <div className="mx-auto max-w-4xl px-6 py-8 space-y-8">
          {/* Header: Welcome + Status */}
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5" style={{ color: "var(--theme-accent)" }} />
                <h1 className="text-2xl font-bold" style={{ color: "var(--theme-text-primary)" }}>
                  {greeting}，{user?.username || "用户"}
                </h1>
              </div>
              <p className="mt-1 text-sm" style={{ color: "var(--theme-text-muted)" }}>
                选择一项任务开始工作，或继续之前的对话
              </p>
            </div>
            <ReadinessStatusBar />
          </div>

          {/* Quick Task Cards */}
          <section>
            <h2 className="mb-3 text-xs font-semibold uppercase tracking-wider" style={{ color: "var(--theme-text-muted)" }}>
              快捷任务
            </h2>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {QUICK_TASKS.map((task, index) => (
                <QuickTaskCard
                  key={task.title}
                  icon={task.icon}
                  title={task.title}
                  description={task.description}
                  gradient={task.gradient}
                  onClick={() => handleQuickTask(index)}
                />
              ))}
            </div>
          </section>

          {/* Recent Conversations */}
          <section>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-xs font-semibold uppercase tracking-wider" style={{ color: "var(--theme-text-muted)" }}>
                最近对话
              </h2>
              {sessions.length > 0 && (
                <button
                  type="button"
                  className="flex items-center gap-1 text-xs font-medium transition-colors"
                  style={{ color: "var(--theme-accent)" }}
                  onClick={() => navigate("/chat")}
                >
                  查看全部
                  <ArrowRight className="h-3 w-3" />
                </button>
              )}
            </div>

            {recentSessions.length === 0 ? (
              <div
                className="flex flex-col items-center justify-center rounded-2xl py-12"
                style={{
                  backgroundColor: "var(--theme-glass-bg)",
                  border: "1px solid var(--theme-glass-border)"
                }}
              >
                <MessageSquarePlus className="h-10 w-10 mb-3" style={{ color: "var(--theme-text-muted)" }} />
                <p className="text-sm font-medium" style={{ color: "var(--theme-text-secondary)" }}>
                  还没有对话记录
                </p>
                <p className="mt-1 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                  点击上方快捷任务开始你的第一次对话
                </p>
              </div>
            ) : (
              <div
                className="overflow-hidden rounded-2xl"
                style={{
                  backgroundColor: "var(--theme-glass-bg)",
                  border: "1px solid var(--theme-glass-border)"
                }}
              >
                {recentSessions.map((session, index) => {
                  const timeLabel = session.lastTime
                    ? formatDistanceToNow(new Date(session.lastTime), { addSuffix: true, locale: zhCN })
                    : null;

                  return (
                    <button
                      key={session.id}
                      type="button"
                      className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors"
                      style={{
                        borderBottom: index < recentSessions.length - 1 ? "1px solid var(--theme-glass-border)" : undefined
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.backgroundColor = "var(--theme-bg-elevated)";
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.backgroundColor = "transparent";
                      }}
                      onClick={() => navigate(`/chat/${session.id}`)}
                    >
                      <Clock className="h-4 w-4 shrink-0" style={{ color: "var(--theme-text-muted)" }} />
                      <span className="flex-1 min-w-0 truncate text-sm" style={{ color: "var(--theme-text-primary)" }}>
                        {session.title || "新对话"}
                      </span>
                      {timeLabel && (
                        <span className="shrink-0 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                          {timeLabel}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
            )}
          </section>

          {/* System Availability */}
          <section>
            <h2 className="mb-3 text-xs font-semibold uppercase tracking-wider" style={{ color: "var(--theme-text-muted)" }}>
              系统能力
            </h2>
            <div className="flex flex-wrap gap-2">
              {CAPABILITY_CHECKS.map((cap) => {
                const status = getCapabilityStatus(cap.id);
                const Icon = status.status === "ok"
                  ? CheckCircle
                  : status.status === "blocked"
                    ? XCircle
                    : status.status === "degraded"
                      ? AlertTriangle
                      : Clock;

                const colorMap = {
                  ok: "var(--color-success, #22c55e)",
                  blocked: "var(--color-error, #ef4444)",
                  degraded: "var(--color-warning, #f59e0b)",
                  unknown: "var(--theme-text-muted)"
                };

                return (
                  <div
                    key={cap.id}
                    className="flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-medium"
                    style={{
                      backgroundColor: "var(--theme-glass-bg)",
                      border: "1px solid var(--theme-glass-border)",
                      color: colorMap[status.status]
                    }}
                  >
                    <Icon className="h-3.5 w-3.5" />
                    <span>{cap.label}</span>
                    <span style={{ color: "var(--theme-text-muted)" }}>·</span>
                    <span>{status.label}</span>
                  </div>
                );
              })}
            </div>
          </section>
        </div>
      </div>
    </MainLayout>
  );
}
