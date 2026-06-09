import { describe, expect, it } from "vitest";

import { AGENT_STREAM_EVENTS } from "@/types";
import { normalizeAgentStreamEvent } from "@/stores/chatStreamUtils";

describe("normalizeAgentStreamEvent", () => {
  it("normalizes final markdown artifact events for whole-document download", () => {
    const normalized = normalizeAgentStreamEvent(AGENT_STREAM_EVENTS.ARTIFACT_CREATED, {
      id: "artifact-run-1",
      title: "完整项目介绍.md",
      language: "markdown",
      artifactType: "MARKDOWN",
      content: "# Redis 项目介绍",
      runId: "run-1"
    });

    expect(normalized).toMatchObject({
      type: AGENT_STREAM_EVENTS.ARTIFACT,
      items: [
        {
          id: "artifact-run-1",
          language: "markdown",
          title: "完整项目介绍.md",
          code: "# Redis 项目介绍",
          isComplete: true
        }
      ]
    });
    expect(normalized?.type === AGENT_STREAM_EVENTS.ARTIFACT ? normalized.serverArtifacts : []).toEqual([]);
  });

  it("keeps persisted artifact metadata available for server downloads", () => {
    const normalized = normalizeAgentStreamEvent(AGENT_STREAM_EVENTS.ARTIFACT_CREATED, {
      id: "artifact-db-1",
      artifactId: "artifact-db-1",
      title: "完整项目介绍.md",
      artifactType: "MARKDOWN",
      mimeType: "text/markdown",
      storageRef: "s3://agent-artifacts/artifact-db-1",
      previewText: "# Redis 项目介绍",
      scanStatus: "CLEAN"
    });

    expect(normalized).toMatchObject({
      type: AGENT_STREAM_EVENTS.ARTIFACT,
      serverArtifacts: [
        {
          artifactId: "artifact-db-1",
          title: "完整项目介绍.md",
          mimeType: "text/markdown",
          storageRef: "s3://agent-artifacts/artifact-db-1",
          previewText: "# Redis 项目介绍",
          scanStatus: "CLEAN"
        }
      ]
    });
  });
});
