import * as React from "react";
import { Check, Copy, Download, Edit3, FileText, Maximize2, Minimize2, Save } from "lucide-react";
import { toast } from "sonner";

import { CodeEditor } from "@/components/ai-elements/renderer/CodeEditor";
import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import { A2UILiteRenderer } from "@/components/a2ui-lite/A2UILiteRenderer";
import type { A2UILiteAction, A2UILiteSurface } from "@/components/a2ui-lite/a2uiTypes";
import { downloadAgentArtifact, updateAgentArtifact } from "@/services/agentArtifactService";
import { AGENT_ARTIFACT_SCAN_STATUS, type AgentArtifact, type ArtifactBlock } from "@/types";

interface ArtifactInspectorTabProps {
  artifacts: ArtifactBlock[];
  serverArtifacts?: AgentArtifact[];
  onClose?: () => void;
}

interface MergedItem {
  id: string;
  title: string;
  code?: string;
  server?: AgentArtifact;
  isComplete: boolean;
  language?: string;
  mimeType?: string | null;
}

function mergeArtifacts(artifacts: ArtifactBlock[], serverArtifacts: AgentArtifact[]): MergedItem[] {
  const items: MergedItem[] = [];
  for (const a of artifacts) {
    const server = serverArtifacts.find((item) => item.artifactId === a.id);
    items.push({
      id: a.id,
      title: a.title,
      code: server?.previewText ?? a.code,
      server,
      isComplete: a.isComplete,
      language: a.language,
      mimeType: server?.mimeType
    });
  }
  for (const sa of serverArtifacts) {
    if (!items.some((i) => i.id === sa.artifactId)) {
      items.push({
        id: sa.artifactId,
        title: sa.title ?? "Artifact",
        code: sa.previewText ?? undefined,
        server: sa,
        isComplete: true,
        mimeType: sa.mimeType
      });
    }
  }
  return items;
}

