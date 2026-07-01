import { useState } from "react";
import { History, Play, Square } from "lucide-react";
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
  listSandboxExecutions,
  listSandboxArtifacts,
  type SandboxSession,
  type SandboxExecution,
  type SandboxExecutionResult,
  type SandboxArtifact
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

function formatTimestamp(value?: string) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
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

  const handleCreateSession = async () => {
    try {
      const data = await createSandboxSession();
      setSession(data);
      setLastResult(null);
      setArtifacts([]);
      setExecutions([]);
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
    } catch (error) {
      toast.error(getErrorMessage(error, "执行失败"));
      console.error(error);
    } finally {
      setExecuting(false);
    }
  };

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
                  {artifacts.map((art) => (
                    <div key={art.artifactId} className="p-2 bg-slate-50 rounded text-sm">
                      <div className="font-medium">{art.name || art.artifactId}</div>
                      <div className="text-muted-foreground">{art.mediaType || art.mimeType || "unknown"} · {art.scanStatus || "UNKNOWN"}</div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
