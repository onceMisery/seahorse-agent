import { useCallback, useEffect, useMemo, useRef, useState, type ComponentType, type ReactNode } from "react";
import {
  Activity,
  BadgeCheck,
  Boxes,
  CheckCircle2,
  Clock3,
  Gauge,
  GitBranch,
  MessageSquareWarning,
  PauseCircle,
  PlayCircle,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldCheck,
  SquareActivity,
  TerminalSquare,
  WalletCards,
  XCircle
} from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { ADVANCED_ADMIN_FEATURES, getAdvancedFeatureState, type FeatureState } from "@/config/productMode";
import { cn } from "@/lib/utils";
import {
  approveAiInfraApproval,
  acceptEvalCandidate,
  createAiInfraCanaryRollout,
  generateAiInfraReadinessReport,
  getAiInfraAgents,
  getAiInfraApprovals,
  getAiInfraCostUsageAggregate,
  getAiInfraSreHealth,
  getAiInfraTools,
  getFeedbackEvaluationCandidates,
  getLatestAiInfraReadinessReport,
  getLatestAiInfraRollout,
  pauseAiInfraRollout,
  promoteAiInfraRollout,
  rejectAiInfraApproval,
  rejectEvalCandidate,
  rollbackAiInfraRollout,
  runEvalRegression,
  type ApiRecord,
  type ApprovalStatus,
  type PageResult
} from "@/services/aiInfraService";
import { getErrorMessage } from "@/utils/error";

type ConsoleTab = "overview" | "approvals" | "feedback" | "agents" | "tools" | "operations";
type StatusTone = "neutral" | "success" | "warning" | "danger" | "info";
type FeatureKey = keyof typeof ADVANCED_ADMIN_FEATURES;

type MetricCard = {
  title: string;
  value: string;
  detail: string;
  icon: ComponentType<{ className?: string }>;
  tone: StatusTone;
};

type ReadinessForm = {
  tenantId: string;
  agentId: string;
  versionId: string;
  operator: string;
};

type RolloutForm = ReadinessForm & {
  rolloutId: string;
  canaryPercent: string;
  targetVersionId: string;
  comment: string;
};

type EvalRegressionForm = {
  datasetId: string;
  modelId: string;
  baselinePassRate: string;
};

const PAGE_SIZE = 10;
const DEFAULT_TENANT_ID = "tenant-default";
const DEFAULT_OPERATOR = "admin";
const ALL_APPROVAL_STATUSES = "ALL";

const tabs: Array<{ value: ConsoleTab; label: string; icon: ComponentType<{ className?: string }>; feature?: FeatureKey }> = [
  { value: "overview", label: "总览", icon: SquareActivity, feature: "AI_INFRA_CONSOLE" },
  { value: "approvals", label: "审批", icon: ShieldCheck, feature: "AGENT_RUN_MANAGEMENT" },
  { value: "feedback", label: "反馈评估", icon: MessageSquareWarning, feature: "AGENT_EVALUATION" },
  { value: "agents", label: "Agent", icon: Boxes, feature: "AGENT_DEFINITION_MANAGEMENT" },
  { value: "tools", label: "工具", icon: TerminalSquare, feature: "TOOL_CATALOG_MANAGEMENT" },
  { value: "operations", label: "运维", icon: GitBranch }
];

const statusToneClasses: Record<StatusTone, string> = {
  neutral: "border-slate-200 bg-slate-50 text-slate-600",
  success: "border-emerald-200 bg-emerald-50 text-emerald-700",
  warning: "border-amber-200 bg-amber-50 text-amber-700",
  danger: "border-rose-200 bg-rose-50 text-rose-700",
  info: "border-sky-200 bg-sky-50 text-sky-700"
};

const iconToneClasses: Record<StatusTone, string> = {
  neutral: "bg-slate-900 text-white",
  success: "bg-emerald-600 text-white",
  warning: "bg-amber-500 text-white",
  danger: "bg-rose-600 text-white",
  info: "bg-sky-600 text-white"
};

const approvalStatuses: Array<{ value: typeof ALL_APPROVAL_STATUSES | ApprovalStatus; label: string }> = [
  { value: ALL_APPROVAL_STATUSES, label: "全部状态" },
  { value: "PENDING", label: "待处理" },
  { value: "APPROVED", label: "已批准" },
  { value: "REJECTED", label: "已拒绝" },
  { value: "MODIFIED", label: "已修改" }
];

const statusLabels: Record<string, string> = {
  ACTIVE: "启用",
  APPROVED: "已批准",
  AUTO: "自动",
  CANARY: "灰度中",
  CANCELLED: "已取消",
  CRITICAL: "严重",
  DENIED: "已拒绝",
  DISABLED: "已禁用",
  DOWN: "离线",
  DRAFT: "草稿",
  ENABLED: "已启用",
  FAIL: "失败",
  FAILED: "失败",
  GREEN: "绿色",
  HEALTHY: "健康",
  HIGH: "高",
  IMPROVED: "已改善",
  INFO: "信息",
  LOW: "低",
  MANUAL: "人工",
  MEDIUM: "中",
  MODIFIED: "已修改",
  PASS: "通过",
  PAUSED: "已暂停",
  PENDING: "待处理",
  PROMOTED: "已发布",
  PUBLISHED: "已发布",
  READY: "就绪",
  RED: "红色",
  REGRESSED: "已回退",
  REJECTED: "已拒绝",
  RUNNING: "运行中",
  SUCCEEDED: "成功",
  UNKNOWN: "未知",
  UP: "在线",
  WAITING_APPROVAL: "待审批",
  WARN: "警告",
  WARNING: "警告",
  WATCH: "关注"
};

