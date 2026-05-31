import { useEffect, useState } from "react";
import { Plus, Play, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { getKnowledgeBases, type KnowledgeBase } from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";

// 简化的 Backfill 面板（后端 API 需要逐个实现）
export function MetadataBackfillPanel() {
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string>("");
  const [createOpen, setCreateOpen] = useState(false);
  const [jobName, setJobName] = useState("");

  useEffect(() => {
    getKnowledgeBases(1, 100).then((data) => {
      setKbs(data || []);
      if (data && data.length > 0) setSelectedKbId(data[0].id);
    }).catch(console.error);
  }, []);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <span>Backfill 任务</span>
          <div className="flex items-center gap-2">
            <Select value={selectedKbId} onValueChange={setSelectedKbId}>
              <SelectTrigger className="w-[180px]"><SelectValue placeholder="选择知识库" /></SelectTrigger>
              <SelectContent>
                {kbs.map((kb) => <SelectItem key={kb.id} value={kb.id}>{kb.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <Button variant="outline" size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="w-4 h-4 mr-1" />创建任务
            </Button>
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="text-center py-8 text-muted-foreground">
          <p>Backfill 任务管理</p>
          <p className="text-sm mt-1">创建、运行、暂停、恢复、取消 backfill 任务</p>
        </div>
      </CardContent>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader><DialogTitle>创建 Backfill 任务</DialogTitle></DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">任务名称</label>
              <Input value={jobName} onChange={(e) => setJobName(e.target.value)} placeholder="请输入任务名称" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" onClick={() => { toast.info("Backfill 任务创建功能待后端 API 接入"); setCreateOpen(false); }}>创建</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
