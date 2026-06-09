import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { WorkspaceInspector } from "@/components/chat/workbench/WorkspaceInspector";
import type { Message } from "@/types";

const message: Message = {
  id: "assistant-1",
  role: "assistant",
  content: "done",
  timeline: [{ id: "step-1", title: "PLAN", status: "DONE" }],
  sources: [{ id: "source-1", title: "Source one" }],
  artifacts: [{ id: "artifact-1", title: "Report", language: "markdown", code: "# Report", isComplete: true }],
  toolCalls: [{
    id: "call-1",
    toolId: "web_search",
    status: "SUCCEEDED",
    argumentsPreviewJson: "{\"query\":\"seahorse\"}",
    resultSummary: "2 sources",
    durationMs: 1200
  }],
  skills: [{
    id: "deep-research",
    name: "deep-research",
    status: "LOADED",
    injectMode: "METADATA_ONLY",
    category: "PUBLIC",
    allowedTools: ["web_search"],
    description: "Research workflow",
    resourcePath: "SKILL.md"
  }]
};

describe("WorkspaceInspector", () => {
  it("shows tab counts for active message data", () => {
    render(<WorkspaceInspector message={message} open onClose={() => undefined} />);
    expect(screen.getByText("Artifacts")).toBeInTheDocument();
    expect(screen.getAllByText("1").length).toBeGreaterThan(0);
  });

  it("renders tool call details in the workbench", async () => {
    const user = userEvent.setup();
    render(<WorkspaceInspector message={message} open onClose={() => undefined} />);
    expect(screen.getByText("Tool Calls")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: /Tool Calls/ }));
    expect(screen.getByText("web_search")).toBeInTheDocument();
    expect(screen.getByText(/"query": "seahorse"/)).toBeInTheDocument();
    expect(screen.getByText("2 sources")).toBeInTheDocument();
  });

  it("renders skill diagnostics without exposing skill body content", async () => {
    const user = userEvent.setup();
    render(<WorkspaceInspector message={message} open onClose={() => undefined} />);
    expect(screen.getByText("Skills")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: /Skills/ }));
    expect(screen.getByText("deep-research")).toBeInTheDocument();
    expect(screen.getByText("METADATA_ONLY")).toBeInTheDocument();
    expect(screen.getByText("SKILL.md")).toBeInTheDocument();
    expect(screen.getByText("web_search")).toBeInTheDocument();
    expect(screen.queryByText("Instructions:")).not.toBeInTheDocument();
  });
});
