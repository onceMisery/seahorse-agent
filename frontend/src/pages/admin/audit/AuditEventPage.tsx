import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
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

const EVENT_TYPE_LABELS: Record<string, string> = {
  AGENT_PUBLISHED: "Agent 发布",
  AGENT_PUBLISH_VALIDATED: "发布校验",
  AGENT_ROLLED_BACK: "Agent 回滚",
  RUN_STARTED: "运行开始",
  RUN_FINISHED: "运行结束",
  TOOL_POLICY_DECIDED: "工具策略",
  TOOL_INVOKED: "工具调用",
  APPROVAL_DECIDED: "审批决策",
  RESOURCE_ACL_CHANGED: "资源权限",
  SECRET_USED: "密钥使用",
  CONNECTOR_IMPORTED: "连接器导入",
  CONNECTOR_CREDENTIAL_BOUND: "凭据绑定",
  CONNECTOR_OPERATION_ENABLED: "连接器启用",
  CONNECTOR_OPERATION_DISABLED: "连接器禁用",
  SANDBOX_SESSION_CREATED: "沙箱创建",
  SANDBOX_EXECUTION_FINISHED: "沙箱执行",
  AGENT_HANDOFF_CREATED: "转交创建",
  AGENT_HANDOFF_FINISHED: "转交完成",
  CONTEXT_ACCESSED: "内部上下文访问"
};

const displayActor = (event: AuditEvent) => event.actor || event.actorId || event.actorType || "-";
const displayResource = (event: AuditEvent) => event.resource || event.resourceId || event.resourceType || "-";
const displayTime = (event: AuditEvent) => {
  const value = event.timestamp || event.occurredAt;
  return value ? new Date(value).toLocaleString("zh-CN") : "-";
};
const displayPayload = (event: AuditEvent) => {
  if (event.payload) return event.payload;
  if (!event.redactedPayload) return null;
  try {
    return JSON.parse(event.redactedPayload);
  } catch {
    return event.redactedPayload;
  }
};

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
    } catch {
      setSelectedEvent(event);
    } finally {
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
          <Input
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="搜索操作人"
            className="w-[180px]"
            onKeyDown={(event) => event.key === "Enter" && (setPageNo(1), setKeyword(searchInput.trim()))}
          />
          <Select value={eventTypeFilter} onValueChange={(value) => { setEventTypeFilter(value); setPageNo(1); }}>
            <SelectTrigger className="w-[170px]"><SelectValue placeholder="事件类型" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部业务事件</SelectItem>
              <SelectItem value="RUN_STARTED">运行开始</SelectItem>
              <SelectItem value="RUN_FINISHED">运行结束</SelectItem>
              <SelectItem value="TOOL_INVOKED">工具调用</SelectItem>
              <SelectItem value="APPROVAL_DECIDED">审批决策</SelectItem>
              <SelectItem value="RESOURCE_ACL_CHANGED">资源权限</SelectItem>
              <SelectItem value="SECRET_USED">密钥使用</SelectItem>
              <SelectItem value="CONTEXT_ACCESSED">内部上下文访问</SelectItem>
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
                  <TableHead className="w-[160px]">资源</TableHead>
                  <TableHead className="w-[160px]">时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {events.map((event) => (
                  <TableRow key={event.auditId} className="cursor-pointer hover:bg-slate-50" onClick={() => handleRowClick(event)}>
                    <TableCell className="font-medium">{displayActor(event)}</TableCell>
                    <TableCell><Badge variant="outline">{EVENT_TYPE_LABELS[event.eventType || ""] || event.eventType || "-"}</Badge></TableCell>
                    <TableCell className="text-sm text-muted-foreground">{event.agentId || "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{displayResource(event)}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{displayTime(event)}</TableCell>
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
            <Button variant="outline" size="sm" onClick={() => setPageNo((page) => Math.max(1, page - 1))} disabled={pageData.current <= 1}>上一页</Button>
            <span>{pageData.current} / {pageData.pages}</span>
            <Button variant="outline" size="sm" onClick={() => setPageNo((page) => Math.min(pageData.pages || 1, page + 1))} disabled={pageData.current >= pageData.pages}>下一页</Button>
          </div>
        </div>
      )}

      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="sm:max-w-[600px]">
          <DialogHeader><DialogTitle>审计详情</DialogTitle></DialogHeader>
          {selectedEvent && (
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div><span className="text-slate-500">操作人:</span> {displayActor(selectedEvent)}</div>
                <div><span className="text-slate-500">事件类型:</span> {EVENT_TYPE_LABELS[selectedEvent.eventType || ""] || selectedEvent.eventType || "-"}</div>
                <div><span className="text-slate-500">Agent:</span> {selectedEvent.agentId || "-"}</div>
                <div><span className="text-slate-500">Run ID:</span> {selectedEvent.runId || "-"}</div>
                <div><span className="text-slate-500">资源:</span> {displayResource(selectedEvent)}</div>
                <div><span className="text-slate-500">时间:</span> {displayTime(selectedEvent)}</div>
              </div>
              {displayPayload(selectedEvent) && (
                <div>
                  <div className="text-sm text-slate-500 mb-1">载荷</div>
                  <pre className="bg-slate-50 p-3 rounded-lg text-xs font-mono overflow-auto max-h-[300px]">
                    {typeof displayPayload(selectedEvent) === "string"
                      ? displayPayload(selectedEvent)
                      : JSON.stringify(displayPayload(selectedEvent), null, 2)}
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
