import { useState } from "react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { publishAgent, type AgentPublishCheck } from "@/services/agentDefinitionService";
import { getErrorMessage } from "@/utils/error";

interface AgentPublishDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  agentId: string;
  publishCheck: AgentPublishCheck | null;
  onSuccess: () => void;
}

export function AgentPublishDialog({ open, onOpenChange, agentId, publishCheck, onSuccess }: AgentPublishDialogProps) {
  const [publishing, setPublishing] = useState(false);
  const [form, setForm] = useState({
    instructions: "",
    toolSetJson: "[]",
    modelConfigJson: "{}",
    memoryConfigJson: "{}",
    guardrailConfigJson: "{}",
    changeSummary: ""
  });

  const hasFailedChecks = publishCheck?.checks?.some((c) => !c.passed);

  const handlePublish = async () => {
    if (!form.instructions.trim()) {
      toast.error("请输入发布指令");
      return;
    }
    const changeSummary = form.changeSummary.trim();
    if (!changeSummary) {
      toast.error("请输入发布备注");
      return;
    }
    if (changeSummary.length > 500) {
      toast.error("发布备注不能超过 500 字符");
      return;
    }

    try {
      setPublishing(true);
      await publishAgent(agentId, {
        instructions: form.instructions.trim(),
        toolSetJson: form.toolSetJson.trim() || "[]",
        modelConfigJson: form.modelConfigJson.trim() || "{}",
        memoryConfigJson: form.memoryConfigJson.trim() || "{}",
        guardrailConfigJson: form.guardrailConfigJson.trim() || "{}",
        changeSummary
      });
      toast.success("Agent 发布成功");
      setForm({
        instructions: "",
        toolSetJson: "[]",
        modelConfigJson: "{}",
        memoryConfigJson: "{}",
        guardrailConfigJson: "{}",
        changeSummary: ""
      });
      onSuccess();
    } catch (error) {
      toast.error(getErrorMessage(error, "发布失败"));
      console.error(error);
    } finally {
      setPublishing(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="sm:max-w-[520px]">
        <AlertDialogHeader>
          <AlertDialogTitle>确认发布 Agent</AlertDialogTitle>
          <AlertDialogDescription>
            发布后 Agent 将进入生产状态。请确认发布检查已通过。
          </AlertDialogDescription>
        </AlertDialogHeader>

        {publishCheck?.checks && publishCheck.checks.length > 0 && (
          <div className="space-y-2 mb-4">
            <div className="text-sm font-medium">发布检查结果：</div>
            {publishCheck.checks.map((check, idx) => (
              <div key={idx} className={`p-2 rounded text-sm ${check.passed ? "bg-green-50 text-green-700" : "bg-red-50 text-red-700"}`}>
                <Badge variant={check.passed ? "default" : "destructive"} className="mr-2">
                  {check.passed ? "通过" : "失败"}
                </Badge>
                {check.checkType}: {check.message || (check.passed ? "OK" : "未通过")}
              </div>
            ))}
          </div>
        )}

        <div className="space-y-3">
          <div className="space-y-2">
            <label className="text-sm font-medium">发布指令</label>
            <Textarea
              value={form.instructions}
              onChange={(e) => setForm((prev) => ({ ...prev, instructions: e.target.value }))}
              placeholder="请输入本版本 Agent instructions"
              rows={4}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">工具集 JSON</label>
              <Input
                value={form.toolSetJson}
                onChange={(e) => setForm((prev) => ({ ...prev, toolSetJson: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">模型配置 JSON</label>
              <Input
                value={form.modelConfigJson}
                onChange={(e) => setForm((prev) => ({ ...prev, modelConfigJson: e.target.value }))}
              />
            </div>
          </div>
          <label className="text-sm font-medium">发布备注</label>
          <Textarea
            value={form.changeSummary}
            onChange={(e) => setForm((prev) => ({ ...prev, changeSummary: e.target.value }))}
            placeholder="请输入发布原因或备注"
            maxLength={500}
            rows={3}
          />
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction
            onClick={handlePublish}
            disabled={publishing || !!hasFailedChecks || !form.instructions.trim() || !form.changeSummary.trim()}
            className={hasFailedChecks ? "opacity-50 cursor-not-allowed" : ""}
          >
            {publishing ? "发布中..." : "确认发布"}
          </AlertDialogAction>
        </AlertDialogFooter>

        {hasFailedChecks && (
          <p className="text-xs text-red-500 mt-2">存在未通过的检查项，请修复后再发布</p>
        )}
      </AlertDialogContent>
    </AlertDialog>
  );
}
