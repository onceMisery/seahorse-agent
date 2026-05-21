// @ts-nocheck
/* eslint-disable */

import * as React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Check, Copy, ImageIcon } from "lucide-react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark } from "react-syntax-highlighter/dist/esm/styles/prism";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface MarkdownRendererProps {
  content: string;
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        code({ inline, className, children, node, ...props }) {
          const match = /language-(\w+)/.exec(className || "");
          const language = match?.[1] || "text";
          const value = String(children).replace(/\n$/, "");

          if (inline || !value.includes('\n')) {
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

          return (
            <div className="my-3 overflow-hidden rounded-md" style={{ border: "1px solid var(--theme-glass-border)", backgroundColor: "var(--theme-bg-elevated)" }}>
              <div className="flex items-center justify-between px-3 py-1.5" style={{ borderBottom: "1px solid var(--theme-glass-border)", backgroundColor: "var(--theme-bg-surface)" }}>
                <span className="font-mono text-[11px] font-semibold uppercase tracking-wider" style={{ color: "var(--theme-text-muted)" }}>
                  {language}
                </span>
                <CopyButton value={value} />
              </div>
              <div className="overflow-x-auto">
                <SyntaxHighlighter
                  language={language}
                  style={oneDark}
                  PreTag="div"
                  customStyle={{
                    margin: 0,
                    padding: "0.75rem 1rem",
                    background: "transparent",
                    fontSize: "13px",
                    lineHeight: "1.5"
                  }}
                  showLineNumbers={false}
                  wrapLines={true}
                >
                  {value}
                </SyntaxHighlighter>
              </div>
            </div>
          );
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

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = React.useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  };

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={handleCopy}
      aria-label="复制代码"
      className="h-7 w-7 transition-colors"
      style={{ color: "var(--theme-text-muted)" }}
    >
      {copied ? (
        <Check className="h-3.5 w-3.5 text-green-400" />
      ) : (
        <Copy className="h-3.5 w-3.5" />
      )}
    </Button>
  );
}
