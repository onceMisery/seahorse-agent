import { useEffect, useState } from "react";
import { Activity, Download, FolderX, Gauge, History, Info, Play, RefreshCw, Save, Server, ShieldCheck, Square, TimerReset, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { getAdvancedFeatureState, ADVANCED_ADMIN_FEATURES } from "@/config/productMode";
import { FeatureUnavailableState } from "@/components/common/FeatureUnavailableState";
import {
  createSandboxSession,
  executeInSandbox,
  closeSandboxSession,
  downloadSandboxArtifact,
  getSandboxArtifact,
  listSandboxSessions,
  listSandboxExecutions,
  listSandboxArtifacts,
  sweepExpiredSandboxSessions,
  sweepOrphanedSandboxRuntimeResources,
  getSandboxRuntimeHealth,
  getSandboxRuntimeProfiles,
  upsertSandboxRuntimeProfilePolicy,
  reapOrphanedSandboxRuntimeContainers,
  currentSandboxTenantId,
  type SandboxSession,
  type SandboxExecution,
  type SandboxExecutionResult,
  type SandboxArtifact,
  type SandboxArtifactDetail,
  type SandboxRuntimeHealth,
  type SandboxRuntimeProfile,
  type SandboxRuntimeProfilesResponse
} from "@/services/sandboxService";
import { getErrorMessage } from "@/utils/error";

function isTerminalSessionStatus(status?: string) {
  return ["CANCELLED", "FAILED", "SUCCEEDED", "TIMED_OUT", "CLOSED"].includes(status || "");
}

function executionBadgeVariant(status?: string): "default" | "secondary" | "destructive" {
  if (status === "SUCCEEDED") return "default";
  if (status === "FAILED" || status === "CANCELLED") return "destructive";
  return "secondary";
}

function artifactBadgeVariant(status?: string): "default" | "secondary" | "destructive" {
  if (status === "CLEAN" || status === "REDACTED") return "default";
  if (status === "BLOCKED") return "destructive";
  return "secondary";
}

function formatTimestamp(value?: string) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function artifactDisplayName(artifact?: SandboxArtifact | SandboxArtifactDetail | null) {
  return artifact?.name || artifact?.filename || artifact?.artifactId || "-";
}

function runtimeHealthBadgeVariant(status?: string): "default" | "secondary" | "destructive" {
  if (status === "HEALTHY") return "default";
  if (status === "UNAVAILABLE" || status === "UNSUPPORTED") return "destructive";
  return "secondary";
}

function runtimeProfileBadgeVariant(status?: string): "default" | "secondary" | "destructive" {
  if (status === "SUPPORTED") return "default";
  if (status === "BLOCKED") return "destructive";
  return "secondary";
}

function formatDurationSeconds(value?: number) {
  if (!value || value < 0) return "-";
  if (value % 3600 === 0) return `${value / 3600}h`;
  if (value % 60 === 0) return `${value / 60}m`;
  return `${value}s`;
}

function formatRuntimeCapacity(health?: SandboxRuntimeHealth | null) {
  if (!health) return "-";
  const limit = health.activeSessionLimit ?? 0;
  const active = health.activeSessionCount ?? 0;
  if (limit <= 0) return health.capacityStatus || "UNBOUNDED";
  return `${health.capacityStatus || "UNKNOWN"} ${active}/${limit}`;
}

function RuntimeGovernancePanel({
  health,
  profiles,
  loading,
  error,
  onRefresh,
  onSavePolicy,
  savingProfileRuntimeType
}: {
  health: SandboxRuntimeHealth | null;
  profiles: SandboxRuntimeProfilesResponse | null;
  loading: boolean;
  error: string | null;
  onRefresh: () => void;
  onSavePolicy: (profile: SandboxRuntimeProfile, ttlSeconds: number, status: string) => void;
  savingProfileRuntimeType: string | null;
}) {
  const profileRows = profiles?.profiles || [];
  const checkedAt = health?.checkedAt ? formatTimestamp(health.checkedAt) : "-";
  const [ttlDrafts, setTtlDrafts] = useState<Record<string, string>>({});
  const [statusDrafts, setStatusDrafts] = useState<Record<string, string>>({});

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between gap-2">
          <span className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4" />
            Runtime governance
          </span>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8"
            title="Refresh runtime governance"
            disabled={loading}
            onClick={onRefresh}
          >
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {error && (
          <div className="rounded border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </div>
        )}

        {!error && loading && !health && !profiles && (
          <div className="grid gap-3 sm:grid-cols-3">
            {[0, 1, 2].map((item) => (
              <div key={item} className="h-20 animate-pulse rounded border border-slate-100 bg-slate-50" />
            ))}
          </div>
        )}

        {!loading && !error && !health && !profiles && (
          <div className="text-sm text-muted-foreground">No runtime governance data</div>
        )}

        {(health || profiles) && (
          <>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded border border-slate-100 bg-slate-50 p-3">
                <div className="mb-2 flex items-center gap-2 text-xs uppercase text-muted-foreground">
                  <Server className="h-3.5 w-3.5" />
                  Runtime
                </div>
                <Badge variant={runtimeHealthBadgeVariant(health?.status)}>
                  {health?.status || "UNKNOWN"}
                </Badge>
                <div className="mt-2 truncate font-mono text-xs text-slate-600">
                  {health?.runtime || "-"} / {health?.engine || "-"}
                </div>
                <div className="mt-1 text-xs text-muted-foreground">{checkedAt}</div>
              </div>

              <div className="rounded border border-slate-100 bg-slate-50 p-3">
                <div className="mb-2 flex items-center gap-2 text-xs uppercase text-muted-foreground">
                  <Gauge className="h-3.5 w-3.5" />
                  Capacity
                </div>
                <div className="font-mono text-sm text-slate-700">{formatRuntimeCapacity(health)}</div>
                <div className="mt-1 text-xs text-muted-foreground">
                  Remaining: {health?.activeSessionRemaining ?? "-"}
                </div>
                <div className="mt-1 text-xs text-muted-foreground">
                  Containers: {health?.inspectedContainerCount ?? 0} / orphan {health?.orphanContainerCount ?? 0}
                </div>
              </div>

              <div className="rounded border border-slate-100 bg-slate-50 p-3">
                <div className="mb-2 flex items-center gap-2 text-xs uppercase text-muted-foreground">
                  <Activity className="h-3.5 w-3.5" />
                  Policy
                </div>
                <div className="font-mono text-sm text-slate-700">
                  {profiles?.defaultNetworkPolicy || "DENY_ALL"}
                </div>
                <div className="mt-1 text-xs text-muted-foreground">
                  TTL: {formatDurationSeconds(profiles?.defaultTtlSeconds)}
                </div>
                <div className="mt-1 text-xs text-muted-foreground">
                  Profiles: {profileRows.length || "-"}
                </div>
              </div>
            </div>

            <div className="space-y-2">
              {profileRows.map((profile) => (
                <div
                  key={profile.runtimeType || profile.profileId}
                  className="grid gap-3 rounded border border-slate-100 bg-white p-3 text-sm sm:grid-cols-[minmax(0,1fr)_auto_auto_auto_auto]"
                >
                  <div className="min-w-0">
                    <div className="truncate font-mono text-xs text-slate-600">
                      {profile.runtimeType || "UNKNOWN"}
                    </div>
                    <div className="truncate text-sm font-medium text-slate-800">
                      {profile.profileId || "-"}
                    </div>
                    <div className="mt-1 truncate font-mono text-xs text-muted-foreground">
                      TTL {profile.sessionTtlSeconds || profiles?.defaultTtlSeconds || 3600}s
                    </div>
                  </div>
                  <Badge variant={runtimeProfileBadgeVariant(profile.status)}>
                    {profile.status || "UNKNOWN"}
                  </Badge>
                  <Badge variant={profile.networkAllowed ? "destructive" : "secondary"}>
                    {profile.networkAllowed ? "NETWORK" : "NO NETWORK"}
                  </Badge>
                  <select
                    className="h-8 rounded border border-slate-200 bg-white px-2 text-xs"
                    value={statusDrafts[profile.runtimeType || ""] || profile.policyStatus || "ACTIVE"}
                    onChange={(event) =>
                      setStatusDrafts((prev) => ({
                        ...prev,
                        [profile.runtimeType || ""]: event.target.value
                      }))
                    }
                  >
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="DISABLED">DISABLED</option>
                  </select>
                  <div className="flex items-center gap-1">
                    <Input
                      type="number"
                      min={60}
                      max={7200}
                      step={60}
                      className="h-8 w-24 font-mono text-xs"
                      value={
                        ttlDrafts[profile.runtimeType || ""]
                          ?? String(profile.sessionTtlSeconds || profiles?.defaultTtlSeconds || 3600)
                      }
                      onChange={(event) =>
                        setTtlDrafts((prev) => ({
                          ...prev,
                          [profile.runtimeType || ""]: event.target.value
                        }))
                      }
                    />
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8"
                      title="Save runtime profile policy"
                      disabled={!profile.runtimeType || savingProfileRuntimeType === profile.runtimeType}
                      onClick={() => {
                        const key = profile.runtimeType || "";
                        const ttlSeconds = Number(ttlDrafts[key] ?? profile.sessionTtlSeconds ?? profiles?.defaultTtlSeconds ?? 3600);
                        const status = statusDrafts[key] || profile.policyStatus || "ACTIVE";
                        onSavePolicy(profile, ttlSeconds, status);
                      }}
                    >
                      <Save className={`h-4 w-4 ${savingProfileRuntimeType === profile.runtimeType ? "animate-pulse" : ""}`} />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

export function SandboxPage() {
  const featureState = getAdvancedFeatureState(ADVANCED_ADMIN_FEATURES.SANDBOX);

  const [session, setSession] = useState<SandboxSession | null>(null);
  const [toolId, setToolId] = useState("");
  const [argsJson, setArgsJson] = useState("{}");
  const [executing, setExecuting] = useState(false);
  const [closing, setClosing] = useState(false);
  const [lastResult, setLastResult] = useState<SandboxExecutionResult | null>(null);
  const [executions, setExecutions] = useState<SandboxExecution[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [artifacts, setArtifacts] = useState<SandboxArtifact[]>([]);
  const [selectedArtifactId, setSelectedArtifactId] = useState<string | null>(null);
  const [artifactDetail, setArtifactDetail] = useState<SandboxArtifactDetail | null>(null);
  const [loadingArtifactDetailId, setLoadingArtifactDetailId] = useState<string | null>(null);
  const [downloadingArtifactId, setDownloadingArtifactId] = useState<string | null>(null);
  const [sessions, setSessions] = useState<SandboxSession[]>([]);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [sweepingExpiredSessions, setSweepingExpiredSessions] = useState(false);
  const [sweepingOrphanedRuntimeResources, setSweepingOrphanedRuntimeResources] = useState(false);
  const [checkingRuntimeHealth, setCheckingRuntimeHealth] = useState(false);
  const [reapingOrphanedRuntimeContainers, setReapingOrphanedRuntimeContainers] = useState(false);
  const [runtimeHealth, setRuntimeHealth] = useState<SandboxRuntimeHealth | null>(null);
  const [runtimeProfiles, setRuntimeProfiles] = useState<SandboxRuntimeProfilesResponse | null>(null);
  const [loadingRuntimeGovernance, setLoadingRuntimeGovernance] = useState(false);
  const [runtimeGovernanceError, setRuntimeGovernanceError] = useState<string | null>(null);
  const [savingProfileRuntimeType, setSavingProfileRuntimeType] = useState<string | null>(null);

  const clearArtifactSelection = () => {
    setSelectedArtifactId(null);
    setArtifactDetail(null);
  };

  const refreshSessions = async () => {
    try {
      setLoadingSessions(true);
      const data = await listSandboxSessions();
      setSessions(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载沙箱会话失败"));
      console.error(error);
    } finally {
      setLoadingSessions(false);
    }
  };

  const loadSessionData = async (selected: SandboxSession) => {
    if (!selected.sessionId) return;
    try {
      setLoadingHistory(true);
      const [history, arts] = await Promise.all([
        listSandboxExecutions(selected.sessionId),
        listSandboxArtifacts(selected.sessionId)
      ]);
      setExecutions(history || []);
      setArtifacts(arts || []);
      clearArtifactSelection();
    } catch (error) {
      toast.error(getErrorMessage(error, "加载沙箱会话数据失败"));
      console.error(error);
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleSelectSession = async (selected: SandboxSession) => {
    setSession(selected);
    setLastResult(null);
    await loadSessionData(selected);
  };

  const refreshRuntimeGovernance = async (showToast = false) => {
    try {
      setLoadingRuntimeGovernance(true);
      setRuntimeGovernanceError(null);
      const tenantId = currentSandboxTenantId();
      const [health, profiles] = await Promise.all([
        getSandboxRuntimeHealth(),
        getSandboxRuntimeProfiles(tenantId)
      ]);
      setRuntimeHealth(health || null);
      setRuntimeProfiles(profiles || null);
      if (showToast) {
        toast.success(`Runtime ${health?.status || "UNKNOWN"} / ${profiles?.defaultNetworkPolicy || "DENY_ALL"}`);
      }
    } catch (error) {
      const message = getErrorMessage(error, "Runtime governance load failed");
      setRuntimeGovernanceError(message);
      if (showToast) {
        toast.error(message);
      }
      console.error(error);
    } finally {
      setLoadingRuntimeGovernance(false);
    }
  };

  useEffect(() => {
    if (featureState.enabled) {
      void refreshSessions();
      void refreshRuntimeGovernance();
    }
  }, [featureState.enabled]);

  const refreshExecutions = async (sessionId: string) => {
    try {
      setLoadingHistory(true);
      const data = await listSandboxExecutions(sessionId);
      setExecutions(data || []);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载执行历史失败"));
      console.error(error);
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleSelectArtifact = async (artifactId?: string) => {
    if (!artifactId) return;
    setSelectedArtifactId(artifactId);
    setLoadingArtifactDetailId(artifactId);
    try {
      const detail = await getSandboxArtifact(artifactId);
      setArtifactDetail(detail || null);
    } catch (error) {
      toast.error(getErrorMessage(error, "Load artifact detail failed"));
      console.error(error);
    } finally {
      setLoadingArtifactDetailId(null);
    }
  };

  const handleDownloadArtifact = async (artifact: SandboxArtifact | SandboxArtifactDetail) => {
    const artifactId = artifact.artifactId;
    if (!artifactId) return;
    setDownloadingArtifactId(artifactId);
    try {
      let detail = artifactDetail?.artifactId === artifactId ? artifactDetail : null;
      if (!detail) {
        detail = await getSandboxArtifact(artifactId);
        setSelectedArtifactId(artifactId);
        setArtifactDetail(detail || null);
      }
      if (detail?.downloadable === false) {
        toast.error(detail.downloadBlockedReason || "Artifact download is blocked");
        return;
      }
      const blob = (await downloadSandboxArtifact(artifactId)) as Blob;
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = detail?.filename || artifactDisplayName(artifact);
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      toast.error(getErrorMessage(error, "Download artifact failed"));
      console.error(error);
    } finally {
      setDownloadingArtifactId(null);
    }
  };

  const handleCreateSession = async () => {
    try {
      const data = await createSandboxSession();
      setSession(data);
      setLastResult(null);
      setArtifacts([]);
      setExecutions([]);
      clearArtifactSelection();
      if (data.sessionId) {
        await refreshExecutions(data.sessionId);
      }
      await refreshSessions();
      if (data.status === "FAILED") {
        toast.error(`Sandbox session rejected: ${data.reasonCode || "UNKNOWN"}`);
      } else {
        toast.success("沙箱会话已创建");
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "创建沙箱会话失败"));
      console.error(error);
    }
  };

  const handleExecute = async () => {
    if (!session?.sessionId) {
      toast.error("请先创建沙箱会话");
      return;
    }

    let args;
    try {
      args = JSON.parse(argsJson);
    } catch {
      toast.error("参数 JSON 格式不合法");
      return;
    }

    try {
      setExecuting(true);
      const result = await executeInSandbox(session.sessionId, {
        toolId: toolId || undefined,
        argumentsJson: JSON.stringify(args)
      });
      setLastResult(result);
      toast.success("执行完成");

      const [arts, history] = await Promise.all([
        listSandboxArtifacts(session.sessionId),
        listSandboxExecutions(session.sessionId)
      ]);
      setArtifacts(arts || []);
      setExecutions(history || []);
      clearArtifactSelection();
      await refreshSessions();
    } catch (error) {
      toast.error(getErrorMessage(error, "执行失败"));
      console.error(error);
    } finally {
      setExecuting(false);
    }
  };

  const selectedArtifact = selectedArtifactId
    ? artifactDetail?.artifactId === selectedArtifactId
      ? artifactDetail
      : artifacts.find((artifact) => artifact.artifactId === selectedArtifactId) || null
    : null;
  const selectedArtifactDetailLoaded = artifactDetail?.artifactId === selectedArtifactId;

  const handleClose = async () => {
    if (!session?.sessionId) return;
    if (!confirm("确认关闭沙箱会话？关闭后不能继续执行。")) return;

    try {
      setClosing(true);
      const closed = await closeSandboxSession(session.sessionId);
      setSession((prev) => closed || (prev ? { ...prev, status: "CANCELLED" } : null));
      await refreshSessions();
      toast.success("沙箱会话已关闭");
    } catch (error) {
      toast.error(getErrorMessage(error, "关闭会话失败"));
      console.error(error);
    } finally {
      setClosing(false);
    }
  };

  const handleSweepExpiredSessions = async () => {
    try {
      setSweepingExpiredSessions(true);
      const result = await sweepExpiredSandboxSessions();
      const currentClosed = result.closedSessions?.find((item) => item.sessionId === session?.sessionId);
      if (currentClosed) {
        setSession(currentClosed);
        await loadSessionData(currentClosed);
      }
      await refreshSessions();
      const closedCount = result.closedCount ?? 0;
      const failedCount = result.failedCount ?? 0;
      if (failedCount > 0) {
        toast.error(`Expired sweep closed ${closedCount} session(s), failed ${failedCount}`);
      } else {
        toast.success(`Expired sweep closed ${closedCount} session(s)`);
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "Sweep expired sessions failed"));
      console.error(error);
    } finally {
      setSweepingExpiredSessions(false);
    }
  };

  const handleSweepOrphanedRuntimeResources = async () => {
    try {
      setSweepingOrphanedRuntimeResources(true);
      const result = await sweepOrphanedSandboxRuntimeResources();
      await refreshSessions();
      await refreshRuntimeGovernance();
      const removedCount = result.removedWorkspaceCount ?? 0;
      const failedCount = result.failedWorkspaceCount ?? 0;
      const orphanContainerCount = result.orphanContainerCount ?? 0;
      const containerInspectionFailures = result.failedContainerInspectionCount ?? 0;
      if (failedCount > 0 || containerInspectionFailures > 0) {
        toast.error(
          `Runtime orphan sweep removed ${removedCount} workspace(s), found ${orphanContainerCount} orphan container(s), failed ${failedCount + containerInspectionFailures}`
        );
      } else {
        toast.success(
          `Runtime orphan sweep removed ${removedCount} workspace(s), found ${orphanContainerCount} orphan container(s)`
        );
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "Runtime orphan sweep failed"));
      console.error(error);
    } finally {
      setSweepingOrphanedRuntimeResources(false);
    }
  };

  const handleInspectRuntimeHealth = async () => {
    try {
      setCheckingRuntimeHealth(true);
      const health = await getSandboxRuntimeHealth();
      setRuntimeHealth(health || null);
      setRuntimeGovernanceError(null);
      const status = health.status || "UNKNOWN";
      const inspected = health.inspectedContainerCount ?? 0;
      const orphans = health.orphanContainerCount ?? 0;
      const failures = health.failedContainerInspectionCount ?? 0;
      const activeSessions = health.activeSessionCount ?? 0;
      const activeLimit = health.activeSessionLimit ?? 0;
      const capacity =
        activeLimit > 0
          ? `${health.capacityStatus || "UNKNOWN"} ${activeSessions}/${activeLimit}`
          : health.capacityStatus || "UNBOUNDED";
      const summary = `Runtime ${status}: engine ${health.engineAvailable ? "available" : "unavailable"}, workspace ${health.workspaceAvailable ? "available" : "unavailable"}, capacity ${capacity}, containers ${inspected}, orphan ${orphans}`;
      if (status === "HEALTHY") {
        toast.success(summary);
      } else if (failures > 0 || status === "UNAVAILABLE" || status === "UNSUPPORTED") {
        toast.error(summary);
      } else {
        toast.warning(summary);
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "Runtime health check failed"));
      console.error(error);
    } finally {
      setCheckingRuntimeHealth(false);
    }
  };

  const handleReapOrphanedRuntimeContainers = async () => {
    try {
      setReapingOrphanedRuntimeContainers(true);
      const preview = await reapOrphanedSandboxRuntimeContainers(true);
      const orphanCount = preview.orphanContainerCount ?? 0;
      const inspectionFailures = preview.failedContainerInspectionCount ?? 0;
      if (inspectionFailures > 0) {
        toast.error(`Runtime container inspection failed: ${inspectionFailures}`);
        return;
      }
      if (orphanCount <= 0) {
        toast.success("No orphan runtime containers found");
        return;
      }
      if (!confirm(`Reap ${orphanCount} orphan runtime container(s)?`)) {
        return;
      }
      const result = await reapOrphanedSandboxRuntimeContainers(false);
      await refreshRuntimeGovernance();
      const reapedCount = result.reapedContainerCount ?? 0;
      const failedCount = result.failedContainerCount ?? 0;
      if (failedCount > 0) {
        toast.error(`Runtime container reap removed ${reapedCount}, failed ${failedCount}`);
      } else {
        toast.success(`Runtime container reap removed ${reapedCount}`);
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "Runtime container reap failed"));
      console.error(error);
    } finally {
      setReapingOrphanedRuntimeContainers(false);
    }
  };

  const handleSaveRuntimeProfilePolicy = async (
    profile: SandboxRuntimeProfile,
    ttlSeconds: number,
    status: string
  ) => {
    if (!profile.runtimeType) return;
    if (!Number.isFinite(ttlSeconds) || ttlSeconds < 60 || ttlSeconds > 7200) {
      toast.error("Runtime profile TTL must be between 60 and 7200 seconds");
      return;
    }
    try {
      setSavingProfileRuntimeType(profile.runtimeType);
      await upsertSandboxRuntimeProfilePolicy({
        policyId: profile.policyId,
        tenantId: currentSandboxTenantId(),
        runtimeType: profile.runtimeType,
        profileId: profile.profileId,
        status,
        sessionTtlSeconds: Math.trunc(ttlSeconds),
        networkAllowed: false
      });
      await refreshRuntimeGovernance();
      toast.success(`Runtime profile ${profile.runtimeType} policy saved`);
    } catch (error) {
      toast.error(getErrorMessage(error, "Runtime profile policy save failed"));
      console.error(error);
    } finally {
      setSavingProfileRuntimeType(null);
    }
  };

  if (!featureState.enabled) {
    return <FeatureUnavailableState featureState={featureState} featureName="沙箱" />;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">沙箱</h1>
          <p className="admin-page-subtitle">创建沙箱会话，复现工具调用</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="space-y-4">
          <RuntimeGovernancePanel
            health={runtimeHealth}
            profiles={runtimeProfiles}
            loading={loadingRuntimeGovernance}
            error={runtimeGovernanceError}
            onRefresh={() => void refreshRuntimeGovernance(true)}
            onSavePolicy={(profile, ttlSeconds, status) =>
              void handleSaveRuntimeProfilePolicy(profile, ttlSeconds, status)
            }
            savingProfileRuntimeType={savingProfileRuntimeType}
          />

          <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <span>会话</span>
              {session && <Badge variant={session.status === "CREATED" ? "default" : "secondary"}>{session.status}</Badge>}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {!session ? (
              <Button className="admin-primary-gradient" onClick={handleCreateSession}>
                <Play className="w-4 h-4 mr-1" />
                创建会话
              </Button>
            ) : (
              <>
                <div className="text-sm">
                  <span className="text-slate-500">会话 ID：</span> {session.sessionId}
                </div>
                <div className="grid gap-2 rounded border border-slate-100 bg-slate-50 p-3 text-xs text-muted-foreground sm:grid-cols-2">
                  <div>
                    <div className="uppercase">Profile</div>
                    <div className="font-mono text-slate-700">{session.profileId || "-"}</div>
                  </div>
                  <div>
                    <div className="uppercase">Expires</div>
                    <div className="text-slate-700">{formatTimestamp(session.expiresAt)}</div>
                  </div>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">工具 ID（可选）</label>
                  <Input value={toolId} onChange={(e) => setToolId(e.target.value)} placeholder="tool-id" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">参数 (JSON)</label>
                  <Textarea value={argsJson} onChange={(e) => setArgsJson(e.target.value)} rows={6} className="font-mono text-sm" />
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button className="admin-primary-gradient" disabled={executing || isTerminalSessionStatus(session.status)} onClick={handleExecute}>
                    {executing ? "执行中..." : "执行"}
                  </Button>
                  <Button variant="outline" disabled={loadingHistory} onClick={() => session.sessionId && refreshExecutions(session.sessionId)}>
                    <History className="w-4 h-4 mr-1" />
                    {loadingHistory ? "刷新中..." : "刷新历史"}
                  </Button>
                  <Button variant="destructive" disabled={closing || isTerminalSessionStatus(session.status)} onClick={handleClose}>
                    <Square className="w-4 h-4 mr-1" />
                    {closing ? "关闭中..." : "关闭会话"}
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center justify-between gap-2">
                <span>最近会话</span>
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    title="Sweep expired sessions"
                    disabled={sweepingExpiredSessions}
                    onClick={() => void handleSweepExpiredSessions()}
                  >
                    <TimerReset className={`h-4 w-4 ${sweepingExpiredSessions ? "animate-spin" : ""}`} />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    title="Inspect runtime health"
                    disabled={checkingRuntimeHealth}
                    onClick={() => void handleInspectRuntimeHealth()}
                  >
                    <Activity className={`h-4 w-4 ${checkingRuntimeHealth ? "animate-pulse" : ""}`} />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    title="Reap orphaned runtime containers"
                    disabled={reapingOrphanedRuntimeContainers}
                    onClick={() => void handleReapOrphanedRuntimeContainers()}
                  >
                    <Trash2 className={`h-4 w-4 ${reapingOrphanedRuntimeContainers ? "animate-pulse" : ""}`} />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    title="Sweep orphaned runtime workspaces"
                    disabled={sweepingOrphanedRuntimeResources}
                    onClick={() => void handleSweepOrphanedRuntimeResources()}
                  >
                    <FolderX className={`h-4 w-4 ${sweepingOrphanedRuntimeResources ? "animate-pulse" : ""}`} />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    title="刷新会话"
                    disabled={loadingSessions}
                    onClick={() => void refreshSessions()}
                  >
                    <RefreshCw className={`h-4 w-4 ${loadingSessions ? "animate-spin" : ""}`} />
                  </Button>
                </div>
              </CardTitle>
            </CardHeader>
            <CardContent>
              {sessions.length === 0 ? (
                <div className="text-sm text-muted-foreground">
                  {loadingSessions ? "加载中..." : "暂无会话"}
                </div>
              ) : (
                <div className="space-y-2">
                  {sessions.map((item) => {
                    const active = item.sessionId && item.sessionId === session?.sessionId;
                    return (
                      <button
                        key={item.sessionId || item.runId}
                        type="button"
                        disabled={!item.sessionId}
                        className={`w-full rounded border p-3 text-left text-sm transition ${
                          active
                            ? "border-sky-300 bg-sky-50"
                            : "border-slate-100 bg-slate-50 hover:border-slate-200 hover:bg-white"
                        }`}
                        onClick={() => void handleSelectSession(item)}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <div className="min-w-0 truncate font-mono text-xs text-slate-600">
                            {item.sessionId || "-"}
                          </div>
                          <Badge variant={item.status === "CREATED" ? "default" : "secondary"}>
                            {item.status || "UNKNOWN"}
                          </Badge>
                        </div>
                        <div className="mt-2 grid gap-1 text-xs text-muted-foreground sm:grid-cols-2">
                          <div className="truncate">{item.runtimeType || "CODE_INTERPRETER"}</div>
                          <div className="truncate sm:text-right">{formatTimestamp(item.updatedAt || item.createdAt)}</div>
                          <div className="truncate">Profile: {item.profileId || "-"}</div>
                          <div className="truncate sm:text-right">Expires: {formatTimestamp(item.expiresAt)}</div>
                        </div>
                        {item.runId && (
                          <div className="mt-1 truncate text-xs text-muted-foreground">
                            Run: {item.runId}
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          {lastResult?.execution && (
            <Card>
              <CardHeader><CardTitle>执行结果</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  <Badge variant={executionBadgeVariant(lastResult.execution.status)}>{lastResult.execution.status}</Badge>
                  {lastResult.execution.resultSummary && (
                    <pre className="bg-slate-50 p-3 rounded-lg text-sm font-mono overflow-auto max-h-[200px]">{lastResult.execution.resultSummary}</pre>
                  )}
                  {lastResult.reasonCode && (
                    <div className="text-sm text-muted-foreground">原因: {lastResult.reasonCode}</div>
                  )}
                  <div className="text-sm text-muted-foreground">执行 ID: {lastResult.execution.executionId}</div>
                </div>
              </CardContent>
            </Card>
          )}

          {session && (
            <Card>
              <CardHeader><CardTitle>执行历史</CardTitle></CardHeader>
              <CardContent>
                {executions.length === 0 ? (
                  <div className="text-sm text-muted-foreground">{loadingHistory ? "加载中..." : "暂无执行记录"}</div>
                ) : (
                  <div className="space-y-2">
                    {executions.map((execution) => (
                      <div key={execution.executionId} className="grid grid-cols-[96px_1fr] gap-3 rounded border border-slate-100 bg-slate-50 p-3 text-sm">
                        <Badge variant={executionBadgeVariant(execution.status)}>{execution.status}</Badge>
                        <div className="min-w-0 space-y-1">
                          <div className="truncate font-mono text-xs text-slate-500">{execution.executionId}</div>
                          <div className="text-muted-foreground">
                            {execution.runtimeType} · {execution.reasonCode} · {formatTimestamp(execution.updatedAt || execution.createdAt)}
                          </div>
                          {execution.resultSummary && (
                            <div className="truncate text-slate-700">{execution.resultSummary}</div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {artifacts.length > 0 && (
            <Card>
              <CardHeader><CardTitle>产物</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {artifacts.map((art) => {
                    const detailForArtifact = artifactDetail?.artifactId === art.artifactId ? artifactDetail : null;
                    const downloadBlocked = detailForArtifact?.downloadable === false;
                    return (
                      <div key={art.artifactId} className="p-2 bg-slate-50 rounded text-sm">
                        <div className="flex items-center justify-between gap-2">
                          <button
                            type="button"
                            className="min-w-0 truncate text-left font-medium"
                            onClick={() => handleSelectArtifact(art.artifactId)}
                          >
                            {artifactDisplayName(art)}
                          </button>
                          <div className="flex shrink-0 items-center gap-1">
                            <Badge variant={artifactBadgeVariant(art.scanStatus)}>{art.scanStatus || "UNKNOWN"}</Badge>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-8 w-8"
                              title="Details"
                              disabled={loadingArtifactDetailId === art.artifactId}
                              onClick={() => handleSelectArtifact(art.artifactId)}
                            >
                              <Info className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-8 w-8"
                              title={downloadBlocked ? "Download blocked" : "Download"}
                              disabled={downloadBlocked || downloadingArtifactId === art.artifactId}
                              onClick={() => handleDownloadArtifact(detailForArtifact || art)}
                            >
                              <Download className="h-4 w-4" />
                            </Button>
                          </div>
                        </div>
                        <div className="mt-1 truncate text-muted-foreground">
                          {art.mediaType || art.mimeType || "unknown"} · {art.sensitivity || "UNKNOWN"} · {art.promptVisible ? "PROMPT_VISIBLE" : "PROMPT_BLOCKED"}
                        </div>
                        {art.scanSummary && (
                          <div className="mt-1 truncate text-xs text-muted-foreground">
                            {art.scanSummary}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
                {selectedArtifact && (
                  <div className="mt-4 rounded border border-slate-200 bg-white p-3 text-sm">
                    <div className="flex items-center justify-between gap-2">
                      <div className="min-w-0">
                        <div className="truncate font-medium">{artifactDisplayName(selectedArtifact)}</div>
                        <div className="truncate font-mono text-xs text-muted-foreground">{selectedArtifact.artifactId}</div>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <Badge variant={artifactBadgeVariant(selectedArtifact.scanStatus)}>
                          {selectedArtifact.scanStatus || "UNKNOWN"}
                        </Badge>
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={
                            selectedArtifact.downloadable === false ||
                            loadingArtifactDetailId === selectedArtifact.artifactId ||
                            downloadingArtifactId === selectedArtifact.artifactId
                          }
                          onClick={() => handleDownloadArtifact(selectedArtifact)}
                        >
                          <Download className="h-4 w-4" />
                          Download
                        </Button>
                      </div>
                    </div>
                    <div className="mt-3 grid gap-2 sm:grid-cols-2">
                      <div>
                        <div className="text-xs uppercase text-muted-foreground">MIME</div>
                        <div className="truncate">{selectedArtifact.contentType || selectedArtifact.mediaType || selectedArtifact.mimeType || "unknown"}</div>
                      </div>
                      <div>
                        <div className="text-xs uppercase text-muted-foreground">Sensitivity</div>
                        <div>{selectedArtifact.sensitivity || "UNKNOWN"}</div>
                      </div>
                      <div>
                        <div className="text-xs uppercase text-muted-foreground">Scan Summary</div>
                        <div className="truncate">{selectedArtifact.scanSummary || selectedArtifact.scanStatus || "UNKNOWN"}</div>
                      </div>
                      <div>
                        <div className="text-xs uppercase text-muted-foreground">Redaction JSON</div>
                        <div className="truncate font-mono text-xs">{selectedArtifact.redactionSummaryJson || "not recorded"}</div>
                      </div>
                      <div>
                        <div className="text-xs uppercase text-muted-foreground">Filename</div>
                        <div className="truncate">{selectedArtifact.filename || artifactDisplayName(selectedArtifact)}</div>
                      </div>
                      <div>
                        <div className="text-xs uppercase text-muted-foreground">Created</div>
                        <div>{formatTimestamp(selectedArtifact.createdAt)}</div>
                      </div>
                    </div>
                    <div className="mt-3 rounded bg-slate-50 p-2 text-xs text-muted-foreground">
                      {loadingArtifactDetailId === selectedArtifact.artifactId && "Download policy: loading"}
                      {loadingArtifactDetailId !== selectedArtifact.artifactId &&
                        selectedArtifact.downloadable === false &&
                        (selectedArtifact.downloadBlockedReason || "Download blocked")}
                      {loadingArtifactDetailId !== selectedArtifact.artifactId &&
                        selectedArtifact.downloadable !== false &&
                        (selectedArtifactDetailLoaded ? "Download policy: allowed" : "Download policy: pending")}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