function displayStatusLabel(value: unknown) {
  const label = asString(value);
  return statusLabels[label.toUpperCase()] ?? label;
}

function asString(value: unknown, fallback = "-") {
  if (value === null || value === undefined || value === "") return fallback;
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return fallback;
}

function asNumber(value: unknown, fallback = 0) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

function formatTime(value: unknown) {
  const raw = asString(value, "");
  if (!raw) return "-";
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return raw;
  return date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}

function formatMoney(value: unknown) {
  const amount = asNumber(value, 0);
  return amount.toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4
  });
}

function asOptionalNumber(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function asRecord(value: unknown): ApiRecord {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  return value as ApiRecord;
}

function asRecordArray(value: unknown): ApiRecord[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is ApiRecord => !!item && typeof item === "object" && !Array.isArray(item));
}

function formatPercent(value: unknown) {
  const number = asOptionalNumber(value);
  if (number === null) return "-";
  return `${(number * 100).toFixed(1)}%`;
}

function formatPercentDelta(value: unknown) {
  const number = asOptionalNumber(value);
  if (number === null) return "-";
  const sign = number > 0 ? "+" : "";
  return `${sign}${(number * 100).toFixed(1)} 个百分点`;
}

function pageRecords(page: PageResult<ApiRecord> | null) {
  return page?.records ?? [];
}

function statusTone(status: string): StatusTone {
  const normalized = status.toUpperCase();
  if (["PASS", "READY", "ACTIVE", "APPROVED", "SUCCEEDED", "HEALTHY", "PUBLISHED", "ENABLED", "GREEN", "UP"].includes(normalized)) {
    return "success";
  }
  if (["PENDING", "WARN", "WARNING", "CANARY", "RUNNING", "WAITING_APPROVAL", "DRAFT"].includes(normalized)) {
    return "warning";
  }
  if (["FAIL", "FAILED", "REJECTED", "DENIED", "DISABLED", "PAUSED", "CANCELLED", "RED", "DOWN"].includes(normalized)) {
    return "danger";
  }
  if (["REGRESSED"].includes(normalized)) return "danger";
  if (["IMPROVED"].includes(normalized)) return "success";
  if (["MODIFIED", "PROMOTED"].includes(normalized)) return "info";
  return "neutral";
}

function StatusBadge({ value }: { value: unknown }) {
  const label = asString(value);
  return (
    <Badge variant="outline" className={cn("border", statusToneClasses[statusTone(label)])}>
      {displayStatusLabel(label)}
    </Badge>
  );
}

function DataPanel({
  title,
  description,
  actions,
  children
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b border-slate-100 p-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-base font-semibold text-slate-950">{title}</h2>
          {description ? <p className="mt-1 text-sm text-slate-500">{description}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
      </div>
      <div className="p-4">{children}</div>
    </section>
  );
}

function EmptyState({ loading, label }: { loading: boolean; label: string }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
      {loading ? "加载中..." : label}
    </div>
  );
}

function InlineFeatureUnavailableState({
  featureState,
  featureName
}: {
  featureState: FeatureState;
  featureName: string;
}) {
  return (
    <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
      <p className="font-medium text-slate-700">{featureName}未启用</p>
      <p className="mt-1">{featureState.reason || "此功能当前不可用，请联系管理员开启。"}</p>
    </div>
  );
}

function Field({
  label,
  children
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="space-y-1.5 text-sm font-medium text-slate-700">
      <span>{label}</span>
      {children}
    </label>
  );
}

function MetricTile({ metric }: { metric: MetricCard }) {
  const Icon = metric.icon;
  return (
    <article className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className={cn("flex h-10 w-10 items-center justify-center rounded-md", iconToneClasses[metric.tone])}>
          <Icon className="h-5 w-5" />
        </div>
        <StatusBadge
          value={metric.tone === "success" ? "PASS" : metric.tone === "danger" ? "FAIL" : metric.tone === "warning" ? "WATCH" : "INFO"}
        />
      </div>
      <p className="mt-4 text-sm font-medium text-slate-500">{metric.title}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-950">{metric.value}</p>
      <p className="mt-1 text-xs text-slate-500">{metric.detail}</p>
    </article>
  );
}

function JsonPreview({ value }: { value: unknown }) {
  const text = useMemo(() => {
    try {
      return JSON.stringify(value ?? {}, null, 2);
    } catch {
      return String(value ?? "");
    }
  }, [value]);
  return (
    <pre className="max-h-[360px] overflow-auto rounded-lg border border-slate-200 bg-slate-950 p-4 text-xs leading-5 text-slate-100">
      {text}
    </pre>
  );
}

