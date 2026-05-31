import { useEffect, useState } from "react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import { compareVersionQuality, type VersionQualityDiff } from "@/services/ragEvaluationService";
import { getKnowledgeBases, type KnowledgeBase } from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";

export function VersionQualityComparePage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.RAG_EVALUATION);

  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string>("");
  const [baseVersionId, setBaseVersionId] = useState("");
  const [candidateVersionId, setCandidateVersionId] = useState("");
  const [comparing, setComparing] = useState(false);
  const [result, setResult] = useState<VersionQualityDiff | null>(null);

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

  const handleCompare = async () => {
    if (!selectedKbId || !baseVersionId.trim() || !candidateVersionId.trim()) {
      toast.error("请输入 Base 和 Candidate 版本 ID");
      return;
    }

    try {
      setComparing(true);
      const data = await compareVersionQuality(selectedKbId, baseVersionId.trim(), candidateVersionId.trim());
      setResult(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "版本质量对比失败"));
      console.error(error);
    } finally {
      setComparing(false);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="版本质量对比" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">版本质量对比</h1>
          <p className="admin-page-subtitle">对比不同版本的知识库质量差异</p>
        </div>
      </div>

      <Card className="mb-4">
        <CardContent className="pt-6">
          <div className="flex flex-wrap items-end gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">知识库</label>
              <Select value={selectedKbId} onValueChange={setSelectedKbId}>
                <SelectTrigger className="w-[200px]"><SelectValue placeholder="选择知识库" /></SelectTrigger>
                <SelectContent>
                  {kbs.map((kb) => (
                    <SelectItem key={kb.id} value={kb.id}>{kb.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Base 版本 ID</label>
              <Input value={baseVersionId} onChange={(e) => setBaseVersionId(e.target.value)} placeholder="基础版本 ID" className="w-[200px]" />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Candidate 版本 ID</label>
              <Input value={candidateVersionId} onChange={(e) => setCandidateVersionId(e.target.value)} placeholder="候选版本 ID" className="w-[200px]" />
            </div>
            <Button className="admin-primary-gradient" disabled={comparing} onClick={handleCompare}>
              {comparing ? "对比中..." : "执行对比"}
            </Button>
          </div>
        </CardContent>
      </Card>

      {result && (
        <div className="space-y-4">
          <Card>
            <CardHeader><CardTitle>覆盖率对比</CardTitle></CardHeader>
            <CardContent>
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <div className="text-slate-500 text-sm">Base 覆盖率</div>
                  <div className="text-2xl font-bold">{((result.baseCoverage ?? 0) * 100).toFixed(1)}%</div>
                </div>
                <div>
                  <div className="text-slate-500 text-sm">Candidate 覆盖率</div>
                  <div className="text-2xl font-bold">{((result.candidateCoverage ?? 0) * 100).toFixed(1)}%</div>
                </div>
                <div>
                  <div className="text-slate-500 text-sm">差异</div>
                  <div className={`text-2xl font-bold ${(result.diffCoverage ?? 0) >= 0 ? "text-green-600" : "text-red-600"}`}>
                    {((result.diffCoverage ?? 0) * 100).toFixed(1)}%
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          {(result.degradedSamples && result.degradedSamples.length > 0) && (
            <Card>
              <CardHeader><CardTitle>退化样本</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {result.degradedSamples.map((sample, idx) => (
                    <div key={idx} className="p-3 bg-red-50 rounded-lg text-sm">
                      <div className="font-medium">{sample.documentId || "-"}</div>
                      <div className="text-muted-foreground">Base: {((sample.baseQuality ?? 0) * 100).toFixed(1)}% → Candidate: {((sample.candidateQuality ?? 0) * 100).toFixed(1)}%</div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {(result.improvedSamples && result.improvedSamples.length > 0) && (
            <Card>
              <CardHeader><CardTitle>收益样本</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {result.improvedSamples.map((sample, idx) => (
                    <div key={idx} className="p-3 bg-green-50 rounded-lg text-sm">
                      <div className="font-medium">{sample.documentId || "-"}</div>
                      <div className="text-muted-foreground">Base: {((sample.baseQuality ?? 0) * 100).toFixed(1)}% → Candidate: {((sample.candidateQuality ?? 0) * 100).toFixed(1)}%</div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
