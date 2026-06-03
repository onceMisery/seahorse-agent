import * as React from "react";
import CodeMirror from "@uiw/react-codemirror";
import { javascript } from "@codemirror/lang-javascript";
import { json } from "@codemirror/lang-json";
import { python } from "@codemirror/lang-python";
import { monokaiInit } from "@uiw/codemirror-theme-monokai";

import { cn } from "@/lib/utils";
import { useThemeStore } from "@/stores/themeStore";
import type { CodeEditorLanguage } from "@/components/ai-elements/types";

interface CodeEditorProps {
  value: string;
  onChange?: (value: string) => void;
  language?: CodeEditorLanguage | string | null;
  readonly?: boolean;
  height?: string;
  minHeight?: string;
  maxHeight?: string;
  className?: string;
  placeholder?: string;
  showLineNumbers?: boolean;
  lineWrapping?: boolean;
}

function normalizeLanguage(language?: string | null): CodeEditorLanguage {
  const normalized = (language || "text").toLowerCase();
  if (normalized === "js") return "javascript";
  if (normalized === "ts" || normalized === "tsx") return "typescript";
  if (normalized === "py") return "python";
  if (
    normalized === "javascript" ||
    normalized === "typescript" ||
    normalized === "python" ||
    normalized === "json"
  ) {
    return normalized;
  }
  return "text";
}

export function CodeEditor({
  value,
  onChange,
  language,
  readonly = false,
  height,
  minHeight = "160px",
  maxHeight = "520px",
  className,
  placeholder = "Enter code...",
  showLineNumbers = true,
  lineWrapping = true
}: CodeEditorProps) {
  const theme = useThemeStore((state) => state.theme);
  const normalizedLanguage = normalizeLanguage(language);

  const extensions = React.useMemo(() => {
    const languageExtension =
      normalizedLanguage === "javascript"
        ? javascript()
        : normalizedLanguage === "typescript"
          ? javascript({ typescript: true })
          : normalizedLanguage === "python"
            ? python()
            : normalizedLanguage === "json"
              ? json()
              : null;

    return languageExtension ? [languageExtension] : [];
  }, [normalizedLanguage]);

  const editorTheme = React.useMemo(() => {
    if (theme !== "dark") return undefined;
    return monokaiInit({
      settings: {
        background: "transparent",
        gutterBackground: "transparent"
      }
    });
  }, [theme]);

  return (
    <div
      className={cn("overflow-hidden rounded-lg border", className)}
      style={{
        borderColor: "var(--theme-glass-border)",
        backgroundColor: "var(--theme-bg-elevated)"
      }}
    >
      <CodeMirror
        value={value}
        onChange={(nextValue) => onChange?.(nextValue)}
        extensions={extensions}
        theme={editorTheme}
        editable={!readonly}
        readOnly={readonly}
        height={height}
        minHeight={minHeight}
        maxHeight={maxHeight}
        placeholder={placeholder}
        basicSetup={{
          lineNumbers: showLineNumbers,
          highlightActiveLineGutter: showLineNumbers,
          foldGutter: true,
          highlightSpecialChars: true,
          history: !readonly,
          drawSelection: true,
          dropCursor: !readonly,
          allowMultipleSelections: !readonly,
          indentOnInput: !readonly,
          syntaxHighlighting: true,
          bracketMatching: true,
          closeBrackets: !readonly,
          autocompletion: !readonly,
          rectangularSelection: !readonly,
          crosshairCursor: !readonly,
          highlightActiveLine: !readonly,
          highlightSelectionMatches: true,
          searchKeymap: true,
          foldKeymap: true,
          completionKeymap: !readonly,
          lintKeymap: true
        }}
        className={cn(
          "text-sm",
          lineWrapping && "[&_.cm-content]:whitespace-pre-wrap [&_.cm-line]:break-words"
        )}
      />
    </div>
  );
}
