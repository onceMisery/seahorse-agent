import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  getLatestRollout,
  createCanaryRollout,
  type AgentRollout
} from "@/services/agentRolloutService";
import { getErrorMessage } from "@/utils/error";
import { RolloutTimeline } from "./components/RolloutTimeline";
import { RolloutActionDialog, type RolloutActionType } from "./components/RolloutActionDialog";

export function AgentRolloutPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_ROLLOUT_MANAGEMENT);
  const { agentId } = useParams<{ agentId: string }>();
  const [versionId, setVersionId] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [rollout, setRollout] = useState<AgentRollout | null>(null);
  const [loading, setLoading] = useState(false);
  const [canaryPercent, setCanaryPercent] = useState(10);
  const [actionType, setActionType] = useState<RolloutActionType | null>(null);

  const fetchRollout = async () => {
    if (!agentId || !versionId || !tenantId) return;
    setLoading(true);
    try {
      const data = await getLatestRollout(agentId, versionId, tenantId);
      setRollout(data ?? null);
    } catch {
      setRollout(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRollout();
  }, [agentId, versionId, tenantId]);

  const handleCreateCanary = async () => {
    if (!agentId || !versionId || !tenantId) {
      toast.error("请填写 Version ID 和 Tenant ID");
      return;
    }
    try {
      await createCanaryRollout(agentId, versionId, {
        tenantId,
        canaryPercent,
        operator: "admin"
      });
      toast.success("Canary rollout 已创建");
      fetchRollout();
    } catch (error) {
      toast.error(getErrorMessage(error, "创建失败"));
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="灰度发布" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Agent 灰度发布</h1>
          <p className="admin-page-subtitle">
            Agent: <span className="font-mono">{agentId}</span>
          </p>
        </div>
      </div>

      {/* Query params */}
      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label className="mb-1 block text-xs text-slate-500">Version ID</label>
          <Input value={versionId} onChange={(e) => setVersionId(e.target.value)} placeholder="版本 ID" className="w-[240px]" />
        </div>
        <div>
          <label className="mb-1 block text-xs text-slate-500">Tenant ID</label>
          <Input value={tenantId} onChange={(e) => setTenantId(e.target.value)} placeholder="租户 ID" className="w-[200px]" />
        </div>
        <Button variant="outline" onClick={fetchRollout} disabled={loading || !versionId || !tenantId}>
          查询
        </Button>
      </div>

      {/* Current rollout status */}
      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="py-6 text-center text-sm text-slate-500">加载中...</div>
          ) : rollout ? (
            <div className="space-y-4">
              <div className="flex items-center gap-4">
                <div>
                  <span className="text-xs text-slate-500">状态</span>
                  <div className="font-medium">{rollout.status ?? "-"}</div>
                </div>
                <div>
                  <span className="text-xs text-slate-500">Canary</span>
                  <div className="font-medium">{rollout.canaryPercent ?? 0}%</div>
                </div>
                <div>
                  <span className="text-xs text-slate-500">操作人</span>
                  <div className="font-medium">{rollout.operator ?? "-"}</div>
                </div>
              </div>

              <div className="flex gap-2">
                {rollout.status === "CANARY" || rollout.status === "ACTIVE" ? (
                  <>
                    <Button variant="outline" onClick={() => setActionType("pause")}>暂停</Button>
                    <Button onClick={() => setActionType("promote")}>全量发布</Button>
                    <Button variant="destructive" onClick={() => setActionType("rollback")}>回滚</Button>
                  </>
                ) : null}
                {rollout.status === "PAUSED" ? (
                  <>
                    <Button onClick={() => setActionType("promote")}>继续发布</Button>
                    <Button variant="destructive" onClick={() => setActionType("rollback")}>回滚</Button>
                  </>
                ) : null}
              </div>

              <RolloutTimeline rollout={rollout} />
            </div>
          ) : (
            <div className="space-y-4">
              <div className="py-4 text-center text-sm text-slate-500">
                该版本暂无灰度发布记录
              </div>
              <div className="flex items-end gap-3 border-t border-slate-100 pt-4">
                <div>
                  <label className="mb-1 block text-xs text-slate-500">Canary 百分比</label>
                  <Input
                    type="number"
                    min={1}
                    max={100}
                    value={canaryPercent}
                    onChange={(e) => setCanaryPercent(Number(e.target.value))}
                    className="w-[120px]"
                  />
                </div>
                <Button onClick={handleCreateCanary} disabled={!versionId || !tenantId}>
                  创建 Canary 发布
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {actionType && rollout?.rolloutId ? (
        <RolloutActionDialog
          actionType={actionType}
          agentId={agentId!}
          rolloutId={rollout.rolloutId}
          tenantId={tenantId}
          onClose={() => setActionType(null)}
          onSuccess={() => { setActionType(null); fetchRollout(); }}
        />
      ) : null}
    </div>
  );
}
