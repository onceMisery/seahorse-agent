import * as React from "react";
import { AlertTriangle, CheckCircle2 } from "lucide-react";

import { cn } from "@/lib/utils";
import { resolveMemoryConflictInteractive } from "@/services/memoryGovernanceService";
import type { MemoryConflictPrompt } from "@/types";

interface MemoryConflictInteractiveCardProps {
  prompt: MemoryConflictPrompt;
  onResolved?: (conflictId: string, action: string) => void;
}

export function MemoryConflictInteractiveCard({ prompt, onResolved }: MemoryConflictInteractiveCardProps) {
  const [selectedAction, setSelectedAction] = React.useState(prompt.selectedAction ?? "");
  const [mergeContent, setMergeContent] = React.useState("");
  const [status, setStatus] = React.useState(prompt.status ?? "pending");
  const [submitting, setSubmitting] = React.useState(false);

  const isResolved = status === "resolved";
  const canConfirm = Boolean(selectedAction) && !submitting && !isResolved;

  const confirm = async () => {
    if (!selectedAction) return;
    setSubmitting(true);
    setStatus("pending");
    try {
      const request = {
        conflictId: prompt.conflictId,
        action: selectedAction,
        source: "chat-ui"
      };
      const mergedContent = mergeContent.trim();
      const result = await resolveMemoryConflictInteractive(selectedAction === "merge" && mergedContent
        ? { ...request, mergedContent }
        : request);
      if (result?.resolved === false) {
        setStatus("failed");
        return;
      }
      setStatus("resolved");
      onResolved?.(prompt.conflictId, selectedAction);
    } catch {
      setStatus("failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="rounded-lg border p-3 text-sm"
      style={{
        borderColor: "var(--theme-accent-alpha-20)",
        backgroundColor: "var(--theme-bg-elevated)",
        color: "var(--theme-text-primary)"
      }}
    >
      <div className="flex items-start gap-2">
        <div
          className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-md"
          style={{ backgroundColor: "var(--theme-accent-alpha-10)" }}
        >
          {isResolved ? (
            <CheckCircle2 className="h-4 w-4" style={{ color: "var(--theme-accent)" }} />
          ) : (
            <AlertTriangle className="h-4 w-4" style={{ color: "var(--theme-accent)" }} />
          )}
        </div>
        <div className="min-w-0 flex-1 space-y-3">
          <div>
            <p className="font-medium leading-6">
              {isResolved ? "已根据你的选择处理" : prompt.question || "请确认这两条记忆应如何处理"}
            </p>
            {!isResolved ? (
              <div className="mt-2 grid gap-2 text-xs md:grid-cols-2">
                <MemoryRef label="记忆 A" content={prompt.contentA} memoryId={prompt.memoryId1} />
                <MemoryRef label="记忆 B" content={prompt.contentB} memoryId={prompt.memoryId2} />
              </div>
            ) : null}
          </div>

          {!isResolved ? (
            <div className="flex flex-wrap gap-2">
              {prompt.options.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setSelectedAction(option.value)}
                  className={cn(
                    "rounded-md border px-2.5 py-1.5 text-xs transition",
                    selectedAction === option.value && "font-medium"
                  )}
                  style={{
                    borderColor: selectedAction === option.value
                      ? "var(--theme-accent)"
                      : "var(--theme-accent-alpha-20)",
                    backgroundColor: selectedAction === option.value
                      ? "var(--theme-accent-alpha-20)"
                      : "transparent",
                    color: "var(--theme-text-primary)"
                  }}
                >
                  {option.label}
                </button>
              ))}
            </div>
          ) : null}

          {!isResolved && selectedAction === "merge" ? (
            <textarea
              aria-label="合并后的记忆内容"
              value={mergeContent}
              onChange={(event) => setMergeContent(event.target.value)}
              className="min-h-20 w-full resize-y rounded-md border bg-transparent p-2 text-sm outline-none"
              style={{
                borderColor: "var(--theme-accent-alpha-20)",
                color: "var(--theme-text-primary)"
              }}
            />
          ) : null}

          {!isResolved ? (
            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={!canConfirm}
                onClick={() => void confirm()}
                className="rounded-md px-3 py-1.5 text-xs font-medium transition disabled:pointer-events-none disabled:opacity-50"
                style={{
                  backgroundColor: "var(--theme-accent)",
                  color: "var(--theme-bg)"
                }}
              >
                {submitting ? "处理中" : "确认"}
              </button>
              {status === "failed" ? (
                <span className="text-xs text-rose-400">处理失败，请重试。</span>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function MemoryRef({ label, content, memoryId }: { label: string; content?: string; memoryId?: string }) {
  return (
    <div
      className="min-w-0 rounded-md border p-2"
      style={{ borderColor: "var(--theme-accent-alpha-10)" }}
    >
      <div className="mb-1 font-medium" style={{ color: "var(--theme-text-secondary)" }}>
        {label}
      </div>
      <div className="break-words" style={{ color: "var(--theme-text-primary)" }}>
        {content?.trim() || memoryId || "-"}
      </div>
    </div>
  );
}
