import * as React from "react";
import { AlertCircle, CheckCircle2, Database, Loader2 } from "lucide-react";

import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import {
  getAgentRunContextSnapshot,
  type RunContextSnapshotVO
} from "@/services/runContextSnapshotService";

interface ContextSnapshotInspectorTabProps {
  agentRunId?: string;
}

type SnapshotObject = Record<string, unknown>;

function parseJsonObject(value?: string | null): SnapshotObject {
  if (!value) return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed as SnapshotObject : {};
  } catch {
    return {};
  }
}

function asRecord(value: unknown): SnapshotObject {
  return value && typeof value === "object" && !Array.isArray(value) ? value as SnapshotObject : {};
}

function asString(value: unknown): string | null {
  if (typeof value === "string" && value.trim()) return value.trim();
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return null;
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map(asString).filter((item): item is string => Boolean(item));
}

function isEmptyObject(value: SnapshotObject): boolean {
  return Object.keys(value).length === 0;
}

function firstObject(...values: SnapshotObject[]): SnapshotObject {
  return values.find((value) => !isEmptyObject(value)) ?? {};
}

function displayValue(value: unknown): string | null {
  const scalar = asString(value);
  if (scalar) return scalar;
  if (Array.isArray(value)) {
    const items = value.map(displayValue).filter((item): item is string => Boolean(item));
    return items.length > 0 ? items.join(", ") : null;
  }
  if (value && typeof value === "object") {
    return JSON.stringify(value);
  }
  return null;
}

function FieldRow({ label, value }: { label: string; value?: string | number | null }) {
  if (value === undefined || value === null || value === "") return null;
  return (
    <div className="flex items-start justify-between gap-3 py-1.5 text-xs">
      <span className="shrink-0" style={{ color: "var(--theme-text-muted)" }}>
        {label}
      </span>
      <span className="min-w-0 break-all text-right font-mono" style={{ color: "var(--theme-text-primary)" }}>
        {value}
      </span>
    </div>
  );
}

function ObjectSection({ title, data }: { title: string; data: SnapshotObject }) {
  if (isEmptyObject(data)) return null;
  return (
    <Section title={title}>
      {Object.entries(data).map(([key, value]) => (
        <FieldRow key={key} label={key} value={displayValue(value)} />
      ))}
    </Section>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section
      className="rounded-lg p-3"
      style={{
        backgroundColor: "var(--sh-workbench-panel-subtle)",
        border: "1px solid var(--sh-workbench-border)"
      }}
    >
      <h3 className="mb-2 text-xs font-semibold" style={{ color: "var(--theme-text-primary)" }}>
        {title}
      </h3>
      {children}
    </section>
  );
}

function ToolList({ toolIds }: { toolIds: string[] }) {
  if (toolIds.length === 0) {
    return <p className="text-xs" style={{ color: "var(--theme-text-muted)" }}>暂无工具快照</p>;
  }
  return (
    <div className="flex flex-wrap gap-1.5">
      {toolIds.map((toolId) => (
        <span
          key={toolId}
          className="rounded-md px-1.5 py-1 font-mono text-[11px]"
          style={{
            backgroundColor: "var(--sh-workbench-panel)",
            border: "1px solid var(--sh-workbench-border)",
            color: "var(--theme-text-secondary)"
          }}
        >
          {toolId}
        </span>
      ))}
    </div>
  );
}

