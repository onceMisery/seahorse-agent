import { useEffect, useState } from "react";
import { CheckCircle, XCircle, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  triggerProductionGate,
  getLatestProductionGate,
  type ProductionGateReport
} from "@/services/productionGateService";
import { getErrorMessage } from "@/utils/error";

export function ProductionGatePanel({ agentId }: { agentId: string }) {
  const [report, setReport] = useState<ProductionGateReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [triggering, setTriggering] = useState(false);

  const fetchReport = async () => {
    setLoading(true);
    try {
      const data = await getLatestProductionGate(agentId);
      setReport(data ?? null);
    } catch {
      setReport(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReport();
  }, [agentId]);

  const handleTrigger = async () => {
    setTriggering(true);
    try {
      await triggerProductionGate(agentId);
      toast.success("门禁检查已触发");
      fetchReport();
    } catch (error) {
      toast.error(getErrorMessage(error, "触发失败"));
    } finally {
      setTriggering(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700">生产门禁</h3>
        <Button variant="outline" size="sm" onClick={handleTrigger} disabled={triggering}>
          <RefreshCw className={`mr-1 h-3 w-3 ${triggering ? "animate-spin" : ""}`} />
          触发检查
        </Button>
      </div>

      {loading ? (
        <div className="py-4 text-center text-sm text-slate-500">加载中...</div>
      ) : report ? (
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-slate-500">状态:</span>
            <span className={`font-medium ${report.status === "PASSED" ? "text-emerald-600" : "text-red-600"}`}>
              {report.status ?? "-"}
            </span>
            {report.generatedAt ? (
              <span className="ml-auto text-xs text-slate-400">
                {new Date(report.generatedAt).toLocaleString("zh-CN")}
              </span>
            ) : null}
          </div>

          {report.checks?.length ? (
            <div className="space-y-1">
              {report.checks.map((check, i) => (
                <div
                  key={i}
                  className={`flex items-start gap-2 rounded border p-2 text-sm ${
                    check.passed ? "border-emerald-200 bg-emerald-50" : "border-red-200 bg-red-50"
                  }`}
                >
                  {check.passed ? (
                    <CheckCircle className="mt-0.5 h-4 w-4 text-emerald-600" />
                  ) : (
                    <XCircle className="mt-0.5 h-4 w-4 text-red-600" />
                  )}
                  <div>
                    <div className="font-medium text-slate-700">{check.checkType}</div>
                    <div className="text-xs text-slate-500">{check.message}</div>
                    {check.detail ? (
                      <div className="mt-1 text-xs text-slate-400">{check.detail}</div>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          暂无门禁报告，点击"触发检查"生成
        </div>
      )}
    </div>
  );
}
