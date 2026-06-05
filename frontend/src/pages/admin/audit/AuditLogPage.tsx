import { useEffect, useState } from "react";
import { RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import * as adminService from "@/services/adminService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 20;

const actionOptions = [
  { value: "all", label: "全部操作" },
  { value: "LOGIN_SUCCESS", label: "登录成功" },
  { value: "LOGIN_FAILED", label: "登录失败" },
  { value: "SUSPEND_TENANT", label: "暂停租户" },
  { value: "BAN_USER", label: "封禁用户" },
  { value: "PAYMENT_SUCCESS", label: "支付成功" },
  { value: "CREATE_AGENT", label: "创建 Agent" },
  { value: "DELETE_AGENT", label: "删除 Agent" },
  { value: "PUBLISH_AGENT", label: "发布 Agent" }
];

export function AuditLogPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AUDIT_LOG);

  const [logs, setLogs] = useState<adminService.AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [tenantId, setTenantId] = useState("");
  const [action, setAction] = useState("all");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [pageNo, setPageNo] = useState(1);

  const loadLogs = async () => {
    try {
      setLoading(true);
      const data = await adminService.queryAuditLogs({
        tenantId: tenantId.trim() || undefined,
        action: action !== "all" ? action : undefined,
        startTime: startTime || undefined,
        endTime: endTime || undefined,
        page: pageNo,
        size: PAGE_SIZE
      });
      setLogs(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载审计日志失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadLogs();
  }, [pageNo, action]);

  const handleSearch = () => {
    setPageNo(1);
    loadLogs();
  };

  const handleRefresh = () => {
    loadLogs();
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  const getActionBadge = (actionType?: string) => {
    if (!actionType) return <Badge variant="outline">-</Badge>;
    
    const isLogin = actionType.includes("LOGIN");
    const isSecurity = actionType.includes("SUSPEND") || actionType.includes("BAN");
    const isPayment = actionType.includes("PAYMENT");
    const isAgent = actionType.includes("AGENT");

    if (isLogin) {
      return <Badge className="bg-blue-100 text-blue-700">{actionType}</Badge>;
    }
    if (isSecurity) {
      return <Badge variant="destructive">{actionType}</Badge>;
    }
    if (isPayment) {
      return <Badge className="bg-green-100 text-green-700">{actionType}</Badge>;
    }
    if (isAgent) {
      return <Badge className="bg-purple-100 text-purple-700">{actionType}</Badge>;
    }
    return <Badge variant="secondary">{actionType}</Badge>;
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="审计日志" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">审计日志</h1>
          <p className="admin-page-subtitle">查看平台操作审计记录</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={handleRefresh}>
            <RefreshCw className="w-4 h-4 mr-1" />
            刷新
          </Button>
        </div>
      </div>

      {/* Filter Bar */}
      <Card className="mb-4">
        <CardContent className="pt-6">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
            <div>
              <label className="text-sm font-medium mb-2 block">租户 ID</label>
              <Input
                value={tenantId}
                onChange={(e) => setTenantId(e.target.value)}
                placeholder="输入租户 ID"
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-2 block">操作类型</label>
              <Select value={action} onValueChange={setAction}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {actionOptions.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <label className="text-sm font-medium mb-2 block">开始时间</label>
              <Input
                type="datetime-local"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-2 block">结束时间</label>
              <Input
                type="datetime-local"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
            </div>
            <div className="flex items-end">
              <Button onClick={handleSearch} className="w-full">
                <Search className="w-4 h-4 mr-1" />
                搜索
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Logs Table */}
      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : logs.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无日志</div>
          ) : (
            <Table className="min-w-[1400px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[160px]">时间</TableHead>
                  <TableHead className="w-[160px]">租户 ID</TableHead>
                  <TableHead className="w-[120px]">操作者</TableHead>
                  <TableHead className="w-[160px]">操作</TableHead>
                  <TableHead className="w-[120px]">资源类型</TableHead>
                  <TableHead className="w-[160px]">资源 ID</TableHead>
                  <TableHead>详情</TableHead>
                  <TableHead className="w-[120px]">IP 地址</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {logs.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatTime(log.createdAt)}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{log.tenantId}</TableCell>
                    <TableCell>{log.operator}</TableCell>
                    <TableCell>{getActionBadge(log.action)}</TableCell>
                    <TableCell>{log.resourceType || "-"}</TableCell>
                    <TableCell className="font-mono text-xs">{log.resourceId || "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground max-w-[300px] truncate">
                      {log.detail || "-"}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{log.ipAddress || "-"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Pagination */}
      {logs.length > 0 && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <span>共 {logs.length} 条</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.max(1, prev - 1))}
              disabled={pageNo === 1}
            >
              上一页
            </Button>
            <span>第 {pageNo} 页</span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => prev + 1)}
              disabled={logs.length < PAGE_SIZE}
            >
              下一页
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
