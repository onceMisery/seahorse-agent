import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  Braces,
  Calendar,
  CheckCircle2,
  Clock,
  Copy,
  Cpu,
  Database,
  FileText,
  Gauge,
  GitBranch,
  Hash,
  Info,
  Layers3,
  Loader2,
  MessageSquareText,
  RefreshCw,
  Search,
  Settings,
  TimerReset,
  User,
  XCircle,
  Zap
} from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { getRagTraceDetail, type RagTraceDetail, type RagTraceNode, type RagTraceRun } from "@/services/ragTraceService";
import { getErrorMessage } from "@/utils/error";
import {
  clamp,
  formatDateTime,
  formatDuration,
  normalizeStatus,
  resolveNodeDuration,
  statusBadgeVariant,
  statusLabel,
  toTimestamp,
  type TimelineNode
} from "@/pages/admin/traces/traceUtils";

type JsonRecord = Record<string, unknown>;

type EnrichedNode = TimelineNode & {
  depthValue: number;
  resolvedDurationMs: number;
  offsetMs: number;
  leftPercent: number;
  widthPercent: number;
  startTs: number;
  endTs: number;
};

type StageKind = "retrieval" | "memory" | "planning" | "model" | "tool" | "default";

const STAGE_META: Record<StageKind, { label: string; tone: string; icon: React.ComponentType<{ className?: string }> }> = {
  retrieval: { label: "检索阶段", tone: "bg-cyan-50 text-cyan-700 border-cyan-200", icon: Search },
  memory: { label: "记忆阶段", tone: "bg-violet-50 text-violet-700 border-violet-200", icon: Database },
  planning: { label: "编排阶段", tone: "bg-blue-50 text-blue-700 border-blue-200", icon: GitBranch },
  model: { label: "模型阶段", tone: "bg-emerald-50 text-emerald-700 border-emerald-200", icon: MessageSquareText },
  tool: { label: "工具阶段", tone: "bg-amber-50 text-amber-700 border-amber-200", icon: Zap },
  default: { label: "执行阶段", tone: "bg-slate-50 text-slate-700 border-slate-200", icon: Activity }
};

const STATUS_COLORS: Record<string, { dot: string; bar: string; text: string; border: string }> = {
  success: { dot: "bg-emerald-500", bar: "bg-emerald-400", text: "text-emerald-700", border: "border-emerald-200" },
  failed: { dot: "bg-red-500", bar: "bg-red-400", text: "text-red-700", border: "border-red-200" },
  running: { dot: "bg-amber-500", bar: "bg-amber-400", text: "text-amber-700", border: "border-amber-200" },
  default: { dot: "bg-slate-300", bar: "bg-slate-300", text: "text-slate-600", border: "border-slate-200" }
};

const decodeTraceId = (value?: string): string => {
  if (!value) return "";
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
};

const copyToClipboard = (text: string, label: string) => {
  navigator.clipboard.writeText(text).then(() => {
    toast.success(`${label} 已复制`);
  }).catch(() => {
    toast.error("复制失败");
  });
};

function isRecord(value: unknown): value is JsonRecord {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function parseExtraData(value?: string | null): unknown {
  if (!value?.trim()) return null;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return JSON.stringify(value, null, 2);
}

function compact(value: unknown, max = 240): string {
  const text = displayValue(value).trim();
  if (!text || text === "-") return "-";
  return text.length > max ? `${text.slice(0, max)}...` : text;
}

function pickValue(record: unknown, keys: string[]): unknown {
  if (!isRecord(record)) return undefined;
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && value !== "") {
      return value;
    }
  }
  return undefined;
}

function formatJson(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "string") {
    const parsed = parseExtraData(value);
    return typeof parsed === "string" ? parsed : JSON.stringify(parsed, null, 2);
  }
  return JSON.stringify(value, null, 2);
}

function shortId(value?: string | null): string {
  if (!value) return "-";
  return value.length > 24 ? `${value.slice(0, 10)}...${value.slice(-6)}` : value;
}

