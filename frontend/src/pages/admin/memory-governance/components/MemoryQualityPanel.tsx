import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getMemoryQualitySnapshots, runMemoryQuality, type MemoryQualitySnapshot } from "@/services/memoryGovernanceService";
import { getErrorMessage } from "@/utils/error";

export function MemoryQualityPanel() {
  const [snapshots, setSnapshots] = useState<MemoryQualitySnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);

  const loadSnapshots = async () => {
    try {
      setLoading(true);
      const data = await getMemoryQualitySnapshots();
      setSnapshots(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载质量快照失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSnapshots();
  }, []);

  const handleRunQuality = async () => {
    try {
      setRunning(true);
      await runMemoryQuality();
      toast.success("质量检查已触发");
      loadSnapshots();
    } catch (error) {
      toast.error(getErrorMessage(error, "触发质量检查失败"));
      console.error(error);
    } finally {
      setRunning(false);
    }
  };

  return (
    <>
      <div className="flex items-center gap-2 mb-4">
        <Button variant="outline" size="sm" onClick={loadSnapshots}>
          <RefreshCw className="w-4 h-4 mr-1" />刷新
        </Button>
        <Button className="admin-primary-gradient" size="sm" disabled={running} onClick={handleRunQuality}>
          {running ? "运行中..." : "运行质量检查"}
        </Button>
      </div>

      {loading ? (
        <div className="text-center py-8 text-muted-foreground">加载中...</div>
      ) : snapshots.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <div className="text-center py-8 text-muted-foreground">暂无质量快照</div>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {snapshots.map((snap) => (
            <Card key={snap.snapshotId}>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">快照 {snap.snapshotTime ? new Date(snap.snapshotTime).toLocaleString("zh-CN") : "-"}</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-4 gap-4 text-sm">
                  <div>
                    <div className="text-slate-500">总记忆数</div>
                    <div className="font-medium">{snap.totalMemories ?? 0}</div>
                  </div>
                  <div>
                    <div className="text-slate-500">高质量</div>
                    <div className="font-medium text-green-600">{snap.highQualityCount ?? 0}</div>
                  </div>
                  <div>
                    <div className="text-slate-500">低质量</div>
                    <div className="font-medium text-red-600">{snap.lowQualityCount ?? 0}</div>
                  </div>
                  <div>
                    <div className="text-slate-500">平均质量</div>
                    <div className="font-medium">{((snap.averageQuality ?? 0) * 100).toFixed(1)}%</div>
                  </div>
                </div>
                {snap.governanceSuggestions && snap.governanceSuggestions.length > 0 && (
                  <div className="mt-3">
                    <div className="text-xs text-slate-500 mb-1">治理建议</div>
                    <ul className="text-sm list-disc list-inside space-y-1">
                      {snap.governanceSuggestions.map((s, i) => <li key={i}>{s}</li>)}
                    </ul>
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </>
  );
}
