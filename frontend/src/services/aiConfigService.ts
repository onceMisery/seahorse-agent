import { api } from "@/services/api";

export interface AiModelConfigItem {
  id: string;
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

export async function getAiModelConfigs(): Promise<AiModelConfigItem[]> {
  return api.get<AiModelConfigItem[], AiModelConfigItem[]>("/admin/ai-config");
}

export async function updateAiModelConfig(key: string, value: string): Promise<void> {
  return api.put(`/admin/ai-config/${key}`, { value });
}

export async function createAiModelConfig(config: {
  configKey: string;
  configValue: string;
  configType?: string;
  encrypted?: boolean;
  description?: string;
}): Promise<AiModelConfigItem> {
  return api.post<AiModelConfigItem, AiModelConfigItem>("/admin/ai-config", config);
}

export async function deleteAiModelConfig(key: string): Promise<void> {
  return api.delete(`/admin/ai-config/${key}`);
}