function nodeTitle(node?: Pick<RagTraceNode, "nodeName" | "methodName" | "nodeId"> | null) {
  return node?.nodeName || node?.methodName || node?.nodeId || "-";
}

function classifyNode(node?: Pick<RagTraceNode, "nodeName" | "nodeType" | "methodName" | "className"> | null): StageKind {
  const text = `${node?.nodeType || ""} ${node?.nodeName || ""} ${node?.methodName || ""} ${node?.className || ""}`.toLowerCase();
  if (text.includes("retrieval") || text.includes("search") || text.includes("vector")) return "retrieval";
  if (text.includes("memory")) return "memory";
  if (text.includes("rewrite") || text.includes("intent") || text.includes("guidance") || text.includes("optimize")) return "planning";
  if (text.includes("model") || text.includes("llm") || text.includes("chat")) return "model";
  if (text.includes("tool")) return "tool";
  return "default";
}

function statusColors(status?: string | null) {
  return STATUS_COLORS[normalizeStatus(status) || "default"] || STATUS_COLORS.default;
}

function nodeInput(extra: unknown, fallback?: unknown) {
  return pickValue(extra, ["input", "prompt", "question", "query", "request", "arguments", "argumentsJson"]) ?? fallback;
}

function nodeOutput(extra: unknown, fallback?: unknown) {
  return pickValue(extra, ["output", "result", "response", "answer", "completion", "content"]) ?? fallback;
}

function runInput(extra: unknown) {
  return pickValue(extra, ["input", "prompt", "question", "query", "userInput", "request"]);
}

function runOutput(extra: unknown) {
  return pickValue(extra, ["output", "answer", "response", "completion", "content"]);
}

function runModel(extra: unknown) {
  return pickValue(extra, ["model", "modelId", "modelName", "provider"]);
}

function tokenUsage(extra: unknown) {
  const value = pickValue(extra, ["tokenUsage", "usage", "tokens"]);
  return isRecord(value) ? value : null;
}

function modelParams(extra: unknown) {
  const value = pickValue(extra, ["modelParams", "modelParameters", "parameters", "config"]);
  return isRecord(value) ? value : null;
}

function MetricItem({
  icon: Icon,
  label,
  value,
  variant = "default"
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string | number;
  variant?: "default" | "success" | "error" | "warning" | "primary";
}) {
  const styles = {
    default: "text-slate-600",
    success: "text-emerald-600",
    error: "text-red-600",
    warning: "text-amber-600",
    primary: "text-blue-600"
  };

  return (
    <div className="flex min-w-[132px] items-center gap-2 px-4 py-3">
      <Icon className={cn("h-4 w-4", styles[variant])} />
      <span className={cn("font-mono text-lg font-semibold tabular-nums", styles[variant])}>{value}</span>
      <span className="text-xs text-slate-500">{label}</span>
    </div>
  );
}

function TimeScale({ totalMs }: { totalMs: number }) {
  const ticks = [0, 25, 50, 75, 100];
  return (
    <div className="relative h-7 border-b border-slate-200">
      {ticks.map((percent) => (
        <div
          key={percent}
          className="absolute bottom-0 top-0 flex flex-col items-center"
          style={{ left: `${percent}%`, transform: "translateX(-50%)" }}
        >
          <div className="h-2 w-px bg-slate-300" />
          <span className="mt-0.5 font-mono text-[10px] text-slate-400">
            {formatDuration((totalMs * percent) / 100)}
          </span>
        </div>
      ))}
    </div>
  );
}

function StageBadge({ node }: { node: RagTraceNode }) {
  const stage = STAGE_META[classifyNode(node)];
  const Icon = stage.icon;
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs", stage.tone)}>
      <Icon className="h-3 w-3" />
      {stage.label}
    </span>
  );
}

