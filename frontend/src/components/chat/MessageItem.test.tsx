import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { MessageItem } from "@/components/chat/MessageItem";
import { useChatStore } from "@/stores/chatStore";
import type { Message } from "@/types";

describe("MessageItem branch switcher", () => {
  beforeEach(() => {
    useChatStore.setState({
      switchMessageBranch: vi.fn(),
      editUserMessageBranch: vi.fn(),
      regenerateAssistantMessageBranch: vi.fn()
    });
  });

  it("renders branch position and switches to the next sibling", () => {
    const switchMessageBranch = vi.fn();
    useChatStore.setState({
      switchMessageBranch
    });
    const message: Message = {
      id: "2",
      role: "user",
      content: "hello",
      status: "done",
      branchIndex: 2,
      branchTotal: 3,
      preSiblings: ["1"],
      nextSiblings: ["3"]
    };

    render(<MessageItem message={message} />);

    expect(screen.getByText("2 / 3")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Next branch"));

    expect(switchMessageBranch).toHaveBeenCalledWith("2", "3");
  });

  it("edits a user message through a branch fork action", () => {
    const editUserMessageBranch = vi.fn();
    useChatStore.setState({
      editUserMessageBranch
    });
    const message: Message = {
      id: "2",
      role: "user",
      content: "old prompt",
      status: "done"
    };

    render(<MessageItem message={message} />);

    fireEvent.click(screen.getByLabelText("Edit message"));
    fireEvent.change(screen.getByLabelText("Edit message content"), {
      target: { value: "edited prompt" }
    });
    fireEvent.click(screen.getByLabelText("Save edit"));

    expect(editUserMessageBranch).toHaveBeenCalledWith("2", "edited prompt");
  });

  it("renders user message markdown and sanitized html through the shared renderer", () => {
    const message: Message = {
      id: "2",
      role: "user",
      content: "## Request\n\n<strong>Important</strong><script>alert(1)</script>",
      status: "done"
    };

    const { container } = render(<MessageItem message={message} />);

    expect(screen.getByRole("heading", { level: 2, name: "Request" })).toBeInTheDocument();
    expect(screen.getByText("Important")).toBeInTheDocument();
    expect(container.querySelector("script")).not.toBeInTheDocument();
    expect(screen.queryByText(/<strong>/)).not.toBeInTheDocument();
  });

  it("regenerates an assistant message through a branch action", () => {
    const regenerateAssistantMessageBranch = vi.fn();
    useChatStore.setState({
      regenerateAssistantMessageBranch
    });
    const message: Message = {
      id: "3",
      role: "assistant",
      content: "old answer",
      status: "done",
      parentId: "2"
    };

    render(<MessageItem message={message} />);

    fireEvent.click(screen.getByLabelText("Regenerate response"));

    expect(regenerateAssistantMessageBranch).toHaveBeenCalledWith("3");
  });
});
