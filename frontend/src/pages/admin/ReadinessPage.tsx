import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle, Info, RefreshCw, XCircle } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  getReadinessSummary,
  runReadinessCheck,
  type ReadinessCheck,
  type ReadinessSummary,
} from "@/services/readinessService";
import { getErrorMessage } from "@/utils/error";

const STATUS_CONFIG = {
  healthy: { icon: CheckCircle, color: "text-emerald-500", label: "系统就绪" },
  degraded: { icon: AlertTriangle, color: "text-amber-500", label: "部分能力降级" },
  blocked: { icon: XCircle, color: "text-red-500", label: "关键能力缺失" },
};

const SEVERITY_CONFIG = {
  error: { badge: "destructive", label: "阻断" },
  warn: { badge: "outline text-amber-600 border-amber-300", label: "降级" },
  info: { badge: "outline text-slate-500 border-slate-200", label: "提示" },
};

const CHECK_STATUS_CONFIG = {
  passed: { icon: CheckCircle, color: "text-emerald-500", label: "通过" },
  failed: { icon: XCircle, color: "text-red-500", label: "失败" },
  skipped: { icon: Info, color: "text-slate-400", label: "跳过" },
};

const MODE_LABELS: Record<string, string> = {
  DEMO: "演示模式",
  RAG: "RAG 模式",
  ENTERPRISE: "企业模式",
};

const MODE_DESCRIPTIONS: Record<string, string> = {
  DEMO: "体验模式，基础聊天和示例任务可用，RAG 和企业治理能力为轻量版本",
  RAG: "知识库问答模式，RAG 检索和 Trace 可用，企业治理能力部分启用",
  ENTERPRISE: "企业级模式，全部能力可用，依赖缺失将阻断相关功能",
};

export default function ReadinessPage() {
  const [summary, setSummary] = useState<ReadinessSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [runningCheckId, setRunningCheckId] = useState<string | null>(null);

  const fetchSummary = async () => {
    setLoading(true);
    try {
      const data = await getReadinessSummary();
      setSummary(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载系统诊断失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSummary();
  }, []);

  const handleRerunCheck = async (checkId: string) => {
    setRunningCheckId(checkId);
    try {
      await runReadinessCheck(checkId);
      await fetchSummary();
      toast.success("检查完成");
    } catch (error) {
      toast.error(getErrorMessage(error, "检查执行失败"));
    } finally {
      setRunningCheckId(null);
    }
  };

  const overallConfig = summary ? STATUS_CONFIG[summary.overall] || STATUS_CONFIG.healthy : null;

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-800">系统诊断</h1>
          <p className="mt-1 text-sm text-slate-500">
            检查系统各项基础设施的可用状态，根据当前产品模式给出诊断和修复建议
          </p>
        </div>
        <Button variant="outline" onClick={fetchSummary} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          刷新诊断
        </Button>
      </div>

      {summary && (
        <>
          {/* Overview Card */}
          <Card>
            <CardContent className="flex items-center gap-6 p-6">
              {overallConfig && (
                <overallConfig.icon className={`h-12 w-12 ${overallConfig.color}`} />
              )}
              <div className="flex-1">
                <div className="flex items-center gap-3">
                  <h2 className="text-lg font-semibold text-slate-800">
                    {overallConfig?.label}
                  </h2>
                  <Badge variant="outline" className="text-xs">
                    {MODE_LABELS[summary.mode] || summary.mode}
                  </Badge>
                </div>
                <p className="mt-1 text-sm text-slate-500">
                  {MODE_DESCRIPTIONS[summary.mode] || ""}
                </p>
                <div className="mt-2 flex items-center gap-4 text-xs text-slate-400">
                  <span className="text-emerald-500">{summary.passedCount} 通过</span>
                  <span className="text-red-500">{summary.failedCount} 失败</span>
                  <span>共 {summary.totalCount} 项检查</span>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Check Items */}
          <div className="space-y-3">
            {summary.checks.map((check) => (
              <CheckItemCard
                key={check.id}
                check={check}
                isRunning={runningCheckId === check.id}
                onRerun={() => handleRerunCheck(check.id)}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function CheckItemCard({
  check,
  isRunning,
  onRerun,
}: {
  check: ReadinessCheck;
  isRunning: boolean;
  onRerun: () => void;
}) {
  const statusConfig = CHECK_STATUS_CONFIG[check.status];
  const severityConfig = SEVERITY_CONFIG[check.severity];
  const StatusIcon = statusConfig.icon;

  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex items-start gap-4">
          <StatusIcon className={`mt-0.5 h-5 w-5 shrink-0 ${statusConfig.color}`} />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-slate-800">{check.name}</span>
              <Badge variant="outline" className={`text-[10px] ${severityConfig.badge}`}>
                {severityConfig.label}
              </Badge>
              <Badge variant="outline" className={`text-[10px] ${statusConfig.color} border-current`}>
                {statusConfig.label}
              </Badge>
            </div>
            <p className="mt-1 text-sm text-slate-600">{check.message}</p>

            {check.status === "failed" && (
              <div className="mt-2 space-y-1">
                {check.impact && (
                  <p className="text-xs text-amber-600">
                    <span className="font-medium">影响：</span>{check.impact}
                  </p>
                )}
                {check.suggestion && (
                  <p className="text-xs text-blue-600">
                    <span className="font-medium">建议：</span>{check.suggestion}
                  </p>
                )}
              </div>
            )}
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={onRerun}
            disabled={isRunning}
            className="shrink-0"
            aria-label={`重新检查 ${check.name}`}
            title={`重新检查 ${check.name}`}
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isRunning ? "animate-spin" : ""}`} />
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
