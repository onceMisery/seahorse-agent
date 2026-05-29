import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Group, Panel, Separator } from "react-resizable-panels";

import { ArtifactPanel } from "@/components/chat/ArtifactPanel";
import { ChatInput } from "@/components/chat/ChatInput";
import { DeepSeaBackground } from "@/components/chat/DeepSeaBackground";
import { MessageList } from "@/components/chat/MessageList";
import { MainLayout } from "@/components/layout/MainLayout";
import { useActiveArtifacts } from "@/stores/artifactStore";
import { useChatStore } from "@/stores/chatStore";

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
  const showWelcome = messages.length === 0 && !isLoading;
  const [sessionsReady, setSessionsReady] = React.useState(false);
  const [artifactPanelOpen, setArtifactPanelOpen] = React.useState(false);
  const invalidSessionHandledRef = React.useRef<string | null>(null);
  const sessionExists = React.useMemo(() => {
    if (!sessionId) return false;
    return sessions.some((session) => session.id === sessionId);
  }, [sessionId, sessions]);

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

  const latestArtifacts = useActiveArtifacts();

  React.useEffect(() => {
    if (latestArtifacts.artifacts.length > 0 || latestArtifacts.serverArtifacts.length > 0) {
      setArtifactPanelOpen(true);
    }
  }, [latestArtifacts.artifacts.length, latestArtifacts.serverArtifacts.length, latestArtifacts.version]);

  const showArtifactPanel = artifactPanelOpen
    && (latestArtifacts.artifacts.length > 0 || latestArtifacts.serverArtifacts.length > 0);

  return (
    <MainLayout>
      <div className="relative flex h-full">
        <Group id="seahorse-chat-panels">
          <Panel minSize={40}>
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

          {showArtifactPanel && (
            <>
              <Separator className="hidden md:flex w-[3px] items-center justify-center
                bg-[var(--theme-glass-border)] hover:bg-[var(--color-accent-primary)] transition-colors
                cursor-col-resize" />
              <Panel defaultSize={30} minSize={20} maxSize={50} className="hidden md:flex">
                <ArtifactPanel
                  artifacts={latestArtifacts.artifacts}
                  serverArtifacts={latestArtifacts.serverArtifacts}
                  onClose={() => setArtifactPanelOpen(false)}
                />
              </Panel>
            </>
          )}
        </Group>
        {showArtifactPanel && (
          <div
            className="fixed inset-x-0 bottom-0 z-40 h-[62dvh] overflow-hidden rounded-t-lg border-t shadow-[0_-18px_42px_rgba(0,0,0,0.18)] md:hidden"
            style={{
              backgroundColor: "var(--theme-bg-elevated)",
              borderColor: "var(--theme-glass-border)"
            }}
          >
            <ArtifactPanel
              artifacts={latestArtifacts.artifacts}
              serverArtifacts={latestArtifacts.serverArtifacts}
              onClose={() => setArtifactPanelOpen(false)}
            />
          </div>
        )}
      </div>
    </MainLayout>
  );
}
