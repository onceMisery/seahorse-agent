import { describe, expect, it } from "vitest";

import { normalizeAuditEvent, normalizeAuditEventPage } from "@/services/auditCostService";

describe("audit event mapping", () => {
  it("maps backend audit records into the UI shape", () => {
    const event = normalizeAuditEvent({
      auditId: "audit-1",
      tenantId: "tenant-a",
      eventType: "CONTEXT_ACCESSED",
      actorType: "USER",
      actorId: "user-1",
      runId: null,
      agentId: "agent-1",
      resourceType: "KNOWLEDGE_BASE",
      resourceId: "kb-1",
      redactedPayload:
        "{\"decisionId\":\"d-1\",\"effect\":\"ALLOW\",\"reasonCode\":\"ALLOWED\",\"resourceType\":\"KNOWLEDGE_BASE\",\"resourceId\":\"kb-1\"}",
      occurredAt: "2026-06-06T00:00:00Z"
    });

    expect(event.actor).toBe("user-1");
    expect(event.resource).toBe("KNOWLEDGE_BASE/kb-1");
    expect(event.payload).toMatchObject({
      decisionId: "d-1",
      effect: "ALLOW",
      reasonCode: "ALLOWED"
    });
    expect(event.timestamp).toBe("2026-06-06T00:00:00Z");
  });

  it("maps paged audit records", () => {
    const page = normalizeAuditEventPage({
      records: [
        {
          auditId: "audit-1",
          tenantId: "tenant-a",
          eventType: "TOOL_INVOKED",
          actorType: "AGENT",
          actorId: "agent-1",
          runId: "run-1",
          agentId: "agent-1",
          resourceType: "tool",
          resourceId: "weather",
          redactedPayload: "{}",
          occurredAt: "2026-06-06T00:00:00Z"
        }
      ],
      total: 1,
      size: 10,
      current: 1,
      pages: 1
    });

    expect(page.records[0].resource).toBe("tool/weather");
    expect(page.records[0].actor).toBe("agent-1");
  });
});
