import { create } from "zustand";

export type WorkbenchTab =
  | "artifacts"
  | "timeline"
  | "context"
  | "sources"
  | "approvals"
  | "tools"
  | "skills"
  | "cost"
  | "memory"
  | "ui";

interface WorkbenchState {
  activeMessageId: string | null;
  activeTab: WorkbenchTab;
  inspectorOpen: boolean;
  openInspector: (messageId: string, tab?: WorkbenchTab) => void;
  closeInspector: () => void;
  setActiveTab: (tab: WorkbenchTab) => void;
  resetWorkbench: () => void;
}

export const useWorkbenchStore = create<WorkbenchState>((set, get) => ({
  activeMessageId: null,
  activeTab: "timeline",
  inspectorOpen: false,
  openInspector: (messageId, tab) => {
    set({
      activeMessageId: messageId,
      activeTab: tab ?? get().activeTab,
      inspectorOpen: true
    });
  },
  closeInspector: () => set({ inspectorOpen: false }),
  setActiveTab: (tab) => set({ activeTab: tab, inspectorOpen: true }),
  resetWorkbench: () => set({ activeMessageId: null, activeTab: "timeline", inspectorOpen: false })
}));
