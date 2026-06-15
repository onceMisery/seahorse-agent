import * as React from "react";
import { Loader2, RefreshCw, RotateCcw } from "lucide-react";
import { toast } from "sonner";

import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import { resumeAgentRun, retryAgentRun } from "@/services/agentRunService";
import type { AgentQuota, AgentRunCostSummary } from "@/types";

interface CostQuotaInspectorTabProps {
  costSummary?: AgentRunCostSummary;
  quota?: AgentQuota[];
  canResume?: boolean;
  canRetry?: boolean;
  agentRunId?: string;
}

function formatNumber(value?: number): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return "0";
  return value.toLocaleString();
}

function formatCost(value?: number): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return "$0.0000";
  return `$${value.toFixed(4)}`;
}

export function CostQuotaInspectorTab({
  costSummary,
  quota = [],
  canResume,
  canRetry,
  agentRunId
}: CostQuotaInspectorTabProps) {
  const [loading, setLoading] = React.useState<"resume" | "retry" | null>(null);

  const hasContent = Boolean(costSummary) || quota.length > 0;
  if (!hasContent) return <InspectorEmptyState />;

  const handleResume = async () => {
    if (!agentRunId || loading) return;
    setLoading("resume");
    try {
      await resumeAgentRun(agentRunId);
      toast.success("已恢复运行");
    } catch {
      toast.error("恢复失败");
    } finally {
      setLoading(null);
    }
  };

  const handleRetry = async () => {
    if (!agentRunId || loading) return;
    setLoading("retry");
    try {
      await retryAgentRun(agentRunId);
      toast.success("已重试");
    } catch {
      toast.error("重试失败");
    } finally {
      setLoading(null);
    }
  };

  return (
    <div className="p-3 space-y-3">
      {costSummary && (
        <div
          className="rounded-lg p-3 space-y-2"
          style={{ border: "1px solid var(--sh-workbench-border)" }}
        >
          <p className="text-[10px] font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
            运行成本
          </p>
          <div className="grid grid-cols-3 gap-2">
            <div>
              <p className="text-[10px]" style={{ color: "var(--theme-text-muted)" }}>Token</p>
              <p className="text-sm font-semibold" style={{ color: "var(--theme-text-primary)" }}>
                {formatNumber(costSummary.totalTokens)}
              </p>
            </div>
            <div>
              <p className="text-[10px]" style={{ color: "var(--theme-text-muted)" }}>调用次数</p>
              <p className="text-sm font-semibold" style={{ color: "var(--theme-text-primary)" }}>
                {formatNumber(costSummary.totalCalls)}
              </p>
            </div>
            <div>
              <p className="text-[10px]" style={{ color: "var(--theme-text-muted)" }}>费用</p>
              <p className="text-sm font-semibold" style={{ color: "var(--sh-workbench-accent)" }}>
                {formatCost(costSummary.totalCost)}
              </p>
            </div>
          </div>
        </div>
      )}

      {quota.length > 0 && (
        <div className="space-y-2">
          <p className="text-[10px] font-semibold uppercase px-1" style={{ color: "var(--theme-text-muted)" }}>
            配额
          </p>
          {quota.map((item) => {
            const used = item.used ?? 0;
            const limit = item.limit ?? item.remaining ?? 0;
            const pct = limit > 0 ? Math.min((used / limit) * 100, 100) : 0;
            return (
              <div
                key={item.id}
                className="rounded-lg p-3 space-y-1.5"
                style={{ border: "1px solid var(--sh-workbench-border)" }}
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium" style={{ color: "var(--theme-text-primary)" }}>
                    {item.label}
                  </span>
                  <span className="text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
                    {used} / {limit} {item.unit ?? ""}
                  </span>
                </div>
                <div
                  className="h-1.5 w-full overflow-hidden rounded-full"
                  style={{ backgroundColor: "var(--sh-workbench-panel-subtle)" }}
                >
                  <div
                    className="h-full rounded-full transition-all"
                    style={{
                      width: `${pct}%`,
                      backgroundColor: pct >= 90 ? "rgb(239,68,68)" : "var(--sh-workbench-accent)"
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}

      {(canResume || canRetry) && agentRunId && (
        <div className="flex gap-2 pt-1">
          {canResume && (
            <button
              type="button"
              onClick={handleResume}
              disabled={loading !== null}
              className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-xs font-medium transition-colors disabled:opacity-60"
              style={{
                backgroundColor: "var(--sh-workbench-accent-soft)",
                color: "var(--sh-workbench-accent)",
                border: "1px solid var(--sh-workbench-accent)"
              }}
            >
              {loading === "resume"
                ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                : <RefreshCw className="h-3.5 w-3.5" />}
              恢复运行
            </button>
          )}
          {canRetry && (
            <button
              type="button"
              onClick={handleRetry}
              disabled={loading !== null}
              className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-xs font-medium transition-colors disabled:opacity-60"
              style={{
                backgroundColor: "var(--sh-workbench-panel-subtle)",
                color: "var(--theme-text-secondary)",
                border: "1px solid var(--sh-workbench-border)"
              }}
            >
              {loading === "retry"
                ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                : <RotateCcw className="h-3.5 w-3.5" />}
              重试
            </button>
          )}
        </div>
      )}
    </div>
  );
}
