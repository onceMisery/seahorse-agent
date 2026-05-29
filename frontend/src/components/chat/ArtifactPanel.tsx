import * as React from "react";
import { Check, Copy, Download, FileText, Maximize2, Minimize2, X } from "lucide-react";
import * as Tabs from "@radix-ui/react-tabs";
import type { AgentArtifact, ArtifactBlock } from "@/types";
import { AGENT_ARTIFACT_DISPOSITION } from "@/types";

interface ArtifactPanelProps {
  artifacts: ArtifactBlock[];
  serverArtifacts?: AgentArtifact[];
  onClose?: () => void;
}

const SAFE_PREVIEW_TYPES = new Set(["text/markdown", "text/plain", "image/png", "image/jpeg", "image/gif", "image/webp"]);

function canPreviewInline(artifact: AgentArtifact): boolean {
  if (artifact.disposition === AGENT_ARTIFACT_DISPOSITION.ATTACHMENT_DOWNLOAD) return false;
  if (artifact.mimeType && SAFE_PREVIEW_TYPES.has(artifact.mimeType)) return true;
  return artifact.canPreview === true;
}

export function ArtifactPanel({ artifacts, serverArtifacts, onClose }: ArtifactPanelProps) {
  const [activeTab, setActiveTab] = React.useState<string>(artifacts[0]?.id ?? "");
  const [copied, setCopied] = React.useState(false);
  const [fullscreen, setFullscreen] = React.useState(false);

  const allItems = React.useMemo(() => {
    const items: { id: string; title: string; code?: string; server?: AgentArtifact; isComplete: boolean }[] = [];
    for (const artifact of artifacts) {
      items.push({
        id: artifact.id,
        title: artifact.title,
        code: artifact.code,
        isComplete: artifact.isComplete
      });
    }
    for (const serverArtifact of serverArtifacts ?? []) {
      if (!items.some((item) => item.id === serverArtifact.artifactId)) {
        items.push({
          id: serverArtifact.artifactId,
          title: serverArtifact.title ?? "Artifact",
          code: serverArtifact.previewText ?? undefined,
          server: serverArtifact,
          isComplete: true
        });
      }
    }
    return items;
  }, [artifacts, serverArtifacts]);

  React.useEffect(() => {
    if (!allItems.some((item) => item.id === activeTab) && allItems.length > 0) {
      setActiveTab(allItems[0].id);
    }
  }, [allItems, activeTab]);

  if (allItems.length === 0) return null;

  const active = allItems.find((item) => item.id === activeTab) ?? allItems[0];
  const actionsEnabled = Boolean(active?.isComplete && active?.code);

  const handleCopy = async () => {
    if (!actionsEnabled || !active?.code) return;
    try {
      await navigator.clipboard.writeText(active.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard can be unavailable in restricted browser contexts.
    }
  };

  const handleDownload = () => {
    if (!actionsEnabled || !active?.code) return;
    const blob = new Blob([active.code], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${active.title || "artifact"}.txt`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const panelClass = fullscreen
    ? "fixed inset-0 z-50 flex flex-col"
    : "flex h-full flex-col overflow-hidden";

  return (
    <div className={panelClass} style={{ backgroundColor: "var(--theme-bg-elevated)" }}>
      <div
        className="flex shrink-0 items-center justify-between px-3 py-2"
        style={{ borderBottom: "1px solid var(--theme-glass-border)" }}
      >
        <div className="flex min-w-0 items-center gap-2">
          <FileText className="h-4 w-4 shrink-0" style={{ color: "var(--theme-accent)" }} />
          <span className="truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
            {active?.title ?? "Artifact"}
          </span>
          {!active?.isComplete && (
            <span
              className="inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[10px] animate-pulse"
              style={{ backgroundColor: "var(--theme-accent-muted)", color: "var(--theme-accent)" }}
            >
              生成中
            </span>
          )}
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handleCopy}
            disabled={!actionsEnabled}
            className="rounded p-1.5 transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
            title="复制"
          >
            {copied ? (
              <Check className="h-3.5 w-3.5" style={{ color: "var(--theme-success)" }} />
            ) : (
              <Copy className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
            )}
          </button>
          <button
            onClick={handleDownload}
            disabled={!actionsEnabled}
            className="rounded p-1.5 transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
            title="下载"
          >
            <Download className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
          </button>
          <button
            onClick={() => setFullscreen(!fullscreen)}
            className="rounded p-1.5 transition-colors hover:bg-white/10"
            title="全屏"
          >
            {fullscreen ? (
              <Minimize2 className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
            ) : (
              <Maximize2 className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
            )}
          </button>
          {onClose && (
            <button onClick={onClose} className="rounded p-1.5 transition-colors hover:bg-white/10" title="关闭">
              <X className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
            </button>
          )}
        </div>
      </div>

      {allItems.length > 1 && (
        <Tabs.Root value={activeTab} onValueChange={setActiveTab}>
          <Tabs.List
            className="flex shrink-0 gap-1 overflow-x-auto px-3 py-1.5"
            style={{ borderBottom: "1px solid var(--theme-glass-border)" }}
          >
            {allItems.slice(0, 5).map((item) => (
              <Tabs.Trigger
                key={item.id}
                value={item.id}
                className="whitespace-nowrap rounded px-2.5 py-1 text-xs transition-colors data-[state=active]:font-medium"
                style={{
                  color: activeTab === item.id ? "var(--theme-accent)" : "var(--theme-text-muted)",
                  backgroundColor: activeTab === item.id ? "var(--theme-accent-muted)" : "transparent"
                }}
              >
                {item.title}
              </Tabs.Trigger>
            ))}
            {allItems.length > 5 && (
              <span className="px-2 py-1 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                +{allItems.length - 5} more
              </span>
            )}
          </Tabs.List>
        </Tabs.Root>
      )}

      <div className="min-h-0 flex-1 overflow-auto p-4">
        {active?.server && canPreviewInline(active.server) && active.server.mimeType?.startsWith("image/") ? (
          <img src={active.server.storageRef ?? ""} alt={active.title} className="h-auto max-w-full rounded" />
        ) : (
          <pre
            className="whitespace-pre-wrap break-words font-mono text-sm leading-relaxed"
            style={{ color: "var(--theme-text-primary)" }}
          >
            <code>{active?.code || "(暂无内容)"}</code>
            {!active?.isComplete && (
              <span className="ml-0.5 inline-block animate-pulse" style={{ color: "var(--theme-accent)" }}>
                |
              </span>
            )}
          </pre>
        )}
      </div>
    </div>
  );
}
