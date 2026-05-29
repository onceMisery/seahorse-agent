import { create } from "zustand";

import type { AgentArtifact, ArtifactBlock } from "@/types";

interface ArtifactSnapshot {
  artifacts: ArtifactBlock[];
  serverArtifacts: AgentArtifact[];
  version: number;
}

interface ArtifactStore {
  activeMessageId: string | null;
  snapshots: Record<string, ArtifactSnapshot>;
  upsertMessageArtifacts: (
    messageId: string,
    artifacts?: ArtifactBlock[],
    serverArtifacts?: AgentArtifact[]
  ) => void;
  mergeMessageArtifacts: (
    messageId: string,
    artifacts?: ArtifactBlock[],
    serverArtifacts?: AgentArtifact[]
  ) => ArtifactSnapshot | undefined;
  retargetMessageArtifacts: (fromMessageId: string, toMessageId: string) => void;
  clearMessageArtifacts: (messageId: string) => void;
}

function mergeArtifacts(current: ArtifactBlock[] | undefined, incoming: ArtifactBlock[]) {
  const map = new Map<string, ArtifactBlock>();
  for (const item of current ?? []) map.set(item.id, item);
  for (const item of incoming) {
    const existing = map.get(item.id);
    if (!existing) {
      map.set(item.id, item);
      continue;
    }
    if (item.append) {
      map.set(item.id, {
        ...existing,
        ...item,
        language: existing.language,
        title: existing.title,
        code: `${existing.code ?? ""}${item.code ?? ""}`,
        isComplete: item.isComplete
      });
      continue;
    }
    map.set(item.id, {
      ...existing,
      ...item,
      language: item.language === "javascript" ? existing.language : item.language,
      title: /^Artifact(?: \d+)?$/.test(item.title) ? existing.title : item.title,
      code: item.code || existing.code,
      isComplete: item.isComplete
    });
  }
  return Array.from(map.values()).map(({ append, ...item }) => item);
}

function mergeServerArtifacts(current: AgentArtifact[] | undefined, incoming: AgentArtifact[]) {
  const map = new Map<string, AgentArtifact>();
  for (const item of current ?? []) map.set(item.artifactId, item);
  for (const item of incoming) map.set(item.artifactId, { ...map.get(item.artifactId), ...item });
  return Array.from(map.values());
}

export const useArtifactStore = create<ArtifactStore>((set, get) => ({
  activeMessageId: null,
  snapshots: {},
  upsertMessageArtifacts: (messageId, artifacts = [], serverArtifacts = []) => {
    if (!messageId || (artifacts.length === 0 && serverArtifacts.length === 0)) return;
    set((state) => {
      const previous = state.snapshots[messageId];
      return {
        activeMessageId: messageId,
        snapshots: {
          ...state.snapshots,
          [messageId]: {
            artifacts,
            serverArtifacts,
            version: (previous?.version ?? 0) + 1
          }
        }
      };
    });
  },
  mergeMessageArtifacts: (messageId, artifacts = [], serverArtifacts = []) => {
    if (!messageId || (artifacts.length === 0 && serverArtifacts.length === 0)) return undefined;
    const previous = get().snapshots[messageId];
    const next = {
      artifacts: mergeArtifacts(previous?.artifacts, artifacts),
      serverArtifacts: mergeServerArtifacts(previous?.serverArtifacts, serverArtifacts),
      version: (previous?.version ?? 0) + 1
    };
    set((state) => ({
      activeMessageId: messageId,
      snapshots: {
        ...state.snapshots,
        [messageId]: next
      }
    }));
    return next;
  },
  retargetMessageArtifacts: (fromMessageId, toMessageId) => {
    if (!fromMessageId || !toMessageId || fromMessageId === toMessageId) return;
    set((state) => {
      const previous = state.snapshots[fromMessageId];
      if (!previous) return state;
      const next = { ...state.snapshots };
      delete next[fromMessageId];
      next[toMessageId] = previous;
      return {
        activeMessageId: state.activeMessageId === fromMessageId ? toMessageId : state.activeMessageId,
        snapshots: next
      };
    });
  },
  clearMessageArtifacts: (messageId) => {
    set((state) => {
      if (!state.snapshots[messageId]) return state;
      const next = { ...state.snapshots };
      delete next[messageId];
      return {
        activeMessageId: state.activeMessageId === messageId ? null : state.activeMessageId,
        snapshots: next
      };
    });
  }
}));

export function useActiveArtifacts() {
  return useArtifactStore((state) => {
    const active = state.activeMessageId ? state.snapshots[state.activeMessageId] : undefined;
    return {
      artifacts: active?.artifacts ?? [],
      serverArtifacts: active?.serverArtifacts ?? [],
      version: active?.version ?? 0
    };
  });
}
