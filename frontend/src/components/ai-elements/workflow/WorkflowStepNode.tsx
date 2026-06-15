import { AlertCircle, CheckCircle, Clock, Loader2, MinusCircle, type LucideIcon } from "lucide-react";
import { Handle, Position, type Node, type NodeProps } from "@xyflow/react";

import type { WorkflowStepNodeData } from "@/components/ai-elements/types";
import { cn } from "@/lib/utils";

interface StatusMeta {
  icon: LucideIcon;
  color: string;
  bg: string;
  label: string;
}

const STATUS_MAP: Record<string, StatusMeta> = {
  COMPLETED: { icon: CheckCircle, color: "#10b981", bg: "rgba(16,185,129,0.12)", label: "已完成" },
  SUCCESS:   { icon: CheckCircle, color: "#10b981", bg: "rgba(16,185,129,0.12)", label: "已完成" },
  FAILED:    { icon: AlertCircle, color: "#ef4444", bg: "rgba(239,68,68,0.12)", label: "失败" },
  ERROR:     { icon: AlertCircle, color: "#ef4444", bg: "rgba(239,68,68,0.12)", label: "失败" },
  RUNNING:   { icon: Loader2, color: "#3b82f6", bg: "rgba(59,130,246,0.12)", label: "运行中" },
  SKIPPED:   { icon: MinusCircle, color: "#94a3b8", bg: "rgba(148,163,184,0.14)", label: "已跳过" },
};

const DEFAULT_STATUS: StatusMeta = { icon: Clock, color: "#f59e0b", bg: "rgba(245,158,11,0.12)", label: "等待中" };

function statusMeta(status?: string): StatusMeta {
  const key = status?.toUpperCase() ?? "";
  return STATUS_MAP[key] ?? DEFAULT_STATUS;
}

type WorkflowStepNodeModel = Node<WorkflowStepNodeData, "workflowStep">;

export function WorkflowStepNode({ data, selected }: NodeProps<WorkflowStepNodeModel>) {
  const meta = statusMeta(data.status);
  const Icon = meta.icon;

  return (
    <div
      className={cn("min-w-[220px] rounded-lg border bg-white px-3 py-3 shadow-sm", selected && "ring-2")}
      style={{
        borderColor: selected ? "var(--theme-accent)" : "var(--theme-glass-border)",
        color: "var(--theme-text-primary)"
      }}
    >
      <Handle type="target" position={Position.Top} />
      <div className="flex items-start gap-2">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md" style={{ background: meta.bg }}>
          <Icon className={cn("h-4 w-4", data.status?.toUpperCase() === "RUNNING" && "animate-spin")} style={{ color: meta.color }} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            {typeof data.stepNo === "number" ? (
              <span className="font-mono text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
                #{data.stepNo}
              </span>
            ) : null}
            {data.stepType ? (
              <span className="rounded px-1.5 py-0.5 font-mono text-[10px]" style={{ background: meta.bg, color: meta.color }}>
                {data.stepType}
              </span>
            ) : null}
          </div>
          <div className="mt-1 line-clamp-2 text-sm font-medium">{data.label}</div>
          <div className="mt-2 flex items-center gap-2 text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
            <span>{meta.label}</span>
            {data.duration ? <span>{data.duration}</span> : null}
          </div>
          {data.errorMessage ? (
            <div className="mt-2 line-clamp-2 text-[11px]" style={{ color: "#ef4444" }}>
              {data.errorMessage}
            </div>
          ) : null}
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
