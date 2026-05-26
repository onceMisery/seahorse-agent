import { api } from "@/services/api";
import type { UserMemoryCenterResponse } from "@/types";

export async function getUserMemoryCenter(limit = 50): Promise<UserMemoryCenterResponse> {
  return api.get<UserMemoryCenterResponse, UserMemoryCenterResponse>("/api/me/memories", {
    params: { limit }
  });
}

export async function deleteUserMemory(memoryId: string): Promise<void> {
  await api.delete(`/api/me/memories/${encodeURIComponent(memoryId)}`);
}

export async function setUserMemoryPrivacyMode(enabled: boolean): Promise<{
  userId: string;
  privacyMode: boolean;
  updatedAt?: string | null;
}> {
  return api.post("/api/me/memory-settings/privacy-mode", { enabled });
}
