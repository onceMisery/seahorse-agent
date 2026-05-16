import { Brain, Loader2 } from "lucide-react";

interface ThinkingIndicatorProps {
  content?: string;
  duration?: number;
}

export function ThinkingIndicator({ content, duration }: ThinkingIndicatorProps) {
  return (
    <div className="rounded-lg glass p-4" style={{ borderColor: "var(--theme-accent-alpha-30)" }}>
      <div className="flex items-center gap-2" style={{ color: "var(--theme-accent)" }}>
        <Loader2 className="h-4 w-4 animate-spin" />
        <span className="text-sm font-medium">正在深度思考...</span>
        {duration ? (
          <span className="text-xs px-2 py-0.5 rounded-full" style={{ backgroundColor: "var(--theme-accent-alpha-20)", color: "var(--theme-accent)" }}>
            {duration}秒
          </span>
        ) : null}
      </div>
      <div className="mt-3 flex items-start gap-2 text-sm" style={{ color: "var(--theme-accent-light)" }}>
        <Brain className="mt-0.5 h-4 w-4 shrink-0" style={{ color: "var(--theme-accent)" }} />
        <p className="whitespace-pre-wrap leading-relaxed">
          {content || ""}
          <span className="ml-1 inline-block h-4 w-1.5 animate-pulse align-middle" style={{ backgroundColor: "var(--theme-accent)" }} />
        </p>
      </div>
    </div>
  );
}
