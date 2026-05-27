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

/**
 * 独立产物面板，支持 Tab 切换、预览、复制、下载。
 */
export function ArtifactPanel({ artifacts, serverArtifacts, onClose }: ArtifactPanelProps) {
  const [activeTab, setActiveTab] = React.useState<string>(artifacts[0]?.id ?? "");
  const [copied, setCopied] = React.useState(false);
  const [fullscreen, setFullscreen] = React.useState(false);

  const allItems = React.useMemo(() => {
    const items: { id: string; title: string; code?: string; server?: AgentArtifact; isComplete: boolean }[] = [];
    for (const a of artifacts) {
      items.push({ id: a.id, title: a.title, code: a.code, isComplete: a.isComplete });
    }
    for (const sa of serverArtifacts ?? []) {
      if (!items.some((i) => i.id === sa.artifactId)) {
        items.push({
          id: sa.artifactId,
          title: sa.title ?? "Artifact",
          code: sa.previewText ?? undefined,
          server: sa,
          isComplete: true
        });
      }
    }
    return items;
  }, [artifacts, serverArtifacts]);

  React.useEffect(() => {
    if (!allItems.some((i) => i.id === activeTab) && allItems.length > 0) {
      setActiveTab(allItems[0].id);
    }
  }, [allItems, activeTab]);

  if (allItems.length === 0) return null;

  const active = allItems.find((i) => i.id === activeTab) ?? allItems[0];

  const handleCopy = async () => {
    if (!active?.code) return;
    try {
      await navigator.clipboard.writeText(active.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch { /* clipboard unavailable */ }
  };

  const handleDownload = () => {
    if (!active?.code) return;
    const blob = new Blob([active.code], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${active.title || "artifact"}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const panelClass = fullscreen
    ? "fixed inset-0 z-50 flex flex-col"
    : "flex h-full flex-col overflow-hidden";

  return (
    <div className={panelClass} style={{ backgroundColor: "var(--theme-bg-elevated)" }}>
      {/* 顶部工具栏 */}
      <div
        className="flex items-center justify-between px-3 py-2 shrink-0"
        style={{ borderBottom: "1px solid var(--theme-glass-border)" }}
      >
        <div className="flex items-center gap-2 min-w-0">
          <FileText className="h-4 w-4 shrink-0" style={{ color: "var(--theme-accent)" }} />
          <span className="text-sm font-medium truncate" style={{ color: "var(--theme-text-primary)" }}>
            {active?.title ?? "Artifact"}
          </span>
          {!active?.isComplete && (
            <span className="inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded-full animate-pulse"
              style={{ backgroundColor: "var(--theme-accent-muted)", color: "var(--theme-accent)" }}>
              streaming...
            </span>
          )}
        </div>
        <div className="flex items-center gap-1">
          <button onClick={handleCopy} className="p-1.5 rounded hover:bg-white/10 transition-colors"
            title="复制">
            {copied ? <Check className="h-3.5 w-3.5" style={{ color: "var(--theme-success)" }} />
              : <Copy className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />}
          </button>
          <button onClick={handleDownload} className="p-1.5 rounded hover:bg-white/10 transition-colors"
            title="下载">
            <Download className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
          </button>
          <button onClick={() => setFullscreen(!fullscreen)}
            className="p-1.5 rounded hover:bg-white/10 transition-colors" title="全屏">
            {fullscreen
              ? <Minimize2 className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
              : <Maximize2 className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />}
          </button>
          {onClose && (
            <button onClick={onClose} className="p-1.5 rounded hover:bg-white/10 transition-colors"
              title="关闭">
              <X className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
            </button>
          )}
        </div>
      </div>

      {/* Tab 切换（多产物时显示） */}
      {allItems.length > 1 && (
        <Tabs.Root value={activeTab} onValueChange={setActiveTab}>
          <Tabs.List className="flex gap-1 px-3 py-1.5 overflow-x-auto shrink-0"
            style={{ borderBottom: "1px solid var(--theme-glass-border)" }}>
            {allItems.slice(0, 5).map((item) => (
              <Tabs.Trigger key={item.id} value={item.id}
                className="px-2.5 py-1 text-xs rounded transition-colors whitespace-nowrap data-[state=active]:font-medium"
                style={{
                  color: activeTab === item.id ? "var(--theme-accent)" : "var(--theme-text-muted)",
                  backgroundColor: activeTab === item.id ? "var(--theme-accent-muted)" : "transparent"
                }}>
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

      {/* 内容预览区 */}
      <div className="flex-1 min-h-0 overflow-auto p-4">
        {active?.server && canPreviewInline(active.server) && active.server.mimeType?.startsWith("image/") ? (
          <img src={active.server.storageRef ?? ""} alt={active.title}
            className="max-w-full h-auto rounded" />
        ) : (
          <pre className="text-sm font-mono whitespace-pre-wrap break-words leading-relaxed"
            style={{ color: "var(--theme-text-primary)" }}>
            {active?.code ?? "(无内容)"}
          </pre>
        )}
      </div>
    </div>
  );
}
