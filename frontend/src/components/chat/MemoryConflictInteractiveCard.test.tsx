import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { MemoryConflictInteractiveCard } from "@/components/chat/MemoryConflictInteractiveCard";
import { resolveMemoryConflictInteractive } from "@/services/memoryGovernanceService";
import type { MemoryConflictPrompt } from "@/types";

vi.mock("@/services/memoryGovernanceService", () => ({
  resolveMemoryConflictInteractive: vi.fn()
}));

describe("MemoryConflictInteractiveCard", () => {
  const prompt: MemoryConflictPrompt = {
    conflictId: "mem-conflict-1",
    memoryId1: "memory-a",
    memoryId2: "memory-b",
    conflictType: "CONTRADICTION",
    severity: "HIGH",
    question: "请选择正确的记忆",
    status: "pending",
    options: [
      { value: "keep_a", label: "保留记忆 A" },
      { value: "keep_b", label: "保留记忆 B" }
    ]
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(resolveMemoryConflictInteractive).mockResolvedValue({ resolved: true });
  });

  it("calls the interactive resolve API and reports the resolved action", async () => {
    const onResolved = vi.fn();

    render(<MemoryConflictInteractiveCard prompt={prompt} onResolved={onResolved} />);

    fireEvent.click(screen.getByRole("button", { name: "保留记忆 A" }));
    fireEvent.click(screen.getByRole("button", { name: "确认" }));

    await waitFor(() => {
      expect(resolveMemoryConflictInteractive).toHaveBeenCalledWith({
        conflictId: "mem-conflict-1",
        action: "keep_a",
        source: "chat-ui"
      });
      expect(onResolved).toHaveBeenCalledWith("mem-conflict-1", "keep_a");
      expect(screen.getByText("已根据你的选择处理")).toBeInTheDocument();
    });
  });
});
