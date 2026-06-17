import * as React from "react";
import { AnimatePresence, motion } from "motion/react";
import { Loader2 } from "lucide-react";

import type { AgentTimelineItem } from "@/types";

interface AgentLiveStatusProps {
  timeline: AgentTimelineItem[];
  currentStepId?: string | null;
  toolCalls?: { id: string; toolId: string; status?: string }[];
}

const STEP_LABELS: Record<string, string> = {
  PLAN: "规划研究方向",
  SEARCH: "搜索相关资料",
  FETCH: "抓取网页内容",
  EXTRACT_EVIDENCE: "提取关键证据",
  SYNTHESIZE: "综合分析",
  WRITE_REPORT: "撰写报告",
  VERIFY_CITATIONS: "验证引用",
  TOOL_SEARCH: "搜索可用工具",
  MEMORY_READ: "读取记忆",
  MEMORY_WRITE: "写入记忆",
  WEB_SEARCH: "网络搜索",
  WEB_FETCH: "抓取网页",
  IMAGE_GENERATION: "生成图片",
  CHART_VISUALIZATION: "生成图表"
};

function localizeStep(title: string): string {
  const key = title.toUpperCase().replace(/\s+/g, "_");
  return STEP_LABELS[key] ?? title;
}

function findActiveStep(
  timeline: AgentTimelineItem[],
  currentStepId?: string | null
): AgentTimelineItem | null {
  if (currentStepId) {
    const found = timeline.find((t) => t.id === currentStepId);
    if (found) return found;
  }
  // Fallback: last item with RUNNING/IN_PROGRESS status
  for (let i = timeline.length - 1; i >= 0; i--) {
    const s = (timeline[i].status ?? "").toUpperCase();
    if (s === "RUNNING" || s === "IN_PROGRESS") return timeline[i];
  }
  // Last timeline item if any
  return timeline.length > 0 ? timeline[timeline.length - 1] : null;
}

function findActiveToolCall(
  toolCalls?: { id: string; toolId: string; status?: string }[]
): { toolId: string } | null {
  if (!toolCalls) return null;
  for (let i = toolCalls.length - 1; i >= 0; i--) {
    const s = (toolCalls[i].status ?? "").toUpperCase();
    if (s === "RUNNING" || s === "IN_PROGRESS" || s === "EXECUTING") {
      return { toolId: toolCalls[i].toolId };
    }
  }
  return null;
}

export function AgentLiveStatus({ timeline, currentStepId, toolCalls }: AgentLiveStatusProps) {
  const activeStep = findActiveStep(timeline, currentStepId);
  const activeTool = findActiveToolCall(toolCalls);

  if (!activeStep && !activeTool) return null;

  const displayText = activeTool
    ? `正在调用工具 ${activeTool.toolId}`
    : activeStep
      ? localizeStep(activeStep.title)
      : null;

  if (!displayText) return null;

  const completedCount = timeline.filter((t) => {
    const s = (t.status ?? "").toUpperCase();
    return s === "DONE" || s === "FINISHED" || s === "COMPLETED";
  }).length;

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={activeStep?.id ?? activeTool?.toolId ?? "idle"}
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -4 }}
        transition={{ duration: 0.25, ease: "easeOut" }}
        className="flex items-center gap-2.5 rounded-lg px-3 py-2"
        style={{
          background: "var(--sh-workbench-panel-subtle, rgba(255,255,255,0.03))",
          border: "1px solid var(--sh-workbench-border, rgba(255,255,255,0.06))"
        }}
      >
        <motion.div
          animate={{ rotate: 360 }}
          transition={{ duration: 1.2, repeat: Infinity, ease: "linear" }}
        >
          <Loader2
            className="h-3.5 w-3.5"
            style={{ color: "var(--sh-workbench-accent, var(--theme-accent))" }}
          />
        </motion.div>
        <div className="min-w-0 flex-1">
          <span
            className="block truncate text-xs font-medium"
            style={{ color: "var(--sh-workbench-accent, var(--theme-accent))" }}
          >
            {displayText}
          </span>
          {timeline.length > 1 ? (
            <span
              className="text-[10px]"
              style={{ color: "var(--theme-text-muted, rgba(255,255,255,0.4))" }}
            >
              已完成 {completedCount}/{timeline.length} 步
            </span>
          ) : null}
        </div>
        {/* Mini progress dots */}
        <div className="flex items-center gap-1">
          {timeline.slice(0, 8).map((item) => {
            const s = (item.status ?? "").toUpperCase();
            const isDone = s === "DONE" || s === "FINISHED" || s === "COMPLETED";
            const isActive = item.id === (activeStep?.id ?? "");
            return (
              <motion.div
                key={item.id}
                className="h-1.5 rounded-full"
                animate={{
                  width: isActive ? 12 : 6,
                  backgroundColor: isDone
                    ? "var(--sh-workbench-accent, var(--theme-accent))"
                    : isActive
                      ? "var(--sh-workbench-accent, var(--theme-accent))"
                      : "var(--theme-text-muted, rgba(255,255,255,0.2))"
                }}
                transition={{ duration: 0.3 }}
                style={{ opacity: isDone ? 1 : isActive ? 0.8 : 0.35 }}
              />
            );
          })}
        </div>
      </motion.div>
    </AnimatePresence>
  );
}
