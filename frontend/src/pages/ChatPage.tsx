import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Group, Panel, Separator } from "react-resizable-panels";

import { ChatInput } from "@/components/chat/ChatInput";
import { DeepSeaBackground } from "@/components/chat/DeepSeaBackground";
import { MessageList } from "@/components/chat/MessageList";
import { WorkspaceInspector } from "@/components/chat/workbench/WorkspaceInspector";
import { MainLayout } from "@/components/layout/MainLayout";
import { useChatStore } from "@/stores/chatStore";
import { useWorkbenchStore } from "@/stores/workbenchStore";

export function ChatPage() {
  const navigate = useNavigate();
  const { sessionId } = useParams<{ sessionId: string }>();
  const {
    messages,
    isLoading,
    isStreaming,
    currentSessionId,
    sessions,
    isCreatingNew,
    fetchSessions,
    selectSession,
    createSession
  } = useChatStore();
  const { activeMessageId, inspectorOpen, closeInspector } = useWorkbenchStore();
  const showWelcome = messages.length === 0 && !isLoading;
  const [sessionsReady, setSessionsReady] = React.useState(false);
  const invalidSessionHandledRef = React.useRef<string | null>(null);
  const sessionExists = React.useMemo(() => {
    if (!sessionId) return false;
    return sessions.some((session) => session.id === sessionId);
  }, [sessionId, sessions]);

  const activeMessage = React.useMemo(
    () => messages.find((m) => m.id === activeMessageId) ?? null,
    [messages, activeMessageId]
  );

  React.useEffect(() => {
    let active = true;
    fetchSessions()
      .catch(() => null)
      .finally(() => {
        if (active) {
          setSessionsReady(true);
        }
      });
    return () => {
      active = false;
    };
  }, [fetchSessions]);

  React.useEffect(() => {
    if (sessionId) {
      if (sessionsReady && !sessionExists) {
        if (invalidSessionHandledRef.current === sessionId) {
          return;
        }
        invalidSessionHandledRef.current = sessionId;
        void createSession().catch(() => null);
        if (currentSessionId) {
          navigate(`/chat/${currentSessionId}`, { replace: true });
        } else {
          navigate("/chat", { replace: true });
        }
        return;
      }
      invalidSessionHandledRef.current = null;
      selectSession(sessionId).catch(() => null);
      return;
    }
    invalidSessionHandledRef.current = null;
    if (!sessionsReady) {
      return;
    }
    if (isCreatingNew) {
      return;
    }
    if (currentSessionId) {
      return;
    }
    createSession().catch(() => null);
  }, [
    sessionId,
    sessionsReady,
    sessionExists,
    isCreatingNew,
    currentSessionId,
    selectSession,
    createSession,
    navigate
  ]);

  React.useEffect(() => {
    if (currentSessionId && currentSessionId !== sessionId) {
      navigate(`/chat/${currentSessionId}`, { replace: true });
    }
  }, [currentSessionId, sessionId, navigate]);

  return (
    <MainLayout>
      <div className="relative flex h-full">
        <Group id="seahorse-chat-panels">
          <Panel minSize={44}>
            <div className="relative flex h-full min-w-0 flex-col">
              <DeepSeaBackground />
              <div className="flex-1 min-h-0">
                <MessageList
                  messages={messages}
                  isLoading={isLoading}
                  isStreaming={isStreaming}
                  sessionKey={currentSessionId}
                />
              </div>
              {showWelcome ? null : (
                <div className="relative z-20">
                  <div className="mx-auto max-w-[800px] px-6 pt-1 pb-4">
                    <ChatInput />
                  </div>
                </div>
              )}
            </div>
          </Panel>

          {inspectorOpen && (
            <>
              <Separator className="hidden md:flex w-[3px] items-center justify-center
                bg-[var(--sh-workbench-border)] hover:bg-[var(--sh-workbench-accent)] transition-colors
                cursor-col-resize" />
              <Panel defaultSize={34} minSize={26} maxSize={48} className="hidden md:flex">
                <WorkspaceInspector
                  message={activeMessage}
                  open={inspectorOpen}
                  onClose={closeInspector}
                />
              </Panel>
            </>
          )}
        </Group>

        {inspectorOpen && (
          <div
            className="workspace-inspector-mobile fixed inset-x-0 bottom-0 z-[var(--sh-z-inspector-mobile)] md:hidden"
          >
            <div
              className="h-full rounded-t-xl shadow-xl"
              style={{ background: "var(--sh-workbench-panel)", border: "1px solid var(--sh-workbench-border)" }}
            >
              <WorkspaceInspector
                message={activeMessage}
                open={inspectorOpen}
                onClose={closeInspector}
              />
            </div>
          </div>
        )}
      </div>
    </MainLayout>
  );
}
