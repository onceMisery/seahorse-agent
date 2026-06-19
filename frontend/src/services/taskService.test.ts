import { describe, expect, it, vi } from "vitest";

describe("taskService", () => {
  it("uses the configured /api base only once for task SSE subscriptions", async () => {
    vi.resetModules();
    vi.stubEnv("VITE_API_BASE_URL", "/api");
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => undefined))
    );

    const { subscribeTaskEvents } = await import("@/services/taskService");

    const subscription = subscribeTaskEvents("task-1", {
      onEvent: vi.fn()
    });

    await vi.waitFor(() => {
      expect(fetch).toHaveBeenCalled();
    });

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe("/api/tasks/task-1/events");
    subscription.close();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });
});
