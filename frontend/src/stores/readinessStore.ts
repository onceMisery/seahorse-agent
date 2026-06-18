import { create } from "zustand";
import { getReadinessSummary, type ReadinessSummary } from "@/services/readinessService";

interface ReadinessStore {
  summary: ReadinessSummary | null;
  isLoading: boolean;
  error: string | null;
  loadSummary: () => Promise<void>;
}

export const useReadinessStore = create<ReadinessStore>((set) => ({
  summary: null,
  isLoading: false,
  error: null,

  loadSummary: async () => {
    set({ isLoading: true, error: null });
    try {
      const summary = await getReadinessSummary();
      set({ summary, isLoading: false });
    } catch (err) {
      set({
        error: err instanceof Error ? err.message : "加载系统状态失败",
        isLoading: false
      });
    }
  }
}));
