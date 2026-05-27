import * as React from "react";
import type { AgentSource, ContentBlock } from "@/types";
import { MarkdownRenderer } from "@/components/chat/MarkdownRenderer";
import { ArtifactSandbox } from "@/components/chat/ArtifactSandbox";

interface MessageContentProps {
  blocks: ContentBlock[];
  rawText: string;
  sources?: AgentSource[];
}

export const MessageContent = React.memo(function MessageContent({ blocks, rawText, sources }: MessageContentProps) {
  if (!blocks || blocks.length === 0) {
    return <MarkdownRenderer content={rawText ?? ""} sources={sources} />;
  }

  return (
    <div className="space-y-2">
      {blocks.map((block, index) => {
        if (block.type === "artifact" && block.artifact) {
          return <ArtifactSandbox key={block.artifact.id} artifact={block.artifact} />;
        }
        if (block.type === "text" && block.text) {
          return <MarkdownRenderer key={`text-${index}`} content={block.text} sources={sources} />;
        }
        return null;
      })}
    </div>
  );
});
