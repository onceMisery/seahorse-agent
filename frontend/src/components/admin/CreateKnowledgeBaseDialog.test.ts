import { describe, expect, it } from "vitest";

import { resolveEmbeddingModelCandidates } from "./CreateKnowledgeBaseDialog";
import type { AiModelConfigItem } from "@/services/aiConfigService";

function configItem(configKey: string, configValue: string): AiModelConfigItem {
  return {
    id: configKey,
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
  it("uses the admin embedding model config when rag settings have no candidates", () => {
    const candidates = resolveEmbeddingModelCandidates([], [
      configItem("ai.embedding.model", "BAAI/bge-m3")
    ]);

    expect(candidates).toEqual([
      expect.objectContaining({
        id: "BAAI/bge-m3",
        model: "BAAI/bge-m3",
        enabled: true
      })
    ]);
  });
});
