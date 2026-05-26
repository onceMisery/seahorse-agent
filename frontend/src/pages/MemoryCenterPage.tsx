import * as React from "react";
import { Database, Loader2, RefreshCw, ShieldCheck, ShieldOff, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { MainLayout } from "@/components/layout/MainLayout";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  deleteUserMemory,
  getUserMemoryCenter,
  setUserMemoryPrivacyMode
} from "@/services/userMemoryService";
import type { UserMemory, UserMemoryCenterResponse } from "@/types";

function memoryTypeLabel(value?: string | null) {
  if (!value) return "Memory";
  return value.replace(/_/g, " ").toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

function formatUpdatedAt(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString();
}

export function MemoryCenterPage() {
  const [data, setData] = React.useState<UserMemoryCenterResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [refreshing, setRefreshing] = React.useState(false);
  const [updatingPrivacy, setUpdatingPrivacy] = React.useState(false);
  const [deletingId, setDeletingId] = React.useState<string | null>(null);

  const load = React.useCallback(async (silent = false) => {
    if (silent) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    try {
      const next = await getUserMemoryCenter();
      setData(next);
    } catch (error) {
      toast.error((error as Error).message || "Failed to load memories");
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
        current
          ? {
              ...current,
              privacyMode: next.privacyMode
            }
          : current
      );
      toast.success(next.privacyMode ? "Privacy mode enabled" : "Privacy mode disabled");
    } catch (error) {
      toast.error((error as Error).message || "Failed to update privacy mode");
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
          ? {
              ...current,
              memories: current.memories.filter((item) => item.memoryId !== memory.memoryId)
            }
          : current
      );
      toast.success("Memory deleted");
    } catch (error) {
      toast.error((error as Error).message || "Failed to delete memory");
    } finally {
      setDeletingId(null);
    }
  };

  const memories = data?.memories ?? [];

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
                Personal memory
              </div>
              <h1 className="mt-4 text-3xl font-semibold tracking-normal" style={{ color: "var(--theme-text-primary)" }}>
                Memory center
              </h1>
              <p className="mt-2 max-w-2xl text-sm leading-6" style={{ color: "var(--theme-text-secondary)" }}>
                Review long-term memories used by the Web assistant and turn privacy mode on when a session should not
                read or write personal memory.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                variant="outline"
                onClick={() => load(true).catch(() => null)}
                disabled={refreshing || loading}
                className="rounded-xl"
              >
                {refreshing ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
                Refresh
              </Button>
              <Button
                onClick={handleTogglePrivacy}
                disabled={updatingPrivacy || loading}
                className="rounded-xl"
                variant={data?.privacyMode ? "secondary" : "default"}
              >
                {updatingPrivacy ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : data?.privacyMode ? (
                  <ShieldOff className="h-4 w-4" />
                ) : (
                  <ShieldCheck className="h-4 w-4" />
                )}
                {data?.privacyMode ? "Disable privacy" : "Enable privacy"}
              </Button>
            </div>
          </header>

          <section
            className="rounded-2xl p-4"
            style={{
              backgroundColor: "var(--theme-bg-elevated)",
              border: "1px solid var(--theme-glass-border)"
            }}
          >
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-sm font-semibold" style={{ color: "var(--theme-text-primary)" }}>
                  Memory access
                </p>
                <p className="mt-1 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                  {data?.privacyMode
                    ? "Privacy mode is active. Long-term memory should stay out of new context."
                    : "Privacy mode is off. The assistant can use eligible long-term memories."}
                </p>
              </div>
              <Badge variant={data?.privacyMode ? "destructive" : "secondary"}>
                {data?.privacyMode ? "Privacy on" : "Memory on"}
              </Badge>
            </div>
          </section>

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
          ) : memories.length === 0 ? (
            <div
              className="flex min-h-[260px] flex-col items-center justify-center rounded-2xl p-8 text-center"
              style={{
                backgroundColor: "var(--theme-bg-elevated)",
                border: "1px solid var(--theme-glass-border)",
                color: "var(--theme-text-secondary)"
              }}
            >
              <Database className="h-10 w-10" style={{ color: "var(--theme-text-muted)" }} />
              <p className="mt-4 text-base font-semibold" style={{ color: "var(--theme-text-primary)" }}>
                No memories yet
              </p>
              <p className="mt-2 max-w-md text-sm">
                Useful preferences and stable facts will appear here after the assistant stores them.
              </p>
            </div>
          ) : (
            <div className="grid gap-3">
              {memories.map((memory) => (
                <article
                  key={memory.memoryId}
                  className="rounded-2xl p-4"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    border: "1px solid var(--theme-glass-border)"
                  }}
                >
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline">{memoryTypeLabel(memory.memoryType)}</Badge>
                        {memory.sensitivity ? <Badge variant="secondary">{memory.sensitivity}</Badge> : null}
                        {memory.status ? <Badge variant="secondary">{memory.status}</Badge> : null}
                      </div>
                      <p className="mt-3 text-sm leading-6" style={{ color: "var(--theme-text-primary)" }}>
                        {memory.displayText}
                      </p>
                      <p className="mt-3 text-xs" style={{ color: "var(--theme-text-muted)" }}>
                        {formatUpdatedAt(memory.updatedAt)}
                      </p>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="Delete memory"
                      onClick={() => handleDelete(memory)}
                      disabled={deletingId === memory.memoryId}
                      className="h-9 w-9 self-start rounded-xl text-rose-500 hover:text-rose-500"
                    >
                      {deletingId === memory.memoryId ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <Trash2 className="h-4 w-4" />
                      )}
                    </Button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
