import type { AiModelConfigItem } from "@/services/aiConfigService";
import type { ModelCandidate } from "@/services/settingsService";

const EMBEDDING_MODEL_CONFIG_KEY = "ai.embedding.model";
const MODEL_REGISTRY_CONFIG_KEY = "ai.models";

interface TenantModelRegistryItem {
  id?: string;
  provider?: string;
  model?: string;
  capability?: string;
  enabled?: boolean;
  dimension?: number | null;
  priority?: number | null;
}

function parseModelRegistry(value?: string | null): TenantModelRegistryItem[] {
  if (!value?.trim()) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function resolveEmbeddingModelCandidates(
  candidates: ModelCandidate[] = [],
  configs: AiModelConfigItem[] = [],
  defaultModel?: string | null,
  tenantId = "default"
): ModelCandidate[] {
  const uniqueMap = new Map<string, ModelCandidate>();

  candidates.forEach((item) => {
    if (!item || item.enabled === false) return;
    const id = (item.id || item.model || "").trim();
    if (!id) return;
    uniqueMap.set(id, { ...item, id });
  });

  configs
    .filter((item) => item.configKey === MODEL_REGISTRY_CONFIG_KEY)
    .filter((item) => !item.tenantId || item.tenantId === tenantId)
    .flatMap((item) => parseModelRegistry(item.configValue))
    .filter((item) => item.enabled !== false)
    .filter((item) => item.capability === "embedding")
    .forEach((item) => {
      const id = (item.id || item.model || "").trim();
      if (!id || uniqueMap.has(id)) return;
      uniqueMap.set(id, {
        id,
        provider: item.provider || "",
        model: item.model || id,
        enabled: true,
        dimension: item.dimension,
        priority: item.priority
      });
    });

  const addFallbackModel = (model?: string | null) => {
    const normalized = (model || "").trim();
    if (!normalized) return;
    const exists = Array.from(uniqueMap.values()).some((item) => {
      return item.id === normalized || item.model === normalized;
    });
    if (exists) return;
    uniqueMap.set(normalized, {
      id: normalized,
      provider: "",
      model: normalized,
      enabled: true
    });
  };

  addFallbackModel(defaultModel);
  addFallbackModel(
    configs.find((item) => item.configKey === EMBEDDING_MODEL_CONFIG_KEY)?.configValue
  );

  return Array.from(uniqueMap.values());
}
