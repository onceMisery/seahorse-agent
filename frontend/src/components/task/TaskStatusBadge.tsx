import type { Task } from "@/types/task";

const STATUS_CONFIG: Record<Task["status"], { label: string; color: string; bg: string }> = {
  pending: { label: "等待中", color: "var(--theme-text-muted)", bg: "var(--theme-glass-bg)" },
  running: { label: "运行中", color: "var(--theme-accent)", bg: "var(--theme-accent-alpha-15, rgba(168,85,247,0.15))" },
  succeeded: { label: "已完成", color: "#22c55e", bg: "rgba(34,197,94,0.12)" },
  failed: { label: "失败", color: "#ef4444", bg: "rgba(239,68,68,0.12)" },
  cancelled: { label: "已取消", color: "var(--theme-text-muted)", bg: "var(--theme-glass-bg)" }
};

interface TaskStatusBadgeProps {
  status: Task["status"];
}

export function TaskStatusBadge({ status }: TaskStatusBadgeProps) {
  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG.pending;
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
      style={{ color: config.color, backgroundColor: config.bg }}
    >
      {status === "running" && (
        <span
          className="h-1.5 w-1.5 animate-pulse rounded-full"
          style={{ backgroundColor: config.color }}
        />
      )}
      {config.label}
    </span>
  );
}
