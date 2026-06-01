import { useState } from "react";
import { Play } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { api } from "@/services/api";
import { getErrorMessage } from "@/utils/error";

interface GoldenProfile {
  profileId?: string;
  userId?: string;
  label?: string;
}

export function MemoryRecallEvalPanel() {
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<Record<string, unknown> | null>(null);

  const handleRunEval = async () => {
    setRunning(true);
    try {
      const data = await api.post<Record<string, unknown>, Record<string, unknown>>(
        "/memories/recall-quality/evaluate"
      );
      setResult(data);
      toast.success("Recall 评测已触发");
    } catch (error) {
      toast.error(getErrorMessage(error, "评测失败"));
    } finally {
      setRunning(false);
    }
  };

  const handleRunProfile = async (profileId: string) => {
    try {
      await api.post(`/memories/recall-quality/golden/profiles/${encodeURIComponent(profileId)}/run`);
      toast.success(`Profile ${profileId} 评测已触发`);
    } catch (error) {
      toast.error(getErrorMessage(error, "评测失败"));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700">Recall 评测</h3>
        <Button size="sm" onClick={handleRunEval} disabled={running}>
          <Play className={`mr-1 h-3 w-3 ${running ? "animate-spin" : ""}`} />
          运行评测
        </Button>
      </div>

      {result ? (
        <pre className="max-h-[300px] overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs text-slate-700">
          {JSON.stringify(result, null, 2)}
        </pre>
      ) : (
        <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
          点击"运行评测"触发 Recall 质量评估
        </div>
      )}
    </div>
  );
}
