import * as React from "react";
import { FileText } from "lucide-react";

export function InspectorEmptyState() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 px-6 py-12 text-center">
      <div
        className="flex h-12 w-12 items-center justify-center rounded-xl"
        style={{ backgroundColor: "var(--sh-workbench-accent-soft)" }}
      >
        <FileText className="h-6 w-6" style={{ color: "var(--sh-workbench-accent)" }} />
      </div>
      <div>
        <p className="text-sm font-medium" style={{ color: "var(--theme-text-primary)" }}>
          选择一条带有运行信息的回复
        </p>
        <p className="mt-1 text-xs" style={{ color: "var(--theme-text-muted)" }}>
          产物、来源、审批和成本会在这里集中查看。
        </p>
      </div>
    </div>
  );
}
