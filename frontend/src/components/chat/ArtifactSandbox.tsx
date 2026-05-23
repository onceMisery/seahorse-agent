import * as React from "react";
import { Code, Copy, Check, Maximize2, Minimize2 } from "lucide-react";
import type { ArtifactBlock } from "@/types";

interface ArtifactSandboxProps {
  artifact: ArtifactBlock;
}

function buildSrcDoc(artifact: ArtifactBlock): string {
  const { language, code } = artifact;

  switch (language) {
    case "html":
      return code;
    case "css":
      return `<!DOCTYPE html><html><head><style>${code}</style></head><body><p style="color:#94a3b8;font:14px sans-serif;">CSS Preview</p></body></html>`;
    case "javascript":
    case "js":
      return `<!DOCTYPE html><html><head><style>body{margin:0;font:14px/1.6 system-ui,sans-serif;background:#1e1e2e;color:#cdd6f4;}</style></head><body><pre id="output"></pre><script>
try {
  const out = document.getElementById("output");
  const log = console.log;
  console.log = (...args) => { log(...args); out.textContent += args.map(a => typeof a === "object" ? JSON.stringify(a, null, 2) : String(a)).join(" ") + "\\n"; };
  ${code}
} catch(e) { document.body.innerHTML = '<pre style="color:#f38ba8">' + e + '</pre>'; }
<\/script></body></html>`;
    case "tsx":
      return `<!DOCTYPE html><html><head><style>body{margin:0;font:14px/1.6 system-ui,sans-serif;background:#1e1e2e;color:#cdd6f4;padding:16px;}</style></head><body><div id="root"></div><script src="https://unpkg.com/@babel/standalone/babel.min.js"><\/script><script type="text/babel">${code}<\/script></body></html>`;
    case "vue":
      return `<!DOCTYPE html><html><head><style>body{margin:0;font:14px/1.6 system-ui,sans-serif;background:#1e1e2e;color:#cdd6f4;padding:16px;}</style></head><body><div id="app"></div><script src="https://unpkg.com/vue@3/dist/vue.global.js"><\/script><script>${code}<\/script></body></html>`;
    default:
      return `<!DOCTYPE html><html><body><pre>${code}</pre></body></html>`;
  }
}

export function ArtifactSandbox({ artifact }: ArtifactSandboxProps) {
  const [copied, setCopied] = React.useState(false);
  const [expanded, setExpanded] = React.useState(false);

  const srcDoc = React.useMemo(
    () => buildSrcDoc(artifact),
    [artifact.id, artifact.code, artifact.isComplete]
  );

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(artifact.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // ignore
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
      {/* 工具栏 */}
      <div
        className="flex items-center justify-between px-3 py-1.5"
        style={{
          borderBottom: "1px solid var(--theme-glass-border)",
          backgroundColor: "var(--theme-bg-surface)",
        }}
      >
        <div className="flex items-center gap-2">
          <Code className="h-3.5 w-3.5" style={{ color: "var(--theme-accent)" }} />
          <span
            className="text-[11px] font-mono font-semibold uppercase tracking-wider"
            style={{ color: "var(--theme-text-muted)" }}
          >
            {artifact.language}
          </span>
          <span
            className="text-xs"
            style={{ color: "var(--theme-text-secondary)" }}
          >
            {artifact.title}
          </span>
          {!artifact.isComplete && (
            <span
              className="inline-block h-2 w-2 rounded-full animate-pulse"
              style={{ backgroundColor: "var(--theme-accent)" }}
            />
          )}
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={handleCopy}
            className="h-7 w-7 flex items-center justify-center rounded transition-colors"
            style={{ color: "var(--theme-text-muted)" }}
            aria-label="复制代码"
          >
            {copied ? <Check className="h-3.5 w-3.5 text-green-400" /> : <Copy className="h-3.5 w-3.5" />}
          </button>
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            className="h-7 w-7 flex items-center justify-center rounded transition-colors"
            style={{ color: "var(--theme-text-muted)" }}
            aria-label={expanded ? "收起预览" : "展开预览"}
          >
            {expanded ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
          </button>
        </div>
      </div>

      {/* iframe 沙箱 */}
      <iframe
        srcDoc={srcDoc}
        sandbox="allow-scripts"
        className="w-full border-0"
        style={{
          height: expanded ? "500px" : "300px",
          transition: "height 0.2s ease",
        }}
        title={`${artifact.language}: ${artifact.title}`}
      />
    </div>
  );
}
