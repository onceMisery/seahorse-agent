import * as React from "react";
import type { A2UILiteAction, A2UILiteNode } from "@/components/a2ui-lite/a2uiTypes";

interface RendererProps {
  node: A2UILiteNode;
  onAction: (action: A2UILiteAction) => void;
}

function MetricComponent({ node }: { node: A2UILiteNode }) {
  const { label, value, delta } = node.props as {
    label?: string;
    value?: string | number;
    delta?: string | number;
  };
  return (
    <div
      className="rounded-lg p-3"
      style={{ border: "1px solid var(--sh-workbench-border)" }}
    >
      {label && (
        <p className="text-[10px] font-semibold uppercase" style={{ color: "var(--theme-text-muted)" }}>
          {label}
        </p>
      )}
      <p className="mt-1 text-lg font-bold" style={{ color: "var(--theme-text-primary)" }}>
        {String(value ?? "")}
      </p>
      {delta != null && (
        <p className="text-[11px]" style={{ color: "var(--theme-text-muted)" }}>
          {String(delta)}
        </p>
      )}
    </div>
  );
}

function TableComponent({ node }: { node: A2UILiteNode }) {
  const { columns, rows } = node.props as {
    columns?: string[];
    rows?: string[][];
  };
  if (!columns || !rows) return null;
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs">
        <thead>
          <tr style={{ borderBottom: "1px solid var(--sh-workbench-border)" }}>
            {columns.map((col, i) => (
              <th
                key={i}
                className="px-2 py-1.5 text-left font-semibold"
                style={{ color: "var(--theme-text-muted)" }}
              >
                {col}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, ri) => (
            <tr
              key={ri}
              style={{ borderBottom: "1px solid var(--sh-workbench-border)" }}
            >
              {row.map((cell, ci) => (
                <td
                  key={ci}
                  className="px-2 py-1.5"
                  style={{ color: "var(--theme-text-primary)" }}
                >
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function SourceGridComponent({ node }: { node: A2UILiteNode }) {
  const { title, trust, snippet } = node.props as {
    title?: string;
    trust?: string;
    snippet?: string;
  };
  return (
    <div
      className="rounded-lg p-3 space-y-1"
      style={{ border: "1px solid var(--sh-workbench-border)" }}
    >
      {title && (
        <p className="text-xs font-medium" style={{ color: "var(--theme-text-primary)" }}>
          {title}
        </p>
      )}
      {trust && (
        <span
          className="inline-block rounded-full px-1.5 py-0.5 text-[10px]"
          style={{
            backgroundColor: "var(--sh-workbench-accent-soft)",
            color: "var(--sh-workbench-accent)"
          }}
        >
          {trust}
        </span>
      )}
      {snippet && (
        <p className="text-[11px] leading-relaxed" style={{ color: "var(--theme-text-muted)" }}>
          {snippet}
        </p>
      )}
    </div>
  );
}

function CalloutComponent({ node }: { node: A2UILiteNode }) {
  const { title, body, tone } = node.props as {
    title?: string;
    body?: string;
    tone?: string;
  };
  const toneColor =
    tone === "warning"
      ? "rgba(234,179,8,0.15)"
      : tone === "error"
        ? "rgba(239,68,68,0.15)"
        : tone === "success"
          ? "rgba(34,197,94,0.15)"
          : "var(--sh-workbench-accent-soft)";

  return (
    <div
      className="rounded-lg p-3 space-y-1"
      style={{
        backgroundColor: toneColor,
        border: "1px solid var(--sh-workbench-border)"
      }}
    >
      {title && (
        <p className="text-xs font-semibold" style={{ color: "var(--theme-text-primary)" }}>
          {title}
        </p>
      )}
      {body && (
        <p className="text-xs leading-relaxed" style={{ color: "var(--theme-text-secondary)" }}>
          {body}
        </p>
      )}
    </div>
  );
}

const WHITELISTED_ACTION_TYPES = new Set([
  "open_artifact",
  "select_source",
  "copy_text",
  "set_prompt_draft"
]);

function ActionRowComponent({
  node,
  onAction
}: {
  node: A2UILiteNode;
  onAction: (action: A2UILiteAction) => void;
}) {
  const { actions } = node.props as {
    actions?: Array<{ label: string; actionType: string; payload?: Record<string, unknown> }>;
  };
  if (!actions) return null;

  return (
    <div className="flex flex-wrap gap-2">
      {actions
        .filter((a) => WHITELISTED_ACTION_TYPES.has(a.actionType))
        .map((a, i) => (
          <button
            key={`${a.actionType}-${i}`}
            type="button"
            onClick={() =>
              onAction({
                type: a.actionType as A2UILiteAction["type"],
                payload: a.payload ?? {}
              })
            }
            className="rounded-lg px-3 py-1.5 text-xs font-medium transition-colors hover:opacity-80"
            style={{
              backgroundColor: "var(--sh-workbench-accent-soft)",
              color: "var(--sh-workbench-accent)",
              border: "1px solid var(--sh-workbench-accent)"
            }}
          >
            {a.label}
          </button>
        ))}
    </div>
  );
}

export function renderNode(node: A2UILiteNode, onAction: (action: A2UILiteAction) => void): React.ReactNode {
  switch (node.type) {
    case "metric":
      return <MetricComponent key={node.id} node={node} />;
    case "table":
      return <TableComponent key={node.id} node={node} />;
    case "source_grid":
      return <SourceGridComponent key={node.id} node={node} />;
    case "callout":
      return <CalloutComponent key={node.id} node={node} />;
    case "action_row":
      return <ActionRowComponent key={node.id} node={node} onAction={onAction} />;
    default:
      return (
        <div key={node.id} role="alert" className="text-xs" style={{ color: "var(--theme-text-muted)" }}>
          Unsupported UI component: {node.type}
        </div>
      );
  }
}
