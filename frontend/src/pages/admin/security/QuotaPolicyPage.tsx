import { useState } from "react";
import { Plus } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  createQuotaPolicy,
  evaluateQuotaDecision,
  type QuotaDecisionEvaluation
} from "@/services/securityGovernanceService";
import { getErrorMessage } from "@/utils/error";

export function QuotaPolicyPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.QUOTA_MANAGEMENT);

  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [simResult, setSimResult] = useState<QuotaDecisionEvaluation | null>(null);
  const [simulating, setSimulating] = useState(false);
  const [simForm, setSimForm] = useState({
    tenantId: "",
    userId: "",
    agentId: "",
    toolId: "",
    modelId: "",
    runId: "",
    riskLevel: "LOW",
    tokens: "0",
    calls: "1",
    cost: "0"
  });
  const [createForm, setCreateForm] = useState({
    policyId: "",
    tenantId: "",
    scope: "AGENT",
    subjectId: "",
    status: "ACTIVE",
    tokenLimit: "1000",
    callLimit: "100",
    costLimit: "10",
    warnRatio: "0.8"
  });

  const handleCreate = async () => {
    const tenantId = createForm.tenantId.trim();
    const subjectId = createForm.subjectId.trim() || (createForm.scope === "TENANT" ? tenantId : "");
    if (!createForm.policyId.trim() || !tenantId || !subjectId) {
      toast.error("请填写策略 ID、租户 ID 和 Subject ID");
      return;
    }
    try {
      setCreating(true);
      await createQuotaPolicy({
        policyId: createForm.policyId.trim(),
        tenantId,
        scope: createForm.scope,
        subjectId,
        status: createForm.status,
        tokenLimit: Number(createForm.tokenLimit) || undefined,
        callLimit: Number(createForm.callLimit) || undefined,
        costLimit: Number(createForm.costLimit) || undefined,
        warnRatio: Number(createForm.warnRatio) || undefined
      });
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
        agentId: simForm.agentId || undefined,
        userId: simForm.userId || undefined,
        toolId: simForm.toolId || undefined,
        modelId: simForm.modelId || undefined,
        runId: simForm.runId || undefined,
        riskLevel: simForm.riskLevel || undefined,
        tokens: Number(simForm.tokens) || 0,
        calls: Number(simForm.calls) || 0,
        cost: Number(simForm.cost) || 0
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
                <label className="text-sm font-medium">Tool ID</label>
                <Input value={simForm.toolId} onChange={(e) => setSimForm((p) => ({ ...p, toolId: e.target.value }))} placeholder="tool-id" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Tokens</label>
                <Input value={simForm.tokens} onChange={(e) => setSimForm((p) => ({ ...p, tokens: e.target.value }))} type="number" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Calls</label>
                <Input value={simForm.calls} onChange={(e) => setSimForm((p) => ({ ...p, calls: e.target.value }))} type="number" />
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
              <label className="text-sm font-medium">策略 ID</label>
              <Input value={createForm.policyId} onChange={(e) => setCreateForm((p) => ({ ...p, policyId: e.target.value }))} placeholder="policy-id" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <label className="text-sm font-medium">租户 ID</label>
                <Input value={createForm.tenantId} onChange={(e) => setCreateForm((p) => ({ ...p, tenantId: e.target.value }))} placeholder="tenant-id" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Subject ID</label>
                <Input value={createForm.subjectId} onChange={(e) => setCreateForm((p) => ({ ...p, subjectId: e.target.value }))} placeholder="agent/user id" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Token 限额</label>
                <Input type="number" value={createForm.tokenLimit} onChange={(e) => setCreateForm((p) => ({ ...p, tokenLimit: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">作用域</label>
                <Select value={createForm.scope} onValueChange={(v) => setCreateForm((p) => ({ ...p, scope: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="TENANT">TENANT</SelectItem>
                    <SelectItem value="USER">USER</SelectItem>
                    <SelectItem value="AGENT">AGENT</SelectItem>
                    <SelectItem value="TOOL">TOOL</SelectItem>
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
              <label className="text-sm font-medium">策略 ID</label>
              <Input value={createForm.policyId} onChange={(e) => setCreateForm((p) => ({ ...p, policyId: e.target.value }))} placeholder="policy-id" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <label className="text-sm font-medium">租户 ID</label>
                <Input value={createForm.tenantId} onChange={(e) => setCreateForm((p) => ({ ...p, tenantId: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Subject ID</label>
                <Input value={createForm.subjectId} onChange={(e) => setCreateForm((p) => ({ ...p, subjectId: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-2">
                <label className="text-sm font-medium">Token 限额</label>
                <Input type="number" value={createForm.tokenLimit} onChange={(e) => setCreateForm((p) => ({ ...p, tokenLimit: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">调用限额</label>
                <Input type="number" value={createForm.callLimit} onChange={(e) => setCreateForm((p) => ({ ...p, callLimit: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">成本限额</label>
                <Input type="number" value={createForm.costLimit} onChange={(e) => setCreateForm((p) => ({ ...p, costLimit: e.target.value }))} />
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
