import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, X } from "lucide-react";
import { format } from "date-fns";
import { zhCN } from "date-fns/locale";

import { TaskStatusBadge } from "@/components/task/TaskStatusBadge";
import { TaskTypeBadge } from "@/components/task/TaskTypeBadge";
import { MainLayout } from "@/components/layout/MainLayout";
import { Button } from "@/components/ui/button";
import { useTaskStore } from "@/stores/taskStore";

export function TaskRunPage() {
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const { activeTask, isLoading, loadTask, cancelTask } = useTaskStore();
  const pollingRef = React.useRef<ReturnType<typeof setInterval>>();

  // Load task on mount
  React.useEffect(() => {
    if (taskId) {
      loadTask(taskId);
    }
  }, [taskId, loadTask]);

  // Poll for status updates while running/pending
  React.useEffect(() => {
    if (!activeTask || activeTask.status === "pending" || activeTask.status === "running") {
      pollingRef.current = setInterval(() => {
        if (taskId) loadTask(taskId);
      }, 3000);
    }
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [activeTask?.status, taskId, loadTask]);

  // Navigate to chat when quick_chat task has a conversation
  React.useEffect(() => {
    if (activeTask?.type === "quick_chat" && activeTask.conversationId) {
      navigate(`/chat/${activeTask.conversationId}`, { replace: true });
    }
  }, [activeTask?.type, activeTask?.conversationId, navigate]);

  const task = activeTask;
  const canCancel = task && (task.status === "pending" || task.status === "running");

  return (
    <MainLayout>
      <div className="mx-auto flex h-full max-w-3xl flex-col overflow-y-auto px-4 py-6">
        {/* Header */}
        <div className="mb-6 flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 shrink-0"
            onClick={() => navigate("/workspace/tasks")}
          >
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div className="flex-1 min-w-0">
            <h1
              className="truncate text-lg font-semibold"
              style={{ color: "var(--theme-text-primary)" }}
            >
              {task?.title || task?.question || "任务详情"}
            </h1>
            <div className="mt-1 flex items-center gap-2">
              {task && <TaskTypeBadge type={task.type} />}
              {task && <TaskStatusBadge status={task.status} />}
            </div>
          </div>
          {canCancel && (
            <Button
              variant="ghost"
              size="sm"
              className="gap-1.5 text-xs"
              style={{ color: "var(--theme-text-muted)" }}
              onClick={() => task && cancelTask(task.taskId)}
            >
              <X className="h-3.5 w-3.5" />
              取消
            </Button>
          )}
        </div>

        {/* Task Content */}
        {isLoading && !task ? (
          <div className="flex flex-1 items-center justify-center">
            <div
              className="text-sm"
              style={{ color: "var(--theme-text-muted)" }}
            >
              加载中...
            </div>
          </div>
        ) : task ? (
          <div className="space-y-4">
            {/* Meta info */}
            <div
              className="rounded-xl p-4"
              style={{
                backgroundColor: "var(--theme-glass-bg)",
                border: "1px solid var(--theme-glass-border)"
              }}
            >
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt style={{ color: "var(--theme-text-muted)" }}>任务 ID</dt>
                  <dd className="mt-0.5 font-mono text-xs" style={{ color: "var(--theme-text-secondary)" }}>
                    {task.taskId}
                  </dd>
                </div>
                <div>
                  <dt style={{ color: "var(--theme-text-muted)" }}>创建时间</dt>
                  <dd className="mt-0.5 text-xs" style={{ color: "var(--theme-text-secondary)" }}>
                    {format(new Date(task.createdAt), "yyyy-MM-dd HH:mm:ss", { locale: zhCN })}
                  </dd>
                </div>
                {task.startedAt && (
                  <div>
                    <dt style={{ color: "var(--theme-text-muted)" }}>开始时间</dt>
                    <dd className="mt-0.5 text-xs" style={{ color: "var(--theme-text-secondary)" }}>
                      {format(new Date(task.startedAt), "yyyy-MM-dd HH:mm:ss", { locale: zhCN })}
                    </dd>
                  </div>
                )}
                {task.finishedAt && (
                  <div>
                    <dt style={{ color: "var(--theme-text-muted)" }}>结束时间</dt>
                    <dd className="mt-0.5 text-xs" style={{ color: "var(--theme-text-secondary)" }}>
                      {format(new Date(task.finishedAt), "yyyy-MM-dd HH:mm:ss", { locale: zhCN })}
                    </dd>
                  </div>
                )}
                {task.agentId && (
                  <div>
                    <dt style={{ color: "var(--theme-text-muted)" }}>Agent</dt>
                    <dd className="mt-0.5 font-mono text-xs" style={{ color: "var(--theme-text-secondary)" }}>
                      {task.agentId}
                    </dd>
                  </div>
                )}
                {task.runId && (
                  <div>
                    <dt style={{ color: "var(--theme-text-muted)" }}>Run ID</dt>
                    <dd className="mt-0.5 font-mono text-xs" style={{ color: "var(--theme-text-secondary)" }}>
                      {task.runId}
                    </dd>
                  </div>
                )}
              </dl>
            </div>

            {/* Question */}
            {task.question && (
              <div
                className="rounded-xl p-4"
                style={{
                  backgroundColor: "var(--theme-glass-bg)",
                  border: "1px solid var(--theme-glass-border)"
                }}
              >
                <h3 className="mb-2 text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
                  输入
                </h3>
                <p className="text-sm leading-relaxed" style={{ color: "var(--theme-text-primary)" }}>
                  {task.question}
                </p>
              </div>
            )}

            {/* Agent run info */}
            {task.type === "agent_run" && task.runId && (
              <div
                className="rounded-xl p-4"
                style={{
                  backgroundColor: "var(--theme-glass-bg)",
                  border: "1px solid var(--theme-glass-border)"
                }}
              >
                <h3 className="mb-2 text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
                  Agent 运行
                </h3>
                <p className="text-sm" style={{ color: "var(--theme-text-secondary)" }}>
                  Agent 运行已启动，Run ID: <code className="font-mono text-xs">{task.runId}</code>
                </p>
                <p className="mt-2 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                  任务完成后将在此页面显示运行结果和产物。
                </p>
              </div>
            )}

            {/* Status: pending with no conversation yet */}
            {task.type === "quick_chat" && !task.conversationId && (
              <div
                className="flex items-center gap-3 rounded-xl p-4"
                style={{
                  backgroundColor: "var(--theme-accent-alpha-15, rgba(168,85,247,0.1))",
                  border: "1px solid var(--theme-glass-border)"
                }}
              >
                <div
                  className="h-4 w-4 animate-spin rounded-full border-2 border-t-transparent"
                  style={{ borderColor: "var(--theme-accent)", borderTopColor: "transparent" }}
                />
                <span className="text-sm" style={{ color: "var(--theme-text-secondary)" }}>
                  正在准备聊天会话...
                </span>
              </div>
            )}
          </div>
        ) : (
          <div className="flex flex-1 items-center justify-center">
            <div className="text-sm" style={{ color: "var(--theme-text-muted)" }}>
              任务未找到
            </div>
          </div>
        )}
      </div>
    </MainLayout>
  );
}
