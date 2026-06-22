import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}));

import { api } from "@/services/api";
import { switchMessageBranch } from "@/services/sessionService";

describe("sessionService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("saves the selected branch cursor before reloading the message tree", async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      conversationId: "conversation-branch",
      leafMessageId: "3"
    });
    vi.mocked(api.get).mockResolvedValueOnce([{
      message: {
        id: "3",
        conversationId: "conversation-branch",
        role: "assistant",
        content: "new branch",
        vote: null
      },
      preSiblings: ["1", "2"],
      nextSiblings: [],
      branchIndex: 3,
      branchTotal: 3
    }]);

    await expect(switchMessageBranch("conversation-branch", "3")).resolves.toEqual([{
      message: {
        id: "3",
        conversationId: "conversation-branch",
        role: "assistant",
        content: "new branch",
        vote: null
      },
      preSiblings: ["1", "2"],
      nextSiblings: [],
      branchIndex: 3,
      branchTotal: 3
    }]);

    expect(api.post).toHaveBeenCalledWith("/conversations/conversation-branch/branch-cursor", {
      leafMessageId: "3"
    });
    expect(api.get).toHaveBeenCalledWith("/conversations/conversation-branch/messages/tree");
  });
});
