import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";

// ── 类型定义 ──

export interface ResourceAclRule {
  ruleId?: string;
  tenantId?: string;
  scope?: string;
  resource?: string;
  principal?: string;
  effect?: string;
  priority?: number;
  reason?: string;
  expiresAt?: string;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AclImportDryRunResult {
  added?: number;
  skipped?: number;
  conflicts?: number;
  errors?: Array<{ line?: number; message?: string }>;
}

export interface AccessDecision {
  decisionId?: string;
  tenantId?: string;
  subject?: string;
  resource?: string;
  action?: string;
  agentId?: string;
  runId?: string;
  result?: string;
  denyReason?: string;
  matchedRules?: Array<{ ruleId?: string; effect?: string }>;
  decisionTime?: string;
}

export interface SecretItem {
  secretId?: string;
  name?: string;
  type?: string;
  maskedValue?: string;
  description?: string;
  createTime?: string;
}

export interface QuotaPolicy {
  policyId?: string;
  name?: string;
  tenantId?: string;
  scope?: string;
  resource?: string;
  limit?: number;
  unit?: string;
  effect?: string;
  status?: string;
  createTime?: string;
}

export interface QuotaDecisionEvaluation {
  result?: string;
  matchedPolicies?: Array<{ policyId?: string; name?: string }>;
  reason?: string;
}

// ── 资源 ACL ──

export function createAclRule(payload: Omit<ResourceAclRule, "ruleId" | "createTime" | "updateTime">) {
  return api.post<ResourceAclRule, ResourceAclRule>("/api/resource-acl-rules", payload);
}

export function listAclRules(params: {
  current?: number;
  size?: number;
  tenantId?: string;
  scope?: string;
  resource?: string;
  principal?: string;
  effect?: string;
  status?: string;
}) {
  return api.get<PageResult<ResourceAclRule>>("/api/resource-acl-rules", { params });
}

export function dryRunImportAclRules(payload: Record<string, unknown>) {
  return api.post<AclImportDryRunResult, AclImportDryRunResult>(
    "/api/resource-acl-rules:dry-run-import",
    payload
  );
}

export function importAclRules(payload: Record<string, unknown>) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    "/api/resource-acl-rules:import",
    payload
  );
}

export function disableAclRule(ruleId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/resource-acl-rules/${encodeURIComponent(ruleId)}/disable`
  );
}

// ── 访问决策 ──

export function listAccessDecisions(params: {
  current?: number;
  size?: number;
  tenantId?: string;
  subject?: string;
  resource?: string;
  action?: string;
  agentId?: string;
  runId?: string;
}) {
  return api.get<PageResult<AccessDecision>>("/api/access-decisions", { params });
}

// ── 密钥 ──

export function createSecret(payload: { name: string; type: string; value: string; description?: string }) {
  return api.post<Record<string, unknown>, Record<string, unknown>>("/api/secrets", payload);
}

export function listSecrets(params?: { current?: number; size?: number; keyword?: string }) {
  return api.get<PageResult<SecretItem>>("/api/secrets", { params });
}

// ── 配额策略 ──

export function createQuotaPolicy(payload: Omit<QuotaPolicy, "policyId" | "createTime">) {
  return api.post<QuotaPolicy, QuotaPolicy>("/api/quotas/policies", payload);
}

export function disableQuotaPolicy(policyId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/quotas/policies/${encodeURIComponent(policyId)}/disable`
  );
}

export function evaluateQuotaDecision(payload: {
  tenantId?: string;
  userId?: string;
  agentId?: string;
  resource?: string;
  cost?: number;
}) {
  return api.post<QuotaDecisionEvaluation, QuotaDecisionEvaluation>(
    "/api/quotas/decisions:evaluate",
    payload
  );
}
