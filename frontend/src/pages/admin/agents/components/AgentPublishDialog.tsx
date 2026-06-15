import { useState } from "react";
import { toast } from "sonner";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { publishAgent, type AgentPublishCheck } from "@/services/agentDefinitionService";
import { getErrorMessage } from "@/utils/error";

interface AgentPublishDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  agentId: string;
  publishCheck: AgentPublishCheck | null;
  skillSetJson?: string;
  onSuccess: () => void;
}

export function AgentPublishDialog({
  open,
  onOpenChange,
  agentId,
  publishCheck,
  skillSetJson = "{}",
  onSuccess
}: AgentPublishDialogProps) {
  const [publishing, setPublishing] = useState(false);
  const [form, setForm] = useState({
    instructions: "",
    toolSetJson: "[]",
    modelConfigJson: "{}",
    memoryConfigJson: "{}",
    guardrailConfigJson: "{}",
    changeSummary: ""
  });

  const hasFailedChecks = publishCheck?.checks?.some((check) => !check.passed);

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
      toast.error("发布备注不能超过 500 个字符");
      return;
    }

    try {
      setPublishing(true);
      await publishAgent(agentId, {
        instructions: form.instructions.trim(),
        toolSetJson: form.toolSetJson.trim() || "[]",
        skillSetJson: skillSetJson.trim() || "{}",
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
    } finally {
      setPublishing(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="sm:max-w-[560px]">
        <AlertDialogHeader>
          <AlertDialogTitle>确认发布 Agent</AlertDialogTitle>
          <AlertDialogDescription>
            发布后 Agent 将进入生产状态。请确认发布检查已通过，并确认本版本的工具、模型与 Skill 快照。
          </AlertDialogDescription>
        </AlertDialogHeader>

        {publishCheck?.checks && publishCheck.checks.length > 0 ? (
          <div className="mb-4 space-y-2">
            <div className="text-sm font-medium">发布检查结果：</div>
            {publishCheck.checks.map((check, index) => (
              <div key={`${check.checkType}-${index}`} className={`rounded p-2 text-sm ${check.passed ? "bg-green-50 text-green-700" : "bg-red-50 text-red-700"}`}>
                <Badge variant={check.passed ? "default" : "destructive"} className="mr-2">
                  {check.passed ? "通过" : "失败"}
                </Badge>
                {check.checkType}: {check.message || (check.passed ? "OK" : "未通过")}
              </div>
            ))}
          </div>
        ) : null}

        <div className="space-y-3">
          <div className="space-y-2">
            <label className="text-sm font-medium">发布指令</label>
            <Textarea
              value={form.instructions}
              onChange={(event) => setForm((prev) => ({ ...prev, instructions: event.target.value }))}
              placeholder="请输入本版本 Agent 指令"
              rows={4}
            />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-2">
              <label className="text-sm font-medium">工具集 JSON</label>
              <Input value={form.toolSetJson} onChange={(event) => setForm((prev) => ({ ...prev, toolSetJson: event.target.value }))} />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">模型配置 JSON</label>
              <Input value={form.modelConfigJson} onChange={(event) => setForm((prev) => ({ ...prev, modelConfigJson: event.target.value }))} />
            </div>
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium">Skill 快照 JSON</label>
            <Textarea readOnly value={skillSetJson} rows={4} className="font-mono text-xs" />
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium">发布备注</label>
            <Textarea
              value={form.changeSummary}
              onChange={(event) => setForm((prev) => ({ ...prev, changeSummary: event.target.value }))}
              placeholder="请输入发布原因或备注"
              maxLength={500}
              rows={3}
            />
          </div>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction
            onClick={handlePublish}
            disabled={publishing || Boolean(hasFailedChecks) || !form.instructions.trim() || !form.changeSummary.trim()}
            className={hasFailedChecks ? "cursor-not-allowed opacity-50" : ""}
          >
            {publishing ? "发布中..." : "确认发布"}
          </AlertDialogAction>
        </AlertDialogFooter>

        {hasFailedChecks ? (
          <p className="mt-2 text-xs text-red-500">存在未通过的检查项，请修复后再发布。</p>
        ) : null}
      </AlertDialogContent>
    </AlertDialog>
  );
}
