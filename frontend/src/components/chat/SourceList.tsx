import * as React from "react";
import { ChevronDown, ChevronRight, ExternalLink, FileText } from "lucide-react";

import type { AgentSource } from "@/types";

interface SourceListProps {
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

export function SourceList({ sources }: SourceListProps) {
  const [expandedId, setExpandedId] = React.useState<string | null>(null);

  if (sources.length === 0) return null;

  return (
    <div className="space-y-2">
      <div className="text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
        Sources ({sources.length})
      </div>
      <div className="space-y-1.5">
        {sources.map((source, index) => {
          const confidence = confidenceFromScore(source.score);
          const style = CONFIDENCE_STYLES[confidence] ?? CONFIDENCE_STYLES.UNKNOWN;
          const isExpanded = expandedId === source.id;

          return (
            <div
              key={source.id}
              className="rounded-lg overflow-hidden transition-colors"
              style={{
                border: "1px solid var(--theme-glass-border)",
                backgroundColor: "var(--theme-bg-surface)"
              }}
            >
              {/* 折叠头部 */}
              <button
                onClick={() => setExpandedId(isExpanded ? null : source.id)}
                className="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-white/5 transition-colors"
              >
                {isExpanded
                  ? <ChevronDown className="h-3 w-3 shrink-0" style={{ color: "var(--theme-text-muted)" }} />
                  : <ChevronRight className="h-3 w-3 shrink-0" style={{ color: "var(--theme-text-muted)" }} />}
                <span className="inline-flex items-center justify-center h-4 w-4 rounded text-[10px] font-bold shrink-0"
                  style={{ backgroundColor: "var(--theme-accent-muted)", color: "var(--theme-accent)" }}>
                  {index + 1}
                </span>
                <span className="truncate text-sm font-medium flex-1" style={{ color: "var(--theme-text-primary)" }}>
                  {source.title}
                </span>
                <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium shrink-0"
                  style={{ backgroundColor: style.bg, color: style.text }}>
                  {style.label}
                </span>
              </button>

              {/* 展开详情 */}
              {isExpanded && (
                <div className="px-3 pb-2.5 pt-0 space-y-2"
                  style={{ borderTop: "1px solid var(--theme-glass-border)" }}>
                  {source.snippet && (
                    <p className="text-xs leading-relaxed pt-2" style={{ color: "var(--theme-text-muted)" }}>
                      {source.snippet}
                    </p>
                  )}
                  <div className="flex flex-wrap items-center gap-2 text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
                    {source.sourceType && (
                      <span className="px-1.5 py-0.5 rounded"
                        style={{ backgroundColor: "var(--theme-bg-elevated)" }}>
                        {source.sourceType}
                      </span>
                    )}
                    {typeof source.score === "number" && (
                      <span>相关度 {Math.round(source.score * 100)}%</span>
                    )}
                  </div>
                  {source.url && (
                    <a href={source.url} target="_blank" rel="noreferrer"
                      className="inline-flex items-center gap-1 text-xs hover:underline"
                      style={{ color: "var(--theme-accent)" }}>
                      <ExternalLink className="h-3 w-3" />
                      <span className="truncate max-w-[250px]">{source.url}</span>
                    </a>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
