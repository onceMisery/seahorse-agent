import { useEffect, useState } from "react";
import { Save } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  getMemoryPolicyConfig,
  updateMemoryPolicyConfig,
} from "@/services/memoryGovernanceService";
import { getErrorMessage } from "@/utils/error";

export function MemoryPolicyConfigPanel() {
  const [config, setConfig] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setLoading(true);
    getMemoryPolicyConfig()
      .then((data) => setConfig(JSON.stringify(data, null, 2)))
      .catch(() => setConfig("{}"))
      .finally(() => setLoading(false));
  }, []);

  const handleSave = async () => {
    try {
      const parsed = JSON.parse(config);
      setSaving(true);
      await updateMemoryPolicyConfig(parsed);
      toast.success("策略配置已保存");
    } catch (error) {
      if (error instanceof SyntaxError) {
        toast.error("JSON 格式无效");
      } else {
        toast.error(getErrorMessage(error, "保存失败"));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700">策略配置</h3>
        <Button size="sm" onClick={handleSave} disabled={saving || loading}>
          <Save className="mr-1 h-3 w-3" />
          保存
        </Button>
      </div>

      {loading ? (
        <div className="py-4 text-center text-sm text-slate-500">加载中...</div>
      ) : (
        <textarea
          className="min-h-[300px] w-full rounded-lg border border-slate-200 bg-slate-50 p-4 font-mono text-xs text-slate-700 focus:border-slate-400 focus:outline-none"
          value={config}
          onChange={(e) => setConfig(e.target.value)}
          spellCheck={false}
        />
      )}
    </div>
  );
}
