import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AnimatePresence, motion } from "motion/react";

import { ChatInput } from "@/components/chat/ChatInput";
import { DeepSeaBackground } from "@/components/chat/DeepSeaBackground";
import { MessageList } from "@/components/chat/MessageList";
import { WorkspaceInspector } from "@/components/chat/workbench/WorkspaceInspector";
import { ReadinessStatusBar } from "@/components/readiness/ReadinessStatusBar";
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
    isCreating,
    fetchSessions,
    selectSession,
    createSession,
    startNewSessionDraft
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
        startNewSessionDraft();
        navigate("/chat", { replace: true });
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
    if (isCreatingNew || isCreating) {
      return;
    }
    if (!currentSessionId && messages.length === 0) {
      createSession().catch(() => null);
    }
  }, [
    sessionId,
    sessionsReady,
    sessionExists,
    isCreatingNew,
    isCreating,
    currentSessionId,
    messages.length,
    selectSession,
    createSession,
    startNewSessionDraft,
    navigate
  ]);

  React.useEffect(() => {
    if (sessionId && currentSessionId && currentSessionId !== sessionId) {
      navigate(`/chat/${currentSessionId}`, { replace: true });
    }
  }, [currentSessionId, sessionId, navigate]);

  React.useEffect(() => {
    if (!sessionId && sessionsReady && currentSessionId && messages.length === 0 && isCreatingNew) {
      startNewSessionDraft();
    }
  }, [sessionId, sessionsReady, currentSessionId, messages.length, isCreatingNew, startNewSessionDraft]);

  return (
    <MainLayout>
      <div className="relative flex h-full">
        {/* Chat area — always full width */}
        <div className="relative flex h-full min-w-0 flex-1 flex-col">
          <div className="relative z-10 flex justify-end px-4 pt-2">
            <ReadinessStatusBar />
          </div>
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

        {/* Slide-out inspector drawer */}
        <AnimatePresence>
          {inspectorOpen && (
            <>
              {/* Backdrop */}
              <motion.div
                key="inspector-backdrop"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2 }}
                className="fixed inset-0 z-40 bg-black/30 backdrop-blur-[2px]"
                onClick={closeInspector}
              />
              {/* Drawer */}
              <motion.div
                key="inspector-drawer"
                initial={{ x: "100%" }}
                animate={{ x: 0 }}
                exit={{ x: "100%" }}
                transition={{ type: "spring", stiffness: 300, damping: 30 }}
                className="fixed right-0 top-0 z-50 h-full w-[420px] max-w-[85vw] shadow-2xl"
                style={{ background: "var(--sh-workbench-panel)" }}
              >
                <WorkspaceInspector
                  message={activeMessage}
                  open={inspectorOpen}
                  onClose={closeInspector}
                />
              </motion.div>
            </>
          )}
        </AnimatePresence>
      </div>
    </MainLayout>
  );
}
