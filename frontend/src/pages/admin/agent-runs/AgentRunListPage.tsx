import { useCallback, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ChevronLeft, ChevronRight, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { listAgentRuns, cancelAgentRun } from "@/services/agentRunService";
import { getErrorMessage } from "@/utils/error";

type RunRecord = Record<string, unknown>;

const PAGE_SIZE = 15;

const STATUS_OPTIONS = [
  { value: "ALL", label: "全部状态" },
  { value: "RUNNING", label: "运行中" },
  { value: "COMPLETED", label: "已完成" },
  { value: "FAILED", label: "失败" },
  { value: "WAITING", label: "等待中" },
  { value: "CANCELLED", label: "已取消" },
  { value: "PAUSED", label: "已暂停" }
];

function getStatusBadge(status?: string) {
  switch (status) {
    case "RUNNING":
    case "ACTIVE":
      return <Badge className="bg-blue-100 text-blue-700">运行中</Badge>;
    case "COMPLETED":
      return <Badge className="bg-green-100 text-green-700">已完成</Badge>;
    case "FAILED":
    case "ERROR":
      return <Badge variant="destructive">失败</Badge>;
    case "WAITING":
      return <Badge className="bg-amber-100 text-amber-700">等待中</Badge>;
    case "CANCELLED":
      return <Badge variant="secondary">已取消</Badge>;
    case "PAUSED":
    case "SUSPENDED":
      return <Badge className="bg-slate-100 text-slate-600">已暂停</Badge>;
    default:
      return <Badge variant="outline">{status || "-"}</Badge>;
  }
}

export function AgentRunListPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [runs, setRuns] = useState<RunRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);

  // Filters
  const [agentIdFilter, setAgentIdFilter] = useState(searchParams.get("agentId") ?? "");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [runIdSearch, setRunIdSearch] = useState("");

  const fetchRuns = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listAgentRuns({
        agentId: agentIdFilter || undefined,
        status: (statusFilter && statusFilter !== "ALL") ? statusFilter : undefined,
        runId: runIdSearch || undefined,
        current: page,
        size: PAGE_SIZE
      });
      setRuns(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载运行列表失败"));
    } finally {
      setLoading(false);
    }
  }, [agentIdFilter, statusFilter, runIdSearch, page]);

  useEffect(() => {
    if (!featureState.enabled) return;
    fetchRuns();
  }, [fetchRuns, featureState.enabled]);

  const handleCancel = async (runId: string) => {
    if (!window.confirm(`确认取消 Run ${runId.slice(0, 8)}？`)) return;
    try {
      await cancelAgentRun(runId);
      toast.success("Run 已取消");
      fetchRuns();
    } catch (error) {
      toast.error(getErrorMessage(error, "取消失败"));
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="Agent 运行管理" />;
  }

  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Agent 运行管理</h1>
          <p className="admin-page-subtitle">查看和管理 Agent 运行实例</p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label className="mb-1 block text-xs text-slate-500">Agent ID</label>
          <Input
            value={agentIdFilter}
            onChange={(e) => { setAgentIdFilter(e.target.value); setPage(1); }}
            placeholder="按 Agent ID 筛选"
            className="w-[200px]"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs text-slate-500">Run ID</label>
          <Input
            value={runIdSearch}
            onChange={(e) => { setRunIdSearch(e.target.value); setPage(1); }}
            placeholder="按 Run ID 搜索"
            className="w-[200px]"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs text-slate-500">状态</label>
          <Select value={statusFilter} onValueChange={(v) => { setStatusFilter(v); setPage(1); }}>
            <SelectTrigger className="w-[140px]">
              <SelectValue placeholder="全部状态" />
            </SelectTrigger>
            <SelectContent>
              {STATUS_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button variant="outline" onClick={fetchRuns} disabled={loading}>
          <RefreshCw className={`mr-1 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          刷新
        </Button>
      </div>

      {/* Table */}
      <Card>
        <CardContent className="pt-6">
          {runs.length === 0 && !loading ? (
            <div className="py-8 text-center text-muted-foreground">
              暂无运行记录
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Run ID</TableHead>
                  <TableHead>Agent ID</TableHead>
                  <TableHead className="w-[100px]">状态</TableHead>
                  <TableHead>输入摘要</TableHead>
                  <TableHead className="w-[160px]">启动时间</TableHead>
                  <TableHead className="w-[140px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {runs.map((run) => {
                  const runId = (run.runId as string) || "";
                  const status = (run.status as string) || "";
                  return (
                    <TableRow key={runId}>
                      <TableCell className="font-mono text-sm">{runId.slice(0, 12)}</TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">
                        {((run.agentId as string) || "-").slice(0, 12)}
                      </TableCell>
                      <TableCell>{getStatusBadge(status)}</TableCell>
                      <TableCell className="max-w-[300px] truncate text-sm text-muted-foreground">
                        {(run.inputSummary as string) || "-"}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {run.startedAt ? new Date(run.startedAt as string).toLocaleString("zh-CN") : "-"}
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-1">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => navigate(`/admin/agent-inspector/${runId}`)}
                          >
                            <Search className="mr-1 h-3 w-3" />
                            检视
                          </Button>
                          {status === "RUNNING" || status === "ACTIVE" ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-red-600"
                              onClick={() => handleCancel(runId)}
                            >
                              取消
                            </Button>
                          ) : null}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}

          {/* Pagination */}
          {totalPages > 1 ? (
            <div className="mt-4 flex items-center justify-between">
              <span className="text-xs text-slate-500">
                共 {total} 条，第 {page}/{totalPages} 页
              </span>
              <div className="flex gap-1">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page <= 1}
                  onClick={() => setPage((p) => p - 1)}
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
