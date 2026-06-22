import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { publishAgent } from "@/services/agentDefinitionService";
import { AgentPublishDialog } from "./AgentPublishDialog";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn()
  }
}));

vi.mock("@/services/agentDefinitionService", async () => {
  const actual = await vi.importActual<typeof import("@/services/agentDefinitionService")>(
    "@/services/agentDefinitionService"
  );
  return {
    ...actual,
    publishAgent: vi.fn().mockResolvedValue({})
  };
});

describe("AgentPublishDialog", () => {
  it("requires a change summary before publish is enabled", async () => {
    const user = userEvent.setup();

    render(
      <AgentPublishDialog
        open
        onOpenChange={() => undefined}
        agentId="agent-1"
        publishCheck={null}
        onSuccess={() => undefined}
      />
    );

    const confirm = screen.getByRole("button", { name: "确认发布" });
    expect(confirm).toBeDisabled();

    await user.type(screen.getByPlaceholderText("请输入本版本 Agent 指令"), "be useful");
    expect(confirm).toBeDisabled();

    await user.type(screen.getByPlaceholderText("请输入发布原因或备注"), "initial release");
    expect(confirm).toBeEnabled();
  });

  it("publishes skillSetJson together with the version payload", async () => {
    const user = userEvent.setup();

    render(
      <AgentPublishDialog
        open
        onOpenChange={() => undefined}
        agentId="agent-1"
        publishCheck={null}
        skillSetJson='{"version":1,"skills":[]}'
        onSuccess={() => undefined}
      />
    );

    await user.type(screen.getByPlaceholderText("请输入本版本 Agent 指令"), "be useful");
    await user.type(screen.getByPlaceholderText("请输入发布原因或备注"), "initial release");
    await user.click(screen.getByRole("button", { name: "确认发布" }));

    expect(publishAgent).toHaveBeenCalledWith(
      "agent-1",
      expect.objectContaining({
        skillSetJson: '{"version":1,"skills":[]}'
      })
    );
  });
});
