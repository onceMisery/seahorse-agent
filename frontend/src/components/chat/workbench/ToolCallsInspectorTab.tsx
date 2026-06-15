import * as React from "react";
import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import type { AgentToolCallView } from "@/types";

interface ToolCallsInspectorTabProps {
  toolCalls: AgentToolCallView[];
}

function statusStyle(status?: string): { bg: string; text: string } {
  const normalized = (status ?? "").toUpperCase();
  if (normalized === "SUCCEEDED" || normalized === "DONE" || normalized === "COMPLETED") {
    return { bg: "rgba(34,197,94,0.15)", text: "rgb(34,197,94)" };
  }
  if (normalized === "RUNNING" || normalized === "IN_PROGRESS") {
    return { bg: "rgba(59,130,246,0.15)", text: "rgb(59,130,246)" };
  }
  if (normalized === "WAITING_USER" || normalized === "PENDING_APPROVAL") {
    return { bg: "rgba(245,158,11,0.16)", text: "rgb(217,119,6)" };
  }
  if (normalized === "FAILED" || normalized === "ERROR") {
    return { bg: "rgba(239,68,68,0.15)", text: "rgb(239,68,68)" };
  }
  return { bg: "var(--sh-workbench-panel-subtle)", text: "var(--theme-text-muted)" };
}

function formatDuration(durationMs?: number): string | null {
  if (typeof durationMs !== "number" || !Number.isFinite(durationMs)) return null;
  if (durationMs < 1000) return `${Math.round(durationMs)}ms`;
  return `${(durationMs / 1000).toFixed(1)}s`;
}

function formatPreview(value?: string): string {
  if (!value) return "暂无预览";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

export function ToolCallsInspectorTab({ toolCalls }: ToolCallsInspectorTabProps) {
  if (toolCalls.length === 0) return <InspectorEmptyState />;

  return (
    <div className="space-y-2 p-3">
      {toolCalls.map((toolCall) => {
        const style = statusStyle(toolCall.status);
        const duration = formatDuration(toolCall.durationMs);

        return (
          <div
            key={toolCall.id}
            className="rounded-lg p-3"
            style={{
              backgroundColor: "var(--sh-workbench-panel-subtle)",
              border: "1px solid var(--sh-workbench-border)"
            }}
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-xs font-semibold" style={{ color: "var(--theme-text-primary)" }}>
                {toolCall.toolId}
              </span>
              <span
                className="rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                style={{ backgroundColor: style.bg, color: style.text }}
              >
                {toolCall.status}
              </span>
              {duration ? (
                <span className="text-[10px]" style={{ color: "var(--theme-text-muted)" }}>
                  {duration}
                </span>
              ) : null}
              {toolCall.riskLevel ? (
                <span className="text-[10px]" style={{ color: "var(--theme-text-muted)" }}>
                  {toolCall.riskLevel}
                </span>
              ) : null}
            </div>

            <pre
              className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap break-words rounded-md p-2 font-mono text-[11px]"
              style={{
                backgroundColor: "var(--sh-workbench-panel)",
                border: "1px solid var(--sh-workbench-border)",
                color: "var(--theme-text-secondary)"
              }}
            >
              {formatPreview(toolCall.argumentsPreviewJson)}
            </pre>

            {toolCall.resultSummary ? (
              <p className="mt-2 text-xs" style={{ color: "var(--theme-text-secondary)" }}>
                {toolCall.resultSummary}
              </p>
            ) : null}
            {toolCall.error ? (
              <p className="mt-2 text-xs" style={{ color: "rgb(239,68,68)" }}>
                {toolCall.error}
              </p>
            ) : null}
            {toolCall.approvalId ? (
              <p className="mt-2 font-mono text-[10px]" style={{ color: "var(--theme-text-muted)" }}>
                审批：{toolCall.approvalId}
              </p>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
