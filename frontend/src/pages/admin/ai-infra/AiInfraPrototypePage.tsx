import { useMemo, useState, type ComponentType } from "react";
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  BadgeCheck,
  Boxes,
  BrainCircuit,
  CheckCircle2,
  CircleDot,
  Clock3,
  CloudCog,
  Code2,
  DatabaseZap,
  FileCheck2,
  Fingerprint,
  GitBranch,
  KeyRound,
  Layers3,
  LockKeyhole,
  Network,
  PauseCircle,
  Play,
  Radar,
  Route,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  SquareActivity,
  TerminalSquare,
  TimerReset,
  Workflow
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type ConsoleTab = "overview" | "runtime" | "factory" | "governance";
type Tone = "slate" | "blue" | "emerald" | "amber" | "rose";

type Metric = {
  label: string;
  value: string;
  detail: string;
  tone: Tone;
};

type Phase = {
  id: string;
  name: string;
  owner: string;
  status: "Ready" | "Design" | "Next" | "Later";
  progress: number;
};

type Capability = {
  label: string;
  value: string;
  icon: ComponentType<{ className?: string }>;
  tone: Tone;
};

type RunStep = {
  label: string;
  type: string;
  status: "done" | "running" | "blocked";
  time: string;
};

type AgentCard = {
  name: string;
  template: string;
  risk: string;
  status: string;
  tools: number;
};

const tabs: Array<{ value: ConsoleTab; label: string; icon: ComponentType<{ className?: string }> }> = [
  { value: "overview", label: "控制面", icon: SquareActivity },
  { value: "runtime", label: "运行时", icon: Workflow },
  { value: "factory", label: "Agent 工厂", icon: Boxes },
  { value: "governance", label: "治理", icon: ShieldCheck }
];

const metrics: Metric[] = [
  { label: "Agent 定义", value: "42", detail: "17 个已发布", tone: "blue" },
  { label: "运行中 Run", value: "218", detail: "12 个等待审批", tone: "emerald" },
  { label: "工具策略", value: "96", detail: "31 条高风险规则", tone: "amber" },
  { label: "评估门禁通过率", value: "87.4%", detail: "滚动 7 天", tone: "slate" }
];

const phases: Phase[] = [
  { id: "P0", name: "架构基线", owner: "Platform", status: "Ready", progress: 100 },
  { id: "P1", name: "Registry / Run Store", owner: "Runtime", status: "Design", progress: 68 },
  { id: "P2", name: "Tool Gateway", owner: "Security", status: "Design", progress: 54 },
  { id: "P3", name: "Durable Runtime", owner: "Runtime", status: "Next", progress: 22 },
  { id: "P4", name: "Context DB / ACL", owner: "Knowledge", status: "Next", progress: 18 },
  { id: "P5", name: "Connectors / Sandbox", owner: "Integration", status: "Later", progress: 8 },
  { id: "P6", name: "Agent Factory", owner: "Console", status: "Later", progress: 6 },
  { id: "P7", name: "A2A / Mesh", owner: "Platform", status: "Later", progress: 3 },
  { id: "P8", name: "Production Hardening", owner: "SRE", status: "Later", progress: 12 }
];

const capabilities: Capability[] = [
  { label: "Agent Registry", value: "Definition / Version", icon: BrainCircuit, tone: "blue" },
  { label: "Run Store", value: "Run / Step / Checkpoint", icon: Route, tone: "emerald" },
  { label: "Tool Gateway", value: "MCP / OpenAPI / Internal", icon: TerminalSquare, tone: "amber" },
  { label: "Policy Engine", value: "RBAC / ABAC / HITL", icon: LockKeyhole, tone: "rose" },
  { label: "Context DB", value: "RAG / Memory / Citation", icon: DatabaseZap, tone: "blue" },
  { label: "Agent Mesh", value: "Handoff / A2A", icon: Network, tone: "slate" }
];

