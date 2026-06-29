import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { MarkdownRenderer, normalizeAssistantMarkdown } from "@/components/chat/MarkdownRenderer";

describe("MarkdownRenderer", () => {
  it("normalizes compact agent markdown into renderable headings", () => {
    render(
      <MarkdownRenderer content={"前言。###1.标准公式最基础的计算逻辑为：#### A.项目估算 * **定义**：有效时间。"} />
    );

    expect(screen.getByRole("heading", { level: 3, name: "1.标准公式最基础的计算逻辑为：" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4, name: /A\.项目估算/ })).toBeInTheDocument();
    expect(screen.queryByText(/###1/)).not.toBeInTheDocument();
  });

  it("renders math and safe html instead of showing raw markup", () => {
    const { container } = render(
      <MarkdownRenderer content={"公式： $$ x^2 + y^2 = z^2 $$ <strong>HTML</strong><script>alert(1)</script>"} />
    );

    expect(container.querySelector(".katex")).toBeInTheDocument();
    expect(screen.getByText("HTML")).toBeInTheDocument();
    expect(container.querySelector("script")).not.toBeInTheDocument();
  });

  it("keeps normalized content stable for empty text", () => {
    expect(normalizeAssistantMarkdown("")).toBe("");
  });

  it("renders inline HTML mixed with markdown correctly", () => {
    const { container } = render(
      <MarkdownRenderer content={"这是 **Markdown加粗** 和 <em>HTML斜体</em> 的混合"} />
    );

    // Markdown bold should render
    expect(container.querySelector("strong")).toBeInTheDocument();
    // HTML em should render
    expect(container.querySelector("em")).toBeInTheDocument();
    expect(screen.getByText("HTML斜体")).toBeInTheDocument();
  });

  it("renders markdown inside block-level HTML elements", () => {
    const { container } = render(
      <MarkdownRenderer content={"<div>\n\n**bold inside div**\n\n*italic inside div*\n\n</div>"} />
    );

    // Check if markdown inside div is parsed
    const strong = container.querySelector("div strong");
    const em = container.querySelector("div em");
    expect(strong).toBeInTheDocument();
    expect(em).toBeInTheDocument();
  });

  it("preserves class attributes on HTML elements through sanitize", () => {
    const { container } = render(
      <MarkdownRenderer content={'<span class="custom-class">styled text</span>'} />
    );

    const span = container.querySelector(".custom-class");
    expect(span).toBeInTheDocument();
    expect(span?.textContent).toBe("styled text");
  });
});
