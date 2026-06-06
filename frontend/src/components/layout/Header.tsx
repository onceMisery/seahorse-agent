import * as React from "react";
import { Github, Menu } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useChatStore } from "@/stores/chatStore";

interface HeaderProps {
  onToggleSidebar: () => void;
}

export function Header({ onToggleSidebar }: HeaderProps) {
  const { currentSessionId, sessions } = useChatStore();
  const currentSession = React.useMemo(
    () => sessions.find((session) => session.id === currentSessionId),
    [sessions, currentSessionId]
  );

  return (
    <header className="sticky top-0 z-20" style={{ backgroundColor: "transparent" }}>
      <div className="flex h-16 items-center justify-between px-6">
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            onClick={onToggleSidebar}
            aria-label="切换侧边栏"
            className="lg:hidden"
            style={{ color: "var(--theme-text-secondary)" }}
          >
            <Menu className="h-5 w-5" />
          </Button>
          <p className="text-base font-medium" style={{ color: "var(--theme-text-primary)" }}>
            {currentSession?.title || "新对话"}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <a
            href="https://github.com/onceMisery/seahorse-agent"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-2 rounded-xl px-3 py-1.5 text-sm transition glass-hover"
            style={{ color: "var(--theme-text-secondary)" }}
            aria-label="打开 GitHub 仓库"
          >
            <Github className="h-4 w-4" />
            <span className="font-medium">GitHub</span>
          </a>
        </div>
      </div>
    </header>
  );
}
