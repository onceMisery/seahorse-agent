export const PRODUCT_MODES = {
  CONSUMER_WEB: "consumer-web",
  ENTERPRISE_PLATFORM: "enterprise-platform"
} as const;

export type ProductMode = (typeof PRODUCT_MODES)[keyof typeof PRODUCT_MODES];

export const ADVANCED_ADMIN_FEATURES = {
  AI_INFRA_CONSOLE: "aiInfraConsole",
  INTENT_MANAGEMENT: "intentManagement",
  INGESTION_MANAGEMENT: "ingestionManagement"
} as const;

export type AdvancedAdminFeature =
  (typeof ADVANCED_ADMIN_FEATURES)[keyof typeof ADVANCED_ADMIN_FEATURES];

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
