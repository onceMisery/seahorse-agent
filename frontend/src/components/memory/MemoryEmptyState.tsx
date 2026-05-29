import { Database } from "lucide-react";

interface MemoryEmptyStateProps {
  hasFilter: boolean;
}

export function MemoryEmptyState({ hasFilter }: MemoryEmptyStateProps) {
  return (
    <div
      className="flex min-h-[260px] flex-col items-center justify-center rounded-2xl p-8 text-center"
      style={{
        backgroundColor: "var(--theme-bg-elevated)",
        border: "1px solid var(--theme-glass-border)"
      }}
    >
      <Database className="h-10 w-10" style={{ color: "var(--theme-text-muted)" }} />
      <p className="mt-4 text-base font-semibold" style={{ color: "var(--theme-text-primary)" }}>
        {hasFilter ? "没有匹配当前筛选的记忆" : "还没有长期记忆"}
      </p>
      <p className="mt-2 max-w-md text-sm" style={{ color: "var(--theme-text-secondary)" }}>
        {hasFilter
          ? "尝试调整搜索条件或清除筛选"
          : "助手在对话中发现有用的偏好和事实后，会自动保存到这里"}
      </p>
    </div>
  );
}
