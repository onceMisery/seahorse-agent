import { ExternalLink, FileText } from "lucide-react";

import type { AgentSource } from "@/types";

interface SourceListProps {
  sources: AgentSource[];
}

export function SourceList({ sources }: SourceListProps) {
  if (sources.length === 0) return null;

  return (
    <div className="space-y-2">
      <div className="text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
        Sources
      </div>
      <div className="grid gap-2 sm:grid-cols-2">
        {sources.map((source) => (
          <a
            key={source.id}
            href={source.url || undefined}
            target={source.url ? "_blank" : undefined}
            rel={source.url ? "noreferrer" : undefined}
            className="group rounded-lg px-3 py-2 transition-colors"
            style={{
              border: "1px solid var(--theme-glass-border)",
              backgroundColor: "var(--theme-bg-surface)"
            }}
          >
            <div className="flex min-w-0 items-center gap-2">
              <FileText className="h-3.5 w-3.5 shrink-0" style={{ color: "var(--theme-accent)" }} />
              <span className="truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                {source.title}
              </span>
              {source.url ? (
                <ExternalLink className="h-3 w-3 shrink-0 opacity-60" style={{ color: "var(--theme-text-muted)" }} />
              ) : null}
            </div>
            {source.snippet ? (
              <p className="mt-1 line-clamp-2 text-xs leading-relaxed" style={{ color: "var(--theme-text-muted)" }}>
                {source.snippet}
              </p>
            ) : null}
            <div className="mt-2 flex items-center gap-2 text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
              {source.sourceType ? <span>{source.sourceType}</span> : null}
              {typeof source.score === "number" ? <span>{Math.round(source.score * 100)}%</span> : null}
            </div>
          </a>
        ))}
      </div>
    </div>
  );
}
