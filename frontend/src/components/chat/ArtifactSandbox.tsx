import * as React from "react";
import { Check, Code, Copy, Download, Eye, Maximize2, Minimize2 } from "lucide-react";
import type { ArtifactBlock } from "@/types";

interface ArtifactSandboxProps {
  artifact: ArtifactBlock;
}

export function ArtifactSandbox({ artifact }: ArtifactSandboxProps) {
  const [copied, setCopied] = React.useState(false);
  const [expanded, setExpanded] = React.useState(false);
  const [showSource, setShowSource] = React.useState(false);
  const copyTimerRef = React.useRef<ReturnType<typeof setTimeout>>();
  const isHtml = artifact.language === "html";

  React.useEffect(() => () => clearTimeout(copyTimerRef.current), []);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(artifact.code);
      setCopied(true);
      copyTimerRef.current = setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard may be unavailable in restricted browser contexts.
    }
  };

  const handleDownload = () => {
    const extension = artifact.language === "markdown" ? "md" : artifact.language === "javascript" ? "js" : artifact.language;
    const mimeType = artifact.language === "html"
      ? "text/html;charset=utf-8"
      : artifact.language === "markdown"
        ? "text/markdown;charset=utf-8"
        : "text/plain;charset=utf-8";
    const blob = new Blob([artifact.code], { type: mimeType });
    const href = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = href;
    link.download = artifact.title || `artifact.${extension}`;
    link.click();
    URL.revokeObjectURL(href);
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
          {isHtml ? (
            <button
              type="button"
              onClick={() => setShowSource((value) => !value)}
              className="flex h-7 w-7 items-center justify-center rounded transition-colors"
              style={{ color: "var(--theme-text-muted)" }}
              aria-label={showSource ? "预览 HTML" : "查看 HTML 源码"}
              title={showSource ? "预览" : "源码"}
            >
              {showSource ? <Eye className="h-3.5 w-3.5" /> : <Code className="h-3.5 w-3.5" />}
            </button>
          ) : null}
          <button
            type="button"
            onClick={handleCopy}
            className="flex h-7 w-7 items-center justify-center rounded transition-colors"
            style={{ color: "var(--theme-text-muted)" }}
            aria-label="复制产物代码"
            title="复制"
          >
            {copied ? <Check className="h-3.5 w-3.5 text-green-400" /> : <Copy className="h-3.5 w-3.5" />}
          </button>
          <button
            type="button"
            onClick={handleDownload}
            className="flex h-7 w-7 items-center justify-center rounded transition-colors"
            style={{ color: "var(--theme-text-muted)" }}
            aria-label="下载产物"
            title="下载"
          >
            <Download className="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            onClick={() => setExpanded((value) => !value)}
            className="flex h-7 w-7 items-center justify-center rounded transition-colors"
            style={{ color: "var(--theme-text-muted)" }}
            aria-label={expanded ? "收起产物预览" : "展开产物预览"}
            title={expanded ? "收起" : "展开"}
          >
            {expanded ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
          </button>
        </div>
      </div>

      {isHtml && !showSource ? (
        <iframe
          title={artifact.title}
          srcDoc={artifact.code}
          sandbox=""
          className="block w-full bg-white"
          style={{
            height: expanded ? "640px" : "360px",
            transition: "height 0.2s ease"
          }}
        />
      ) : (
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
      )}
    </div>
  );
}
