import { ShieldCheck, ShieldOff, Loader2 } from "lucide-react";

interface MemoryPrivacyBannerProps {
  privacyMode: boolean;
  loading: boolean;
  onToggle: () => void;
}

export function MemoryPrivacyBanner({ privacyMode, loading, onToggle }: MemoryPrivacyBannerProps) {
  return (
    <div
      className="flex items-center justify-between rounded-2xl p-4"
      style={{
        backgroundColor: "var(--theme-bg-elevated)",
        border: "1px solid var(--theme-glass-border)"
      }}
    >
      <div className="flex items-center gap-3">
        {privacyMode ? (
          <ShieldCheck className="h-5 w-5 shrink-0" style={{ color: "var(--theme-accent)" }} />
        ) : (
          <ShieldOff className="h-5 w-5 shrink-0" style={{ color: "var(--theme-text-muted)" }} />
        )}
        <div>
          <p className="text-sm font-semibold" style={{ color: "var(--theme-text-primary)" }}>
            {privacyMode ? "隐私模式已开启" : "长期记忆可用于新对话"}
          </p>
          <p className="mt-0.5 text-xs" style={{ color: "var(--theme-text-muted)" }}>
            {privacyMode
              ? "新对话不会读取或写入长期记忆"
              : "助手可在新对话中使用符合条件的长期记忆"}
          </p>
        </div>
      </div>
      <button
        type="button"
        onClick={onToggle}
        disabled={loading}
        className="flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60"
        style={{
          backgroundColor: privacyMode ? "var(--theme-accent-alpha-10)" : "var(--theme-bg-elevated)",
          border: "1px solid var(--theme-accent-alpha-20)",
          color: privacyMode ? "var(--theme-accent)" : "var(--theme-text-secondary)"
        }}
      >
        {loading ? (
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
        ) : privacyMode ? (
          <ShieldOff className="h-3.5 w-3.5" />
        ) : (
          <ShieldCheck className="h-3.5 w-3.5" />
        )}
        {privacyMode ? "关闭隐私模式" : "开启隐私模式"}
      </button>
    </div>
  );
}
