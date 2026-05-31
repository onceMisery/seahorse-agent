import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
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
import { getAgentRunSnapshot, type AgentRunSnapshot } from "@/services/agentRunService";
import { getErrorMessage } from "@/utils/error";

export function AgentRunListPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.AGENT_RUN_MANAGEMENT);
  const navigate = useNavigate();

  const [runs, setRuns] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchInput, setSearchInput] = useState("");
  const [runIdInput, setRunIdInput] = useState("");

  const handleLookup = async () => {
    if (!runIdInput.trim()) {
      toast.error("请输入 Run ID");
      return;
    }

    try {
      setLoading(true);
      const data = await getAgentRunSnapshot(runIdInput.trim());
      setRuns((prev) => [
        { runId: runIdInput.trim(), status: (data as any)?.run?.status, inputSummary: (data as any)?.run?.inputSummary, startedAt: (data as any)?.run?.startedAt },
        ...prev.filter((r) => r.runId !== runIdInput.trim())
      ]);
    } catch (error) {
      toast.error(getErrorMessage(error, "查询 Run 失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="Agent 运行管理" />;
  }

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case "RUNNING":
        return <Badge className="bg-blue-100 text-blue-700">运行中</Badge>;
      case "COMPLETED":
        return <Badge className="bg-green-100 text-green-700">已完成</Badge>;
      case "FAILED":
        return <Badge variant="destructive">失败</Badge>;
      case "WAITING":
        return <Badge className="bg-amber-100 text-amber-700">等待中</Badge>;
      case "CANCELLED":
        return <Badge variant="secondary">已取消</Badge>;
      default:
        return <Badge variant="outline">{status || "-"}</Badge>;
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Agent 运行管理</h1>
          <p className="admin-page-subtitle">查看和管理 Agent 运行实例</p>
        </div>
        <div className="admin-page-actions">
          <Input
            value={runIdInput}
            onChange={(e) => setRunIdInput(e.target.value)}
            placeholder="输入 Run ID 查询"
            className="w-[220px]"
            onKeyDown={(e) => e.key === "Enter" && handleLookup()}
          />
          <Button variant="outline" onClick={handleLookup} disabled={loading}>
            <Search className="w-4 h-4 mr-1" />
            查询
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {runs.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              输入 Run ID 查询运行实例，或在 Agent 检视器中查看详情
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Run ID</TableHead>
                  <TableHead className="w-[100px]">状态</TableHead>
                  <TableHead>输入摘要</TableHead>
                  <TableHead className="w-[160px]">启动时间</TableHead>
                  <TableHead className="w-[100px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {runs.map((run) => (
                  <TableRow key={run.runId as string}>
                    <TableCell className="font-mono text-sm">{(run.runId as string) || "-"}</TableCell>
                    <TableCell>{getStatusBadge(run.status as string)}</TableCell>
                    <TableCell className="text-sm text-muted-foreground truncate max-w-[300px]">{(run.inputSummary as string) || "-"}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {run.startedAt ? new Date(run.startedAt as string).toLocaleString("zh-CN") : "-"}
                    </TableCell>
                    <TableCell>
                      <Button variant="outline" size="sm" onClick={() => navigate(`/admin/agent-inspector/${run.runId}`)}>
                        检视
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
