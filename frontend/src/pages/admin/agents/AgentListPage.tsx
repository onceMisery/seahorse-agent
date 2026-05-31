import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, RefreshCw, Search } from "lucide-react";
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
import { listAgents, disableAgent, type AgentDefinition } from "@/services/agentDefinitionService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

const statusOptions = [
  { value: "all", label: "全部状态" },
  { value: "DRAFT", label: "草稿" },
  { value: "PUBLISHED", label: "已发布" },
  { value: "DISABLED", label: "已禁用" }
];

const riskOptions = [
  { value: "all", label: "全部风险" },
  { value: "LOW", label: "低" },
  { value: "MEDIUM", label: "中" },
  { value: "HIGH", label: "高" }
];

export function AgentListPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_DEFINITION_MANAGEMENT);
  const navigate = useNavigate();

  const [pageData, setPageData] = useState<PageResult<AgentDefinition> | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [statusFilter, setStatusFilter] = useState("all");
  const [riskFilter, setRiskFilter] = useState("all");
  const [disabling, setDisabling] = useState<string | null>(null);

  const agents = pageData?.records || [];

  const loadAgents = async (current = pageNo, kw = keyword) => {
    try {
      setLoading(true);
      const data = await listAgents({
        current,
        size: PAGE_SIZE,
        keyword: kw || undefined,
        status: statusFilter !== "all" ? statusFilter : undefined,
        riskLevel: riskFilter !== "all" ? riskFilter : undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 Agent 列表失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadAgents();
  }, [pageNo, keyword, statusFilter, riskFilter]);

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchInput.trim());
  };

  const handleRefresh = () => {
    setPageNo(1);
    loadAgents(1, keyword);
  };

  const handleDisable = async (agentId: string) => {
    try {
      setDisabling(agentId);
      await disableAgent(agentId);
      toast.success("已禁用 Agent");
      await loadAgents(pageNo, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "禁用 Agent 失败"));
      console.error(error);
    } finally {
      setDisabling(null);
    }
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case "PUBLISHED":
        return <Badge className="bg-green-100 text-green-700">已发布</Badge>;
      case "DRAFT":
        return <Badge variant="secondary">草稿</Badge>;
      case "DISABLED":
        return <Badge variant="destructive">已禁用</Badge>;
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
    return <FeatureUnavailableState featureState={featureState} featureName="Agent 管理" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Agent 管理</h1>
          <p className="admin-page-subtitle">管理 Agent 生命周期，包括创建、编辑、发布与禁用</p>
        </div>
        <div className="admin-page-actions">
          <Input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="搜索 Agent 名称"
            className="w-[200px]"
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
          />
          <Button variant="outline" onClick={handleSearch}>
            <Search className="w-4 h-4 mr-1" />
            搜索
          </Button>
          <Select value={statusFilter} onValueChange={(v) => { setStatusFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[130px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {statusOptions.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={riskFilter} onValueChange={(v) => { setRiskFilter(v); setPageNo(1); }}>
            <SelectTrigger className="w-[130px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {riskOptions.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={handleRefresh}>
            <RefreshCw className="w-4 h-4 mr-1" />
            刷新
          </Button>
          <Button className="admin-primary-gradient" onClick={() => navigate("/admin/agents/new")}>
            <Plus className="w-4 h-4 mr-1" />
            创建 Agent
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : agents.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无 Agent</div>
          ) : (
            <Table className="min-w-[1000px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[200px]">名称</TableHead>
                  <TableHead className="w-[100px]">状态</TableHead>
                  <TableHead className="w-[80px]">风险</TableHead>
                  <TableHead className="w-[100px]">版本</TableHead>
                  <TableHead className="w-[120px]">发布状态</TableHead>
                  <TableHead className="w-[80px]">工具数</TableHead>
                  <TableHead className="w-[160px]">更新时间</TableHead>
                  <TableHead className="w-[200px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {agents.map((agent) => (
                  <TableRow key={agent.agentId}>
                    <TableCell>
                      <div>
                        <div className="font-medium text-slate-900">{agent.name || "-"}</div>
                        <div className="text-xs text-slate-400 truncate max-w-[180px]">{agent.agentId}</div>
                      </div>
                    </TableCell>
                    <TableCell>{getStatusBadge(agent.status)}</TableCell>
                    <TableCell>{getRiskBadge(agent.riskLevel)}</TableCell>
                    <TableCell className="text-muted-foreground">v{agent.currentVersionNumber ?? "-"}</TableCell>
                    <TableCell>
                      <span className="text-sm text-muted-foreground">{agent.latestPublishStatus || "-"}</span>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{agent.toolCount ?? 0}</TableCell>
                    <TableCell className="text-muted-foreground text-xs">{formatTime(agent.updateTime)}</TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button variant="outline" size="sm" onClick={() => navigate(`/admin/agents/${agent.agentId}`)}>
                          详情
                        </Button>
                        <Button variant="outline" size="sm" onClick={() => navigate(`/admin/agents/${agent.agentId}/edit`)}>
                          编辑
                        </Button>
                        {agent.status === "PUBLISHED" && (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            disabled={disabling === agent.agentId}
                            onClick={() => {
                              if (confirm("确认禁用此 Agent？禁用后将停止服务。")) {
                                handleDisable(agent.agentId!);
                              }
                            }}
                          >
                            禁用
                          </Button>
                        )}
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
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.max(1, prev - 1))}
              disabled={pageData.current <= 1}
            >
              上一页
            </Button>
            <span>{pageData.current} / {pageData.pages}</span>
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
    </div>
  );
}
