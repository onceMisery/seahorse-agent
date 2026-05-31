import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { getTool, enableTool, disableTool, listToolInvocations, type ToolItem, type ToolInvocation } from "@/services/toolCatalogService";
import { ToolRiskBadge } from "./components/ToolRiskBadge";
import { getErrorMessage } from "@/utils/error";

export function ToolDetailPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.TOOL_CATALOG_MANAGEMENT);
  const { toolId } = useParams<{ toolId: string }>();
  const navigate = useNavigate();

  const [tool, setTool] = useState<ToolItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [invocations, setInvocations] = useState<ToolInvocation[]>([]);
  const [toggling, setToggling] = useState(false);

  const loadTool = async () => {
    if (!toolId) return;
    try {
      setLoading(true);
      const data = await getTool(toolId);
      setTool(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载工具详情失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const loadInvocations = async () => {
    if (!toolId) return;
    try {
      const data = await listToolInvocations({ toolId, current: 1, size: 20 });
      setInvocations(data?.records || []);
    } catch (error) {
      console.error(error);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    loadTool();
    loadInvocations();
  }, [toolId]);

  const handleToggle = async () => {
    if (!toolId || !tool) return;
    const action = tool.enabled ? "禁用" : "启用";
    if (!confirm(`确认${action}此工具？`)) return;

    try {
      setToggling(true);
      if (tool.enabled) {
        await disableTool(toolId);
      } else {
        await enableTool(toolId);
      }
      toast.success(`工具已${action}`);
      await loadTool();
    } catch (error) {
      toast.error(getErrorMessage(error, `${action}工具失败`));
      console.error(error);
    } finally {
      setToggling(false);
    }
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="工具目录" />;
  }

  if (loading) {
    return <div className="admin-page"><div className="text-center py-8 text-muted-foreground">加载中...</div></div>;
  }

  if (!tool) {
    return (
      <div className="admin-page">
        <div className="text-center py-8 text-muted-foreground">
          <p>工具不存在或已被删除</p>
          <Button variant="outline" className="mt-4" onClick={() => navigate("/admin/tools")}>返回列表</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => navigate("/admin/tools")}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="admin-page-title">{tool.name || "工具详情"}</h1>
            <p className="admin-page-subtitle">ID: {toolId}</p>
          </div>
        </div>
        <div className="admin-page-actions">
          <Button
            variant={tool.enabled ? "destructive" : "default"}
            disabled={toggling}
            onClick={handleToggle}
          >
            {toggling ? "操作中..." : tool.enabled ? "禁用" : "启用"}
          </Button>
          <Button variant="outline" onClick={() => { loadTool(); loadInvocations(); }}>
            <RefreshCw className="w-4 h-4 mr-1" />
            刷新
          </Button>
        </div>
      </div>

      {/* 摘要区 */}
      <Card className="mb-4">
        <CardContent className="pt-6">
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
            <div>
              <div className="text-xs text-slate-500">Provider</div>
              <div className="mt-1 font-medium">{tool.provider || "-"}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500">资源类型</div>
              <div className="mt-1">{tool.resourceType || "-"}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500">风险等级</div>
              <div className="mt-1"><ToolRiskBadge riskLevel={tool.riskLevel} /></div>
            </div>
            <div>
              <div className="text-xs text-slate-500">状态</div>
              <div className="mt-1">
                {tool.enabled ? <Badge className="bg-green-100 text-green-700">已启用</Badge> : <Badge variant="secondary">已禁用</Badge>}
              </div>
            </div>
            <div>
              <div className="text-xs text-slate-500">审批要求</div>
              <div className="mt-1">
                {tool.approvalRequired ? <Badge variant="destructive">需要审批</Badge> : <Badge variant="secondary">免审批</Badge>}
              </div>
            </div>
          </div>
          {tool.description && (
            <div className="mt-4">
              <div className="text-xs text-slate-500">描述</div>
              <div className="mt-1 text-sm text-slate-700">{tool.description}</div>
            </div>
          )}
        </CardContent>
      </Card>

      <Tabs defaultValue="schema">
        <TabsList>
          <TabsTrigger value="schema">参数 Schema</TabsTrigger>
          <TabsTrigger value="output">输出 Schema</TabsTrigger>
          <TabsTrigger value="invocations">调用记录</TabsTrigger>
        </TabsList>

        <TabsContent value="schema">
          <Card>
            <CardHeader><CardTitle>参数 Schema</CardTitle></CardHeader>
            <CardContent>
              {tool.parameterSchema ? (
                <pre className="bg-slate-50 p-4 rounded-lg text-sm font-mono overflow-auto max-h-[500px]">
                  {JSON.stringify(tool.parameterSchema, null, 2)}
                </pre>
              ) : (
                <div className="text-center py-4 text-muted-foreground">暂无参数 Schema</div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="output">
          <Card>
            <CardHeader><CardTitle>输出 Schema</CardTitle></CardHeader>
            <CardContent>
              {tool.outputSchema ? (
                <pre className="bg-slate-50 p-4 rounded-lg text-sm font-mono overflow-auto max-h-[500px]">
                  {JSON.stringify(tool.outputSchema, null, 2)}
                </pre>
              ) : (
                <div className="text-center py-4 text-muted-foreground">暂无输出 Schema</div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="invocations">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center justify-between">
                <span>最近调用记录</span>
                <Button variant="outline" size="sm" onClick={() => navigate("/admin/tool-invocations")}>
                  查看全部审计
                </Button>
              </CardTitle>
            </CardHeader>
            <CardContent>
              {invocations.length === 0 ? (
                <div className="text-center py-4 text-muted-foreground">暂无调用记录</div>
              ) : (
                <div className="space-y-2">
                  {invocations.map((inv) => (
                    <div key={inv.invocationId} className="flex items-center justify-between p-3 bg-slate-50 rounded-lg">
                      <div>
                        <span className="font-medium">{inv.toolName || "-"}</span>
                        <span className="ml-2 text-sm text-muted-foreground">{inv.status || "-"}</span>
                      </div>
                      <div className="text-sm text-muted-foreground">{formatTime(inv.createTime)}</div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
