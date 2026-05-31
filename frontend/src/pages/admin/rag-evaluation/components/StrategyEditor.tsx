import { useState, useEffect } from "react";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type { RetrievalStrategyTemplate } from "@/services/ragEvaluationService";

interface StrategyEditorProps {
  template: Partial<RetrievalStrategyTemplate>;
  onChange: (template: Partial<RetrievalStrategyTemplate>) => void;
}

export function StrategyEditor({ template, onChange }: StrategyEditorProps) {
  const [optionsJson, setOptionsJson] = useState("{}");
  const [optionsError, setOptionsError] = useState("");

  useEffect(() => {
    setOptionsJson(JSON.stringify(template.options || {}, null, 2));
  }, [template.options]);

  const handleOptionsChange = (value: string) => {
    setOptionsJson(value);
    try {
      const parsed = JSON.parse(value);
      setOptionsError("");
      onChange({ ...template, options: parsed });
    } catch {
      setOptionsError("JSON 格式不合法");
    }
  };

  return (
    <div className="space-y-3">
      <div className="space-y-2">
        <label className="text-sm font-medium">模板 Key</label>
        <Input
          value={template.templateKey || ""}
          onChange={(e) => onChange({ ...template, templateKey: e.target.value })}
          placeholder="唯一标识符，如 default-strategy"
        />
      </div>
      <div className="space-y-2">
        <label className="text-sm font-medium">名称</label>
        <Input
          value={template.name || ""}
          onChange={(e) => onChange({ ...template, name: e.target.value })}
          placeholder="策略名称"
        />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-2">
          <label className="text-sm font-medium">TopK</label>
          <Input
            type="number"
            value={template.topK ?? 10}
            onChange={(e) => onChange({ ...template, topK: parseInt(e.target.value) || 10 })}
          />
        </div>
        <div className="space-y-2">
          <label className="text-sm font-medium">Rerank</label>
          <select
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={template.rerank ? "true" : "false"}
            onChange={(e) => onChange({ ...template, rerank: e.target.value === "true" })}
          >
            <option value="false">否</option>
            <option value="true">是</option>
          </select>
        </div>
      </div>
      <div className="space-y-2">
        <label className="text-sm font-medium">高级选项 (JSON)</label>
        <Textarea
          value={optionsJson}
          onChange={(e) => handleOptionsChange(e.target.value)}
          rows={6}
          className="font-mono text-sm"
        />
        {optionsError && <p className="text-xs text-destructive">{optionsError}</p>}
      </div>
    </div>
  );
}
