import { useFeatureStore } from "@/stores/featureStore";

export const PRODUCT_MODES = {
  CONSUMER_WEB: "consumer-web",
  ENTERPRISE_PLATFORM: "enterprise-platform"
} as const;

export type ProductMode = (typeof PRODUCT_MODES)[keyof typeof PRODUCT_MODES];

export const ADVANCED_ADMIN_FEATURES = {
  AI_INFRA_CONSOLE: "LOCAL_AGENT",
  INTENT_MANAGEMENT: "INTENT_TREE_MANAGEMENT",
  INGESTION_MANAGEMENT: "INGESTION_PIPELINE_MANAGEMENT",
  AGENT_DEFINITION_MANAGEMENT: "AGENT_DEFINITION_MANAGEMENT",
  AGENT_FACTORY_MANAGEMENT: "AGENT_FACTORY_MANAGEMENT",
  AGENT_TOOL_BINDING_MANAGEMENT: "AGENT_TOOL_BINDING_MANAGEMENT",
  AGENT_RUN_MANAGEMENT: "AGENT_RUN_MANAGEMENT",
  AGENT_EVALUATION: "AGENT_EVALUATION",
  TOOL_CATALOG_MANAGEMENT: "TOOL_CATALOG_MANAGEMENT",
  PRODUCTION_GATE: "PRODUCTION_GATE",
  AGENT_ROLLOUT_MANAGEMENT: "AGENT_ROLLOUT_MANAGEMENT",
  CONNECTOR_MANAGEMENT: "CONNECTOR_MANAGEMENT",
  SECRET_MANAGEMENT: "SECRET_MANAGEMENT",
  RESOURCE_ACL_MANAGEMENT: "RESOURCE_ACL_MANAGEMENT",
  QUOTA_MANAGEMENT: "QUOTA_MANAGEMENT",
  SANDBOX: "SANDBOX",
  MCP_TOOL: "MCP_TOOL",
  MEMORY_GOVERNANCE: "MEMORY_GOVERNANCE",
  RAG_EVALUATION: "RAG_EVALUATION",
  METADATA_GOVERNANCE: "METADATA_GOVERNANCE",
  AUDIT_LOG: "AUDIT_LOG",
  COST_ANALYTICS: "COST_ANALYTICS"
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

export function isAdvancedAdminEnabled(feature: AdvancedAdminFeature) {
  const capabilities = useFeatureStore.getState().capabilities;
  if (capabilities) {
    return Boolean(capabilities.features?.[feature]?.enabled);
  }
  return !isConsumerWebMode() && explicitAdvancedAdmin;
}

export function getAdvancedFeatureState(feature: AdvancedAdminFeature): FeatureState {
  const capabilities = useFeatureStore.getState().capabilities;
  if (capabilities) {
    return capabilities.features?.[feature] ?? {
      visible: false,
      enabled: false,
      reason: "后端未返回该功能能力"
    };
  }

  if (isConsumerWebMode()) {
    return { visible: false, enabled: false, reason: "当前为消费端模式，此功能不可用" };
  }

  if (!explicitAdvancedAdmin) {
    return { visible: true, enabled: false, reason: "高级管理功能未启用，请联系管理员开启" };
  }

  return { visible: true, enabled: true };
}
