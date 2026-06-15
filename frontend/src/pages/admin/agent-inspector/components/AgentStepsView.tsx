import { useEffect, useState } from "react";
import type { Edge, Node } from "@xyflow/react";
import { toast } from "sonner";

import { Shimmer, WorkflowCanvas, workflowStepsToGraph } from "@/components/ai-elements";
import type { WorkflowStepNodeData } from "@/components/ai-elements/types";
import { getAgentRunSteps, type AgentRunStep } from "@/services/agentArtifactService";
import { getAgentRunWorkflow } from "@/services/agentRunService";
import type { AgentRunWorkflow } from "@/types";
import { getErrorMessage } from "@/utils/error";

function formatDuration(start?: string, end?: string) {
  if (!start || !end) return "-";
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (Number.isNaN(ms) || ms < 0) return "-";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

function statusBadge(status?: string) {
  if (!status) return null;
  const colors: Record<string, string> = {
    COMPLETED: "bg-emerald-100 text-emerald-700",
    FAILED: "bg-red-100 text-red-700",
    RUNNING: "bg-blue-100 text-blue-700",
    SKIPPED: "bg-slate-100 text-slate-500",
    PENDING: "bg-amber-100 text-amber-700"
  };
  return (
    <span className={`rounded px-1.5 py-0.5 font-mono text-xs ${colors[status] ?? "bg-slate-100 text-slate-600"}`}>
      {status}
    </span>
  );
}

export function AgentStepsView({ runId }: { runId: string }) {
  const [steps, setSteps] = useState<AgentRunStep[]>([]);
  const [workflow, setWorkflow] = useState<AgentRunWorkflow | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!runId) return;
    let cancelled = false;
    setLoading(true);
    Promise.allSettled([getAgentRunWorkflow(runId), getAgentRunSteps(runId)])
      .then(([workflowResult, stepsResult]) => {
        if (cancelled) return;
        if (workflowResult.status === "fulfilled") {
          setWorkflow(workflowResult.value);
        } else {
          setWorkflow(null);
        }
        if (stepsResult.status === "fulfilled") {
          setSteps(Array.isArray(stepsResult.value) ? stepsResult.value : []);
        } else {
          toast.error(getErrorMessage(stepsResult.reason, "加载步骤失败"));
          setSteps([]);
        }
      })
      .catch((error) => {
        if (!cancelled) toast.error(getErrorMessage(error, "加载步骤失败"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [runId]);

  if (loading) {
    return (
      <div className="space-y-4">
        <Shimmer className="h-[260px] rounded-lg" />
        <Shimmer lines={4} />
      </div>
    );
  }

  if (steps.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        暂无步骤
      </div>
    );
  }

  const graph = workflow && workflow.nodes.length > 0
    ? workflowToGraph(workflow)
    : workflowStepsToGraph(steps);

  return (
    <div className="space-y-4">
      <WorkflowCanvas nodes={graph.nodes} edges={graph.edges} />
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-xs text-slate-500">
              <th className="pb-2 pr-3 font-medium">#</th>
              <th className="pb-2 pr-3 font-medium">类型</th>
              <th className="pb-2 pr-3 font-medium">状态</th>
              <th className="pb-2 pr-3 font-medium">摘要</th>
              <th className="pb-2 pr-3 font-medium">耗时</th>
            </tr>
          </thead>
          <tbody>
            {steps.map((step) => (
              <tr
                key={step.stepId ?? step.stepNo}
                className={`border-b border-slate-100 ${step.status === "FAILED" ? "bg-red-50" : ""}`}
              >
                <td className="py-2 pr-3 font-mono text-xs text-slate-400">{step.stepNo}</td>
                <td className="py-2 pr-3">
                  <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-700">
                    {step.stepType}
                  </span>
                </td>
                <td className="py-2 pr-3">{statusBadge(step.status)}</td>
                <td className="py-2 pr-3 text-xs text-slate-600">
                  {step.summary ?? "-"}
                  {step.status === "FAILED" && step.errorMessage ? (
                    <div className="mt-1 text-red-600">{step.errorMessage}</div>
                  ) : null}
                </td>
                <td className="py-2 pr-3 font-mono text-xs text-slate-500">
                  {formatDuration(step.startedAt, step.finishedAt)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function workflowToGraph(workflow: AgentRunWorkflow): {
  nodes: Node<WorkflowStepNodeData, "workflowStep">[];
  edges: Edge[];
} {
  return {
    nodes: workflow.nodes.map((node) => ({
      id: node.id,
      type: "workflowStep",
      position: node.position,
      data: {
        label: node.data.label,
        status: node.data.status,
        description: node.data.description,
        duration: formatMs(node.data.durationMs),
        stepType: node.data.stepType ?? undefined,
        stepNo: node.data.stepNo ?? undefined,
        errorMessage: node.data.errorMessage
      }
    })),
    edges: workflow.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      animated: Boolean(edge.animated),
      type: edge.type ?? "smoothstep",
      label: edge.label ?? undefined
    }))
  };
}

function formatMs(durationMs?: number | null) {
  if (typeof durationMs !== "number" || durationMs < 0) return undefined;
  if (durationMs < 1000) return `${durationMs}ms`;
  return `${(durationMs / 1000).toFixed(2)}s`;
}
