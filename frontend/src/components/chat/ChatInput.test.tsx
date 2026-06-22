import * as React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ChatInput } from "@/components/chat/ChatInput";

const sendMessage = vi.fn();
const setSelectedTaskTemplateId = vi.fn();
const serviceMocks = vi.hoisted(() => ({
  listRoleCards: vi.fn(),
  listRunProfiles: vi.fn(),
  getAppliedRunProfileForConversation: vi.fn(),
  applyRunProfileToConversation: vi.fn()
}));

vi.mock("@/stores/chatStore", () => ({
  useChatStore: () => ({
    currentSessionId: "conversation-1",
    sendMessage,
    isStreaming: false,
    cancelGeneration: vi.fn(),
    deepThinkingEnabled: false,
    setDeepThinkingEnabled: vi.fn(),
    selectedTaskTemplateId: "quick-answer",
    setSelectedTaskTemplateId,
    inputFocusKey: 1
  })
}));

vi.mock("@/components/chat/SkillTrigger", () => ({
  SkillTrigger: React.forwardRef((_props, ref) => {
    React.useImperativeHandle(ref, () => ({ openPicker: vi.fn() }));
    return null;
  })
}));

vi.mock("@/components/chat/prompt/PromptEnhancerButton", () => ({
  PromptEnhancerButton: () => null
}));

vi.mock("@/components/chat/prompt/PromptEnhancerDialog", () => ({
  PromptEnhancerDialog: () => null
}));

vi.mock("@/components/ai-elements/renderer/MarkdownRenderer", () => ({
  MarkdownRenderer: ({ content }: { content: string }) => <div>{content}</div>
}));

vi.mock("@/components/ui/select", async () => {
  const ReactModule = await vi.importActual<typeof React>("react");
  const SelectContext = ReactModule.createContext<{
    onValueChange?: (value: string) => void;
    disabled?: boolean;
    value?: string;
  }>({});
  return {
    Select: ({
      children,
      onValueChange,
      disabled,
      value
    }: {
      children: React.ReactNode;
      value?: string;
      onValueChange?: (value: string) => void;
      disabled?: boolean;
    }) => (
      <SelectContext.Provider value={{ onValueChange, disabled, value }}>
        <div>{children}</div>
      </SelectContext.Provider>
    ),
    SelectTrigger: ({ children, ...props }: React.HTMLAttributes<HTMLButtonElement>) => {
      const context = ReactModule.useContext(SelectContext);
      return (
        <button type="button" data-selected-value={context.value} {...props}>
          {children}
        </button>
      );
    },
    SelectValue: ({ placeholder }: { placeholder?: string }) => <span>{placeholder}</span>,
    SelectContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    SelectItem: ({ children, value }: { children: React.ReactNode; value: string }) => {
      const context = ReactModule.useContext(SelectContext);
      return (
        <button
          type="button"
          role="option"
          disabled={context.disabled}
          onClick={() => context.onValueChange?.(value)}
        >
          {children}
        </button>
      );
    }
  };
});

vi.mock("@/services/taskTemplateService", () => ({
  listTaskTemplates: vi.fn().mockResolvedValue([
    {
      templateId: "quick-answer",
      name: "Quick answer",
      enabled: true,
      maxCostTier: "LOW",
      estimatedDuration: "SHORT"
    }
  ])
}));

vi.mock("@/services/agentService", () => ({
  listAgents: vi.fn().mockResolvedValue({ records: [] })
}));

vi.mock("@/services/roleCardService", () => ({
  listRoleCards: serviceMocks.listRoleCards
}));

vi.mock("@/services/runProfileService", () => ({
  listRunProfiles: serviceMocks.listRunProfiles,
  getAppliedRunProfileForConversation: serviceMocks.getAppliedRunProfileForConversation,
  applyRunProfileToConversation: serviceMocks.applyRunProfileToConversation
}));

vi.mock("@/services/quotaSummaryService", () => ({
  getQuotaSummary: vi.fn().mockResolvedValue({ status: "AVAILABLE", remainingCalls: 9, callLimit: 10 })
}));

