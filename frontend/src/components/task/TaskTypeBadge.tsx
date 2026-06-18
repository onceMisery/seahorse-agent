import type { Task } from "@/types/task";

const TYPE_CONFIG: Record<Task["type"], { label: string; icon: string }> = {
  quick_chat: { label: "快速聊天", icon: "💬" },
  agent_run: { label: "Agent 运行", icon: "🤖" }
};

interface TaskTypeBadgeProps {
  type: Task["type"];
}

export function TaskTypeBadge({ type }: TaskTypeBadgeProps) {
  const config = TYPE_CONFIG[type] ?? TYPE_CONFIG.quick_chat;
  return (
    <span
      className="inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs"
      style={{
        color: "var(--theme-text-secondary)",
        backgroundColor: "var(--theme-glass-bg)",
        border: "1px solid var(--theme-glass-border)"
      }}
    >
      <span className="text-[10px]">{config.icon}</span>
      {config.label}
    </span>
  );
}
