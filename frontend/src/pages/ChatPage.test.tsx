import * as React from "react";
import { render, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { ChatPage } from "@/pages/ChatPage";

const sendMessage = vi.fn();
const selectSession = vi.fn(() => Promise.resolve());
const fetchSessions = vi.fn(() => Promise.resolve());
const createSession = vi.fn(() => Promise.resolve());
const startNewSessionDraft = vi.fn();

vi.mock("@/components/chat/ChatInput", () => ({
  ChatInput: () => <div data-testid="chat-input" />
}));

vi.mock("@/components/chat/DeepSeaBackground", () => ({
  DeepSeaBackground: () => <div data-testid="deep-sea-background" />
}));

vi.mock("@/components/chat/MessageList", () => ({
  MessageList: () => <div data-testid="message-list" />
}));

vi.mock("@/components/chat/workbench/WorkspaceInspector", () => ({
  WorkspaceInspector: () => <div data-testid="workspace-inspector" />
}));

vi.mock("@/components/readiness/ReadinessStatusBar", () => ({
  ReadinessStatusBar: () => <div data-testid="readiness-status" />
}));

vi.mock("@/components/layout/MainLayout", () => ({
  MainLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>
}));

vi.mock("@/stores/chatStore", () => ({
  useChatStore: () => ({
    messages: [],
    isLoading: false,
    isStreaming: false,
    currentSessionId: "conversation-1",
    sessions: [{ id: "conversation-1", title: "Workspace task" }],
    isCreatingNew: false,
    isCreating: false,
    fetchSessions,
    selectSession,
    createSession,
    startNewSessionDraft,
    sendMessage
  })
}));

vi.mock("@/stores/workbenchStore", () => ({
  useWorkbenchStore: () => ({
    activeMessageId: null,
    inspectorOpen: false,
    closeInspector: vi.fn()
  })
}));

describe("ChatPage", () => {
  it("auto-sends workspace task input once when routed with autoSend state", async () => {
    render(
      <MemoryRouter
        future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
        initialEntries={[
          {
            pathname: "/chat/conversation-1",
            state: { autoSend: "生成 Mermaid 架构图", autoSendId: "task-1" }
          }
        ]}
      >
        <Routes>
          <Route path="/chat/:sessionId" element={<ChatPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(sendMessage).toHaveBeenCalledWith("生成 Mermaid 架构图", {
        conversationIdOverride: "conversation-1"
      });
    });

    expect(sendMessage).toHaveBeenCalledTimes(1);
  });
});
