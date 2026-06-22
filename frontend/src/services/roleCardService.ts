import { api } from "@/services/api";

export interface RoleCardVO {
  id: number | string;
  tenantId?: string | null;
  userId?: string | null;
  name: string;
  definition: string;
  avatarRef?: string | null;
  higherPerm?: boolean | number | null;
  enabled?: boolean | number | null;
  shareScope?: "PRIVATE" | "TEAM" | "ORG" | string | null;
  approvalStatus?: "PENDING" | "APPROVED" | "REJECTED" | string | null;
  published?: boolean | number | null;
  assetSource?: "USER" | "SYSTEM" | string | null;
  presetKey?: string | null;
  presetVersion?: number | null;
  readonly?: boolean | number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface RoleCardRequest {
  name: string;
  definition: string;
  higherPerm?: boolean;
  avatarRef?: string | null;
  shareScope?: "PRIVATE" | "TEAM" | "ORG" | string | null;
  approvalStatus?: "PENDING" | "APPROVED" | "REJECTED" | string | null;
  published?: boolean;
}

export async function listRoleCards(): Promise<RoleCardVO[]> {
  return api.get<RoleCardVO[], RoleCardVO[]>("/api/role-cards");
}

export async function createRoleCard(request: RoleCardRequest): Promise<number | string> {
  return api.post<number | string, number | string>("/api/role-cards", request);
}

export async function updateRoleCard(id: number | string, request: RoleCardRequest): Promise<void> {
  return api.put<void, void>(`/api/role-cards/${encodeURIComponent(String(id))}`, request);
}

export async function activateRoleCard(id: number | string): Promise<void> {
  return api.put<void, void>(`/api/role-cards/${encodeURIComponent(String(id))}/activate`);
}

export async function deleteRoleCard(id: number | string): Promise<void> {
  return api.delete<void, void>(`/api/role-cards/${encodeURIComponent(String(id))}`);
}
