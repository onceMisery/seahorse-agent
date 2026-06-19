import * as React from "react";
import {
  CheckCircle2,
  Circle,
  XCircle,
  Loader2,
  AlertTriangle,
  Wrench,
  Search,
  Brain,
  Sparkles,
  FileOutput,
  UserCheck
} from "lucide-react";
import { format } from "date-fns";

import type { TaskEvent } from "@/types/task";

interface TaskTimelineProps {
  events: TaskEvent[];
  connecting?: boolean;
}

/** 事件类型 → 图标/标签映射（路线图事件规范）。 */
const EVENT_META: Record<string, { icon: React.ComponentType<{ className?: string }>; tone: string }> = {
  "task.created": { icon: Circle, tone: "var(--theme-text-muted)" },
  "task.started": { icon: Loader2, tone: "var(--theme-accent)" },
  "model.selected": { icon: Sparkles, tone: "var(--theme-accent)" },
  "memory.recalled": { icon: Brain, tone: "var(--theme-accent)" },
  "retrieval.started": { icon: Search, tone: "var(--theme-accent)" },
  "retrieval.completed": { icon: Search, tone: "#22c55e" },
  "skill.selected": { icon: Sparkles, tone: "var(--theme-accent)" },
  "tool.started": { icon: Wrench, tone: "var(--theme-accent)" },
  "tool.completed": { icon: Wrench, tone: "#22c55e" },
  "approval.required": { icon: UserCheck, tone: "#f59e0b" },
  "artifact.created": { icon: FileOutput, tone: "#22c55e" },
  degraded: { icon: AlertTriangle, tone: "#f59e0b" },
  "task.completed": { icon: CheckCircle2, tone: "#22c55e" },
  "task.failed": { icon: XCircle, tone: "#ef4444" }
};

export function TaskTimeline({ events, connecting }: TaskTimelineProps) {
  if (events.length === 0 && !connecting) {
    return (
      <div className="text-sm" style={{ color: "var(--theme-text-muted)" }}>
        暂无运行事件
      </div>
    );
  }

  return (
    <ol className="space-y-0">
      {events.map((ev, idx) => {
        const meta = EVENT_META[ev.type] ?? { icon: Circle, tone: "var(--theme-text-muted)" };
        const Icon = meta.icon;
        const isLast = idx === events.length - 1;
        const spin = ev.type === "task.started" && isLast && connecting;
        return (
          <li key={`${ev.seq}-${ev.type}`} className="flex gap-3">
            <div className="flex flex-col items-center">
              <Icon
                className={`h-4 w-4 shrink-0 ${spin ? "animate-spin" : ""}`}
                style={{ color: meta.tone }}
              />
              {!isLast && (
                <div
                  className="w-px flex-1"
                  style={{ backgroundColor: "var(--theme-glass-border)", minHeight: "1.25rem" }}
                />
              )}
            </div>
            <div className={`min-w-0 flex-1 ${isLast ? "pb-0" : "pb-4"}`}>
              <div className="flex items-baseline justify-between gap-2">
                <p className="text-sm" style={{ color: "var(--theme-text-primary)" }}>
                  {ev.message || ev.type}
                </p>
                <span className="shrink-0 font-mono text-[10px]" style={{ color: "var(--theme-text-muted)" }}>
                  {format(new Date(ev.at), "HH:mm:ss")}
                </span>
              </div>
              {ev.data && typeof ev.data === "object" && "runId" in ev.data && (
                <p className="mt-0.5 truncate font-mono text-[10px]" style={{ color: "var(--theme-text-muted)" }}>
                  {String((ev.data as Record<string, unknown>).runId)}
                </p>
              )}
            </div>
          </li>
        );
      })}
      {connecting && events.length === 0 && (
        <li className="flex items-center gap-2 text-sm" style={{ color: "var(--theme-text-muted)" }}>
          <Loader2 className="h-4 w-4 animate-spin" />
          正在连接事件流...
        </li>
      )}
    </ol>
  );
}
