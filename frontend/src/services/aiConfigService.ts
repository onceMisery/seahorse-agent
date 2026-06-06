import { api } from "@/services/api";

export interface AiModelConfigItem {
  id: string;
  tenantId?: string;
  configKey: string;
  configValue: string;
  displayValue: string;
  configType: string;
  encrypted: boolean;
  description: string;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export async function getAiModelConfigs(params?: { tenantId?: string }): Promise<AiModelConfigItem[]> {
  return api.get<AiModelConfigItem[], AiModelConfigItem[]>("/admin/ai-config", { params });
}

export async function updateAiModelConfig(key: string, value: string, tenantId?: string): Promise<void> {
  return api.put(`/admin/ai-config/${key}`, { value, tenantId });
}

export async function createAiModelConfig(config: {
  tenantId?: string;
  configKey: string;
  configValue: string;
  configType?: string;
  encrypted?: boolean;
  description?: string;
}): Promise<AiModelConfigItem> {
  return api.post<AiModelConfigItem, AiModelConfigItem>("/admin/ai-config", config);
}

export async function deleteAiModelConfig(key: string, tenantId?: string): Promise<void> {
  return api.delete(`/admin/ai-config/${key}`, { params: { tenantId } });
}