function WaterfallRow({
  node,
  selected,
  isTopSlowest,
  onSelect
}: {
  node: EnrichedNode;
  selected: boolean;
  isTopSlowest?: boolean;
  onSelect: () => void;
}) {
  const colors = statusColors(node.status);
  const title = nodeTitle(node);

  return (
    <button
      type="button"
      className={cn(
        "grid w-full grid-cols-[minmax(190px,1fr)_136px_minmax(220px,1.6fr)_108px] gap-4 px-4 py-3 text-left transition-colors",
        "hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-200",
        selected && "bg-blue-50/70",
        isTopSlowest && !selected && "bg-amber-50/50"
      )}
      aria-pressed={selected}
      onClick={onSelect}
    >
      <div
        className="flex min-w-0 items-center gap-2"
        style={{ paddingLeft: `${Math.min(node.depthValue, 6) * 16}px` }}
      >
        <span className={cn("h-2.5 w-2.5 shrink-0 rounded-full", colors.dot)} />
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-slate-800" title={title}>{title}</p>
          <p className="truncate font-mono text-[11px] text-slate-400" title={node.nodeId}>{shortId(node.nodeId)}</p>
        </div>
        {isTopSlowest ? <Zap className="h-3.5 w-3.5 shrink-0 text-amber-500" /> : null}
      </div>

      <div className="flex items-center">
        <StageBadge node={node} />
      </div>

      <div className="flex items-center">
        <div className="relative h-7 w-full overflow-hidden rounded-md bg-slate-50">
          {[25, 50, 75].map((percent) => (
            <div
              key={percent}
              className="absolute bottom-0 top-0 w-px bg-slate-200"
              style={{ left: `${percent}%` }}
            />
          ))}
          <div
            className={cn("absolute bottom-1 top-1 rounded-md", colors.bar)}
            style={{
              left: `${node.leftPercent}%`,
              width: `${Math.max(node.widthPercent, 0.6)}%`,
              minWidth: "5px"
            }}
            title={`${title} - ${formatDuration(node.resolvedDurationMs)}`}
          />
        </div>
      </div>

      <div className="text-right">
        <p className="font-mono text-sm font-semibold text-slate-800">{formatDuration(node.resolvedDurationMs)}</p>
        <p className="font-mono text-[10px] text-slate-400">@{formatDuration(node.offsetMs)}</p>
      </div>
    </button>
  );
}

function CodeBlock({ value, empty = "暂无数据" }: { value: unknown; empty?: string }) {
  const text = formatJson(value);
  return (
    <pre className="max-h-72 overflow-auto rounded-lg border border-slate-200 bg-slate-950 p-3 font-mono text-xs leading-relaxed text-slate-100">
      {text === "-" ? empty : text}
    </pre>
  );
}

function KeyValue({ label, value, mono = false }: { label: string; value: unknown; mono?: boolean }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
      <p className="text-[11px] text-slate-500">{label}</p>
      <p className={cn("mt-1 break-words text-sm text-slate-800", mono && "font-mono text-xs")}>{displayValue(value)}</p>
    </div>
  );
}

function InsightCard({
  icon: Icon,
  label,
  value,
  detail
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  detail: string;
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
        <Icon className="h-4 w-4 text-slate-400" />
        {label}
      </div>
      <p className="mt-3 text-lg font-semibold text-slate-900">{value}</p>
      <p className="mt-1 line-clamp-2 text-xs text-slate-500">{detail}</p>
    </div>
  );
}

