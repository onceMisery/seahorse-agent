import * as React from "react";
import * as Tabs from "@radix-ui/react-tabs";
import {
  Activity,
  Boxes,
  Brain,
  CheckSquare,
  Coins,
  Globe,
  LayoutGrid,
  Wrench,
  X
} from "lucide-react";

import { ArtifactInspectorTab } from "@/components/chat/workbench/ArtifactInspectorTab";
import { ApprovalsInspectorTab } from "@/components/chat/workbench/ApprovalsInspectorTab";
import { CostQuotaInspectorTab } from "@/components/chat/workbench/CostQuotaInspectorTab";
import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import { InspectorTabButton } from "@/components/chat/workbench/InspectorTabButton";
import { MemoryInspectorTab } from "@/components/chat/workbench/MemoryInspectorTab";
import { SourcesInspectorTab } from "@/components/chat/workbench/SourcesInspectorTab";
import { TimelineInspectorTab } from "@/components/chat/workbench/TimelineInspectorTab";
import { ToolCallsInspectorTab } from "@/components/chat/workbench/ToolCallsInspectorTab";
import { UIInspectorTab } from "@/components/chat/workbench/UIInspectorTab";
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
  const toolCallCount = message?.toolCalls?.length ?? 0;
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
          <InspectorTabButton value="tools" label="Tool Calls" count={toolCallCount}>
            <Wrench className="h-3.5 w-3.5" />
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
                <TimelineInspectorTab
                  timeline={message.timeline ?? []}
                  currentStepId={message.currentStepId}
                />
              </Tabs.Content>
              <Tabs.Content value="artifacts" className="h-full overflow-y-auto">
                <ArtifactInspectorTab
                  artifacts={message.artifacts ?? []}
                  serverArtifacts={message.serverArtifacts ?? []}
                />
              </Tabs.Content>
              <Tabs.Content value="sources" className="h-full">
                <SourcesInspectorTab sources={message.sources ?? []} />
              </Tabs.Content>
              <Tabs.Content value="approvals" className="h-full">
                <ApprovalsInspectorTab approvals={message.approvals ?? []} />
              </Tabs.Content>
              <Tabs.Content value="tools" className="h-full">
                <ToolCallsInspectorTab toolCalls={message.toolCalls ?? []} />
              </Tabs.Content>
              <Tabs.Content value="cost" className="h-full">
                <CostQuotaInspectorTab
                  costSummary={message.costSummary}
                  quota={message.quota}
                  canResume={message.canResume}
                  canRetry={message.canRetry}
                  agentRunId={message.agentRunId}
                />
              </Tabs.Content>
              <Tabs.Content value="memory" className="h-full">
                <MemoryInspectorTab memories={message.memories ?? []} />
              </Tabs.Content>
              <Tabs.Content value="ui" className="h-full">
                <UIInspectorTab
                  artifacts={message.artifacts ?? []}
                  serverArtifacts={message.serverArtifacts ?? []}
                />
              </Tabs.Content>
            </>
          )}
        </div>
      </Tabs.Root>
    </div>
  );
}
