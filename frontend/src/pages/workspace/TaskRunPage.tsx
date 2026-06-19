import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, X, RefreshCw } from "lucide-react";
import { format } from "date-fns";
import { zhCN } from "date-fns/locale";

import { TaskStatusBadge } from "@/components/task/TaskStatusBadge";
import { TaskTypeBadge } from "@/components/task/TaskTypeBadge";
import { TaskTimeline } from "@/components/task/TaskTimeline";
import { TaskArtifacts } from "@/components/task/TaskArtifacts";
import { MainLayout } from "@/components/layout/MainLayout";
import { Button } from "@/components/ui/button";
import { useTaskStore } from "@/stores/taskStore";
import { listTaskArtifacts, subscribeTaskEvents } from "@/services/taskService";
import type { TaskArtifact, TaskEvent } from "@/types/task";

export function TaskRunPage() {
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const { activeTask, isLoading, loadTask, cancelTask } = useTaskStore();

  const [events, setEvents] = React.useState<TaskEvent[]>([]);
  const [artifacts, setArtifacts] = React.useState<TaskArtifact[]>([]);
  const [connecting, setConnecting] = React.useState(false);
  const [reconnecting, setReconnecting] = React.useState(false);
  const subRef = React.useRef<{ close: () => void } | null>(null);
  const seqRef = React.useRef<Set<number>>(new Set());

  // Load task on mount
  React.useEffect(() => {
    if (taskId) {
      loadTask(taskId);
    }
  }, [taskId, loadTask]);

  const task = activeTask;
  const isAgentRun = task?.type === "agent_run";

  // Conversational tasks → redirect to chat
  React.useEffect(() => {
    if (task && task.type !== "agent_run" && task.conversationId) {
      navigate(`/chat/${task.conversationId}`, { replace: true });
    }
  }, [task?.type, task?.conversationId, navigate]);

  const loadArtifacts = React.useCallback(() => {
    if (!taskId) return;
    listTaskArtifacts(taskId)
      .then(setArtifacts)
      .catch(() => null);
  }, [taskId]);

  // Subscribe to SSE events for agent_run tasks (with auto-reconnect)
  React.useEffect(() => {
    if (!taskId || !isAgentRun) return;

    let disposed = false;
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;

    const connect = () => {
      setConnecting(true);
      subRef.current = subscribeTaskEvents(taskId, {
        onEvent: (ev) => {
          if (seqRef.current.has(ev.seq)) return;
          seqRef.current.add(ev.seq);
          setEvents((prev) => [...prev, ev].sort((a, b) => a.seq - b.seq));
          if (ev.type === "artifact.created" || ev.type === "task.completed") {
            loadArtifacts();
          }
          if (ev.type === "task.completed" || ev.type === "task.failed") {
            setConnecting(false);
            loadTask(taskId);
          }
        },
        onError: () => {
          if (disposed) return;
          // auto-reconnect after 3s
          setReconnecting(true);
          reconnectTimer = setTimeout(() => {
            setReconnecting(false);
            connect();
          }, 3000);
        },
        onDone: () => {
          setConnecting(false);
        }
      });
    };

    connect();
    loadArtifacts();

    return () => {
      disposed = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      subRef.current?.close();
    };
  }, [taskId, isAgentRun, loadArtifacts, loadTask]);

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
            <h1 className="truncate text-lg font-semibold" style={{ color: "var(--theme-text-primary)" }}>
              {task?.title || task?.question || "任务详情"}
            </h1>
            <div className="mt-1 flex items-center gap-2">
              {task && <TaskTypeBadge type={task.type} />}
              {task && <TaskStatusBadge status={task.status} />}
              {reconnecting && (
                <span className="flex items-center gap-1 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                  <RefreshCw className="h-3 w-3 animate-spin" />
                  正在恢复连接
                </span>
              )}
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

        {isLoading && !task ? (
          <div className="flex flex-1 items-center justify-center">
            <div className="text-sm" style={{ color: "var(--theme-text-muted)" }}>
              加载中...
            </div>
          </div>
        ) : task ? (
          <div className="space-y-4">
            {/* Meta info */}
            <div
              className="rounded-xl p-4"
              style={{ backgroundColor: "var(--theme-glass-bg)", border: "1px solid var(--theme-glass-border)" }}
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
                style={{ backgroundColor: "var(--theme-glass-bg)", border: "1px solid var(--theme-glass-border)" }}
              >
                <h3 className="mb-2 text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
                  输入
                </h3>
                <p className="text-sm leading-relaxed" style={{ color: "var(--theme-text-primary)" }}>
                  {task.question}
                </p>
              </div>
            )}

            {/* Timeline (agent_run) */}
            {isAgentRun && (
              <div
                className="rounded-xl p-4"
                style={{ backgroundColor: "var(--theme-glass-bg)", border: "1px solid var(--theme-glass-border)" }}
              >
                <h3 className="mb-3 text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
                  运行过程
                </h3>
                <TaskTimeline events={events} connecting={connecting} />
              </div>
            )}

            {/* Artifacts */}
            {artifacts.length > 0 && (
              <div>
                <h3 className="mb-3 text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
                  产物
                </h3>
                <TaskArtifacts artifacts={artifacts} />
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
