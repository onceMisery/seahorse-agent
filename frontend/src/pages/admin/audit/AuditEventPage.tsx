import { useEffect, useState } from "react";
import { RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { PageResult } from "@/services/metadataGovernanceService";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { listAuditEvents, getAuditEvent, type AuditEvent } from "@/services/auditCostService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

export function AuditEventPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AUDIT_LOG);

  const [pageData, setPageData] = useState<PageResult<AuditEvent> | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [eventTypeFilter, setEventTypeFilter] = useState("all");
  const [selectedEvent, setSelectedEvent] = useState<AuditEvent | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const events = pageData?.records || [];

  const loadEvents = async (current = pageNo, kw = keyword) => {
    try {
      setLoading(true);
      const data = await listAuditEvents({
        current,
        size: PAGE_SIZE,
        actor: kw || undefined,
        eventType: eventTypeFilter !== "all" ? eventTypeFilter : undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载审计日志失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadEvents();
  }, [pageNo, keyword, eventTypeFilter]);

  const handleRowClick = async (event: AuditEvent) => {
    try {
      const detail = event.auditId ? await getAuditEvent(event.auditId) : event;
      setSelectedEvent(detail || event);
      setDetailOpen(true);
    } catch {
      setSelectedEvent(event);
      setDetailOpen(true);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="审计日志" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">审计日志</h1>
          <p className="admin-page-subtitle">查看系统操作审计记录</p>
        </div>
        <div className="admin-page-actions">
          <Input value={searchInput} onChange={(e) => setSearchInput(e.target.value)} placeholder="搜索操作人" className="w-[180px]" onKeyDown={(e) => e.key === "Enter" && (setPageNo(1), setKeyword(searchInput.trim()))} />
          <Select value={eventTypeFilter} onValueChange={(v) => { setEventTypeFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[150px]"><SelectValue placeholder="事件类型" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部</SelectItem>
              <SelectItem value="AGENT_PUBLISH">Agent 发布</SelectItem>
              <SelectItem value="AGENT_DISABLE">Agent 禁用</SelectItem>
              <SelectItem value="TOOL_ENABLE">工具启用</SelectItem>
              <SelectItem value="TOOL_DISABLE">工具禁用</SelectItem>
              <SelectItem value="APPROVAL_DECISION">审批决策</SelectItem>
              <SelectItem value="ACL_CHANGE">ACL 变更</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={() => loadEvents(pageNo, keyword)}>
            <RefreshCw className="w-4 h-4 mr-1" />刷新
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : events.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无审计记录</div>
          ) : (
            <Table className="min-w-[900px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[120px]">操作人</TableHead>
                  <TableHead className="w-[140px]">事件类型</TableHead>
                  <TableHead className="w-[100px]">Agent</TableHead>
                  <TableHead className="w-[100px]">资源</TableHead>
                  <TableHead className="w-[160px]">时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {events.map((event) => (
                  <TableRow key={event.auditId} className="cursor-pointer hover:bg-slate-50" onClick={() => handleRowClick(event)}>
                    <TableCell className="font-medium">{event.actor || "-"}</TableCell>
                    <TableCell><Badge variant="outline">{event.eventType || "-"}</Badge></TableCell>
                    <TableCell className="text-sm text-muted-foreground">{event.agentId || "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{event.resource || "-"}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{event.timestamp ? new Date(event.timestamp).toLocaleString("zh-CN") : "-"}</TableCell>
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

      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="sm:max-w-[600px]">
          <DialogHeader><DialogTitle>审计详情</DialogTitle></DialogHeader>
          {selectedEvent && (
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div><span className="text-slate-500">操作人:</span> {selectedEvent.actor || "-"}</div>
                <div><span className="text-slate-500">事件类型:</span> {selectedEvent.eventType || "-"}</div>
                <div><span className="text-slate-500">Agent:</span> {selectedEvent.agentId || "-"}</div>
                <div><span className="text-slate-500">Run:</span> {selectedEvent.runId || "-"}</div>
              </div>
              {selectedEvent.payload && (
                <div>
                  <div className="text-sm text-slate-500 mb-1">Payload</div>
                  <pre className="bg-slate-50 p-3 rounded-lg text-xs font-mono overflow-auto max-h-[300px]">
                    {JSON.stringify(selectedEvent.payload, null, 2)}
                  </pre>
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
