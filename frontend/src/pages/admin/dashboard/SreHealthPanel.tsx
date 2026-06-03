import { useEffect, useState } from "react";
import { AlertCircle, CheckCircle, RefreshCw, XCircle } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  getAiInfraSreHealth,
  type SreHealthReport,
  type SreHealthStatus,
} from "@/services/aiInfraService";
import { getErrorMessage } from "@/utils/error";

const statusConfig: Record<SreHealthStatus, { icon: typeof CheckCircle; color: string; label: string }> = {
  GREEN: { icon: CheckCircle, color: "text-emerald-500", label: "正常" },
  WARN: { icon: AlertCircle, color: "text-amber-500", label: "警告" },
  RED: { icon: XCircle, color: "text-red-500", label: "异常" },
};

export function SreHealthPanel() {
  const [health, setHealth] = useState<SreHealthReport | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchHealth = async () => {
    setLoading(true);
    try {
      const data = await getAiInfraSreHealth();
      setHealth(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载健康状态失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHealth();
  }, []);

  const overallConfig = health ? statusConfig[health.status] : null;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-medium text-slate-700">SRE Health</h3>
          {overallConfig && (
            <Badge variant="outline" className={`${overallConfig.color} border-current`}>
              {overallConfig.label}
            </Badge>
          )}
          {health?.checkedAt && (
            <span className="text-xs text-slate-400">
              {new Date(health.checkedAt).toLocaleString()}
            </span>
          )}
        </div>
        <Button variant="outline" size="sm" onClick={fetchHealth} disabled={loading}>
          <RefreshCw className={`mr-1 h-3 w-3 ${loading ? "animate-spin" : ""}`} />
        </Button>
      </div>

      {loading ? (
        <div className="py-4 text-center text-sm text-slate-500">加载中...</div>
      ) : health?.items?.length ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {health.items.map((item) => {
            const config = statusConfig[item.status] ?? statusConfig.WARN;
            const Icon = config.icon;
            return (
              <Card key={item.contributorName}>
                <CardContent className="flex items-start gap-3 pt-4 pb-4">
                  <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${config.color}`} />
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium">{item.contributorName}</div>
                    <div className="mt-0.5 text-xs text-slate-500 break-all">{item.message}</div>
                    {item.evidenceRef && (
                      <div className="mt-1 text-xs text-slate-400 break-all">{item.evidenceRef}</div>
                    )}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          {health ? "暂无运行态健康报告" : "无法加载健康状态"}
        </div>
      )}
    </div>
  );
}
