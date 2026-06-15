import { useEffect, useState } from "react";
import { RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { PageResult } from "@/services/metadataGovernanceService";
import { listMemoryTraces, type MemoryTrace } from "@/services/memoryGovernanceService";
import { getErrorMessage } from "@/utils/error";

export function MemoryTracePanel() {
  const [pageData, setPageData] = useState<PageResult<MemoryTrace> | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [memoryIdFilter, setMemoryIdFilter] = useState("");
  const [runIdFilter, setRunIdFilter] = useState("");

  const traces = pageData?.records || [];

  const loadTraces = async () => {
    try {
      setLoading(true);
      const data = await listMemoryTraces({
        current: pageNo,
        size: 10,
        memoryId: memoryIdFilter || undefined,
        runId: runIdFilter || undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 Trace 失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTraces();
  }, [pageNo]);

  return (
    <>
      <div className="flex items-center gap-2 mb-4">
        <Input value={memoryIdFilter} onChange={(e) => setMemoryIdFilter(e.target.value)} placeholder="记忆 ID" className="w-[180px]" />
        <Input value={runIdFilter} onChange={(e) => setRunIdFilter(e.target.value)} placeholder="Run ID" className="w-[180px]" />
        <Button variant="outline" size="sm" onClick={() => { setPageNo(1); loadTraces(); }}>
          <Search className="w-4 h-4 mr-1" />搜索
        </Button>
        <Button variant="outline" size="sm" onClick={loadTraces}>
          <RefreshCw className="w-4 h-4 mr-1" />刷新
        </Button>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : traces.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无 Trace 记录</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>记忆 ID</TableHead>
                  <TableHead>Run ID</TableHead>
                  <TableHead>操作</TableHead>
                  <TableHead>详情</TableHead>
                  <TableHead>时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {traces.map((trace) => (
                  <TableRow key={trace.traceId}>
                    <TableCell className="font-mono text-sm">{trace.memoryId || "-"}</TableCell>
                    <TableCell className="font-mono text-sm">{trace.runId || "-"}</TableCell>
                    <TableCell>{trace.operation || "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground truncate max-w-[200px]">{trace.detail || "-"}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{trace.createTime ? new Date(trace.createTime).toLocaleString("zh-CN") : "-"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {pageData && (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
          <span>共 {pageData.total} 条</span>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setPageNo((p) => Math.max(1, p - 1))} disabled={pageData.current <= 1}>上一页</Button>
            <span>{pageData.current} / {pageData.pages}</span>
            <Button variant="outline" size="sm" onClick={() => setPageNo((p) => Math.min(pageData.pages || 1, p + 1))} disabled={pageData.current >= pageData.pages}>下一页</Button>
          </div>
        </div>
      )}
    </>
  );
}
