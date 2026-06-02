import { cn } from "@/lib/utils";

interface ShimmerProps {
  className?: string;
  lines?: number;
}

export function Shimmer({ className, lines = 1 }: ShimmerProps) {
  if (lines <= 1) {
    return <span aria-hidden="true" className={cn("ai-shimmer block h-4 rounded-md", className)} />;
  }

  return (
    <div className={cn("space-y-2", className)} aria-hidden="true">
      {Array.from({ length: lines }).map((_, index) => (
        <span
          key={index}
          className="ai-shimmer block h-4 rounded-md"
          style={{ width: `${Math.max(44, 96 - index * 14)}%` }}
        />
      ))}
    </div>
  );
}

export function MessageSkeleton({ className }: { className?: string }) {
  return (
    <div className={cn("space-y-3 rounded-xl p-4", className)}>
      <div className="flex items-center gap-3">
        <Shimmer className="h-8 w-8 rounded-full" />
        <Shimmer className="h-3 w-32" />
      </div>
      <Shimmer lines={3} />
    </div>
  );
}

export function CodeSkeleton({ className }: { className?: string }) {
  return (
    <div className={cn("rounded-lg border p-3", className)} style={{ borderColor: "var(--theme-glass-border)" }}>
      <Shimmer className="mb-4 h-3 w-20" />
      <Shimmer lines={5} />
    </div>
  );
}
