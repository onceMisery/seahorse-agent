import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { Sidebar } from "@/components/layout/Sidebar";

vi.mock("@/components/common/SeahorseLogo", () => ({
  SeahorseLogo: () => <div data-testid="seahorse-logo" />
}));

vi.mock("@/components/common/Loading", () => ({
  Loading: ({ label }: { label: string }) => <div>{label}</div>
}));

vi.mock("@/stores/authStore", () => ({
  useAuthStore: () => ({
    user: { userId: "1", username: "admin", role: "admin" },
    logout: vi.fn()
  })
}));

vi.mock("@/stores/chatStore", () => ({
  useChatStore: () => ({
    sessions: [],
    currentSessionId: null,
    isLoading: false,
    sessionsLoaded: true,
    isCreating: false,
    startNewSessionDraft: vi.fn(),
    deleteSession: vi.fn(),
    renameSession: vi.fn(),
    selectSession: vi.fn(),
    fetchSessions: vi.fn().mockResolvedValue(undefined)
  })
}));

vi.mock("@/stores/themeStore", () => ({
  COLOR_THEMES: {
    marine: { label: "Marine" },
    white: { label: "White" },
    purple: { label: "Purple" },
    emerald: { label: "Emerald" },
    amber: { label: "Amber" },
    deepSea: { label: "Deep Sea" }
  },
  VISIBLE_COLOR_THEME_KEYS: ["marine", "white"],
  useThemeStore: () => ({
    colorTheme: "marine",
    setColorTheme: vi.fn()
  })
}));

describe("Sidebar", () => {
  it("removes the closed sidebar from the accessibility and focus tree", () => {
    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <Sidebar isOpen={false} onClose={() => undefined} />
      </MemoryRouter>
    );

    const sidebar = screen.getByRole("complementary", { hidden: true });
    expect(sidebar).toHaveAttribute("aria-hidden", "true");
    expect(sidebar).toHaveAttribute("inert");
  });
});