function SreHealthItems({ sreHealth, loading }: { sreHealth: ApiRecord | null; loading: boolean }) {
  const healthItems = asRecordArray(sreHealth?.items);
  if (!healthItems.length) {
    return (
      <div className="space-y-3">
        <EmptyState loading={loading} label="暂无运行健康检查项" />
        <JsonPreview value={sreHealth ?? { status: "UNKNOWN", items: [] }} />
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="text-sm text-slate-600">
          整体状态：<StatusBadge value={sreHealth?.status ?? sreHealth?.overallStatus ?? "UNKNOWN"} />
        </div>
        <span className="font-mono text-xs text-slate-500">
          {formatTime(sreHealth?.checkedAt)}
        </span>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>检查项</TableHead>
            <TableHead>状态</TableHead>
            <TableHead>消息</TableHead>
            <TableHead>证据</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {healthItems.map((item, index) => (
            <TableRow key={`${asString(item.contributorName, "contributor")}-${index}`}>
              <TableCell className="font-mono text-xs">{asString(item.contributorName)}</TableCell>
              <TableCell><StatusBadge value={item.status} /></TableCell>
              <TableCell className="max-w-[260px] text-xs text-slate-600">{asString(item.message)}</TableCell>
              <TableCell className="max-w-[220px] truncate font-mono text-xs text-slate-500">
                {asString(item.evidenceRef)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function OperationFeatureNotice({
  featureState,
  label
}: {
  featureState: FeatureState;
  label: string;
}) {
  if (featureState.enabled) return null;
  return (
    <div className="mb-4">
      <InlineFeatureUnavailableState featureState={featureState} featureName={label} />
    </div>
  );
}

export function AiInfraConsolePage() {
  const requestSeq = useRef(0);
  const pageFeatureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AI_INFRA_CONSOLE);
  const approvalFeatureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT);
  const feedbackFeatureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_EVALUATION);
  const agentFeatureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT);
  const toolFeatureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.TOOL_CATALOG_MANAGEMENT);
  const readinessFeatureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.ENTERPRISE_PILOT_READINESS);
  const rolloutFeatureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_ROLLOUT_MANAGEMENT);
  const costFeatureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.COST_ANALYTICS);
  const [activeTab, setActiveTab] = useState<ConsoleTab>("overview");
  const [tenantId, setTenantId] = useState(DEFAULT_TENANT_ID);
  const [agentKeyword, setAgentKeyword] = useState("");
  const [approvalStatus, setApprovalStatus] = useState<typeof ALL_APPROVAL_STATUSES | ApprovalStatus>(ALL_APPROVAL_STATUSES);
  const [toolKeyword, setToolKeyword] = useState("");
  const [feedbackUserId, setFeedbackUserId] = useState("");
  const [feedbackRunId, setFeedbackRunId] = useState("");
  const [feedbackReason, setFeedbackReason] = useState("");
  const [agents, setAgents] = useState<PageResult<ApiRecord> | null>(null);
  const [approvals, setApprovals] = useState<PageResult<ApiRecord> | null>(null);
  const [tools, setTools] = useState<PageResult<ApiRecord> | null>(null);
  const [feedbackCandidates, setFeedbackCandidates] = useState<PageResult<ApiRecord> | null>(null);
  const [sreHealth, setSreHealth] = useState<ApiRecord | null>(null);
  const [costUsage, setCostUsage] = useState<ApiRecord | null>(null);
  const [readinessResult, setReadinessResult] = useState<ApiRecord | null>(null);
  const [rolloutResult, setRolloutResult] = useState<ApiRecord | null>(null);
  const [evalRegressionResult, setEvalRegressionResult] = useState<ApiRecord | null>(null);
  const [approvalComment, setApprovalComment] = useState("已在 Agent 控制台审核");
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [readinessForm, setReadinessForm] = useState<ReadinessForm>({
    tenantId: DEFAULT_TENANT_ID,
    agentId: "",
    versionId: "",
    operator: DEFAULT_OPERATOR
  });
  const [rolloutForm, setRolloutForm] = useState<RolloutForm>({
    tenantId: DEFAULT_TENANT_ID,
    agentId: "",
    versionId: "",
    operator: DEFAULT_OPERATOR,
    rolloutId: "",
    canaryPercent: "10",
    targetVersionId: "",
    comment: "由 Agent 控制台管理"
  });
  const [evalRegressionForm, setEvalRegressionForm] = useState<EvalRegressionForm>({
    datasetId: "default",
    modelId: "",
    baselinePassRate: ""
  });
  const tabFeatureStates: Partial<Record<ConsoleTab, FeatureState>> = {
    overview: pageFeatureState,
    approvals: approvalFeatureState,
    feedback: feedbackFeatureState,
    agents: agentFeatureState,
    tools: toolFeatureState
  };
  const isTabEnabled = (tab: ConsoleTab) => tabFeatureStates[tab]?.enabled ?? true;
  const emptyPage = useMemo<PageResult<ApiRecord>>(
    () => ({ records: [], total: 0, size: PAGE_SIZE, current: 1, pages: 0 }),
    []
  );

  const loadConsole = useCallback(async () => {
    const requestId = ++requestSeq.current;
    setLoading(true);
    try {
      const status = approvalStatus === ALL_APPROVAL_STATUSES ? "" : approvalStatus;
      const safeTenantId = tenantId.trim();
      const [agentPage, approvalPage, toolPage, health, cost, candidatePage] = await Promise.all([
        agentFeatureState.enabled
          ? getAiInfraAgents({ tenantId: safeTenantId || undefined, keyword: agentKeyword.trim() || undefined, size: PAGE_SIZE })
          : Promise.resolve(emptyPage),
        approvalFeatureState.enabled
          ? getAiInfraApprovals({ tenantId: safeTenantId || undefined, status, size: PAGE_SIZE })
          : Promise.resolve(emptyPage),
        toolFeatureState.enabled
          ? getAiInfraTools({ keyword: toolKeyword.trim() || undefined, size: PAGE_SIZE })
          : Promise.resolve(emptyPage),
        pageFeatureState.enabled ? getAiInfraSreHealth() : Promise.resolve({ status: "DISABLED" }),
        costFeatureState.enabled && safeTenantId
          ? getAiInfraCostUsageAggregate({ tenantId: safeTenantId })
          : Promise.resolve({ tenantId: "", totalTokens: 0, totalCalls: 0, totalCost: 0 }),
        feedbackFeatureState.enabled
          ? getFeedbackEvaluationCandidates({
              userId: feedbackUserId.trim() || undefined,
              runId: feedbackRunId.trim() || undefined,
              reason: feedbackReason.trim() || undefined,
              size: PAGE_SIZE
            })
          : Promise.resolve(emptyPage)
      ]);
      if (requestSeq.current !== requestId) return;
      setAgents(agentPage);
      setApprovals(approvalPage);
      setTools(toolPage);
      setSreHealth(health);
      setCostUsage(cost);
      setFeedbackCandidates(candidatePage);
    } catch (error) {
      if (requestSeq.current !== requestId) return;
      toast.error(getErrorMessage(error, "加载 Agent 控制台失败"));
    } finally {
      if (requestSeq.current === requestId) {
        setLoading(false);
      }
    }
  }, [
    agentFeatureState.enabled,
    agentKeyword,
    approvalFeatureState.enabled,
    approvalStatus,
    costFeatureState.enabled,
    emptyPage,
    feedbackFeatureState.enabled,
    feedbackReason,
    feedbackRunId,
    feedbackUserId,
    pageFeatureState.enabled,
    tenantId,
    toolFeatureState.enabled,
    toolKeyword
  ]);

  useEffect(() => {
    loadConsole();
  }, [loadConsole]);

  useEffect(() => {
    if (!isTabEnabled(activeTab)) {
      setActiveTab("overview");
    }
  }, [
    activeTab,
    agentFeatureState.enabled,
    approvalFeatureState.enabled,
    feedbackFeatureState.enabled,
    pageFeatureState.enabled,
    toolFeatureState.enabled
  ]);

  const metrics = useMemo<MetricCard[]>(() => {
    const pendingApprovals = pageRecords(approvals).filter((item) => asString(item.status) === "PENDING").length;
    const healthStatus = asString(sreHealth?.status ?? sreHealth?.overallStatus, "UNKNOWN");
    return [
      {
        title: "Agent 定义",
        value: String(agents?.total ?? 0),
        detail: `本页 ${pageRecords(agents).length} 条记录`,
        icon: Boxes,
        tone: agents?.total ? "info" : "neutral"
      },
      {
        title: "待审批",
        value: String(pendingApprovals),
        detail: `共 ${approvals?.total ?? 0} 条审批记录`,
        icon: Clock3,
        tone: pendingApprovals > 0 ? "warning" : "success"
      },
      {
        title: "反馈候选",
        value: String(feedbackCandidates?.total ?? 0),
        detail: "用户点踩的助手消息",
        icon: MessageSquareWarning,
        tone: feedbackCandidates?.total ? "warning" : "success"
      },
      {
        title: "工具目录",
        value: String(tools?.total ?? 0),
        detail: "来自工具网关 API",
        icon: TerminalSquare,
        tone: tools?.total ? "info" : "neutral"
      },
      {
        title: "SRE 健康",
        value: displayStatusLabel(healthStatus),
        detail: "聚合健康报告",
        icon: Activity,
        tone: statusTone(healthStatus)
      },
      {
        title: "成本",
        value: formatMoney(costUsage?.cost ?? costUsage?.totalCost),
        detail: `${asString(costUsage?.calls ?? costUsage?.totalCalls, "0")} 次调用`,
        icon: WalletCards,
        tone: "neutral"
      }
    ];
  }, [agents, approvals, costUsage, feedbackCandidates, sreHealth, tools]);

  const updateReadinessForm = (field: keyof ReadinessForm, value: string) => {
    setReadinessForm((current) => ({ ...current, [field]: value }));
  };

  const updateRolloutForm = (field: keyof RolloutForm, value: string) => {
    setRolloutForm((current) => ({ ...current, [field]: value }));
  };

  const updateEvalRegressionForm = (field: keyof EvalRegressionForm, value: string) => {
    setEvalRegressionForm((current) => ({ ...current, [field]: value }));
  };

  const runApprovalAction = async (approvalId: string, action: "approve" | "reject") => {
    if (!approvalFeatureState.enabled) {
      toast.error("审批中心未启用");
      return;
    }
    setActionLoading(`${action}:${approvalId}`);
    try {
      if (action === "approve") {
        await approveAiInfraApproval(approvalId, approvalComment);
        toast.success("审批已通过");
      } else {
        await rejectAiInfraApproval(approvalId, approvalComment);
        toast.success("审批已拒绝");
      }
      await loadConsole();
    } catch (error) {
      toast.error(getErrorMessage(error, "审批操作失败"));
    } finally {
      setActionLoading(null);
    }
  };

  const runEvalCandidateAction = async (candidateId: string, action: "accept" | "reject") => {
    if (!feedbackFeatureState.enabled) {
      toast.error("评估反馈未启用");
      return;
    }
    setActionLoading(`${action}:${candidateId}`);
    try {
      if (action === "accept") {
        await acceptEvalCandidate(candidateId);
        toast.success("候选已加入评估数据集");
      } else {
        await rejectEvalCandidate(candidateId);
        toast.success("候选已拒绝");
      }
      await loadConsole();
    } catch (error) {
      toast.error(getErrorMessage(error, "反馈候选操作失败"));
    } finally {
      setActionLoading(null);
    }
  };

  const runReadinessAction = async (action: "generate" | "latest") => {
    if (!readinessFeatureState.enabled) {
      toast.error("企业试点就绪度未启用");
      return;
    }
    if (!readinessForm.tenantId || !readinessForm.agentId || !readinessForm.versionId) {
      toast.error("tenantId、agentId 和 versionId 必填");
      return;
    }
    setActionLoading(`readiness:${action}`);
    try {
      const result =
        action === "generate"
          ? await generateAiInfraReadinessReport(readinessForm)
          : await getLatestAiInfraReadinessReport(readinessForm);
      setReadinessResult(result);
      toast.success(action === "generate" ? "就绪度报告已生成" : "已加载最新就绪度报告");
    } catch (error) {
      toast.error(getErrorMessage(error, "就绪度操作失败"));
    } finally {
      setActionLoading(null);
    }
  };

  const runEvalRegressionAction = async () => {
    if (!feedbackFeatureState.enabled) {
      toast.error("评估反馈未启用");
      return;
    }
    const datasetId = evalRegressionForm.datasetId.trim();
    if (!datasetId) {
      toast.error("datasetId 必填");
      return;
    }
    const baseline =
      evalRegressionForm.baselinePassRate.trim() === ""
        ? undefined
        : Number(evalRegressionForm.baselinePassRate);
    if (baseline !== undefined && (!Number.isFinite(baseline) || baseline < 0 || baseline > 1)) {
      toast.error("baselinePassRate 必须在 0 到 1 之间");
      return;
    }
    setActionLoading("eval:regression");
    try {
      const result = await runEvalRegression({
        datasetId,
        modelId: evalRegressionForm.modelId.trim() || undefined,
        baselinePassRate: baseline
      });
      setEvalRegressionResult(result);
      toast.success("评估回归已完成");
    } catch (error) {
      toast.error(getErrorMessage(error, "评估回归失败"));
    } finally {
      setActionLoading(null);
    }
  };

  const runRolloutAction = async (action: "create" | "latest" | "pause" | "promote" | "rollback") => {
    if (!rolloutFeatureState.enabled) {
      toast.error("Agent 发布管理未启用");
      return;
    }
    if (!rolloutForm.tenantId || !rolloutForm.agentId) {
      toast.error("tenantId 和 agentId 必填");
      return;
    }
    if ((action === "create" || action === "latest") && !rolloutForm.versionId) {
      toast.error("versionId 必填");
      return;
    }
    if (["pause", "promote", "rollback"].includes(action) && !rolloutForm.rolloutId) {
      toast.error("rolloutId 必填");
      return;
    }
    setActionLoading(`rollout:${action}`);
    try {
      const canaryPercent = Math.max(0, Math.min(100, Number(rolloutForm.canaryPercent) || 0));
      const actionRequest = {
        tenantId: rolloutForm.tenantId,
        agentId: rolloutForm.agentId,
        rolloutId: rolloutForm.rolloutId,
        operator: rolloutForm.operator,
        comment: rolloutForm.comment,
        targetVersionId: rolloutForm.targetVersionId || undefined
      };
      const result =
        action === "create"
          ? await createAiInfraCanaryRollout({ ...rolloutForm, canaryPercent })
          : action === "latest"
            ? await getLatestAiInfraRollout(rolloutForm)
            : action === "pause"
              ? await pauseAiInfraRollout(actionRequest)
              : action === "promote"
                ? await promoteAiInfraRollout(actionRequest)
                : await rollbackAiInfraRollout(actionRequest);
      setRolloutResult(result);
      toast.success("发布操作已完成");
    } catch (error) {
      toast.error(getErrorMessage(error, "发布操作失败"));
    } finally {
      setActionLoading(null);
    }
  };

  if (!pageFeatureState.enabled) {
    return <FeatureUnavailableState featureState={pageFeatureState} featureName="Agent 控制台" />;
  }

  return (
    <div className="admin-page ai-infra-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Agent 控制台</h1>
          <p className="admin-page-subtitle">
            管理 Agent 运行、审批、工具、成本与反馈评估候选。
          </p>
        </div>
        <div className="admin-page-actions">
          <Input
            value={tenantId}
            onChange={(event) => setTenantId(event.target.value)}
            className="w-[220px]"
            placeholder="tenantId"
          />
          <Button variant="outline" onClick={loadConsole} disabled={loading}>
            <RefreshCw className={cn("mr-2 h-4 w-4", loading && "animate-spin")} />
            刷新
          </Button>
        </div>
      </div>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {metrics.map((metric) => (
          <MetricTile key={metric.title} metric={metric} />
        ))}
      </section>

      <div className="flex flex-wrap gap-2 rounded-lg border border-slate-200 bg-slate-100 p-2">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const active = activeTab === tab.value;
          const enabled = isTabEnabled(tab.value);
          return (
            <button
              key={tab.value}
              type="button"
              onClick={() => {
                if (enabled) setActiveTab(tab.value);
              }}
              disabled={!enabled}
              className={cn(
                "inline-flex h-10 items-center gap-2 rounded-md px-3 text-sm font-medium transition",
                active ? "bg-slate-950 text-white shadow-sm" : "text-slate-600 hover:bg-white hover:text-slate-950",
                !enabled && "cursor-not-allowed opacity-50 hover:bg-transparent hover:text-slate-600"
              )}
            >
              <Icon className="h-4 w-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {activeTab === "overview" ? (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(360px,0.8fr)]">
          <DataPanel title="最新审批" description="来自审批 API 的当前人工确认队列">
            {approvalFeatureState.enabled ? (
              <ApprovalTable
                records={pageRecords(approvals).slice(0, 5)}
                loading={loading}
                actionLoading={actionLoading}
                onAction={runApprovalAction}
              />
            ) : (
              <InlineFeatureUnavailableState featureState={approvalFeatureState} featureName="审批中心" />
            )}
          </DataPanel>
          <DataPanel title="运行健康" description="后端上报的 SRE 健康检查项与证据引用。">
            <SreHealthItems sreHealth={sreHealth} loading={loading} />
          </DataPanel>
        </div>
      ) : null}

      {activeTab === "approvals" ? (
        approvalFeatureState.enabled ? (
          <DataPanel
            title="审批收件箱"
            description="查询待处理审批请求，并调用通过或拒绝接口。"
            actions={
              <>
                <Select value={approvalStatus} onValueChange={(value) => setApprovalStatus(value as typeof ALL_APPROVAL_STATUSES | ApprovalStatus)}>
                  <SelectTrigger className="w-[170px] bg-white">
                    <SelectValue placeholder="审批状态" />
                  </SelectTrigger>
                  <SelectContent>
                    {approvalStatuses.map((item) => (
                      <SelectItem key={item.value} value={item.value}>
                        {item.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button variant="outline" onClick={loadConsole}>
                  <Search className="mr-2 h-4 w-4" />
                  查询
                </Button>
              </>
            }
          >
            <div className="mb-4">
              <Field label="审批意见">
                <Textarea value={approvalComment} onChange={(event) => setApprovalComment(event.target.value)} />
              </Field>
            </div>
            <ApprovalTable records={pageRecords(approvals)} loading={loading} actionLoading={actionLoading} onAction={runApprovalAction} />
          </DataPanel>
        ) : (
          <InlineFeatureUnavailableState featureState={approvalFeatureState} featureName="审批中心" />
        )
      ) : null}

      {activeTab === "feedback" ? (
        feedbackFeatureState.enabled ? (
          <DataPanel
          title="反馈评估候选"
          description="用户点踩的助手消息，可抽样进入质量评估。"
          actions={
            <>
              <Input
                value={feedbackUserId}
                onChange={(event) => setFeedbackUserId(event.target.value)}
                className="w-[180px] bg-white"
                placeholder="userId"
              />
              <Input
                value={feedbackRunId}
                onChange={(event) => setFeedbackRunId(event.target.value)}
                className="w-[180px] bg-white"
                placeholder="runId"
              />
              <Input
                value={feedbackReason}
                onChange={(event) => setFeedbackReason(event.target.value)}
                className="w-[180px] bg-white"
                placeholder="reason"
              />
              <Button variant="outline" onClick={loadConsole}>
                <Search className="mr-2 h-4 w-4" />
                查询
              </Button>
            </>
          }
        >
          <EvalCandidateTable
            records={pageRecords(feedbackCandidates)}
            loading={loading}
            actionLoading={actionLoading}
            onAction={runEvalCandidateAction}
          />
          </DataPanel>
        ) : (
          <InlineFeatureUnavailableState featureState={feedbackFeatureState} featureName="评估反馈" />
        )
      ) : null}

      {activeTab === "agents" ? (
        agentFeatureState.enabled ? (
          <DataPanel
          title="Agent 目录"
          description="查看 Agent 定义记录与已发布版本。"
          actions={
            <>
              <Input
                value={agentKeyword}
                onChange={(event) => setAgentKeyword(event.target.value)}
                className="w-[260px] bg-white"
                placeholder="搜索 Agent"
              />
              <Button variant="outline" onClick={loadConsole}>
                <Search className="mr-2 h-4 w-4" />
                查询
              </Button>
            </>
          }
        >
          <GenericRecordsTable
            loading={loading}
            emptyLabel="暂无 Agent"
            records={pageRecords(agents)}
            columns={[
              { key: "agentId", label: "Agent ID" },
              { key: "name", label: "名称" },
              { key: "ownerTeam", label: "负责人" },
              { key: "riskLevel", label: "风险", status: true },
              { key: "status", label: "状态", status: true },
              { key: "createdAt", label: "创建时间", time: true }
            ]}
          />
          </DataPanel>
        ) : (
          <InlineFeatureUnavailableState featureState={agentFeatureState} featureName="Agent 定义管理" />
        )
      ) : null}

      {activeTab === "tools" ? (
        toolFeatureState.enabled ? (
          <DataPanel
          title="工具目录"
          description="查看工具目录记录，并检查风险与审批策略字段。"
          actions={
            <>
              <Input
                value={toolKeyword}
                onChange={(event) => setToolKeyword(event.target.value)}
                className="w-[260px] bg-white"
                placeholder="搜索工具"
              />
              <Button variant="outline" onClick={loadConsole}>
                <Search className="mr-2 h-4 w-4" />
                查询
              </Button>
            </>
          }
        >
          <GenericRecordsTable
            loading={loading}
            emptyLabel="暂无工具"
            records={pageRecords(tools)}
            columns={[
              { key: "toolId", label: "工具 ID" },
              { key: "name", label: "名称" },
              { key: "resourceType", label: "资源" },
              { key: "riskLevel", label: "风险", status: true },
              { key: "requiresApproval", label: "需要审批" },
              { key: "enabled", label: "启用状态", status: true }
            ]}
          />
          </DataPanel>
        ) : (
          <InlineFeatureUnavailableState featureState={toolFeatureState} featureName="工具目录" />
        )
      ) : null}

      {activeTab === "operations" ? (
        <div className="grid gap-4 xl:grid-cols-2">
          <DataPanel title="试点就绪度" description="生成或读取最新就绪度报告。">
            <OperationFeatureNotice featureState={readinessFeatureState} label="试点就绪度" />
            <div className="grid gap-3 md:grid-cols-2">
              <Field label="tenantId">
                <Input value={readinessForm.tenantId} onChange={(event) => updateReadinessForm("tenantId", event.target.value)} />
              </Field>
              <Field label="operator">
                <Input value={readinessForm.operator} onChange={(event) => updateReadinessForm("operator", event.target.value)} />
              </Field>
              <Field label="agentId">
                <Input value={readinessForm.agentId} onChange={(event) => updateReadinessForm("agentId", event.target.value)} />
              </Field>
              <Field label="versionId">
                <Input value={readinessForm.versionId} onChange={(event) => updateReadinessForm("versionId", event.target.value)} />
              </Field>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button onClick={() => runReadinessAction("generate")} disabled={!readinessFeatureState.enabled || actionLoading === "readiness:generate"}>
                <BadgeCheck className="mr-2 h-4 w-4" />
                生成
              </Button>
              <Button variant="outline" onClick={() => runReadinessAction("latest")} disabled={!readinessFeatureState.enabled || actionLoading === "readiness:latest"}>
                <RefreshCw className="mr-2 h-4 w-4" />
                最新
              </Button>
            </div>
            <div className="mt-4">
              <JsonPreview value={readinessResult ?? { message: "暂无就绪度结果" }} />
            </div>
          </DataPanel>

          <DataPanel title="评估回归" description="使用当前模型运行评估数据集，并与基线结果对比。">
            <OperationFeatureNotice featureState={feedbackFeatureState} label="评估回归" />
            <div className="grid gap-3 md:grid-cols-3">
              <Field label="datasetId">
                <Input
                  value={evalRegressionForm.datasetId}
                  onChange={(event) => updateEvalRegressionForm("datasetId", event.target.value)}
                />
              </Field>
              <Field label="modelId">
                <Input
                  value={evalRegressionForm.modelId}
                  onChange={(event) => updateEvalRegressionForm("modelId", event.target.value)}
                  placeholder="可选"
                />
              </Field>
              <Field label="baselinePassRate">
                <Input
                  value={evalRegressionForm.baselinePassRate}
                  onChange={(event) => updateEvalRegressionForm("baselinePassRate", event.target.value)}
                  placeholder="0.82"
                />
              </Field>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button onClick={runEvalRegressionAction} disabled={!feedbackFeatureState.enabled || actionLoading === "eval:regression"}>
                <Gauge className="mr-2 h-4 w-4" />
                运行回归
              </Button>
            </div>
            <div className="mt-4">
              <EvalRegressionResult value={evalRegressionResult} />
            </div>
          </DataPanel>

          <DataPanel title="发布管理" description="调用灰度、最新、暂停、发布和回滚接口。">
            <OperationFeatureNotice featureState={rolloutFeatureState} label="发布管理" />
            <div className="grid gap-3 md:grid-cols-2">
              <Field label="tenantId">
                <Input value={rolloutForm.tenantId} onChange={(event) => updateRolloutForm("tenantId", event.target.value)} />
              </Field>
              <Field label="operator">
                <Input value={rolloutForm.operator} onChange={(event) => updateRolloutForm("operator", event.target.value)} />
              </Field>
              <Field label="agentId">
                <Input value={rolloutForm.agentId} onChange={(event) => updateRolloutForm("agentId", event.target.value)} />
              </Field>
              <Field label="versionId">
                <Input value={rolloutForm.versionId} onChange={(event) => updateRolloutForm("versionId", event.target.value)} />
              </Field>
              <Field label="rolloutId">
                <Input value={rolloutForm.rolloutId} onChange={(event) => updateRolloutForm("rolloutId", event.target.value)} />
              </Field>
              <Field label="canaryPercent">
                <Input value={rolloutForm.canaryPercent} onChange={(event) => updateRolloutForm("canaryPercent", event.target.value)} />
              </Field>
              <Field label="targetVersionId">
                <Input value={rolloutForm.targetVersionId} onChange={(event) => updateRolloutForm("targetVersionId", event.target.value)} />
              </Field>
              <Field label="comment">
                <Input value={rolloutForm.comment} onChange={(event) => updateRolloutForm("comment", event.target.value)} />
              </Field>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button onClick={() => runRolloutAction("create")} disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:create"}>
                <PlayCircle className="mr-2 h-4 w-4" />
                灰度
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("latest")} disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:latest"}>
                <RefreshCw className="mr-2 h-4 w-4" />
                最新
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("pause")} disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:pause"}>
                <PauseCircle className="mr-2 h-4 w-4" />
                暂停
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("promote")} disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:promote"}>
                <CheckCircle2 className="mr-2 h-4 w-4" />
                发布
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("rollback")} disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:rollback"}>
                <RotateCcw className="mr-2 h-4 w-4" />
                回滚
              </Button>
            </div>
            <div className="mt-4">
              <JsonPreview value={rolloutResult ?? { message: "暂无发布结果" }} />
            </div>
          </DataPanel>
        </div>
      ) : null}

      <section className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
        Consumer Web 模式会将本地 Agent、主机 Shell、沙箱、企业连接器、凭据管理和远程 Agent Mesh 保持在高级功能开关之后。
      </section>
    </div>
  );
}

