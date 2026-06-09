import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { UIInspectorTab } from "@/components/chat/workbench/UIInspectorTab";
import { AGENT_ARTIFACT_SCAN_STATUS, type AgentArtifact } from "@/types";

const surfaceJson = JSON.stringify({
  version: "seahorse-a2ui-lite/v1",
  title: "Generated UI",
  root: {
    id: "root",
    type: "callout",
    props: {
      body: "Safe UI content"
    }
  }
});

function serverA2UiArtifact(overrides: Partial<AgentArtifact> = {}): AgentArtifact {
  return {
    artifactId: "ui-1",
    runId: "run-1",
    title: "Generated UI",
    mimeType: "application/vnd.seahorse.a2ui+json",
    previewText: surfaceJson,
    scanStatus: AGENT_ARTIFACT_SCAN_STATUS.CLEAN,
    canPreview: true,
    ...overrides
  };
}

describe("UIInspectorTab", () => {
  it("renders clean previewable server A2UI artifacts", () => {
    render(
      <UIInspectorTab
        artifacts={[]}
        serverArtifacts={[serverA2UiArtifact()]}
      />
    );

    expect(screen.getByText("Generated UI")).toBeInTheDocument();
    expect(screen.getByText("Safe UI content")).toBeInTheDocument();
  });

  it("does not render server A2UI artifacts that are not previewable", () => {
    render(
      <UIInspectorTab
        artifacts={[]}
        serverArtifacts={[serverA2UiArtifact({
          scanStatus: AGENT_ARTIFACT_SCAN_STATUS.PENDING,
          canPreview: false
        })]}
      />
    );

    expect(screen.queryByText("Generated UI")).not.toBeInTheDocument();
    expect(screen.queryByText("Safe UI content")).not.toBeInTheDocument();
  });
});
