import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { RefreshCw, RotateCcw, Search, Wrench } from "lucide-react";
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
import { listTools, enableTool, disableTool, type ToolItem } from "@/services/toolCatalogService";
import {
  listMcpServers,
  refreshMcpServerTools,
  restartMcpServer,
  testMcpServer,
  type McpServerStatus,
  type McpServerTestResult
} from "@/services/mcpServerService";
import { ToolRiskBadge } from "./components/ToolRiskBadge";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;
const DEFERRED_TOOL_SEARCH_ID = "tool_search";

function isDeferredDiscoveryTool(tool: ToolItem) {
  return tool.toolId === DEFERRED_TOOL_SEARCH_ID;
}

function mcpStatusVariant(status?: string): "default" | "destructive" | "secondary" | "outline" {
  if (status === "READY") return "default";
  if (status === "FAILED") return "destructive";
  if (status === "DISABLED") return "secondary";
  return "outline";
}

function mcpStatusLabel(status?: string) {
  if (status === "READY") return "就绪";
  if (status === "FAILED") return "失败";
  if (status === "DISABLED") return "已禁用";
  return status || "-";
}

export function ToolCatalogPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.TOOL_CATALOG_MANAGEMENT);
  const navigate = useNavigate();

  const [pageData, setPageData] = useState<PageResult<ToolItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [providerFilter, setProviderFilter] = useState("all");
  const [riskFilter, setRiskFilter] = useState("all");
  const [enabledFilter, setEnabledFilter] = useState("all");
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [mcpServers, setMcpServers] = useState<McpServerStatus[]>([]);
  const [mcpLoading, setMcpLoading] = useState(true);
  const [mcpTestingName, setMcpTestingName] = useState<string | null>(null);
  const [mcpActionName, setMcpActionName] = useState<string | null>(null);
  const [mcpTestResults, setMcpTestResults] = useState<Record<string, McpServerTestResult>>({});

  const tools = pageData?.records || [];

  const loadTools = async (current = pageNo, kw = keyword) => {
    try {
      setLoading(true);
      const data = await listTools({
        current,
        size: PAGE_SIZE,
        keyword: kw || undefined,
        provider: providerFilter !== "all" ? providerFilter : undefined,
        riskLevel: riskFilter !== "all" ? riskFilter : undefined,
        enabled: enabledFilter === "enabled" ? true : enabledFilter === "disabled" ? false : undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载工具列表失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const loadMcpServers = async () => {
    try {
      setMcpLoading(true);
      setMcpServers(await listMcpServers());
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 MCP Server 状态失败"));
      console.error(error);
    } finally {
      setMcpLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadTools();
  }, [pageNo, keyword, providerFilter, riskFilter, enabledFilter]);

  useEffect(() => {
    if (!featureState.enabled) return;
    loadMcpServers();
  }, []);

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchInput.trim());
  };

  const handleRefresh = () => {
    setPageNo(1);
    loadTools(1, keyword);
    loadMcpServers();
  };

  const handleMcpTest = async (serverName: string) => {
    try {
      setMcpTestingName(serverName);
      const result = await testMcpServer(serverName);
      setMcpTestResults((prev) => ({ ...prev, [serverName]: result }));
      if (result.success) {
        toast.success("MCP 服务测试成功");
      } else {
        toast.error(result.message || "MCP 服务测试失败");
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "MCP 服务测试失败"));
      console.error(error);
    } finally {
      setMcpTestingName(null);
    }
  };

  const handleMcpRestart = async (serverName: string) => {
    try {
      setMcpActionName(`restart:${serverName}`);
      await restartMcpServer(serverName);
      await loadMcpServers();
      toast.success("MCP 服务已重启");
    } catch (error) {
      toast.error(getErrorMessage(error, "MCP 服务重启失败"));
      console.error(error);
    } finally {
      setMcpActionName(null);
    }
  };

  const handleMcpRefreshTools = async (serverName: string) => {
    try {
      setMcpActionName(`refresh:${serverName}`);
      await refreshMcpServerTools(serverName);
      await loadMcpServers();
      toast.success("MCP 工具已刷新");
    } catch (error) {
      toast.error(getErrorMessage(error, "MCP 工具刷新失败"));
      console.error(error);
    } finally {
      setMcpActionName(null);
    }
  };

  const handleToggle = async (toolId: string, currentEnabled: boolean) => {
    const action = currentEnabled ? "禁用" : "启用";
    if (!confirm(`确认${action}此工具？${currentEnabled ? "禁用后关联 Agent 将无法使用" : "影响范围请在绑定页确认"}`)) {
      return;
    }

    try {
      setTogglingId(toolId);
      if (currentEnabled) {
        await disableTool(toolId);
      } else {
        await enableTool(toolId);
      }
      toast.success(`工具已${action}`);
      await loadTools(pageNo, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, `${action}工具失败`));
      console.error(error);
    } finally {
      setTogglingId(null);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="工具目录" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">工具目录</h1>
          <p className="admin-page-subtitle">管理和监控 Agent 可使用的工具</p>
          <p className="mt-1 text-xs text-muted-foreground">
            tool_search 只暴露已授权工具的元数据；普通工具仍由 Agent 绑定与运行时策略决定。
          </p>
        </div>
        <div className="admin-page-actions">
          <Input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="搜索工具名称"
            className="w-[180px]"
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
          />
          <Button variant="outline" onClick={handleSearch}>
            <Search className="w-4 h-4 mr-1" />
            搜索
          </Button>
          <Select value={providerFilter} onValueChange={(v) => { setProviderFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[130px]"><SelectValue placeholder="来源" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部来源</SelectItem>
              <SelectItem value="BUILTIN">Built-in</SelectItem>
              <SelectItem value="MCP">MCP</SelectItem>
              <SelectItem value="OPENAPI">OpenAPI</SelectItem>
              <SelectItem value="A2A">A2A</SelectItem>
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
          <Select value={enabledFilter} onValueChange={(v) => { setEnabledFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[120px]"><SelectValue placeholder="启用状态" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部</SelectItem>
              <SelectItem value="enabled">已启用</SelectItem>
              <SelectItem value="disabled">已禁用</SelectItem>
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
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-slate-900">MCP 服务</h2>
              <p className="text-xs text-muted-foreground">发现状态与诊断输出</p>
            </div>
            <Badge variant="secondary">{mcpServers.length} 个服务</Badge>
          </div>
          {mcpLoading ? (
            <div className="text-sm text-muted-foreground">正在加载 MCP 服务...</div>
          ) : mcpServers.length === 0 ? (
            <div className="text-sm text-muted-foreground">暂无 MCP 服务</div>
          ) : (
            <div className="grid gap-3 md:grid-cols-2">
              {mcpServers.map((server) => (
                <div key={server.name} className="rounded-md border border-border p-4">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <div className="font-medium text-slate-900">{server.name}</div>
                      <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                        <span>{server.transport || "-"}</span>
                        <span>{server.toolCount ?? server.tools?.length ?? 0} 个工具</span>
                        <span>{server.enabled ? "已启用" : "已禁用"}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={mcpTestingName === server.name}
                        onClick={() => handleMcpTest(server.name)}
                      >
                        测试
                      </Button>
                      <Button
                        aria-label={`restart-${server.name}`}
                        variant="outline"
                        size="sm"
                        disabled={mcpActionName === `restart:${server.name}`}
                        onClick={() => handleMcpRestart(server.name)}
                      >
                        <RotateCcw className="h-3.5 w-3.5" />
                        重启
                      </Button>
                      <Button
                        aria-label={`refresh-tools-${server.name}`}
                        variant="outline"
                        size="sm"
                        disabled={mcpActionName === `refresh:${server.name}`}
                        onClick={() => handleMcpRefreshTools(server.name)}
                      >
                        <Wrench className="h-3.5 w-3.5" />
                        刷新工具
                      </Button>
                      <Badge variant={mcpStatusVariant(server.status)}>{mcpStatusLabel(server.status)}</Badge>
                    </div>
                  </div>
                  {server.tools && server.tools.length > 0 ? (
                    <div className="mt-3 flex flex-wrap gap-1">
                      {server.tools.map((tool, index) => (
                        <Badge key={`${tool.toolId ?? "tool"}-${index}`} variant="outline">
                          {tool.toolId}
                        </Badge>
                      ))}
                    </div>
                  ) : null}
                  {server.stderrTail ? (
                    <pre className="mt-3 max-h-24 overflow-auto whitespace-pre-wrap break-words rounded-md bg-muted p-2 text-xs text-muted-foreground">
                      {server.stderrTail}
                    </pre>
                  ) : null}
                  {mcpTestResults[server.name] ? (
                    <pre className="mt-3 max-h-24 overflow-auto whitespace-pre-wrap break-words rounded-md border border-border bg-background p-2 text-xs text-muted-foreground">
                      {mcpTestResults[server.name].content || mcpTestResults[server.name].message || "-"}
                    </pre>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : tools.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无工具</div>
          ) : (
            <Table className="min-w-[900px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[200px]">名称</TableHead>
                  <TableHead className="w-[120px]">供应商</TableHead>
                  <TableHead className="w-[100px]">资源类型</TableHead>
                  <TableHead className="w-[100px]">风险等级</TableHead>
                  <TableHead className="w-[100px]">状态</TableHead>
                  <TableHead className="w-[100px]">审批要求</TableHead>
                  <TableHead className="w-[120px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tools.map((tool) => (
                  <TableRow key={tool.toolId}>
                    <TableCell>
                      <div className="font-medium text-slate-900 cursor-pointer hover:text-indigo-600" onClick={() => navigate(`/admin/tools/${tool.toolId}`)}>
                        {tool.name || "-"}
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{tool.provider || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">
                      <div className="flex flex-wrap items-center gap-1">
                        <span>{tool.resourceType || "-"}</span>
                        {isDeferredDiscoveryTool(tool) ? <Badge variant="secondary">延迟发现</Badge> : null}
                      </div>
                    </TableCell>
                    <TableCell><ToolRiskBadge riskLevel={tool.riskLevel} /></TableCell>
                    <TableCell>
                      {tool.enabled ? <Badge className="bg-green-100 text-green-700">已启用</Badge> : <Badge variant="secondary">已禁用</Badge>}
                    </TableCell>
                    <TableCell>
                      {tool.approvalRequired ? <Badge variant="destructive">需要审批</Badge> : <Badge variant="secondary">免审批</Badge>}
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button variant="outline" size="sm" onClick={() => navigate(`/admin/tools/${tool.toolId}`)}>
                          详情
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className={tool.enabled ? "text-destructive hover:text-destructive" : "text-green-600 hover:text-green-600"}
                          disabled={togglingId === tool.toolId}
                          onClick={() => handleToggle(tool.toolId!, !!tool.enabled)}
                        >
                          {tool.enabled ? "禁用" : "启用"}
                        </Button>
                      </div>
                    </TableCell>
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
            <Button variant="outline" size="sm" onClick={() => setPageNo((prev) => Math.max(1, prev - 1))} disabled={pageData.current <= 1}>
              上一页
            </Button>
            <span>{pageData.current} / {pageData.pages}</span>
            <Button variant="outline" size="sm" onClick={() => setPageNo((prev) => Math.min(pageData.pages || 1, prev + 1))} disabled={pageData.current >= pageData.pages}>
              下一页
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