export function ContextSnapshotInspectorTab({ agentRunId }: ContextSnapshotInspectorTabProps) {
  const [snapshot, setSnapshot] = React.useState<RunContextSnapshotVO | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!agentRunId) {
      setSnapshot(null);
      setError(null);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);
    getAgentRunContextSnapshot(agentRunId)
      .then((next) => {
        if (active) setSnapshot(next);
      })
      .catch(() => {
        if (active) {
          setSnapshot(null);
          setError("上下文快照加载失败");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [agentRunId]);

  if (!agentRunId) return <InspectorEmptyState />;

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-xs" style={{ color: "var(--theme-text-muted)" }}>
        <Loader2 className="h-4 w-4 animate-spin" />
        加载运行上下文...
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-xs" style={{ color: "rgb(239,68,68)" }}>
        <AlertCircle className="h-4 w-4" />
        {error}
      </div>
    );
  }

  if (!snapshot) return <InspectorEmptyState />;

  const snapshotJson = parseJsonObject(snapshot.snapshotJson);
  const traceContext = parseJsonObject(snapshot.traceContextJson);
  const executorConfig = firstObject(asRecord(snapshotJson.executorConfig), parseJsonObject(snapshot.executorConfigJson));
  const runProfile = asRecord(snapshotJson.runProfile);
  const profileModelConfig = asRecord(snapshotJson.profileModelConfig);
  const memoryScope = asRecord(snapshotJson.memoryScope);
  const guardrailConfig = asRecord(snapshotJson.guardrailConfig);
  const modelConfig = asRecord(snapshotJson.modelConfig);
  const roleCard = asRecord(snapshotJson.roleCard);
  const agentScope = asRecord(snapshotJson.agentScope);
  const toolIds = asStringArray(snapshotJson.toolIds);
  const mcpToolIds = asStringArray(snapshotJson.mcpToolIds);
  const a2aAgentIds = asStringArray(snapshotJson.a2aAgentIds);
  const engine = asString(snapshotJson.executorEngine) ?? snapshot.executorEngine;
  const runProfileId = asString(snapshotJson.runProfileId) ?? asString(snapshot.runProfileId);
  const branchLeafMessageId = asString(snapshotJson.branchLeafMessageId) ?? asString(snapshot.branchLeafMessageId);
  const studioTraceId = asString(agentScope.studioTraceId) ?? asString(traceContext.studioTraceId);

  return (
    <div className="space-y-3 p-3">
      <Section title="基础">
        <div className="mb-2 flex items-center gap-2">
          <CheckCircle2 className="h-3.5 w-3.5" style={{ color: "var(--sh-workbench-accent)" }} />
          <span className="font-mono text-xs font-semibold" style={{ color: "var(--theme-text-primary)" }}>
            {engine}
          </span>
        </div>
        <FieldRow label="Run ID" value={snapshot.runId} />
        <FieldRow label="会话" value={snapshot.conversationId} />
        <FieldRow label="分支叶子" value={branchLeafMessageId} />
        <FieldRow label="运行画像" value={runProfileId} />
        <FieldRow label="Trace" value={asString(traceContext.traceId)} />
        <FieldRow label="Studio Trace" value={studioTraceId} />
        <FieldRow label="创建" value={snapshot.createTime} />
      </Section>

      <Section title="模型与角色">
        <FieldRow label="模型" value={asString(modelConfig.modelId) ?? asString(modelConfig.model)} />
        <FieldRow label="温度" value={asString(modelConfig.temperature)} />
        <FieldRow label="角色" value={asString(roleCard.name) ?? asString(snapshot.roleCardId)} />
        <FieldRow label="高权限" value={asString(roleCard.higherPerm)} />
      </Section>

      <Section title="普通工具">
        <ToolList toolIds={toolIds} />
      </Section>

      <ObjectSection title="Run Profile" data={runProfile} />

      <ObjectSection title="Executor Config" data={executorConfig} />

      <ObjectSection title="Profile Model" data={profileModelConfig} />

      <ObjectSection title="Memory Scope" data={memoryScope} />

      <ObjectSection title="Guardrail" data={guardrailConfig} />

      <Section title="MCP 工具">
        <ToolList toolIds={mcpToolIds} />
      </Section>

      <Section title="A2A Agent">
        <ToolList toolIds={a2aAgentIds} />
      </Section>

      {(studioTraceId || asString(agentScope.nacosNamespace) || asString(agentScope.nacosGroup)) ? (
        <Section title="AgentScope">
          <FieldRow label="Studio Trace" value={studioTraceId} />
          <FieldRow label="Nacos Namespace" value={asString(agentScope.nacosNamespace)} />
          <FieldRow label="Nacos Group" value={asString(agentScope.nacosGroup)} />
        </Section>
      ) : null}

      <Section title="原始快照">
        <div className="flex items-center gap-2 pb-2 text-xs" style={{ color: "var(--theme-text-muted)" }}>
          <Database className="h-3.5 w-3.5" />
          保存于 Seahorse 统一运行上下文
        </div>
        <pre
          className="max-h-64 overflow-auto whitespace-pre-wrap break-words rounded-md p-2 font-mono text-[11px] leading-relaxed"
          style={{
            backgroundColor: "var(--sh-workbench-panel)",
            border: "1px solid var(--sh-workbench-border)",
            color: "var(--theme-text-secondary)"
          }}
        >
          {JSON.stringify(snapshotJson, null, 2)}
        </pre>
      </Section>
    </div>
  );
}
