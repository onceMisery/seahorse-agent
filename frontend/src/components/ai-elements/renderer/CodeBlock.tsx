import * as React from "react";
import { Check, Copy, Edit, Eye } from "lucide-react";

import { Button } from "@/components/ui/button";
import { CodeEditor } from "@/components/ai-elements/renderer/CodeEditor";
import type { CodeEditorLanguage } from "@/components/ai-elements/types";

interface CodeBlockProps {
  code: string;
  language?: CodeEditorLanguage | string | null;
  editable?: boolean;
  onChange?: (value: string) => void;
}

export function CodeBlock({ code, language, editable = false, onChange }: CodeBlockProps) {
  const [copied, setCopied] = React.useState(false);
  const [isEditing, setIsEditing] = React.useState(editable);
  const [draft, setDraft] = React.useState(code);

  React.useEffect(() => {
    setDraft(code);
  }, [code]);

  const copy = async () => {
    await navigator.clipboard.writeText(draft);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  };

  const applyChange = (next: string) => {
    setDraft(next);
    onChange?.(next);
  };

  return (
    <div
      className="my-3 overflow-hidden rounded-lg border"
      style={{
        borderColor: "var(--theme-glass-border)",
        backgroundColor: "var(--theme-bg-elevated)"
      }}
    >
      <div
        className="flex items-center justify-between px-3 py-2"
        style={{ borderBottom: "1px solid var(--theme-glass-border)" }}
      >
        <span
          className="font-mono text-[11px] font-semibold uppercase"
          style={{ color: "var(--theme-text-muted)" }}
        >
          {language || "text"}
        </span>
        <div className="flex items-center gap-1">
          {editable ? (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              onClick={() => setIsEditing((current) => !current)}
              aria-label={isEditing ? "Preview code" : "Edit code"}
            >
              {isEditing ? <Eye className="h-3.5 w-3.5" /> : <Edit className="h-3.5 w-3.5" />}
            </Button>
          ) : null}
          <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={copy} aria-label="Copy code">
            {copied ? <Check className="h-3.5 w-3.5 text-green-400" /> : <Copy className="h-3.5 w-3.5" />}
          </Button>
        </div>
      </div>
      <CodeEditor
        value={draft}
        onChange={applyChange}
        language={language}
        readonly={!isEditing}
        minHeight="96px"
        maxHeight="520px"
        className="rounded-none border-0"
      />
    </div>
  );
}
