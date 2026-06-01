import { create } from "zustand";

import {
  getBackendFeatures,
  type BackendFeatureResponse,
  type BackendFeatureState
} from "@/services/featureService";

interface FeatureStore {
  capabilities: BackendFeatureResponse | null;
  isLoading: boolean;
  error: string | null;
  loadCapabilities: () => Promise<void>;
  getFeatureState: (feature: string) => BackendFeatureState;
}

const disabled: BackendFeatureState = {
  enabled: false,
  visible: false,
  reason: "功能未启用"
};

export const useFeatureStore = create<FeatureStore>((set, get) => ({
  capabilities: null,
  isLoading: false,
  error: null,
  loadCapabilities: async () => {
    set({ isLoading: true, error: null });
    try {
      const capabilities = await getBackendFeatures();
      set({ capabilities });
    } catch (error) {
      set({ error: error instanceof Error ? error.message : "能力配置加载失败" });
    } finally {
      set({ isLoading: false });
    }
  },
  getFeatureState: (feature) => {
    return get().capabilities?.features?.[feature] ?? disabled;
  }
}));
