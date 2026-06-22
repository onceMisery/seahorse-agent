import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";
import { emptyPage, optionalGet } from "@/services/optionalEndpoint";

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
  subjectType?: string;
  subjectId?: string;
  resource?: string;
  resourceType?: string;
  resourceId?: string;
  action?: string;
  agentId?: string;
  runId?: string;
  result?: string;
  effect?: string;
  denyReason?: string;
  reasonCode?: string;
  matchedRules?: Array<{ ruleId?: string; effect?: string }>;
  decisionTime?: string;
  createdAt?: string;
}

export interface SecretItem {
  secretRef?: string;
  secretId?: string;
  tenantId?: string;
  name?: string;
  secretType?: string;
  type?: string;
  maskedValue?: string;
  maskedHint?: string;
  description?: string;
  status?: string;
  createdAt?: string;
  createTime?: string;
}

export interface CreateSecretPayload {
  tenantId: string;
  secretValue: string;
  metadataJson?: string;
}

export interface QuotaPolicy {
  policyId?: string;
  tenantId?: string;
  scope?: string;
  subjectId?: string;
  status?: string;
  tokenLimit?: number;
  callLimit?: number;
  costLimit?: number;
  warnRatio?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface QuotaPolicyPayload {
  policyId: string;
  tenantId: string;
  scope: string;
  subjectId: string;
  status?: string;
  tokenLimit?: number;
  callLimit?: number;
  costLimit?: number;
  warnRatio?: number;
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
  return optionalGet(
    api.get<PageResult<ResourceAclRule>>("/api/resource-acl-rules", { params, suppressErrorToast: true }),
    emptyPage<ResourceAclRule>(params.current, params.size)
  );
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
  return optionalGet(
    api.get<PageResult<AccessDecision>>("/api/access-decisions", { params, suppressErrorToast: true }),
    emptyPage<AccessDecision>(params.current, params.size)
  );
}

// ── 密钥 ──

export function createSecret(payload: CreateSecretPayload) {
  return api.post<SecretItem, SecretItem>("/api/secrets", payload);
}

// ── 配额策略 ──

export function createQuotaPolicy(payload: QuotaPolicyPayload) {
  return api.post<QuotaPolicy, QuotaPolicy>("/api/quotas/policies", payload);
}

export function disableQuotaPolicy(policyId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/quotas/policies/${encodeURIComponent(policyId)}/disable`
  );
}

export function evaluateQuotaDecision(payload: {
  tenantId?: string;
  agentId?: string;
  userId?: string;
  toolId?: string;
  modelId?: string;
  runId?: string;
  riskLevel?: string;
  tokens?: number;
  calls?: number;
  cost?: number;
}) {
  return api.post<QuotaDecisionEvaluation, QuotaDecisionEvaluation>(
    "/api/quotas/decisions:evaluate",
    payload
  );
}
