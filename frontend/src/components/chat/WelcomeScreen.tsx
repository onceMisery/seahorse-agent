import * as React from "react";
import { ArrowUpRight, BookOpen, Brain, Check, Lightbulb, Send, Square } from "lucide-react";

import { cn } from "@/lib/utils";
import { listSampleQuestions } from "@/services/sampleQuestionService";
import { useChatStore } from "@/stores/chatStore";
import { SeahorseLogo } from "@/components/common/SeahorseLogo";

type PromptPreset = {
  id?: string;
  title: string;
  description: string;
  prompt: string;
  icon: React.ComponentType<{ className?: string }>;
};

const PRESET_ICONS = [BookOpen, Check, Lightbulb];

const DEFAULT_PRESETS: PromptPreset[] = [
  { title: "内容总结", description: "提炼 3-5 条关键信息与行动点", prompt: "请帮我总结以下内容，并列出3-5条要点：", icon: BookOpen },
  { title: "任务拆解", description: "把目标拆成可执行步骤与优先级", prompt: "请把下面需求拆解为步骤，并给出优先级和里程碑：", icon: Check },
  { title: "灵感扩展", description: "给出多个方案并比较优缺点", prompt: "围绕以下主题给出5-8个方案，并注明优缺点：", icon: Lightbulb },
];

const TYPING_TEXTS = ["RAG 智能问答助手", "知识检索增强系统", "AI 对话引擎 v2.0"];

function useTypingEffect(texts: string[], speed = 80, pause = 1800) {
  const [display, setDisplay] = React.useState("");
  const [textIdx, setTextIdx] = React.useState(0);
  const [charIdx, setCharIdx] = React.useState(0);
  const [deleting, setDeleting] = React.useState(false);

  React.useEffect(() => {
    const current = texts[textIdx];
    const delay = deleting ? speed / 2 : charIdx === current.length ? pause : speed;
    const t = setTimeout(() => {
      if (!deleting && charIdx < current.length) {
        setDisplay(current.slice(0, charIdx + 1));
        setCharIdx((c) => c + 1);
      } else if (!deleting && charIdx === current.length) {
        setDeleting(true);
      } else if (deleting && charIdx > 0) {
        setDisplay(current.slice(0, charIdx - 1));
        setCharIdx((c) => c - 1);
      } else {
        setDeleting(false);
        setTextIdx((i) => (i + 1) % texts.length);
      }
    }, delay);
    return () => clearTimeout(t);
  }, [charIdx, deleting, textIdx, texts, speed, pause]);

  return display;
}

