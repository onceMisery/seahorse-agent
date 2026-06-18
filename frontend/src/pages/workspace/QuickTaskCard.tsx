import type { LucideIcon } from "lucide-react";

interface QuickTaskCardProps {
  icon: LucideIcon;
  title: string;
  description: string;
  gradient?: string;
  onClick: () => void;
}

export function QuickTaskCard({ icon: Icon, title, description, gradient, onClick }: QuickTaskCardProps) {
  return (
    <button
      type="button"
      className="group flex items-center gap-4 rounded-2xl p-4 text-left glass-hover transition-all hover:-translate-y-[2px]"
      style={{
        backgroundColor: "var(--theme-glass-bg)",
        border: "1px solid var(--theme-glass-border)"
      }}
      onClick={onClick}
    >
      <span
        className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl text-white shadow-lg transition-transform group-hover:scale-105"
        style={{
          background: gradient || "var(--theme-gradient)",
          boxShadow: "0 6px 14px var(--theme-accent-alpha-30)"
        }}
      >
        <Icon className="h-5 w-5" />
      </span>
      <span className="flex-1 min-w-0">
        <span className="block text-sm font-semibold" style={{ color: "var(--theme-text-primary)" }}>
          {title}
        </span>
        <span className="mt-0.5 block text-xs leading-relaxed" style={{ color: "var(--theme-text-muted)" }}>
          {description}
        </span>
      </span>
    </button>
  );
}
