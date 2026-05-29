import * as React from "react";
import * as Tabs from "@radix-ui/react-tabs";
import {
  Activity,
  Boxes,
  Brain,
  CheckSquare,
  Coins,
  Database,
  Globe,
  LayoutGrid,
  X
} from "lucide-react";

import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import { InspectorTabButton } from "@/components/chat/workbench/InspectorTabButton";
import { useWorkbenchStore } from "@/stores/workbenchStore";
import type { Message } from "@/types";

interface WorkspaceInspectorProps {
  message: Message | null;
  open: boolean;
  onClose: () => void;
}

export function WorkspaceInspector({ message, open, onClose }: WorkspaceInspectorProps) {
  const { activeTab, setActiveTab } = useWorkbenchStore();

  if (!open) return null;

  const artifactCount = (message?.artifacts?.length ?? 0) + (message?.serverArtifacts?.length ?? 0);
  const timelineCount = message?.timeline?.length ?? 0;
  const sourceCount = message?.sources?.length ?? 0;
  const approvalCount = message?.approvals?.length ?? 0;
  const memoryCount = message?.memories?.length ?? 0;
  const hasCost = Boolean(message?.costSummary);

  return (
    <div
      className="flex h-full flex-col"
      style={{
        background: "var(--sh-workbench-panel)",
        borderLeft: "1px solid var(--sh-workbench-border)"
      }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-3 py-2"
        style={{ borderBottom: "1px solid var(--sh-workbench-border)" }}
      >
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-xs font-semibold" style={{ color: "var(--theme-text-primary)" }}>
            运行详情
          </span>
          {message?.agentRunId ? (
            <span
              className="truncate rounded px-1.5 py-0.5 font-mono text-[10px]"
              style={{
                backgroundColor: "var(--sh-workbench-panel-subtle)",
                color: "var(--theme-text-muted)"
              }}
            >
              {message.agentRunId.slice(0, 8)}
            </span>
          ) : null}
          {message?.agentRunStatus ? (
            <span
              className="rounded-full px-2 py-0.5 text-[10px] font-medium"
              style={{
                backgroundColor: "var(--sh-workbench-accent-soft)",
                color: "var(--sh-workbench-accent)"
              }}
            >
              {message.agentRunStatus}
            </span>
          ) : null}
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="关闭检查器"
          className="flex h-6 w-6 items-center justify-center rounded transition-colors"
          style={{ color: "var(--theme-text-muted)" }}
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Tabs */}
      <Tabs.Root
        value={activeTab}
        onValueChange={(v) => setActiveTab(v as typeof activeTab)}
        className="flex min-h-0 flex-1 flex-col"
      >
        <Tabs.List
          className="flex flex-wrap gap-0.5 px-2 py-1.5"
          style={{ borderBottom: "1px solid var(--sh-workbench-border)" }}
          aria-label="检查器标签"
        >
          <InspectorTabButton value="timeline" label="Timeline" count={timelineCount}>
            <Activity className="h-3.5 w-3.5" />
          </InspectorTabButton>
          <InspectorTabButton value="artifacts" label="Artifacts" count={artifactCount}>
            <Boxes className="h-3.5 w-3.5" />
          </InspectorTabButton>
          <InspectorTabButton value="sources" label="Sources" count={sourceCount}>
            <Globe className="h-3.5 w-3.5" />
          </InspectorTabButton>
          <InspectorTabButton value="approvals" label="Approvals" count={approvalCount}>
            <CheckSquare className="h-3.5 w-3.5" />
          </InspectorTabButton>
          <InspectorTabButton value="cost" label="Cost" count={hasCost ? 1 : 0}>
            <Coins className="h-3.5 w-3.5" />
          </InspectorTabButton>
          <InspectorTabButton value="memory" label="Memory" count={memoryCount}>
            <Brain className="h-3.5 w-3.5" />
          </InspectorTabButton>
          <InspectorTabButton value="ui" label="UI">
            <LayoutGrid className="h-3.5 w-3.5" />
          </InspectorTabButton>
        </Tabs.List>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {!message ? (
            <InspectorEmptyState />
          ) : (
            <>
              <Tabs.Content value="timeline" className="h-full">
                <InspectorEmptyState />
              </Tabs.Content>
              <Tabs.Content value="artifacts" className="h-full">
                <InspectorEmptyState />
              </Tabs.Content>
              <Tabs.Content value="sources" className="h-full">
                <InspectorEmptyState />
              </Tabs.Content>
              <Tabs.Content value="approvals" className="h-full">
                <InspectorEmptyState />
              </Tabs.Content>
              <Tabs.Content value="cost" className="h-full">
                <InspectorEmptyState />
              </Tabs.Content>
              <Tabs.Content value="memory" className="h-full">
                <InspectorEmptyState />
              </Tabs.Content>
              <Tabs.Content value="ui" className="h-full">
                <InspectorEmptyState />
              </Tabs.Content>
            </>
          )}
        </div>
      </Tabs.Root>
    </div>
  );
}
