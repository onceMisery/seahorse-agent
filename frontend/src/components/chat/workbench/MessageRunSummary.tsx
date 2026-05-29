import { useWorkbenchStore, type WorkbenchTab } from "@/stores/workbenchStore";
import type { Message } from "@/types";

interface MessageRunSummaryProps {
  message: Message;
}

const tabByMetric: Record<string, WorkbenchTab> = {
  steps: "timeline",
  sources: "sources",
  artifacts: "artifacts",
  approvals: "approvals",
  cost: "cost",
  memories: "memory"
};

interface MetricItem {
  key: string;
  label: string;
  count: number | string;
  tab: WorkbenchTab;
}

export function MessageRunSummary({ message }: MessageRunSummaryProps) {
  const { openInspector } = useWorkbenchStore();

  const metrics: MetricItem[] = [];

  const timelineCount = message.timeline?.length ?? 0;
  if (timelineCount > 0) {
    metrics.push({ key: "steps", label: "步骤", count: timelineCount, tab: tabByMetric.steps });
  }

  const sourceCount = message.sources?.length ?? 0;
  if (sourceCount > 0) {
    metrics.push({ key: "sources", label: "来源", count: sourceCount, tab: tabByMetric.sources });
  }

  const artifactCount = (message.artifacts?.length ?? 0) + (message.serverArtifacts?.length ?? 0);
  if (artifactCount > 0) {
    metrics.push({ key: "artifacts", label: "产物", count: artifactCount, tab: tabByMetric.artifacts });
  }

  const approvalCount = message.approvals?.length ?? 0;
  if (approvalCount > 0) {
    metrics.push({ key: "approvals", label: "审批", count: approvalCount, tab: tabByMetric.approvals });
  }

  if (message.costSummary) {
    const cost = message.costSummary.totalCost;
    metrics.push({
      key: "cost",
      label: "费用",
      count: typeof cost === "number" ? `$${cost.toFixed(4)}` : "-",
      tab: tabByMetric.cost
    });
  }

  const memoryCount = message.memories?.length ?? 0;
  if (memoryCount > 0) {
    metrics.push({ key: "memories", label: "记忆", count: memoryCount, tab: tabByMetric.memories });
  }

  if (metrics.length === 0) return null;

  return (
    <div className="mt-2 flex flex-wrap gap-1.5">
      {metrics.map((metric) => (
        <button
          key={metric.key}
          type="button"
          onClick={() => openInspector(message.id, metric.tab)}
          className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] transition-colors hover:opacity-80"
          style={{
            backgroundColor: "var(--theme-accent-alpha-10)",
            border: "1px solid var(--theme-accent-alpha-20)",
            color: "var(--theme-text-secondary)"
          }}
        >
          <span className="font-semibold" style={{ color: "var(--theme-accent)" }}>
            {metric.count}
          </span>
          {metric.label}
        </button>
      ))}
    </div>
  );
}
