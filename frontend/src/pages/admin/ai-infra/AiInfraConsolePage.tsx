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
  { value: "overview", label: "Overview", icon: SquareActivity, feature: "AI_INFRA_CONSOLE" },
  { value: "approvals", label: "Approvals", icon: ShieldCheck, feature: "AGENT_RUN_MANAGEMENT" },
  { value: "feedback", label: "Feedback", icon: MessageSquareWarning, feature: "AGENT_EVALUATION" },
  { value: "agents", label: "Agents", icon: Boxes, feature: "AGENT_DEFINITION_MANAGEMENT" },
  { value: "tools", label: "Tools", icon: TerminalSquare, feature: "TOOL_CATALOG_MANAGEMENT" },
  { value: "operations", label: "Operations", icon: GitBranch }
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
  { value: ALL_APPROVAL_STATUSES, label: "All statuses" },
  { value: "PENDING", label: "Pending" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
  { value: "MODIFIED", label: "Modified" }
];

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
  return `${sign}${(number * 100).toFixed(1)} pts`;
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
      {label}
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
      {loading ? "Loading..." : label}
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
        <EmptyState loading={loading} label="No runtime health contributors reported" />
        <JsonPreview value={sreHealth ?? { status: "UNKNOWN", items: [] }} />
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="text-sm text-slate-600">
          Overall: <StatusBadge value={sreHealth?.status ?? sreHealth?.overallStatus ?? "UNKNOWN"} />
        </div>
        <span className="font-mono text-xs text-slate-500">
          {formatTime(sreHealth?.checkedAt)}
        </span>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Contributor</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Message</TableHead>
            <TableHead>Evidence</TableHead>
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
  const [approvalComment, setApprovalComment] = useState("Reviewed in AI Infra console");
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
    comment: "Managed from AI Infra console"
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
      toast.error(getErrorMessage(error, "Failed to load AI Infra console"));
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
        title: "Agent definitions",
        value: String(agents?.total ?? 0),
        detail: `${pageRecords(agents).length} records on this page`,
        icon: Boxes,
        tone: agents?.total ? "info" : "neutral"
      },
      {
        title: "Pending approvals",
        value: String(pendingApprovals),
        detail: `${approvals?.total ?? 0} approval records`,
        icon: Clock3,
        tone: pendingApprovals > 0 ? "warning" : "success"
      },
      {
        title: "Feedback candidates",
        value: String(feedbackCandidates?.total ?? 0),
        detail: "Disliked assistant messages",
        icon: MessageSquareWarning,
        tone: feedbackCandidates?.total ? "warning" : "success"
      },
      {
        title: "Tool catalog",
        value: String(tools?.total ?? 0),
        detail: "From Tool Gateway API",
        icon: TerminalSquare,
        tone: tools?.total ? "info" : "neutral"
      },
      {
        title: "SRE Health",
        value: healthStatus,
        detail: "Aggregated health report",
        icon: Activity,
        tone: statusTone(healthStatus)
      },
      {
        title: "Cost",
        value: formatMoney(costUsage?.cost ?? costUsage?.totalCost),
        detail: `${asString(costUsage?.calls ?? costUsage?.totalCalls, "0")} calls`,
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
        toast.success("Approval approved");
      } else {
        await rejectAiInfraApproval(approvalId, approvalComment);
        toast.success("Approval rejected");
      }
      await loadConsole();
    } catch (error) {
      toast.error(getErrorMessage(error, "Approval action failed"));
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
        toast.success("Candidate accepted into eval dataset");
      } else {
        await rejectEvalCandidate(candidateId);
        toast.success("Candidate rejected");
      }
      await loadConsole();
    } catch (error) {
      toast.error(getErrorMessage(error, "Eval candidate action failed"));
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
      toast.error("tenantId, agentId and versionId are required");
      return;
    }
    setActionLoading(`readiness:${action}`);
    try {
      const result =
        action === "generate"
          ? await generateAiInfraReadinessReport(readinessForm)
          : await getLatestAiInfraReadinessReport(readinessForm);
      setReadinessResult(result);
      toast.success(action === "generate" ? "Readiness report generated" : "Latest readiness report loaded");
    } catch (error) {
      toast.error(getErrorMessage(error, "Readiness action failed"));
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
      toast.error("datasetId is required");
      return;
    }
    const baseline =
      evalRegressionForm.baselinePassRate.trim() === ""
        ? undefined
        : Number(evalRegressionForm.baselinePassRate);
    if (baseline !== undefined && (!Number.isFinite(baseline) || baseline < 0 || baseline > 1)) {
      toast.error("baselinePassRate must be between 0 and 1");
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
      toast.success("Eval regression completed");
    } catch (error) {
      toast.error(getErrorMessage(error, "Eval regression failed"));
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
      toast.error("tenantId and agentId are required");
      return;
    }
    if ((action === "create" || action === "latest") && !rolloutForm.versionId) {
      toast.error("versionId is required");
      return;
    }
    if (["pause", "promote", "rollback"].includes(action) && !rolloutForm.rolloutId) {
      toast.error("rolloutId is required");
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
      toast.success("Rollout action completed");
    } catch (error) {
      toast.error(getErrorMessage(error, "Rollout action failed"));
    } finally {
      setActionLoading(null);
    }
  };

  if (!pageFeatureState.enabled) {
    return <FeatureUnavailableState featureState={pageFeatureState} featureName="AI Infra Console" />;
  }

  return (
    <div className="admin-page ai-infra-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">AI Infra Console</h1>
          <p className="admin-page-subtitle">
            Web-facing agent operations for runs, approvals, tools, cost, and feedback evaluation candidates.
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
            Refresh
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
          <DataPanel title="Latest approvals" description="Current HITL approval queue from the approval API">
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
          <DataPanel title="Runtime health" description="Backend-reported SRE health contributors and evidence references.">
            <SreHealthItems sreHealth={sreHealth} loading={loading} />
          </DataPanel>
        </div>
      ) : null}

      {activeTab === "approvals" ? (
        approvalFeatureState.enabled ? (
          <DataPanel
            title="Approval Inbox"
            description="Query pending approval requests and call approve or reject APIs."
            actions={
              <>
                <Select value={approvalStatus} onValueChange={(value) => setApprovalStatus(value as typeof ALL_APPROVAL_STATUSES | ApprovalStatus)}>
                  <SelectTrigger className="w-[170px] bg-white">
                    <SelectValue placeholder="Approval status" />
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
                  Query
                </Button>
              </>
            }
          >
            <div className="mb-4">
              <Field label="Decision comment">
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
          title="Feedback Evaluation Candidates"
          description="Disliked assistant messages that can be sampled for quality review."
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
                Query
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
          title="Agent Catalog"
          description="Read agent definition records and published versions."
          actions={
            <>
              <Input
                value={agentKeyword}
                onChange={(event) => setAgentKeyword(event.target.value)}
                className="w-[260px] bg-white"
                placeholder="Search agent"
              />
              <Button variant="outline" onClick={loadConsole}>
                <Search className="mr-2 h-4 w-4" />
                Query
              </Button>
            </>
          }
        >
          <GenericRecordsTable
            loading={loading}
            emptyLabel="No agents"
            records={pageRecords(agents)}
            columns={[
              { key: "agentId", label: "Agent ID" },
              { key: "name", label: "Name" },
              { key: "ownerTeam", label: "Owner" },
              { key: "riskLevel", label: "Risk", status: true },
              { key: "status", label: "Status", status: true },
              { key: "createdAt", label: "Created", time: true }
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
          title="Tool Catalog"
          description="Read tool catalog records and check risk/approval policy fields."
          actions={
            <>
              <Input
                value={toolKeyword}
                onChange={(event) => setToolKeyword(event.target.value)}
                className="w-[260px] bg-white"
                placeholder="Search tool"
              />
              <Button variant="outline" onClick={loadConsole}>
                <Search className="mr-2 h-4 w-4" />
                Query
              </Button>
            </>
          }
        >
          <GenericRecordsTable
            loading={loading}
            emptyLabel="No tools"
            records={pageRecords(tools)}
            columns={[
              { key: "toolId", label: "Tool ID" },
              { key: "name", label: "Name" },
              { key: "resourceType", label: "Resource" },
              { key: "riskLevel", label: "Risk", status: true },
              { key: "requiresApproval", label: "Approval" },
              { key: "enabled", label: "Enabled", status: true }
            ]}
          />
          </DataPanel>
        ) : (
          <InlineFeatureUnavailableState featureState={toolFeatureState} featureName="工具目录" />
        )
      ) : null}

      {activeTab === "operations" ? (
        <div className="grid gap-4 xl:grid-cols-2">
          <DataPanel title="Pilot readiness" description="Generate or read latest readiness report.">
            <OperationFeatureNotice featureState={readinessFeatureState} label="Pilot readiness unavailable" />
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
                Generate
              </Button>
              <Button variant="outline" onClick={() => runReadinessAction("latest")} disabled={!readinessFeatureState.enabled || actionLoading === "readiness:latest"}>
                <RefreshCw className="mr-2 h-4 w-4" />
                Latest
              </Button>
            </div>
            <div className="mt-4">
              <JsonPreview value={readinessResult ?? { message: "No readiness result" }} />
            </div>
          </DataPanel>

          <DataPanel title="Eval regression" description="Run an eval dataset against the current model and compare it with a baseline.">
            <OperationFeatureNotice featureState={feedbackFeatureState} label="Eval regression unavailable" />
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
                  placeholder="optional"
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
                Run regression
              </Button>
            </div>
            <div className="mt-4">
              <EvalRegressionResult value={evalRegressionResult} />
            </div>
          </DataPanel>

          <DataPanel title="Rollout" description="Use canary, latest, pause, promote and rollback APIs.">
            <OperationFeatureNotice featureState={rolloutFeatureState} label="Rollout unavailable" />
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
                Canary
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("latest")} disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:latest"}>
                <RefreshCw className="mr-2 h-4 w-4" />
                Latest
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("pause")} disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:pause"}>
                <PauseCircle className="mr-2 h-4 w-4" />
                Pause
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("promote")} disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:promote"}>
                <CheckCircle2 className="mr-2 h-4 w-4" />
                Promote
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("rollback")} disabled={!rolloutFeatureState.enabled || actionLoading === "rollout:rollback"}>
                <RotateCcw className="mr-2 h-4 w-4" />
                Rollback
              </Button>
            </div>
            <div className="mt-4">
              <JsonPreview value={rolloutResult ?? { message: "No rollout result" }} />
            </div>
          </DataPanel>
        </div>
      ) : null}

      <section className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
        Consumer Web mode keeps local agent, host shell, sandbox, enterprise connectors, credential management and remote agent mesh behind advanced feature gates.
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
    return <EmptyState loading={loading} label="No feedback candidates" />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Feedback ID</TableHead>
          <TableHead>Message / Run</TableHead>
          <TableHead>User</TableHead>
          <TableHead>Reason</TableHead>
          <TableHead>Comment</TableHead>
          <TableHead>Created</TableHead>
          <TableHead className="text-right">Action</TableHead>
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
                    Accept
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={actionLoading === `reject:${candidateId}`}
                    onClick={() => onAction(candidateId, "reject")}
                  >
                    <XCircle className="mr-1.5 h-3.5 w-3.5" />
                    Reject
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
    return <EmptyState loading={false} label="No regression result" />;
  }
  const baseline = asRecord(value.baseline);
  const dimensions = asRecordArray(value.dimensions);
  return (
    <div className="space-y-4">
      <div className="grid gap-3 md:grid-cols-4">
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-medium text-slate-500">Current</p>
          <p className="mt-1 text-xl font-semibold text-slate-950">
            {formatPercent(baseline.currentPassRate)}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-medium text-slate-500">Baseline</p>
          <p className="mt-1 text-xl font-semibold text-slate-950">
            {formatPercent(baseline.baselinePassRate)}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-medium text-slate-500">Delta</p>
          <p className="mt-1 text-xl font-semibold text-slate-950">
            {formatPercentDelta(baseline.passRateDelta)}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-medium text-slate-500">Cases</p>
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
              <TableHead>Dimension</TableHead>
              <TableHead>Score</TableHead>
              <TableHead>Mode</TableHead>
              <TableHead>Reason</TableHead>
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
    return <EmptyState loading={loading} label="No approvals" />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Approval ID</TableHead>
          <TableHead>Run / Tool</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Risk</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Created</TableHead>
          <TableHead className="text-right">Action</TableHead>
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
                    Approve
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={!pending || actionLoading === `reject:${approvalId}`}
                    onClick={() => onAction(approvalId, "reject")}
                  >
                    <XCircle className="mr-1.5 h-3.5 w-3.5" />
                    Reject
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
