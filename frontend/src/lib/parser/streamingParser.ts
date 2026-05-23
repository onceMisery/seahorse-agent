import type { ContentBlock, ArtifactBlock, ArtifactLanguage } from "@/types";

const ARTIFACT_OPEN_RE = /^<artifact\s+(?=[^>]*language=)[^>]*language="([^"]+)"(?=[^>]*title=)[^>]*title="([^"]+)"[^>]*>/i;
const ARTIFACT_CLOSE_RE = /^<\/artifact>/i;
const CODE_FENCE_OPEN_RE = /^```(\w*)/;
const CODE_FENCE_CLOSE_RE = /^```\s*$/;

const ARTIFACT_LANGUAGES = new Set(["html", "css", "javascript", "js", "tsx", "vue"]);

function normalizeLang(lang: string): ArtifactLanguage {
  if (lang === "js") return "javascript";
  return lang as ArtifactLanguage;
}

export function parseStreamingText(rawText: string, messageId: string): ContentBlock[] {
  const blocks: ContentBlock[] = [];
  const lines = rawText.split("\n");

  let inCodeBlock = false;
  let codeBlockLang = "";
  let inArtifact = false;

  let currentTextLines: string[] = [];
  let currentArtifactLines: string[] = [];
  let currentArtifact: Omit<ArtifactBlock, "code"> | null = null;

  // 确定性 ID：基于 messageId + 出现次序
  let artifactCount = 0;

  const flushText = () => {
    if (currentTextLines.length > 0) {
      const text = currentTextLines.join("\n").trim();
      if (text) blocks.push({ type: "text", text });
      currentTextLines = [];
    }
  };

  const flushArtifact = (isComplete: boolean) => {
    if (currentArtifact) {
      blocks.push({
        type: "artifact",
        artifact: {
          ...currentArtifact,
          code: currentArtifactLines.join("\n"),
          isComplete,
        },
      });
      currentArtifactLines = [];
      currentArtifact = null;
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const isLastLine = i === lines.length - 1;

    // ── Artifact 标签处理 ──
    if (!inCodeBlock && !inArtifact) {
      const artifactMatch = line.match(ARTIFACT_OPEN_RE);
      if (artifactMatch) {
        flushText();
        const [, language, title] = artifactMatch;
        const id = `${messageId}-artifact-${artifactCount++}`;
        currentArtifact = {
          id,
          language: normalizeLang(language.toLowerCase()),
          title: title ?? "Untitled",
          isComplete: false,
        };
        inArtifact = true;
        continue;
      }
    }

    if (inArtifact) {
      if (ARTIFACT_CLOSE_RE.test(line.trim())) {
        inArtifact = false;
        flushArtifact(true);
      } else {
        currentArtifactLines.push(line);
        if (isLastLine) flushArtifact(false);
      }
      continue;
    }

    // ── 代码块围栏处理 ──
    if (!inArtifact && !inCodeBlock) {
      const fenceMatch = line.match(CODE_FENCE_OPEN_RE);
      if (fenceMatch) {
        const lang = (fenceMatch[1] ?? "").toLowerCase();
        const isArtifactLang = ARTIFACT_LANGUAGES.has(lang);

        if (isArtifactLang) {
          flushText();
          const id = `${messageId}-artifact-${artifactCount++}`;
          currentArtifact = {
            id,
            language: normalizeLang(lang),
            title: `${lang.toUpperCase()} Preview`,
            isComplete: false,
          };
          inArtifact = true;
          continue;
        } else {
          inCodeBlock = true;
          codeBlockLang = lang;
          currentTextLines.push(line);
          continue;
        }
      }
    }

    if (inCodeBlock) {
      currentTextLines.push(line);
      if (CODE_FENCE_CLOSE_RE.test(line)) {
        inCodeBlock = false;
        codeBlockLang = "";
      }
      continue;
    }

    // 普通文本
    currentTextLines.push(line);
  }

  flushText();
  return blocks;
}
