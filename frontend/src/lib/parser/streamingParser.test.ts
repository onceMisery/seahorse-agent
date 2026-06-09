import { describe, expect, it } from "vitest";

import { parseStreamingText } from "@/lib/parser/streamingParser";

describe("parseStreamingText", () => {
  it("extracts markdown artifacts so a complete document can be copied or downloaded", () => {
    const blocks = parseStreamingText(
      [
        "正文会继续流式展示。",
        '<artifact language="markdown" title="完整项目介绍.md">',
        "# Redis 项目介绍",
        "",
        "这是完整文档。",
        "</artifact>"
      ].join("\n"),
      "message-1"
    );

    expect(blocks).toHaveLength(2);
    expect(blocks[0]).toEqual({ type: "text", text: "正文会继续流式展示。" });
    expect(blocks[1]).toMatchObject({
      type: "artifact",
      artifact: {
        id: "message-1-artifact-0",
        language: "markdown",
        title: "完整项目介绍.md",
        code: "# Redis 项目介绍\n\n这是完整文档。",
        isComplete: true
      }
    });
  });

  it("extracts html preview artifacts separately from markdown text", () => {
    const blocks = parseStreamingText(
      [
        "## 九、生成稿件和版式产物摘要",
        '<artifact language="html" title="Web 预览">',
        "<section><h1>Redis</h1></section>",
        "</artifact>"
      ].join("\n"),
      "message-2"
    );

    expect(blocks[0]).toEqual({ type: "text", text: "## 九、生成稿件和版式产物摘要" });
    expect(blocks[1]).toMatchObject({
      type: "artifact",
      artifact: {
        language: "html",
        title: "Web 预览",
        code: "<section><h1>Redis</h1></section>",
        isComplete: true
      }
    });
  });
});
