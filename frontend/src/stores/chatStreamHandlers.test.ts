import { describe, expect, it } from "vitest";

import {
  applyAgentRunSnapshotToMessage,
  applyAgentStreamEventToMessage
} from "@/stores/chatStreamHandlers";
import { AGENT_STREAM_EVENTS, type AgentRunSnapshot, type Message, type StreamEventEnvelope } from "@/types";

function assistantMessage(overrides: Partial<Message> = {}): Message {
  return {
    id: "assistant-1",
    role: "assistant",
    content: "",
    status: "streaming",
    ...overrides
  };
}

function envelope(
  eventSeq: number,
  eventType: string,
  typedPayload: unknown
): StreamEventEnvelope {
  return {
    eventId: `evt-${eventSeq}`,
    eventSeq,
    eventType,
    runId: "run-1",
    timestamp: `2026-06-08T00:00:${String(eventSeq).padStart(2, "0")}Z`,
    typedPayload
  };
}

describe("chatStreamHandlers", () => {
  it("merges live agent events into message workspace fields by stable id", () => {
    const message = assistantMessage({
      lastEventSeq: 4,
      timeline: [{ id: "step-1", title: "Plan", status: "RUNNING", detail: "old" }],
      sources: [{ id: "source-1", title: "Source one", snippet: "old" }]
    });

    applyAgentStreamEventToMessage(
      message,
      envelope(5, AGENT_STREAM_EVENTS.STEP_FINISHED, {
        stepId: "step-1",
        title: "Plan",
        status: "DONE",
        summary: "complete"
      })
    );
    applyAgentStreamEventToMessage(
      message,
      envelope(6, AGENT_STREAM_EVENTS.SOURCE_FOUND, {
        itemId: "source-1",
        title: "Source one",
        snippet: "fresh",
        score: 0.91
      })
    );

    expect(message.lastEventSeq).toBe(6);
    expect(message.timeline).toEqual([
      { id: "step-1", title: "Plan", status: "DONE", detail: "complete" }
    ]);
    expect(message.sources).toEqual([
      { id: "source-1", title: "Source one", snippet: "fresh", score: 0.91 }
    ]);
  });

  it("ignores stale live events and appends artifact deltas once", () => {
    const message = assistantMessage({
      lastEventSeq: 10,
      artifacts: [{
        id: "artifact-1",
        title: "Report",
        language: "markdown",
        code: "hello",
        isComplete: false
      }]
    });

    applyAgentStreamEventToMessage(
      message,
      envelope(9, AGENT_STREAM_EVENTS.ARTIFACT_CONTENT, {
        artifactId: "artifact-1",
        delta: " stale"
      })
    );
    applyAgentStreamEventToMessage(
      message,
      envelope(11, AGENT_STREAM_EVENTS.ARTIFACT_CONTENT, {
        artifactId: "artifact-1",
        delta: " world"
      })
    );

    expect(message.lastEventSeq).toBe(11);
    expect(message.artifacts?.[0]).toMatchObject({
      id: "artifact-1",
      code: "hello world",
      isComplete: false
    });
  });

  it("hydrates snapshot fields without letting older snapshots overwrite newer live data", () => {
    const message = assistantMessage({
      content: "live text",
      rawText: "live text",
      status: "streaming",
      lastEventSeq: 10,
      timeline: [{ id: "step-1", title: "Live step", status: "DONE" }],
      sources: [{ id: "source-1", title: "Live source" }]
    });
    const snapshot: AgentRunSnapshot = {
      run: {
        runId: "run-1",
        status: "RUNNING",
        startedAt: "2026-06-08T00:00:00Z"
      },
      lastEventSeq: 8,
      messageSnapshot: {
        content: "older text",
        thinking: "snapshot thought"
      },
      steps: [{ stepId: "step-1", stepType: "Snapshot step", status: "RUNNING" }],
      sources: [{ itemId: "source-1", title: "Snapshot source" }],
      artifacts: [{
        artifactId: "artifact-1",
        runId: "run-1",
        title: "Report",
        mimeType: "text/markdown",
        previewText: "preview",
        scanStatus: "CLEAN"
      }]
    };

    applyAgentRunSnapshotToMessage(message, snapshot);

    expect(message.content).toBe("live text");
    expect(message.rawText).toBe("live text");
    expect(message.thinking).toBe("snapshot thought");
    expect(message.lastEventSeq).toBe(10);
    expect(message.timeline).toEqual([{ id: "step-1", title: "Live step", status: "DONE" }]);
    expect(message.sources).toEqual([{ id: "source-1", title: "Live source" }]);
    expect(message.serverArtifacts).toEqual([{
      artifactId: "artifact-1",
      runId: "run-1",
      title: "Report",
      mimeType: "text/markdown",
      previewText: "preview",
      scanStatus: "CLEAN"
    }]);
  });

  it("keeps a streaming message active when hydrating a running snapshot", () => {
    const message = assistantMessage({
      status: "streaming",
      lastEventSeq: 1
    });
    const snapshot: AgentRunSnapshot = {
      run: {
        runId: "run-1",
        status: "RUNNING"
      },
      lastEventSeq: 2,
      messageSnapshot: {
        content: "partial"
      }
    };

    applyAgentRunSnapshotToMessage(message, snapshot);

    expect(message.content).toBe("partial");
    expect(message.status).toBe("streaming");
    expect(message.agentRunStatus).toBe("RUNNING");
  });
});
