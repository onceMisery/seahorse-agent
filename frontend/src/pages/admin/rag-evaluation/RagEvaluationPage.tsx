import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  listDatasets,
  createDataset,
  type RetrievalEvaluationDataset
} from "@/services/ragEvaluationService";
import { getKnowledgeBases, type KnowledgeBase } from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";

export function RagEvaluationPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.RAG_EVALUATION);
  const navigate = useNavigate();

  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string>("");
  const [datasets, setDatasets] = useState<RetrievalEvaluationDataset[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [newDatasetName, setNewDatasetName] = useState("");
  const [newDatasetDesc, setNewDatasetDesc] = useState("");
  const [creating, setCreating] = useState(false);

  // Load knowledge bases
  useEffect(() => {
    const loadKbs = async () => {
      try {
        const data = await getKnowledgeBases(1, 100);
        setKbs(data || []);
        if (data && data.length > 0 && !selectedKbId) {
          setSelectedKbId(data[0].id);
        }
      } catch (error) {
        console.error(error);
      }
    };
    if (featureState.enabled) loadKbs();
  }, [featureState.enabled]);

  // Load datasets when kb changes
  useEffect(() => {
    if (!selectedKbId) return;
    const loadDatasets = async () => {
      try {
        setLoading(true);
        const data = await listDatasets(selectedKbId, { current: 1, size: 50 });
        setDatasets(data?.records || []);
      } catch (error) {
        toast.error(getErrorMessage(error, "加载评测数据集失败"));
        console.error(error);
      } finally {
        setLoading(false);
      }
    };
    loadDatasets();
  }, [selectedKbId]);

  const handleCreate = async () => {
    if (!selectedKbId || !newDatasetName.trim()) {
      toast.error("请输入数据集名称");
      return;
    }

    try {
      setCreating(true);
      await createDataset(selectedKbId, { name: newDatasetName.trim(), description: newDatasetDesc.trim() });
      toast.success("数据集创建成功");
      setCreateOpen(false);
      setNewDatasetName("");
      setNewDatasetDesc("");
      // Reload
      const data = await listDatasets(selectedKbId, { current: 1, size: 50 });
      setDatasets(data?.records || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "创建数据集失败"));
      console.error(error);
    } finally {
      setCreating(false);
    }
  };

  const formatTime = (dateStr?: string | null) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleString("zh-CN");
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="RAG 评测" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">RAG 评测</h1>
          <p className="admin-page-subtitle">评估知识库检索质量，支持数据集评测与策略对比</p>
        </div>
        <div className="admin-page-actions">
          <Select value={selectedKbId} onValueChange={setSelectedKbId}>
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder="选择知识库" />
            </SelectTrigger>
            <SelectContent>
              {kbs.map((kb) => (
                <SelectItem key={kb.id} value={kb.id}>{kb.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={() => navigate("/admin/rag-strategies")}>
            策略模板
          </Button>
          <Button className="admin-primary-gradient" onClick={() => setCreateOpen(true)}>
            <Plus className="w-4 h-4 mr-1" />
            新增数据集
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {!selectedKbId ? (
            <div className="text-center py-8 text-muted-foreground">请先选择知识库</div>
          ) : loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : datasets.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无评测数据集，点击"新增数据集"创建</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>数据集名称</TableHead>
                  <TableHead className="w-[100px]">样本数</TableHead>
                  <TableHead className="w-[160px]">创建时间</TableHead>
                  <TableHead className="w-[160px]">更新时间</TableHead>
                  <TableHead className="w-[100px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {datasets.map((ds) => (
                  <TableRow key={ds.datasetId}>
                    <TableCell className="font-medium">{ds.name || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{ds.sampleCount ?? 0}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{formatTime(ds.createTime)}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{formatTime(ds.updateTime)}</TableCell>
                    <TableCell>
                      <Button variant="outline" size="sm" onClick={() => navigate(`/admin/rag-evaluation/${selectedKbId}/${ds.datasetId}`)}>
                        详情
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle>新增评测数据集</DialogTitle>
            <DialogDescription>创建数据集用于评估检索质量</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">数据集名称</label>
              <Input value={newDatasetName} onChange={(e) => setNewDatasetName(e.target.value)} placeholder="请输入数据集名称" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">描述（可选）</label>
              <Input value={newDatasetDesc} onChange={(e) => setNewDatasetDesc(e.target.value)} placeholder="请输入描述" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" disabled={creating} onClick={handleCreate}>
              {creating ? "创建中..." : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
