import { useCallback, useEffect, useMemo, useRef, useState, type ComponentType, type ReactNode } from "react";
import {
  Activity,
  BadgeCheck,
  Boxes,
  CheckCircle2,
  Clock3,
  Gauge,
  GitBranch,
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
import { cn } from "@/lib/utils";
import {
  approveAiInfraApproval,
  createAiInfraCanaryRollout,
  generateAiInfraReadinessReport,
  getAiInfraAgents,
  getAiInfraApprovals,
  getAiInfraCostUsageAggregate,
  getAiInfraSreHealth,
  getAiInfraTools,
  getLatestAiInfraReadinessReport,
  getLatestAiInfraRollout,
  pauseAiInfraRollout,
  promoteAiInfraRollout,
  rejectAiInfraApproval,
  rollbackAiInfraRollout,
  type ApiRecord,
  type ApprovalStatus,
  type PageResult
} from "@/services/aiInfraService";
import { getErrorMessage } from "@/utils/error";

type ConsoleTab = "overview" | "approvals" | "agents" | "tools" | "operations";
type StatusTone = "neutral" | "success" | "warning" | "danger" | "info";

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

const PAGE_SIZE = 10;
const DEFAULT_TENANT_ID = "tenant-default";
const DEFAULT_OPERATOR = "admin";
const ALL_APPROVAL_STATUSES = "ALL";

const tabs: Array<{ value: ConsoleTab; label: string; icon: ComponentType<{ className?: string }> }> = [
  { value: "overview", label: "总览", icon: SquareActivity },
  { value: "approvals", label: "审批", icon: ShieldCheck },
  { value: "agents", label: "Agent", icon: Boxes },
  { value: "tools", label: "工具", icon: TerminalSquare },
  { value: "operations", label: "准入 / 灰度", icon: GitBranch }
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
  { value: "PENDING", label: "待审批" },
  { value: "APPROVED", label: "已通过" },
  { value: "REJECTED", label: "已拒绝" },
  { value: "MODIFIED_APPROVED", label: "修改后通过" }
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

function pageRecords(page: PageResult<ApiRecord> | null) {
  return page?.records ?? [];
}

function statusTone(status: string): StatusTone {
  const normalized = status.toUpperCase();
  if (["PASS", "READY", "ACTIVE", "APPROVED", "SUCCEEDED", "HEALTHY", "PUBLISHED", "ENABLED"].includes(normalized)) {
    return "success";
  }
  if (["PENDING", "WARN", "WARNING", "CANARY", "RUNNING", "WAITING_APPROVAL", "DRAFT"].includes(normalized)) {
    return "warning";
  }
  if (["FAIL", "FAILED", "REJECTED", "DENIED", "DISABLED", "PAUSED", "CANCELLED"].includes(normalized)) {
    return "danger";
  }
  if (["MODIFIED_APPROVED", "PROMOTED"].includes(normalized)) return "info";
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
      {loading ? "加载中..." : label}
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
        <StatusBadge value={metric.tone === "success" ? "PASS" : metric.tone === "danger" ? "FAIL" : metric.tone === "warning" ? "WATCH" : "INFO"} />
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

export function AiInfraConsolePage() {
  const requestSeq = useRef(0);
  const [activeTab, setActiveTab] = useState<ConsoleTab>("overview");
  const [tenantId, setTenantId] = useState(DEFAULT_TENANT_ID);
  const [agentKeyword, setAgentKeyword] = useState("");
  const [approvalStatus, setApprovalStatus] = useState<typeof ALL_APPROVAL_STATUSES | ApprovalStatus>(ALL_APPROVAL_STATUSES);
  const [toolKeyword, setToolKeyword] = useState("");
  const [agents, setAgents] = useState<PageResult<ApiRecord> | null>(null);
  const [approvals, setApprovals] = useState<PageResult<ApiRecord> | null>(null);
  const [tools, setTools] = useState<PageResult<ApiRecord> | null>(null);
  const [sreHealth, setSreHealth] = useState<ApiRecord | null>(null);
  const [costUsage, setCostUsage] = useState<ApiRecord | null>(null);
  const [readinessResult, setReadinessResult] = useState<ApiRecord | null>(null);
  const [rolloutResult, setRolloutResult] = useState<ApiRecord | null>(null);
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

  const loadConsole = useCallback(async () => {
    const requestId = ++requestSeq.current;
    setLoading(true);
    try {
      const status = approvalStatus === ALL_APPROVAL_STATUSES ? "" : approvalStatus;
      const [agentPage, approvalPage, toolPage, health, cost] = await Promise.all([
        getAiInfraAgents({ tenantId: tenantId.trim() || undefined, keyword: agentKeyword.trim() || undefined, size: PAGE_SIZE }),
        getAiInfraApprovals({ tenantId: tenantId.trim() || undefined, status, size: PAGE_SIZE }),
        getAiInfraTools({ keyword: toolKeyword.trim() || undefined, size: PAGE_SIZE }),
        getAiInfraSreHealth(),
        tenantId.trim()
          ? getAiInfraCostUsageAggregate({ tenantId: tenantId.trim() })
          : Promise.resolve({ tenantId: "", tokens: 0, calls: 0, cost: 0 })
      ]);
      if (requestSeq.current !== requestId) return;
      setAgents(agentPage);
      setApprovals(approvalPage);
      setTools(toolPage);
      setSreHealth(health);
      setCostUsage(cost);
    } catch (error) {
      if (requestSeq.current !== requestId) return;
      toast.error(getErrorMessage(error, "加载 AI Infra 控制台失败"));
    } finally {
      if (requestSeq.current === requestId) {
        setLoading(false);
      }
    }
  }, [agentKeyword, approvalStatus, tenantId, toolKeyword]);

  useEffect(() => {
    loadConsole();
  }, [loadConsole]);

  const metrics = useMemo<MetricCard[]>(() => {
    const pendingApprovals = pageRecords(approvals).filter((item) => asString(item.status) === "PENDING").length;
    const healthStatus = asString(sreHealth?.status ?? sreHealth?.overallStatus, "UNKNOWN");
    return [
      {
        title: "Agent 定义",
        value: String(agents?.total ?? 0),
        detail: `${pageRecords(agents).length} 条当前页记录`,
        icon: Boxes,
        tone: agents?.total ? "info" : "neutral"
      },
      {
        title: "待审批",
        value: String(pendingApprovals),
        detail: `${approvals?.total ?? 0} 条审批记录`,
        icon: Clock3,
        tone: pendingApprovals > 0 ? "warning" : "success"
      },
      {
        title: "工具目录",
        value: String(tools?.total ?? 0),
        detail: "来自 Tool Gateway API",
        icon: TerminalSquare,
        tone: tools?.total ? "info" : "neutral"
      },
      {
        title: "SRE Health",
        value: healthStatus,
        detail: "聚合健康报告",
        icon: Activity,
        tone: statusTone(healthStatus)
      },
      {
        title: "Token 用量",
        value: asString(costUsage?.tokens ?? costUsage?.totalTokens, "0"),
        detail: `${asString(costUsage?.calls ?? costUsage?.totalCalls, "0")} calls`,
        icon: Gauge,
        tone: "neutral"
      },
      {
        title: "成本",
        value: formatMoney(costUsage?.cost ?? costUsage?.totalCost),
        detail: "当前租户累计聚合",
        icon: WalletCards,
        tone: "neutral"
      }
    ];
  }, [agents, approvals, costUsage, sreHealth, tools]);

  const updateReadinessForm = (field: keyof ReadinessForm, value: string) => {
    setReadinessForm((current) => ({ ...current, [field]: value }));
  };

  const updateRolloutForm = (field: keyof RolloutForm, value: string) => {
    setRolloutForm((current) => ({ ...current, [field]: value }));
  };

  const runApprovalAction = async (approvalId: string, action: "approve" | "reject") => {
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

  const runReadinessAction = async (action: "generate" | "latest") => {
    if (!readinessForm.tenantId || !readinessForm.agentId || !readinessForm.versionId) {
      toast.error("请填写 tenantId、agentId 和 versionId");
      return;
    }
    setActionLoading(`readiness:${action}`);
    try {
      const result =
        action === "generate"
          ? await generateAiInfraReadinessReport(readinessForm)
          : await getLatestAiInfraReadinessReport(readinessForm);
      setReadinessResult(result);
      toast.success(action === "generate" ? "准入报告已生成" : "已加载最新准入报告");
    } catch (error) {
      toast.error(getErrorMessage(error, "准入报告操作失败"));
    } finally {
      setActionLoading(null);
    }
  };

  const runRolloutAction = async (action: "create" | "latest" | "pause" | "promote" | "rollback") => {
    if (!rolloutForm.tenantId || !rolloutForm.agentId) {
      toast.error("请填写 tenantId 和 agentId");
      return;
    }
    if ((action === "create" || action === "latest") && !rolloutForm.versionId) {
      toast.error("请填写 versionId");
      return;
    }
    if (["pause", "promote", "rollback"].includes(action) && !rolloutForm.rolloutId) {
      toast.error("请填写 rolloutId");
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
      toast.success("Rollout 操作完成");
    } catch (error) {
      toast.error(getErrorMessage(error, "Rollout 操作失败"));
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div className="admin-page ai-infra-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">AI Infra 控制台</h1>
          <p className="admin-page-subtitle">
            连接后端 AI Infra API，集中查看 Agent、审批、工具、SRE、成本和企业试点准入状态。
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
          return (
            <button
              key={tab.value}
              type="button"
              onClick={() => setActiveTab(tab.value)}
              className={cn(
                "inline-flex h-10 items-center gap-2 rounded-md px-3 text-sm font-medium transition",
                active ? "bg-slate-950 text-white shadow-sm" : "text-slate-600 hover:bg-white hover:text-slate-950"
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
          <DataPanel title="最新审批队列" description="展示 HITL 审批 API 返回的当前页数据">
            <ApprovalTable
              records={pageRecords(approvals).slice(0, 5)}
              loading={loading}
              actionLoading={actionLoading}
              onAction={runApprovalAction}
            />
          </DataPanel>
          <DataPanel title="SRE / 成本原始证据" description="用于排查聚合字段映射差异">
            <JsonPreview value={{ sreHealth, costUsage }} />
          </DataPanel>
        </div>
      ) : null}

      {activeTab === "approvals" ? (
        <DataPanel
          title="Approval Inbox"
          description="支持查询待审批请求，并调用 approve / reject API。"
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
      ) : null}

      {activeTab === "agents" ? (
        <DataPanel
          title="Agent Catalog"
          description="读取 Agent Definition API，确认 registry / published version 基础数据可见。"
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
            emptyLabel="暂无 Agent 数据"
            records={pageRecords(agents)}
            columns={[
              { key: "agentId", label: "Agent ID" },
              { key: "name", label: "名称" },
              { key: "ownerTeam", label: "Owner" },
              { key: "riskLevel", label: "风险", status: true },
              { key: "status", label: "状态", status: true },
              { key: "createdAt", label: "创建时间", time: true }
            ]}
          />
        </DataPanel>
      ) : null}

      {activeTab === "tools" ? (
        <DataPanel
          title="Tool Catalog"
          description="读取 Tool Gateway 工具目录，检查高风险工具是否被审批策略约束。"
          actions={
            <>
              <Input
                value={toolKeyword}
                onChange={(event) => setToolKeyword(event.target.value)}
                className="w-[260px] bg-white"
                placeholder="搜索 Tool"
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
            emptyLabel="暂无工具数据"
            records={pageRecords(tools)}
            columns={[
              { key: "toolId", label: "Tool ID" },
              { key: "name", label: "名称" },
              { key: "resourceType", label: "资源类型" },
              { key: "riskLevel", label: "风险", status: true },
              { key: "requiresApproval", label: "审批" },
              { key: "enabled", label: "启用", status: true }
            ]}
          />
        </DataPanel>
      ) : null}

      {activeTab === "operations" ? (
        <div className="grid gap-4 xl:grid-cols-2">
          <DataPanel title="企业试点准入" description="生成或读取 latest readiness report，不伪造 PASS。">
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
              <Button onClick={() => runReadinessAction("generate")} disabled={actionLoading === "readiness:generate"}>
                <BadgeCheck className="mr-2 h-4 w-4" />
                生成报告
              </Button>
              <Button variant="outline" onClick={() => runReadinessAction("latest")} disabled={actionLoading === "readiness:latest"}>
                <RefreshCw className="mr-2 h-4 w-4" />
                读取 latest
              </Button>
            </div>
            <div className="mt-4">
              <JsonPreview value={readinessResult ?? { message: "暂无准入报告结果" }} />
            </div>
          </DataPanel>

          <DataPanel title="Rollout 操作" description="使用已有 canary / latest / pause / promote / rollback API。">
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
              <Button onClick={() => runRolloutAction("create")} disabled={actionLoading === "rollout:create"}>
                <PlayCircle className="mr-2 h-4 w-4" />
                创建 Canary
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("latest")} disabled={actionLoading === "rollout:latest"}>
                <RefreshCw className="mr-2 h-4 w-4" />
                latest
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("pause")} disabled={actionLoading === "rollout:pause"}>
                <PauseCircle className="mr-2 h-4 w-4" />
                pause
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("promote")} disabled={actionLoading === "rollout:promote"}>
                <CheckCircle2 className="mr-2 h-4 w-4" />
                promote
              </Button>
              <Button variant="outline" onClick={() => runRolloutAction("rollback")} disabled={actionLoading === "rollout:rollback"}>
                <RotateCcw className="mr-2 h-4 w-4" />
                rollback
              </Button>
            </div>
            <div className="mt-4">
              <JsonPreview value={rolloutResult ?? { message: "暂无 rollout 操作结果" }} />
            </div>
          </DataPanel>
        </div>
      ) : null}

      <section className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
        当前页面只接入已存在的 AI Infra API，不实现前端发布向导、真实百分比分流、真实扣费或远程 Agent mesh。
      </section>
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
    return <EmptyState loading={loading} label="暂无审批数据" />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Approval ID</TableHead>
          <TableHead>Run / Tool</TableHead>
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
          <TableRow key={`${asString(item.id ?? item.agentId ?? item.toolId, "row")}-${index}`}>
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