vi.mock("@/services/conversationAttachmentService", () => ({
  deleteConversationAttachment: vi.fn(),
  uploadConversationAttachment: vi.fn()
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

describe("ChatInput run profile selector", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMocks.listRoleCards.mockResolvedValue([]);
    serviceMocks.listRunProfiles.mockResolvedValue([
      {
        id: 77,
        name: "Research Profile",
        executorEngine: "agentscope",
        enabled: false
      }
    ]);
    serviceMocks.getAppliedRunProfileForConversation.mockResolvedValue(undefined);
    serviceMocks.applyRunProfileToConversation.mockResolvedValue({
      runProfileId: 77,
      executorEngine: "agentscope",
      explicitToolAllowlist: false,
      toolIds: [],
      mcpToolIds: [],
      a2aAgentIds: []
    });
  });

  it("passes the selected run profile when sending a message", async () => {
    render(<ChatInput />);

    await waitFor(() => {
      expect(screen.getByText("Research Profile")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Research Profile"));
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Use profile" } });
    const buttons = screen.getAllByRole("button");
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => {
      expect(sendMessage).toHaveBeenCalledWith("Use profile", expect.objectContaining({
        runProfileId: "77"
      }));
    });
  });

  it("does not treat the auto-selected role card as a run profile override", async () => {
    serviceMocks.listRoleCards.mockResolvedValue([
      {
        id: 99,
        name: "Default Role",
        definition: "Default",
        enabled: true,
        higherPerm: false
      }
    ]);
    render(<ChatInput />);

    await waitFor(() => {
      expect(screen.getByText("Research Profile")).toBeInTheDocument();
      expect(screen.getByText("Default Role")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Research Profile"));
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Use profile role" } });
    const buttons = screen.getAllByRole("button");
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => {
      const options = sendMessage.mock.calls[0]?.[1];
      expect(options).toEqual(expect.objectContaining({
        runProfileId: "77"
      }));
      expect(options.roleCardId).toBeUndefined();
    });
  });

  it("shows the role card selector as default while an active run profile owns the role", async () => {
    serviceMocks.listRoleCards.mockResolvedValue([
      {
        id: 99,
        name: "Default Role",
        definition: "Default",
        enabled: true,
        higherPerm: false
      }
    ]);
    render(<ChatInput />);

    await waitFor(() => {
      expect(screen.getByText("Research Profile")).toBeInTheDocument();
      expect(screen.getByText("Default Role")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Research Profile"));

    await waitFor(() => {
      expect(screen.getByLabelText("Role card")).toHaveAttribute("data-selected-value", "__default_role__");
    });
  });

  it("keeps the auto-selected role card for plain chat without a run profile", async () => {
    serviceMocks.listRoleCards.mockResolvedValue([
      {
        id: 99,
        name: "Default Role",
        definition: "Default",
        enabled: true,
        higherPerm: false
      }
    ]);
    render(<ChatInput />);

    await waitFor(() => {
      expect(screen.getByText("Default Role")).toBeInTheDocument();
    });

    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Use role" } });
    const buttons = screen.getAllByRole("button");
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => {
      expect(sendMessage).toHaveBeenCalledWith("Use role", expect.objectContaining({
        roleCardId: "99",
        runProfileId: undefined
      }));
    });
  });

  it("applies the selected run profile to the current conversation", async () => {
    render(<ChatInput />);

    await waitFor(() => {
      expect(screen.getByText("Research Profile")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Research Profile"));
    fireEvent.click(screen.getByLabelText("应用方案到当前会话"));

    await waitFor(() => {
      expect(serviceMocks.applyRunProfileToConversation).toHaveBeenCalledWith("conversation-1", "77");
    });
  });

  it("restores the run profile applied to the current conversation", async () => {
    serviceMocks.getAppliedRunProfileForConversation.mockResolvedValue({
      profile: {
        id: 77,
        name: "Research Profile",
        executorEngine: "agentscope"
      },
      toolBindings: []
    });
    render(<ChatInput />);

    await waitFor(() => {
      expect(serviceMocks.getAppliedRunProfileForConversation).toHaveBeenCalledWith("conversation-1");
    });

    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Continue applied profile" } });
    const buttons = screen.getAllByRole("button");
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => {
      expect(sendMessage).toHaveBeenCalledWith("Continue applied profile", expect.objectContaining({
        runProfileId: "77"
      }));
    });
  });
});
