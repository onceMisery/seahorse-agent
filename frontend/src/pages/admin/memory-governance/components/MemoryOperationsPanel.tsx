import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { api } from "@/services/api";
import { getErrorMessage } from "@/utils/error";

type DataView = "operations" | "outbox" | "profile-facts" | "corrections";

const VIEWS: { key: DataView; label: string; endpoint: string }[] = [
  { key: "operations", label: "操作记录", endpoint: "/memories/operations" },
  { key: "outbox", label: "发件箱", endpoint: "/memories/outbox" },
  { key: "profile-facts", label: "画像事实", endpoint: "/memories/profile-facts" },
  { key: "corrections", label: "修正记录", endpoint: "/memories/corrections" }
];

export function MemoryOperationsPanel() {
  const [activeView, setActiveView] = useState<DataView>("operations");
  const [data, setData] = useState<unknown[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchData = async (view: DataView) => {
    const endpoint = VIEWS.find((v) => v.key === view)?.endpoint;
    if (!endpoint) return;
    setLoading(true);
    try {
      const result = await api.get<unknown[]>(endpoint);
      setData(Array.isArray(result) ? result : []);
    } catch {
      setData([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData(activeView);
  }, [activeView]);

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        {VIEWS.map((v) => (
          <Button
            key={v.key}
            variant={activeView === v.key ? "default" : "outline"}
            size="sm"
            onClick={() => setActiveView(v.key)}
          >
            {v.label}
          </Button>
        ))}
        <Button variant="ghost" size="sm" onClick={() => fetchData(activeView)} className="ml-auto">
          <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} />
        </Button>
      </div>

      {loading ? (
        <div className="py-4 text-center text-sm text-slate-500">加载中...</div>
      ) : data.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          暂无数据
        </div>
      ) : (
        <pre className="max-h-[400px] overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs text-slate-700">
          {JSON.stringify(data, null, 2)}
        </pre>
      )}
    </div>
  );
}
