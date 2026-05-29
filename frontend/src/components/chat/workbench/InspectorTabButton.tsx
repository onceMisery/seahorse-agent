import * as React from "react";
import * as Tabs from "@radix-ui/react-tabs";
import * as Tooltip from "@radix-ui/react-tooltip";

export interface InspectorTabButtonProps {
  value: string;
  label: string;
  count?: number;
  children: React.ReactNode;
}

export function InspectorTabButton({ value, label, count, children }: InspectorTabButtonProps) {
  return (
    <Tooltip.Provider delayDuration={400}>
      <Tooltip.Root>
        <Tooltip.Trigger asChild>
          <Tabs.Trigger
            value={value}
            className="group relative flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors"
            style={{
              color: "var(--theme-text-secondary)"
            }}
          >
            <span className="flex h-4 w-4 items-center justify-center">{children}</span>
            <span className="hidden sm:inline">{label}</span>
            {count !== undefined && count > 0 ? (
              <span
                className="flex h-4 min-w-[16px] items-center justify-center rounded-full px-1 text-[10px] font-semibold"
                style={{
                  backgroundColor: "var(--sh-workbench-accent-soft)",
                  color: "var(--sh-workbench-accent)"
                }}
              >
                {count}
              </span>
            ) : null}
          </Tabs.Trigger>
        </Tooltip.Trigger>
        <Tooltip.Portal>
          <Tooltip.Content
            side="bottom"
            className="rounded-md px-2 py-1 text-xs shadow-md"
            style={{
              backgroundColor: "var(--sh-workbench-panel)",
              color: "var(--theme-text-primary)",
              border: "1px solid var(--sh-workbench-border)"
            }}
          >
            {label}
            {count !== undefined && count > 0 ? ` (${count})` : ""}
            <Tooltip.Arrow style={{ fill: "var(--sh-workbench-panel)" }} />
          </Tooltip.Content>
        </Tooltip.Portal>
      </Tooltip.Root>
    </Tooltip.Provider>
  );
}
