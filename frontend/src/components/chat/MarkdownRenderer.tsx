import * as React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { ImageIcon } from "lucide-react";

import { CodeBlock } from "@/components/ai-elements/renderer/CodeBlock";
import { CitationBadge } from "@/components/chat/CitationBadge";
import { MermaidDiagram } from "@/components/chat/MermaidDiagram";
import { cn } from "@/lib/utils";
import type { AgentSource } from "@/types";

interface MarkdownRendererProps {
  content: string;
  sources?: AgentSource[];
}

export function MarkdownRenderer({ content, sources }: MarkdownRendererProps) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        p({ children, ...props }) {
          return <p {...props}>{renderWithCitations(children, sources)}</p>;
        },
        li({ children, ...props }) {
          return <li {...props}>{renderWithCitations(children, sources)}</li>;
        },
        code({ className, children, ...props }) {
          const match = /language-(\w+)/.exec(className || "");
          const language = match?.[1] || "text";
          const value = String(children).replace(/\n$/, "");
          const hasLanguage = Boolean(match);
          const hasNewlines = value.includes('\n');

          if (language === "mermaid" && hasNewlines) {
            return <MermaidDiagram code={value} />;
          }

          if (!hasLanguage && !hasNewlines) {
            return (
              <code
                className={cn("rounded px-1.5 py-0.5 text-[13px] font-mono", className)}
                style={{ backgroundColor: "var(--theme-bg-elevated)", color: "var(--theme-text-primary)" }}
                {...props}
              >
                {children}
              </code>
            );
          }

          return <CodeBlock code={value} language={language} editable />;
        },
        img({ src, alt, ...props }) {
          const [hasError, setHasError] = React.useState(false);

          if (hasError) {
            return (
              <div className="my-3 flex items-center gap-2 text-sm" style={{ color: "var(--theme-text-muted)" }}>
                <ImageIcon className="h-4 w-4" />
                <span>图片加载失败</span>
              </div>
            );
          }

          return (
            <img
              src={src}
              alt=""
              className="my-3 max-w-full rounded-lg"
              onError={() => setHasError(true)}
              loading="lazy"
              {...props}
            />
          );
        },
        a({ children, ...props }) {
          return (
            <a
              style={{ color: "var(--theme-accent)" }}
              className="underline-offset-4 hover:underline"
              target="_blank"
              rel="noreferrer"
              {...props}
            >
              {children}
            </a>
          );
        },
        table({ children, ...props }) {
          return (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse rounded-md" style={{ border: "1px solid var(--theme-glass-border)" }} {...props}>
                {children}
              </table>
            </div>
          );
        },
        thead({ children, ...props }) {
          return (
            <thead style={{ backgroundColor: "var(--theme-bg-surface)" }} {...props}>
              {children}
            </thead>
          );
        },
        th({ children, ...props }) {
          return (
            <th className="px-3 py-2 text-left text-sm font-semibold last:border-r-0" style={{ borderBottom: "1px solid var(--theme-glass-border)", borderRight: "1px solid var(--theme-glass-border)", color: "var(--theme-text-primary)" }} {...props}>
              {children}
            </th>
          );
        },
        td({ children, ...props }) {
          return (
            <td className="px-3 py-2.5 text-sm last:border-r-0" style={{ borderBottom: "1px solid var(--theme-glass-border)", borderRight: "1px solid var(--theme-glass-border)", color: "var(--theme-text-primary)" }} {...props}>
              {children}
            </td>
          );
        },
        blockquote({ children, ...props }) {
          return (
            <blockquote
              className="my-3 pl-3 pr-3 py-2 italic"
              style={{ borderLeft: "4px solid var(--theme-accent)", backgroundColor: "var(--theme-accent-alpha-10)", color: "var(--theme-text-secondary)" }}
              {...props}
            >
              {children}
            </blockquote>
          );
        },
        ul({ children, ...props }) {
          return (
            <ul className="my-2 ml-6 list-disc space-y-1" {...props}>
              {children}
            </ul>
          );
        },
        ol({ children, ...props }) {
          return (
            <ol className="my-2 ml-6 list-decimal space-y-1" {...props}>
              {children}
            </ol>
          );
        }
      }}
      className="prose max-w-none prose-headings:font-semibold prose-p:leading-relaxed"
      style={{
        "--tw-prose-headings": "var(--theme-text-primary)",
        "--tw-prose-body": "var(--theme-text-primary)",
        "--tw-prose-bold": "var(--theme-text-primary)",
        "--tw-prose-links": "var(--theme-accent)",
        "--tw-prose-code": "var(--theme-text-primary)",
        "--tw-prose-quotes": "var(--theme-text-secondary)",
        "--tw-prose-quote-borders": "var(--theme-accent)",
        "--tw-prose-bullets": "var(--theme-text-muted)",
        "--tw-prose-counters": "var(--theme-text-muted)",
        "--tw-prose-li": "var(--theme-text-primary)"
      }}
    >
      {content}
    </ReactMarkdown>
  );
}

function renderWithCitations(children: React.ReactNode, sources?: AgentSource[]): React.ReactNode {
  if (!sources || sources.length === 0) {
    return children;
  }
  return React.Children.map(children, (child, idx) => {
    if (typeof child !== "string") {
      return child;
    }
    const segments: React.ReactNode[] = [];
    let lastIndex = 0;
    for (const match of child.matchAll(/\[(\d+)\]/g)) {
      const num = parseInt(match[1], 10);
      const source = sources[num - 1];
      if (!source) continue;
      const matchIndex = match.index!;
      if (matchIndex > lastIndex) {
        segments.push(child.slice(lastIndex, matchIndex));
      }
      segments.push(
        <CitationBadge key={`cite-${idx}-${matchIndex}`} index={num} source={source} />
      );
      lastIndex = matchIndex + match[0].length;
    }
    if (segments.length === 0) return child;
    if (lastIndex < child.length) {
      segments.push(child.slice(lastIndex));
    }
    return <React.Fragment key={`frag-${idx}`}>{segments}</React.Fragment>;
  });
}
