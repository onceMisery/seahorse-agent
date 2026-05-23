import * as React from "react";
import type { ContentBlock } from "@/types";
import { MarkdownRenderer } from "@/components/chat/MarkdownRenderer";
import { ArtifactSandbox } from "@/components/chat/ArtifactSandbox";

interface MessageContentProps {
  blocks: ContentBlock[];
  rawText: string;
}

export const MessageContent = React.memo(function MessageContent({ blocks, rawText }: MessageContentProps) {
  // 历史消息没有 blocks 或 blocks 为空时，回退到纯 Markdown
  if (!blocks || blocks.length === 0) {
    return <MarkdownRenderer content={rawText ?? ""} />;
  }

  return (
    <div className="space-y-2">
      {blocks.map((block, index) => {
        if (block.type === "artifact" && block.artifact) {
          return <ArtifactSandbox key={block.artifact.id} artifact={block.artifact} />;
        }
        if (block.type === "text" && block.text) {
          return <MarkdownRenderer key={`text-${index}`} content={block.text} />;
        }
        return null;
      })}
    </div>
  );
});
