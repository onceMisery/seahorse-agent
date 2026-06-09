import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const guardedFiles = [
  "src/hooks/useStreamResponse.ts",
  "src/stores/chatStore.ts",
  "src/components/chat/workbench/WorkspaceInspector.tsx",
  "src/components/chat/workbench/ArtifactInspectorTab.tsx",
  "src/services/agentArtifactService.ts"
];

const badCodePoints = [
  0x9354,
  0x9239,
  0x7035,
  0x6d93,
  0x95ab,
  0x59ab,
  0x9983,
  0x9241,
  0x9242,
  0xfffd
];

function readFrontendFile(relativePath: string) {
  return readFileSync(resolve(__dirname, "..", "..", relativePath), "utf8");
}

describe("chat workspace encoding guard", () => {
  it("keeps chat and workbench source files free from known mojibake code points", () => {
    const hits = guardedFiles.flatMap((file) => {
      const text = readFrontendFile(file);
      return badCodePoints.flatMap((codePoint) => {
        const char = String.fromCharCode(codePoint);
        return text.includes(char) ? [`${file}:0x${codePoint.toString(16).toUpperCase()}`] : [];
      });
    });

    expect(hits).toEqual([]);
  });

  it("keeps critical Chinese labels and stream timeout copy readable", () => {
    const workspaceInspector = readFrontendFile("src/components/chat/workbench/WorkspaceInspector.tsx");
    const artifactInspector = readFrontendFile("src/components/chat/workbench/ArtifactInspectorTab.tsx");
    const streamHook = readFrontendFile("src/hooks/useStreamResponse.ts");

    expect(workspaceInspector).toContain("运行详情");
    expect(workspaceInspector).toContain("关闭检查器");
    expect(artifactInspector).toContain("复制内容");
    expect(artifactInspector).toContain("下载");
    expect(artifactInspector).toContain("文件未通过安全扫描");
    expect(streamHook).toContain("Stream timeout: 服务器未在规定时间内响应");
  });
});
