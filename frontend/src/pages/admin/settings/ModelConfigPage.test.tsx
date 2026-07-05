import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ModelConfigPage } from "./ModelConfigPage";
import { getAiModelConfigGateResult, getAiModelConfigs } from "@/services/aiConfigService";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

vi.mock("@/utils/storage", () => ({
  storage: {
    getUser: () => ({ tenantId: "tenant-a" })
  }
}));

vi.mock("@/services/aiConfigService", async () => {
  const actual = await vi.importActual<typeof import("@/services/aiConfigService")>("@/services/aiConfigService");
  return {
    ...actual,
    getAiModelConfigGateResult: vi.fn(),
    getAiModelConfigs: vi.fn()
  };
});

describe("ModelConfigPage", () => {
  it("opens model config GateResult evidence from the registry action", async () => {
    const user = userEvent.setup();
    vi.mocked(getAiModelConfigs).mockResolvedValue([
      {
        id: "cfg-1",
        tenantId: "tenant-a",
        configKey: "ai.models",
        configValue: JSON.stringify([
          {
            id: "chat-default",
            capability: "chat",
            provider: "openai-compatible",
            model: "gpt-4.1-mini",
            baseUrl: "https://api.example.test/v1",
            secretRef: "secret_model_provider",
            enabled: true,
            defaultModel: true
          }
        ]),
        displayValue: "[...]",
        configType: "JSON",
        encrypted: false,
        description: "Tenant model registry",
        createdBy: "admin",
        updatedBy: "admin",
        createdAt: "2026-07-06T03:40:00Z",
        updatedAt: "2026-07-06T03:40:00Z"
      }
    ]);
    vi.mocked(getAiModelConfigGateResult).mockResolvedValue({
      subjectType: "MODEL_CONFIG",
      subjectId: "ai.models",
      status: "BLOCKED",
      passed: false,
      blockingCodes: ["MODEL_CONFIG_JSON_INVALID"],
      sourceType: "AI_MODEL_CONFIG",
      sourceId: "cfg-1",
      checkedAt: "2026-07-06T03:50:00Z",
      items: [
        {
          code: "MODEL_CONFIG_JSON",
          status: "FAIL",
          message: "Config value must be valid JSON"
        }
      ]
    });

    render(<ModelConfigPage />);

    await waitFor(() => {
      expect(getAiModelConfigs).toHaveBeenCalledWith({ tenantId: "tenant-a" });
    });
    expect(await screen.findByDisplayValue("chat-default")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Gate/i }));

    expect(getAiModelConfigGateResult).toHaveBeenCalledWith("ai.models", "tenant-a");
    expect(await screen.findByText("Model Config GateResult")).toBeInTheDocument();
    expect(screen.getByText("MODEL_CONFIG:ai.models")).toBeInTheDocument();
    expect(screen.getByText("BLOCKED")).toBeInTheDocument();
    expect(screen.getByText("MODEL_CONFIG_JSON_INVALID")).toBeInTheDocument();
    expect(screen.getByText("AI_MODEL_CONFIG")).toBeInTheDocument();
    expect(screen.getByText("Config value must be valid JSON")).toBeInTheDocument();
  });
});
