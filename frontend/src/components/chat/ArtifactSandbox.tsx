import * as React from "react";
import { Check, Code, Copy, Maximize2, Minimize2 } from "lucide-react";
import type { ArtifactBlock } from "@/types";

interface ArtifactSandboxProps {
  artifact: ArtifactBlock;
}

export function ArtifactSandbox({ artifact }: ArtifactSandboxProps) {
  const [copied, setCopied] = React.useState(false);
  const [expanded, setExpanded] = React.useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(artifact.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard may be unavailable in restricted browser contexts.
    }
  };

  return (
    <div
      className="my-3 overflow-hidden rounded-lg transition-all duration-200"
      style={{
        border: "1px solid var(--theme-glass-border)",
        backgroundColor: "var(--theme-bg-elevated)",
      }}
    >
      <div
        className="flex items-center justify-between px-3 py-1.5"
        style={{
          borderBottom: "1px solid var(--theme-glass-border)",
          backgroundColor: "var(--theme-bg-surface)",
        }}
      >
        <div className="flex min-w-0 items-center gap-2">
          <Code className="h-3.5 w-3.5 shrink-0" style={{ color: "var(--theme-accent)" }} />
          <span
            className="shrink-0 text-[11px] font-mono font-semibold uppercase tracking-wider"
            style={{ color: "var(--theme-text-muted)" }}
          >
            {artifact.language}
          </span>
          <span className="truncate text-xs" style={{ color: "var(--theme-text-secondary)" }}>
            {artifact.title}
          </span>
          {!artifact.isComplete && (
            <span
              className="inline-block h-2 w-2 shrink-0 animate-pulse rounded-full"
              style={{ backgroundColor: "var(--theme-accent)" }}
            />
          )}
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={handleCopy}
            className="flex h-7 w-7 items-center justify-center rounded transition-colors"
            style={{ color: "var(--theme-text-muted)" }}
            aria-label="Copy artifact code"
            title="Copy"
          >
            {copied ? <Check className="h-3.5 w-3.5 text-green-400" /> : <Copy className="h-3.5 w-3.5" />}
          </button>
          <button
            type="button"
            onClick={() => setExpanded((value) => !value)}
            className="flex h-7 w-7 items-center justify-center rounded transition-colors"
            style={{ color: "var(--theme-text-muted)" }}
            aria-label={expanded ? "Collapse artifact preview" : "Expand artifact preview"}
            title={expanded ? "Collapse" : "Expand"}
          >
            {expanded ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
          </button>
        </div>
      </div>

      <pre
        className="m-0 w-full overflow-auto px-4 py-3 text-xs leading-relaxed"
        style={{
          maxHeight: expanded ? "560px" : "280px",
          color: "var(--theme-text-primary)",
          backgroundColor: "var(--theme-bg-elevated)",
          transition: "max-height 0.2s ease",
        }}
      >
        <code>{artifact.code}</code>
      </pre>
    </div>
  );
}
