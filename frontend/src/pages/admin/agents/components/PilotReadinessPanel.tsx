import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  generateReadiness,
  getLatestReadiness,
  type PilotReadinessReport
} from "@/services/pilotReadinessService";
import { getErrorMessage } from "@/utils/error";

export function PilotReadinessPanel({ agentId }: { agentId: string }) {
  const [versionId, setVersionId] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [report, setReport] = useState<PilotReadinessReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);

  const fetchReport = async () => {
    if (!agentId || !versionId || !tenantId) return;
    setLoading(true);
    try {
      const data = await getLatestReadiness(agentId, versionId, tenantId);
      setReport(data ?? null);
    } catch {
      setReport(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReport();
  }, [agentId, versionId, tenantId]);

  const handleGenerate = async () => {
    if (!versionId || !tenantId) {
      toast.error("请填写 Version ID 和 Tenant ID");
      return;
    }
    setGenerating(true);
    try {
      await generateReadiness(agentId, versionId, { tenantId, operator: "admin" });
      toast.success("就绪报告已生成");
      fetchReport();
    } catch (error) {
      toast.error(getErrorMessage(error, "生成失败"));
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-medium text-slate-700">企业试点就绪</h3>

      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label className="mb-1 block text-xs text-slate-500">Version ID</label>
          <Input value={versionId} onChange={(e) => setVersionId(e.target.value)} placeholder="版本 ID" className="w-[220px]" />
        </div>
        <div>
          <label className="mb-1 block text-xs text-slate-500">Tenant ID</label>
          <Input value={tenantId} onChange={(e) => setTenantId(e.target.value)} placeholder="租户 ID" className="w-[180px]" />
        </div>
        <Button variant="outline" size="sm" onClick={handleGenerate} disabled={generating || !versionId || !tenantId}>
          <RefreshCw className={`mr-1 h-3 w-3 ${generating ? "animate-spin" : ""}`} />
          生成报告
        </Button>
      </div>

      {loading ? (
        <div className="py-4 text-center text-sm text-slate-500">加载中...</div>
      ) : report ? (
        <div className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
          <div className="flex items-center gap-3 text-sm">
            <span className="text-slate-500">状态:</span>
            <span className={`font-medium ${report.status === "READY" ? "text-emerald-600" : "text-amber-600"}`}>
              {report.status ?? "-"}
            </span>
            {report.generatedAt ? (
              <span className="ml-auto text-xs text-slate-400">
                {new Date(report.generatedAt).toLocaleString("zh-CN")}
              </span>
            ) : null}
          </div>
          {report.sections?.length ? (
            <pre className="max-h-[300px] overflow-auto rounded border border-slate-100 bg-slate-50 p-3 text-xs text-slate-700">
              {JSON.stringify(report.sections, null, 2)}
            </pre>
          ) : null}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          暂无就绪报告
        </div>
      )}
    </div>
  );
}
