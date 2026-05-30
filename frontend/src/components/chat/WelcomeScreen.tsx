import * as React from "react";
import { ArrowUpRight, BookOpen, Check, Lightbulb } from "lucide-react";

import { ChatInput, type ChatInputDraft } from "@/components/chat/ChatInput";
import { SeahorseLogo } from "@/components/common/SeahorseLogo";
import { cn } from "@/lib/utils";
import { listSampleQuestions } from "@/services/sampleQuestionService";
import { useChatStore } from "@/stores/chatStore";

type PromptPreset = {
  id?: string;
  title: string;
  description: string;
  prompt: string;
  icon: React.ComponentType<{ className?: string }>;
};

const PRESET_ICONS = [BookOpen, Check, Lightbulb];

const DEFAULT_PRESETS: PromptPreset[] = [
  {
    title: "总结提炼",
    description: "提取关键要点",
    prompt: "总结以下内容并列出关键要点：",
    icon: BookOpen
  },
  {
    title: "制定计划",
    description: "将目标拆解为步骤",
    prompt: "将这个目标拆解为具体步骤、优先级和里程碑：",
    icon: Check
  },
  {
    title: "探索对比",
    description: "对比多种方案",
    prompt: "针对这个主题给出多种方案并对比优劣：",
    icon: Lightbulb
  }
];

export function WelcomeScreen() {
  const [promptPresets, setPromptPresets] = React.useState<PromptPreset[]>(DEFAULT_PRESETS);
  const [draft, setDraft] = React.useState<ChatInputDraft | null>(null);
  const isStreaming = useChatStore((state) => state.isStreaming);

  React.useEffect(() => {
    let active = true;
    listSampleQuestions()
      .then((data) => {
        if (!active || !data?.length) return;
        const mapped = data
          .filter((item) => item.question?.trim())
          .slice(0, 3)
          .map((item, index) => {
            const prompt = item.question.trim();
            return {
              id: item.id,
              title: item.title?.trim() || (prompt.length > 18 ? `${prompt.slice(0, 18)}...` : prompt),
              description: item.description?.trim() || "从这个提示开始",
              prompt,
              icon: PRESET_ICONS[index % PRESET_ICONS.length]
            };
          });
        if (mapped.length > 0) setPromptPresets(mapped);
      })
      .catch(() => null);
    return () => {
      active = false;
    };
  }, []);

  const applyPreset = (prompt: string) => {
    if (isStreaming) return;
    setDraft({ id: `${Date.now()}`, text: prompt });
  };

  return (
    <div className="relative flex min-h-full items-center justify-center overflow-hidden px-4 py-12 sm:px-6">
      <div className="relative w-full max-w-[860px]">
        <div className="mb-10 flex flex-col items-center text-center opacity-0 animate-fade-up" style={{ animationFillMode: "both" }}>
          <div className="relative mb-5">
            {/* 内层光圈 — 静态 */}
            <div
              aria-hidden="true"
              className="absolute rounded-full pointer-events-none"
              style={{
                width: 220,
                height: 220,
                top: -10,
                left: -10,
                border: "1px solid var(--theme-accent-alpha-20)",
                backgroundImage: "conic-gradient(from 0deg, transparent 75%, var(--theme-accent-alpha-40) 100%)",
                borderRadius: "50%"
              }}
            />
            {/* 外层光圈 — 旋转 */}
            <div
              aria-hidden="true"
              className="absolute rounded-full pointer-events-none"
              style={{
                width: 260,
                height: 260,
                top: -30,
                left: -30,
                border: "1px dashed var(--theme-accent-alpha-20)",
                borderRadius: "50%",
                animation: "spin-slow 12s linear infinite"
              }}
            />
            <div
              aria-hidden="true"
              className="absolute rounded-full pointer-events-none"
              style={{
                width: 280,
                height: 280,
                top: -40,
                left: -40,
                background: "radial-gradient(circle, var(--theme-accent-alpha-10) 0%, transparent 70%)",
                filter: "blur(20px)"
              }}
            />
            <div className="relative animate-float">
              <SeahorseLogo size={200} />
            </div>
          </div>
          <h2
            className="font-display text-5xl font-bold tracking-normal glow-text"
            style={{ color: "var(--theme-text-primary)" }}
          >
            SEAHORSE AI
          </h2>
          <p className="mt-3 max-w-xl text-sm leading-6" style={{ color: "var(--theme-text-secondary)" }}>
            研究、写作、分析、文件问答和个人记忆的智能助手。
          </p>
        </div>

        <div className="opacity-0 animate-fade-up" style={{ animationDelay: "80ms", animationFillMode: "both" }}>
          <ChatInput draft={draft} />
        </div>

        <div className="mt-9 opacity-0 animate-fade-up" style={{ animationDelay: "160ms", animationFillMode: "both" }}>
          <div className="flex items-center justify-center gap-3 text-xs font-mono tracking-normal" style={{ color: "var(--theme-text-muted)" }}>
            <span className="h-px w-8" style={{ backgroundColor: "var(--theme-accent-alpha-30)" }} />
            快速提问
            <span className="h-px w-8" style={{ backgroundColor: "var(--theme-accent-alpha-30)" }} />
          </div>
          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {promptPresets.map((preset, index) => {
              const Icon = preset.icon;
              return (
                <button
                  key={preset.id ?? preset.title}
                  type="button"
                  onClick={() => applyPreset(preset.prompt)}
                  disabled={isStreaming}
                  className={cn(
                    "group relative overflow-hidden rounded-2xl p-4 text-left transition-all duration-300 hover:-translate-y-1",
                    isStreaming && "cursor-not-allowed opacity-60"
                  )}
                  style={{
                    background: "var(--theme-glass-bg)",
                    backdropFilter: "blur(16px)",
                    border: "1px solid var(--theme-glass-border)",
                    boxShadow: "0 0 15px var(--theme-accent-alpha-10), inset 0 1px 0 var(--theme-accent-alpha-20)"
                  }}
                >
                  <span
                    className="absolute right-3 top-3 text-xs font-mono opacity-20"
                    style={{ color: "var(--theme-accent)" }}
                  >
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <div className="flex items-center gap-3">
                    <span
                      className="flex h-10 w-10 items-center justify-center rounded-xl transition-colors duration-200"
                      style={{
                        backgroundColor: "var(--theme-accent-alpha-10)",
                        color: "var(--theme-accent)",
                        border: "1px solid var(--theme-accent-alpha-20)"
                      }}
                    >
                      <Icon className="h-4 w-4" />
                    </span>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold" style={{ color: "var(--theme-text-primary)" }}>
                        {preset.title}
                      </p>
                      <p className="truncate text-xs" style={{ color: "var(--theme-text-secondary)" }}>
                        {preset.description}
                      </p>
                    </div>
                  </div>
                  <div className="mt-3 flex items-center gap-2 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                    <span className="min-w-0 flex-1 truncate">{preset.prompt}</span>
                    <ArrowUpRight
                      className="h-3.5 w-3.5 shrink-0 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
                      style={{ color: "var(--theme-accent)" }}
                    />
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