const runSteps: RunStep[] = [
  { label: "Run created", type: "AgentRun", status: "done", time: "09:41:12" },
  { label: "ContextPack built", type: "RAG + Memory", status: "done", time: "09:41:13" },
  { label: "Policy decision", type: "Tool Gateway", status: "done", time: "09:41:16" },
  { label: "Approval required", type: "HITL", status: "blocked", time: "09:41:18" },
  { label: "Resume checkpoint", type: "Runtime", status: "running", time: "pending" }
];

const agents: AgentCard[] = [
  { name: "Knowledge Curator", template: "knowledge-curator", risk: "MEDIUM", status: "Draft", tools: 5 },
  { name: "Procurement Assistant", template: "workflow-assistant", risk: "HIGH", status: "Review", tools: 7 },
  { name: "Compliance Reviewer", template: "compliance-reviewer", risk: "HIGH", status: "Published", tools: 4 },
  { name: "Data Analyst", template: "data-analyst", risk: "MEDIUM", status: "Canary", tools: 6 }
];

const toneClasses: Record<Tone, { bg: string; text: string; border: string; icon: string }> = {
  slate: {
    bg: "bg-slate-50",
    text: "text-slate-700",
    border: "border-slate-200",
    icon: "bg-slate-900 text-white"
  },
  blue: {
    bg: "bg-sky-50",
    text: "text-sky-700",
    border: "border-sky-200",
    icon: "bg-sky-600 text-white"
  },
  emerald: {
    bg: "bg-emerald-50",
    text: "text-emerald-700",
    border: "border-emerald-200",
    icon: "bg-emerald-600 text-white"
  },
  amber: {
    bg: "bg-amber-50",
    text: "text-amber-700",
    border: "border-amber-200",
    icon: "bg-amber-500 text-white"
  },
  rose: {
    bg: "bg-rose-50",
    text: "text-rose-700",
    border: "border-rose-200",
    icon: "bg-rose-600 text-white"
  }
};

const statusStyle: Record<Phase["status"], string> = {
  Ready: "border-emerald-200 bg-emerald-50 text-emerald-700",
  Design: "border-sky-200 bg-sky-50 text-sky-700",
  Next: "border-amber-200 bg-amber-50 text-amber-700",
  Later: "border-slate-200 bg-slate-50 text-slate-500"
};

const stepStyle: Record<RunStep["status"], string> = {
  done: "bg-emerald-500",
  running: "bg-sky-500",
  blocked: "bg-amber-500"
};

function SegmentButton({
  active,
  icon: Icon,
  label,
  onClick
}: {
  active: boolean;
  icon: ComponentType<{ className?: string }>;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "inline-flex h-10 items-center gap-2 rounded-lg px-3 text-sm font-medium transition active:translate-y-px",
        active ? "bg-slate-950 text-white shadow-sm" : "text-slate-500 hover:bg-white hover:text-slate-900"
      )}
    >
      <Icon className="h-4 w-4" />
      <span>{label}</span>
    </button>
  );
}

function MetricTile({ item }: { item: Metric }) {
  const tone = toneClasses[item.tone];
  return (
    <div className={cn("rounded-xl border bg-white p-4", tone.border)}>
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs font-medium uppercase text-slate-400">{item.label}</p>
        <span className={cn("h-2 w-2 rounded-full", tone.icon)} />
      </div>
      <p className="mt-3 font-mono text-3xl font-semibold tracking-tight text-slate-950">{item.value}</p>
      <p className="mt-1 text-xs text-slate-500">{item.detail}</p>
    </div>
  );
}

