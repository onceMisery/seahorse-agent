import { useEffect } from "react";
import { CheckCircle, AlertTriangle, XCircle, Info } from "lucide-react";
import { useReadinessStore } from "@/stores/readinessStore";

const STATUS_CONFIG = {
  healthy: {
    icon: CheckCircle,
    color: "var(--color-success, #22c55e)",
    bgColor: "var(--color-success-bg, rgba(34, 197, 94, 0.08))",
    borderColor: "var(--color-success-border, rgba(34, 197, 94, 0.2))",
    label: "系统就绪"
  },
  degraded: {
    icon: AlertTriangle,
    color: "var(--color-warning, #f59e0b)",
    bgColor: "var(--color-warning-bg, rgba(245, 158, 11, 0.08))",
    borderColor: "var(--color-warning-border, rgba(245, 158, 11, 0.2))",
    label: "部分能力降级"
  },
  blocked: {
    icon: XCircle,
    color: "var(--color-error, #ef4444)",
    bgColor: "var(--color-error-bg, rgba(239, 68, 68, 0.08))",
    borderColor: "var(--color-error-border, rgba(239, 68, 68, 0.2))",
    label: "关键能力缺失"
  }
};

const MODE_LABELS: Record<string, string> = {
  DEMO: "演示模式",
  RAG: "RAG 模式",
  ENTERPRISE: "企业模式"
};

export function ReadinessStatusBar() {
  const { summary, isLoading, loadSummary } = useReadinessStore();

  useEffect(() => {
    if (!summary && !isLoading) {
      loadSummary();
    }
  }, [summary, isLoading, loadSummary]);

  if (isLoading && !summary) {
    return (
      <div
        className="flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs"
        style={{
          backgroundColor: "var(--theme-glass-bg)",
          border: "1px solid var(--theme-glass-border)",
          color: "var(--theme-text-muted)"
        }}
      >
        <Info className="h-3.5 w-3.5 animate-pulse" />
        <span>检查系统状态...</span>
      </div>
    );
  }

  if (!summary) return null;

  const config = STATUS_CONFIG[summary.overall] || STATUS_CONFIG.healthy;
  const Icon = config.icon;
  const modeLabel = MODE_LABELS[summary.mode] || summary.mode;

  const failedChecks = summary.checks.filter((c) => c.status === "failed");
  const tooltip = failedChecks.length > 0
    ? failedChecks.map((c) => `${c.name}: ${c.impact || c.message}`).join("\n")
    : "";

  return (
    <div
      className="flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs transition-colors"
      style={{
        backgroundColor: config.bgColor,
        border: `1px solid ${config.borderColor}`,
        color: config.color
      }}
      title={tooltip}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" />
      <span className="font-medium">{config.label}</span>
      <span
        className="text-[11px]"
        style={{ color: "var(--theme-text-muted)" }}
      >
        {modeLabel} · {summary.passedCount}/{summary.totalCount} 通过
      </span>
    </div>
  );
}
