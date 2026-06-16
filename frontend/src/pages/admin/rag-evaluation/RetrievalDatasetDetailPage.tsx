import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Play, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  getDataset,
  evaluateDataset,
  compareStrategies,
  listEvaluationRuns,
  listEvaluationComparisons,
  listStrategyTemplates,
  promoteStrategyFromComparison,
  type EvaluationRun,
  type EvaluationComparison,
  type RetrievalStrategyTemplate
} from "@/services/ragEvaluationService";
import { EvaluationResultPanel } from "./components/EvaluationResultPanel";
import { getErrorMessage } from "@/utils/error";

export function RetrievalDatasetDetailPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.RAG_EVALUATION);
  const { kbId, datasetId } = useParams<{ kbId: string; datasetId: string }>();
  const navigate = useNavigate();

  const [dataset, setDataset] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [runs, setRuns] = useState<EvaluationRun[]>([]);
  const [comparisons, setComparisons] = useState<EvaluationComparison[]>([]);
  const [strategies, setStrategies] = useState<RetrievalStrategyTemplate[]>([]);
  const [evaluateOpen, setEvaluateOpen] = useState(false);
  const [compareOpen, setCompareOpen] = useState(false);
  const [selectedStrategy, setSelectedStrategy] = useState("");
  const [baseStrategy, setBaseStrategy] = useState("");
  const [candidateStrategy, setCandidateStrategy] = useState("");
  const [running, setRunning] = useState(false);

  useEffect(() => {
    if (!featureState.enabled || !kbId || !datasetId) return;

    const load = async () => {
      try {
        setLoading(true);
        const [ds, rs, cs, st] = await Promise.all([
          getDataset(kbId, datasetId).catch(() => null),
          listEvaluationRuns(kbId, datasetId).catch(() => []),
          listEvaluationComparisons(kbId, datasetId).catch(() => []),
          listStrategyTemplates(kbId).catch(() => [])
        ]);
        setDataset(ds as Record<string, unknown> | null);
        setRuns(rs as EvaluationRun[] || []);
        setComparisons(cs as EvaluationComparison[] || []);
        setStrategies(st as RetrievalStrategyTemplate[] || []);
      } catch (error) {
        toast.error(getErrorMessage(error, "加载数据集详情失败"));
        console.error(error);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [featureState.enabled, kbId, datasetId]);

  const handleEvaluate = async () => {
    if (!kbId || !datasetId || !selectedStrategy) {
      toast.error("请选择策略");
      return;
    }

    try {
      setRunning(true);
      await evaluateDataset(kbId, datasetId, {
        strategyName: selectedStrategy,
        topK: 5
      });
      toast.success("评测已触发");
      setEvaluateOpen(false);
      const rs = await listEvaluationRuns(kbId, datasetId);
      setRuns(rs || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "评测失败"));
      console.error(error);
    } finally {
      setRunning(false);
    }
  };

  const handleCompare = async () => {
    if (!kbId || !datasetId || !baseStrategy || !candidateStrategy) {
      toast.error("请选择两个策略进行对比");
      return;
    }

    try {
      setRunning(true);
      await compareStrategies(kbId, datasetId, {
        baselineStrategyName: baseStrategy,
        topK: 5,
        strategies: [
          { strategyName: baseStrategy, topK: 5, options: {} },
          { strategyName: candidateStrategy, topK: 5, options: {} }
        ]
      });
      toast.success("对比已触发");
      setCompareOpen(false);
      const cs = await listEvaluationComparisons(kbId, datasetId);
      setComparisons(cs || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "对比失败"));
      console.error(error);
    } finally {
      setRunning(false);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="RAG 评测" />;
  }

  if (loading) {
    return <div className="admin-page"><div className="text-center py-8 text-muted-foreground">加载中...</div></div>;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => navigate("/admin/rag-evaluation")}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="admin-page-title">数据集详情</h1>
            <p className="admin-page-subtitle">{(dataset as Record<string, unknown>)?.name || datasetId}</p>
          </div>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={() => setEvaluateOpen(true)}>
            <Play className="w-4 h-4 mr-1" />
            运行评测
          </Button>
          <Button variant="outline" onClick={() => setCompareOpen(true)}>
            策略对比
          </Button>
          <Button variant="outline" onClick={() => { /* reload */ }}>
            <RefreshCw className="w-4 h-4 mr-1" />
            刷新
          </Button>
        </div>
      </div>

      <Tabs defaultValue="runs">
        <TabsList>
          <TabsTrigger value="runs">评测运行</TabsTrigger>
          <TabsTrigger value="comparisons">策略对比</TabsTrigger>
        </TabsList>

        <TabsContent value="runs">
          <Card>
            <CardHeader><CardTitle>评测运行记录</CardTitle></CardHeader>
            <CardContent>
              {runs.length === 0 ? (
                <div className="text-center py-4 text-muted-foreground">暂无评测运行记录</div>
              ) : (
                <div className="space-y-3">
                  {runs.map((run) => (
                    <EvaluationResultPanel key={run.runId} run={run} />
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="comparisons">
          <Card>
            <CardHeader><CardTitle>策略对比记录</CardTitle></CardHeader>
            <CardContent>
              {comparisons.length === 0 ? (
                <div className="text-center py-4 text-muted-foreground">暂无对比记录</div>
              ) : (
                <div className="space-y-3">
                  {comparisons.map((comp) => (
                    <div key={comp.comparisonId} className="p-4 bg-slate-50 rounded-lg">
                      <div className="flex items-center gap-4 mb-3">
                        <Badge variant="outline">{comp.baseStrategyKey}</Badge>
                        <span className="text-slate-400">vs</span>
                        <Badge variant="outline">{comp.candidateStrategyKey}</Badge>
                        <Badge variant={comp.status === "COMPLETED" ? "default" : "secondary"}>{comp.status}</Badge>
                      </div>
                      {comp.diffHitRate !== undefined && (
                        <div className="grid grid-cols-3 gap-4 text-sm">
                          <div>
                            <div className="text-slate-500">Base 命中率</div>
                            <div className="font-medium">{((comp.baseHitRate ?? 0) * 100).toFixed(1)}%</div>
                          </div>
                          <div>
                            <div className="text-slate-500">Candidate 命中率</div>
                            <div className="font-medium">{((comp.candidateHitRate ?? 0) * 100).toFixed(1)}%</div>
                          </div>
                          <div>
                            <div className="text-slate-500">差异</div>
                            <div className={`font-medium ${(comp.diffHitRate ?? 0) >= 0 ? "text-green-600" : "text-red-600"}`}>
                              {((comp.diffHitRate ?? 0) * 100).toFixed(1)}%
                            </div>
                          </div>
                        </div>
                      )}
                      {comp.status === "COMPLETED" && comp.comparisonId && (comp.diffHitRate ?? 0) >= 0 && (
                        <div className="mt-3 flex justify-end">
                          <Button
                            size="sm"
                            variant="outline"
                            className="border-green-500 text-green-700 hover:bg-green-50"
                            onClick={async () => {
                              if (!kbId || !datasetId || !comp.comparisonId) return;
                              try {
                                await promoteStrategyFromComparison(kbId, datasetId, comp.comparisonId, {
                                  strategyKey: comp.candidateStrategyKey || ""
                                });
                                toast.success("策略已推广为推荐模板");
                                const st = await listStrategyTemplates(kbId);
                                setStrategies(st || []);
                              } catch (error) {
                                toast.error(getErrorMessage(error, "推广失败"));
                              }
                            }}
                          >
                            推荐为线上策略
                          </Button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* 运行评测对话框 */}
      <Dialog open={evaluateOpen} onOpenChange={setEvaluateOpen}>
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle>运行评测</DialogTitle>
            <DialogDescription>选择一个检索策略运行评测</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <Select value={selectedStrategy} onValueChange={setSelectedStrategy}>
              <SelectTrigger><SelectValue placeholder="选择策略" /></SelectTrigger>
              <SelectContent>
                {strategies.map((s) => (
                  <SelectItem key={s.templateKey} value={s.templateKey || ""}>{s.name || s.templateKey}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEvaluateOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" disabled={running || !selectedStrategy} onClick={handleEvaluate}>
              {running ? "运行中..." : "开始评测"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 策略对比对话框 */}
      <Dialog open={compareOpen} onOpenChange={setCompareOpen}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>策略对比</DialogTitle>
            <DialogDescription>选择两个策略进行对比</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">Base 策略</label>
              <Select value={baseStrategy} onValueChange={setBaseStrategy}>
                <SelectTrigger><SelectValue placeholder="选择基础策略" /></SelectTrigger>
                <SelectContent>
                  {strategies.map((s) => (
                    <SelectItem key={s.templateKey} value={s.templateKey || ""}>{s.name || s.templateKey}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Candidate 策略</label>
              <Select value={candidateStrategy} onValueChange={setCandidateStrategy}>
                <SelectTrigger><SelectValue placeholder="选择候选策略" /></SelectTrigger>
                <SelectContent>
                  {strategies.filter((s) => s.templateKey !== baseStrategy).map((s) => (
                    <SelectItem key={s.templateKey} value={s.templateKey || ""}>{s.name || s.templateKey}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCompareOpen(false)}>取消</Button>
            <Button className="admin-primary-gradient" disabled={running || !baseStrategy || !candidateStrategy} onClick={handleCompare}>
              {running ? "运行中..." : "开始对比"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
