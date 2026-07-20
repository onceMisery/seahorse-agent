import { useEffect, useState } from "react";
import { History, RefreshCw } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { getGateResultHistory, type GateResult } from "@/services/gateResultService";

interface GateResultHistoryProps {
  subjectType: string;
  subjectId: string;
  /** 一次拉取的历史条数，默认 10。 */
  limit?: number;
}

function formatTime(dateStr?: string | null) {
  if (!dateStr) return "-";
  return new Date(dateStr).toLocaleString("zh-CN");
}

function statusBadgeVariant(status?: string): "default" | "destructive" | "secondary" {
  if (status === "FAIL") return "destructive";
  if (status === "WARN") return "secondary";
  return "default";
}

/**
 * 展示某个受治理对象的统一门禁历史记录。
 * append-only 门禁表按 checkedAt 倒序返回，用于审计追溯和回归对比。
 */
export function GateResultHistory({ subjectType, subjectId, limit = 10 }: GateResultHistoryProps) {
  const [records, setRecords] = useState<GateResult[] | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    if (!subjectType || !subjectId) return;
    setLoading(true);
    try {
      const data = await getGateResultHistory(subjectType, subjectId, limit);
      setRecords(Array.isArray(data) ? data : []);
    } catch {
      setRecords([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subjectType, subjectId, limit]);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
          <History className="h-4 w-4" />
          门禁历史
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          <RefreshCw className={`mr-1 h-3 w-3 ${loading ? "animate-spin" : ""}`} />
          刷新
        </Button>
      </div>

      {loading ? (
        <div className="py-4 text-center text-sm text-muted-foreground">加载中...</div>
      ) : records && records.length > 0 ? (
        <div className="space-y-2">
          {records.map((record, index) => (
            <div
              key={`${record.checkedAt ?? ""}-${index}`}
              className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border p-3"
            >
              <div className="flex items-center gap-2">
                <Badge variant={statusBadgeVariant(record.status)}>{record.status}</Badge>
                <span className="text-sm text-slate-600">
                  阻断 {record.blockingCodes?.length ?? 0} · 检查项 {record.items?.length ?? 0}
                </span>
                {record.sourceType ? (
                  <span className="text-xs text-slate-400">来源 {record.sourceType}</span>
                ) : null}
              </div>
              <span className="text-xs text-muted-foreground">{formatTime(record.checkedAt)}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="rounded-md border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-muted-foreground">
          暂无门禁历史记录
        </div>
      )}
    </div>
  );
}
