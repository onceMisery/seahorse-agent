import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import type { AgentMemory } from "@/types";

interface MemoryInspectorTabProps {
  memories: AgentMemory[];
}

export function MemoryInspectorTab({ memories }: MemoryInspectorTabProps) {
  if (memories.length === 0) return <InspectorEmptyState />;

  return (
    <div className="p-3 space-y-2">
      {memories.map((memory) => (
        <div
          key={memory.id}
          className="rounded-lg p-3 space-y-1"
          style={{ border: "1px solid var(--sh-workbench-border)" }}
        >
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-medium" style={{ color: "var(--theme-text-primary)" }}>
              {memory.title}
            </span>
            {memory.action && (
              <span
                className="shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                style={{
                  backgroundColor: "var(--sh-workbench-accent-soft)",
                  color: "var(--sh-workbench-accent)"
                }}
              >
                {memory.action}
              </span>
            )}
          </div>
          {memory.content && (
            <p
              className="text-[11px] leading-relaxed"
              style={{ color: "var(--theme-text-muted)" }}
            >
              {memory.content}
            </p>
          )}
        </div>
      ))}
    </div>
  );
}