export function ArtifactInspectorTab({ artifacts, serverArtifacts = [], onClose: _onClose }: ArtifactInspectorTabProps) {
  const [liveServerArtifacts, setLiveServerArtifacts] = React.useState<AgentArtifact[]>(serverArtifacts);
  const [selectedId, setSelectedId] = React.useState<string>("");
  const [copied, setCopied] = React.useState(false);
  const [fullscreen, setFullscreen] = React.useState(false);
  const [editing, setEditing] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [editedContentById, setEditedContentById] = React.useState<Record<string, string>>({});
  const [a2uiParseError, setA2uiParseError] = React.useState<string | null>(null);
  const copyTimerRef = React.useRef<ReturnType<typeof setTimeout>>();

  React.useEffect(() => {
    setLiveServerArtifacts(serverArtifacts);
  }, [serverArtifacts]);

  const allItems = React.useMemo(() => mergeArtifacts(artifacts, liveServerArtifacts), [artifacts, liveServerArtifacts]);

  React.useEffect(() => () => clearTimeout(copyTimerRef.current), []);

  React.useEffect(() => {
    if (allItems.length > 0 && (!selectedId || !allItems.some((i) => i.id === selectedId))) {
      setSelectedId(allItems[0].id);
    }
  }, [allItems, selectedId]);

  const active = allItems.find((i) => i.id === selectedId) ?? allItems[0] ?? null;
  const activeServerArtifact = React.useMemo(
    () => active?.server ?? liveServerArtifacts.find((item) => item.artifactId === active?.id),
    [active, liveServerArtifacts]
  );
  const activeCode = active ? editedContentById[active.id] ?? active.code ?? "" : "";
  const isClean = !activeServerArtifact || activeServerArtifact.scanStatus === AGENT_ARTIFACT_SCAN_STATUS.CLEAN;
  const isHtml = active?.language === "html" || active?.mimeType === "text/html";

  const a2uiSurface = React.useMemo<A2UILiteSurface | null>(() => {
    if (!active) return null;
    if (active.server?.mimeType === "application/vnd.seahorse.a2ui+json") {
      try {
        const parsed = JSON.parse(activeCode || (active.server.previewText ?? ""));
        if (parsed?.version === "seahorse-a2ui-lite/v1") return parsed as A2UILiteSurface;
      } catch {
        return null;
      }
    }
    if (active.language === "json" && activeCode) {
      try {
        const parsed = JSON.parse(activeCode);
        if (parsed?.version === "seahorse-a2ui-lite/v1") return parsed as A2UILiteSurface;
      } catch {
        return null;
      }
    }
    return null;
  }, [active, activeCode]);

  React.useEffect(() => {
    if (active?.language === "json" && activeCode && !a2uiSurface) {
      try {
        JSON.parse(activeCode);
        setA2uiParseError(null);
      } catch (err) {
        setA2uiParseError((err as Error).message);
      }
    } else {
      setA2uiParseError(null);
    }
  }, [active, activeCode, a2uiSurface]);

  if (allItems.length === 0 || !active) return <InspectorEmptyState />;

  const handleCopy = async () => {
    if (!activeCode) return;
    try {
      await navigator.clipboard.writeText(activeCode);
      setCopied(true);
      copyTimerRef.current = setTimeout(() => setCopied(false), 1500);
    } catch {
      toast.error("Copy failed");
    }
  };

  const handleDownload = async () => {
    if (!active || !isClean) return;
    if (active.server) {
      try {
        const blob = (await downloadAgentArtifact(active.server.artifactId)) as unknown as Blob;
        const href = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = href;
        link.download = active.title;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(href);
      } catch {
        toast.error("Download failed");
      }
      return;
    }
    if (!activeCode) return;
    const blob = new Blob([activeCode], { type: "text/plain;charset=utf-8" });
    const href = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = href;
    link.download = `${active.title || "artifact"}.txt`;
    link.click();
    URL.revokeObjectURL(href);
  };

  const handleSave = async () => {
    if (!active) return;
    if (!activeServerArtifact) {
      setEditing(false);
      toast.success("Saved locally");
      return;
    }
    setSaving(true);
    try {
      const updated = await updateAgentArtifact(activeServerArtifact.artifactId, activeCode);
      setLiveServerArtifacts((current) => {
        const next = current.filter((item) => item.artifactId !== updated.artifactId);
        next.push({
          ...activeServerArtifact,
          ...updated,
          artifactId: updated.artifactId ?? activeServerArtifact.artifactId
        });
        return next;
      });
      setEditedContentById((current) => ({
        ...current,
        [active.id]: updated.previewText ?? activeCode
      }));
      setEditing(false);
      toast.success("Artifact saved");
    } catch {
      toast.error("Save failed");
    } finally {
      setSaving(false);
    }
  };

  const containerClass = fullscreen
    ? "fixed inset-0 z-50 flex flex-col"
    : "flex h-full flex-col";

  return (
    <div
      className={containerClass}
      style={{ background: "var(--sh-workbench-panel)" }}
    >
      <div className="flex min-h-0 flex-1">
        {allItems.length > 1 && (
          <div
            className="flex w-40 shrink-0 flex-col overflow-y-auto"
            style={{ borderRight: "1px solid var(--sh-workbench-border)" }}
          >
            {allItems.map((item) => {
              const isActive = item.id === active.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setSelectedId(item.id)}
                  className="flex flex-col gap-0.5 px-3 py-2.5 text-left transition-colors"
                  style={{
                    backgroundColor: isActive ? "var(--sh-workbench-accent-soft)" : "transparent",
                    borderLeft: isActive ? "2px solid var(--sh-workbench-accent)" : "2px solid transparent"
                  }}
                >
                  <span
                    className="truncate text-xs font-medium"
                    style={{ color: isActive ? "var(--sh-workbench-accent)" : "var(--theme-text-primary)" }}
                  >
                    {item.title}
                  </span>
                  {item.language && (
                    <span className="text-[10px] uppercase" style={{ color: "var(--theme-text-muted)" }}>
                      {item.language}
                    </span>
                  )}
                  {!item.isComplete && (
                    <span className="text-[10px] animate-pulse" style={{ color: "var(--sh-workbench-accent)" }}>
                      streaming
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <div
            className="flex shrink-0 items-center justify-between px-3 py-2"
            style={{ borderBottom: "1px solid var(--sh-workbench-border)" }}
          >
            <div className="flex min-w-0 items-center gap-2">
              <FileText className="h-3.5 w-3.5 shrink-0" style={{ color: "var(--sh-workbench-accent)" }} />
              <span className="truncate text-xs font-medium" style={{ color: "var(--theme-text-primary)" }}>
                {active.title}
              </span>
              {!active.isComplete && (
                <span
                  className="animate-pulse rounded-full px-1.5 py-0.5 text-[10px]"
                  style={{
                    backgroundColor: "var(--sh-workbench-accent-soft)",
                    color: "var(--sh-workbench-accent)"
                  }}
                >
                  streaming
                </span>
              )}
            </div>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={handleCopy}
                aria-label="复制内容"
                className="flex h-6 w-6 items-center justify-center rounded transition-colors hover:bg-white/10"
              >
                {copied
                  ? <Check className="h-3.5 w-3.5" style={{ color: "#22c55e" }} />
                  : <Copy className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />}
              </button>
              <button
                type="button"
                onClick={() => setEditing((current) => !current)}
                aria-label={editing ? "Preview content" : "Edit content"}
                className="flex h-6 w-6 items-center justify-center rounded transition-colors hover:bg-white/10"
              >
                <Edit3
                  className="h-3.5 w-3.5"
                  style={{ color: editing ? "var(--sh-workbench-accent)" : "var(--theme-text-muted)" }}
                />
              </button>
              {editing && (
                <button
                  type="button"
                  onClick={handleSave}
                  disabled={saving}
                  aria-label="Save content"
                  className="flex h-6 w-6 items-center justify-center rounded transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Save className="h-3.5 w-3.5" style={{ color: "var(--sh-workbench-accent)" }} />
                </button>
              )}
              <button
                type="button"
                onClick={handleDownload}
                disabled={!isClean}
                aria-label="下载"
                title={isClean ? "下载" : "文件未通过安全扫描"}
                className="flex h-6 w-6 items-center justify-center rounded transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
              >
                <Download className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
              </button>
              <button
                type="button"
                onClick={() => setFullscreen(!fullscreen)}
                aria-label={fullscreen ? "退出全屏" : "全屏"}
                className="flex h-6 w-6 items-center justify-center rounded transition-colors hover:bg-white/10"
              >
                {fullscreen
                  ? <Minimize2 className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />
                  : <Maximize2 className="h-3.5 w-3.5" style={{ color: "var(--theme-text-muted)" }} />}
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-auto">
            {editing ? (
              <div className="p-4">
                <CodeEditor
                  value={activeCode}
                  onChange={(next) => setEditedContentById((current) => ({ ...current, [active.id]: next }))}
                  language={active.language ?? "text"}
                  minHeight="360px"
                  maxHeight="70vh"
                />
              </div>
            ) : a2uiSurface ? (
              <A2UILiteRenderer
                surface={a2uiSurface}
                onAction={(_action: A2UILiteAction) => undefined}
              />
            ) : isHtml ? (
              <iframe
                title={active.title}
                srcDoc={activeCode}
                sandbox=""
                className="h-full min-h-[520px] w-full bg-white"
              />
            ) : (
              <div className="p-4">
                {a2uiParseError && (
                  <p
                    className="mb-2 text-[11px]"
                    style={{ color: "rgb(239,68,68)" }}
                  >
                    JSON parse error: {a2uiParseError}
                  </p>
                )}
                <pre
                  className="text-xs font-mono whitespace-pre-wrap break-words leading-relaxed"
                  style={{ color: "var(--theme-text-primary)" }}
                >
                  {activeCode || "(No content)"}
                </pre>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
