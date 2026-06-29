import * as React from "react";
import rehypeKatex from "rehype-katex";
import rehypeRaw from "rehype-raw";
import rehypeSanitize, { defaultSchema, type Options as SanitizeOptions } from "rehype-sanitize";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import { ImageIcon } from "lucide-react";
import "katex/dist/katex.min.css";

import { CodeBlock } from "@/components/ai-elements/renderer/CodeBlock";
import { CitationBadge } from "@/components/chat/CitationBadge";
import { MermaidDiagram } from "@/components/chat/MermaidDiagram";
import { cn } from "@/lib/utils";
import type { AgentSource } from "@/types";

/**
 * Custom sanitize schema that preserves class/style attributes needed by
 * KaTeX, code-block syntax highlighting, and Mermaid diagrams while
 * still stripping dangerous elements like <script>.
 */
const sanitizeSchema: SanitizeOptions = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    // Allow class + style on every element (KaTeX, code-highlight, Mermaid)
    "*": [
      ...(defaultSchema.attributes?.["*"] ?? []),
      "className",
      "class",
      "style"
    ],
    // Allow data-* attributes used by custom components
    code: [...(defaultSchema.attributes?.code ?? []), "className", "data-language"],
    // Allow span/div attributes needed by KaTeX
    span: [...(defaultSchema.attributes?.span ?? []), "className", "style", "aria-hidden"],
    div: [...(defaultSchema.attributes?.div ?? []), "className", "style"],
    // Allow id on headings for anchor links
    h1: [...(defaultSchema.attributes?.h1 ?? []), "id"],
    h2: [...(defaultSchema.attributes?.h2 ?? []), "id"],
    h3: [...(defaultSchema.attributes?.h3 ?? []), "id"],
    h4: [...(defaultSchema.attributes?.h4 ?? []), "id"],
    h5: [...(defaultSchema.attributes?.h5 ?? []), "id"],
    h6: [...(defaultSchema.attributes?.h6 ?? []), "id"]
  },
  tagNames: [
    ...(defaultSchema.tagNames ?? []),
    // Allow MathML elements used by KaTeX
    "math", "mrow", "mi", "mo", "mn", "msup", "msub", "mfrac",
    "munder", "mover", "munderover", "msqrt", "mroot", "mtable",
    "mtr", "mtd", "mtext", "mspace", "annotation", "semantics",
    // Allow details/summary for collapsible sections
    "details", "summary"
  ]
};

interface MarkdownRendererProps {
  content: string;
  sources?: AgentSource[];
}

export function MarkdownRenderer({ content, sources }: MarkdownRendererProps) {
  const normalizedContent = React.useMemo(() => normalizeAssistantMarkdown(content), [content]);

  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm, remarkMath]}
      rehypePlugins={[[rehypeRaw], [rehypeSanitize, sanitizeSchema], [rehypeKatex]]}
      components={{
        h1({ children, ...props }) {
          return (
            <h1
              className="mb-4 mt-6 text-2xl font-bold tracking-tight"
              style={{ color: "var(--theme-text-primary)", borderBottom: "1px solid var(--theme-glass-border)", paddingBottom: "0.5rem" }}
              {...props}
            >
              {children}
            </h1>
          );
        },
        h2({ children, ...props }) {
          return (
            <h2
              className="mb-3 mt-5 text-xl font-semibold tracking-tight"
              style={{ color: "var(--theme-text-primary)" }}
              {...props}
            >
              {children}
            </h2>
          );
        },
        h3({ children, ...props }) {
          return (
            <h3
              className="mb-2 mt-4 text-lg font-semibold"
              style={{ color: "var(--theme-text-primary)" }}
              {...props}
            >
              {children}
            </h3>
          );
        },
        h4({ children, ...props }) {
          return (
            <h4
              className="mb-2 mt-3 text-base font-semibold"
              style={{ color: "var(--theme-text-primary)" }}
              {...props}
            >
              {children}
            </h4>
          );
        },
        p({ children, ...props }) {
          return <p className="my-2.5 text-sm leading-[1.8]" style={{ color: "var(--theme-text-primary)" }} {...props}>{renderWithCitations(children, sources)}</p>;
        },
        li({ children, ...props }) {
          return <li className="text-sm leading-[1.8]" style={{ color: "var(--theme-text-primary)" }} {...props}>{renderWithCitations(children, sources)}</li>;
        },
        hr(props) {
          return <hr className="my-6" style={{ borderColor: "var(--theme-glass-border)" }} {...props} />;
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
                className={cn("rounded px-1.5 py-0.5 font-mono text-[0.875em]", className)}
                style={{ backgroundColor: "var(--theme-accent-alpha-10)", color: "var(--theme-accent)" }}
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
              className="my-4 rounded-r-lg pl-4 pr-4 py-3 not-italic"
              style={{ borderLeft: "3px solid var(--theme-accent)", backgroundColor: "var(--theme-accent-alpha-10)", color: "var(--theme-text-secondary)" }}
              {...props}
            >
              {children}
            </blockquote>
          );
        },
        ul({ children, ...props }) {
          return (
            <ul className="my-2.5 ml-5 list-disc space-y-1.5" style={{ color: "var(--theme-text-primary)" }} {...props}>
              {children}
            </ul>
          );
        },
        ol({ children, ...props }) {
          return (
            <ol className="my-2.5 ml-5 list-decimal space-y-1.5" style={{ color: "var(--theme-text-primary)" }} {...props}>
              {children}
            </ol>
          );
        }
      }}
      className="prose max-w-none prose-p:leading-[1.8] prose-li:leading-[1.8]"
      style={{
        color: "var(--theme-text-primary)",
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
      {normalizedContent}
    </ReactMarkdown>
  );
}

export function normalizeAssistantMarkdown(content: string): string {
  if (!content) {
    return "";
  }

  return content
    // Normalize line endings
    .replace(/\r\n?/g, "\n")
    // Protect $$...$$ math blocks: ensure blank lines around them for remark-math
    .replace(/\s*\$\$([\s\S]*?)\$\$\s*/g, (_match, expression: string) => {
      const trimmed = expression.trim();
      return trimmed ? `\n\n$$\n${trimmed}\n$$\n\n` : "\n\n";
    })
    // Heading after CJK/sentence-ending punctuation: "前言。###标题" → "前言。\n\n###标题"
    .replace(/([。！？；：.!?;:）)\]}])\s*(#{1,6})(?!#)(?=[^\n])/g, "$1\n\n$2")
    // Heading preceded by horizontal whitespace (not at line start): insert blank line
    .replace(/([^\S\n]+)(#{1,6})(?!#)(?=\S)/g, "\n\n$2")
    // Heading without space after #: "###标题" → "### 标题" (line-start only)
    .replace(/^(#{1,6})(?!#)(?=\S)/gm, "$1 ")
    // Protect inline code spans from being broken by other regexes
    // (no-op here; we only normalize structural issues)
    // Ensure blank line before list items that follow a paragraph
    .replace(/([^\n])\n(\s*[-*+])\s/g, "$1\n\n$2 ")
    .replace(/([^\n])\n(\s*\d+\.)\s/g, "$1\n\n$2 ")
    
    // Collapse excessive blank lines
    .replace(/\n{3,}/g, "\n\n")
    .trim();
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
