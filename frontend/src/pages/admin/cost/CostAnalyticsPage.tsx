import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { aggregateCostUsage, type CostAggregate } from "@/services/auditCostService";
import { getErrorMessage } from "@/utils/error";

export function CostAnalyticsPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.COST_ANALYTICS);

  const [aggregate, setAggregate] = useState<CostAggregate | null>(null);
  const [loading, setLoading] = useState(false);
  const [groupBy, setGroupBy] = useState("agent");
  const [tenantId, setTenantId] = useState("");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");

  const loadAggregate = async () => {
    try {
      setLoading(true);
      const data = await aggregateCostUsage({
        tenantId: tenantId || undefined,
        startTime: startTime || undefined,
        endTime: endTime || undefined,
        groupBy
      });
      setAggregate(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载成本数据失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (featureState.enabled) loadAggregate();
  }, [featureState.enabled, groupBy]);

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="成本分析" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">成本分析</h1>
          <p className="admin-page-subtitle">查看和分析系统使用成本</p>
        </div>
      </div>

      <Card className="mb-4">
        <CardContent className="pt-6">
          <div className="flex flex-wrap items-end gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">分组维度</label>
              <Select value={groupBy} onValueChange={setGroupBy}>
                <SelectTrigger className="w-[150px]"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="agent">按 Agent</SelectItem>
                  <SelectItem value="model">按模型</SelectItem>
                  <SelectItem value="tool">按工具</SelectItem>
                  <SelectItem value="tenant">按租户</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">租户 ID</label>
              <Input value={tenantId} onChange={(e) => setTenantId(e.target.value)} placeholder="可选" className="w-[150px]" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">开始时间</label>
              <Input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} className="w-[200px]" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">结束时间</label>
              <Input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} className="w-[200px]" />
            </div>
            <Button variant="outline" onClick={loadAggregate} disabled={loading}>
              <RefreshCw className="w-4 h-4 mr-1" />
              {loading ? "加载中..." : "查询"}
            </Button>
          </div>
        </CardContent>
      </Card>

      {aggregate && (
        <div className="grid grid-cols-3 gap-4">
          <Card>
            <CardContent className="pt-6">
              <div className="text-slate-500 text-sm">总成本</div>
              <div className="text-2xl font-bold">${(aggregate.totalCost ?? 0).toFixed(4)}</div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <div className="text-slate-500 text-sm">总 Token 数</div>
              <div className="text-2xl font-bold">{(aggregate.totalTokens ?? 0).toLocaleString()}</div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6">
              <div className="text-slate-500 text-sm">总调用次数</div>
              <div className="text-2xl font-bold">{(aggregate.totalCalls ?? 0).toLocaleString()}</div>
            </CardContent>
          </Card>
        </div>
      )}

      {aggregate && groupBy === "agent" && aggregate.byAgent && (
        <Card className="mt-4">
          <CardHeader><CardTitle>按 Agent 统计</CardTitle></CardHeader>
          <CardContent>
            <div className="space-y-2">
              {aggregate.byAgent.map((item, idx) => (
                <div key={idx} className="flex items-center justify-between p-3 bg-slate-50 rounded-lg">
                  <span className="font-medium">{item.agentId || "-"}</span>
                  <div className="flex items-center gap-4 text-sm">
                    <span className="text-muted-foreground">{item.calls} 次调用</span>
                    <span className="font-medium">${(item.cost ?? 0).toFixed(4)}</span>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {aggregate && groupBy === "model" && aggregate.byModel && (
        <Card className="mt-4">
          <CardHeader><CardTitle>按模型统计</CardTitle></CardHeader>
          <CardContent>
            <div className="space-y-2">
              {aggregate.byModel.map((item, idx) => (
                <div key={idx} className="flex items-center justify-between p-3 bg-slate-50 rounded-lg">
                  <span className="font-medium">{item.model || "-"}</span>
                  <div className="flex items-center gap-4 text-sm">
                    <span className="text-muted-foreground">{item.calls} 次调用</span>
                    <span className="font-medium">${(item.cost ?? 0).toFixed(4)}</span>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
