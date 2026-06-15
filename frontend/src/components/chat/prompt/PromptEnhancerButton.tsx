import { Lightbulb } from "lucide-react";

interface PromptEnhancerButtonProps {
  disabled?: boolean;
  onClick: () => void;
}

export function PromptEnhancerButton({ disabled, onClick }: PromptEnhancerButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label="优化提示词"
      title="整理问题"
      className="flex h-9 w-9 items-center justify-center rounded-xl transition-colors disabled:cursor-not-allowed disabled:opacity-60 hover:opacity-80"
      style={{
        backgroundColor: "var(--theme-bg-elevated)",
        border: "1px solid var(--theme-accent-alpha-10)",
        color: "var(--theme-accent)"
      }}
    >
      <Lightbulb className="h-4 w-4" />
    </button>
  );
}
