import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import type { EvaluationRun } from "@/services/ragEvaluationService";

interface EvaluationResultPanelProps {
  run: EvaluationRun;
}

export function EvaluationResultPanel({ run }: EvaluationResultPanelProps) {
  const formatPercent = (value?: number) => {
    if (value === undefined || value === null) return "-";
    return `${(value * 100).toFixed(1)}%`;
  };

  return (
    <Card>
      <CardContent className="pt-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Badge variant="outline">{run.strategyKey || "-"}</Badge>
            <Badge variant={run.status === "COMPLETED" ? "default" : "secondary"}>{run.status || "-"}</Badge>
          </div>
          <span className="text-xs text-muted-foreground">
            {run.createTime ? new Date(run.createTime).toLocaleString("zh-CN") : "-"}
          </span>
        </div>

        <div className="grid grid-cols-4 gap-4 text-sm">
          <div>
            <div className="text-slate-500">命中率</div>
            <div className="font-medium">{formatPercent(run.hitRate)}</div>
          </div>
          <div>
            <div className="text-slate-500">MRR</div>
            <div className="font-medium">{formatPercent(run.mrr)}</div>
          </div>
          <div>
            <div className="text-slate-500">NDCG</div>
            <div className="font-medium">{formatPercent(run.ndcg)}</div>
          </div>
          <div>
            <div className="text-slate-500">失败样本</div>
            <div className="font-medium">{run.failCount ?? 0}</div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
