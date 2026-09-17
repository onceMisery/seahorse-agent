import { useCallback, useEffect, useMemo, useState } from "react";
import { CheckCheck, RefreshCw, Sparkles, X } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  generatePolicyAdjustmentTickets,
  listPolicyAdjustmentTickets,
  runPolicyAdjustmentTicketReviews,
  isRollbackRecommended,
  REVIEW_OUTCOME_LABELS,
  TICKET_DIRECTION_LABELS,
  TICKET_STATUS_LABELS,
  type PolicyAdjustmentTicket,
  type PolicyAdjustmentTicketReview,
  type PolicyAdjustmentTicketStatus,
  type TicketBatchDecisionResult,
  type TicketPageResponse
} from "@/services/policyAdjustmentTicketService";
import { PolicyAdjustmentTicketDrawer } from "./components/PolicyAdjustmentTicketDrawer";
import { BatchDecisionDialog, type TicketBatchAction } from "./components/BatchDecisionDialog";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;
const DEFAULT_SAMPLE_LIMIT = 200;
const MAX_SAMPLE_LIMIT = 1000;

/**
 * 只有待审批工单可被勾选。
 *
 * 批量通过/驳回的语义是"对待办做处置"；已通过、已落地、已回滚的工单再被批量提交，
 * 只会换来一堆 400（状态迁移不被允许），而失败原因还得运营逐条去猜。
 */
function isSelectable(ticket: PolicyAdjustmentTicket) {
  return ticket.status === "PENDING";
}

export function PolicyAdjustmentTicketPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.MEMORY_GOVERNANCE);

  const [pageData, setPageData] = useState<TicketPageResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [statusFilter, setStatusFilter] = useState("all");
  const [sampleLimit, setSampleLimit] = useState(String(DEFAULT_SAMPLE_LIMIT));
  const [generating, setGenerating] = useState(false);
  const [selectedTicket, setSelectedTicket] = useState<PolicyAdjustmentTicket | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [batchAction, setBatchAction] = useState<TicketBatchAction | null>(null);
  const [batchOpen, setBatchOpen] = useState(false);

  const [baselinePassRate, setBaselinePassRate] = useState("");
  const [currentPassRate, setCurrentPassRate] = useState("");
  const [reviewing, setReviewing] = useState(false);

  const pageInfo = pageData?.page;
  const tickets = useMemo(() => pageInfo?.records ?? [], [pageInfo]);
  const latestReviews = pageData?.latestReviews ?? {};

  const selectableIds = useMemo(
    () => tickets.filter(isSelectable).map((item) => item.ticketId).filter(Boolean) as string[],
    [tickets]
  );

  const loadTickets = useCallback(
    async (current = pageNo) => {
      try {
        setLoading(true);
        const data = await listPolicyAdjustmentTickets({
          current,
          size: PAGE_SIZE,
          status: statusFilter !== "all" ? (statusFilter as PolicyAdjustmentTicketStatus) : undefined
        });
        setPageData(data);
      } catch (error) {
        toast.error(getErrorMessage(error, "加载策略调整工单失败"));
        console.error(error);
      } finally {
        setLoading(false);
      }
    },
    [pageNo, statusFilter]
  );

  useEffect(() => {
    if (!featureState.enabled) return;
    loadTickets();
  }, [featureState.enabled, loadTickets]);

  // 翻页或换筛选后，旧的勾选指向的是已经不在视野里的工单，继续留着会造成误操作
  useEffect(() => {
    setSelectedIds(new Set());
  }, [pageNo, statusFilter]);

  const handleRefresh = () => {
    setPageNo(1);
    loadTickets(1);
  };

  const handleGenerate = async () => {
    const parsed = Number.parseInt(sampleLimit, 10);
    if (!Number.isFinite(parsed) || parsed < 1 || parsed > MAX_SAMPLE_LIMIT) {
      toast.error(`采样上限需要在 1 ~ ${MAX_SAMPLE_LIMIT} 之间`);
      return;
    }
    try {
      setGenerating(true);
      const result = await generatePolicyAdjustmentTickets({ sampleLimit: parsed });
      const created = result?.tickets?.length ?? 0;
      const signalCount = result?.report?.signals?.length ?? 0;

      if (created > 0) {
        toast.success(`闭环完成，新建 ${created} 条工单`);
      } else if (signalCount > 0) {
        toast.info(`闭环产出 ${signalCount} 条信号，但没有新建工单——这些建议上一轮可能已经开过`);
      } else {
        toast.info(result?.report?.summary || "本次闭环没有产出可行动信号");
      }
      setPageNo(1);
      loadTickets(1);
    } catch (error) {
      toast.error(getErrorMessage(error, "运行自学习闭环失败"));
      console.error(error);
    } finally {
      setGenerating(false);
    }
  };

  /**
   * 手动触发回访。两个通过率是判定的唯一依据，缺一个后端就写不出结论，
   * 所以这里直接拦在前端，避免发出一个必然无效的请求。
   */
  const handleRunReviews = async () => {
    const baseline = Number.parseFloat(baselinePassRate);
    const current = Number.parseFloat(currentPassRate);
    if (!Number.isFinite(baseline) || !Number.isFinite(current)) {
      toast.error("请填写基线通过率与当前通过率（0 ~ 1 之间的小数）");
      return;
    }
    if (baseline < 0 || baseline > 1 || current < 0 || current > 1) {
      toast.error("通过率需要在 0 ~ 1 之间");
      return;
    }
    try {
      setReviewing(true);
      const result = await runPolicyAdjustmentTicketReviews({
        baselinePassRate: baseline,
        currentPassRate: current,
        source: "manual"
      });
      const written = result?.reviews?.length ?? 0;
      const skipped = result?.skipped ?? 0;
      if (written > 0) {
        toast.success(`回访完成，写入 ${written} 条记录${skipped > 0 ? `，跳过 ${skipped} 张（冷却期内）` : ""}`);
      } else {
        toast.info(result?.note || `没有写入回访记录：扫描 ${result?.scanned ?? 0} 张，跳过 ${skipped} 张`);
      }
      loadTickets(pageNo);
    } catch (error) {
      toast.error(getErrorMessage(error, "执行回访失败"));
      console.error(error);
    } finally {
      setReviewing(false);
    }
  };

  const toggleOne = (ticketId: string, checked: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(ticketId);
      } else {
        next.delete(ticketId);
      }
      return next;
    });
  };

  const toggleAll = (checked: boolean) => {
    setSelectedIds(checked ? new Set(selectableIds) : new Set());
  };

  const handleBatchResult = (result: TicketBatchDecisionResult | null, error?: unknown) => {
    if (!result) {
      // 整批失败的错误提示已由对话框里的 error 文案承接，这里不再重复弹一次
      if (error) {
        console.error(error);
      }
      return;
    }
    const succeeded = result.succeededTicketIds?.length ?? 0;
    const failed = result.failed ?? [];

    if (failed.length === 0) {
      toast.success(`批量操作完成，成功 ${succeeded} 张`);
      setSelectedIds(new Set());
    } else {
      // 部分失败：只清空成功的，失败的留在勾选项里让人重试，
      // 并把具体原因逐条列出来——"有 2 张失败了"这种提示没有任何可操作性
      const failedIds = new Set(failed.map((item) => item.ticketId).filter(Boolean) as string[]);
      setSelectedIds((prev) => new Set([...prev].filter((id) => failedIds.has(id))));
      const detail = failed
        .slice(0, 3)
        .map((item) => `${item.ticketId}：${item.reason}`)
        .join("；");
      const more = failed.length > 3 ? `等 ${failed.length} 张` : "";
      toast.warning(`成功 ${succeeded} 张、失败 ${failed.length} 张${more ? `（${more}）` : ""}：${detail}`);
    }
    loadTickets(pageNo);
  };

  const handleRowClick = (ticket: PolicyAdjustmentTicket) => {
    setSelectedTicket(ticket);
    setDrawerOpen(true);
  };

  const handleDecisionComplete = () => {
    setDrawerOpen(false);
    setSelectedTicket(null);
    setSelectedIds(new Set());
    loadTickets(pageNo);
  };

  const formatPercent = (value?: number) => {
    if (typeof value !== "number" || Number.isNaN(value)) return "-";
    return `${(value * 100).toFixed(1)}%`;
  };

  const formatTime = (value?: string) => {
    if (!value) return "-";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN");
  };

  const getStatusBadge = (status?: PolicyAdjustmentTicketStatus) => {
    if (!status) return <Badge variant="outline">-</Badge>;
    const styles: Record<PolicyAdjustmentTicketStatus, string> = {
      PENDING: "bg-amber-100 text-amber-700",
      APPROVED: "bg-green-100 text-green-700",
      REJECTED: "bg-red-100 text-red-700",
      APPLIED: "bg-blue-100 text-blue-700",
      ROLLED_BACK: "bg-slate-100 text-slate-700"
    };
    return <Badge className={styles[status]}>{TICKET_STATUS_LABELS[status] || status}</Badge>;
  };

  const getReviewBadge = (review?: PolicyAdjustmentTicketReview) => {
    if (!review?.outcome) return <span className="text-xs text-muted-foreground">-</span>;
    const rollback = isRollbackRecommended(review);
    return (
      <Badge variant="outline" className={rollback ? "bg-red-50 text-red-700 border-red-200" : ""}>
        {rollback ? "建议回滚" : REVIEW_OUTCOME_LABELS[review.outcome] || review.outcome}
      </Badge>
    );
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="策略调整工单" />;
  }

  const selectedCount = selectedIds.size;
  const allSelected = selectableIds.length > 0 && selectableIds.every((id) => selectedIds.has(id));

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">策略调整工单</h1>
          <p className="admin-page-subtitle">
            自学习闭环产出的建议在这里审批；落地由人在配置侧完成，本页只做登记
          </p>
        </div>
        <div className="admin-page-actions">
          <Input
            value={sampleLimit}
            onChange={(e) => setSampleLimit(e.target.value)}
            type="number"
            min={1}
            max={MAX_SAMPLE_LIMIT}
            placeholder="采样上限"
            className="w-[120px]"
          />
          <Button onClick={handleGenerate} disabled={generating}>
            <Sparkles className="w-4 h-4 mr-1" />
            {generating ? "运行中..." : "运行闭环"}
          </Button>
          <Select
            value={statusFilter}
            onValueChange={(v) => {
              setStatusFilter(v);
              setPageNo(1);
            }}
          >
            <SelectTrigger className="w-[130px]">
              <SelectValue placeholder="状态" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部</SelectItem>
              <SelectItem value="PENDING">待审批</SelectItem>
              <SelectItem value="APPROVED">已通过</SelectItem>
              <SelectItem value="REJECTED">已拒绝</SelectItem>
              <SelectItem value="APPLIED">已落地</SelectItem>
              <SelectItem value="ROLLED_BACK">已回滚</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={handleRefresh}>
            <RefreshCw className="w-4 h-4 mr-1" />
            刷新
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="text-slate-500">回访已落地工单：</span>
        <Input
          value={baselinePassRate}
          onChange={(e) => setBaselinePassRate(e.target.value)}
          type="number"
          step="0.01"
          min={0}
          max={1}
          placeholder="基线通过率"
          className="w-[130px]"
        />
        <Input
          value={currentPassRate}
          onChange={(e) => setCurrentPassRate(e.target.value)}
          type="number"
          step="0.01"
          min={0}
          max={1}
          placeholder="当前通过率"
          className="w-[130px]"
        />
        <Button variant="outline" onClick={handleRunReviews} disabled={reviewing}>
          <CheckCheck className="w-4 h-4 mr-1" />
          {reviewing ? "回访中..." : "执行回访"}
        </Button>
        <span className="text-xs text-slate-400">
          冷却期内的工单会被跳过；判定为回归时只标记「建议回滚」，不会自动改配置
        </span>
      </div>

      {selectedCount > 0 ? (
        <div className="flex flex-wrap items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm">
          <span>已选 {selectedCount} 张</span>
          <Button
            size="sm"
            onClick={() => {
              setBatchAction("approve");
              setBatchOpen(true);
            }}
          >
            批量通过
          </Button>
          <Button
            size="sm"
            variant="destructive"
            onClick={() => {
              setBatchAction("reject");
              setBatchOpen(true);
            }}
          >
            批量驳回
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setSelectedIds(new Set())}>
            <X className="w-4 h-4 mr-1" />
            清空选择
          </Button>
        </div>
      ) : null}

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : tickets.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              暂无工单，点击右上角「运行闭环」生成
            </div>
          ) : (
            <Table className="min-w-[1100px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[40px]">
                    <Checkbox
                      checked={
                        allSelected
                          ? true
                          : selectableIds.some((id) => selectedIds.has(id))
                            ? "indeterminate"
                            : false
                      }
                      onCheckedChange={(checked) => toggleAll(Boolean(checked))}
                      disabled={selectableIds.length === 0}
                      aria-label="全选待审批工单"
                    />
                  </TableHead>
                  <TableHead className="w-[200px]">目标</TableHead>
                  <TableHead className="w-[200px]">配置键</TableHead>
                  <TableHead className="w-[110px]">方向</TableHead>
                  <TableHead className="w-[100px]">不认可率</TableHead>
                  <TableHead className="w-[90px]">样本量</TableHead>
                  <TableHead className="w-[100px]">状态</TableHead>
                  <TableHead className="w-[120px]">最近回访</TableHead>
                  <TableHead className="w-[120px]">创建人</TableHead>
                  <TableHead className="w-[160px]">创建时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tickets.map((item) => {
                  const ticketId = item.ticketId ?? "";
                  const selectable = isSelectable(item);
                  return (
                    <TableRow
                      key={ticketId}
                      className="cursor-pointer hover:bg-slate-50"
                      onClick={() => handleRowClick(item)}
                    >
                      <TableCell onClick={(event) => event.stopPropagation()}>
                        <Checkbox
                          checked={selectedIds.has(ticketId)}
                          onCheckedChange={(checked) => toggleOne(ticketId, Boolean(checked))}
                          disabled={!selectable}
                          aria-label={selectable ? "选择工单" : "只有待审批工单可勾选"}
                        />
                      </TableCell>
                      <TableCell className="font-medium">{item.targetKey || "-"}</TableCell>
                      <TableCell className="text-sm text-muted-foreground font-mono">
                        {item.policyKey || "-"}
                      </TableCell>
                      <TableCell className="text-sm">
                        {item.direction ? TICKET_DIRECTION_LABELS[item.direction] || item.direction : "-"}
                      </TableCell>
                      <TableCell className="text-sm">{formatPercent(item.disapprovalRate)}</TableCell>
                      <TableCell className="text-sm">{item.sampleCount ?? "-"}</TableCell>
                      <TableCell>{getStatusBadge(item.status)}</TableCell>
                      <TableCell>{getReviewBadge(latestReviews[ticketId])}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">{item.createdBy || "-"}</TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {formatTime(item.createdAt)}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {pageInfo ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
          <span>共 {pageInfo.total} 条</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.max(1, prev - 1))}
              disabled={pageInfo.current <= 1}
            >
              上一页
            </Button>
            <span>
              {pageInfo.current} / {pageInfo.pages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.min(pageInfo.pages || 1, prev + 1))}
              disabled={pageInfo.current >= pageInfo.pages}
            >
              下一页
            </Button>
          </div>
        </div>
      ) : null}

      <PolicyAdjustmentTicketDrawer
        open={drawerOpen}
        onOpenChange={(open) => {
          setDrawerOpen(open);
          if (!open) setSelectedTicket(null);
        }}
        ticket={selectedTicket}
        onDecisionComplete={handleDecisionComplete}
      />

      <BatchDecisionDialog
        open={batchOpen}
        onOpenChange={setBatchOpen}
        ticketIds={[...selectedIds]}
        action={batchAction ?? "approve"}
        onResult={handleBatchResult}
      />
    </div>
  );
}
