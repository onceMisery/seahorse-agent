import * as React from "react";
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  useEdgesState,
  useNodesState,
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

export function WorkflowCanvas({ nodes: initialNodes, edges: initialEdges, onNodeClick, height = 460 }: WorkflowCanvasProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState<WorkflowStepNodeModel>(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  React.useEffect(() => {
    setNodes(initialNodes);
  }, [initialNodes, setNodes]);

  React.useEffect(() => {
    setEdges(initialEdges);
  }, [initialEdges, setEdges]);

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
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
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
