import { useEffect, useState } from "react";
import { RefreshCw, Trash2, Merge } from "lucide-react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { api } from "@/services/api";

interface CleanupCandidate {
  memoryId?: string;
  layer?: string;
  content?: string;
  qualityScore?: number | null;
  accessCount?: number | null;
  lastAccessedAt?: string | null;
  createTime?: string | null;
  reason?: string;
  [key: string]: unknown;
}

const formatDate = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN");
};

const qualityColor = (score?: number | null) => {
  if (!score && score !== 0) return "text-slate-500";
  if (score >= 0.7) return "text-green-600";
  if (score >= 0.4) return "text-amber-600";
  return "text-red-600";
};

export function MemoryCleanupPanel() {
  const [candidates, setCandidates] = useState<CleanupCandidate[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionTarget, setActionTarget] = useState<{ memory: CleanupCandidate; action: "forget" | "merge" } | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const loadCandidates = async () => {
    setLoading(true);
    try {
      const result = await api.get<CleanupCandidate[] | { candidates?: CleanupCandidate[] }>("/memories/governance/cleanup-suggestions");
      const list = Array.isArray(result) ? result : (result?.candidates || []);
      setCandidates(list);
    } catch {
      // Endpoint may not exist yet; show empty state
      setCandidates([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCandidates();
  }, []);

  const handleAction = async () => {
    if (!actionTarget) return;
    const { memory, action } = actionTarget;
    setActionLoading(true);
    try {
      if (action === "forget") {
        await api.post(`/memories/governance/forget`, { memoryId: memory.memoryId });
        toast.success(`记忆 [${memory.memoryId?.slice(0, 8)}...] 已标记为遗忘`);
      } else {
        await api.post(`/memories/governance/merge`, { memoryId: memory.memoryId });
        toast.success(`记忆 [${memory.memoryId?.slice(0, 8)}...] 已提交合并`);
      }
      setActionTarget(null);
      await loadCandidates();
    } catch (error) {
      toast.error(`操作失败：${(error as Error)?.message || "未知错误"}`);
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-slate-600">
            基于质量快照和访问频率，列出低价值记忆供人工确认后清理。所有操作需人工确认，不会自动执行。
          </p>
        </div>
        <Button variant="ghost" size="sm" onClick={loadCandidates} disabled={loading}>
          <RefreshCw className={`mr-1 h-3 w-3 ${loading ? "animate-spin" : ""}`} />
          刷新
        </Button>
      </div>

      {loading ? (
        <div className="py-8 text-center text-sm text-slate-500">加载清理建议中...</div>
      ) : candidates.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-8 text-center">
          <div className="text-slate-500 text-sm">暂无清理建议</div>
          <div className="text-slate-400 text-xs mt-1">运行治理任务后会自动生成低价值记忆建议</div>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                <th className="pb-2 pr-3 font-medium">记忆 ID</th>
                <th className="pb-2 pr-3 font-medium">层级</th>
                <th className="pb-2 pr-3 font-medium">质量分</th>
                <th className="pb-2 pr-3 font-medium">访问次数</th>
                <th className="pb-2 pr-3 font-medium">最后访问</th>
                <th className="pb-2 pr-3 font-medium">原因</th>
                <th className="pb-2 pr-3 font-medium text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {candidates.map((c, idx) => (
                <tr key={c.memoryId || idx} className="border-b border-slate-100">
                  <td className="py-2 pr-3 font-mono text-xs">{c.memoryId ? c.memoryId.slice(0, 12) + "..." : "-"}</td>
                  <td className="py-2 pr-3"><Badge variant="outline">{c.layer || "-"}</Badge></td>
                  <td className={`py-2 pr-3 font-medium ${qualityColor(c.qualityScore)}`}>
                    {c.qualityScore != null ? (c.qualityScore * 100).toFixed(0) + "%" : "-"}
                  </td>
                  <td className="py-2 pr-3">{c.accessCount ?? 0}</td>
                  <td className="py-2 pr-3 text-xs text-slate-500">{formatDate(c.lastAccessedAt)}</td>
                  <td className="py-2 pr-3 max-w-[200px] truncate text-slate-600" title={c.reason || ""}>{c.reason || "-"}</td>
                  <td className="py-2 pr-3 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-destructive h-7"
                        onClick={() => setActionTarget({ memory: c, action: "forget" })}
                      >
                        <Trash2 className="mr-1 h-3 w-3" />
                        遗忘
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        className="h-7"
                        onClick={() => setActionTarget({ memory: c, action: "merge" })}
                      >
                        <Merge className="mr-1 h-3 w-3" />
                        合并
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <AlertDialog open={Boolean(actionTarget)} onOpenChange={(open) => (!open ? setActionTarget(null) : null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {actionTarget?.action === "forget" ? "确认遗忘此记忆？" : "确认合并此记忆？"}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {actionTarget?.action === "forget"
                ? `记忆 [${actionTarget?.memory.memoryId?.slice(0, 12)}...] 将被标记为遗忘，后续召回不再使用。关联的画像事实也将同步失效。`
                : `记忆 [${actionTarget?.memory.memoryId?.slice(0, 12)}...] 将提交合并请求，由治理引擎评估后执行。`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleAction}
              disabled={actionLoading}
              className={actionTarget?.action === "forget" ? "bg-destructive text-destructive-foreground" : ""}
            >
              {actionLoading ? "处理中..." : actionTarget?.action === "forget" ? "确认遗忘" : "确认合并"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
