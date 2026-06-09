import { afterEach, describe, expect, it, vi } from "vitest";

import { createStreamResponse } from "@/hooks/useStreamResponse";
import type { StreamEventEnvelope } from "@/types";

function sseResponse(body: string) {
  const encoder = new TextEncoder();
  return new Response(
    new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(body));
        controller.close();
      }
    }),
    {
      status: 200,
      headers: { "content-type": "text/event-stream" }
    }
  );
}

describe("createStreamResponse resume handling", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it("reconnects with resumeRunId and lastEventSeq after an interrupted stream", async () => {
    let lastEventSeq: number | null = null;
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(sseResponse([
        "event:stream_event",
        "data:{\"eventId\":\"evt-1\",\"eventSeq\":1,\"eventType\":\"agent.step.started\",\"runId\":\"run-1\",\"timestamp\":\"2026-06-08T00:00:01Z\",\"typedPayload\":{\"stepId\":\"step-1\"}}",
        ""
      ].join("\n")))
      .mockResolvedValueOnce(sseResponse([
        "event:stream_event",
        "data:{\"eventId\":\"evt-2\",\"eventSeq\":2,\"eventType\":\"agent.step.finished\",\"runId\":\"run-1\",\"timestamp\":\"2026-06-08T00:00:02Z\",\"typedPayload\":{\"stepId\":\"step-1\"}}",
        "",
        "event:done",
        "data:[DONE]",
        ""
      ].join("\n")));
    vi.stubGlobal("fetch", fetchMock);

    const stream = createStreamResponse(
      {
        url: "/rag/v3/chat?conversationId=session-1",
        retryCount: 1,
        retryDelayMs: 0,
        resume: () => ({ runId: "run-1", lastEventSeq })
      },
      {
        onStreamEvent: (event: StreamEventEnvelope) => {
          lastEventSeq = event.eventSeq;
        }
      }
    );

    await stream.start();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe("/rag/v3/chat?conversationId=session-1");
    expect(fetchMock.mock.calls[1][0]).toBe("/rag/v3/chat?resumeRunId=run-1&lastEventSeq=1");
    expect(lastEventSeq).toBe(2);
  });
});
