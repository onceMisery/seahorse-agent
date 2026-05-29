import * as React from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";

import {
  enhanceResearchPrompt,
  type EnhanceResearchPromptInput
} from "@/components/chat/prompt/promptEnhancer";

interface PromptEnhancerDialogProps {
  open: boolean;
  draft: string;
  onClose: () => void;
  onApply: (enhanced: string) => void;
}

type OutputType = EnhanceResearchPromptInput["outputType"];
type SourcePreference = EnhanceResearchPromptInput["sourcePreference"];
type Depth = EnhanceResearchPromptInput["depth"];

export function PromptEnhancerDialog({ open, draft, onClose, onApply }: PromptEnhancerDialogProps) {
  const [outputType, setOutputType] = React.useState<OutputType>("report");
  const [sourcePreference, setSourcePreference] = React.useState<SourcePreference>("official-and-current");
  const [depth, setDepth] = React.useState<Depth>("standard");

  const enhanced = React.useMemo(
    () => enhanceResearchPrompt({ original: draft, outputType, sourcePreference, depth }),
    [draft, outputType, sourcePreference, depth]
  );

  return (
    <Dialog.Root open={open} onOpenChange={(v) => { if (!v) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay
          className="fixed inset-0 z-40"
          style={{ backgroundColor: "rgba(0,0,0,0.5)" }}
        />
        <Dialog.Content
          className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2 rounded-2xl p-5 shadow-xl"
          style={{
            backgroundColor: "var(--sh-workbench-panel)",
            border: "1px solid var(--sh-workbench-border)"
          }}
        >
          <div className="flex items-center justify-between">
            <Dialog.Title
              className="text-sm font-semibold"
              style={{ color: "var(--theme-text-primary)" }}
            >
              整理问题
            </Dialog.Title>
            <Dialog.Close asChild>
              <button
                type="button"
                aria-label="关闭"
                className="flex h-6 w-6 items-center justify-center rounded transition-colors hover:bg-white/10"
                style={{ color: "var(--theme-text-muted)" }}
              >
                <X className="h-4 w-4" />
              </button>
            </Dialog.Close>
          </div>

          <div className="mt-4 space-y-3">
            <div>
              <label
                className="mb-1 block text-[11px] font-medium uppercase"
                style={{ color: "var(--theme-text-muted)" }}
              >
                原始问题
              </label>
              <div
                className="rounded-lg px-3 py-2 text-xs leading-relaxed"
                style={{
                  backgroundColor: "var(--sh-workbench-panel-subtle)",
                  color: "var(--theme-text-secondary)",
                  border: "1px solid var(--sh-workbench-border)"
                }}
              >
                {draft}
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div>
                <label
                  htmlFor="pe-output-type"
                  className="mb-1 block text-[11px] font-medium uppercase"
                  style={{ color: "var(--theme-text-muted)" }}
                >
                  输出类型
                </label>
                <select
                  id="pe-output-type"
                  value={outputType}
                  onChange={(e) => setOutputType(e.target.value as OutputType)}
                  className="w-full rounded-lg px-2 py-1.5 text-xs"
                  style={{
                    backgroundColor: "var(--sh-workbench-panel-subtle)",
                    border: "1px solid var(--sh-workbench-border)",
                    color: "var(--theme-text-primary)"
                  }}
                >
                  <option value="answer">简洁回答</option>
                  <option value="report">报告</option>
                  <option value="comparison">对比分析</option>
                  <option value="plan">行动计划</option>
                </select>
              </div>

              <div>
                <label
                  htmlFor="pe-source"
                  className="mb-1 block text-[11px] font-medium uppercase"
                  style={{ color: "var(--theme-text-muted)" }}
                >
                  来源偏好
                </label>
                <select
                  id="pe-source"
                  value={sourcePreference}
                  onChange={(e) => setSourcePreference(e.target.value as SourcePreference)}
                  className="w-full rounded-lg px-2 py-1.5 text-xs"
                  style={{
                    backgroundColor: "var(--sh-workbench-panel-subtle)",
                    border: "1px solid var(--sh-workbench-border)",
                    color: "var(--theme-text-primary)"
                  }}
                >
                  <option value="official-and-current">官方权威</option>
                  <option value="broad-web">广泛网络</option>
                  <option value="uploaded-files">上传文件</option>
                </select>
              </div>

              <div>
                <label
                  htmlFor="pe-depth"
                  className="mb-1 block text-[11px] font-medium uppercase"
                  style={{ color: "var(--theme-text-muted)" }}
                >
                  分析深度
                </label>
                <select
                  id="pe-depth"
                  value={depth}
                  onChange={(e) => setDepth(e.target.value as Depth)}
                  className="w-full rounded-lg px-2 py-1.5 text-xs"
                  style={{
                    backgroundColor: "var(--sh-workbench-panel-subtle)",
                    border: "1px solid var(--sh-workbench-border)",
                    color: "var(--theme-text-primary)"
                  }}
                >
                  <option value="quick">快速</option>
                  <option value="standard">标准</option>
                  <option value="deep">深度</option>
                </select>
              </div>
            </div>

            <div>
              <label
                className="mb-1 block text-[11px] font-medium uppercase"
                style={{ color: "var(--theme-text-muted)" }}
              >
                增强后预览
              </label>
              <pre
                className="max-h-40 overflow-y-auto rounded-lg px-3 py-2 text-[11px] leading-relaxed whitespace-pre-wrap"
                style={{
                  backgroundColor: "var(--sh-workbench-panel-subtle)",
                  border: "1px solid var(--sh-workbench-border)",
                  color: "var(--theme-text-secondary)"
                }}
              >
                {enhanced}
              </pre>
            </div>
          </div>

          <div className="mt-4 flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg px-3 py-1.5 text-xs font-medium transition-colors hover:opacity-80"
              style={{
                backgroundColor: "var(--sh-workbench-panel-subtle)",
                border: "1px solid var(--sh-workbench-border)",
                color: "var(--theme-text-secondary)"
              }}
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => { onApply(enhanced); onClose(); }}
              className="rounded-lg px-3 py-1.5 text-xs font-medium transition-colors hover:opacity-80"
              style={{
                backgroundColor: "var(--sh-workbench-accent-soft)",
                border: "1px solid var(--sh-workbench-accent)",
                color: "var(--sh-workbench-accent)"
              }}
            >
              应用到输入框
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
