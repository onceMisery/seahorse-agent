import type { Edge, Node } from "@xyflow/react";

import type { WorkflowStep, WorkflowStepNodeData } from "@/components/ai-elements/types";

const NODE_WIDTH = 220;
const NODE_HEIGHT = 96;
const HORIZONTAL_GAP = 72;
const VERTICAL_GAP = 72;

export function formatWorkflowDuration(start?: string | null, end?: string | null) {
  if (!start || !end) return undefined;
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (!Number.isFinite(ms) || ms < 0) return undefined;
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

export function workflowStepsToGraph(steps: WorkflowStep[]): {
  nodes: Node<WorkflowStepNodeData, "workflowStep">[];
  edges: Edge[];
} {
  const sorted = [...steps].sort((a, b) => (a.stepNo ?? 0) - (b.stepNo ?? 0));
  const nodes = sorted.map((step, index) => {
    const row = Math.floor(index / 4);
    const column = index % 4;
    const id = step.stepId || `step-${step.stepNo ?? index + 1}`;
    return {
      id,
      type: "workflowStep",
      position: {
        x: column * (NODE_WIDTH + HORIZONTAL_GAP),
        y: row * (NODE_HEIGHT + VERTICAL_GAP)
      },
      data: {
        label: step.summary || step.stepType || `Step ${step.stepNo ?? index + 1}`,
        status: step.status,
        description: step.summary,
        duration: formatWorkflowDuration(step.startedAt, step.finishedAt),
        stepType: step.stepType,
        stepNo: step.stepNo,
        errorMessage: step.errorMessage
      }
    } satisfies Node<WorkflowStepNodeData, "workflowStep">;
  });

  const edges = nodes.slice(0, -1).map((node, index) => {
    const next = nodes[index + 1];
    return {
      id: `${node.id}-${next.id}`,
      source: node.id,
      target: next.id,
      animated: Boolean(next.data.status === "RUNNING"),
      type: "smoothstep"
    } satisfies Edge;
  });

  return { nodes, edges };
}
