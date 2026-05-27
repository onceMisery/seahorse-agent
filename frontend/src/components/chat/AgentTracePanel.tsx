import { Box, Clock3, Database, Download, Gauge, ShieldAlert } from "lucide-react";
import { toast } from "sonner";

import { ApprovalCard } from "@/components/chat/ApprovalCard";
import { SourceList } from "@/components/chat/SourceList";
import { Button } from "@/components/ui/button";
import { downloadAgentArtifact } from "@/services/agentArtifactService";
import { AGENT_ARTIFACT_SCAN_STATUS, type AgentArtifact, type Message } from "@/types";

/** 研究步骤中文标签映射 */
const RESEARCH_STEP_LABELS: Record<string, string> = {
  PLAN: "规划研究方向",
  SEARCH: "搜索相关资料",
  FETCH: "抓取网页内容",
  EXTRACT_EVIDENCE: "提取关键证据",
  SYNTHESIZE: "综合分析",
  WRITE_REPORT: "撰写报告",
  VERIFY_CITATIONS: "验证引用"
};

function localizeStepTitle(title: string): string {
  const upper = title.toUpperCase().replace(/\s+/g, "_");
  return RESEARCH_STEP_LABELS[upper] ?? RESEARCH_STEP_LABELS[title] ?? title;
}

interface AgentTracePanelProps {
  message: Message;
}

function formatDuration(durationMs?: number) {
  if (typeof durationMs !== "number") return "";
  if (durationMs < 1000) return `${Math.round(durationMs)}ms`;
  return `${(durationMs / 1000).toFixed(1)}s`;
}

function formatNumber(value?: number) {
  if (typeof value !== "number" || !Number.isFinite(value)) return "0";
  return value.toLocaleString();
}

function formatCost(value?: number) {
  if (typeof value !== "number" || !Number.isFinite(value)) return "$0.0000";
  return `$${value.toFixed(4)}`;
}

function artifactTitle(artifact: AgentArtifact) {
  return artifact.title?.trim() || artifact.artifactId;
}

function artifactMeta(artifact: AgentArtifact) {
  return [artifact.artifactType, artifact.mimeType, artifact.scanStatus].filter(Boolean).join(" / ");
}

async function downloadArtifact(artifact: AgentArtifact) {
  try {
    const blob = await downloadAgentArtifact(artifact.artifactId);
    const href = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = href;
    link.download = artifactTitle(artifact);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(href);
  } catch {
    toast.error("Artifact download failed");
  }
}

