import * as React from "react";
import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import type { AgentTimelineItem } from "@/types";

interface TimelineInspectorTabProps {
  timeline: AgentTimelineItem[];
  currentStepId?: string | null;
}

const RESEARCH_STEP_LABELS: Record<string, string> = {
  PLAN: "规划研究方向",
  SEARCH: "搜索相关资料",
  FETCH: "抓取网页内容",
  EXTRACT_EVIDENCE: "提取关键证据",
  SYNTHESIZE: "综合分析",
  WRITE_REPORT: "撰写报告",
  VERIFY_CITATIONS: "验证引用"
};

function localizeStepTitle(title: string): string {
  const upper = title.toUpperCase().replace(/\s+/g, "_");
  return RESEARCH_STEP_LABELS[upper] ?? RESEARCH_STEP_LABELS[title] ?? title;
}

function formatDuration(durationMs?: number): string {
  if (typeof durationMs !== "number") return "";
  if (durationMs < 1000) return `${Math.round(durationMs)}ms`;
  return `${(durationMs / 1000).toFixed(1)}s`;
}

function statusStyle(status?: string): { bg: string; text: string } {
  const s = (status ?? "").toUpperCase();
  if (s === "DONE" || s === "FINISHED" || s === "COMPLETED") {
    return { bg: "rgba(34,197,94,0.15)", text: "rgb(34,197,94)" };
  }
  if (s === "RUNNING" || s === "IN_PROGRESS") {
    return { bg: "rgba(59,130,246,0.15)", text: "rgb(59,130,246)" };
  }
  if (s === "ERROR" || s === "FAILED") {
    return { bg: "rgba(239,68,68,0.15)", text: "rgb(239,68,68)" };
  }
  return { bg: "var(--sh-workbench-panel-subtle)", text: "var(--theme-text-muted)" };
}

export function TimelineInspectorTab({ timeline, currentStepId }: TimelineInspectorTabProps) {
  if (timeline.length === 0) return <InspectorEmptyState />;

  return (
    <div className="p-3 space-y-1">
      {timeline.map((item) => {
        const isActive = item.id === currentStepId;
        const style = statusStyle(item.status);
        const duration = formatDuration(item.durationMs);

        return (
          <div
            key={item.id}
            className="flex gap-3 rounded-lg px-3 py-2.5"
            style={{
              backgroundColor: isActive ? "var(--sh-workbench-accent-soft)" : "transparent",
              border: isActive ? "1px solid var(--sh-workbench-accent)" : "1px solid transparent"
            }}
          >
            <div className="mt-1.5 flex shrink-0 flex-col items-center gap-1">
              <div
                className="h-2 w-2 rounded-full"
                style={{ backgroundColor: style.text }}
              />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span
                  className="text-xs font-medium"
                  style={{ color: isActive ? "var(--sh-workbench-accent)" : "var(--theme-text-primary)" }}
                >
                  {localizeStepTitle(item.title)}
                </span>
                {item.status && (
                  <span
                    className="rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                    style={{ backgroundColor: style.bg, color: style.text }}
                  >
                    {item.status}
                  </span>
                )}
                {duration && (
                  <span className="text-[10px]" style={{ color: "var(--theme-text-muted)" }}>
                    {duration}
                  </span>
                )}
              </div>
              {item.detail && (
                <p
                  className="mt-0.5 text-[11px] leading-relaxed"
                  style={{ color: "var(--theme-text-muted)" }}
                >
                  {item.detail}
                </p>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
