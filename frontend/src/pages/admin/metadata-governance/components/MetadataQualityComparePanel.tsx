import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { getKnowledgeBases, type KnowledgeBase } from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";
import { useEffect } from "react";

export function MetadataQualityComparePanel() {
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string>("");
  const [baseVersion, setBaseVersion] = useState("");
  const [candidateVersion, setCandidateVersion] = useState("");

  useEffect(() => {
    getKnowledgeBases(1, 100).then((data) => {
      setKbs(data || []);
      if (data && data.length > 0) setSelectedKbId(data[0].id);
    }).catch(console.error);
  }, []);

  const handleCompare = () => {
    if (!selectedKbId || !baseVersion.trim() || !candidateVersion.trim()) {
      toast.error("请填写完整的对比参数");
      return;
    }
    toast.info("质量对比功能待后端 API 接入");
  };

  return (
    <Card>
      <CardHeader><CardTitle>质量对比</CardTitle></CardHeader>
      <CardContent>
        <div className="space-y-4">
          <div className="space-y-2">
            <label className="text-sm font-medium">知识库</label>
            <Select value={selectedKbId} onValueChange={setSelectedKbId}>
              <SelectTrigger className="w-[200px]"><SelectValue placeholder="选择知识库" /></SelectTrigger>
              <SelectContent>
                {kbs.map((kb) => <SelectItem key={kb.id} value={kb.id}>{kb.name}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Base 版本</label>
              <Input value={baseVersion} onChange={(e) => setBaseVersion(e.target.value)} placeholder="基础版本标识" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Candidate 版本</label>
              <Input value={candidateVersion} onChange={(e) => setCandidateVersion(e.target.value)} placeholder="候选版本标识" />
            </div>
          </div>
          <Button className="admin-primary-gradient" onClick={handleCompare}>执行对比</Button>
        </div>
      </CardContent>
    </Card>
  );
}
