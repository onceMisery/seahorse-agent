export const PRODUCT_MODES = {
  CONSUMER_WEB: "consumer-web",
  ENTERPRISE_PLATFORM: "enterprise-platform"
} as const;

export type ProductMode = (typeof PRODUCT_MODES)[keyof typeof PRODUCT_MODES];

export const ADVANCED_ADMIN_FEATURES = {
  // 原有三项
  AI_INFRA_CONSOLE: "aiInfraConsole",
  INTENT_MANAGEMENT: "intentManagement",
  INGESTION_MANAGEMENT: "ingestionManagement",

  // Agent 管理
  AGENT_DEFINITION_MANAGEMENT: "agentDefinitionManagement",
  AGENT_FACTORY_MANAGEMENT: "agentFactoryManagement",
  AGENT_TOOL_BINDING_MANAGEMENT: "agentToolBindingManagement",
  AGENT_RUN_MANAGEMENT: "agentRunManagement",
  AGENT_EVALUATION: "agentEvaluation",

  // 工具管理
  TOOL_CATALOG_MANAGEMENT: "toolCatalogManagement",
  PRODUCTION_GATE: "productionGate",
  AGENT_ROLLOUT_MANAGEMENT: "agentRolloutManagement",

  // 集成
  CONNECTOR_MANAGEMENT: "connectorManagement",
  SECRET_MANAGEMENT: "secretManagement",

  // 安全治理
  RESOURCE_ACL_MANAGEMENT: "resourceAclManagement",
  QUOTA_MANAGEMENT: "quotaManagement",

  // 治理
  SANDBOX: "sandbox",
  MCP_TOOL: "mcpTool",
  MEMORY_GOVERNANCE: "memoryGovernance",
  RAG_EVALUATION: "ragEvaluation",
  METADATA_GOVERNANCE: "metadataGovernance",

  // 可观测
  AUDIT_LOG: "auditLog",
  COST_ANALYTICS: "costAnalytics"
} as const;

export type AdvancedAdminFeature =
  (typeof ADVANCED_ADMIN_FEATURES)[keyof typeof ADVANCED_ADMIN_FEATURES];

export type FeatureState = {
  visible: boolean;
  enabled: boolean;
  reason?: string;
};

function resolveProductMode(value: string | undefined): ProductMode {
  const normalized = (value || PRODUCT_MODES.CONSUMER_WEB).trim().toLowerCase();
  return normalized === PRODUCT_MODES.ENTERPRISE_PLATFORM
    ? PRODUCT_MODES.ENTERPRISE_PLATFORM
    : PRODUCT_MODES.CONSUMER_WEB;
}

const productMode = resolveProductMode(import.meta.env.VITE_SEAHORSE_PRODUCT_MODE);

const explicitAdvancedAdmin = import.meta.env.VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN === "true";

export function isConsumerWebMode() {
  return productMode === PRODUCT_MODES.CONSUMER_WEB;
}

export function isAdvancedAdminEnabled(_feature: AdvancedAdminFeature) {
  return !isConsumerWebMode() && explicitAdvancedAdmin;
}

/**
 * 获取高级功能的完整状态，支持"可用、未启用、无权限、接口缺失"四类状态表达。
 * 在后端尚未提供 /api/features 前，基于本地 env 与产品模式判断。
 */
export function getAdvancedFeatureState(feature: AdvancedAdminFeature): FeatureState {
  if (isConsumerWebMode()) {
    return { visible: false, enabled: false, reason: "当前为消费者模式，此功能不可用" };
  }

  if (!explicitAdvancedAdmin) {
    return { visible: true, enabled: false, reason: "高级管理功能未启用，请联系管理员开启" };
  }

  return { visible: true, enabled: true };
}
