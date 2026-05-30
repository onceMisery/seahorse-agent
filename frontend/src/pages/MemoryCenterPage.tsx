import * as React from "react";
import { Database, Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { MainLayout } from "@/components/layout/MainLayout";
import { Button } from "@/components/ui/button";
import { MemoryCard } from "@/components/memory/MemoryCard";
import { MemoryEmptyState } from "@/components/memory/MemoryEmptyState";
import { MemoryPrivacyBanner } from "@/components/memory/MemoryPrivacyBanner";
import { MemoryToolbar } from "@/components/memory/MemoryToolbar";
import {
  deleteUserMemory,
  getUserMemoryCenter,
  setUserMemoryPrivacyMode
} from "@/services/userMemoryService";
import type { UserMemory, UserMemoryCenterResponse } from "@/types";

export function MemoryCenterPage() {
  const [data, setData] = React.useState<UserMemoryCenterResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [refreshing, setRefreshing] = React.useState(false);
  const [updatingPrivacy, setUpdatingPrivacy] = React.useState(false);
  const [deletingId, setDeletingId] = React.useState<string | null>(null);
  const [query, setQuery] = React.useState("");
  const [typeFilter, setTypeFilter] = React.useState("");
  const [sensitivityFilter, setSensitivityFilter] = React.useState("");

  const load = React.useCallback(async (silent = false) => {
    if (silent) {
      setRefreshing(true);
    } else {
      setLoading(true);
      setLoadError(null);
    }
    try {
      const next = await getUserMemoryCenter();
      setData(next);
    } catch (error) {
      const msg = (error as Error).message || "加载记忆失败";
      if (!silent) setLoadError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  React.useEffect(() => {
    load().catch(() => null);
  }, [load]);

  const handleTogglePrivacy = async () => {
    const nextEnabled = !data?.privacyMode;
    setUpdatingPrivacy(true);
    try {
      const next = await setUserMemoryPrivacyMode(nextEnabled);
      setData((current) =>
        current ? { ...current, privacyMode: next.privacyMode } : current
      );
      toast.success(next.privacyMode ? "隐私模式已开启" : "隐私模式已关闭");
    } catch (error) {
      toast.error((error as Error).message || "更新隐私模式失败");
    } finally {
      setUpdatingPrivacy(false);
    }
  };

  const handleDelete = async (memory: UserMemory) => {
    setDeletingId(memory.memoryId);
    try {
      await deleteUserMemory(memory.memoryId);
      setData((current) =>
        current
          ? { ...current, memories: current.memories.filter((item) => item.memoryId !== memory.memoryId) }
          : current
      );
      toast.success("记忆已删除");
    } catch (error) {
      toast.error((error as Error).message || "删除记忆失败");
    } finally {
      setDeletingId(null);
    }
  };

  const memories = data?.memories ?? [];
  const hasFilter = Boolean(query || typeFilter || sensitivityFilter);

  const filteredMemories = React.useMemo(() => {
    return memories.filter((m) => {
      if (query && !m.displayText.toLowerCase().includes(query.toLowerCase())) return false;
      if (typeFilter && m.memoryType !== typeFilter) return false;
      if (sensitivityFilter && m.sensitivity !== sensitivityFilter) return false;
      return true;
    });
  }, [memories, query, typeFilter, sensitivityFilter]);

  return (
    <MainLayout>
      <div className="h-full overflow-y-auto px-6 py-8">
        <div className="mx-auto flex max-w-5xl flex-col gap-6">
          <header className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <div
                className="inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold"
                style={{
                  backgroundColor: "var(--theme-accent-alpha-10)",
                  color: "var(--theme-accent)"
                }}
              >
                <Database className="h-3.5 w-3.5" />
                个人记忆
              </div>
              <h1
                className="mt-4 text-3xl font-semibold tracking-normal"
                style={{ color: "var(--theme-text-primary)" }}
              >
                记忆中心
              </h1>
              <p
                className="mt-2 max-w-2xl text-sm leading-6"
                style={{ color: "var(--theme-text-secondary)" }}
              >
                查看助手保存的长期记忆，开启隐私模式后新对话将不会读取或写入记忆。
              </p>
            </div>
            <Button
              variant="outline"
              onClick={() => load(true).catch(() => null)}
              disabled={refreshing || loading}
              className="rounded-xl"
            >
              {refreshing ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
              刷新
            </Button>
          </header>

          {data && (
            <MemoryPrivacyBanner
              privacyMode={data.privacyMode}
              loading={updatingPrivacy}
              onToggle={handleTogglePrivacy}
            />
          )}

          {!loading && memories.length > 0 && (
            <MemoryToolbar
              query={query}
              type={typeFilter}
              sensitivity={sensitivityFilter}
              onQueryChange={setQuery}
              onTypeChange={setTypeFilter}
              onSensitivityChange={setSensitivityFilter}
              onRefresh={() => load(true).catch(() => null)}
            />
          )}

          {loading ? (
            <div className="grid gap-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <div
                  key={index}
                  className="h-28 animate-pulse rounded-2xl"
                  style={{ backgroundColor: "var(--theme-bg-elevated)" }}
                />
              ))}
            </div>
          ) : loadError ? (
            <div
              className="flex min-h-[200px] flex-col items-center justify-center rounded-2xl p-8 text-center"
              style={{
                backgroundColor: "var(--theme-bg-elevated)",
                border: "1px solid var(--theme-glass-border)"
              }}
            >
              <p className="text-sm font-semibold" style={{ color: "rgb(239,68,68)" }}>
                加载失败
              </p>
              <p className="mt-1 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                {loadError}
              </p>
              <Button
                variant="outline"
                className="mt-4 rounded-xl"
                onClick={() => load().catch(() => null)}
              >
                重试
              </Button>
            </div>
          ) : filteredMemories.length === 0 ? (
            <MemoryEmptyState hasFilter={hasFilter} />
          ) : (
            <div className="grid gap-3">
              {filteredMemories.map((memory) => (
                <MemoryCard
                  key={memory.memoryId}
                  memory={memory}
                  deleting={deletingId === memory.memoryId}
                  onDelete={() => handleDelete(memory)}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
