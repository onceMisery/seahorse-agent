import { api } from "@/services/api";

export interface BackendFeatureState {
  enabled: boolean;
  visible: boolean;
  reason?: string;
}

export interface BackendFeatureResponse {
  productMode: string;
  features: Record<string, BackendFeatureState>;
}

export function getBackendFeatures() {
  return api.get<BackendFeatureResponse, BackendFeatureResponse>("/api/features");
}
