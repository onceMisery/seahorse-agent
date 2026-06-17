import * as React from "react";
import * as Tabs from "@radix-ui/react-tabs";
import { AnimatePresence, motion } from "motion/react";
import {
  Activity,
  Boxes,
  Brain,
  CheckSquare,
  Coins,
  Globe,
  Library,
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
import { SkillInspectorTab } from "@/components/chat/workbench/SkillInspectorTab";
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

  const artifactCount = (message?.artifacts?.length ?? 0) + (message?.serverArtifacts?.length ?? 0);
  const timelineCount = message?.timeline?.length ?? 0;
  const sourceCount = message?.sources?.length ?? 0;
  const approvalCount = message?.approvals?.length ?? 0;
  const toolCallCount = message?.toolCalls?.length ?? 0;
  const skillCount = message?.skills?.length ?? 0;
  const memoryCount = message?.memories?.length ?? 0;
  const hasCost = Boolean(message?.costSummary);

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          key="workspace-inspector"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: 20 }}
          transition={{ duration: 0.25, ease: "easeOut" }}
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
              <InspectorTabButton value="timeline" label="时间线" count={timelineCount}>
                <Activity className="h-3.5 w-3.5" />
              </InspectorTabButton>
              <InspectorTabButton value="artifacts" label="产物" count={artifactCount}>
                <Boxes className="h-3.5 w-3.5" />
              </InspectorTabButton>
              <InspectorTabButton value="sources" label="来源" count={sourceCount}>
                <Globe className="h-3.5 w-3.5" />
              </InspectorTabButton>
              <InspectorTabButton value="approvals" label="审批" count={approvalCount}>
                <CheckSquare className="h-3.5 w-3.5" />
              </InspectorTabButton>
              <InspectorTabButton value="tools" label="工具调用" count={toolCallCount}>
                <Wrench className="h-3.5 w-3.5" />
              </InspectorTabButton>
              <InspectorTabButton value="skills" label="Skill" count={skillCount}>
                <Library className="h-3.5 w-3.5" />
              </InspectorTabButton>
              <InspectorTabButton value="cost" label="成本" count={hasCost ? 1 : 0}>
                <Coins className="h-3.5 w-3.5" />
              </InspectorTabButton>
              <InspectorTabButton value="memory" label="记忆" count={memoryCount}>
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
                <AnimatePresence mode="wait">
                  <motion.div
                    key={activeTab}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.15 }}
                    className="h-full"
                  >
                    {activeTab === "timeline" && (
                      <TimelineInspectorTab
                        timeline={message.timeline ?? []}
                        currentStepId={message.currentStepId}
                      />
                    )}
                    {activeTab === "artifacts" && (
                      <ArtifactInspectorTab
                        artifacts={message.artifacts ?? []}
                        serverArtifacts={message.serverArtifacts ?? []}
                      />
                    )}
                    {activeTab === "sources" && (
                      <SourcesInspectorTab sources={message.sources ?? []} />
                    )}
                    {activeTab === "approvals" && (
                      <ApprovalsInspectorTab approvals={message.approvals ?? []} />
                    )}
                    {activeTab === "tools" && (
                      <ToolCallsInspectorTab toolCalls={message.toolCalls ?? []} />
                    )}
                    {activeTab === "skills" && (
                      <SkillInspectorTab skills={message.skills ?? []} />
                    )}
                    {activeTab === "cost" && (
                      <CostQuotaInspectorTab
                        costSummary={message.costSummary}
                        quota={message.quota}
                        canResume={message.canResume}
                        canRetry={message.canRetry}
                        agentRunId={message.agentRunId}
                      />
                    )}
                    {activeTab === "memory" && (
                      <MemoryInspectorTab memories={message.memories ?? []} />
                    )}
                    {activeTab === "ui" && (
                      <UIInspectorTab
                        artifacts={message.artifacts ?? []}
                        serverArtifacts={message.serverArtifacts ?? []}
                      />
                    )}
                  </motion.div>
                </AnimatePresence>
              )}
            </div>
          </Tabs.Root>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
