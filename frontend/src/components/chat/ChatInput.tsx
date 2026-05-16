import * as React from "react";
import { Brain, Lightbulb, Send, Square } from "lucide-react";

import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";

export function ChatInput() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const {
    sendMessage,
    isStreaming,
    cancelGeneration,
    deepThinkingEnabled,
    setDeepThinkingEnabled,
    inputFocusKey
  } = useChatStore();

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
    if (!inputFocusKey) return;
    focusInput();
  }, [inputFocusKey, focusInput]);

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
    <div className="space-y-4">
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
            <div className="relative">
              <Textarea
                ref={textareaRef}
                value={value}
                onChange={(event) => setValue(event.target.value)}
                placeholder={deepThinkingEnabled ? "输入需要深度分析的问题..." : "问我任何关于研究、分析或创意的问题..."}
                className="max-h-40 min-h-[44px] w-full resize-none border-0 bg-transparent px-2 pt-2 pb-2 pr-2 text-[15px] shadow-none focus-visible:ring-0"
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
                aria-label="聊天输入框"
              />
            </div>
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
        <p className="text-xs" style={{ color: "var(--theme-accent)" }}>
          <span className="inline-flex items-center gap-1.5">
            <Lightbulb className="h-3.5 w-3.5" />
            深度思考模式已开启，AI将进行更深入的分析推理
          </span>
        </p>
      ) : null}
      <p className="text-center text-xs" style={{ color: "var(--theme-text-muted)" }}>
        <kbd className="rounded px-1.5 py-0.5" style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-secondary)" }}>Enter</kbd> 发送
        <span className="px-1.5">·</span>
        <kbd className="rounded px-1.5 py-0.5" style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-secondary)" }}>
          Shift + Enter
        </kbd>{" "}
        换行
        {isStreaming ? <span className="ml-2 animate-pulse-soft">生成中...</span> : null}
      </p>
    </div>
  );
}
