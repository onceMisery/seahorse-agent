import { describe, expect, it, beforeEach } from "vitest";
import { useWorkbenchStore } from "@/stores/workbenchStore";

describe("workbenchStore", () => {
  beforeEach(() => {
    useWorkbenchStore.getState().resetWorkbench();
  });

  it("opens the inspector on a selected message and tab", () => {
    useWorkbenchStore.getState().openInspector("message-1", "timeline");
    expect(useWorkbenchStore.getState().activeMessageId).toBe("message-1");
    expect(useWorkbenchStore.getState().activeTab).toBe("timeline");
    expect(useWorkbenchStore.getState().inspectorOpen).toBe(true);
  });

  it("keeps the active message when switching tabs", () => {
    useWorkbenchStore.getState().openInspector("message-2", "artifacts");
    useWorkbenchStore.getState().setActiveTab("sources");
    expect(useWorkbenchStore.getState().activeMessageId).toBe("message-2");
    expect(useWorkbenchStore.getState().activeTab).toBe("sources");
  });
});
