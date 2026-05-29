import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { WorkspaceInspector } from "@/components/chat/workbench/WorkspaceInspector";
import type { Message } from "@/types";

const message: Message = {
  id: "assistant-1",
  role: "assistant",
  content: "done",
  timeline: [{ id: "step-1", title: "PLAN", status: "DONE" }],
  sources: [{ id: "source-1", title: "Source one" }],
  artifacts: [{ id: "artifact-1", title: "Report", language: "markdown", code: "# Report", isComplete: true }]
};

describe("WorkspaceInspector", () => {
  it("shows tab counts for active message data", () => {
    render(<WorkspaceInspector message={message} open onClose={() => undefined} />);
    expect(screen.getByText("Artifacts")).toBeInTheDocument();
    expect(screen.getAllByText("1").length).toBeGreaterThan(0);
  });
});
