import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import * as Tabs from "@radix-ui/react-tabs";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  getAgentRunSnapshot,
  getAgentRunCostSummary,
  listAgentRunEvents
} from "@/services/agentRunService";
import type { AgentRunSnapshot, AgentRunCostSummary, StreamEventEnvelope } from "@/types";
import { getErrorMessage } from "@/utils/error";
import { AgentEventStream } from "./components/AgentEventStream";
import { AgentStateView } from "./components/AgentStateView";
import { AgentContextView } from "./components/AgentContextView";
import { AgentToolsView } from "./components/AgentToolsView";
import { AgentStepsView } from "./components/AgentStepsView";
import { AgentCheckpointsView } from "./components/AgentCheckpointsView";
import { AgentHandoffsView } from "./components/AgentHandoffsView";
import { AgentArtifactsView } from "./components/AgentArtifactsView";
import { AgentRunActions } from "./components/AgentRunActions";

const TABS = [
  { value: "events", label: "Events" },
  { value: "state", label: "State" },
  { value: "context", label: "Context" },
  { value: "tools", label: "Tools" },
  { value: "steps", label: "Steps" },
  { value: "checkpoints", label: "Checkpoints" },
  { value: "handoffs", label: "Handoffs" },
  { value: "artifacts", label: "Artifacts" }
] as const;

type InspectorTab = (typeof TABS)[number]["value"];

function normalizeReplayEvents(events: StreamEventEnvelope[]): StreamEventEnvelope[] {
  const bySeq = new Map<number, StreamEventEnvelope>();
  for (const event of events) {
    if (typeof event.eventSeq !== "number" || !Number.isFinite(event.eventSeq)) {
      continue;
    }
    if (!bySeq.has(event.eventSeq)) {
      bySeq.set(event.eventSeq, event);
    }
  }
  return Array.from(bySeq.values()).sort((a, b) => a.eventSeq - b.eventSeq);
}

export function AgentInspectorPage() {
  const { runId: routeRunId } = useParams<{ runId?: string }>();
  const [inputRunId, setInputRunId] = useState(routeRunId ?? "");
  const [activeRunId, setActiveRunId] = useState(routeRunId ?? "");
  const [activeTab, setActiveTab] = useState<InspectorTab>("events");
  const [loading, setLoading] = useState(false);
  const [snapshot, setSnapshot] = useState<AgentRunSnapshot | null>(null);
  const [costSummary, setCostSummary] = useState<AgentRunCostSummary | null>(null);
  const [events, setEvents] = useState<StreamEventEnvelope[]>([]);

  useEffect(() => {
    if (routeRunId) {
      setInputRunId(routeRunId);
      setActiveRunId(routeRunId);
    }
  }, [routeRunId]);

  useEffect(() => {
    if (!activeRunId) return;
    let cancelled = false;
    setLoading(true);
    Promise.all([
      getAgentRunSnapshot(activeRunId).catch(() => null),
      getAgentRunCostSummary(activeRunId).catch(() => null),
      listAgentRunEvents(activeRunId, 0).catch(() => [])
    ])
      .then(([snap, cost, evts]) => {
        if (cancelled) return;
        setSnapshot(snap ?? null);
        setCostSummary(cost ?? null);
        setEvents(Array.isArray(evts) ? normalizeReplayEvents(evts) : []);
      })
      .catch((error) => {
        if (cancelled) return;
        toast.error(getErrorMessage(error, "Failed to load run data"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeRunId]);

  const handleLoad = () => {
    const trimmed = inputRunId.trim();
    if (!trimmed) return;
    setActiveRunId(trimmed);
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Agent Inspector</h1>
          <p className="admin-page-subtitle">
            Web inspector-style debug view for agent run events, state, context, and tool calls.
          </p>
        </div>
      </div>

      <div className="flex gap-2">
        <Input
          value={inputRunId}
          onChange={(e) => setInputRunId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleLoad()}
          placeholder="Enter run ID..."
          className="max-w-[480px]"
        />
        <Button onClick={handleLoad} disabled={loading || !inputRunId.trim()}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          Load
        </Button>
      </div>

      {activeRunId ? (
        <div className="flex items-center justify-between">
          <div className="space-y-1 text-sm text-slate-500">
            Inspecting: <span className="font-mono text-slate-700">{activeRunId}</span>
            {costSummary ? (
              <span className="ml-4">
                Tokens: {costSummary.totalTokens} · Calls: {costSummary.totalCalls} · Cost:{" "}
                {costSummary.totalCost.toLocaleString("zh-CN", { minimumFractionDigits: 4 })}
              </span>
            ) : null}
            {snapshot?.run?.status ? (
              <span className="ml-3 rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-700">
                {snapshot.run.status}
              </span>
            ) : null}
          </div>
          <AgentRunActions
            runId={activeRunId}
            status={snapshot?.run?.status}
            onActionComplete={() => setActiveRunId((prev) => prev)}
          />
        </div>
      ) : null}

      <Tabs.Root
        value={activeTab}
        onValueChange={(v) => setActiveTab(v as InspectorTab)}
        className="space-y-4"
      >
        <Tabs.List className="flex flex-wrap gap-2 rounded-lg border border-slate-200 bg-slate-100 p-2">
          {TABS.map((tab) => (
            <Tabs.Trigger
              key={tab.value}
              value={tab.value}
              className="inline-flex h-10 items-center gap-2 rounded-md px-3 text-sm font-medium transition data-[state=active]:bg-slate-950 data-[state=active]:text-white data-[state=inactive]:text-slate-600 data-[state=inactive]:hover:bg-white data-[state=inactive]:hover:text-slate-950"
            >
              {tab.label}
            </Tabs.Trigger>
          ))}
        </Tabs.List>

        <Tabs.Content value="events">
          <AgentEventStream events={events} />
        </Tabs.Content>

        <Tabs.Content value="state">
          <AgentStateView snapshot={snapshot} />
        </Tabs.Content>

        <Tabs.Content value="context">
          <AgentContextView snapshot={snapshot} />
        </Tabs.Content>

        <Tabs.Content value="tools">
          <AgentToolsView events={events} />
        </Tabs.Content>

        <Tabs.Content value="steps">
          <AgentStepsView runId={activeRunId} />
        </Tabs.Content>

        <Tabs.Content value="checkpoints">
          <AgentCheckpointsView runId={activeRunId} />
        </Tabs.Content>

        <Tabs.Content value="handoffs">
          <AgentHandoffsView runId={activeRunId} />
        </Tabs.Content>

        <Tabs.Content value="artifacts">
          <AgentArtifactsView runId={activeRunId} />
        </Tabs.Content>
      </Tabs.Root>
    </div>
  );
}
