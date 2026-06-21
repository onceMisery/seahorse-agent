import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";

import { LoginPage } from "@/pages/LoginPage";

const loginMock = vi.fn();
const navigateMock = vi.hoisted(() => vi.fn());

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigateMock
  };
});

vi.mock("@/stores/authStore", () => ({
  useAuthStore: () => ({
    login: loginMock,
    isLoading: false
  })
}));

vi.mock("@/components/common/SeahorseLogo", () => ({
  SeahorseLogo: () => <div data-testid="seahorse-logo" />
}));

describe("LoginPage", () => {
  beforeEach(() => {
    loginMock.mockReset();
    navigateMock.mockReset();
    window.history.pushState({}, "", "/login");
  });

  it("prefills the admin login with the current default password", () => {
    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByLabelText("用户名")).toHaveValue("admin");
    expect(screen.getByLabelText("密码")).toHaveValue("admin123");
  });

  it("opens the workspace after login when no redirect is requested", async () => {
    loginMock.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();

    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <LoginPage />
      </MemoryRouter>
    );

    await user.click(screen.getByRole("button", { name: "进入系统" }));

    expect(loginMock).toHaveBeenCalledWith("admin", "admin123");
    expect(navigateMock).toHaveBeenCalledWith("/workspace");
  });

  it("ignores external redirect targets after login", async () => {
    loginMock.mockResolvedValueOnce(undefined);
    window.history.pushState({}, "", "/login?redirect=https%3A%2F%2Fevil.example%2Fadmin");
    const user = userEvent.setup();

    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <LoginPage />
      </MemoryRouter>
    );

    await user.click(screen.getByRole("button", { name: "进入系统" }));

    expect(navigateMock).toHaveBeenCalledWith("/workspace");
  });

  it("opens an internal redirect target after login", async () => {
    loginMock.mockResolvedValueOnce(undefined);
    window.history.pushState({}, "", "/login?redirect=%2Fadmin%2Frun-profiles%3Ftab%3Dactive");
    const user = userEvent.setup();

    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <LoginPage />
      </MemoryRouter>
    );

    await user.click(screen.getByRole("button", { name: "进入系统" }));

    expect(navigateMock).toHaveBeenCalledWith("/admin/run-profiles?tab=active");
  });

  it("keeps the original redirect when auth state changes location during login", async () => {
    loginMock.mockImplementationOnce(async () => {
      window.history.pushState({}, "", "/workspace");
    });
    window.history.pushState({}, "", "/login?redirect=%2Fadmin%2Frun-profiles%3Ffrom%3De2e");
    const user = userEvent.setup();

    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <LoginPage />
      </MemoryRouter>
    );

    await user.click(screen.getByRole("button", { name: "进入系统" }));

    expect(navigateMock).toHaveBeenCalledWith("/admin/run-profiles?from=e2e");
  });
});
