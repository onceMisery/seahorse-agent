import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { AGENT_ARTIFACT_SCAN_STATUS, type AgentArtifact, type ArtifactBlock } from "@/types";

vi.mock("@/components/ai-elements/renderer/CodeEditor", () => ({
  CodeEditor: ({ value, onChange }: { value: string; onChange?: (value: string) => void }) => (
    <textarea
      aria-label="artifact editor"
      value={value}
      onChange={(event) => onChange?.(event.currentTarget.value)}
    />
  )
}));

vi.mock("@/services/agentArtifactService", () => ({
  downloadAgentArtifact: vi.fn(),
  updateAgentArtifact: vi.fn()
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

import { ArtifactInspectorTab } from "@/components/chat/workbench/ArtifactInspectorTab";
import { updateAgentArtifact } from "@/services/agentArtifactService";

const streamedArtifact: ArtifactBlock = {
  id: "artifact-1",
  title: "Report",
  language: "markdown",
  code: "original",
  isComplete: true
};

const serverArtifact: AgentArtifact = {
  artifactId: "artifact-1",
  runId: "run-1",
  title: "Report",
  mimeType: "text/markdown",
  previewText: "original",
  scanStatus: AGENT_ARTIFACT_SCAN_STATUS.CLEAN,
  canPreview: true
};

describe("ArtifactInspectorTab", () => {
  it("uses the updated server scan status after saving a matching streamed artifact", async () => {
    vi.mocked(updateAgentArtifact).mockResolvedValue({
      ...serverArtifact,
      previewText: "changed",
      scanStatus: AGENT_ARTIFACT_SCAN_STATUS.PENDING,
      canPreview: false
    });

    render(
      <ArtifactInspectorTab
        artifacts={[streamedArtifact]}
        serverArtifacts={[serverArtifact]}
      />
    );

    const downloadButton = screen.getAllByRole("button")[2];
    expect(downloadButton).toBeEnabled();

    fireEvent.click(screen.getByRole("button", { name: "编辑内容" }));
    fireEvent.change(screen.getByLabelText("artifact editor"), {
      target: { value: "changed" }
    });
    fireEvent.click(screen.getByRole("button", { name: "保存内容" }));

    await waitFor(() => {
      expect(updateAgentArtifact).toHaveBeenCalledWith("artifact-1", "changed");
    });
    await waitFor(() => {
      expect(downloadButton).toBeDisabled();
    });
  });
});
