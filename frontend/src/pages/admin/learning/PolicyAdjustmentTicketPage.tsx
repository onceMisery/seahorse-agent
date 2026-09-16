import { useCallback, useEffect, useState } from "react";
import { RefreshCw, Sparkles } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { PageResult } from "@/services/metadataGovernanceService";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  generatePolicyAdjustmentTickets,
  listPolicyAdjustmentTickets,
  TICKET_DIRECTION_LABELS,
  TICKET_STATUS_LABELS,
  type PolicyAdjustmentTicket,
  type PolicyAdjustmentTicketStatus
} from "@/services/policyAdjustmentTicketService";
import { PolicyAdjustmentTicketDrawer } from "./components/PolicyAdjustmentTicketDrawer";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;
const DEFAULT_SAMPLE_LIMIT = 200;
const MAX_SAMPLE_LIMIT = 1000;

export function PolicyAdjustmentTicketPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.MEMORY_GOVERNANCE);

  const [pageData, setPageData] = useState<PageResult<PolicyAdjustmentTicket> | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [statusFilter, setStatusFilter] = useState("all");
  const [sampleLimit, setSampleLimit] = useState(String(DEFAULT_SAMPLE_LIMIT));
  const [generating, setGenerating] = useState(false);
  const [selectedTicket, setSelectedTicket] = useState<PolicyAdjustmentTicket | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const tickets = pageData?.records || [];

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

  const handleRowClick = (ticket: PolicyAdjustmentTicket) => {
    setSelectedTicket(ticket);
    setDrawerOpen(true);
  };

  const handleDecisionComplete = () => {
    setDrawerOpen(false);
    setSelectedTicket(null);
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

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="策略调整工单" />;
  }

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

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : tickets.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              暂无工单，点击右上角「运行闭环」生成
            </div>
          ) : (
            <Table className="min-w-[1000px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[200px]">目标</TableHead>
                  <TableHead className="w-[200px]">配置键</TableHead>
                  <TableHead className="w-[110px]">方向</TableHead>
                  <TableHead className="w-[100px]">不认可率</TableHead>
                  <TableHead className="w-[90px]">样本量</TableHead>
                  <TableHead className="w-[100px]">状态</TableHead>
                  <TableHead className="w-[120px]">创建人</TableHead>
                  <TableHead className="w-[160px]">创建时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tickets.map((item) => (
                  <TableRow
                    key={item.ticketId}
                    className="cursor-pointer hover:bg-slate-50"
                    onClick={() => handleRowClick(item)}
                  >
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
                    <TableCell className="text-sm text-muted-foreground">{item.createdBy || "-"}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{formatTime(item.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {pageData ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
          <span>共 {pageData.total} 条</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.max(1, prev - 1))}
              disabled={pageData.current <= 1}
            >
              上一页
            </Button>
            <span>
              {pageData.current} / {pageData.pages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.min(pageData.pages || 1, prev + 1))}
              disabled={pageData.current >= pageData.pages}
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
    </div>
  );
}
