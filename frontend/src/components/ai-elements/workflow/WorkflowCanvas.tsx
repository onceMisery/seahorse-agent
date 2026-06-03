import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  type Edge,
  type Node,
  type NodeTypes
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";

import { WorkflowStepNode } from "@/components/ai-elements/workflow/WorkflowStepNode";
import type { WorkflowStepNodeData } from "@/components/ai-elements/types";

type WorkflowStepNodeModel = Node<WorkflowStepNodeData, "workflowStep">;

const nodeTypes = {
  workflowStep: WorkflowStepNode
} satisfies NodeTypes;

interface WorkflowCanvasProps {
  nodes: WorkflowStepNodeModel[];
  edges: Edge[];
  onNodeClick?: (node: WorkflowStepNodeModel) => void;
  height?: number;
}

export function WorkflowCanvas({ nodes, edges, onNodeClick, height = 460 }: WorkflowCanvasProps) {
  return (
    <div
      className="overflow-hidden rounded-lg border"
      style={{
        height,
        borderColor: "var(--theme-glass-border)",
        backgroundColor: "var(--theme-bg-surface)"
      }}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodeClick={(_, node) => onNodeClick?.(node as WorkflowStepNodeModel)}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.35}
        maxZoom={1.6}
      >
        <Background color="var(--theme-accent-alpha-20)" gap={18} />
        <Controls />
        <MiniMap pannable zoomable nodeStrokeWidth={3} />
      </ReactFlow>
    </div>
  );
}
