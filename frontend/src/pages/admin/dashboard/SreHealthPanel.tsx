import { useEffect, useState } from "react";
import { CheckCircle, RefreshCw, XCircle } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { getAiInfraSreHealth } from "@/services/aiInfraService";
import { getErrorMessage } from "@/utils/error";

export function SreHealthPanel() {
  const [health, setHealth] = useState<Record<string, unknown> | null>(null);
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

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700">SRE Health</h3>
        <Button variant="outline" size="sm" onClick={fetchHealth} disabled={loading}>
          <RefreshCw className={`mr-1 h-3 w-3 ${loading ? "animate-spin" : ""}`} />
        </Button>
      </div>

      {loading ? (
        <div className="py-4 text-center text-sm text-slate-500">加载中...</div>
      ) : health ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {Object.entries(health).map(([key, value]) => (
            <Card key={key}>
              <CardContent className="flex items-center gap-3 pt-4 pb-4">
                {value === true || value === "UP" || value === "HEALTHY" ? (
                  <CheckCircle className="h-5 w-5 text-emerald-500" />
                ) : value === false || value === "DOWN" || value === "UNHEALTHY" ? (
                  <XCircle className="h-5 w-5 text-red-500" />
                ) : (
                  <div className="flex h-5 w-5 items-center justify-center rounded-full bg-slate-100 text-xs font-bold text-slate-500">
                    -
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="text-xs text-slate-500">{key}</div>
                  <div className="truncate text-sm font-medium">
                    {typeof value === "object" ? JSON.stringify(value) : String(value)}
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          无法加载健康状态
        </div>
      )}
    </div>
  );
}
