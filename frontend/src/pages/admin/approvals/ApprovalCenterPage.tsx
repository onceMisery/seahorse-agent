import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { RefreshCw, Search } from "lucide-react";
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
import { getApproval, listApprovals, type ApprovalItem } from "@/services/approvalService";
import { ApprovalDetailDrawer } from "./components/ApprovalDetailDrawer";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

function normalizedStatus(status?: string) {
  return (status || "").toUpperCase();
}

export function ApprovalCenterPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT);
  const [searchParams, setSearchParams] = useSearchParams();
  const directApprovalId = searchParams.get("approvalId")?.trim() || "";

  const [pageData, setPageData] = useState<PageResult<ApprovalItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [statusFilter, setStatusFilter] = useState("all");
  const [riskFilter, setRiskFilter] = useState("all");
  const [selectedApproval, setSelectedApproval] = useState<ApprovalItem | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const approvals = pageData?.records || [];

  const loadApprovals = async (current = pageNo, kw = keyword) => {
    try {
      setLoading(true);
      const data = await listApprovals({
        current,
        size: PAGE_SIZE,
        runId: kw || undefined,
        status: statusFilter !== "all" ? statusFilter : undefined,
        riskLevel: riskFilter !== "all" ? riskFilter : undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载审批列表失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadApprovals();
  }, [pageNo, keyword, statusFilter, riskFilter]);

  useEffect(() => {
    if (!featureState.enabled || !directApprovalId) return;
    let active = true;
    getApproval(directApprovalId)
      .then((approval) => {
        if (!active) return;
        setSelectedApproval(approval);
        setDrawerOpen(true);
      })
      .catch((error) => {
        if (!active) return;
        toast.error(getErrorMessage(error, "加载审批详情失败"));
        console.error(error);
      });
    return () => {
      active = false;
    };
  }, [featureState.enabled, directApprovalId]);

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchInput.trim());
  };

  const handleRefresh = () => {
    setPageNo(1);
    loadApprovals(1, keyword);
  };

  const handleRowClick = (item: ApprovalItem) => {
    setSelectedApproval(item);
    setDrawerOpen(true);
  };

  const handleDecisionComplete = () => {
    handleDrawerOpenChange(false);
    loadApprovals(pageNo, keyword);
  };

  const handleDrawerOpenChange = (open: boolean) => {
    setDrawerOpen(open);
    if (!open) {
      setSelectedApproval(null);
      if (directApprovalId) {
        const next = new URLSearchParams(searchParams);
        next.delete("approvalId");
        setSearchParams(next, { replace: true });
      }
    }
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  const getStatusBadge = (status?: string) => {
    switch (normalizedStatus(status)) {
      case "PENDING":
        return <Badge className="bg-amber-100 text-amber-700">待审批</Badge>;
      case "APPROVED":
        return <Badge className="bg-green-100 text-green-700">已通过</Badge>;
      case "REJECTED":
        return <Badge variant="destructive">已拒绝</Badge>;
      case "MODIFIED":
        return <Badge className="bg-blue-100 text-blue-700">已修改</Badge>;
      default:
        return <Badge variant="outline">{status || "-"}</Badge>;
    }
  };

  const getRiskBadge = (riskLevel?: string) => {
    switch (riskLevel) {
      case "HIGH":
        return <Badge variant="destructive">高</Badge>;
      case "MEDIUM":
        return <Badge className="bg-amber-100 text-amber-700">中</Badge>;
      case "LOW":
        return <Badge className="bg-green-100 text-green-700">低</Badge>;
      default:
        return <Badge variant="outline">{riskLevel || "-"}</Badge>;
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="审批中心" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">审批中心</h1>
          <p className="admin-page-subtitle">管理人工介入审批请求</p>
        </div>
        <div className="admin-page-actions">
          <Input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="搜索 Run ID"
            className="w-[200px]"
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
          />
          <Button variant="outline" onClick={handleSearch}>
            <Search className="w-4 h-4 mr-1" />
            搜索
          </Button>
          <Select value={statusFilter} onValueChange={(v) => { setStatusFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[120px]"><SelectValue placeholder="状态" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部</SelectItem>
              <SelectItem value="PENDING">待审批</SelectItem>
              <SelectItem value="APPROVED">已通过</SelectItem>
              <SelectItem value="REJECTED">已拒绝</SelectItem>
              <SelectItem value="MODIFIED">已修改</SelectItem>
            </SelectContent>
          </Select>
          <Select value={riskFilter} onValueChange={(v) => { setRiskFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[120px]"><SelectValue placeholder="风险等级" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部风险</SelectItem>
              <SelectItem value="LOW">低</SelectItem>
              <SelectItem value="MEDIUM">中</SelectItem>
              <SelectItem value="HIGH">高</SelectItem>
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
          ) : approvals.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无审批记录</div>
          ) : (
            <Table className="min-w-[1000px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[160px]">工具名称</TableHead>
                  <TableHead className="w-[100px]">状态</TableHead>
                  <TableHead className="w-[80px]">风险</TableHead>
                  <TableHead className="w-[120px]">提交人</TableHead>
                  <TableHead className="w-[140px]">Run ID</TableHead>
                  <TableHead className="w-[160px]">时间</TableHead>
                  <TableHead className="w-[100px]">决策人</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {approvals.map((item) => (
                  <TableRow key={item.approvalId} className="cursor-pointer hover:bg-slate-50" onClick={() => handleRowClick(item)}>
                    <TableCell className="font-medium">{item.toolName || "-"}</TableCell>
                    <TableCell>{getStatusBadge(item.status)}</TableCell>
                    <TableCell>{getRiskBadge(item.riskLevel)}</TableCell>
                    <TableCell className="text-muted-foreground">{item.submittedBy || "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{item.runId || "-"}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{formatTime(item.createTime)}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{item.decidedBy || "-"}</TableCell>
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
            <Button variant="outline" size="sm" onClick={() => setPageNo((prev) => Math.max(1, prev - 1))} disabled={pageData.current <= 1}>上一页</Button>
            <span>{pageData.current} / {pageData.pages}</span>
            <Button variant="outline" size="sm" onClick={() => setPageNo((prev) => Math.min(pageData.pages || 1, prev + 1))} disabled={pageData.current >= pageData.pages}>下一页</Button>
          </div>
        </div>
      ) : null}

      <ApprovalDetailDrawer
        open={drawerOpen}
        onOpenChange={handleDrawerOpenChange}
        approval={selectedApproval}
        onDecisionComplete={handleDecisionComplete}
      />
    </div>
  );
}
