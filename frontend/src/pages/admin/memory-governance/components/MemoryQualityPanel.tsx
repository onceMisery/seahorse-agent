import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getMemoryQualitySnapshots, runMemoryQuality, type MemoryQualitySnapshot } from "@/services/memoryGovernanceService";
import { useAuthStore } from "@/stores/authStore";
import { getErrorMessage } from "@/utils/error";

function snapshotNumber(snapshot: MemoryQualitySnapshot, key: string) {
  const value = snapshot.snapshot?.[key];
  if (typeof value === "number") return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

function snapshotSuggestions(snapshot: MemoryQualitySnapshot) {
  const raw = snapshot.snapshot?.cleanupSuggestions;
  return Array.isArray(raw) ? raw : [];
}

export function MemoryQualityPanel() {
  const userId = useAuthStore((state) => state.user?.userId);
  const [snapshots, setSnapshots] = useState<MemoryQualitySnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);

  const loadSnapshots = async () => {
    try {
      setLoading(true);
      const data = await getMemoryQualitySnapshots({ userId, limit: 20 });
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
  }, [userId]);

  const handleRunQuality = async () => {
    try {
      setRunning(true);
      await runMemoryQuality({ userId });
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
            <Card key={snap.id}>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">快照 {snap.createTime ? new Date(snap.createTime).toLocaleString("zh-CN") : "-"}</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-4 gap-4 text-sm">
                  <div>
                    <div className="text-slate-500">短期记忆</div>
                    <div className="font-medium">{snapshotNumber(snap, "shortTermCount")}</div>
                  </div>
                  <div>
                    <div className="text-slate-500">长期记忆</div>
                    <div className="font-medium">{snapshotNumber(snap, "longTermCount")}</div>
                  </div>
                  <div>
                    <div className="text-slate-500">语义记忆</div>
                    <div className="font-medium">{snapshotNumber(snap, "semanticCount")}</div>
                  </div>
                  <div>
                    <div className="text-slate-500">冲突数</div>
                    <div className="font-medium text-amber-600">{snapshotNumber(snap, "conflictCount")}</div>
                  </div>
                </div>
                {snapshotSuggestions(snap).length > 0 && (
                  <div className="mt-3">
                    <div className="text-xs text-slate-500 mb-1">治理建议</div>
                    <ul className="text-sm list-disc list-inside space-y-1">
                      {snapshotSuggestions(snap).map((suggestion, i) => (
                        <li key={i}>
                          {typeof suggestion === "string"
                            ? suggestion
                            : JSON.stringify(suggestion)}
                        </li>
                      ))}
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
