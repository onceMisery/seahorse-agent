export function normalizeAssistantMarkdown(content: string): string {
  if (!content) {
    return "";
  }

  return content
    .replace(/\r\n?/g, "\n")
    .replace(/\s*\$\$([\s\S]*?)\$\$\s*/g, (_match, expression: string) => {
      const trimmed = expression.trim();
      return trimmed ? `\n\n$$\n${trimmed}\n$$\n\n` : "\n\n";
    })
    .replace(/([。！？；：.!?;:）)\]}])\s*(#{1,6})(?!#)(?=[^\n])/g, "$1\n\n$2")
    .replace(/([^\S\n]+)(#{1,6})(?!#)(?=\S)/g, "\n\n$2")
    .replace(/^(#{1,6})(?!#)(?=\S)/gm, "$1 ")
    .replace(/([^\n])\n(\s*[-*+])\s/g, "$1\n\n$2 ")
    .replace(/([^\n])\n(\s*\d+\.)\s/g, "$1\n\n$2 ")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}
