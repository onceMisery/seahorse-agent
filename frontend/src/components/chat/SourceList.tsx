import * as React from "react";
import { ChevronDown, ChevronRight, ExternalLink } from "lucide-react";

import type { AgentSource } from "@/types";

interface SourceListProps {
  sources: AgentSource[];
}

const TRUST_STYLES: Record<string, { bg: string; text: string; label: string }> = {
  HIGH: { bg: "var(--sh-trust-high-bg)", text: "var(--sh-trust-high)", label: "High trust" },
  MEDIUM: { bg: "var(--sh-trust-medium-bg)", text: "var(--sh-trust-medium)", label: "Medium trust" },
  LOW: { bg: "var(--sh-trust-low-bg)", text: "var(--sh-trust-low)", label: "Low trust" },
  UNTRUSTED: { bg: "var(--sh-trust-untrusted-bg)", text: "var(--sh-trust-untrusted)", label: "Untrusted" },
  UNKNOWN: { bg: "var(--sh-trust-unknown-bg)", text: "var(--sh-trust-unknown)", label: "Unknown" }
};

function trustLevelFromSource(source: AgentSource): string {
  const normalized = source.trustLevel?.toUpperCase();
  if (normalized && TRUST_STYLES[normalized]) return normalized;
  if (source.score == null) return "UNKNOWN";
  if (source.score >= 0.85) return "HIGH";
  if (source.score >= 0.7) return "MEDIUM";
  if (source.score > 0) return "LOW";
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
          const trustLevel = trustLevelFromSource(source);
          const style = TRUST_STYLES[trustLevel] ?? TRUST_STYLES.UNKNOWN;
          const isExpanded = expandedId === source.id;

          return (
            <div
              key={source.id}
              className="overflow-hidden rounded-lg transition-colors"
              style={{
                border: "1px solid var(--theme-glass-border)",
                backgroundColor: "var(--theme-bg-surface)"
              }}
            >
              <button
                onClick={() => setExpandedId(isExpanded ? null : source.id)}
                className="flex w-full items-center gap-2 px-3 py-2 text-left transition-colors hover:bg-white/5"
              >
                {isExpanded
                  ? <ChevronDown className="h-3 w-3 shrink-0" style={{ color: "var(--theme-text-muted)" }} />
                  : <ChevronRight className="h-3 w-3 shrink-0" style={{ color: "var(--theme-text-muted)" }} />}
                <span className="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded text-[10px] font-bold"
                  style={{ backgroundColor: "var(--theme-accent-muted)", color: "var(--theme-accent)" }}>
                  {index + 1}
                </span>
                <span className="flex-1 truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                  {source.title}
                </span>
                <span className="inline-flex shrink-0 items-center rounded px-1.5 py-0.5 text-[10px] font-medium"
                  style={{ backgroundColor: style.bg, color: style.text }}>
                  {style.label}
                </span>
              </button>

              {isExpanded && (
                <div className="space-y-2 px-3 pb-2.5 pt-0"
                  style={{ borderTop: "1px solid var(--theme-glass-border)" }}>
                  {source.snippet && (
                    <p className="pt-2 text-xs leading-relaxed" style={{ color: "var(--theme-text-muted)" }}>
                      {source.snippet}
                    </p>
                  )}
                  <div className="flex flex-wrap items-center gap-2 text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
                    {source.sourceType && (
                      <span className="rounded px-1.5 py-0.5"
                        style={{ backgroundColor: "var(--theme-bg-elevated)" }}>
                        {source.sourceType}
                      </span>
                    )}
                    {typeof source.score === "number" && (
                      <span>Relevance {Math.round(source.score * 100)}%</span>
                    )}
                  </div>
                  {source.url && (
                    <a href={source.url} target="_blank" rel="noreferrer"
                      className="inline-flex items-center gap-1 text-xs hover:underline"
                      style={{ color: "var(--theme-accent)" }}>
                      <ExternalLink className="h-3 w-3" />
                      <span className="max-w-[250px] truncate">{source.url}</span>
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
