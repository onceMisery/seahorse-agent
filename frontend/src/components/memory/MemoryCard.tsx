import { Loader2, Trash2 } from "lucide-react";
import { Link } from "react-router-dom";
import type { UserMemory } from "@/types";

interface MemoryCardProps {
  memory: UserMemory;
  deleting: boolean;
  onDelete: () => void;
}

function memoryTypeLabel(value?: string | null): string {
  if (!value) return "Memory";
  return value.replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

function formatUpdatedAt(value?: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString();
}

export function MemoryCard({ memory, deleting, onDelete }: MemoryCardProps) {
  return (
    <article
      className="rounded-2xl p-4"
      style={{
        backgroundColor: "var(--theme-bg-elevated)",
        border: "1px solid var(--theme-glass-border)"
      }}
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            {memory.memoryType && (
              <span
                className="rounded-full px-2 py-0.5 text-[11px] font-medium"
                style={{
                  backgroundColor: "var(--theme-accent-alpha-10)",
                  color: "var(--theme-accent)"
                }}
              >
                {memoryTypeLabel(memory.memoryType)}
              </span>
            )}
            {memory.sensitivity && (
              <span
                className="rounded-full px-2 py-0.5 text-[11px]"
                style={{
                  backgroundColor: "var(--theme-bg-elevated)",
                  border: "1px solid var(--theme-glass-border)",
                  color: "var(--theme-text-muted)"
                }}
              >
                {memory.sensitivity}
              </span>
            )}
            {memory.status && (
              <span
                className="rounded-full px-2 py-0.5 text-[11px]"
                style={{
                  backgroundColor: "var(--theme-bg-elevated)",
                  border: "1px solid var(--theme-glass-border)",
                  color: "var(--theme-text-muted)"
                }}
              >
                {memory.status}
              </span>
            )}
          </div>
          <p className="mt-3 text-sm leading-6" style={{ color: "var(--theme-text-primary)" }}>
            {memory.displayText}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-3">
            {memory.updatedAt && (
              <p className="text-xs" style={{ color: "var(--theme-text-muted)" }}>
                {formatUpdatedAt(memory.updatedAt)}
              </p>
            )}
            {memory.sourceConversationId && (
              <Link
                to={`/chat/${memory.sourceConversationId}`}
                className="text-xs hover:underline"
                style={{ color: "var(--theme-accent)" }}
              >
                查看来源对话
              </Link>
            )}
          </div>
        </div>
        <button
          type="button"
          aria-label="删除记忆"
          onClick={onDelete}
          disabled={deleting}
          className="flex h-9 w-9 shrink-0 items-center justify-center self-start rounded-xl transition-colors disabled:cursor-not-allowed disabled:opacity-60 hover:bg-rose-500/10"
          style={{ color: "rgb(244,63,94)" }}
        >
          {deleting ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Trash2 className="h-4 w-4" />
          )}
        </button>
      </div>
    </article>
  );
}
