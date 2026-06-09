import * as React from "react";
import { InspectorEmptyState } from "@/components/chat/workbench/InspectorEmptyState";
import type { AgentSkillRuntimeView } from "@/types";

interface SkillInspectorTabProps {
  skills: AgentSkillRuntimeView[];
}

function statusStyle(status?: string): { bg: string; text: string } {
  const normalized = (status ?? "").toUpperCase();
  if (normalized === "LOADED" || normalized === "SELECTED") {
    return { bg: "rgba(34,197,94,0.15)", text: "rgb(34,197,94)" };
  }
  if (normalized === "METADATA_ONLY") {
    return { bg: "rgba(59,130,246,0.15)", text: "rgb(59,130,246)" };
  }
  if (normalized === "SKIPPED" || normalized === "REJECTED") {
    return { bg: "rgba(245,158,11,0.16)", text: "rgb(217,119,6)" };
  }
  return { bg: "var(--sh-workbench-panel-subtle)", text: "var(--theme-text-muted)" };
}

export function SkillInspectorTab({ skills }: SkillInspectorTabProps) {
  if (skills.length === 0) return <InspectorEmptyState />;

  return (
    <div className="space-y-2 p-3">
      {skills.map((skill) => {
        const style = statusStyle(skill.status);
        return (
          <div
            key={skill.id}
            className="rounded-lg p-3"
            style={{
              backgroundColor: "var(--sh-workbench-panel-subtle)",
              border: "1px solid var(--sh-workbench-border)"
            }}
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-xs font-semibold" style={{ color: "var(--theme-text-primary)" }}>
                {skill.name}
              </span>
              <span
                className="rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                style={{ backgroundColor: style.bg, color: style.text }}
              >
                {skill.status}
              </span>
              {skill.injectMode ? (
                <span className="rounded-full px-1.5 py-0.5 text-[10px]" style={{ color: "var(--theme-text-muted)" }}>
                  {skill.injectMode}
                </span>
              ) : null}
              {skill.category ? (
                <span className="rounded-full px-1.5 py-0.5 text-[10px]" style={{ color: "var(--theme-text-muted)" }}>
                  {skill.category}
                </span>
              ) : null}
            </div>

            {skill.description ? (
              <p className="mt-2 text-xs" style={{ color: "var(--theme-text-secondary)" }}>
                {skill.description}
              </p>
            ) : null}

            {skill.resourcePath ? (
              <p className="mt-2 font-mono text-[10px]" style={{ color: "var(--theme-text-muted)" }}>
                {skill.resourcePath}
              </p>
            ) : null}

            {skill.allowedTools && skill.allowedTools.length > 0 ? (
              <div className="mt-2 flex flex-wrap gap-1">
                {skill.allowedTools.map((toolId) => (
                  <span
                    key={toolId}
                    className="rounded px-1.5 py-0.5 font-mono text-[10px]"
                    style={{
                      backgroundColor: "var(--sh-workbench-panel)",
                      border: "1px solid var(--sh-workbench-border)",
                      color: "var(--theme-text-secondary)"
                    }}
                  >
                    {toolId}
                  </span>
                ))}
              </div>
            ) : null}

            {skill.reason ? (
              <p className="mt-2 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                {skill.reason}
              </p>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
