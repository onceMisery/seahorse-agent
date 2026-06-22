import { api } from "@/services/api";
import { optionalGet } from "@/services/optionalEndpoint";

export interface TenantInfo {
  tenantId: string;
  ownerEmail: string;
  status: string;
  planCode: string;
  createdAt: string;
  userCount: number;
  agentCount: number;
  kbCount: number;
}

export interface TenantDetail extends TenantInfo {
  subscription?: { planCode: string; status: string; expiresAt: string };
  resourceSummary: { kbCount: number; agentCount: number; userCount: number; storageUsed: number };
}

export interface AuditLogEntry {
  id: number;
  tenantId: string;
  operator: string;
  action: string;
  resourceType: string;
  resourceId: string;
  detail: string;
  ipAddress: string;
  createdAt: string;
}

export interface TenantUserInfo {
  userId: number;
  username: string;
  email: string;
  role: string;
  status: string;
  createTime: string;
}

// Tenant management
export function listTenants(params: { page?: number; size?: number; status?: string }) {
  return api.get<TenantInfo[], TenantInfo[]>("/api/admin/tenants", { params });
}

export function getTenantDetail(tenantId: string) {
  return api.get<TenantDetail, TenantDetail>(`/api/admin/tenants/${encodeURIComponent(tenantId)}`);
}

export function suspendTenant(tenantId: string) {
  return api.put<unknown, unknown>(`/api/admin/tenants/${encodeURIComponent(tenantId)}/suspend`);
}

export function deleteTenant(tenantId: string, cascade: boolean) {
  return api.delete<unknown, unknown>(`/api/admin/tenants/${encodeURIComponent(tenantId)}`, { params: { cascade } });
}

export function listTenantUsers(tenantId: string, params: { page?: number; size?: number }) {
  return api.get<TenantUserInfo[], TenantUserInfo[]>(`/api/admin/tenants/${encodeURIComponent(tenantId)}/users`, { params });
}

// User management
export function banUser(userId: number) {
  return api.put<unknown, unknown>(`/api/admin/users/${userId}/ban`);
}

export function resetPassword(userId: number) {
  return api.put<unknown, unknown>(`/api/admin/users/${userId}/reset-password`);
}

export function forceLogout(userId: number) {
  return api.post<unknown, unknown>(`/api/admin/users/${userId}/force-logout`);
}

// Audit logs
export function queryAuditLogs(params: { tenantId?: string; action?: string; startTime?: string; endTime?: string; page?: number; size?: number }) {
  return optionalGet(
    api.get<AuditLogEntry[], AuditLogEntry[]>("/api/admin/audit-logs", { params, suppressErrorToast: true }),
    []
  );
}
