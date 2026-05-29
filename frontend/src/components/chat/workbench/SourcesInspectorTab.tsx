import * as React from "react";
import { ExternalLink } from "lucide-react";

import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import type { AgentSource } from "@/types";

interface SourcesInspectorTabProps {
  sources: AgentSource[];
}

const CONFIDENCE_STYLES: Record<string, { bg: string; text: string; label: string }> = {
  HIGH: { bg: "rgba(34,197,94,0.15)", text: "rgb(34,197,94)", label: "高可信" },
  MEDIUM: { bg: "rgba(234,179,8,0.15)", text: "rgb(234,179,8)", label: "中可信" },
  LOW: { bg: "rgba(239,68,68,0.15)", text: "rgb(239,68,68)", label: "低可信" },
  UNKNOWN: { bg: "rgba(156,163,175,0.15)", text: "rgb(156,163,175)", label: "未知" }
};

function confidenceFromScore(score?: number): string {
  if (score == null) return "UNKNOWN";
  if (score >= 0.85) return "HIGH";
  if (score >= 0.7) return "MEDIUM";
  if (score > 0) return "LOW";
  return "UNKNOWN";
}

export function SourcesInspectorTab({ sources }: SourcesInspectorTabProps) {
  const [expandedId, setExpandedId] = React.useState<string | null>(null);

  if (sources.length === 0) return <InspectorEmptyState />;

  return (
    <div className="p-3 space-y-1.5">
      {sources.map((source, index) => {
        const confidence = confidenceFromScore(source.score);
        const style = CONFIDENCE_STYLES[confidence] ?? CONFIDENCE_STYLES.UNKNOWN;
        const isExpanded = expandedId === source.id;

        return (
          <div
            key={source.id}
            className="overflow-hidden rounded-lg"
            style={{ border: "1px solid var(--sh-workbench-border)" }}
          >
            <button
              type="button"
              onClick={() => setExpandedId(isExpanded ? null : source.id)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left transition-colors hover:bg-white/5"
            >
              <span
                className="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded text-[10px] font-bold"
                style={{
                  backgroundColor: "var(--sh-workbench-accent-soft)",
                  color: "var(--sh-workbench-accent)"
                }}
              >
                {index + 1}
              </span>
              <span
                className="flex-1 truncate text-xs font-medium"
                style={{ color: "var(--theme-text-primary)" }}
              >
                {source.title}
              </span>
              <span
                className="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium"
                style={{ backgroundColor: style.bg, color: style.text }}
              >
                {style.label}
              </span>
            </button>

            {isExpanded && (
              <div
                className="space-y-2 px-3 pb-2.5 pt-2"
                style={{ borderTop: "1px solid var(--sh-workbench-border)" }}
              >
                {source.snippet && (
                  <p
                    className="text-[11px] leading-relaxed"
                    style={{ color: "var(--theme-text-muted)" }}
                  >
                    {source.snippet}
                  </p>
                )}
                <div
                  className="flex flex-wrap items-center gap-2 text-[11px]"
                  style={{ color: "var(--theme-text-muted)" }}
                >
                  {source.sourceType && (
                    <span
                      className="rounded px-1.5 py-0.5"
                      style={{ backgroundColor: "var(--sh-workbench-panel-subtle)" }}
                    >
                      {source.sourceType}
                    </span>
                  )}
                  {typeof source.score === "number" && (
                    <span>相关度 {Math.round(source.score * 100)}%</span>
                  )}
                  {typeof source.citationIndex === "number" && (
                    <span>引用 #{source.citationIndex}</span>
                  )}
                </div>
                {source.url && (
                  <a
                    href={source.url}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-[11px] hover:underline"
                    style={{ color: "var(--sh-workbench-accent)" }}
                  >
                    <ExternalLink className="h-3 w-3" />
                    <span className="max-w-[220px] truncate">{source.url}</span>
                  </a>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
