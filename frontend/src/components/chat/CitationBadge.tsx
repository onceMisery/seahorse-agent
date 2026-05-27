import * as React from "react";
import { ExternalLink } from "lucide-react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger
} from "@/components/ui/tooltip";
import type { AgentSource } from "@/types";

interface CitationBadgeProps {
  index: number;
  source: AgentSource;
}

export function CitationBadge({ index, source }: CitationBadgeProps) {
  return (
    <TooltipProvider delayDuration={200}>
      <Tooltip>
        <TooltipTrigger asChild>
          <sup
            className="inline-flex items-center justify-center h-4 min-w-[16px] px-0.5 rounded
              text-[10px] font-bold cursor-pointer select-none align-super mx-[1px]
              transition-colors"
            style={{
              backgroundColor: "var(--theme-accent-muted)",
              color: "var(--theme-accent)"
            }}
          >
            {index}
          </sup>
        </TooltipTrigger>
        <TooltipContent
          side="top"
          className="max-w-[320px] p-3 space-y-1.5"
          style={{
            backgroundColor: "var(--theme-bg-elevated)",
            border: "1px solid var(--theme-glass-border)",
            color: "var(--theme-text-primary)"
          }}
        >
          <div className="text-sm font-medium leading-tight line-clamp-2">
            {source.title}
          </div>
          {source.snippet && (
            <p className="text-xs leading-relaxed line-clamp-3"
              style={{ color: "var(--theme-text-muted)" }}>
              {source.snippet}
            </p>
          )}
          {source.url && (
            <a
              href={source.url}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-[11px] hover:underline"
              style={{ color: "var(--theme-accent)" }}
              onClick={(e) => e.stopPropagation()}
            >
              <ExternalLink className="h-3 w-3" />
              <span className="truncate max-w-[200px]">{source.url}</span>
            </a>
          )}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
