import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Plus, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  listStrategyTemplates,
  createStrategyTemplate,
  deleteStrategyTemplate,
  type RetrievalStrategyTemplate
} from "@/services/ragEvaluationService";
import { StrategyEditor } from "./components/StrategyEditor";
import { getKnowledgeBases, type KnowledgeBase } from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";

export function RetrievalStrategyTemplatePage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.RAG_EVALUATION);
  const navigate = useNavigate();

  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string>("");
  const [templates, setTemplates] = useState<RetrievalStrategyTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [newTemplate, setNewTemplate] = useState<Partial<RetrievalStrategyTemplate>>({
    templateKey: "",
    name: "",
    topK: 10,
    rerank: false,
    metadataFilter: {},
    options: {}
  });

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

  useEffect(() => {
    if (!selectedKbId) return;
    const loadTemplates = async () => {
      try {
        setLoading(true);
        const data = await listStrategyTemplates(selectedKbId);
        setTemplates(data || []);
      } catch (error) {
        toast.error(getErrorMessage(error, "加载策略模板失败"));
        console.error(error);
      } finally {
        setLoading(false);
      }
    };
    loadTemplates();
  }, [selectedKbId]);

  const handleCreate = async () => {
    if (!selectedKbId || !newTemplate.templateKey?.trim()) {
      toast.error("请输入模板 Key");
      return;
    }

    try {
      await createStrategyTemplate(selectedKbId, newTemplate as Omit<RetrievalStrategyTemplate, "createTime" | "updateTime">);
      toast.success("策略模板创建成功");
      setCreateOpen(false);
      const data = await listStrategyTemplates(selectedKbId);
      setTemplates(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "创建策略模板失败"));
      console.error(error);
    }
  };

  const handleDelete = async () => {
    if (!selectedKbId || !deleteTarget) return;
    try {
      await deleteStrategyTemplate(selectedKbId, deleteTarget);
      toast.success("策略模板已删除");
      setDeleteTarget(null);
      const data = await listStrategyTemplates(selectedKbId);
      setTemplates(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "删除失败"));
      console.error(error);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="策略模板" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">检索策略模板</h1>
          <p className="admin-page-subtitle">管理知识库的检索策略配置模板</p>
        </div>
        <div className="admin-page-actions">
          <Select value={selectedKbId} onValueChange={setSelectedKbId}>
            <SelectTrigger className="w-[200px]"><SelectValue placeholder="选择知识库" /></SelectTrigger>
            <SelectContent>
              {kbs.map((kb) => (
                <SelectItem key={kb.id} value={kb.id}>{kb.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={() => navigate("/admin/rag-evaluation")}>
            返回评测
          </Button>
          <Button className="admin-primary-gradient" onClick={() => setCreateOpen(true)}>
            <Plus className="w-4 h-4 mr-1" />
            新增模板
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="text-center py-8 text-muted-foreground">加载中...</div>
          ) : templates.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">暂无策略模板</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>模板 Key</TableHead>
                  <TableHead>名称</TableHead>
                  <TableHead className="w-[80px]">TopK</TableHead>
                  <TableHead className="w-[80px]">Rerank</TableHead>
                  <TableHead className="w-[100px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {templates.map((tpl) => (
                  <TableRow key={tpl.templateKey}>
                    <TableCell className="font-mono text-sm">{tpl.templateKey || "-"}</TableCell>
                    <TableCell className="font-medium">{tpl.name || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{tpl.topK ?? "-"}</TableCell>
                    <TableCell>
                      <Badge variant={tpl.rerank ? "default" : "secondary"}>
                        {tpl.rerank ? "是" : "否"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive" onClick={() => setDeleteTarget(tpl.templateKey || null)}>
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* 创建模板对话框 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-[520px]">
          <DialogHeader>
            <DialogTitle>新增策略模板</DialogTitle>
            <DialogDescription>配置检索策略参数</DialogDescription>
          </DialogHeader>
          <StrategyEditor template={newTemplate} onChange={setNewTemplate} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" onClick={handleCreate}>创建</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 删除确认 */}
      <AlertDialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>确定要删除此策略模板吗？此操作不可恢复。</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} className="bg-destructive text-destructive-foreground">删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
