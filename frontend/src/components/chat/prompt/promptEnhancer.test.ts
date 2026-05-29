import { describe, expect, it } from "vitest";
import { enhanceResearchPrompt } from "@/components/chat/prompt/promptEnhancer";

describe("enhanceResearchPrompt", () => {
  it("adds research structure without replacing the user's intent", () => {
    const result = enhanceResearchPrompt({
      original: "分析 Seahorse Agent 的 UI 升级方向",
      outputType: "report",
      sourcePreference: "official-and-current",
      depth: "deep"
    });
    expect(result).toContain("分析 Seahorse Agent 的 UI 升级方向");
    expect(result).toContain("输出结构");
    expect(result).toContain("引用来源");
  });

  it("preserves original text verbatim in the first paragraph", () => {
    const original = "比较 React 和 Vue 的性能差异";
    const result = enhanceResearchPrompt({
      original,
      outputType: "comparison",
      sourcePreference: "broad-web",
      depth: "standard"
    });
    expect(result.startsWith(original)).toBe(true);
  });

  it("includes output type label for each type", () => {
    const types = ["answer", "report", "comparison", "plan"] as const;
    for (const outputType of types) {
      const result = enhanceResearchPrompt({
        original: "test",
        outputType,
        sourcePreference: "broad-web",
        depth: "quick"
      });
      expect(result).toContain("输出结构");
    }
  });
});
