import { useEffect, useState } from "react";
import { Play, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  runMemoryMaintenance,
  listMemoryMaintenanceRuns,
  getMemoryPolicyConfig,
  updateMemoryPolicyConfig,
  type MemoryMaintenanceRun,
  type MemoryPolicyConfig
} from "@/services/memoryGovernanceService";
import { getErrorMessage } from "@/utils/error";

export function MemoryMaintenancePanel() {
  const [runs, setRuns] = useState<MemoryMaintenanceRun[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [policyConfig, setPolicyConfig] = useState<MemoryPolicyConfig | null>(null);
  const [editingConfig, setEditingConfig] = useState(false);
  const [configJson, setConfigJson] = useState("");
  const [configError, setConfigError] = useState("");
  const [savingConfig, setSavingConfig] = useState(false);

  const loadData = async () => {
    try {
      setLoading(true);
      const [runsData, config] = await Promise.all([
        listMemoryMaintenanceRuns({ current: 1, size: 20 }).catch(() => null),
        getMemoryPolicyConfig().catch(() => null)
      ]);
      setRuns((runsData as any)?.records || runsData || []);
      setPolicyConfig(config);
      if (config) setConfigJson(JSON.stringify(config, null, 2));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载维护任务失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleRunMaintenance = async () => {
    try {
      setRunning(true);
      await runMemoryMaintenance();
      toast.success("维护任务已触发");
      loadData();
    } catch (error) {
      toast.error(getErrorMessage(error, "触发维护任务失败"));
      console.error(error);
    } finally {
      setRunning(false);
    }
  };

  const handleSaveConfig = async () => {
    try {
      const parsed = JSON.parse(configJson);
      setConfigError("");
      setSavingConfig(true);
      await updateMemoryPolicyConfig(parsed);
      toast.success("策略配置已保存");
      setEditingConfig(false);
      loadData();
    } catch (error) {
      if (error instanceof SyntaxError) {
        setConfigError("JSON 格式不合法");
      } else {
        toast.error(getErrorMessage(error, "保存配置失败"));
        // 保留用户输入
      }
      console.error(error);
    } finally {
      setSavingConfig(false);
    }
  };

  return (
    <>
      <div className="flex items-center gap-2 mb-4">
        <Button variant="outline" size="sm" onClick={loadData}>
          <RefreshCw className="w-4 h-4 mr-1" />刷新
        </Button>
        <Button className="admin-primary-gradient" size="sm" disabled={running} onClick={handleRunMaintenance}>
          <Play className="w-4 h-4 mr-1" />{running ? "运行中..." : "运行维护"}
        </Button>
        <Button variant="outline" size="sm" onClick={() => setEditingConfig(!editingConfig)}>
          策略配置
        </Button>
      </div>

      {editingConfig && (
        <Card className="mb-4">
          <CardHeader><CardTitle>策略配置</CardTitle></CardHeader>
          <CardContent>
            <textarea
              className="w-full h-[300px] p-3 border rounded-lg font-mono text-sm"
              value={configJson}
              onChange={(e) => { setConfigJson(e.target.value); setConfigError(""); }}
            />
            {configError && <p className="text-xs text-destructive mt-1">{configError}</p>}
            <div className="mt-2 flex gap-2">
              <Button className="admin-primary-gradient" size="sm" disabled={savingConfig} onClick={handleSaveConfig}>
                {savingConfig ? "保存中..." : "保存配置"}
              </Button>
              <Button variant="outline" size="sm" onClick={() => setEditingConfig(false)}>取消</Button>
            </div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : runs.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无维护运行记录</div>
          ) : (
            <div className="space-y-3">
              {runs.map((run) => (
                <div key={run.runId} className="p-3 bg-slate-50 rounded-lg flex items-center justify-between">
                  <div>
                    <Badge variant="outline" className="mr-2">{run.type || "maintenance"}</Badge>
                    <Badge variant={run.status === "COMPLETED" ? "default" : "secondary"}>{run.status || "-"}</Badge>
                  </div>
                  <div className="text-sm text-muted-foreground">
                    {run.startedAt ? new Date(run.startedAt).toLocaleString("zh-CN") : "-"}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </>
  );
}
