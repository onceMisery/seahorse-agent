import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";

import { LoginPage } from "@/pages/LoginPage";

const loginMock = vi.fn();

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
  it("prefills the admin login with the current default password", () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByLabelText("用户名")).toHaveValue("admin");
    expect(screen.getByLabelText("密码")).toHaveValue("admin123");
  });
});