function RunOverview({
  run,
  nodes,
  runExtra,
  slowestNode
}: {
  run: RagTraceRun;
  nodes: EnrichedNode[];
  runExtra: unknown;
  slowestNode?: EnrichedNode;
}) {
  const model = runModel(runExtra);
  const usage = tokenUsage(runExtra);
  const params = modelParams(runExtra);
  const retrievalCount = nodes.filter((node) => classifyNode(node) === "retrieval").length;
  const errorCount = nodes.filter((node) => normalizeStatus(node.status) === "failed").length;
  const topLevelDuration = nodes
    .filter((node) => Number(node.depth ?? 0) === 0)
    .reduce((sum, node) => sum + node.resolvedDurationMs, 0);

  return (
    <Card className="border-slate-200 shadow-sm">
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="flex items-center gap-2 text-base text-slate-900">
              <Layers3 className="h-4 w-4 text-blue-600" />
              Agent 调用概览
            </CardTitle>
            <p className="mt-1 text-sm text-slate-500">把一次 Agent 调用拆成可诊断的阶段、耗时、上下文与原始属性。</p>
          </div>
          <Badge variant={statusBadgeVariant(run.status)}>{statusLabel(run.status)}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <InsightCard
            icon={TimerReset}
            label="瓶颈 Span"
            value={slowestNode ? formatDuration(slowestNode.resolvedDurationMs) : "-"}
            detail={slowestNode ? nodeTitle(slowestNode) : "暂无节点"}
          />
          <InsightCard
            icon={Search}
            label="检索阶段"
            value={`${retrievalCount} 个`}
            detail={retrievalCount > 0 ? "包含向量检索、关键词检索或检索通道节点" : "当前链路没有检索节点"}
          />
          <InsightCard
            icon={Gauge}
            label="Top-level 累计"
            value={formatDuration(topLevelDuration)}
            detail="根级阶段耗时之和，用于快速判断主流程分布"
          />
          <InsightCard
            icon={AlertTriangle}
            label="异常节点"
            value={`${errorCount} 个`}
            detail={errorCount > 0 ? "请优先查看失败 Span 的错误信息" : "本次链路没有失败节点"}
          />
        </div>

        <div className="grid gap-3 lg:grid-cols-[minmax(0,1.2fr)_minmax(280px,0.8fr)]">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
            <div className="mb-2 flex items-center gap-2 text-xs font-medium text-slate-500">
              <MessageSquareText className="h-4 w-4" />
              输入 / 输出
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <div>
                <p className="mb-1 text-xs text-slate-500">Input</p>
                <CodeBlock value={runInput(runExtra)} empty="暂无运行输入，可在 trace extra_data 中写入 input/prompt/query 字段。" />
              </div>
              <div>
                <p className="mb-1 text-xs text-slate-500">Output</p>
                <CodeBlock value={runOutput(runExtra)} empty="暂无运行输出，可在 trace extra_data 中写入 output/answer/response 字段。" />
              </div>
            </div>
          </div>

          <div className="space-y-3">
            <div className="rounded-lg border border-slate-200 bg-white p-3">
              <div className="mb-2 flex items-center gap-2 text-xs font-medium text-slate-500">
                <Cpu className="h-4 w-4" />
                模型信息
              </div>
              <div className="space-y-2">
                <KeyValue label="入口方法" value={run.entryMethod} mono />
                <KeyValue label="模型 / Provider" value={model || "-"} />
              </div>
            </div>

            {usage ? (
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3">
                <div className="mb-2 flex items-center gap-2 text-xs font-medium text-emerald-700">
                  <Zap className="h-4 w-4" />
                  Token 使用量
                </div>
                <div className="space-y-1.5 text-sm">
                  {isRecord(usage) && Object.entries(usage).map(([key, value]) => (
                    <div key={key} className="flex justify-between">
                      <span className="text-emerald-600">{key}:</span>
                      <span className="font-mono font-semibold text-emerald-800">{String(value)}</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : null}

            {params ? (
              <div className="rounded-lg border border-blue-200 bg-blue-50 p-3">
                <div className="mb-2 flex items-center gap-2 text-xs font-medium text-blue-700">
                  <Settings className="h-4 w-4" />
                  模型参数
                </div>
                <div className="max-h-32 space-y-1.5 overflow-y-auto text-sm">
                  {isRecord(params) && Object.entries(params).map(([key, value]) => (
                    <div key={key} className="flex justify-between gap-2">
                      <span className="truncate text-blue-600">{key}:</span>
                      <span className="font-mono text-xs text-blue-800">{compact(value, 40)}</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function SpanDetailPanel({ node }: { node?: EnrichedNode }) {
  const extra = parseExtraData(node?.extraData);
  const stage = node ? STAGE_META[classifyNode(node)] : STAGE_META.default;
  const StageIcon = stage.icon;

  if (!node) {
    return (
      <Card className="border-slate-200 shadow-sm">
        <CardContent className="py-12 text-center text-slate-500">
          <Info className="mx-auto mb-3 h-8 w-8 text-slate-300" />
          选择左侧 Span 查看详情
        </CardContent>
      </Card>
    );
  }

  const input = nodeInput(extra);
  const output = nodeOutput(extra);
  const usage = tokenUsage(extra);
  const params = modelParams(extra);
  const model = pickValue(extra, ["model", "modelId", "modelName"]);
  const raw = {
    ...node,
    extraData: extra
  };

  return (
    <Card className="sticky top-4 border-slate-200 shadow-sm">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <CardTitle className="flex items-center gap-2 text-base text-slate-900">
              <Activity className="h-4 w-4 text-blue-600" />
              Span 详情
            </CardTitle>
            <p className="mt-2 truncate text-lg font-semibold text-slate-900" title={nodeTitle(node)}>{nodeTitle(node)}</p>
          </div>
          <Badge variant={statusBadgeVariant(node.status)}>{statusLabel(node.status)}</Badge>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <span className={cn("inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs", stage.tone)}>
            <StageIcon className="h-3 w-3" />
            {stage.label}
          </span>
          <span className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-slate-50 px-2 py-1 font-mono text-xs text-slate-700">
            <Clock className="h-3 w-3" />
            {formatDuration(node.resolvedDurationMs)}
          </span>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {node.errorMessage ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <p className="font-medium">错误</p>
            <p className="mt-1">{node.errorMessage}</p>
          </div>
        ) : null}

        <section>
          <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-900">
            <MessageSquareText className="h-4 w-4 text-slate-500" />
            输入 / 输出
          </h3>
          <div className="space-y-3">
            <div>
              <p className="mb-1 text-xs text-slate-500">Input</p>
              <CodeBlock value={input} empty="暂无节点输入" />
            </div>
            <div>
              <p className="mb-1 text-xs text-slate-500">Output</p>
              <CodeBlock value={output} empty="暂无节点输出" />
            </div>
          </div>
        </section>

        {(usage || params || model) ? (
          <section>
            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-900">
              <Cpu className="h-4 w-4 text-slate-500" />
              模型与 Token
            </h3>
            <div className="space-y-2">
              {model ? <KeyValue label="Model" value={model} mono /> : null}
              {usage ? (
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-2">
                  <p className="mb-1.5 text-xs font-medium text-emerald-700">Token 使用量</p>
                  <div className="space-y-1 text-xs">
                    {isRecord(usage) && Object.entries(usage).map(([key, value]) => (
                      <div key={key} className="flex justify-between">
                        <span className="text-emerald-600">{key}:</span>
                        <span className="font-mono font-semibold text-emerald-800">{String(value)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
              {params ? (
                <div className="rounded-lg border border-blue-200 bg-blue-50 p-2">
                  <p className="mb-1.5 text-xs font-medium text-blue-700">模型参数</p>
                  <div className="max-h-28 space-y-1 overflow-y-auto text-xs">
                    {isRecord(params) && Object.entries(params).map(([key, value]) => (
                      <div key={key} className="flex justify-between gap-2">
                        <span className="truncate text-blue-600">{key}:</span>
                        <span className="font-mono text-blue-800">{compact(value, 30)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          </section>
        ) : null}

        <section>
          <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-900">
            <FileText className="h-4 w-4 text-slate-500" />
            属性
          </h3>
          <div className="grid gap-2">
            <KeyValue label="Node ID" value={node.nodeId} mono />
            <KeyValue label="Parent" value={node.parentNodeId || "-"} mono />
            <KeyValue label="Class" value={node.className || "-"} mono />
            <KeyValue label="Method" value={node.methodName || "-"} mono />
            <KeyValue label="Start / End" value={`${formatDateTime(node.startTime)} -> ${formatDateTime(node.endTime)}`} mono />
            <KeyValue label="Offset / Depth" value={`${formatDuration(node.offsetMs)} / ${node.depthValue}`} mono />
          </div>
        </section>

        <section>
          <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-900">
            <Braces className="h-4 w-4 text-slate-500" />
            原始数据
          </h3>
          <CodeBlock value={raw} />
        </section>
      </CardContent>
    </Card>
  );
}

function RawRunPanel({ run, nodes, runExtra }: { run: RagTraceRun; nodes: EnrichedNode[]; runExtra: unknown }) {
  return (
    <Card className="border-slate-200 shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base text-slate-900">
          <Braces className="h-4 w-4 text-blue-600" />
          原始 Trace 数据
        </CardTitle>
      </CardHeader>
      <CardContent>
        <CodeBlock value={{ run: { ...run, extraData: runExtra }, nodes }} />
      </CardContent>
    </Card>
  );
}

export function RagTraceDetailPage() {
  const params = useParams<{ traceId: string }>();
  const traceId = decodeTraceId(params.traceId);
  const detailRequestRef = useRef(0);
  const [detail, setDetail] = useState<RagTraceDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  const loadDetail = async (nextTraceId: string) => {
    if (!nextTraceId) return;
    const requestId = ++detailRequestRef.current;
    setDetailLoading(true);
    try {
      const result = await getRagTraceDetail(nextTraceId);
      if (detailRequestRef.current !== requestId) return;
      setDetail(result);
    } catch (error) {
      if (detailRequestRef.current !== requestId) return;
      toast.error(getErrorMessage(error, "加载链路详情失败"));
      console.error(error);
      setDetail(null);
    } finally {
      if (detailRequestRef.current !== requestId) return;
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    if (!traceId) {
      detailRequestRef.current += 1;
      setDetail(null);
      setDetailLoading(false);
      return;
    }
    loadDetail(traceId);
  }, [traceId]);

  const selectedRun = detail?.run || null;

  const timeline = useMemo(() => {
    const nodes = detail?.nodes || [];
    if (!nodes.length) return { totalWindowMs: 0, nodes: [] as EnrichedNode[] };

    const normalized = nodes.map((node) => {
      const startTs = toTimestamp(node.startTime);
      const endTs = toTimestamp(node.endTime);
      const resolvedDurationMs = resolveNodeDuration(node);
      const depthValue = Math.max(0, Number(node.depth ?? 0));
      const resolvedStartTs = startTs ?? 0;
      const resolvedEndTs = endTs ?? (resolvedStartTs > 0 ? resolvedStartTs + resolvedDurationMs : 0);
      return { ...node, depthValue, resolvedDurationMs, startTs: resolvedStartTs, endTs: resolvedEndTs };
    });

    const withTime = normalized.filter((item) => item.startTs > 0);
    const baseStart = withTime.length
      ? withTime.reduce((min, item) => Math.min(min, item.startTs), withTime[0].startTs)
      : Date.now();
    const maxEnd = withTime.length
      ? withTime.reduce((max, item) => Math.max(max, item.endTs || item.startTs), withTime[0].endTs || withTime[0].startTs)
      : baseStart;
    const runDuration = Number(selectedRun?.durationMs ?? 0);
    const windowDuration = Math.max(runDuration > 0 ? runDuration : maxEnd - baseStart, 1);

    const rows = normalized
      .sort((a, b) => a.startTs - b.startTs || a.depthValue - b.depthValue)
      .map((node) => {
        const offsetMs = node.startTs > 0 ? Math.max(0, node.startTs - baseStart) : 0;
        const leftPercent = clamp((offsetMs / windowDuration) * 100, 0, 99.2);
        const widthPercent = clamp(
          (Math.max(node.resolvedDurationMs, 1) / windowDuration) * 100,
          0.8,
          100 - leftPercent
        );
        return { ...node, offsetMs, leftPercent, widthPercent };
      });

    return { totalWindowMs: windowDuration, nodes: rows };
  }, [detail?.nodes, selectedRun?.durationMs]);

  const stats = useMemo(() => {
    const nodes = timeline.nodes;
    const total = nodes.length;
    const failed = nodes.filter((node) => normalizeStatus(node.status) === "failed").length;
    const success = nodes.filter((node) => normalizeStatus(node.status) === "success").length;
    const running = nodes.filter((node) => normalizeStatus(node.status) === "running").length;
    const avgDuration = total > 0 ? Math.round(nodes.reduce((sum, node) => sum + node.resolvedDurationMs, 0) / total) : 0;
    const sortedByDuration = [...nodes].sort((a, b) => b.resolvedDurationMs - a.resolvedDurationMs);
    const topSlowest = sortedByDuration[0];

    return { total, failed, success, running, avgDuration, topSlowest };
  }, [timeline.nodes]);

  useEffect(() => {
    if (!timeline.nodes.length) {
      setSelectedNodeId(null);
      return;
    }
    if (selectedNodeId && timeline.nodes.some((node) => node.nodeId === selectedNodeId)) {
      return;
    }
    setSelectedNodeId(stats.topSlowest?.nodeId || timeline.nodes[0].nodeId);
  }, [selectedNodeId, stats.topSlowest?.nodeId, timeline.nodes]);

  const selectedNode = timeline.nodes.find((node) => node.nodeId === selectedNodeId) || timeline.nodes[0];
  const runExtra = parseExtraData(selectedRun?.extraData);

  if (detailLoading) {
    return (
      <div className="flex min-h-[400px] items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-slate-500">
          <Loader2 className="h-8 w-8 animate-spin" />
          <p>加载链路详情中...</p>
        </div>
      </div>
    );
  }

  if (!traceId || !selectedRun) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5 text-sm">
            <Link to="/admin/traces" className="text-slate-500 hover:text-slate-700">
              链路追踪
            </Link>
            <span className="text-slate-300">/</span>
            <span className="text-slate-400">详情</span>
          </div>
          <Button asChild variant="outline" size="sm" className="text-slate-600 hover:text-slate-800">
            <Link to="/admin/traces">
              <ArrowLeft className="mr-1.5 h-4 w-4" />
              返回列表
            </Link>
          </Button>
        </div>
        <div className="flex min-h-[300px] items-center justify-center">
          <div className="text-center text-slate-500">
            <AlertTriangle className="mx-auto mb-4 h-12 w-12 text-slate-300" />
            <p>{!traceId ? "缺少 Trace Id" : "暂无数据"}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4 pb-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="mb-2 flex items-center gap-1.5 text-sm">
            <Link to="/admin/traces" className="text-slate-500 transition-colors hover:text-slate-700">
              RAG 链路列表
            </Link>
            <span className="text-slate-300">/</span>
            <span className="text-slate-500">链路详情</span>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-semibold text-slate-950">{selectedRun.traceName || "未命名链路"}</h1>
            <Badge variant={statusBadgeVariant(selectedRun.status)}>{statusLabel(selectedRun.status)}</Badge>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button asChild variant="outline" size="sm" className="text-slate-600 hover:text-slate-800">
            <Link to="/admin/traces">
              <ArrowLeft className="mr-1.5 h-4 w-4" />
              返回列表
            </Link>
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="text-slate-600 hover:text-slate-800"
            onClick={() => loadDetail(traceId)}
            disabled={detailLoading}
          >
            <RefreshCw className={cn("mr-1.5 h-4 w-4", detailLoading && "animate-spin")} />
            刷新
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500">
        <button
          type="button"
          className="flex items-center gap-1 rounded-md px-1 font-mono transition-colors hover:text-slate-800"
          onClick={() => copyToClipboard(traceId, "Trace Id")}
          title="点击复制 Trace Id"
        >
          <Hash className="h-3 w-3" />
          {shortId(traceId)}
          <Copy className="h-3 w-3" />
        </button>
        <span className="flex items-center gap-1">
          <Calendar className="h-3 w-3" />
          {formatDateTime(selectedRun.startTime ?? undefined)}
        </span>
        {(selectedRun.username || selectedRun.userId) ? (
          <span className="flex items-center gap-1">
            <User className="h-3 w-3" />
            {selectedRun.username || selectedRun.userId}
          </span>
        ) : null}
      </div>

      {selectedRun.errorMessage ? (
        <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-500" />
          <div className="text-sm">
            <span className="font-medium text-red-800">执行出错：</span>
            <span className="ml-1 text-red-600">{selectedRun.errorMessage}</span>
          </div>
        </div>
      ) : null}

      <div className="flex overflow-x-auto rounded-lg border border-slate-200 bg-slate-50 shadow-sm divide-x divide-slate-200">
        <MetricItem icon={Clock} label="总耗时" value={formatDuration(selectedRun.durationMs ?? undefined)} variant="primary" />
        <MetricItem icon={Activity} label="Span" value={stats.total} />
        <MetricItem icon={CheckCircle2} label="成功" value={stats.success} variant="success" />
        <MetricItem icon={XCircle} label="失败" value={stats.failed} variant={stats.failed > 0 ? "error" : "default"} />
        {stats.running > 0 ? <MetricItem icon={Loader2} label="运行中" value={stats.running} variant="warning" /> : null}
        <MetricItem icon={Zap} label="平均耗时" value={formatDuration(stats.avgDuration)} />
      </div>

      <RunOverview run={selectedRun} nodes={timeline.nodes} runExtra={runExtra} slowestNode={stats.topSlowest} />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.45fr)_minmax(360px,0.85fr)]">
        <Card className="border-slate-200 shadow-sm">
          <CardHeader className="px-4 py-3">
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2 text-base text-slate-900">
                <GitBranch className="h-4 w-4 text-blue-600" />
                Span Waterfall
              </CardTitle>
              <span className="font-mono text-xs text-slate-500">窗口 {formatDuration(timeline.totalWindowMs)}</span>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {timeline.nodes.length === 0 ? (
              <div className="py-16 text-center text-slate-400">
                <Activity className="mx-auto mb-3 h-10 w-10 opacity-50" />
                <p>暂无 Span 记录</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <div className="min-w-[760px]">
                  <div className="grid grid-cols-[minmax(190px,1fr)_136px_minmax(220px,1.6fr)_108px] gap-4 border-y border-slate-100 bg-slate-50 px-4 py-2 text-xs font-medium text-slate-500">
                    <span>Span</span>
                    <span>阶段</span>
                    <span>时间线</span>
                    <span className="text-right">耗时</span>
                  </div>

                  <div className="grid grid-cols-[minmax(190px,1fr)_136px_minmax(220px,1.6fr)_108px] gap-4 bg-white px-4">
                    <div />
                    <div />
                    <TimeScale totalMs={timeline.totalWindowMs} />
                    <div />
                  </div>

                  <div className="divide-y divide-slate-100">
                    {timeline.nodes.map((node) => (
                      <WaterfallRow
                        key={node.nodeId}
                        node={node}
                        selected={node.nodeId === selectedNode?.nodeId}
                        isTopSlowest={node.nodeId === stats.topSlowest?.nodeId}
                        onSelect={() => setSelectedNodeId(node.nodeId)}
                      />
                    ))}
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <SpanDetailPanel node={selectedNode} />
      </div>

      <RawRunPanel run={selectedRun} nodes={timeline.nodes} runExtra={runExtra} />
    </div>
  );
}