export function AgentTracePanel({ message }: AgentTracePanelProps) {
  const timeline = message.timeline ?? [];
  const sources = message.sources ?? [];
  const artifacts = message.artifacts ?? [];
  const serverArtifacts = message.serverArtifacts ?? [];
  const approvals = message.approvals ?? [];
  const quota = message.quota ?? [];
  const memories = message.memories ?? [];
  const costSummary = message.costSummary;
  const hasTrace =
    timeline.length > 0 ||
    sources.length > 0 ||
    artifacts.length > 0 ||
    serverArtifacts.length > 0 ||
    approvals.length > 0 ||
    quota.length > 0 ||
    memories.length > 0 ||
    Boolean(costSummary);

  if (!hasTrace) return null;

  return (
    <div
      className="mt-3 space-y-4 rounded-lg p-3"
      style={{
        border: "1px solid var(--theme-glass-border)",
        backgroundColor: "var(--theme-bg-elevated)"
      }}
    >
      {timeline.length > 0 ? (
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
            <Clock3 className="h-3.5 w-3.5" />
            Timeline
          </div>
          <div className="space-y-2">
            {timeline.map((item) => (
              <div key={item.id} className="flex gap-3 text-sm">
                <span className="mt-1 h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: "var(--theme-accent)" }} />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium" style={{ color: "var(--theme-text-primary)" }}>
                      {localizeStepTitle(item.title)}
                    </span>
                    {item.status ? (
                      <span className="text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
                        {item.status}
                      </span>
                    ) : null}
                    {item.durationMs ? (
                      <span className="text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
                        {formatDuration(item.durationMs)}
                      </span>
                    ) : null}
                  </div>
                  {item.detail ? (
                    <p className="mt-0.5 text-xs leading-relaxed" style={{ color: "var(--theme-text-muted)" }}>
                      {item.detail}
                    </p>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      <SourceList sources={sources} />

      {approvals.length > 0 ? (
        <div className="space-y-2">
          <div className="text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
            Approvals
          </div>
          {approvals.map((approval) => (
            <ApprovalCard key={approval.id} approval={approval} />
          ))}
        </div>
      ) : null}

      {serverArtifacts.length > 0 ? (
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
            <Box className="h-3.5 w-3.5" />
            Artifacts
          </div>
          <div className="grid gap-2 sm:grid-cols-2">
            {serverArtifacts.map((artifact) => {
              const clean = artifact.scanStatus === AGENT_ARTIFACT_SCAN_STATUS.CLEAN;
              return (
                <div key={artifact.artifactId} className="rounded-lg px-3 py-3" style={{ border: "1px solid var(--theme-glass-border)" }}>
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        {clean ? (
                          <Box className="h-3.5 w-3.5 shrink-0" style={{ color: "var(--theme-accent)" }} />
                        ) : (
                          <ShieldAlert className="h-3.5 w-3.5 shrink-0 text-amber-500" />
                        )}
                        <span className="truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                          {artifactTitle(artifact)}
                        </span>
                      </div>
                      <p className="mt-1 text-[11px] uppercase" style={{ color: "var(--theme-text-muted)" }}>
                        {artifactMeta(artifact)}
                      </p>
                    </div>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      disabled={!clean}
                      aria-label="Download artifact"
                      title={clean ? "Download artifact" : "Artifact is not clean"}
                      onClick={() => downloadArtifact(artifact)}
                    >
                      <Download className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                  {artifact.canPreview && artifact.previewText ? (
                    <p className="mt-2 line-clamp-4 whitespace-pre-wrap text-xs leading-relaxed" style={{ color: "var(--theme-text-muted)" }}>
                      {artifact.previewText}
                    </p>
                  ) : null}
                </div>
              );
            })}
          </div>
        </div>
      ) : null}

      {artifacts.length > 0 || quota.length > 0 || memories.length > 0 || costSummary ? (
        <div className="grid gap-2 sm:grid-cols-3">
          {costSummary ? (
            <div className="rounded-lg px-3 py-2" style={{ border: "1px solid var(--theme-glass-border)" }}>
              <div className="flex items-center gap-2">
                <Gauge className="h-3.5 w-3.5" style={{ color: "var(--theme-accent)" }} />
                <span className="truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                  Run cost
                </span>
              </div>
              <p className="mt-1 text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
                {formatNumber(costSummary.totalTokens)} tokens / {formatNumber(costSummary.totalCalls)} calls
              </p>
              <p className="mt-0.5 text-xs font-medium" style={{ color: "var(--theme-text-primary)" }}>
                {formatCost(costSummary.totalCost)}
              </p>
            </div>
          ) : null}
          {artifacts.map((artifact) => (
            <div key={artifact.id} className="rounded-lg px-3 py-2" style={{ border: "1px solid var(--theme-glass-border)" }}>
              <div className="flex items-center gap-2">
                <Box className="h-3.5 w-3.5" style={{ color: "var(--theme-accent)" }} />
                <span className="truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                  {artifact.title}
                </span>
              </div>
              <p className="mt-1 text-[11px] uppercase" style={{ color: "var(--theme-text-muted)" }}>
                {artifact.language}
              </p>
            </div>
          ))}
          {quota.map((item) => (
            <div key={item.id} className="rounded-lg px-3 py-2" style={{ border: "1px solid var(--theme-glass-border)" }}>
              <div className="flex items-center gap-2">
                <Gauge className="h-3.5 w-3.5" style={{ color: "var(--theme-accent)" }} />
                <span className="truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                  {item.label}
                </span>
              </div>
              <p className="mt-1 text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
                {item.used ?? "-"} / {item.limit ?? item.remaining ?? "-"} {item.unit ?? ""}
              </p>
            </div>
          ))}
          {memories.map((memory) => (
            <div key={memory.id} className="rounded-lg px-3 py-2" style={{ border: "1px solid var(--theme-glass-border)" }}>
              <div className="flex items-center gap-2">
                <Database className="h-3.5 w-3.5" style={{ color: "var(--theme-accent)" }} />
                <span className="truncate text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
                  {memory.title}
                </span>
              </div>
              {memory.content ? (
                <p className="mt-1 text-xs leading-relaxed" style={{ color: "var(--theme-text-muted)" }}>
                  {memory.content}
                </p>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