function EvalCandidateTable({
  records,
  loading,
  actionLoading,
  onAction
}: {
  records: ApiRecord[];
  loading: boolean;
  actionLoading: string | null;
  onAction: (candidateId: string, action: "accept" | "reject") => void;
}) {
  if (!records.length) {
    return <EmptyState loading={loading} label="暂无反馈候选" />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>反馈 ID</TableHead>
          <TableHead>消息 / 运行</TableHead>
          <TableHead>用户</TableHead>
          <TableHead>原因</TableHead>
          <TableHead>备注</TableHead>
          <TableHead>创建时间</TableHead>
          <TableHead className="text-right">操作</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {records.map((item) => {
          const candidateId = asString(item.feedbackId);
          return (
            <TableRow key={candidateId}>
              <TableCell className="font-mono text-xs">{candidateId}</TableCell>
              <TableCell>
                <div className="space-y-1">
                  <p className="font-mono text-xs text-slate-700">{asString(item.messageId)}</p>
                  <p className="text-xs text-slate-500">{asString(item.agentRunId)}</p>
                </div>
              </TableCell>
              <TableCell className="text-xs">{asString(item.userId)}</TableCell>
              <TableCell><StatusBadge value={item.reason} /></TableCell>
              <TableCell className="max-w-[200px] truncate text-xs">{asString(item.comment)}</TableCell>
              <TableCell>{formatTime(item.createdAt)}</TableCell>
              <TableCell>
                <div className="flex justify-end gap-2">
                  <Button
                    size="sm"
                    disabled={actionLoading === `accept:${candidateId}`}
                    onClick={() => onAction(candidateId, "accept")}
                  >
                    <CheckCircle2 className="mr-1.5 h-3.5 w-3.5" />
                    接受
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={actionLoading === `reject:${candidateId}`}
                    onClick={() => onAction(candidateId, "reject")}
                  >
                    <XCircle className="mr-1.5 h-3.5 w-3.5" />
                    拒绝
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

function EvalRegressionResult({ value }: { value: ApiRecord | null }) {
  if (!value) {
    return <EmptyState loading={false} label="暂无回归结果" />;
  }
  const baseline = asRecord(value.baseline);
  const dimensions = asRecordArray(value.dimensions);
  return (
    <div className="space-y-4">
      <div className="grid gap-3 md:grid-cols-4">
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-medium text-slate-500">当前</p>
          <p className="mt-1 text-xl font-semibold text-slate-950">
            {formatPercent(baseline.currentPassRate)}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-medium text-slate-500">基线</p>
          <p className="mt-1 text-xl font-semibold text-slate-950">
            {formatPercent(baseline.baselinePassRate)}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-medium text-slate-500">差值</p>
          <p className="mt-1 text-xl font-semibold text-slate-950">
            {formatPercentDelta(baseline.passRateDelta)}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-medium text-slate-500">用例</p>
          <p className="mt-1 text-xl font-semibold text-slate-950">
            {asString(value.passed, "0")} / {asString(value.total, "0")}
          </p>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <StatusBadge value={baseline.status} />
        <span className="font-mono text-xs text-slate-500">
          {asString(value.datasetId)} · {formatTime(value.runAt)}
        </span>
      </div>
      {dimensions.length ? (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>维度</TableHead>
              <TableHead>分数</TableHead>
              <TableHead>模式</TableHead>
              <TableHead>原因</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {dimensions.map((dimension) => (
              <TableRow key={asString(dimension.dimension)}>
                <TableCell className="font-mono text-xs">{asString(dimension.dimension)}</TableCell>
                <TableCell>{formatPercent(dimension.score)}</TableCell>
                <TableCell>
                  <StatusBadge value={dimension.automated ? "AUTO" : "MANUAL"} />
                </TableCell>
                <TableCell className="max-w-[280px] text-xs text-slate-500">
                  {asString(dimension.reason)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : (
        <JsonPreview value={value} />
      )}
    </div>
  );
}

function ApprovalTable({
  records,
  loading,
  actionLoading,
  onAction
}: {
  records: ApiRecord[];
  loading: boolean;
  actionLoading: string | null;
  onAction: (approvalId: string, action: "approve" | "reject") => void;
}) {
  if (!records.length) {
    return <EmptyState loading={loading} label="暂无审批" />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>审批 ID</TableHead>
          <TableHead>运行 / 工具</TableHead>
          <TableHead>类型</TableHead>
          <TableHead>风险</TableHead>
          <TableHead>状态</TableHead>
          <TableHead>创建时间</TableHead>
          <TableHead className="text-right">操作</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {records.map((item) => {
          const approvalId = asString(item.approvalId);
          const pending = asString(item.status) === "PENDING";
          return (
            <TableRow key={approvalId}>
              <TableCell className="font-mono text-xs">{approvalId}</TableCell>
              <TableCell>
                <div className="space-y-1">
                  <p className="font-mono text-xs text-slate-700">{asString(item.runId)}</p>
                  <p className="text-xs text-slate-500">{asString(item.toolId)}</p>
                </div>
              </TableCell>
              <TableCell>{asString(item.approvalType)}</TableCell>
              <TableCell><StatusBadge value={item.riskLevel} /></TableCell>
              <TableCell><StatusBadge value={item.status} /></TableCell>
              <TableCell>{formatTime(item.createdAt)}</TableCell>
              <TableCell>
                <div className="flex justify-end gap-2">
                  <Button
                    size="sm"
                    disabled={!pending || actionLoading === `approve:${approvalId}`}
                    onClick={() => onAction(approvalId, "approve")}
                  >
                    <CheckCircle2 className="mr-1.5 h-3.5 w-3.5" />
                    通过
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={!pending || actionLoading === `reject:${approvalId}`}
                    onClick={() => onAction(approvalId, "reject")}
                  >
                    <XCircle className="mr-1.5 h-3.5 w-3.5" />
                    拒绝
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

function GenericRecordsTable({
  loading,
  emptyLabel,
  records,
  columns
}: {
  loading: boolean;
  emptyLabel: string;
  records: ApiRecord[];
  columns: Array<{ key: string; label: string; status?: boolean; time?: boolean }>;
}) {
  if (!records.length) {
    return <EmptyState loading={loading} label={emptyLabel} />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {columns.map((column) => (
            <TableHead key={column.key}>{column.label}</TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {records.map((item, index) => (
          <TableRow key={`${asString(item.id ?? item.feedbackId ?? item.agentId ?? item.toolId, "row")}-${index}`}>
            {columns.map((column) => {
              const value = item[column.key];
              return (
                <TableCell key={column.key} className={column.key.endsWith("Id") ? "font-mono text-xs" : undefined}>
                  {column.status ? <StatusBadge value={value} /> : column.time ? formatTime(value) : asString(value)}
                </TableCell>
              );
            })}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
