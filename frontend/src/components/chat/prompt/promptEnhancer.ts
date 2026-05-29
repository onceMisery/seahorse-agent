export interface EnhanceResearchPromptInput {
  original: string;
  outputType: "answer" | "report" | "comparison" | "plan";
  sourcePreference: "official-and-current" | "broad-web" | "uploaded-files";
  depth: "quick" | "standard" | "deep";
}

const OUTPUT_TYPE_LABELS: Record<EnhanceResearchPromptInput["outputType"], string> = {
  answer: "简洁回答",
  report: "结构化报告（含摘要、正文、结论）",
  comparison: "对比分析表格",
  plan: "行动计划（含步骤与优先级）"
};

const SOURCE_LABELS: Record<EnhanceResearchPromptInput["sourcePreference"], string> = {
  "official-and-current": "优先使用官方文档和近期权威来源",
  "broad-web": "广泛搜索网络资料，兼顾多元视角",
  "uploaded-files": "主要基于已上传的文件内容"
};

const DEPTH_LABELS: Record<EnhanceResearchPromptInput["depth"], string> = {
  quick: "快速概览，重点突出",
  standard: "标准深度，覆盖主要方面",
  deep: "深度分析，全面详尽，不遗漏细节"
};

export function enhanceResearchPrompt(input: EnhanceResearchPromptInput): string {
  const { original, outputType, sourcePreference, depth } = input;

  return `${original}

---

**输出结构**：${OUTPUT_TYPE_LABELS[outputType]}

**来源偏好**：${SOURCE_LABELS[sourcePreference]}

**分析深度**：${DEPTH_LABELS[depth]}

**引用来源**：每个关键论点请附上来源标注，格式为 [来源名称](URL) 或 [文件名]。`;
}
