import * as React from "react";
import { Check, Code, Copy, RefreshCw } from "lucide-react";
import mermaid from "mermaid";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";

interface MermaidDiagramProps {
  code: string;
}

let mermaidInitialized = false;

function ensureMermaidInitialized() {
  if (mermaidInitialized) return;
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: "strict",
    theme: "neutral",
    flowchart: {
      htmlLabels: false
    }
  });
  mermaidInitialized = true;
}

export function MermaidDiagram({ code }: MermaidDiagramProps) {
  const [svg, setSvg] = React.useState("");
  const [error, setError] = React.useState("");
  const [showSource, setShowSource] = React.useState(false);
  const [copied, setCopied] = React.useState(false);
  const copyTimerRef = React.useRef<ReturnType<typeof setTimeout>>();
  const diagramId = React.useId().replace(/:/g, "");

  React.useEffect(() => () => clearTimeout(copyTimerRef.current), []);

  React.useEffect(() => {
    let cancelled = false;
    const render = async () => {
      ensureMermaidInitialized();
      setError("");
      try {
        const result = await mermaid.render(`mermaid-${diagramId}`, code);
        if (!cancelled) {
          setSvg(result.svg);
        }
      } catch (err) {
        if (!cancelled) {
          setSvg("");
          setError(err instanceof Error ? err.message : "Mermaid 渲染失败");
        }
      }
    };
    render();
    return () => {
      cancelled = true;
    };
  }, [code, diagramId]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      copyTimerRef.current = setTimeout(() => setCopied(false), 1500);
    } catch {
      toast.error("复制失败");
    }
  };

  return (
    <div
      className="my-3 overflow-hidden rounded-lg"
      style={{
        border: "1px solid var(--theme-glass-border)",
        backgroundColor: "var(--theme-bg-elevated)"
      }}
    >
      <div
        className="flex items-center justify-between px-3 py-2"
        style={{ borderBottom: "1px solid var(--theme-glass-border)" }}
      >
        <div className="flex items-center gap-2 text-xs" style={{ color: "var(--theme-text-secondary)" }}>
          <RefreshCw className="h-3.5 w-3.5" style={{ color: "var(--theme-accent)" }} />
          Mermaid
        </div>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={() => setShowSource((current) => !current)}
            aria-label={showSource ? "预览图表" : "查看源码"}
          >
            <Code className="h-3.5 w-3.5" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={handleCopy}
            aria-label="复制 Mermaid"
          >
            {copied ? <Check className="h-3.5 w-3.5 text-green-400" /> : <Copy className="h-3.5 w-3.5" />}
          </Button>
        </div>
      </div>

      {error ? (
        <div className="space-y-2 px-4 py-3">
          <p className="text-xs text-rose-400">Mermaid 图表渲染失败，已保留源码。</p>
          <pre className="overflow-auto whitespace-pre-wrap text-xs" style={{ color: "var(--theme-text-primary)" }}>
            {code}
          </pre>
        </div>
      ) : showSource ? (
        <pre className="m-0 overflow-auto whitespace-pre-wrap px-4 py-3 text-xs" style={{ color: "var(--theme-text-primary)" }}>
          {code}
        </pre>
      ) : (
        <div
          className="overflow-auto px-4 py-4"
          style={{ backgroundColor: "var(--theme-bg-surface)" }}
          dangerouslySetInnerHTML={{ __html: svg }}
        />
      )}
    </div>
  );
}
