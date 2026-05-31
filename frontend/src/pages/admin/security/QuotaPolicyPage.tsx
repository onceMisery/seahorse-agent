import { useEffect, useState } from "react";
import { Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import type { PageResult } from "@/services/metadataGovernanceService";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  createQuotaPolicy,
  disableQuotaPolicy,
  evaluateQuotaDecision,
  type QuotaPolicy,
  type QuotaDecisionEvaluation
} from "@/services/securityGovernanceService";
import { getErrorMessage } from "@/utils/error";

export function QuotaPolicyPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.QUOTA_MANAGEMENT);

  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [simResult, setSimResult] = useState<QuotaDecisionEvaluation | null>(null);
  const [simulating, setSimulating] = useState(false);
  const [simForm, setSimForm] = useState({ tenantId: "", userId: "", agentId: "", resource: "", cost: "0" });
  const [createForm, setCreateForm] = useState({ name: "", tenantId: "", scope: "", resource: "", limit: 1000, unit: "calls", effect: "allow" });

  const handleCreate = async () => {
    if (!createForm.name || !createForm.tenantId) {
      toast.error("请填写策略名称和租户 ID");
      return;
    }
    try {
      setCreating(true);
      await createQuotaPolicy(createForm as any);
      toast.success("配额策略创建成功");
      setCreateOpen(false);
    } catch (error) {
      toast.error(getErrorMessage(error, "创建失败"));
      console.error(error);
    } finally {
      setCreating(false);
    }
  };

  const handleSimulate = async () => {
    try {
      setSimulating(true);
      const result = await evaluateQuotaDecision({
        tenantId: simForm.tenantId || undefined,
        userId: simForm.userId || undefined,
        agentId: simForm.agentId || undefined,
        resource: simForm.resource || undefined,
        cost: parseFloat(simForm.cost) || undefined
      });
      setSimResult(result);
    } catch (error) {
      toast.error(getErrorMessage(error, "模拟失败"));
      console.error(error);
    } finally {
      setSimulating(false);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="配额策略" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">配额策略</h1>
          <p className="admin-page-subtitle">管理配额策略与决策模拟</p>
        </div>
        <div className="admin-page-actions">
          <Button className="admin-primary-gradient" onClick={() => setCreateOpen(true)}>
            <Plus className="w-4 h-4 mr-1" />
            新增策略
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* 决策模拟 */}
        <Card>
          <CardHeader><CardTitle>决策模拟</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <label className="text-sm font-medium">租户 ID</label>
                <Input value={simForm.tenantId} onChange={(e) => setSimForm((p) => ({ ...p, tenantId: e.target.value }))} placeholder="tenant-id" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">用户 ID</label>
                <Input value={simForm.userId} onChange={(e) => setSimForm((p) => ({ ...p, userId: e.target.value }))} placeholder="user-id" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Agent ID</label>
                <Input value={simForm.agentId} onChange={(e) => setSimForm((p) => ({ ...p, agentId: e.target.value }))} placeholder="agent-id" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">资源</label>
                <Input value={simForm.resource} onChange={(e) => setSimForm((p) => ({ ...p, resource: e.target.value }))} placeholder="resource" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Cost</label>
                <Input value={simForm.cost} onChange={(e) => setSimForm((p) => ({ ...p, cost: e.target.value }))} type="number" />
              </div>
            </div>
            <Button variant="outline" disabled={simulating} onClick={handleSimulate} className="w-full">
              {simulating ? "模拟中..." : "运行模拟"}
            </Button>
            {simResult && (
              <div className="p-3 bg-slate-50 rounded-lg space-y-2">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">结果:</span>
                  <Badge variant={simResult.result === "allow" ? "default" : "destructive"}>{simResult.result || "-"}</Badge>
                </div>
                {simResult.matchedPolicies && simResult.matchedPolicies.length > 0 && (
                  <div className="text-sm">
                    <span className="text-slate-500">命中策略: </span>
                    {simResult.matchedPolicies.map((p, i) => <span key={i} className="font-medium">{p.name || p.policyId}</span>).join(", ")}
                  </div>
                )}
                {simResult.reason && <div className="text-sm text-slate-500">原因: {simResult.reason}</div>}
              </div>
            )}
          </CardContent>
        </Card>

        {/* 创建策略 */}
        <Card>
          <CardHeader><CardTitle>快速创建策略</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">策略名称</label>
              <Input value={createForm.name} onChange={(e) => setCreateForm((p) => ({ ...p, name: e.target.value }))} placeholder="策略名称" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <label className="text-sm font-medium">租户 ID</label>
                <Input value={createForm.tenantId} onChange={(e) => setCreateForm((p) => ({ ...p, tenantId: e.target.value }))} placeholder="tenant-id" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">资源</label>
                <Input value={createForm.resource} onChange={(e) => setCreateForm((p) => ({ ...p, resource: e.target.value }))} placeholder="resource" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">限额</label>
                <Input type="number" value={createForm.limit} onChange={(e) => setCreateForm((p) => ({ ...p, limit: parseInt(e.target.value) || 0 }))} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">单位</label>
                <Select value={createForm.unit} onValueChange={(v) => setCreateForm((p) => ({ ...p, unit: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="calls">调用次数</SelectItem>
                    <SelectItem value="tokens">Token 数</SelectItem>
                    <SelectItem value="cost">费用</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <Button className="admin-primary-gradient w-full" disabled={creating} onClick={handleCreate}>
              {creating ? "创建中..." : "创建策略"}
            </Button>
          </CardContent>
        </Card>
      </div>

      {/* 创建策略对话框 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-[520px]">
          <DialogHeader>
            <DialogTitle>新增配额策略</DialogTitle>
            <DialogDescription>配置资源的配额限制</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">策略名称</label>
              <Input value={createForm.name} onChange={(e) => setCreateForm((p) => ({ ...p, name: e.target.value }))} placeholder="策略名称" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <label className="text-sm font-medium">租户 ID</label>
                <Input value={createForm.tenantId} onChange={(e) => setCreateForm((p) => ({ ...p, tenantId: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">作用域</label>
                <Input value={createForm.scope} onChange={(e) => setCreateForm((p) => ({ ...p, scope: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-2">
                <label className="text-sm font-medium">资源</label>
                <Input value={createForm.resource} onChange={(e) => setCreateForm((p) => ({ ...p, resource: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">限额</label>
                <Input type="number" value={createForm.limit} onChange={(e) => setCreateForm((p) => ({ ...p, limit: parseInt(e.target.value) || 0 }))} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">单位</label>
                <Select value={createForm.unit} onValueChange={(v) => setCreateForm((p) => ({ ...p, unit: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="calls">调用次数</SelectItem>
                    <SelectItem value="tokens">Token 数</SelectItem>
                    <SelectItem value="cost">费用</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" disabled={creating} onClick={handleCreate}>{creating ? "创建中..." : "创建"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