function PhaseRail() {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-slate-900">阶段路线</h2>
          <p className="mt-1 text-xs text-slate-500">AI Infra 交付泳道</p>
        </div>
        <GitBranch className="h-5 w-5 text-slate-400" />
      </div>
      <div className="mt-4 space-y-3">
        {phases.map((phase) => (
          <div key={phase.id} className="rounded-xl border border-slate-100 bg-slate-50/60 p-3">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs font-semibold text-slate-500">{phase.id}</span>
                  <p className="truncate text-sm font-semibold text-slate-900">{phase.name}</p>
                </div>
                <p className="mt-1 text-xs text-slate-500">{phase.owner}</p>
              </div>
              <span className={cn("rounded-full border px-2 py-0.5 text-[11px] font-medium", statusStyle[phase.status])}>
                {phase.status}
              </span>
            </div>
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-white">
              <div
                className="h-full rounded-full bg-slate-900 transition-[width] duration-500"
                style={{ width: `${phase.progress}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function CapabilityMap() {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-base font-semibold text-slate-950">平台能力地图</h2>
          <p className="mt-1 text-sm text-slate-500">Registry、Runtime、Context、Tool、Governance 与 Mesh</p>
        </div>
        <Button className="w-fit gap-2 bg-slate-950 text-white hover:bg-slate-800">
          <Play className="h-4 w-4" />
          试运行
        </Button>
      </div>
      <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {capabilities.map((capability) => {
          const Icon = capability.icon;
          const tone = toneClasses[capability.tone];
          return (
            <div
              key={capability.label}
              className={cn(
                "group rounded-xl border p-4 transition duration-200 hover:-translate-y-0.5 hover:bg-white",
                tone.bg,
                tone.border
              )}
            >
              <div className="flex items-center gap-3">
                <div className={cn("flex h-10 w-10 items-center justify-center rounded-lg", tone.icon)}>
                  <Icon className="h-5 w-5" />
                </div>
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-slate-950">{capability.label}</p>
                  <p className="truncate text-xs text-slate-500">{capability.value}</p>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function RuntimeTopology() {
  const nodes = [
    { label: "Agent Run", icon: Activity, tone: "blue" as Tone },
    { label: "ContextPack", icon: DatabaseZap, tone: "emerald" as Tone },
    { label: "Tool Gateway", icon: TerminalSquare, tone: "amber" as Tone },
    { label: "Policy", icon: ShieldCheck, tone: "rose" as Tone },
    { label: "Audit", icon: FileCheck2, tone: "slate" as Tone }
  ];

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-slate-950">运行拓扑</h2>
          <p className="mt-1 text-sm text-slate-500">带策略门禁的 Agent Run 路径</p>
        </div>
        <Radar className="h-5 w-5 text-slate-400" />
      </div>
      <div className="mt-6 grid gap-3 lg:grid-cols-5">
        {nodes.map((node, index) => {
          const Icon = node.icon;
          const tone = toneClasses[node.tone];
          return (
            <div key={node.label} className="relative">
              <div className={cn("rounded-xl border p-4", tone.bg, tone.border)}>
                <div className={cn("flex h-10 w-10 items-center justify-center rounded-lg", tone.icon)}>
                  <Icon className="h-5 w-5" />
                </div>
                <p className="mt-4 text-sm font-semibold text-slate-950">{node.label}</p>
                <p className="mt-1 font-mono text-[11px] uppercase text-slate-400">stage {index + 1}</p>
              </div>
              {index < nodes.length - 1 ? (
                <ArrowRight className="absolute -right-5 top-1/2 hidden h-4 w-4 -translate-y-1/2 text-slate-300 lg:block" />
              ) : null}
            </div>
          );
        })}
      </div>
    </section>
  );
}

function RunTimeline() {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-slate-950">Run 时间线</h2>
          <p className="mt-1 text-sm text-slate-500">run_8dc1 的 checkpoint 恢复路径</p>
        </div>
        <span className="rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-medium text-amber-700">
          WAITING_APPROVAL
        </span>
      </div>
      <div className="mt-5 space-y-4">
        {runSteps.map((step, index) => (
          <div key={step.label} className="grid grid-cols-[24px_1fr_auto] gap-3">
            <div className="flex flex-col items-center">
              <span className={cn("mt-1 h-3 w-3 rounded-full", stepStyle[step.status])} />
              {index < runSteps.length - 1 ? <span className="mt-1 h-full w-px bg-slate-200" /> : null}
            </div>
            <div className="min-w-0 pb-2">
              <p className="truncate text-sm font-semibold text-slate-900">{step.label}</p>
              <p className="mt-1 text-xs text-slate-500">{step.type}</p>
            </div>
            <p className="font-mono text-xs text-slate-400">{step.time}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function AgentFactoryPanel() {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-slate-950">Agent 工厂</h2>
          <p className="mt-1 text-sm text-slate-500">模板、派生 Agent 与发布门禁</p>
        </div>
        <Sparkles className="h-5 w-5 text-slate-400" />
      </div>
      <div className="mt-5 grid gap-3 lg:grid-cols-2">
        {agents.map((agent) => (
          <div key={agent.name} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-slate-950">{agent.name}</p>
                <p className="mt-1 font-mono text-xs text-slate-500">{agent.template}</p>
              </div>
              <span className="rounded-full border border-slate-200 bg-white px-2 py-0.5 text-[11px] font-medium text-slate-600">
                {agent.status}
              </span>
            </div>
            <div className="mt-4 flex items-center justify-between text-xs text-slate-500">
              <span>Risk {agent.risk}</span>
              <span>{agent.tools} tools</span>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function GovernancePanel() {
  const checks = [
    { label: "Tool Gateway enforced", icon: CheckCircle2, tone: "emerald" as Tone },
    { label: "Critical actions require HITL", icon: PauseCircle, tone: "amber" as Tone },
    { label: "Secrets stored by reference", icon: KeyRound, tone: "blue" as Tone },
    { label: "A2A remote agents disabled by default", icon: Network, tone: "slate" as Tone }
  ];

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-slate-950">治理门禁</h2>
          <p className="mt-1 text-sm text-slate-500">策略、审批、审计与配额控制</p>
        </div>
        <SlidersHorizontal className="h-5 w-5 text-slate-400" />
      </div>
      <div className="mt-5 space-y-3">
        {checks.map((check) => {
          const Icon = check.icon;
          const tone = toneClasses[check.tone];
          return (
            <div key={check.label} className="flex items-center gap-3 rounded-xl border border-slate-100 bg-slate-50 p-3">
              <div className={cn("flex h-8 w-8 items-center justify-center rounded-lg", tone.icon)}>
                <Icon className="h-4 w-4" />
              </div>
              <p className="text-sm font-medium text-slate-800">{check.label}</p>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function RightRail() {
  return (
    <aside className="space-y-4">
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-950">审批队列</h2>
          <Clock3 className="h-4 w-4 text-slate-400" />
        </div>
        <div className="mt-4 space-y-3">
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-3">
            <p className="text-sm font-semibold text-amber-900">Send supplier request</p>
            <p className="mt-1 text-xs text-amber-700">Procurement Assistant · EXTERNAL_SEND</p>
          </div>
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-3">
            <p className="text-sm font-semibold text-rose-900">Forget semantic memory</p>
            <p className="mt-1 text-xs text-rose-700">Knowledge Curator · DELETE</p>
          </div>
        </div>
      </section>
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-950">成本护栏</h2>
          <TimerReset className="h-4 w-4 text-slate-400" />
        </div>
        <div className="mt-4 space-y-4">
          <div>
            <div className="flex items-center justify-between text-xs text-slate-500">
              <span>租户日配额</span>
              <span className="font-mono">62.8%</span>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full w-[62.8%] rounded-full bg-slate-950" />
            </div>
          </div>
          <div>
            <div className="flex items-center justify-between text-xs text-slate-500">
              <span>高风险 Run 预算</span>
              <span className="font-mono">41.3%</span>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full w-[41.3%] rounded-full bg-emerald-500" />
            </div>
          </div>
        </div>
      </section>
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-950">发布门禁</h2>
          <BadgeCheck className="h-4 w-4 text-slate-400" />
        </div>
        <div className="mt-4 grid grid-cols-2 gap-2">
          {["Eval", "Owner", "Quota", "ACL"].map((item) => (
            <div key={item} className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700">
              {item}
            </div>
          ))}
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-700">
            HITL
          </div>
          <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-medium text-slate-500">
            Canary
          </div>
        </div>
      </section>
    </aside>
  );
}

function TabContent({ tab }: { tab: ConsoleTab }) {
  if (tab === "runtime") {
    return (
      <div className="space-y-4">
        <RuntimeTopology />
        <RunTimeline />
      </div>
    );
  }

  if (tab === "factory") {
    return (
      <div className="space-y-4">
        <AgentFactoryPanel />
        <CapabilityMap />
      </div>
    );
  }

  if (tab === "governance") {
    return (
      <div className="space-y-4">
        <GovernancePanel />
        <RuntimeTopology />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <CapabilityMap />
      <RuntimeTopology />
      <RunTimeline />
    </div>
  );
}

export function AiInfraPrototypePage() {
  const [activeTab, setActiveTab] = useState<ConsoleTab>("overview");
  const activeLabel = useMemo(() => tabs.find((tab) => tab.value === activeTab)?.label ?? "控制面", [activeTab]);

  return (
    <div className="admin-page ai-infra-page">
      <header className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
          <div className="max-w-3xl">
            <div className="flex flex-wrap items-center gap-2">
              <span className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium text-slate-600">
                <CloudCog className="h-3.5 w-3.5" />
                AI Infra 控制台
              </span>
              <span className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
                <CircleDot className="h-3.5 w-3.5" />
                平台方案
              </span>
            </div>
            <h1 className="mt-4 text-3xl font-semibold tracking-tight text-slate-950 md:text-4xl">
              Seahorse 企业级 AI 控制面
            </h1>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-500">
              将 Agent Registry、持久运行、工具治理、上下文、审批、评估和 Mesh 收敛到一个运营界面。
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" className="gap-2">
              <FileCheck2 className="h-4 w-4" />
              导出方案
            </Button>
            <Button className="gap-2 bg-slate-950 text-white hover:bg-slate-800">
              <Code2 className="h-4 w-4" />
              API 地图
            </Button>
          </div>
        </div>
        <div className="mt-6 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          {metrics.map((metric) => (
            <MetricTile key={metric.label} item={metric} />
          ))}
        </div>
      </header>

      <div className="grid gap-5 xl:grid-cols-[280px_minmax(0,1fr)_300px]">
        <PhaseRail />

        <main className="min-w-0 space-y-4">
          <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-100/70 p-2 md:flex-row md:items-center md:justify-between">
            <div className="px-2">
              <p className="text-sm font-semibold text-slate-900">{activeLabel}</p>
              <p className="text-xs text-slate-500">方案对齐视图</p>
            </div>
            <div className="flex flex-wrap gap-1">
              {tabs.map((tab) => (
                <SegmentButton
                  key={tab.value}
                  active={activeTab === tab.value}
                  icon={tab.icon}
                  label={tab.label}
                  onClick={() => setActiveTab(tab.value)}
                />
              ))}
            </div>
          </div>
          <TabContent tab={activeTab} />
        </main>

        <RightRail />
      </div>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-950 text-white">
              <AlertTriangle className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-slate-950">实施边界</h2>
              <p className="mt-1 text-sm text-slate-500">
                Run Store、Tool Gateway 和 HITL 完成前，生产写操作工具保持禁用。
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2 text-xs">
            <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-medium text-slate-600">
              Phase 1 先于外部工具
            </span>
            <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-medium text-slate-600">
              Phase 3 先于写操作
            </span>
            <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-medium text-slate-600">
              Phase 7 晚于治理闭环
            </span>
          </div>
        </div>
      </section>
    </div>
  );
}
