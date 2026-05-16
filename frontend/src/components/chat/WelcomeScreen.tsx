import * as React from "react";
import { ArrowUpRight, BookOpen, Brain, Check, Lightbulb, Send, Square } from "lucide-react";

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
    title: "内容总结",
    description: "提炼 3-5 条关键信息与行动点",
    prompt: "请帮我总结以下内容，并列出3-5条要点：",
    icon: BookOpen
  },
  {
    title: "任务拆解",
    description: "把目标拆成可执行步骤与优先级",
    prompt: "请把下面需求拆解为步骤，并给出优先级和里程碑：",
    icon: Check
  },
  {
    title: "灵感扩展",
    description: "给出多个方案并比较优缺点",
    prompt: "围绕以下主题给出5-8个方案，并注明优缺点：",
    icon: Lightbulb
  }
];

export function WelcomeScreen() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const [promptPresets, setPromptPresets] = React.useState<PromptPreset[]>(DEFAULT_PRESETS);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const { sendMessage, isStreaming, cancelGeneration, deepThinkingEnabled, setDeepThinkingEnabled } =
    useChatStore();

  const focusInput = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.focus({ preventScroll: true });
  }, []);

  const adjustHeight = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    const next = Math.min(el.scrollHeight, 160);
    el.style.height = `${next}px`;
  }, []);

  React.useEffect(() => {
    adjustHeight();
  }, [value, adjustHeight]);

  React.useEffect(() => {
    let active = true;

    const loadPresets = async () => {
      const data = await listSampleQuestions().catch(() => null);
      if (!active || !data || data.length === 0) {
        return;
      }
      const mapped = data
        .filter((item) => item.question && item.question.trim())
        .slice(0, 3)
        .map((item, index) => {
          const question = item.question.trim();
          const title =
            item.title?.trim() ||
            (question.length > 12 ? `${question.slice(0, 12)}...` : question) ||
            `推荐问法 ${index + 1}`;
          const description = item.description?.trim() || "直接点选即可开始对话";
          return {
            id: item.id,
            title,
            description,
            prompt: question,
            icon: PRESET_ICONS[index % PRESET_ICONS.length]
          };
        });
      if (mapped.length > 0) {
        setPromptPresets(mapped);
      }
    };

    loadPresets();
    return () => {
      active = false;
    };
  }, []);

  const applyPreset = React.useCallback(
    (prompt: string) => {
      if (isStreaming) return;
      setValue(prompt);
      focusInput();
    },
    [isStreaming, focusInput]
  );

  const handleSubmit = async () => {
    if (isStreaming) {
      cancelGeneration();
      focusInput();
      return;
    }
    if (!value.trim()) return;
    const next = value;
    setValue("");
    focusInput();
    await sendMessage(next);
    focusInput();
  };

  const hasContent = value.trim().length > 0;

  return (
    <div className="relative flex min-h-full items-center justify-center overflow-hidden px-4 py-16 sm:px-6">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-grid-pattern opacity-20 [background-size:40px_40px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-32 right-[-40px] h-72 w-72 rounded-full blur-3xl animate-float"
        style={{ background: "radial-gradient(var(--theme-accent-alpha-20), transparent 70%)" }}
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-36 left-[-80px] h-80 w-80 rounded-full blur-3xl animate-float"
        style={{ background: "radial-gradient(var(--theme-accent-alpha-10), transparent 70%)", animationDelay: "3s" }}
      />

      <div className="relative w-full max-w-[860px]">
        {/* Floating Seahorse Logo */}
        <div
          className="flex flex-col items-center text-center mb-16 opacity-0 animate-fade-up"
          style={{ animationFillMode: "both" }}
        >
          <div className="relative mb-10">
            <div className="absolute inset-0 rounded-full blur-3xl scale-110" style={{ backgroundColor: "var(--theme-accent-alpha-20)" }} />
            <div className="relative animate-float">
              <img
                src="/seahorse-logo.png"
                alt="Seahorse AI"
                className="w-[200px] h-[200px] object-contain drop-shadow-[0_0_30px_rgba(6,182,212,0.4)]"
              />
            </div>
          </div>
          <h2 className="font-display text-5xl font-bold tracking-tight glow-text" style={{ color: "var(--theme-text-primary)" }}>
            Seahorse AI
          </h2>
          <p className="mt-3 text-xl font-medium tracking-wide" style={{ color: "var(--theme-accent)" }}>
            RAG 智能问答助手
          </p>
        </div>

        <div
          className="mt-10 opacity-0 animate-fade-up"
          style={{ animationDelay: "80ms", animationFillMode: "both" }}
        >
          <div className="relative group">
            <div
              className="absolute -inset-1 rounded-3xl blur opacity-30 group-focus-within:opacity-60 transition-opacity"
              style={{ background: "linear-gradient(to right, var(--theme-accent-alpha-20), var(--theme-accent-alpha-10))" }}
            />
            <div
              className={cn(
                "relative glass rounded-3xl glow-border p-2 transition-all duration-200",
                isFocused && "shadow-lg"
              )}
              style={isFocused ? { boxShadow: "var(--theme-shadow-glow)" } : undefined}
            >
              <div className="flex flex-col gap-2 p-3">
                <textarea
                  ref={textareaRef}
                  value={value}
                  onChange={(event) => setValue(event.target.value)}
                  placeholder={deepThinkingEnabled ? "输入需要深度分析的问题..." : "问我任何关于研究、分析或创意的问题..."}
                  className="max-h-40 min-h-[60px] w-full resize-none border-0 bg-transparent px-3 py-2 text-lg focus:outline-none"
                  style={{ color: "var(--theme-text-primary)" }}
                  rows={1}
                  onFocus={() => setIsFocused(true)}
                  onBlur={() => setIsFocused(false)}
                  onCompositionStart={() => {
                    isComposingRef.current = true;
                  }}
                  onCompositionEnd={() => {
                    isComposingRef.current = false;
                  }}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" && !event.shiftKey) {
                      const nativeEvent = event.nativeEvent as KeyboardEvent;
                      if (nativeEvent.isComposing || isComposingRef.current || nativeEvent.keyCode === 229) {
                        return;
                      }
                      event.preventDefault();
                      handleSubmit();
                    }
                  }}
                  aria-label="发送消息"
                />
                <div className="flex items-center justify-between mt-2 pt-4" style={{ borderTop: "1px solid var(--theme-accent-alpha-10)" }}>
                  <div className="flex items-center gap-6">
                    <div className="flex items-center gap-3">
                      <span className="text-xs font-bold uppercase tracking-widest" style={{ color: "var(--theme-text-muted)" }}>深度思考</span>
                      <button
                        type="button"
                        onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                        disabled={isStreaming}
                        aria-pressed={deepThinkingEnabled}
                        className={cn(
                          "w-12 h-6 rounded-full relative transition-colors duration-300",
                          isStreaming && "cursor-not-allowed opacity-60"
                        )}
                        style={{
                          backgroundColor: deepThinkingEnabled ? "var(--theme-accent-alpha-40)" : "var(--theme-bg-elevated)",
                          border: "1px solid var(--theme-accent-alpha-20)"
                        }}
                      >
                        <div
                          className="absolute top-1 w-4 h-4 rounded-full transition-all duration-300"
                          style={{
                            left: deepThinkingEnabled ? "24px" : "4px",
                            backgroundColor: "var(--theme-accent)",
                            boxShadow: "0 0 10px var(--theme-accent-alpha-60)"
                          }}
                        />
                      </button>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={handleSubmit}
                    disabled={!hasContent && !isStreaming}
                    aria-label={isStreaming ? "停止生成" : "发送消息"}
                    className={cn(
                      "w-14 h-14 rounded-2xl flex items-center justify-center transition-all shadow-lg group/send",
                      isStreaming
                        ? ""
                        : hasContent
                          ? ""
                          : "cursor-not-allowed opacity-50"
                    )}
                    style={{
                      backgroundColor: isStreaming ? "var(--destructive)" : "var(--theme-accent)",
                      color: isStreaming ? "#fff" : "var(--theme-bg-deep)",
                      boxShadow: isStreaming ? undefined : "0 0 20px var(--theme-accent-alpha-30)"
                    }}
                  >
                    {isStreaming ? <Square className="h-5 w-5" /> : <Send className="h-5 w-5 group-hover/send:rotate-12 transition-transform" />}
                  </button>
                </div>
              </div>
            </div>
          </div>
          {deepThinkingEnabled ? (
            <p className="mt-3 text-xs" style={{ color: "var(--theme-accent)" }}>
              <span className="inline-flex items-center gap-1.5">
                <Lightbulb className="h-3.5 w-3.5" />
                深度思考模式已开启，AI将进行更深入的分析推理
              </span>
            </p>
          ) : null}
          <p className="mt-3 text-center text-xs" style={{ color: "var(--theme-text-muted)" }}>
            <kbd className="rounded px-1.5 py-0.5" style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-secondary)" }}>
              Enter
            </kbd>{" "}
            发送
            <span className="px-1.5">·</span>
            <kbd className="rounded px-1.5 py-0.5" style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-secondary)" }}>
              Shift + Enter
            </kbd>{" "}
            换行
            {isStreaming ? <span className="ml-2 animate-pulse-soft">生成中...</span> : null}
          </p>
        </div>

        <div
          className="mt-10 opacity-0 animate-fade-up"
          style={{ animationDelay: "160ms", animationFillMode: "both" }}
        >
          <div className="flex items-center justify-center gap-2 text-xs uppercase tracking-[0.24em]" style={{ color: "var(--theme-text-muted)" }}>
            <span className="h-px w-8" style={{ backgroundColor: "var(--theme-glass-border)" }} />
            试试这些开场
            <span className="h-px w-8" style={{ backgroundColor: "var(--theme-glass-border)" }} />
          </div>
          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {promptPresets.map((preset) => {
              const Icon = preset.icon;
              return (
                <button
                  key={preset.id ?? preset.title}
                  type="button"
                  onClick={() => applyPreset(preset.prompt)}
                  disabled={isStreaming}
                  className={cn(
                    "group rounded-2xl glass glass-hover p-4 text-left transition-all duration-200 hover:-translate-y-0.5",
                    isStreaming && "cursor-not-allowed opacity-60"
                  )}
                >
                  <div className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-full" style={{ backgroundColor: "var(--theme-accent-alpha-10)", color: "var(--theme-accent)" }}>
                      <Icon className="h-4 w-4" />
                    </span>
                    <div>
                      <p className="text-sm font-semibold" style={{ color: "var(--theme-text-primary)" }}>{preset.title}</p>
                      <p className="text-xs" style={{ color: "var(--theme-text-secondary)" }}>{preset.description}</p>
                    </div>
                  </div>
                  <div className="mt-3 flex items-center gap-2 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                    <span className="min-w-0 flex-1 truncate">推荐问法：{preset.prompt}</span>
                    <ArrowUpRight className="h-3.5 w-3.5 transition-colors" style={{ color: "var(--theme-text-muted)" }} />
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
