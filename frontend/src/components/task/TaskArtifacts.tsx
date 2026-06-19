import * as React from "react";
import { Copy, Check, Download, FileText, GitBranch, Paperclip } from "lucide-react";
import { toast } from "sonner";

import { MermaidDiagram } from "@/components/chat/MermaidDiagram";
import type { TaskArtifact } from "@/types/task";

interface TaskArtifactsProps {
  artifacts: TaskArtifact[];
}

/** 从 markdown 正文中提取首个 mermaid 代码块（若整体即一张图）。 */
function extractMermaid(content: string | null): string | null {
  if (!content) return null;
  const match = content.match(/```mermaid\s*([\s\S]*?)```/);
  return match ? match[1].trim() : null;
}

function ArtifactCard({ artifact }: { artifact: TaskArtifact }) {
  const [copied, setCopied] = React.useState(false);

  const mermaidCode =
    artifact.type === "mermaid" ? artifact.content : extractMermaid(artifact.content);

  const handleCopy = async () => {
    if (!artifact.content) return;
    try {
      await navigator.clipboard.writeText(artifact.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      toast.error("复制失败");
    }
  };

  const Icon =
    artifact.type === "mermaid" ? GitBranch : artifact.type === "file" || artifact.type === "image" ? Paperclip : FileText;

  return (
    <div
      className="overflow-hidden rounded-xl"
      style={{
        backgroundColor: "var(--theme-glass-bg)",
        border: "1px solid var(--theme-glass-border)"
      }}
    >
      <div
        className="flex items-center justify-between px-4 py-2.5"
        style={{ borderBottom: "1px solid var(--theme-glass-border)" }}
      >
        <div className="flex min-w-0 items-center gap-2">
          <Icon className="h-4 w-4 shrink-0" style={{ color: "var(--theme-accent)" }} />
          <span className="truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
            {artifact.title}
          </span>
          <span
            className="shrink-0 rounded px-1.5 py-0.5 text-[10px] uppercase"
            style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-muted)" }}
          >
            {artifact.type}
          </span>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {artifact.content && (
            <button
              type="button"
              className="rounded p-1.5 transition-colors hover:bg-[var(--theme-bg-elevated)]"
              onClick={handleCopy}
              aria-label="复制"
            >
              {copied ? <Check className="h-3.5 w-3.5 text-green-400" /> : <Copy className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />}
            </button>
          )}
          <a
            href={artifact.downloadUrl}
            className="rounded p-1.5 transition-colors hover:bg-[var(--theme-bg-elevated)]"
            aria-label="下载"
            target="_blank"
            rel="noreferrer"
          >
            <Download className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
          </a>
        </div>
      </div>

      <div className="px-4 py-3">
        {mermaidCode ? (
          <MermaidDiagram code={mermaidCode} />
        ) : artifact.canPreview && artifact.content ? (
          <pre
            className="m-0 overflow-auto whitespace-pre-wrap text-xs leading-relaxed"
            style={{ color: "var(--theme-text-primary)" }}
          >
            {artifact.content}
          </pre>
        ) : (
          <p className="text-xs" style={{ color: "var(--theme-text-muted)" }}>
            该产物为附件类型，请点击下载查看。
          </p>
        )}
      </div>
    </div>
  );
}

export function TaskArtifacts({ artifacts }: TaskArtifactsProps) {
  if (artifacts.length === 0) {
    return null;
  }
  return (
    <div className="space-y-3">
      {artifacts.map((a) => (
        <ArtifactCard key={a.artifactId} artifact={a} />
      ))}
    </div>
  );
}
