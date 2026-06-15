import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { listMemoryConflicts, resolveMemoryConflict, type MemoryConflict } from "@/services/memoryGovernanceService";
import { getErrorMessage } from "@/utils/error";

export function MemoryConflictPanel() {
  const [conflicts, setConflicts] = useState<MemoryConflict[]>([]);
  const [loading, setLoading] = useState(true);

  // Resolve dialog
  const [resolveOpen, setResolveOpen] = useState(false);
  const [selectedConflict, setSelectedConflict] = useState<MemoryConflict | null>(null);
  const [resolution, setResolution] = useState("keep_a");
  const [mergedContent, setMergedContent] = useState("");
  const [resolving, setResolving] = useState(false);

  const loadConflicts = async () => {
    try {
      setLoading(true);
      const data = await listMemoryConflicts({ status: "open" });
      setConflicts(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载冲突列表失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConflicts();
  }, []);

  const handleResolve = async () => {
    if (!selectedConflict?.conflictId) return;

    try {
      setResolving(true);
      await resolveMemoryConflict(
        selectedConflict.conflictId,
        resolution,
        resolution === "merge" ? mergedContent : undefined
      );
      toast.success("冲突已解决");
      setResolveOpen(false);
      loadConflicts();
    } catch (error) {
      toast.error(getErrorMessage(error, "解决冲突失败"));
      console.error(error);
    } finally {
      setResolving(false);
    }
  };

  return (
    <>
      <div className="flex items-center gap-2 mb-4">
        <Button variant="outline" size="sm" onClick={loadConflicts}>
          <RefreshCw className="w-4 h-4 mr-1" />刷新
        </Button>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : conflicts.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无冲突</div>
          ) : (
            <div className="space-y-4">
              {conflicts.map((conflict) => (
                <div key={conflict.conflictId} className="p-4 bg-slate-50 rounded-lg">
                  <div className="flex items-center justify-between mb-3">
                    <Badge variant={conflict.status === "open" ? "destructive" : "default"}>{conflict.status || "-"}</Badge>
                    <Button variant="outline" size="sm" onClick={() => { setSelectedConflict(conflict); setResolveOpen(true); }}>
                      解决
                    </Button>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="p-3 bg-white rounded border">
                      <div className="text-xs text-slate-500 mb-1">记忆 A ({conflict.memoryIdA})</div>
                      <div className="text-sm">{conflict.contentA || "-"}</div>
                    </div>
                    <div className="p-3 bg-white rounded border">
                      <div className="text-xs text-slate-500 mb-1">记忆 B ({conflict.memoryIdB})</div>
                      <div className="text-sm">{conflict.contentB || "-"}</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* 解决冲突对话框 */}
      <Dialog open={resolveOpen} onOpenChange={setResolveOpen}>
        <DialogContent className="sm:max-w-[520px]">
          <DialogHeader><DialogTitle>解决冲突</DialogTitle></DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">处理方式</label>
              <Select value={resolution} onValueChange={setResolution}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="keep_a">保留 A</SelectItem>
                  <SelectItem value="keep_b">保留 B</SelectItem>
                  <SelectItem value="merge">合并</SelectItem>
                  <SelectItem value="discard">废弃两者</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {resolution === "merge" && (
              <div className="space-y-2">
                <label className="text-sm font-medium">合并后内容</label>
                <Textarea value={mergedContent} onChange={(e) => setMergedContent(e.target.value)} rows={4} placeholder="请输入合并后的内容" />
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setResolveOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" disabled={resolving} onClick={handleResolve}>{resolving ? "处理中..." : "确认"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
