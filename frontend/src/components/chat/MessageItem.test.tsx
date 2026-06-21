import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { MessageItem } from "@/components/chat/MessageItem";
import { useChatStore } from "@/stores/chatStore";
import type { Message } from "@/types";

describe("MessageItem branch switcher", () => {
  beforeEach(() => {
    useChatStore.setState({
      switchMessageBranch: vi.fn()
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
});
