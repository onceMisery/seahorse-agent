import { useState } from "react";
import { Download, History, Info, Play, Square } from "lucide-react";
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
  listSandboxExecutions,
  listSandboxArtifacts,
  type SandboxSession,
  type SandboxExecution,
  type SandboxExecutionResult,
  type SandboxArtifact,
  type SandboxArtifactDetail
} from "@/services/sandboxService";
import { getErrorMessage } from "@/utils/error";

function isTerminalSessionStatus(status?: string) {
  return ["CANCELLED", "FAILED", "SUCCEEDED", "CLOSED"].includes(status || "");
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
      setSelectedArtifactId(null);
      setArtifactDetail(null);
      if (data.sessionId) {
        await refreshExecutions(data.sessionId);
      }
      toast.success("沙箱会话已创建");
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
      setSelectedArtifactId(null);
      setArtifactDetail(null);
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
      toast.success("沙箱会话已关闭");
    } catch (error) {
      toast.error(getErrorMessage(error, "关闭会话失败"));
      console.error(error);
    } finally {
      setClosing(false);
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
                    const downloadBlocked = art.promptVisible === false || detailForArtifact?.downloadable === false;
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
                            selectedArtifact.promptVisible === false ||
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
