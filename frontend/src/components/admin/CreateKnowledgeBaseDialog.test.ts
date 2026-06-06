import { describe, expect, it } from "vitest";

import { resolveEmbeddingModelCandidates } from "./CreateKnowledgeBaseDialog";
import type { AiModelConfigItem } from "@/services/aiConfigService";

function configItem(configKey: string, configValue: string, tenantId = "default"): AiModelConfigItem {
  return {
    id: configKey,
    tenantId,
    configKey,
    configValue,
    displayValue: configValue,
    configType: "STRING",
    encrypted: false,
    description: "",
    createdBy: "system",
    updatedBy: "system",
    createdAt: "",
    updatedAt: ""
  };
}

describe("resolveEmbeddingModelCandidates", () => {
  it("uses tenant-scoped embedding models from the model registry config", () => {
    const candidates = resolveEmbeddingModelCandidates(
      [],
      [
        configItem(
          "ai.models",
          JSON.stringify([
            {
              id: "bge-m3",
              provider: "siliconflow",
              model: "BAAI/bge-m3",
              capability: "embedding",
              enabled: true,
              dimension: 1024
            },
            {
              id: "deepseek-chat",
              provider: "siliconflow",
              model: "deepseek-ai/DeepSeek-V3.2",
              capability: "chat",
              enabled: true
            }
          ]),
          "tenant-a"
        ),
        configItem(
          "ai.models",
          JSON.stringify([
            {
              id: "tenant-b-embed",
              provider: "openai",
              model: "text-embedding-3-large",
              capability: "embedding",
              enabled: true
            }
          ]),
          "tenant-b"
        )
      ],
      undefined,
      "tenant-a"
    );

    expect(candidates).toHaveLength(1);
    expect(candidates[0]).toEqual(
      expect.objectContaining({
        id: "bge-m3",
        provider: "siliconflow",
        model: "BAAI/bge-m3",
        dimension: 1024
      })
    );
  });

  it("uses the admin embedding model config when rag settings have no candidates", () => {
    const candidates = resolveEmbeddingModelCandidates(
      [],
      [configItem("ai.embedding.model", "BAAI/bge-m3")],
      undefined,
      "default"
    );

    expect(candidates).toEqual([
      expect.objectContaining({
        id: "BAAI/bge-m3",
        model: "BAAI/bge-m3",
        enabled: true
      })
    ]);
  });
});
