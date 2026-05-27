import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ArtifactPanel } from "@/components/chat/ArtifactPanel";
import { ChatInput } from "@/components/chat/ChatInput";
import { DeepSeaBackground } from "@/components/chat/DeepSeaBackground";
import { MessageList } from "@/components/chat/MessageList";
import { MainLayout } from "@/components/layout/MainLayout";
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

  const latestArtifacts = React.useMemo(() => {
    const last = [...messages].reverse().find((m) => m.role === "assistant" && (m.artifacts?.length || m.serverArtifacts?.length));
    return { artifacts: last?.artifacts ?? [], serverArtifacts: last?.serverArtifacts ?? [] };
  }, [messages]);

  React.useEffect(() => {
    if (latestArtifacts.artifacts.length > 0 || latestArtifacts.serverArtifacts.length > 0) {
      setArtifactPanelOpen(true);
    }
  }, [latestArtifacts]);

  return (
    <MainLayout>
      <div className="relative flex h-full">
        {/* 聊天主区域 */}
        <div className="relative flex flex-1 min-w-0 flex-col">
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

        {/* 产物侧边面板 */}
        {artifactPanelOpen && (latestArtifacts.artifacts.length > 0 || latestArtifacts.serverArtifacts.length > 0) && (
          <div className="hidden md:flex w-[400px] max-w-[40vw] shrink-0"
            style={{ borderLeft: "1px solid var(--theme-glass-border)" }}>
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
