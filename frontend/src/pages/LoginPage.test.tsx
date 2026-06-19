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
});
