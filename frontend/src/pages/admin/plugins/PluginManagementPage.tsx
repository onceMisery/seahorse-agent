import { useEffect, useState } from "react";
import { RefreshCw, CheckCircle, XCircle } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { listPluginStatuses, getPluginHealth, savePluginStatus, type PluginStatus, type PluginHealthSummary } from "@/services/pluginService";
import { getErrorMessage } from "@/utils/error";

export function PluginManagementPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.MCP_TOOL);
  const [plugins, setPlugins] = useState<PluginStatus[]>([]);
  const [health, setHealth] = useState<PluginHealthSummary | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [statuses, healthData] = await Promise.all([
        listPluginStatuses().catch(() => []),
        getPluginHealth().catch(() => null)
      ]);
      setPlugins(Array.isArray(statuses) ? statuses : []);
      setHealth(healthData);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载插件数据失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!featureState.enabled) return;
    fetchData();
  }, [featureState.enabled]);

  const handleToggle = async (plugin: PluginStatus) => {
    try {
      await savePluginStatus({ ...plugin, enabled: !plugin.enabled, updatedBy: "admin" });
      toast.success(`插件 ${plugin.name} 已${plugin.enabled ? "禁用" : "启用"}`);
      fetchData();
    } catch (error) {
      toast.error(getErrorMessage(error, "操作失败"));
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="插件管理" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">插件管理</h1>
          <p className="admin-page-subtitle">管理 Feature Plugin 注册表、健康状态和启停</p>
        </div>
        <Button variant="outline" onClick={fetchData} disabled={loading}>
          <RefreshCw className={`mr-1 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          刷新
        </Button>
      </div>

      {/* Health summary */}
      {health ? (
        <div className="flex gap-4">
          <Card className="flex-1">
            <CardContent className="flex items-center gap-3 pt-6">
              <CheckCircle className="h-8 w-8 text-emerald-500" />
              <div>
                <div className="text-2xl font-bold">{health.healthyCount ?? 0}</div>
                <div className="text-xs text-slate-500">健康</div>
              </div>
            </CardContent>
          </Card>
          <Card className="flex-1">
            <CardContent className="flex items-center gap-3 pt-6">
              <XCircle className="h-8 w-8 text-red-500" />
              <div>
                <div className="text-2xl font-bold">{health.unhealthyCount ?? 0}</div>
                <div className="text-xs text-slate-500">异常</div>
              </div>
            </CardContent>
          </Card>
          <Card className="flex-1">
            <CardContent className="flex items-center gap-3 pt-6">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-sm font-bold text-slate-600">
                {health.totalFeatures ?? plugins.length}
              </div>
              <div>
                <div className="text-2xl font-bold">{health.totalFeatures ?? plugins.length}</div>
                <div className="text-xs text-slate-500">总计</div>
              </div>
            </CardContent>
          </Card>
        </div>
      ) : null}

      {/* Plugin table */}
      <Card>
        <CardContent className="pt-6">
          {plugins.length === 0 && !loading ? (
            <div className="py-8 text-center text-muted-foreground">暂无插件数据</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>名称</TableHead>
                  <TableHead>端口类型</TableHead>
                  <TableHead>功能类型</TableHead>
                  <TableHead>版本</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>健康</TableHead>
                  <TableHead>操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {plugins.map((p, i) => (
                  <TableRow key={p.name ?? i}>
                    <TableCell className="font-medium">{p.name ?? "-"}</TableCell>
                    <TableCell className="text-xs">{p.portType ?? "-"}</TableCell>
                    <TableCell className="text-xs">{p.featureType ?? "-"}</TableCell>
                    <TableCell className="font-mono text-xs">{p.version ?? "-"}</TableCell>
                    <TableCell>
                      <Badge className={p.enabled ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}>
                        {p.enabled ? "启用" : "禁用"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {p.healthy ? (
                        <CheckCircle className="h-4 w-4 text-emerald-500" />
                      ) : (
                        <XCircle className="h-4 w-4 text-red-500" />
                      )}
                    </TableCell>
                    <TableCell>
                      <Button variant="ghost" size="sm" onClick={() => handleToggle(p)}>
                        {p.enabled ? "禁用" : "启用"}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
