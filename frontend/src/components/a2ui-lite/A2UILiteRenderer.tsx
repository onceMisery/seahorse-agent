import * as React from "react";
import { renderNode } from "@/components/a2ui-lite/a2uiRegistry";
import type { A2UILiteAction, A2UILiteNode, A2UILiteSurface } from "@/components/a2ui-lite/a2uiTypes";

interface A2UILiteRendererProps {
  surface: A2UILiteSurface;
  onAction: (action: A2UILiteAction) => void;
}

function renderTree(node: A2UILiteNode, onAction: (action: A2UILiteAction) => void): React.ReactNode {
  const rendered = renderNode(node, onAction);
  if (!node.children || node.children.length === 0) return rendered;

  return (
    <div key={node.id} className="space-y-2">
      {rendered}
      {node.children.map((child) => renderTree(child, onAction))}
    </div>
  );
}

export function A2UILiteRenderer({ surface, onAction }: A2UILiteRendererProps) {
  if (surface.version !== "seahorse-a2ui-lite/v1") {
    return (
      <div role="alert" className="text-xs p-3" style={{ color: "var(--theme-text-muted)" }}>
        Unsupported surface version: {String((surface as { version?: unknown }).version ?? "")}
      </div>
    );
  }

  return (
    <div className="p-3 space-y-2">
      {surface.title && (
        <p className="text-xs font-semibold" style={{ color: "var(--theme-text-primary)" }}>
          {surface.title}
        </p>
      )}
      {renderTree(surface.root, onAction)}
    </div>
  );
}
