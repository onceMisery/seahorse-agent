import { useEffect, useState } from "react";
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
import { listToolInvocations, type ToolInvocation } from "@/services/toolCatalogService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

export function ToolInvocationAuditPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.TOOL_CATALOG_MANAGEMENT);

  const [pageData, setPageData] = useState<PageResult<ToolInvocation> | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [statusFilter, setStatusFilter] = useState("all");

  const invocations = pageData?.records || [];

  const loadInvocations = async (current = pageNo, kw = keyword) => {
    try {
      setLoading(true);
      const data = await listToolInvocations({
        current,
        size: PAGE_SIZE,
        runId: kw || undefined,
        status: statusFilter !== "all" ? statusFilter : undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载调用审计失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadInvocations();
  }, [pageNo, keyword, statusFilter]);

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchInput.trim());
  };

  const handleRefresh = () => {
    setPageNo(1);
    loadInvocations(1, keyword);
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="工具调用审计" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">工具调用审计</h1>
          <p className="admin-page-subtitle">查看工具调用历史与状态</p>
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
              <SelectItem value="SUCCESS">成功</SelectItem>
              <SelectItem value="FAILED">失败</SelectItem>
              <SelectItem value="PENDING">待审批</SelectItem>
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
          ) : invocations.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无调用记录</div>
          ) : (
            <Table className="min-w-[1000px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[180px]">工具名称</TableHead>
                  <TableHead className="w-[140px]">状态</TableHead>
                  <TableHead className="w-[160px]">参数摘要</TableHead>
                  <TableHead className="w-[100px]">耗时(ms)</TableHead>
                  <TableHead className="w-[140px]">审批 ID</TableHead>
                  <TableHead className="w-[140px]">Run ID</TableHead>
                  <TableHead className="w-[160px]">时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {invocations.map((inv) => (
                  <TableRow key={inv.invocationId}>
                    <TableCell className="font-medium">{inv.toolName || "-"}</TableCell>
                    <TableCell>
                      <Badge variant={inv.status === "SUCCESS" ? "default" : inv.status === "FAILED" ? "destructive" : "secondary"}>
                        {inv.status || "-"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground truncate max-w-[150px]">{inv.argumentsSummary || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{inv.durationMs ?? "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{inv.approvalId || "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{inv.runId || "-"}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{formatTime(inv.createTime)}</TableCell>
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
    </div>
  );
}