export function WelcomeScreen() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const [promptPresets, setPromptPresets] = React.useState<PromptPreset[]>(DEFAULT_PRESETS);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const { sendMessage, isStreaming, cancelGeneration, deepThinkingEnabled, setDeepThinkingEnabled } = useChatStore();
  const typingText = useTypingEffect(TYPING_TEXTS);

  const focusInput = React.useCallback(() => {
    textareaRef.current?.focus({ preventScroll: true });
  }, []);

  const adjustHeight = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }, []);

  React.useEffect(() => { adjustHeight(); }, [value, adjustHeight]);

  React.useEffect(() => {
    let active = true;
    listSampleQuestions().catch(() => null).then((data) => {
      if (!active || !data?.length) return;
      const mapped = data.filter((i) => i.question?.trim()).slice(0, 3).map((item, idx) => {
        const q = item.question.trim();
        return { id: item.id, title: item.title?.trim() || (q.length > 12 ? `${q.slice(0, 12)}...` : q), description: item.description?.trim() || "直接点选即可开始对话", prompt: q, icon: PRESET_ICONS[idx % PRESET_ICONS.length] };
      });
      if (mapped.length > 0) setPromptPresets(mapped);
    });
    return () => { active = false; };
  }, []);

  const applyPreset = React.useCallback((prompt: string) => {
    if (isStreaming) return;
    setValue(prompt);
    focusInput();
  }, [isStreaming, focusInput]);

  const handleSubmit = async () => {
    if (isStreaming) { cancelGeneration(); focusInput(); return; }
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
      <div className="relative w-full max-w-[860px]">

        {/* Hero */}
        <div className="flex flex-col items-center text-center mb-12 opacity-0 animate-fade-up" style={{ animationFillMode: "both" }}>
          <div className="relative mb-6">
            {/* 光环 */}
            <div aria-hidden="true" className="absolute rounded-full pointer-events-none"
              style={{
                width: 220, height: 220, top: -10, left: -10,
                border: "1px solid rgba(6,182,212,0.2)",
                animation: "spin-slow 15s linear infinite",
                backgroundImage: "conic-gradient(from 0deg, transparent 75%, rgba(6,182,212,0.4) 100%)",
                borderRadius: "50%"
              }} />
            <div aria-hidden="true" className="absolute rounded-full pointer-events-none"
              style={{
                width: 250, height: 250, top: -25, left: -25,
                border: "1px dashed rgba(6,182,212,0.1)",
                animation: "spin-slow 25s linear infinite reverse",
                borderRadius: "50%"
              }} />
            {/* Logo 光晕 */}
            <div aria-hidden="true" className="absolute rounded-full pointer-events-none"
              style={{
                width: 280, height: 280, top: -40, left: -40,
                background: "radial-gradient(circle, rgba(6,182,212,0.15) 0%, transparent 70%)",
                filter: "blur(20px)",
              }} />
            <div className="relative animate-float">
              <SeahorseLogo size={200} />
            </div>
          </div>

          <h2 className="font-display text-5xl font-bold tracking-widest glow-text"
            style={{ color: "var(--theme-text-primary)", letterSpacing: "0.12em" }}>
            SEAHORSE AI
          </h2>

          {/* 打字副标题 */}
          <div className="mt-3 flex items-center gap-2 h-7">
            <div className="h-px w-6" style={{ background: "rgba(6,182,212,0.4)" }} />
            <p className="text-base font-mono tracking-widest" style={{ color: "var(--theme-accent)", minWidth: 220 }}>
              {typingText}
              <span className="inline-block w-0.5 h-4 ml-0.5 align-middle animate-pulse" style={{ background: "var(--theme-accent)" }} />
            </p>
            <div className="h-px w-6" style={{ background: "rgba(6,182,212,0.4)" }} />
          </div>

          {/* 状态栏 */}
          <div className="mt-5 flex items-center gap-4 text-xs font-mono" style={{ color: "var(--theme-text-muted)" }}>
            <span className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full animate-pulse" style={{ background: "#22c55e" }} />
              系统在线
            </span>
            <span style={{ color: "rgba(6,182,212,0.3)" }}>|</span>
            <span className="flex items-center gap-1.5">
              <Brain className="h-3 w-3" style={{ color: "var(--theme-accent)" }} />
              RAG 引擎就绪
            </span>
            <span style={{ color: "rgba(6,182,212,0.3)" }}>|</span>
            <span>v2.0.0</span>
          </div>
        </div>

        {/* 输入框 */}
        <div className="opacity-0 animate-fade-up" style={{ animationDelay: "80ms", animationFillMode: "both" }}>
          <div className="relative group">
            {/* 水下光晕效果 */}
            <div className="absolute -inset-2 rounded-3xl blur opacity-20 group-focus-within:opacity-50 transition-opacity"
              style={{ background: "radial-gradient(ellipse at center, rgba(6,182,212,0.25), transparent 70%)" }} />
            <div className={cn("relative glass rounded-3xl p-2 transition-all duration-300", isFocused && "shadow-lg")}
              style={{
                border: `1px solid ${isFocused ? "rgba(6,182,212,0.5)" : "rgba(6,182,212,0.15)"}`,
                boxShadow: isFocused ? "0 0 40px rgba(6,182,212,0.15), inset 0 1px 0 rgba(6,182,212,0.1)" : "0 0 20px rgba(6,182,212,0.05)",
              }}>
              {/* 顶部光线 */}
              <div aria-hidden="true" className="absolute top-0 left-12 right-12 h-px transition-opacity duration-300"
                style={{ background: "linear-gradient(90deg, transparent, rgba(6,182,212,0.6), transparent)", opacity: isFocused ? 1 : 0.3 }} />

              <div className="flex flex-col gap-2 p-3">
                <textarea
                  ref={textareaRef}
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                  placeholder={deepThinkingEnabled ? "输入需要深度分析的问题..." : "问我任何关于研究、分析或创意的问题..."}
                  className="max-h-40 min-h-[60px] w-full resize-none border-0 bg-transparent px-3 py-2 text-lg focus:outline-none font-mono"
                  style={{ color: "var(--theme-text-primary)" }}
                  rows={1}
                  onFocus={() => setIsFocused(true)}
                  onBlur={() => setIsFocused(false)}
                  onCompositionStart={() => { isComposingRef.current = true; }}
                  onCompositionEnd={() => { isComposingRef.current = false; }}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      const ne = e.nativeEvent as KeyboardEvent;
                      if (ne.isComposing || isComposingRef.current || ne.keyCode === 229) return;
                      e.preventDefault();
                      handleSubmit();
                    }
                  }}
                  aria-label="发送消息"
                />
                <div className="flex items-center justify-between mt-2 pt-4" style={{ borderTop: "1px solid rgba(6,182,212,0.08)" }}>
                  <div className="flex items-center gap-3">
                    <span className="text-xs font-mono tracking-widest" style={{ color: "var(--theme-text-muted)" }}>深度思考</span>
                    <button type="button" onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)} disabled={isStreaming}
                      aria-pressed={deepThinkingEnabled}
                      className={cn("w-12 h-6 rounded-full relative transition-colors duration-300", isStreaming && "cursor-not-allowed opacity-60")}
                      style={{ backgroundColor: deepThinkingEnabled ? "rgba(6,182,212,0.3)" : "var(--theme-bg-elevated)", border: "1px solid rgba(6,182,212,0.15)" }}>
                      <div className="absolute top-1 w-4 h-4 rounded-full transition-all duration-300"
                        style={{ left: deepThinkingEnabled ? "24px" : "4px", backgroundColor: "var(--theme-accent)", boxShadow: "0 0 10px rgba(6,182,212,0.5)" }} />
                    </button>
                  </div>
                  <button type="button" onClick={handleSubmit} disabled={!hasContent && !isStreaming}
                    aria-label={isStreaming ? "停止生成" : "发送消息"}
                    className={cn("w-14 h-14 rounded-2xl flex items-center justify-center transition-all shadow-lg group/send relative overflow-hidden",
                      !isStreaming && !hasContent && "cursor-not-allowed opacity-50")}
                    style={{ backgroundColor: isStreaming ? "var(--destructive)" : "var(--theme-accent)", color: isStreaming ? "#fff" : "var(--theme-bg-deep)", boxShadow: isStreaming ? undefined : "0 0 25px rgba(6,182,212,0.3)" }}>
                    {!isStreaming && (
                      <span aria-hidden="true" className="absolute inset-0 pointer-events-none"
                        style={{ animation: "btn-sweep 3s ease-in-out infinite", background: "linear-gradient(105deg, transparent 40%, rgba(255,255,255,0.2) 50%, transparent 60%" }} />
                    )}
                    {isStreaming ? <Square className="h-5 w-5" /> : <Send className="h-5 w-5 group-hover/send:rotate-12 transition-transform" />}
                  </button>
                </div>
              </div>
            </div>
          </div>

          {deepThinkingEnabled && (
            <p className="mt-3 text-xs font-mono" style={{ color: "var(--theme-accent)" }}>
              <span className="inline-flex items-center gap-1.5">
                <Lightbulb className="h-3.5 w-3.5" />
                深度思考已开启 — AI 将进行更深入的分析推理
              </span>
            </p>
          )}
          <p className="mt-3 text-center text-xs font-mono" style={{ color: "var(--theme-text-muted)" }}>
            <kbd className="rounded px-1.5 py-0.5" style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-secondary)" }}>Enter</kbd>
            {" "}发送
            <span className="px-1.5">·</span>
            <kbd className="rounded px-1.5 py-0.5" style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-secondary)" }}>Shift+Enter</kbd>
            {" "}换行
            {isStreaming && <span className="ml-2 animate-pulse-soft">生成中...</span>}
          </p>
        </div>

        {/* 快捷提问 */}
        <div className="mt-10 opacity-0 animate-fade-up" style={{ animationDelay: "160ms", animationFillMode: "both" }}>
          <div className="flex items-center justify-center gap-3 text-xs font-mono tracking-widest" style={{ color: "var(--theme-text-muted)" }}>
            <span className="h-px w-8" style={{ backgroundColor: "rgba(6,182,212,0.3)" }} />
            快捷提问
            <span className="h-px w-8" style={{ backgroundColor: "rgba(6,182,212,0.3)" }} />
          </div>
          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {promptPresets.map((preset, idx) => {
              const Icon = preset.icon;
              return (
                <button key={preset.id ?? preset.title} type="button" onClick={() => applyPreset(preset.prompt)}
                  disabled={isStreaming}
                  className={cn("group rounded-2xl p-4 text-left transition-all duration-300 hover:-translate-y-1 relative overflow-hidden", isStreaming && "cursor-not-allowed opacity-60")}
                  style={{
                    background: "rgba(10,36,62,0.5)",
                    backdropFilter: "blur(16px)",
                    border: "1px solid rgba(6,182,212,0.12)",
                    boxShadow: "0 0 15px rgba(6,182,212,0.05), inset 0 1px 0 rgba(6,182,212,0.08)",
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLElement).style.borderColor = "rgba(6,182,212,0.4)";
                    (e.currentTarget as HTMLElement).style.boxShadow = "0 0 25px rgba(6,182,212,0.12), inset 0 1px 0 rgba(6,182,212,0.15)";
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLElement).style.borderColor = "rgba(6,182,212,0.12)";
                    (e.currentTarget as HTMLElement).style.boxShadow = "0 0 15px rgba(6,182,212,0.05), inset 0 1px 0 rgba(6,182,212,0.08)";
                  }}>
                  {/* 卡片序号 */}
                  <span className="absolute top-3 right-3 text-xs font-mono opacity-20" style={{ color: "var(--theme-accent)" }}>
                    {String(idx + 1).padStart(2, "0")}
                  </span>
                  <div className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-xl transition-colors duration-200"
                      style={{ backgroundColor: "rgba(6,182,212,0.1)", color: "var(--theme-accent)", border: "1px solid rgba(6,182,212,0.15)" }}>
                      <Icon className="h-4 w-4" />
                    </span>
                    <div>
                      <p className="text-sm font-semibold" style={{ color: "var(--theme-text-primary)" }}>{preset.title}</p>
                      <p className="text-xs" style={{ color: "var(--theme-text-secondary)" }}>{preset.description}</p>
                    </div>
                  </div>
                  <div className="mt-3 flex items-center gap-2 text-xs font-mono" style={{ color: "var(--theme-text-muted)" }}>
                    <span className="min-w-0 flex-1 truncate">{preset.prompt}</span>
                    <ArrowUpRight className="h-3.5 w-3.5 shrink-0 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" style={{ color: "var(--theme-accent)" }} />
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
