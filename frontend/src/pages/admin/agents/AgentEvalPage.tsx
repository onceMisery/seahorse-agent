import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { listEvalHistory, type AgentEvalSummary } from "@/services/agentEvalService";
import { getErrorMessage } from "@/utils/error";

export function AgentEvalPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_EVALUATION);
  const { agentId } = useParams<{ agentId: string }>();
  const [versionId, setVersionId] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [evalType, setEvalType] = useState("");
  const [summaries, setSummaries] = useState<AgentEvalSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const fetchHistory = async () => {
    if (!agentId || !versionId || !tenantId) return;
    setLoading(true);
    try {
      const result = await listEvalHistory(agentId, versionId, {
        tenantId,
        evalType: evalType || undefined,
        current: 1,
        size: 50
      });
      setSummaries(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载评测历史失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
  }, [agentId, versionId, tenantId, evalType]);

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="Agent 评测" />;
  }

  const getScoreColor = (score?: number, pass?: number) => {
    if (score == null) return "text-slate-500";
    if (pass != null && score >= pass) return "text-emerald-600";
    return "text-red-600";
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Agent 评测</h1>
          <p className="admin-page-subtitle">
            Agent: <span className="font-mono">{agentId}</span> · 评测历史与摘要
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label className="mb-1 block text-xs text-slate-500">版本 ID</label>
          <Input value={versionId} onChange={(e) => setVersionId(e.target.value)} placeholder="版本 ID" className="w-[240px]" />
        </div>
        <div>
          <label className="mb-1 block text-xs text-slate-500">租户 ID</label>
          <Input value={tenantId} onChange={(e) => setTenantId(e.target.value)} placeholder="租户 ID" className="w-[200px]" />
        </div>
        <div>
          <label className="mb-1 block text-xs text-slate-500">评测类型</label>
          <Input value={evalType} onChange={(e) => setEvalType(e.target.value)} placeholder="全部" className="w-[140px]" />
        </div>
        <Button variant="outline" onClick={fetchHistory} disabled={loading || !versionId || !tenantId}>
          <RefreshCw className={`mr-1 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          刷新
        </Button>
      </div>

      <Card>
        <CardContent className="pt-6">
          {summaries.length === 0 && !loading ? (
            <div className="py-8 text-center text-muted-foreground">
              暂无评测记录
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>类型</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead className="text-right">分数</TableHead>
                  <TableHead className="text-right">通过阈值</TableHead>
                  <TableHead className="text-right">用例数</TableHead>
                  <TableHead>创建人</TableHead>
                  <TableHead>时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {summaries.map((s, i) => (
                  <TableRow key={s.summaryId ?? i}>
                    <TableCell>
                      <Badge variant="outline">{s.evalType ?? "-"}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge className={s.status === "PASSED" ? "bg-emerald-100 text-emerald-700" : s.status === "FAILED" ? "bg-red-100 text-red-700" : "bg-slate-100 text-slate-600"}>
                        {s.status ?? "-"}
                      </Badge>
                    </TableCell>
                    <TableCell className={`text-right font-mono font-medium ${getScoreColor(s.score, s.passThreshold)}`}>
                      {s.score?.toFixed(2) ?? "-"}
                    </TableCell>
                    <TableCell className="text-right font-mono text-xs text-slate-500">
                      {s.passThreshold?.toFixed(2) ?? "-"}
                    </TableCell>
                    <TableCell className="text-right text-xs">{s.caseCount ?? 0}</TableCell>
                    <TableCell className="text-xs text-slate-500">{s.createdBy ?? "-"}</TableCell>
                    <TableCell className="text-xs text-slate-400">
                      {s.createdAt ? new Date(s.createdAt).toLocaleDateString("zh-CN") : "-"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          {total > 0 ? (
            <div className="mt-3 text-xs text-slate-500">共 {total} 条记录</div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
